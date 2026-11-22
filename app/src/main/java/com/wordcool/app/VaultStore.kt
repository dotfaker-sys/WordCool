package com.wordcool.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.util.Locale

/**
 * Obsidian 共用的 Markdown 词库文件夹。
 *
 * 用户在设置页选择一个 Obsidian Vault 目录，本应用在其中创建 `WordCool/` 子目录，
 * 每个单词一个 `.md`，单词、音标、释义、组别和学习进度都写在 YAML frontmatter 里。
 * SQLite 只作为本地索引/缓存，Markdown 文件才是主数据。
 */
object VaultStore {

    private const val PREF = "vault"
    private const val KEY_TREE = "tree_uri"
    private const val KEY_NAME = "vault_name"
    const val FOLDER = "WordCool"

    data class VaultFile(val name: String, val uri: Uri, val modified: Long)

    @Volatile
    private var cacheKey = ""

    @Volatile
    private var cacheFolder: Uri? = null

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun enabled(ctx: Context): Boolean {
        val tree = treeUri(ctx) ?: return false
        return try {
            ctx.contentResolver.persistedUriPermissions.any {
                it.uri == tree && it.isWritePermission
            }
        } catch (t: Throwable) {
            false
        }
    }

    fun treeUri(ctx: Context): Uri? =
        prefs(ctx).getString(KEY_TREE, "")?.takeIf { it.isNotBlank() }?.let(Uri::parse)

    fun displayName(ctx: Context): String {
        val raw = prefs(ctx).getString(KEY_NAME, "")?.takeIf { it.isNotBlank() } ?: FOLDER
        // 老版本可能存了 primary:Download/Vault 这种 document id，显示时只取目录名
        return raw.substringAfterLast('/').substringAfterLast(':').ifBlank { FOLDER }
    }

    /** 选择 Vault 根目录；返回是否成功拿到可持久化的读写权限 */
    fun setTree(ctx: Context, tree: Uri): Boolean {
        return try {
            ctx.contentResolver.takePersistableUriPermission(
                tree,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val name = queryName(ctx, rootUri) ?: tree.lastPathSegment ?: "Obsidian"
            prefs(ctx).edit()
                .putString(KEY_TREE, tree.toString())
                .putString(KEY_NAME, name)
                .apply()
            cacheKey = ""
            cacheFolder = null
            true
        } catch (t: Throwable) {
            false
        }
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().remove(KEY_TREE).remove(KEY_NAME).apply()
        cacheKey = ""
        cacheFolder = null
    }

    @Synchronized
    fun folder(ctx: Context): Uri? {
        val tree = treeUri(ctx) ?: return null
        val key = tree.toString()
        if (cacheKey == key && cacheFolder != null) return cacheFolder

        return try {
            val rootId = DocumentsContract.getTreeDocumentId(tree)
            val rootUri = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            val rootName = queryName(ctx, rootUri)
            if (rootName == FOLDER) {
                cacheKey = key
                cacheFolder = rootUri
                return rootUri
            }

            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
            val projection = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME)
            ctx.contentResolver.query(children, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                while (c.moveToNext()) {
                    if (c.getString(nameCol) == FOLDER) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idCol))
                        cacheKey = key
                        cacheFolder = uri
                        return uri
                    }
                }
            }

            val created = DocumentsContract.createDocument(
                ctx.contentResolver, rootUri, Document.MIME_TYPE_DIR, FOLDER
            )
            if (created != null) {
                cacheKey = key
                cacheFolder = created
            }
            created
        } catch (t: Throwable) {
            null
        }
    }

    fun list(ctx: Context): List<VaultFile>? {
        val tree = treeUri(ctx) ?: return null
        val dir = folder(ctx) ?: return null
        val docId = try {
            DocumentsContract.getDocumentId(dir)
        } catch (t: Throwable) {
            return null
        }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
        val projection = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED
        )
        val out = mutableListOf<VaultFile>()
        return try {
            ctx.contentResolver.query(children, projection, null, null, null)?.use { c ->
                val idCol = c.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val nameCol = c.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                val timeCol = c.getColumnIndexOrThrow(Document.COLUMN_LAST_MODIFIED)
                while (c.moveToNext()) {
                    val name = c.getString(nameCol) ?: continue
                    val mime = c.getString(mimeCol) ?: ""
                    if (mime == Document.MIME_TYPE_DIR || !name.endsWith(".md", ignoreCase = true)) continue
                    out.add(
                        VaultFile(
                            name = name,
                            uri = DocumentsContract.buildDocumentUriUsingTree(tree, c.getString(idCol)),
                            modified = if (c.isNull(timeCol)) 0L else c.getLong(timeCol)
                        )
                    )
                }
            }
            out.sortedBy { it.name }
        } catch (t: Throwable) {
            null
        }
    }

    fun read(ctx: Context, file: VaultFile): String? = try {
        ctx.contentResolver.openInputStream(file.uri)?.use {
            it.bufferedReader(Charsets.UTF_8).readText()
        }
    } catch (t: Throwable) {
        null
    }

    fun write(ctx: Context, existing: VaultFile?, name: String, content: String): VaultFile? {
        return try {
            val dir = folder(ctx) ?: return null
            var file = existing
            if (file == null) {
                val uri = DocumentsContract.createDocument(
                    ctx.contentResolver, dir, "text/markdown", name
                ) ?: DocumentsContract.createDocument(
                    ctx.contentResolver, dir, "text/plain", name
                ) ?: return null
                file = VaultFile(name, uri, System.currentTimeMillis())
            }
            val ok = try {
                ctx.contentResolver.openOutputStream(file.uri, "wt")?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            } catch (t: Throwable) {
                ctx.contentResolver.openOutputStream(file.uri)?.use {
                    it.write(content.toByteArray(Charsets.UTF_8))
                }
            }
            if (ok == null) null else file.copy(modified = System.currentTimeMillis())
        } catch (t: Throwable) {
            null
        }
    }

    fun delete(ctx: Context, file: VaultFile?) {
        if (file == null) return
        try {
            DocumentsContract.deleteDocument(ctx.contentResolver, file.uri)
        } catch (t: Throwable) {
            Unit
        }
    }

    fun deleteByName(ctx: Context, name: String) {
        if (name.isBlank()) return
        list(ctx)?.firstOrNull { it.name == name }?.let { delete(ctx, it) }
    }

    fun deleteAll(ctx: Context) {
        list(ctx)?.forEach { delete(ctx, it) }
    }

    private fun queryName(ctx: Context, uri: Uri): String? = try {
        ctx.contentResolver.query(
            uri, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (t: Throwable) {
        null
    }
}

/** Word ↔ Obsidian Markdown（YAML frontmatter + 可读正文）。 */
object VaultMarkdown {

    private const val KEYS =
        "word|phonetic|phonetic_uk|phonetic_us|meaning|example|note|group|" +
            "state|streak|right|wrong|next_review|learned_day|created_at"

    fun fileName(word: String): String {
        val base = word.trim().lowercase(Locale.ROOT)
            .replace(Regex("""[\\/:*?"<>|\u0000-\u001f]"""), "_")
            .trim('.', ' ', '_')
        return (base.ifEmpty { "word" }).take(80) + ".md"
    }

    fun serialize(w: Word): String = buildString {
        append("---\n")
        fun str(k: String, v: String) {
            append(k).append(": \"").append(escape(v)).append("\"\n")
        }
        fun num(k: String, v: Long) {
            append(k).append(": ").append(v).append('\n')
        }
        str("word", w.word)
        str("phonetic", w.phonetic)
        str("phonetic_uk", w.phoneticUk)
        str("phonetic_us", w.phoneticUs)
        str("meaning", w.meaning)
        str("example", w.example)
        str("note", w.note)
        str("group", w.groupName)
        num("state", w.state.toLong())
        num("streak", w.streak.toLong())
        num("right", w.right.toLong())
        num("wrong", w.wrong.toLong())
        num("next_review", w.nextReview)
        num("learned_day", w.learnedDay)
        num("created_at", w.createdAt)
        append("---\n\n# ").append(w.word).append("\n\n")
        if (w.meaning.isNotBlank()) append("**释义**：").append(w.meaning).append("\n\n")
        if (w.example.isNotBlank()) append("**例句**：").append(w.example).append("\n\n")
        if (w.note.isNotBlank()) append("**记忆**：").append(w.note).append('\n')
    }

    fun parse(text: String): Word? {
        val clean = text.removePrefix("\uFEFF").replace("\r\n", "\n").replace('\r', '\n')
        if (!clean.startsWith("---")) return null
        val end = clean.indexOf("\n---", startIndex = 3)
        if (end < 0) return null
        val front = clean.substring(4, end)
        val map = HashMap<String, String>()
        for (line in front.split('\n')) {
            val p = line.indexOf(':')
            if (p <= 0) continue
            val key = line.substring(0, p).trim()
            if (!key.matches(Regex(KEYS))) continue
            map[key] = unquote(line.substring(p + 1).trim())
        }
        val word = map["word"]?.trim().orEmpty()
        if (word.isEmpty()) return null
        return Word(
            word = word,
            phonetic = map["phonetic"].orEmpty(),
            phoneticUk = map["phonetic_uk"].orEmpty(),
            phoneticUs = map["phonetic_us"].orEmpty(),
            meaning = map["meaning"].orEmpty(),
            example = map["example"].orEmpty(),
            note = map["note"].orEmpty(),
            groupName = map["group"].orEmpty(),
            state = map["state"]?.toIntOrNull() ?: 0,
            streak = map["streak"]?.toIntOrNull() ?: 0,
            right = map["right"]?.toIntOrNull() ?: 0,
            wrong = map["wrong"]?.toIntOrNull() ?: 0,
            nextReview = map["next_review"]?.toLongOrNull() ?: 0,
            learnedDay = map["learned_day"]?.toLongOrNull() ?: 0,
            createdAt = map["created_at"]?.toLongOrNull() ?: 0
        )
    }

    private fun escape(s: String): String = s
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")

    private fun unquote(s: String): String {
        if (s.length >= 2 && s.first() == '"' && s.last() == '"') {
            return s.substring(1, s.length - 1)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
        }
        if (s.length >= 2 && s.first() == '\'' && s.last() == '\'') {
            return s.substring(1, s.length - 1)
        }
        return s
    }
}
