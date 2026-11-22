package com.wordcool.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * SQLite ↔ Obsidian Markdown 文件夹同步。
 *
 * Markdown 文件是主数据；SQLite 只是本地索引/缓存。每次 dataChanged 后做一次差异同步：
 * 只有内容和上次清单不同、或文件不存在的单词才写盘，避免每答一个词就重写 711 个文件。
 * 回到前台时先写掉本机改动，再把 Obsidian 里改过的文件读回来。
 */
object VaultSync {

    private const val PREF = "vault"
    private const val KEY_MANIFEST = "manifest"

    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    @Volatile
    private var lastSyncAt = 0L

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

    /** 选择新 Vault 后：先把已有 .md 读进来，再把当前词库全部写出去 */
    fun onVaultSelected(ctx: Context, db: WordDb, onDone: () -> Unit) {
        resetManifest(ctx)
        exec.execute {
            doPull(ctx, db)
            doFlush(ctx, db)
            main.post { onDone() }
        }
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

    /** 回到前台：先写掉本机改动，再拉取 Obsidian 的外部编辑 */
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
            // Vault 是主数据：先把 Obsidian 里的内容读回来，再把本机独有/待同步的写出去。
            // 本机改动在 dataChanged() 时已经写盘，所以这里 pull 在前不会丢新进度。
            doPull(ctx, db)
            doFlush(ctx, db)
            main.post { onDone() }
        }
    }

    /** 数据变动后异步写差异；单个词/批量导入都走这里 */
    fun flush(ctx: Context, db: WordDb) {
        if (!VaultStore.enabled(ctx)) return
        exec.execute { doFlush(ctx, db) }
    }

    /** 删除单词时同步删掉对应 .md */
    fun deleteWord(ctx: Context, word: String) {
        if (!VaultStore.enabled(ctx)) return
        exec.execute {
            val manifest = loadManifest(ctx)
            val file = manifest.optJSONObject(word)?.optString("f").orEmpty()
            if (file.isNotBlank()) VaultStore.deleteByName(ctx, file)
            manifest.remove(word)
            saveManifest(ctx, manifest)
        }
    }

    /** 清空词库时删掉 WordCool/ 下所有 .md，并清空清单 */
    fun clearAll(ctx: Context) {
        if (!VaultStore.enabled(ctx)) return
        exec.execute {
            VaultStore.deleteAll(ctx)
            resetManifest(ctx)
        }
    }

    private fun doFlush(ctx: Context, db: WordDb) {
        val words = db.getWords()
        if (words.isEmpty()) return
        val folderFiles = VaultStore.list(ctx)?.associateBy { it.name }?.toMutableMap() ?: return
        val manifest = loadManifest(ctx)
        val present = words.map { it.word }.toSet()

        for (w in words) {
            val content = VaultMarkdown.serialize(w)
            val hash = content.hashCode()
            val entry = manifest.optJSONObject(w.word)
            val storedFile = entry?.optString("f").orEmpty()
            if (entry != null && entry.optInt("h", Int.MIN_VALUE) == hash &&
                storedFile.isNotBlank() && folderFiles.containsKey(storedFile)
            ) {
                continue
            }

            val desired = storedFile.takeIf { it.isNotBlank() }
                ?: VaultMarkdown.fileName(w.word)
            val existing = folderFiles[desired]
            val written = VaultStore.write(ctx, existing, desired, content) ?: continue
            if (existing == null) folderFiles[written.name] = written
            manifest.put(
                w.word,
                JSONObject().put("f", written.name).put("h", hash)
            )
        }

        // 词被改名或删除：清掉旧文件
        val keys = manifest.keys().asSequence().toList()
        for (key in keys) {
            if (key in present) continue
            val file = manifest.optJSONObject(key)?.optString("f").orEmpty()
            if (file.isNotBlank()) VaultStore.deleteByName(ctx, file)
            manifest.remove(key)
        }
        saveManifest(ctx, manifest)
    }

    private fun doPull(ctx: Context, db: WordDb) {
        val files = VaultStore.list(ctx) ?: return
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
        for (file in files) {
            val text = VaultStore.read(ctx, file) ?: continue
            val word = VaultMarkdown.parse(text) ?: continue
            db.upsertFromVault(word)
            manifest.put(
                word.word,
                JSONObject()
                    .put("f", file.name)
                    .put("h", VaultMarkdown.serialize(word).hashCode())
            )
        }
        saveManifest(ctx, manifest)
    }
}
