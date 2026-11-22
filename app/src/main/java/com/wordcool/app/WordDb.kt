package com.wordcool.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Word(
    var id: Long = 0,
    var word: String = "",
    var phonetic: String = "",
    var meaning: String = "",
    var example: String = "",
    var note: String = "",
    var state: Int = 0,          // 0 未解锁 / 1 学习中 / 2 已掌握
    var streak: Int = 0,
    var right: Int = 0,
    var wrong: Int = 0,
    var nextReview: Long = 0,   // 以「天」为单位
    var learnedDay: Long = 0,
    var createdAt: Long = 0,
    var groupName: String = "",   // v3 新增：Unit 1 / Day 2 等；空串表示未分组
    var phoneticUk: String = "",  // v5 新增：英式音标 UK / BrE
    var phoneticUs: String = ""   // v5 新增：美式音标 US / AmE
)

data class Imported(
    val word: String,
    val phonetic: String = "",
    val meaning: String = "",
    val example: String = "",
    val note: String = "",
    val group: String = "",       // v3 新增：由文档标题行自动识别
    val phoneticUk: String = "",  // v5 新增：英式音标
    val phoneticUs: String = ""   // v5 新增：美式音标
)

data class Stats(
    val total: Int,
    val locked: Int,
    val learning: Int,
    val mastered: Int,
    val unlocked: Int,
    val today: Int,
    val streak: Int,
    val right: Int,
    val wrong: Int,
    val week: List<Pair<String, Int>>
)

/** 划组管理用：name 为真实组名，空串表示「未分组」 */
data class GroupInfo(
    val name: String,
    val total: Int,
    val unlocked: Int,
    val mastered: Int
)

class WordDb(ctx: Context) : SQLiteOpenHelper(ctx.applicationContext, "wordcool.db", null, 3) {

    private val appCtx: Context = ctx.applicationContext
    private val prefs = appCtx.getSharedPreferences("wordcool", Context.MODE_PRIVATE)

    companion object {
        private const val DAY_MS = 86_400_000L
        private val offset: Long get() = TimeZone.getDefault().rawOffset.toLong()

        /** 按本地时区的「日序号」，避免 UTC 日界导致中国用户凌晨算错一天 */
        fun today(): Long = (System.currentTimeMillis() + offset) / DAY_MS

        val DEMO: List<Imported> = listOf(
            Imported("abandon", "/əˈbændən/", "v. 放弃；抛弃", "He abandoned the plan. 他放弃了计划。", "a + band + on：连绑带子都不要了 → 放弃", "Unit 1"),
            Imported("benefit", "/ˈbenɪfɪt/", "n. 益处；v. 受益", "Regular exercise benefits everyone. 规律运动对人人有益。", "bene（好）+ fit（做）", "Unit 1"),
            Imported("curious", "/ˈkjʊəriəs/", "adj. 好奇的", "She is curious about the world. 她对世界充满好奇。", "cure（关心）→ 总想弄明白", "Unit 1"),
            Imported("deliberate", "/dɪˈlɪbərət/", "adj. 蓄意的；从容的", "It was a deliberate choice. 这是刻意的选择。", "de + liber（权衡）→ 反复权衡过", "Unit 1"),
            Imported("efficient", "/ɪˈfɪʃnt/", "adj. 高效的", "This method is more efficient. 这个方法更高效。", "ef + fic（做）→ 做得出来又省劲", "Day 1"),
            Imported("forecast", "/ˈfɔːkɑːst/", "n./v. 预测；预报", "The forecast says rain tomorrow. 预报说明天有雨。", "fore（提前）+ cast（抛出）", "Day 1"),
            Imported("generous", "/ˈdʒenərəs/", "adj. 慷慨的", "He is generous with his time. 他很乐意花时间帮人。", "gener（产生/给予）→ 愿意给", "Day 1"),
            Imported("hesitate", "/ˈhezɪteɪt/", "v. 犹豫", "Don't hesitate to ask. 有疑问尽管问。", "haes（粘住）→ 脚被粘住了，迈不出去", "Day 2"),
            Imported("illustrate", "/ˈɪləstreɪt/", "v. 说明；举例", "Let me illustrate with an example. 我举个例子说明。", "il + lustr（光）→ 把事情照亮", "Day 2"),
            Imported("maintain", "/meɪnˈteɪn/", "v. 维持；保养", "Maintain a healthy routine. 保持健康的作息。", "main（手）+ tain（握）→ 一直握着", "Day 2"),
            Imported("obstacle", "/ˈɒbstəkl/", "n. 障碍", "Fear is the biggest obstacle. 恐惧是最大的障碍。", "ob（对着）+ sta（站）→ 挡在面前站着的东西", "Unit 2"),
            Imported("persuade", "/pəˈsweɪd/", "v. 说服", "I persuaded him to rest. 我劝他去休息。", "per（彻底）+ suade（甜言）→ 说动了", "Unit 2")
        )
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE words (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                word TEXT NOT NULL UNIQUE COLLATE NOCASE,
                phonetic TEXT DEFAULT '',
                meaning TEXT DEFAULT '',
                example TEXT DEFAULT '',
                note TEXT DEFAULT '',
                state INTEGER DEFAULT 0,
                streak INTEGER DEFAULT 0,
                right_count INTEGER DEFAULT 0,
                wrong_count INTEGER DEFAULT 0,
                next_review INTEGER DEFAULT 0,
                learned_day INTEGER DEFAULT 0,
                created_at INTEGER DEFAULT 0,
                group_name TEXT DEFAULT '',
                phonetic_uk TEXT DEFAULT '',
                phonetic_us TEXT DEFAULT ''
            )"""
        )
        db.execSQL("CREATE TABLE checkin (day INTEGER PRIMARY KEY, cnt INTEGER NOT NULL)")
    }

    /** 升级只 ALTER 加列：v1→v2 加 group_name，v2→v3 加英美音标。
     *  旧词库、学习进度、打卡记录、组别全部完整保留；从 v1 直接升 v3 会依次补齐。 */
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE words ADD COLUMN group_name TEXT DEFAULT ''")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE words ADD COLUMN phonetic_uk TEXT DEFAULT ''")
            db.execSQL("ALTER TABLE words ADD COLUMN phonetic_us TEXT DEFAULT ''")
        }
    }

    /** v5：默认口音偏好。0=美音 US，1=英音 UK。旧备份无此键时默认美音 */
    var accent: Int
        get() = prefs.getInt("accent", 0).coerceIn(0, 1)
        set(value) {
            prefs.edit().putInt("accent", value.coerceIn(0, 1)).apply()
        }

    /** v6：导入时自动用内置离线词典补齐缺失音标。默认开，可关 */
    var autoIpa: Boolean
        get() = prefs.getBoolean("auto_ipa", true)
        set(value) {
            prefs.edit().putBoolean("auto_ipa", value).apply()
        }

    /** v6：朗读语速，0.7 慢 / 1.0 正常 / 1.3 快 */
    var speechRate: Float
        get() = prefs.getFloat("speech_rate", 1.0f).coerceIn(0.6f, 1.6f)
        set(value) {
            prefs.edit().putFloat("speech_rate", value.coerceIn(0.6f, 1.6f)).apply()
        }

    var dailyGoal: Int
        get() = prefs.getInt("daily", 10).coerceIn(1, 100)
        set(value) {
            prefs.edit().putInt("daily", value.coerceIn(1, 100)).apply()
        }

    /** v1.6：学习顺序。false=按到期日 / id 顺序，true=每轮开始前打乱 */
    var shuffleOrder: Boolean
        get() = prefs.getBoolean("shuffle_order", false)
        set(value) {
            prefs.edit().putBoolean("shuffle_order", value).apply()
        }

    /** v1.6：学习范围。null=全部词组；空集合也按全部处理；空串表示「未分组」 */
    var studyGroups: Set<String>?
        get() = prefs.getStringSet("study_groups", null)?.toSet()
        set(value) {
            prefs.edit().apply {
                if (value == null || value.isEmpty()) remove("study_groups")
                else putStringSet("study_groups", value)
            }.apply()
        }

    private fun Cursor.readWord(): Word = Word(
        id = getLong(getColumnIndexOrThrow("id")),
        word = getString(getColumnIndexOrThrow("word")) ?: "",
        phonetic = getString(getColumnIndexOrThrow("phonetic")) ?: "",
        meaning = getString(getColumnIndexOrThrow("meaning")) ?: "",
        example = getString(getColumnIndexOrThrow("example")) ?: "",
        note = getString(getColumnIndexOrThrow("note")) ?: "",
        state = getInt(getColumnIndexOrThrow("state")),
        streak = getInt(getColumnIndexOrThrow("streak")),
        right = getInt(getColumnIndexOrThrow("right_count")),
        wrong = getInt(getColumnIndexOrThrow("wrong_count")),
        nextReview = getLong(getColumnIndexOrThrow("next_review")),
        learnedDay = getLong(getColumnIndexOrThrow("learned_day")),
        createdAt = getLong(getColumnIndexOrThrow("created_at")),
        groupName = getColumnIndex("group_name").let { i ->
            if (i >= 0) getString(i) ?: "" else ""
        },
        phoneticUk = getColumnIndex("phonetic_uk").let { i ->
            if (i >= 0) getString(i) ?: "" else ""
        },
        phoneticUs = getColumnIndex("phonetic_us").let { i ->
            if (i >= 0) getString(i) ?: "" else ""
        }
    )

    /** groupFilter：null=全部；""=未分组；其他=精确组名。前两个参数保持原签名 */
    fun getWords(stateFilter: Int = -1, keyword: String = "", groupFilter: String? = null): List<Word> {
        val where = StringBuilder("1=1")
        val args = mutableListOf<String>()
        if (stateFilter in 0..2) {
            where.append(" AND state=?")
            args.add(stateFilter.toString())
        }
        if (keyword.isNotBlank()) {
            where.append(" AND (word LIKE ? OR meaning LIKE ?)")
            args.add("%$keyword%")
            args.add("%$keyword%")
        }
        appendGroupWhere(where, args, groupFilter)
        val c = readableDatabase.query(
            "words", null, where.toString(), args.toTypedArray(),
            null, null, "id DESC"
        )
        return c.use { cur ->
            val out = mutableListOf<Word>()
            while (cur.moveToNext()) out.add(cur.readWord())
            out
        }
    }

    /** v1.10：词库页多组筛选。groups=null / 空集合 = 全部。 */
    fun getWordsForGroups(
        stateFilter: Int = -1,
        keyword: String = "",
        groups: Set<String>? = null
    ): List<Word> {
        val where = StringBuilder("1=1")
        val args = mutableListOf<String>()
        if (stateFilter in 0..2) {
            where.append(" AND state=?")
            args.add(stateFilter.toString())
        }
        if (keyword.isNotBlank()) {
            where.append(" AND (word LIKE ? OR meaning LIKE ?)")
            args.add("%$keyword%")
            args.add("%$keyword%")
        }
        appendGroupsWhere(where, args, groups)
        val c = readableDatabase.query(
            "words", null, where.toString(), args.toTypedArray(),
            null, null, "id DESC"
        )
        return c.use { cur ->
            val out = mutableListOf<Word>()
            while (cur.moveToNext()) out.add(cur.readWord())
            out
        }
    }

    private fun appendGroupWhere(
        where: StringBuilder,
        args: MutableList<String>,
        groupFilter: String?
    ) {
        if (groupFilter == null) return
        if (groupFilter.isEmpty()) {
            where.append(" AND (group_name IS NULL OR group_name='')")
        } else {
            where.append(" AND group_name=?")
            args.add(groupFilter)
        }
    }

    /** v1.6：多组筛选。null 或空集合 = 全部；集合里用空串表示「未分组」。 */
    private fun appendGroupsWhere(
        where: StringBuilder,
        args: MutableList<String>,
        groups: Set<String>?
    ) {
        if (groups.isNullOrEmpty()) return
        val names = groups.filter { it.isNotEmpty() }
        val clauses = mutableListOf<String>()
        if (names.isNotEmpty()) {
            clauses.add("group_name IN (" + names.joinToString(",") { "?" } + ")")
            args.addAll(names)
        }
        if (groups.contains("")) {
            clauses.add("(group_name IS NULL OR group_name='')")
        }
        if (clauses.isNotEmpty()) {
            where.append(" AND (").append(clauses.joinToString(" OR ")).append(")")
        }
    }

    fun getWord(id: Long): Word? {
        val c = readableDatabase.query("words", null, "id=?", arrayOf(id.toString()), null, null, null)
        return c.use { if (it.moveToFirst()) it.readWord() else null }
    }

    /** 按单词（大小写不敏感）查找，Obsidian 同步用 */
    fun getWordByWord(word: String): Word? {
        val c = readableDatabase.query(
            "words", null, "word=?", arrayOf(word.trim()), null, null, null
        )
        return c.use { if (it.moveToFirst()) it.readWord() else null }
    }

    /** 用 Obsidian 文件内容覆盖本机行（含学习进度），不存在则新建 */
    fun upsertFromVault(w: Word): Long {
        val existing = getWordByWord(w.word)
        val cv = ContentValues().apply {
            put("word", w.word.trim())
            put("phonetic", w.phonetic.trim())
            put("meaning", w.meaning.trim())
            put("example", w.example.trim())
            put("note", w.note.trim())
            put("group_name", w.groupName.trim())
            put("phonetic_uk", w.phoneticUk.trim())
            put("phonetic_us", w.phoneticUs.trim())
            put("state", w.state)
            put("streak", w.streak)
            put("right_count", w.right)
            put("wrong_count", w.wrong)
            put("next_review", w.nextReview)
            put("learned_day", w.learnedDay)
            put("created_at", if (w.createdAt > 0) w.createdAt else System.currentTimeMillis())
        }
        return if (existing != null) {
            writableDatabase.update("words", cv, "id=?", arrayOf(existing.id.toString()))
            existing.id
        } else {
            val id = writableDatabase.insertWithOnConflict(
                "words", null, cv, SQLiteDatabase.CONFLICT_IGNORE
            )
            if (id > 0) id else getWordByWord(w.word)?.id ?: 0
        }
    }

    fun insertWord(w: Word): Long {
        val cv = ContentValues().apply {
            put("word", w.word.trim())
            put("phonetic", w.phonetic.trim())
            put("meaning", w.meaning.trim())
            put("example", w.example.trim())
            put("note", w.note.trim())
            put("group_name", w.groupName.trim())
            put("phonetic_uk", w.phoneticUk.trim())
            put("phonetic_us", w.phoneticUs.trim())
            put("state", w.state)
            put("next_review", today())
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertWithOnConflict("words", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    fun updateWord(w: Word) {
        val cv = ContentValues().apply {
            put("word", w.word.trim())
            put("phonetic", w.phonetic.trim())
            put("meaning", w.meaning.trim())
            put("example", w.example.trim())
            put("note", w.note.trim())
            put("group_name", w.groupName.trim())
            put("phonetic_uk", w.phoneticUk.trim())
            put("phonetic_us", w.phoneticUs.trim())
        }
        writableDatabase.update("words", cv, "id=?", arrayOf(w.id.toString()))
    }

    fun deleteWord(id: Long) {
        val w = getWord(id)
        writableDatabase.delete("words", "id=?", arrayOf(id.toString()))
        if (w != null) VaultSync.deleteWord(appCtx, w.word)
    }

    fun setState(id: Long, state: Int) {
        val cv = ContentValues().apply {
            put("state", state)
            put("streak", if (state == 2) 3 else 0)
            put("next_review", today() + if (state == 2) 30 else 0)
        }
        writableDatabase.update("words", cv, "id=?", arrayOf(id.toString()))
    }

    fun resetWord(id: Long) = setState(id, 0)

    /** 从「未解锁」里放出一批新词，返回实际解锁数量；可选只解锁某个组 */
    fun unlockNew(limit: Int, groupFilter: String? = null): Int {
        val db = writableDatabase
        val ids = mutableListOf<Long>()
        val where = StringBuilder("state=0")
        val args = mutableListOf<String>()
        appendGroupWhere(where, args, groupFilter)
        db.rawQuery(
            "SELECT id FROM words WHERE $where ORDER BY id ASC LIMIT ?",
            (args + limit.coerceIn(1, 100).toString()).toTypedArray()
        ).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        val cv = ContentValues().apply { put("state", 1); put("next_review", today()) }
        ids.forEach { db.update("words", cv, "id=?", arrayOf(it.toString())) }
        return ids.size
    }

    /** v1.6：从多个组里解锁一批新词。groups=null / 空集合表示全部。 */
    fun unlockNewForGroups(limit: Int, groups: Set<String>? = null): Int {
        val db = writableDatabase
        val ids = mutableListOf<Long>()
        val where = StringBuilder("state=0")
        val args = mutableListOf<String>()
        appendGroupsWhere(where, args, groups)
        db.rawQuery(
            "SELECT id FROM words WHERE $where ORDER BY id ASC LIMIT ?",
            (args + limit.coerceIn(1, 100).toString()).toTypedArray()
        ).use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        val cv = ContentValues().apply { put("state", 1); put("next_review", today()) }
        ids.forEach { db.update("words", cv, "id=?", arrayOf(it.toString())) }
        return ids.size
    }

    /** 今天该学的词：已解锁且复习日已到；学习中的排前面。可按组缩小范围 */
    fun dueWords(limit: Int = 200, groupFilter: String? = null): List<Word> {
        val where = StringBuilder("state>0 AND next_review<=?")
        val args = mutableListOf<String>(today().toString())
        appendGroupWhere(where, args, groupFilter)
        args.add(limit.toString())
        val c = readableDatabase.rawQuery(
            """SELECT * FROM words WHERE $where
               ORDER BY CASE state WHEN 1 THEN 0 ELSE 1 END, next_review ASC, id ASC
               LIMIT ?""",
            args.toTypedArray()
        )
        return c.use { cur ->
            val out = mutableListOf<Word>()
            while (cur.moveToNext()) out.add(cur.readWord())
            out
        }
    }

    /** v1.6：多组一起取今天该学的词。groups=null / 空集合表示全部。 */
    fun dueWordsForGroups(limit: Int = 200, groups: Set<String>? = null): List<Word> {
        val where = StringBuilder("state>0 AND next_review<=?")
        val args = mutableListOf<String>(today().toString())
        appendGroupsWhere(where, args, groups)
        args.add(limit.toString())
        val c = readableDatabase.rawQuery(
            """SELECT * FROM words WHERE $where
               ORDER BY CASE state WHEN 1 THEN 0 ELSE 1 END, next_review ASC, id ASC
               LIMIT ?""",
            args.toTypedArray()
        )
        return c.use { cur ->
            val out = mutableListOf<Word>()
            while (cur.moveToNext()) out.add(cur.readWord())
            out
        }
    }

    /** grade：0 不认识 / 1 模糊 / 2 认识。间隔 1/2/4/7/15/30 天，连对 3 次变已掌握 */
    fun recordGrade(id: Long, grade: Int) {
        val w = getWord(id) ?: return
        val intervals = longArrayOf(0, 1, 2, 4, 7, 15, 30)
        val streak = when (grade) {
            2 -> w.streak + 1
            1 -> w.streak
            else -> 0
        }
        val gap = when (grade) {
            2 -> intervals[streak.coerceAtMost(intervals.size - 1)].coerceAtLeast(1)
            1 -> 1
            else -> 0
        }
        val state = if (grade == 2 && streak >= 3) 2 else 1
        val cv = ContentValues().apply {
            put("state", state)
            put("streak", streak)
            put("next_review", today() + gap)
            put("right_count", w.right + if (grade == 2) 1 else 0)
            put("wrong_count", w.wrong + if (grade == 0) 1 else 0)
            put("learned_day", today())
        }
        writableDatabase.update("words", cv, "id=?", arrayOf(id.toString()))
        bumpCheckin()
    }

    fun randomMeanings(excludeWord: String, limit: Int): List<String> {
        val c = readableDatabase.rawQuery(
            """SELECT meaning FROM words
               WHERE meaning<>'' AND lower(word)<>lower(?)
               ORDER BY RANDOM() LIMIT ?""",
            arrayOf(excludeWord, limit.toString())
        )
        return c.use { cur ->
            val out = mutableListOf<String>()
            while (cur.moveToNext()) out.add(cur.getString(0))
            out
        }
    }

    fun stats(): Stats {
        val db = readableDatabase
        fun one(sql: String): Int = db.rawQuery(sql, null).use { c ->
            if (c.moveToFirst()) c.getInt(0) else 0
        }
        fun dayCount(day: Long): Int = one("SELECT COALESCE(SUM(cnt),0) FROM checkin WHERE day=$day")

        val total = one("SELECT COUNT(*) FROM words")
        val locked = one("SELECT COUNT(*) FROM words WHERE state=0")
        val learning = one("SELECT COUNT(*) FROM words WHERE state=1")
        val mastered = one("SELECT COUNT(*) FROM words WHERE state=2")
        val right = one("SELECT COALESCE(SUM(right_count),0) FROM words")
        val wrong = one("SELECT COALESCE(SUM(wrong_count),0) FROM words")

        var streak = 0
        var day = if (dayCount(today()) > 0) today() else today() - 1
        while (dayCount(day) > 0 && streak < 3650) {
            streak++
            day--
        }

        val sdf = SimpleDateFormat("MM/dd", Locale.getDefault())
        val off = offset
        val week = mutableListOf<Pair<String, Int>>()
        for (i in 6 downTo 0) {
            val d = today() - i
            week.add(sdf.format(Date(d * DAY_MS - off)) to dayCount(d))
        }
        return Stats(total, locked, learning, mastered, learning + mastered,
            dayCount(today()), streak, right, wrong, week)
    }

    fun quizWords(groupFilter: String? = null): List<Word> =
        getWords(groupFilter = groupFilter).filter { it.meaning.isNotBlank() }

    /** v1.6：小测词池支持多组；groups=null / 空集合表示全部 */
    fun quizWordsForGroups(groups: Set<String>? = null): List<Word> =
        getWords().filter {
            it.meaning.isNotBlank() && (groups == null || groups.isEmpty() || it.groupName in groups)
        }

    // ---------- v3：划组管理 ----------

    /** 所有组（含「未分组」，用空串表示）。
     *  SQL 的 ORDER BY 是字典序（Unit 1, Unit 10, Unit 2），这里改成「前缀 + 数字」自然排序。 */
    fun groups(): List<GroupInfo> {
        val c = readableDatabase.rawQuery(
            """SELECT COALESCE(group_name,'') AS g, COUNT(*),
                      SUM(CASE WHEN state>0 THEN 1 ELSE 0 END),
                      SUM(CASE WHEN state=2 THEN 1 ELSE 0 END)
               FROM words GROUP BY g""",
            null
        )
        return c.use { cur ->
            val out = mutableListOf<GroupInfo>()
            while (cur.moveToNext()) {
                out.add(
                    GroupInfo(
                        cur.getString(0) ?: "",
                        cur.getInt(1),
                        cur.getInt(2),
                        cur.getInt(3)
                    )
                )
            }
            out.sortedWith(
                compareBy<GroupInfo> { groupSortKey(it.name).first }
                    .thenBy { groupSortKey(it.name).second }
                    .thenBy { it.name }
            )
        }
    }

    /** 「Unit 10」→ ("unit ", 10)；无数字的组 → (原名小写, Long.MAX_VALUE)，排在数字组之后 */
    private fun groupSortKey(name: String): Pair<String, Long> {
        if (name.isBlank()) return "" to -1L // 「未分组」永远排最前
        val m = Regex("""^(.*?)(\d+)\s*$""").find(name)
        return if (m != null) {
            m.groupValues[1].lowercase() to m.groupValues[2].toLong()
        } else {
            name.lowercase() to Long.MAX_VALUE
        }
    }

    fun groupLabel(name: String): String = if (name.isBlank()) "未分组" else name

    /** 重命名组；old 为空串表示「未分组」 */
    fun renameGroup(old: String, newName: String): Int {
        val db = writableDatabase
        val cv = ContentValues().apply { put("group_name", newName.trim()) }
        return if (old.isEmpty()) {
            db.update("words", cv, "group_name IS NULL OR group_name=''", null)
        } else {
            db.update("words", cv, "group_name=?", arrayOf(old))
        }
    }

    /** 把整个词库按 id 顺序每 size 个划一组，组名 = "prefix 序号"，返回组数。覆盖已有组名 */
    fun assignSequentialGroups(prefix: String, size: Int): Int {
        val step = size.coerceIn(1, 200)
        val head = prefix.trim().ifEmpty { "Unit" }
        val ids = mutableListOf<Long>()
        readableDatabase.rawQuery("SELECT id FROM words ORDER BY id ASC", null)
            .use { c -> while (c.moveToNext()) ids.add(c.getLong(0)) }
        val db = writableDatabase
        db.beginTransaction()
        try {
            ids.forEachIndexed { i, id ->
                val cv = ContentValues().apply { put("group_name", "$head ${i / step + 1}") }
                db.update("words", cv, "id=?", arrayOf(id.toString()))
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return (ids.size + step - 1) / step
    }

    /** v6：给词库里「三个音标字段全空」的词批量补离线词典里的 UK / US 音标。
     *  只填空字段，绝不覆盖用户手写的音标；查不到的词原样留空。返回补全的词数。 */
    fun fillMissingPhonetics(): Int {
        data class Row(
            val id: Long, val word: String,
            val main: String, val uk: String, val us: String
        )
        val targets = mutableListOf<Row>()
        readableDatabase.rawQuery(
            "SELECT id, word, phonetic, phonetic_uk, phonetic_us FROM words",
            null
        ).use { c ->
            while (c.moveToNext()) {
                targets.add(
                    Row(
                        c.getLong(0), c.getString(1) ?: "",
                        c.getString(2) ?: "", c.getString(3) ?: "", c.getString(4) ?: ""
                    )
                )
            }
        }
        if (targets.isEmpty()) return 0
        val db = writableDatabase
        var filled = 0
        db.beginTransaction()
        try {
            for (r in targets) {
                val ipa = IpaDictionary.lookup(appCtx, r.word) ?: continue
                if (!ipa.hasAny()) continue
                val uk = if (r.uk.isBlank()) ipa.uk else r.uk
                val us = if (r.us.isBlank()) ipa.us else r.us
                val main = if (r.main.isBlank()) (uk.ifEmpty { us }) else r.main
                val cv = ContentValues()
                if (main != r.main) cv.put("phonetic", main)
                if (uk != r.uk) cv.put("phonetic_uk", uk)
                if (us != r.us) cv.put("phonetic_us", us)
                if (cv.size() == 0) continue
                db.update("words", cv, "id=?", arrayOf(r.id.toString()))
                filled++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return filled
    }

    /** 导入去重：不覆盖已有词条的学习记录和手工编辑 */
    fun importWords(items: List<Imported>): Int {
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (it in items) {
                val w = it.word.trim()
                if (w.isEmpty() || w.length > 60) continue
                val cv = ContentValues().apply {
                    put("word", w)
                    put("phonetic", it.phonetic.trim())
                    put("meaning", it.meaning.trim())
                    put("example", it.example.trim())
                    put("note", it.note.trim())
                    put("group_name", it.group.trim())
                    put("phonetic_uk", it.phoneticUk.trim())
                    put("phonetic_us", it.phoneticUs.trim())
                    put("state", 0)
                    put("next_review", today())
                    put("created_at", System.currentTimeMillis())
                }
                val row = db.insertWithOnConflict("words", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
                if (row > 0) {
                    added++
                } else {
                    // 只补空字段，不动用户已填的内容和学习进度
                    db.execSQL(
                        """UPDATE words SET
                             phonetic   = CASE WHEN phonetic=''   THEN ? ELSE phonetic   END,
                             meaning    = CASE WHEN meaning=''    THEN ? ELSE meaning    END,
                             example    = CASE WHEN example=''    THEN ? ELSE example    END,
                             note       = CASE WHEN note=''       THEN ? ELSE note       END,
                             group_name = CASE WHEN group_name='' THEN ? ELSE group_name END,
                             phonetic_uk = CASE WHEN phonetic_uk='' THEN ? ELSE phonetic_uk END,
                             phonetic_us = CASE WHEN phonetic_us='' THEN ? ELSE phonetic_us END
                           WHERE lower(word)=lower(?)""",
                        arrayOf<Any>(
                            it.phonetic.trim(), it.meaning.trim(), it.example.trim(),
                            it.note.trim(), it.group.trim(),
                            it.phoneticUk.trim(), it.phoneticUs.trim(), w
                        )
                    )
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    fun loadDemo(): Int = importWords(DEMO)

    fun clearAll() {
        val db = writableDatabase
        db.execSQL("DELETE FROM words")
        db.execSQL("DELETE FROM checkin")
        VaultSync.clearAll(appCtx)
    }

    fun resetProgress() {
        val db = writableDatabase
        db.execSQL(
            """UPDATE words SET state=0, streak=0, right_count=0, wrong_count=0,
               next_review=?, learned_day=0""",
            arrayOf<Any>(today())
        )
        db.execSQL("DELETE FROM checkin")
    }

    /** v3 新增 group、v5 新增 phonetic_uk / phonetic_us；version 仍为 1，
     *  旧版解析器忽略多出来的字段，旧备份缺字段时按空值恢复。 */
    fun exportJson(): String {
        val arr = JSONArray()
        getWords().forEach { w ->
            arr.put(JSONObject().apply {
                put("word", w.word)
                put("phonetic", w.phonetic)
                put("meaning", w.meaning)
                put("example", w.example)
                put("note", w.note)
                put("state", w.state)
                put("streak", w.streak)
                put("right", w.right)
                put("wrong", w.wrong)
                put("next_review", w.nextReview)
                put("group", w.groupName)
                put("phonetic_uk", w.phoneticUk)
                put("phonetic_us", w.phoneticUs)
            })
        }
        return JSONObject().apply {
            put("app", "单词酷")
            put("version", 1)
            put("exported_at", System.currentTimeMillis())
            put("words", arr)
        }.toString(2)
    }

    /** 恢复备份；旧备份没有 group / 英美音标字段时按空值处理 */
    fun importBackup(json: String): Int {
        val arr = JSONObject(json).optJSONArray("words") ?: return 0
        val db = writableDatabase
        var added = 0
        db.beginTransaction()
        try {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val w = o.optString("word").trim()
                if (w.isEmpty()) continue
                val cv = ContentValues().apply {
                    put("word", w)
                    put("phonetic", o.optString("phonetic"))
                    put("meaning", o.optString("meaning"))
                    put("example", o.optString("example"))
                    put("note", o.optString("note"))
                    put("state", o.optInt("state", 0))
                    put("streak", o.optInt("streak", 0))
                    put("right_count", o.optInt("right", 0))
                    put("wrong_count", o.optInt("wrong", 0))
                    put("next_review", o.optLong("next_review", today()))
                    put("group_name", o.optString("group", ""))
                    put("phonetic_uk", o.optString("phonetic_uk", ""))
                    put("phonetic_us", o.optString("phonetic_us", ""))
                    put("created_at", System.currentTimeMillis())
                }
                if (db.insertWithOnConflict("words", null, cv, SQLiteDatabase.CONFLICT_IGNORE) > 0) added++
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return added
    }

    private fun bumpCheckin() {
        val db = writableDatabase
        val day = today()
        val exists = db.rawQuery("SELECT cnt FROM checkin WHERE day=?", arrayOf(day.toString()))
            .use { it.moveToFirst() }
        if (exists) {
            db.execSQL("UPDATE checkin SET cnt=cnt+1 WHERE day=?", arrayOf<Any>(day))
        } else {
            db.execSQL("INSERT INTO checkin(day,cnt) VALUES(?,1)", arrayOf<Any>(day))
        }
    }
}
