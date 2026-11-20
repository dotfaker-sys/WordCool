package com.wordcool.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity() {

    lateinit var db: WordDb

    private lateinit var content: FrameLayout
    private lateinit var badge: TextView
    private val tabs = mutableListOf<TextView>()
    private val pages = mutableListOf<View>()
    private lateinit var library: LibraryView
    private lateinit var learn: LearnView
    private lateinit var statsPage: StatsView
    private lateinit var importPage: ImportView
    private var current = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ukAvailable = false
    private var usAvailable = false
    private var pendingExport = ""

    companion object {
        private const val REQ_PICK = 1001
        private const val REQ_EXPORT = 1002
        const val ACCENT_US = 0
        const val ACCENT_UK = 1
        private val LOC_US = Locale.US
        private val LOC_UK = Locale.UK
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        db = WordDb(this)

        content = findViewById(R.id.content)
        badge = findViewById(R.id.headerBadge)

        val inflater = LayoutInflater.from(this)
        val p0 = inflater.inflate(R.layout.page_library, content, false)
        val p1 = inflater.inflate(R.layout.page_learn, content, false)
        val p2 = inflater.inflate(R.layout.page_stats, content, false)
        val p3 = inflater.inflate(R.layout.page_import, content, false)
        listOf(p0, p1, p2, p3).forEach {
            it.visibility = View.GONE
            content.addView(it)
            pages.add(it)
        }

        library = LibraryView(this, p0)
        learn = LearnView(this, p1)
        statsPage = StatsView(this, p2)
        importPage = ImportView(this, p3)

        tabs.add(findViewById<TextView>(R.id.tabLibrary))
        tabs.add(findViewById<TextView>(R.id.tabLearn))
        tabs.add(findViewById<TextView>(R.id.tabStats))
        tabs.add(findViewById<TextView>(R.id.tabImport))
        tabs.forEachIndexed { i, t -> t.setOnClickListener { switchTo(i) } }

        initTts()
        switchTo(0)
    }

    override fun onResume() {
        super.onResume()
        if (ttsReady) refreshVoiceAvailability()
        if (::library.isInitialized) refreshCurrent()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    fun switchTo(index: Int) {
        current = index
        pages.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, t ->
            val on = i == index
            t.setTextColor(if (on) Pal.primary else Pal.textSub)
            t.background = if (on) rounded(Pal.softBlue, 10f) else null
        }
        refreshCurrent()
    }

    private fun refreshCurrent() {
        val s = db.stats()
        badge.text = "已解锁 ${s.unlocked} / ${s.total}"
        badge.setTextColor(Pal.primary)
        badge.background = rounded(Pal.softBlue, 18f)
        when (current) {
            0 -> library.refresh()
            1 -> learn.refresh()
            2 -> statsPage.refresh()
            3 -> importPage.refresh()
        }
    }

    /** 任何数据改动后统一刷新顶栏和当前页 */
    fun dataChanged() = refreshCurrent()

    private fun initTts() {
        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    installTtsListener(tts)
                    ttsReady = true
                    refreshVoiceAvailability()
                } else {
                    ttsReady = false
                }
            }
        } catch (e: Exception) {
            ttsReady = false
        }
    }

    /** v6：以前点了没声音只能干瞪眼，现在把系统给的错误码翻成人话。
     *  同时在这里统一设语速，避免切口音后语速被引擎重置。 */
    private fun installTtsListener(engine: TextToSpeech?) {
        val e = engine ?: return
        try {
            e.setSpeechRate(db.speechRate)
            e.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {}

                @Suppress("DEPRECATION")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { this@MainActivity.toast("朗读没播放，检查系统文字转语音设置") }
                }

                @Suppress("DEPRECATION")
                override fun onError(utteranceId: String?, errorCode: Int) {
                    runOnUiThread { this@MainActivity.toast(ttsErrorMessage(errorCode)) }
                }
            })
        } catch (ignored: Exception) {
            // 少数引擎不实现监听器，不影响朗读本身
        }
    }

    private fun ttsErrorMessage(code: Int): String = when (code) {
        TextToSpeech.ERROR_SYNTHESIS -> "语音合成失败，换一个系统 TTS 引擎试试"
        TextToSpeech.ERROR_SERVICE -> "语音服务出错，重启一下 App"
        TextToSpeech.ERROR_OUTPUT -> "音频输出失败：检查音量、静音或蓝牙耳机"
        TextToSpeech.ERROR_NETWORK, TextToSpeech.ERROR_NETWORK_TIMEOUT ->
            "这个语音需要联网，本应用只使用离线语音包"
        TextToSpeech.ERROR_INVALID_REQUEST -> "朗读请求被引擎拒绝"
        TextToSpeech.ERROR_NOT_INSTALLED_YET -> "语音数据还在安装，装完再点"
        else -> "朗读失败（错误码 $code），到导入页「发音与音标」检查语音包"
    }

    /** 逐个探测英 / 美口音是否真的可用，最后恢复用户选的默认口音。
     *  `voices` 里列得出来不等于能用：缺语音数据、需联网的 voice 会被静默回退，必须过滤。 */
    private fun refreshVoiceAvailability() {
        val engine = tts ?: return
        ukAvailable = isAccentAvailable(engine, LOC_UK)
        usAvailable = isAccentAvailable(engine, LOC_US)
        applyAccent(db.accent)
    }

    private fun isAccentAvailable(engine: TextToSpeech, locale: Locale): Boolean {
        val lang = try {
            engine.isLanguageAvailable(locale)
        } catch (e: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        if (lang == TextToSpeech.LANG_MISSING_DATA ||
            lang == TextToSpeech.LANG_NOT_SUPPORTED
        ) return false
        val voices = try {
            engine.voices
        } catch (e: Exception) {
            null
        }
        if (voices != null) {
            return voices.any { v ->
                v.locale.language.equals("en", true) &&
                    v.locale.country.equals(locale.country, true) &&
                    !v.isNetworkConnectionRequired &&
                    v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true &&
                    v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NETWORK_SERVICE_REQUIRED) != true
            }
        }
        return true // 老引擎不提供 voices 列表，setLanguage 通过即认为可用
    }

    /** 优先精确设置本地 voice（比 setLanguage 更能保证口音），失败退回 setLanguage */
    private fun applyAccent(accent: Int): Boolean {
        val engine = tts ?: return false
        val locale = if (accent == ACCENT_UK) LOC_UK else LOC_US
        val voices = try {
            engine.voices
        } catch (e: Exception) {
            null
        }
        val pick = voices?.firstOrNull { v ->
            v.locale.language.equals("en", true) &&
                v.locale.country.equals(locale.country, true) &&
                !v.isNetworkConnectionRequired &&
                v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
        }
        if (pick != null && engine.setVoice(pick) == TextToSpeech.SUCCESS) return true
        val r = try {
            engine.setLanguage(locale)
        } catch (e: Exception) {
            TextToSpeech.LANG_NOT_SUPPORTED
        }
        return r != TextToSpeech.LANG_MISSING_DATA && r != TextToSpeech.LANG_NOT_SUPPORTED
    }

    /** 旧签名保留：按当前默认口音朗读 */
    fun speak(text: String) = speak(text, db.accent)

    /** v5：指定口音朗读。缺该口音语音包时自动回退到另一种，两个都没有则引导安装 */
    fun speak(text: String, accent: Int) {
        if (text.isBlank()) return
        if (!ttsReady || tts == null) {
            toast("语音引擎还在准备，稍等一下再点")
            return
        }
        val ok = if (accent == ACCENT_UK) ukAvailable else usAvailable
        if (ok && applyAccent(accent)) {
            tts?.setSpeechRate(db.speechRate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "wordcool")
            return
        }
        val fallback = if (accent == ACCENT_UK) ACCENT_US else ACCENT_UK
        val fallbackOk = if (fallback == ACCENT_UK) ukAvailable else usAvailable
        if (fallbackOk && applyAccent(fallback)) {
            toast("这台手机没有${accentName(accent)}语音包，已用${accentName(fallback)}朗读；可在系统「文字转语音」里安装")
            tts?.setSpeechRate(db.speechRate)
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle(), "wordcool")
            return
        }
        toast("还没装英语语音包：系统设置 → 语言和输入 → 文字转语音 → 安装英语（美国 / 英国）语音数据")
    }

    fun accentName(accent: Int): String = if (accent == ACCENT_UK) "英音" else "美音"

    fun accentLabel(accent: Int): String = if (accent == ACCENT_UK) "UK 英音" else "US 美音"

    /** 给导入页「发音」设置区显示当前引擎状态 */
    fun ttsStatusText(): String {
        if (!ttsReady) return "语音引擎还在准备…"
        val uk = if (ukAvailable) "英音可用" else "英音未安装"
        val us = if (usAvailable) "美音可用" else "美音未安装"
        return "$uk · $us · 默认${accentName(db.accent)} · 语速${speechRateLabel()}"
    }

    fun speechRateLabel(): String = when (db.speechRate) {
        0.7f -> "慢"
        1.3f -> "快"
        else -> "正常"
    }

    fun ipaDictStatusText(): String = if (IpaDictionary.isAvailable(this)) {
        "内置离线音标词典已就绪 · ${IpaDictionary.entryCount(this)} 条"
    } else {
        "内置离线音标词典读取失败"
    }

    fun setAccent(accent: Int) {
        db.accent = accent
        if (ttsReady) applyAccent(accent)
        dataChanged()
    }

    fun setSpeechRate(rate: Float) {
        db.speechRate = rate
        tts?.setSpeechRate(db.speechRate)
        dataChanged()
    }

    fun setAutoIpa(on: Boolean) {
        db.autoIpa = on
        dataChanged()
    }

    /** 用一句固定英文试听，立刻确认这台手机到底出不出声 */
    fun speakSample() = speak("Hello, this is a pronunciation test.", db.accent)

    /** 给已导入但没音标的老词批量补一次 */
    fun fillAllPhonetics() {
        val n = db.fillMissingPhonetics()
        toast(if (n > 0) "给 $n 个词补上了英 / 美音标" else "没有需要补的词，或词典里没收录")
        dataChanged()
    }

    /** 跳到系统 TTS 数据安装页；回来后 onResume 会重新探测 */
    fun openTtsSettings() {
        try {
            startActivity(Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        } catch (e: Exception) {
            try {
                startActivity(Intent("com.android.settings.TTS_SETTINGS"))
            } catch (e2: Exception) {
                toast("请到系统设置 → 语言和输入 → 文字转语音，安装英语语音数据")
            }
        }
    }

    // ---------- 词条编辑 / 详情 ----------

    fun showWordDialog(word: Word?) {
        val existing = word
        val v = layoutInflater.inflate(R.layout.dialog_word, null)
        val etWord = v.findViewById<EditText>(R.id.etWord)
        val etPhonetic = v.findViewById<EditText>(R.id.etPhonetic)
        val etPhoneticUk = v.findViewById<EditText>(R.id.etPhoneticUk)
        val etPhoneticUs = v.findViewById<EditText>(R.id.etPhoneticUs)
        val etMeaning = v.findViewById<EditText>(R.id.etMeaning)
        val etExample = v.findViewById<EditText>(R.id.etExample)
        val etNote = v.findViewById<EditText>(R.id.etNote)
        val etGroup = v.findViewById<EditText>(R.id.etGroup)
        val tvStats = v.findViewById<TextView>(R.id.tvDialogStats)

        if (existing != null) {
            etWord.setText(existing.word)
            etPhonetic.setText(existing.phonetic)
            etPhoneticUk.setText(existing.phoneticUk)
            etPhoneticUs.setText(existing.phoneticUs)
            etMeaning.setText(existing.meaning)
            etExample.setText(existing.example)
            etNote.setText(existing.note)
            etGroup.setText(existing.groupName)
            val left = existing.nextReview - WordDb.today()
            tvStats.text = "${stateName(existing.state)} · ${db.groupLabel(existing.groupName)} · " +
                "连对 ${existing.streak} 次 · 答对 ${existing.right} / 答错 ${existing.wrong}\n" +
                (if (left > 0) "下次复习：$left 天后" else "今天该复习")
        } else {
            tvStats.text = "新单词会进入「未解锁」，去学习页解锁后开始记。"
        }

        fun currentWordOrWarn(): String {
            val t = etWord.text.toString().trim()
            if (t.isEmpty()) toast("先填一个单词")
            return t
        }
        v.findViewById<TextView>(R.id.btnDialogSpeak).setOnClickListener {
            val t = currentWordOrWarn()
            if (t.isNotEmpty()) speak(t)
        }
        v.findViewById<TextView>(R.id.btnDialogSpeakUk).setOnClickListener {
            val t = currentWordOrWarn()
            if (t.isNotEmpty()) speak(t, ACCENT_UK)
        }
        v.findViewById<TextView>(R.id.btnDialogSpeakUs).setOnClickListener {
            val t = currentWordOrWarn()
            if (t.isNotEmpty()) speak(t, ACCENT_US)
        }
        v.findViewById<TextView>(R.id.btnDialogIpa).setOnClickListener {
            val t = etWord.text.toString().trim()
            if (t.isEmpty()) {
                toast("先填一个单词")
                return@setOnClickListener
            }
            val ipa = IpaDictionary.lookup(this, t)
            if (ipa == null || !ipa.hasAny()) {
                toast("内置词典里没收录「$t」，可以手动填")
                return@setOnClickListener
            }
            var changed = 0
            if (etPhonetic.text.isBlank()) {
                etPhonetic.setText(ipa.uk.ifEmpty { ipa.us }); changed++
            }
            if (etPhoneticUk.text.isBlank() && ipa.uk.isNotEmpty()) {
                etPhoneticUk.setText(ipa.uk); changed++
            }
            if (etPhoneticUs.text.isBlank() && ipa.us.isNotEmpty()) {
                etPhoneticUs.setText(ipa.us); changed++
            }
            toast(if (changed > 0) "补了 $changed 处，确认无误后点保存" else "三个框都已有内容，没覆盖你写的")
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(if (existing == null) "添加单词" else "编辑 / 查看")
            .setView(v)
            .setNegativeButton("取消", null)
            .setPositiveButton(if (existing == null) "添加" else "保存") { _, _ ->
                saveWord(existing, v)
            }
        if (existing != null) {
            builder.setNeutralButton("删除") { _, _ -> existing?.let { confirmDelete(it) } }
        }
        val dlg = builder.create()

        val btnMastered = v.findViewById<TextView>(R.id.btnDialogMastered)
        val btnReset = v.findViewById<TextView>(R.id.btnDialogReset)
        btnMastered.isEnabled = existing != null
        btnReset.isEnabled = existing != null
        btnMastered.setOnClickListener {
            existing?.let {
                db.setState(it.id, 2)
                dlg.dismiss()
                toast("已标记为「已掌握」")
                dataChanged()
            }
        }
        btnReset.setOnClickListener {
            existing?.let {
                db.resetWord(it.id)
                dlg.dismiss()
                toast("已放回「未解锁」")
                dataChanged()
            }
        }
        dlg.show()
    }

    private fun saveWord(existing: Word?, v: View) {
        val w = existing?.copy() ?: Word()
        w.word = v.findViewById<EditText>(R.id.etWord).text.toString().trim()
        w.phonetic = v.findViewById<EditText>(R.id.etPhonetic).text.toString().trim()
        w.phoneticUk = v.findViewById<EditText>(R.id.etPhoneticUk).text.toString().trim()
        w.phoneticUs = v.findViewById<EditText>(R.id.etPhoneticUs).text.toString().trim()
        w.meaning = v.findViewById<EditText>(R.id.etMeaning).text.toString().trim()
        w.example = v.findViewById<EditText>(R.id.etExample).text.toString().trim()
        w.note = v.findViewById<EditText>(R.id.etNote).text.toString().trim()
        w.groupName = v.findViewById<EditText>(R.id.etGroup).text.toString().trim()
        if (w.word.isBlank()) {
            toast("单词不能为空")
            return
        }
        try {
            if (existing == null) {
                val row = db.insertWord(w)
                toast(if (row > 0) "已添加，去学习页解锁吧" else "这个词已经在词库里了")
            } else {
                db.updateWord(w)
                toast("已保存")
            }
            dataChanged()
        } catch (e: Exception) {
            toast("保存失败：${e.message}")
        }
    }

    private fun confirmDelete(w: Word) {
        AlertDialog.Builder(this)
            .setTitle("删除 ${w.word}？")
            .setMessage("会连同学习记录一起删除，无法撤销。")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                db.deleteWord(w.id)
                toast("已删除")
                dataChanged()
            }.show()
    }

    // ---------- 文件导入 / 备份导出 ----------

    fun pickDocument() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    "application/pdf",
                    "application/msword",
                    "application/vnd.ms-excel",
                    "text/plain",
                    "text/csv",
                    "application/json",
                    "application/octet-stream"
                )
            )
        }
        try {
            startActivityForResult(i, REQ_PICK)
        } catch (e: Exception) {
            toast("打不开文件选择器：${e.message}")
        }
    }

    fun exportBackup() {
        pendingExport = db.exportJson()
        val name = "单词酷备份_" +
            SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date()) + ".json"
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, name)
        }
        try {
            startActivityForResult(i, REQ_EXPORT)
        } catch (e: Exception) {
            toast("打不开保存对话框：${e.message}")
        }
    }

    /** v7：按同行做法改成「逐字段只补空」。
     *  文档里写了一个通用音标时保留它，同时把缺的 UK / US 两列补上；
     *  用户手填、文档自带的双音标一律不覆盖。 */
    private fun autofillPhonetics(list: List<Imported>): Pair<List<Imported>, Int> {
        if (!db.autoIpa || !IpaDictionary.isAvailable(this)) return list to 0
        var changed = 0
        val out = list.map { item ->
            val ipa = IpaDictionary.lookup(this, item.word) ?: return@map item
            if (!ipa.hasAny()) return@map item
            val uk = if (item.phoneticUk.isBlank()) ipa.uk else item.phoneticUk
            val us = if (item.phoneticUs.isBlank()) ipa.us else item.phoneticUs
            val main = if (item.phonetic.isBlank()) (uk.ifEmpty { us }) else item.phonetic
            if (uk != item.phoneticUk || us != item.phoneticUs || main != item.phonetic) changed++
            item.copy(phonetic = main, phoneticUk = uk, phoneticUs = us)
        }
        return out to changed
    }

    /** 供「粘贴导入」与文件导入复用同一套预览确认流程。
     *  filledHint：改组后重进预览时沿用首次的补全计数，避免显示成 0。 */
    fun showImportPreview(rawItems: List<Imported>, source: String, filledHint: Int? = null) {
        if (rawItems.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("没识别到单词")
                .setMessage(
                    "文档里没找到「单词 + 释义」格式的行。\n\n支持的写法：\n" +
                        "· abandon /əˈbændən/ 放弃\n· benefit - 益处\n" +
                        "· Word 两列表格 / Excel 两列\n· 每行一个纯单词\n" +
                        "· Unit 1 / Day 2 标题行会自动划组\n\n" +
                        "扫描版 PDF 没有文字层，请复制内容用「粘贴导入」。"
                )
                .setPositiveButton("知道了", null)
                .show()
            return
        }
        val (items, ipaFilledScanned) = autofillPhonetics(rawItems)
        val ipaFilled = filledHint ?: ipaFilledScanned
        // 补全后仍然三列全空的，就是这份离线词典没收录的词：显式列出来，不假装覆盖 100%
        val missing = items.filter {
            it.phonetic.isBlank() && it.phoneticUk.isBlank() && it.phoneticUs.isBlank()
        }.map { it.word }
        val v = layoutInflater.inflate(R.layout.dialog_text, null)
        val et = v.findViewById<EditText>(R.id.etBig)
        et.isEnabled = false

        fun render(list: List<Imported>) {
            val groups = list.groupBy { it.group.ifBlank { "未分组" } }
            val groupLine = groups.entries.joinToString("\n") { "· ${it.key}：${it.value.size} 个" }
            val sample = list.take(8).joinToString("\n") {
                val g = if (it.group.isBlank()) "" else "[${it.group}] "
                val ph = when {
                    it.phoneticUk.isNotBlank() && it.phoneticUs.isNotBlank() ->
                        "UK ${it.phoneticUk} US ${it.phoneticUs}"
                    else -> it.phonetic
                }
                "$g${it.word}  $ph  ${it.meaning}".trim()
            }
            val ipaLine = if (db.autoIpa && IpaDictionary.isAvailable(this@MainActivity)) {
                "\n音标：自动补全 $ipaFilled / ${list.size} 个（离线词典）"
            } else ""
            val missLine = if (missing.isEmpty()) {
                "\n未命中：无，这批词都查到了音标"
            } else {
                "\n未命中 ${missing.size} 个（词典没收录，留空不编造）：" +
                    missing.take(20).joinToString("、") +
                    if (missing.size > 20) " … 等" else ""
            }
            et.setText(
                "识别到 ${list.size} 个单词，自动划成 ${groups.size} 组：$ipaLine$missLine\n" +
                    "$groupLine\n\n预览前 ${minOf(8, list.size)} 条：\n$sample\n\n…"
            )
        }
        render(items)
        val tvHint = v.findViewById<TextView>(R.id.tvHint)
        tvHint.text =
            "来源：$source\n已存在的词不会被覆盖，只补空字段；组名导入后还能改。\n" +
                "音标来自随包内置的开源离线词典，不需要联网，也可在导入页关掉自动补全。" +
                if (missing.isNotEmpty()) "\n\n点这里复制未命中词清单（${missing.size} 个）" else ""
        if (missing.isNotEmpty()) {
            tvHint.setOnClickListener {
                val cm = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(
                    android.content.ClipData.newPlainText(
                        "单词酷未命中词", missing.joinToString("\n")
                    )
                )
                toast("已复制 ${missing.size} 个未命中词，可去别处查音标")
            }
        }

        AlertDialog.Builder(this)
            .setTitle("确认导入")
            .setView(v)
            .setNegativeButton("取消", null)
            .setNeutralButton("统一改组") { _, _ ->
                showGroupNameInput(
                    "把这批词统一设为",
                    items.firstOrNull()?.group ?: "",
                    "例如 Unit 1 / Day 2 / Unit 1 Day 1"
                ) { name ->
                    showImportPreview(items.map { it.copy(group = name) }, source, ipaFilled)
                }
            }
            .setPositiveButton("导入 ${items.size} 个") { _, _ ->
                val added = db.importWords(items)
                toast("导入完成：新增 $added 个，跳过或补全 ${items.size - added} 个")
                dataChanged()
            }.show()
    }

    /** 通用单行输入框：组名、统一改组、重命名都复用 */
    fun showGroupNameInput(title: String, initial: String, hint: String, onDone: (String) -> Unit) {
        val v = layoutInflater.inflate(R.layout.dialog_input, null)
        val et = v.findViewById<EditText>(R.id.etBig)
        et.setText(initial)
        et.hint = hint
        et.setSelection(et.text.length)
        v.findViewById<TextView>(R.id.tvHint).text = "留空表示「未分组」。"
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(v)
            .setNegativeButton("取消", null)
            .setPositiveButton("确定") { _, _ -> onDone(et.text.toString().trim()) }
            .show()
    }

    /** 词库页 / 学习页共用的组别筛选器；回调 null 表示「全部词组」 */
    fun showGroupPicker(current: String?, onPick: (String?) -> Unit) {
        val gs = db.groups()
        val labels = mutableListOf<String>()
        val values = mutableListOf<String?>()
        labels.add(if (gs.isEmpty()) "全部词组" else "全部词组 · ${gs.sumOf { it.total }} 词")
        values.add(null)
        gs.forEach { g ->
            labels.add("${db.groupLabel(g.name)} · ${g.total} 词 · 已解锁 ${g.unlocked}")
            values.add(g.name)
        }
        AlertDialog.Builder(this)
            .setTitle("选择组别")
            .setItems(labels.toTypedArray()) { _, i -> onPick(values[i]) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun importFromUri(uri: Uri) {
        try {
            val name = displayName(uri) ?: "文档"
            val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IllegalArgumentException("打不开这个文件")

            val legacy = (name.endsWith(".doc", true) || name.endsWith(".xls", true)) &&
                !DocxParser.isZip(bytes) && !DocxParser.isPdf(bytes)
            if (legacy) {
                toast("旧版 .doc / .xls 读不了，请另存为 .docx / .xlsx，或用「粘贴导入」")
                return
            }

            val text = String(bytes, Charsets.UTF_8).trimStart()
            // 只有纯文本文件才按内容猜 JSON，避免 docx/xlsx/pdf 的二进制流被误判成备份
            val plainText = !DocxParser.isZip(bytes) && !DocxParser.isPdf(bytes)
            if (name.endsWith(".json", true) ||
                (plainText && text.startsWith("{") && text.contains("\"words\""))
            ) {
                try {
                    val n = db.importBackup(text)
                    toast("已从备份恢复 $n 个单词")
                    dataChanged()
                    return
                } catch (e: Exception) {
                    // 不是本应用的备份，按普通文本继续处理
                }
            }

            showImportPreview(DocxParser.parseBytes(bytes, name), name)
        } catch (e: Exception) {
            AlertDialog.Builder(this)
                .setTitle("读取失败")
                .setMessage(
                    "${e.message}\n\n建议：Word 另存为 .docx、Excel 另存为 .xlsx；" +
                        "扫描版 PDF 请复制内容用「粘贴导入」。"
                )
                .setPositiveButton("知道了", null)
                .show()
        }
    }

    private fun displayName(uri: Uri): String? {
        val c = contentResolver.query(uri, null, null, null, null) ?: return null
        return c.use { cur ->
            if (cur.moveToFirst()) {
                val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0) cur.getString(i) else null
            } else null
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        if (resultCode != RESULT_OK || result == null) return
        val uri = result.data ?: return
        when (requestCode) {
            REQ_PICK -> importFromUri(uri)
            REQ_EXPORT -> {
                try {
                    contentResolver.openOutputStream(uri)?.use {
                        it.write(pendingExport.toByteArray(Charsets.UTF_8))
                    }
                    toast("备份已保存")
                } catch (e: Exception) {
                    toast("保存失败：${e.message}")
                }
            }
        }
    }
}
