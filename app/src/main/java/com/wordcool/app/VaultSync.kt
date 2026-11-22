package com.wordcool.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * SQLite ↔ Obsidian Markdown 文件夹同步。
 *
 * Markdown 文件是主数据；SQLite 只是本地索引/缓存。
 * - 写：只写内容变化的词，文件列表缓存，避免每答一个词都重新遍历 SAF。
 * - 读：文件 mtime 未变且清单哈希一致就跳过，不再每次回前台重写 711 行数据库。
 * - 冲突：回到前台先读 Obsidian，再把本机独有/待同步词写出去。
 */
object VaultSync {

    private const val PREF = "vault"
    private const val KEY_MANIFEST = "manifest"
    private const val MTIME_TOLERANCE = 3000L

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var lastSyncAt = 0L

    /** 当前 WordCool/ 下已知文件；避免每次 dataChanged 都 list 一遍 711 个文件 */
    @Volatile
    private var fileCache: MutableMap<String, VaultStore.VaultFile>? = null

    @Volatile
    private var flushRunning = false

    @Volatile
    private var flushAgain = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun loadManifest(ctx: Context): JSONObject = try {
        JSONObject(prefs(ctx).getString(KEY_MANIFEST, "{}") ?: "{}")
    } catch (t: Throwable) {
        JSONObject()
    }

    private fun saveManifest(ctx: Context, manifest: JSONObject) {
        prefs(ctx).edit().putString(KEY_MANIFEST, manifest.toString()).apply()
    }

    fun resetManifest(ctx: Context) {
        prefs(ctx).edit().remove(KEY_MANIFEST).apply()
    }

    /** 选择新 Vault 时清空清单和文件缓存 */
    fun resetForNewVault(ctx: Context) {
        resetManifest(ctx)
        fileCache = null
        lastSyncAt = 0L
    }

    /** 以本机词库为准，全部写入 Obsidian 文件夹 */
    fun exportAll(ctx: Context, db: WordDb, onDone: () -> Unit) {
        if (!VaultStore.enabled(ctx)) {
            main.post { onDone() }
            return
        }
        exec.execute {
            doFlush(ctx, db)
            main.post { onDone() }
        }
    }

    /** 以文件夹里的 Markdown 为准，导入并覆盖本机词库（导入前先备份 JSON） */
    fun importAll(ctx: Context, db: WordDb, onDone: () -> Unit) {
        if (!VaultStore.enabled(ctx)) {
            main.post { onDone() }
            return
        }
        exec.execute {
            doPull(ctx, db)
            main.post { onDone() }
        }
    }

    /** 回到前台：先读 Obsidian 的外部编辑，再把本机独有/待同步的写出去 */
    fun sync(ctx: Context, db: WordDb, onDone: () -> Unit) {
        if (!VaultStore.enabled(ctx)) {
            main.post { onDone() }
            return
        }
        val now = System.currentTimeMillis()
        if (now - lastSyncAt < 1500L) {
            main.post { onDone() }
            return
        }
        lastSyncAt = now
        exec.execute {
            doPull(ctx, db)
            doFlush(ctx, db)
            main.post { onDone() }
        }
    }

    /** 数据变动后异步写差异；合并连续调用，避免答一个词排一串 711 次遍历 */
    fun flush(ctx: Context, db: WordDb) {
        if (!VaultStore.enabled(ctx)) return
        synchronized(this) {
            if (flushRunning) {
                flushAgain = true
                return
            }
            flushRunning = true
        }
        exec.execute {
            try {
                doFlush(ctx, db)
            } finally {
                val again = synchronized(this) {
                    flushRunning = false
                    val a = flushAgain
                    flushAgain = false
                    a
                }
                if (again) flush(ctx, db)
            }
        }
    }

    /** 删除单词时同步删掉对应 .md */
    fun deleteWord(ctx: Context, word: String) {
        if (!VaultStore.enabled(ctx)) return
        exec.execute {
            val manifest = loadManifest(ctx)
            val file = manifest.optJSONObject(word)?.optString("f").orEmpty()
            if (file.isNotBlank()) {
                VaultStore.deleteByName(ctx, file)
                fileCache?.remove(file)
            }
            manifest.remove(word)
            saveManifest(ctx, manifest)
        }
    }

    /** 清空词库时删掉 WordCool/ 下所有 .md，并清空清单 */
    fun clearAll(ctx: Context) {
        if (!VaultStore.enabled(ctx)) return
        exec.execute {
            VaultStore.deleteAll(ctx)
            fileCache = null
            resetManifest(ctx)
        }
    }

    private fun doFlush(ctx: Context, db: WordDb) {
        val words = db.getWords()
        if (words.isEmpty()) return
        val folderFiles = fileCache ?: VaultStore.list(ctx)
            ?.associateBy { it.name }
            ?.toMutableMap()
            ?: return
        fileCache = folderFiles
        val manifest = loadManifest(ctx)
        val present = words.map { it.word }.toSet()
        var changed = false

        for (w in words) {
            val content = VaultMarkdown.serialize(w)
            val hash = content.hashCode()
            val entry = manifest.optJSONObject(w.word)
            val storedFile = entry?.optString("f").orEmpty()
            val existing = storedFile.takeIf { it.isNotBlank() }?.let { folderFiles[it] }
            if (entry != null && entry.optInt("h", Int.MIN_VALUE) == hash && existing != null) {
                continue
            }

            val desired = storedFile.takeIf { it.isNotBlank() }
                ?: VaultMarkdown.fileName(w.word)
            val written = VaultStore.write(ctx, existing, desired, content) ?: continue
            changed = true
            if (existing == null) folderFiles[written.name] = written
            if (storedFile.isNotBlank() && storedFile != written.name) {
                VaultStore.deleteByName(ctx, storedFile)
                folderFiles.remove(storedFile)
            }
            manifest.put(
                w.word,
                JSONObject()
                    .put("f", written.name)
                    .put("h", hash)
                    .put("t", written.modified)
            )
        }

        // 词被改名或删除：清掉旧文件
        val keys = manifest.keys().asSequence().toList()
        for (key in keys) {
            if (key in present) continue
            val file = manifest.optJSONObject(key)?.optString("f").orEmpty()
            if (file.isNotBlank()) {
                VaultStore.deleteByName(ctx, file)
                folderFiles.remove(file)
            }
            manifest.remove(key)
            changed = true
        }
        if (changed) saveManifest(ctx, manifest)
    }

    private fun doPull(ctx: Context, db: WordDb) {
        val files = VaultStore.list(ctx) ?: return
        fileCache = files.associateBy { it.name }.toMutableMap()
        if (files.isEmpty()) return

        val manifest = loadManifest(ctx)
        if (manifest.length() == 0) {
            // 第一次从文件夹导入前，把本机词库备份到应用私有目录，避免误覆盖
            try {
                java.io.File(ctx.filesDir, "vault_import_backup.json")
                    .writeText(db.exportJson(), Charsets.UTF_8)
            } catch (t: Throwable) {
                Unit
            }
        }

        val existing = db.getWords().map { it.word }.toHashSet()
        val knownFiles = HashMap<String, String>()
        manifest.keys().forEach { word ->
            val f = manifest.optJSONObject(word)?.optString("f").orEmpty()
            if (f.isNotBlank()) knownFiles[f] = word
        }

        var changed = false
        val present = HashSet<String>()
        for (file in files) {
            val known = knownFiles[file.name]
            val entry = known?.let { manifest.optJSONObject(it) }
            val lastT = entry?.optLong("t", 0L) ?: 0L

            // 文件 mtime 没变、词也还在：直接跳过，不读不写
            if (known != null && known in existing &&
                lastT > 0 && file.modified > 0 &&
                file.modified <= lastT + MTIME_TOLERANCE
            ) {
                present.add(known)
                continue
            }

            val text = VaultStore.read(ctx, file)
            if (text == null) {
                if (known != null) present.add(known)
                continue
            }
            val word = VaultMarkdown.parse(text)
            if (word == null) {
                // 文件存在但解析失败：不要误删对应词
                if (known != null) present.add(known)
                continue
            }

            val hash = VaultMarkdown.serialize(word).hashCode()
            if (known != null && known in existing && entry?.optInt("h", Int.MIN_VALUE) == hash) {
                // 内容没变，只更新 mtime 标记，不写数据库
                manifest.put(known, entry!!.put("t", file.modified))
                changed = true
                present.add(known)
                continue
            }

            db.upsertFromVault(word)
            manifest.put(
                word.word,
                JSONObject()
                    .put("f", file.name)
                    .put("h", hash)
                    .put("t", file.modified)
            )
            changed = true
            present.add(word.word)
            existing.add(word.word)
        }

        // 外部删除：只在文件夹里仍有其他文件时处理，避免权限异常导致整库被清空
        if (files.isNotEmpty()) {
            for (word in manifest.keys().asSequence().toList()) {
                if (word in present) continue
                val file = manifest.optJSONObject(word)?.optString("f").orEmpty()
                if (file.isNotBlank()) {
                    db.deleteWordByWord(word)
                    manifest.remove(word)
                    fileCache?.remove(file)
                    changed = true
                }
            }
        }
        if (changed) saveManifest(ctx, manifest)
    }
}
