package com.wordcool.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.LayerDrawable
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.view.ViewGroup
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
    private lateinit var settingsPage: SettingsView
    private var current = 0

    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var ukAvailable = false
    private var usAvailable = false
    private var pendingExport = ""
    private var lastIncomingKey: String? = null
    private var bundledAccentNotified = false

    companion object {
        private const val REQ_PICK = 1001
        private const val REQ_EXPORT = 1002
        private const val REQ_VAULT = 1003
        private const val STATE_TAB = "state_tab"
        const val ACCENT_US = 0
        const val ACCENT_UK = 1
        private val LOC_US = Locale.US
        private val LOC_UK = Locale.UK
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(ThemePrefs.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setTheme(R.style.AppTheme)
        setContentView(R.layout.activity_main)
        Pal.init(this)
        // 用自绘的极光背景替换 XML 里的 layer-list：更多柔光、噪点和斜向反光
        findViewById<View>(R.id.root).background = GlassBackdropDrawable(resources)
        applySystemBars()
        applyHeaderInset()
        db = WordDb(this)

        content = findViewById(R.id.content)
        badge = findViewById(R.id.headerBadge)

        val inflater = LayoutInflater.from(this)
        val p0 = inflater.inflate(R.layout.page_library, content, false)
        val p1 = inflater.inflate(R.layout.page_learn, content, false)
        val p2 = inflater.inflate(R.layout.page_stats, content, false)
        val p3 = inflater.inflate(R.layout.page_import, content, false)
        val p4 = inflater.inflate(R.layout.page_settings, content, false)
        listOf(p0, p1, p2, p3, p4).forEach {
            it.visibility = View.GONE
            content.addView(it)
            pages.add(it)
        }

        library = LibraryView(this, p0)
        learn = LearnView(this, p1)
        statsPage = StatsView(this, p2)
        importPage = ImportView(this, p3)
        settingsPage = SettingsView(this, p4)
        listOf(p0, p1, p2, p3, p4).forEach { applyGlassDepth(it) }

        tabs.add(findViewById<TextView>(R.id.tabLibrary))
        tabs.add(findViewById<TextView>(R.id.tabLearn))
        tabs.add(findViewById<TextView>(R.id.tabStats))
        tabs.add(findViewById<TextView>(R.id.tabImport))
        tabs.add(findViewById<TextView>(R.id.tabSettings))
        tabs.forEachIndexed { i, t -> t.setOnClickListener { switchTo(i) } }
        findViewById<View>(R.id.tabBar).background = GlassSurfaceDrawable(
            resources,
            radiusDp = 26f,
            shadowDp = 8f,
            shadowDyDp = 3f,
            fillTopColor = getColor(R.color.glass_card_top),
            fillBottomColor = getColor(R.color.glass_tabbar),
            tintColor = getColor(R.color.glass_card_tint)
        )

        initTts()
        BundledTts.init(this)
        // 旋转屏幕由 configChanges 自行处理；这里的恢复主要针对进程被系统回收后重建
        switchTo((savedInstanceState?.getInt(STATE_TAB, 0) ?: 0).coerceIn(0, pages.lastIndex))
        if (savedInstanceState != null) return
        if (isIncomingIntent(intent)) {
            // v8：从微信「用其他应用打开 / 分享」进来，直接进导入预览
            handleIncomingIntent(intent)
        } else {
            // v9：正常点图标启动，且用户开了开关时静默查一次新版；没新版不弹窗
            UpdateChecker.autoCheck(this)
        }
    }

    private fun isIncomingIntent(i: Intent?): Boolean = when (i?.action) {
        Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> true
        else -> false
    }

    /** v8：App 已在运行（singleTask）时，微信再发一个文件会走这里 */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (ttsReady) refreshVoiceAvailability()
        if (::library.isInitialized) {
            refreshCurrent()
            // Obsidian 文件夹是主数据：先写本机改动，再把外部编辑读回来
            if (VaultStore.enabled(this)) {
                VaultSync.sync(this, db) { runOnUiThread { refreshCurrent() } }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_TAB, current)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    /**
     * v8：接住「微信 / QQ / 企业微信 / 文件管理器」发来的文件。
     *
     * 微信点文件 →「用其他应用打开」发 ACTION_VIEW（data 里是 content:// Uri）；
     * 长按文件 →「分享 / 发送到其他应用」发 ACTION_SEND（Uri 在 EXTRA_STREAM）；
     * 多选发 ACTION_SEND_MULTIPLE；有的版本还会把 Uri 放在 clipData。
     * 三种一起接，再统一走原来的导入预览流程。纯文本分享按「粘贴导入」处理。
     *
     * 不主动去读微信私有目录：Android 的沙箱不允许，也不需要任何权限。
     */
    private fun handleIncomingIntent(intent: Intent?) {
        val i = intent ?: return
        when (i.action) {
            Intent.ACTION_VIEW, Intent.ACTION_SEND, Intent.ACTION_SEND_MULTIPLE -> Unit
            else -> return // 正常点图标启动是 ACTION_MAIN，别误处理
        }
        val uris = mutableListOf<Uri>()
        i.data?.let { uris.add(it) }
        when (val extra = i.extras?.get(Intent.EXTRA_STREAM)) {
            is Uri -> uris.add(extra)
            is List<*> -> uris.addAll(extra.filterIsInstance<Uri>())
        }
        i.clipData?.let { cd ->
            for (k in 0 until cd.itemCount) cd.getItemAt(k).uri?.let { uris.add(it) }
        }
        val distinct = uris.distinctBy { it.toString() }
        val sharedText = if (i.action == Intent.ACTION_SEND) {
            i.getStringExtra(Intent.EXTRA_TEXT)
        } else null

        // 同一个 intent 被重复投递（重建 / 系统重发）时只处理一次，避免弹两遍预览
        val key = "${i.action}|${distinct.joinToString(",") { it.toString() }}|${sharedText ?: ""}"
        if (key == lastIncomingKey) return
        lastIncomingKey = key

        switchTo(3)
        if (distinct.isNotEmpty()) {
            if (distinct.size == 1) importFromUri(distinct[0], "微信 / 其他应用")
            else importFromUris(distinct, "微信 / 其他应用")
            return
        }
        if (!sharedText.isNullOrBlank()) {
            showImportPreview(DocxParser.parseText(sharedText), "微信分享的文本")
            return
        }
        toast("没拿到文件：在微信里点文件 → 用其他应用打开 → 选「单词酷」；或先保存到手机再用「选择文档并导入」")
    }

    fun switchTo(index: Int) {
        current = index
        pages.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tabs.forEachIndexed { i, t ->
            val on = i == index
            t.setTextColor(if (on) Pal.primary else Pal.textSub)
            t.setTypeface(null, if (on) Typeface.BOLD else Typeface.NORMAL)
            if (on) t.setBackgroundResource(R.drawable.bg_tab_active) else t.background = null
        }
        refreshCurrent()
    }

    /** 状态栏 / 导航栏跟着亮暗主题走，不再按页面单独切换 */
    private fun applySystemBars() {
        val dark = (resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES

        window.statusBarColor = Color.TRANSPARENT
        // API 23–26 没有浅色导航栏图标，亮色主题下退回深底白图标，避免图标看不见
        window.navigationBarColor = if (!dark && Build.VERSION.SDK_INT < 27) {
            getColor(R.color.text)
        } else {
            getColor(R.color.glass_nav)
        }

        var flags = window.decorView.systemUiVisibility
        // 状态栏透明并让玻璃背景延伸到状态栏后面；header 自己加 inset，避免出现白色接缝
        flags = flags or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        val lightStatus = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        flags = if (dark) flags and lightStatus.inv() else flags or lightStatus
        if (Build.VERSION.SDK_INT >= 27) {
            val lightNav = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            flags = if (dark) flags and lightNav.inv() else flags or lightNav
        }
        window.decorView.systemUiVisibility = flags
    }

    /** 状态栏改为透明后，把 header 的内容整体下移状态栏高度，背景仍铺到屏幕最顶部 */
    private fun applyHeaderInset() {
        val header = findViewById<View>(R.id.headerBar)
        val baseTop = header.paddingTop
        header.setOnApplyWindowInsetsListener { v, insets ->
            v.setPadding(
                v.paddingLeft,
                baseTop + insets.systemWindowInsetTop,
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }
        header.requestApplyInsets()
    }

    /** 把 XML 的 layer-list 卡片换成自绘玻璃面：阴影画在卡片外侧，不会被半透明面透出来 */
    private fun applyGlassDepth(v: View) {
        if (v is ViewGroup && v.background is LayerDrawable) {
            v.background = GlassSurfaceDrawable(resources, radiusDp = 20f, shadowDp = 4f)
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) applyGlassDepth(v.getChildAt(i))
        }
    }

    private fun refreshCurrent() {
        val s = db.stats()
        badge.text = "已解锁 ${s.unlocked} / ${s.total}"
        badge.setTextColor(Pal.primary)
        badge.setBackgroundResource(R.drawable.bg_badge)
        when (current) {
            0 -> library.refresh()
            1 -> learn.refresh()
            2 -> statsPage.refresh()
            3 -> importPage.refresh()
            4 -> settingsPage.refresh()
        }
    }

    /** 任何数据改动后统一刷新顶栏和当前页，并把差异写进 Obsidian 文件夹 */
    fun dataChanged() {
        refreshCurrent()
        VaultSync.flush(this, db)
    }

    private fun initTts() {
        try {
            tts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    configureTtsAudio(tts)
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

    /** 明确走媒体音量 / 语音内容，避免个别引擎把 TTS 归到辅助功能或通话流，
     *  出现「媒体音量明明不小、朗读却很小」的情况。 */
    private fun configureTtsAudio(engine: TextToSpeech?) {
        val e = engine ?: return
        try {
            e.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        } catch (ignored: Exception) {
            // 老引擎不支持 setAudioAttributes 时保持系统默认
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
                    v.features?.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED) != true
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
        if (!mediaVolumeOk()) return
        // 优先用随包内置的离线引擎；没装系统语音包的手机也能直接读
        if (BundledTts.speak(text, db.speechRate)) {
            if (accent == ACCENT_UK && !bundledAccentNotified) {
                bundledAccentNotified = true
                toast("当前内置语音为美音，英音按钮也会用美音朗读")
            }
            return
        }
        if (!ttsReady || tts == null) {
            toast(
                if (BundledTts.isInitializing()) "内置语音正在加载，稍等几秒再点"
                else "语音引擎还在准备，稍等一下再点"
            )
            return
        }
        val ok = if (accent == ACCENT_UK) ukAvailable else usAvailable
        if (ok && applyAccent(accent)) {
            speakNow(text)
            return
        }
        val fallback = if (accent == ACCENT_UK) ACCENT_US else ACCENT_UK
        val fallbackOk = if (fallback == ACCENT_UK) ukAvailable else usAvailable
        if (fallbackOk && applyAccent(fallback)) {
            toast("这台手机没有${accentName(accent)}语音包，已用${accentName(fallback)}朗读；可在系统「文字转语音」里安装")
            speakNow(text)
            return
        }
        toast("还没装英语语音包：系统设置 → 语言和输入 → 文字转语音 → 安装英语（美国 / 英国）语音数据")
    }

    private fun mediaVolumeOk(): Boolean {
        val am = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return true
        val vol = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        if (vol == 0) {
            toast("媒体音量为 0：先按音量键调高媒体音量，再点发音")
            return false
        }
        if (vol * 100 / max < 30) {
            toast("媒体音量偏低（$vol/$max），按音量键调高后再听")
        }
        return true
    }

    /** 系统 TTS 兜底；内置引擎不可用时才会走到这里 */
    private fun speakNow(text: String) {
        val engine = tts ?: return
        engine.setSpeechRate(db.speechRate)
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        val result = engine.speak(text, TextToSpeech.QUEUE_FLUSH, params, "wordcool")
        if (result == TextToSpeech.ERROR) {
            toast("系统拒绝了朗读请求：到「设置 → 发音」检查 TTS 引擎和英语语音包")
        }
    }

    fun accentName(accent: Int): String = if (accent == ACCENT_UK) "英音" else "美音"

    fun accentLabel(accent: Int): String = if (accent == ACCENT_UK) "UK 英音" else "US 美音"

    /** 给导入页「发音」设置区显示当前引擎状态 */
    fun ttsStatusText(): String {
        val system = if (!ttsReady) "系统 TTS 未就绪" else {
            val uk = if (ukAvailable) "英音可用" else "英音未安装"
            val us = if (usAvailable) "美音可用" else "美音未安装"
            "$uk · $us"
        }
        return "${BundledTts.statusText()} · $system · 默认${accentName(db.accent)} · 语速${speechRateLabel()}"
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

    /** v1.4：内置考研词库。assets/kaoyan.tsv 每行「单词<TAB>释义<TAB>组别<TAB>备注」，
     *  先走和文件导入相同的预览流程：自动补音标、显示划组与未命中词，确认后才入库。 */
    fun loadKaoyanPack() {
        try {
            val text = assets.open("kaoyan.tsv").bufferedReader(Charsets.UTF_8).use { it.readText() }
            val items = mutableListOf<Imported>()
            text.lineSequence().forEach { raw ->
                val line = raw.trimEnd('\r')
                if (line.isBlank() || line.startsWith("#")) return@forEach
                val cols = line.split('\t')
                if (cols.size < 3) return@forEach
                val word = cols[0].trim()
                val meaning = cols[1].trim()
                val group = cols[2].trim()
                val note = cols.getOrElse(3) { "" }.trim()
                if (word.isEmpty() || meaning.isEmpty() || group.isEmpty()) return@forEach
                items.add(Imported(word = word, meaning = meaning, group = group, note = note))
            }
            if (items.isEmpty()) {
                toast("内置考研词库为空或读取失败")
                return
            }
            showImportPreview(items, "内置考研词库")
        } catch (e: Exception) {
            toast("读取内置考研词库失败：${e.message}")
        }
    }

    /** 系统 TTS 语音包不是 App 能直接下载的：给出可执行的安装路径，
     *  并兼容 AOSP / 三星 / 原生设置等不同入口。 */
    fun openTtsSettings() {
        AlertDialog.Builder(this)
            .setTitle("安装英语语音包")
            .setMessage(
                "语音包由系统 TTS 引擎提供，不在 App 安装包里。\n\n" +
                    "1. 打开系统「文字转语音」设置\n" +
                    "2. 选择引擎：Google 文字转语音 / 手机自带引擎\n" +
                    "3. 安装「英语（美国）」或「英语（英国）」语音数据\n\n" +
                    "如果系统里没有安装入口，可在 Google Play 搜索「Speech Services by Google」更新或安装 Google 文字转语音。\n\n" +
                    "装好后回到本页，状态会自动重新检测；也可以点「试听一句英文」确认。"
            )
            .setNegativeButton("关闭", null)
            .setNeutralButton("Google Play") { _, _ -> openTtsStore() }
            .setPositiveButton("系统设置") { _, _ -> openSystemTtsSettings() }
            .show()
    }

    private fun openSystemTtsSettings() {
        val candidates = listOf(
            Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA),
            Intent("com.android.settings.TTS_SETTINGS"),
            Intent("com.samsung.android.settings.TTS_SETTINGS"),
            Intent(android.provider.Settings.ACTION_SETTINGS)
        )
        for (intent in candidates) {
            try {
                startActivity(intent)
                return
            } catch (ignored: Exception) {
            }
        }
        toast("找不到系统 TTS 设置：请手动打开 设置 → 语言和输入 → 文字转语音")
    }

    private fun openTtsStore() {
        val pkg = "com.google.android.tts"
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg")))
            return
        } catch (ignored: Exception) {
        }
        try {
            startActivity(
                Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://play.google.com/store/apps/details?id=$pkg")
                )
            )
        } catch (e: Exception) {
            toast("打不开应用商店：${e.message}")
        }
    }

    // ---------- 词条编辑 / 详情 ----------

    /** v1.9：词库页只读；从导入页「编辑单词」搜索后打开现有编辑框。 */
    fun showWordPicker() {
        val v = layoutInflater.inflate(R.layout.dialog_pick_word, null)
        val et = v.findViewById<EditText>(R.id.etPickSearch)
        val list = v.findViewById<ListView>(R.id.listPickWords)
        val empty = v.findViewById<TextView>(R.id.tvPickEmpty)

        val all = db.getWords()
        var filtered = all
        val labels = mutableListOf<String>()
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, labels)
        list.adapter = adapter

        fun apply(query: String) {
            val q = query.trim()
            filtered = if (q.isEmpty()) all else all.filter {
                it.word.contains(q, ignoreCase = true) || it.meaning.contains(q, ignoreCase = true)
            }
            labels.clear()
            filtered.forEach { labels.add(it.word) }
            adapter.notifyDataSetChanged()
            empty.show(filtered.isEmpty())
            list.show(filtered.isNotEmpty())
        }
        apply("")
        et.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
                apply(s?.toString().orEmpty())
            override fun afterTextChanged(s: Editable?) {}
        })

        val dlg = AlertDialog.Builder(this)
            .setTitle("编辑单词")
            .setView(v)
            .setNegativeButton("取消", null)
            .create()
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            if (pos in filtered.indices) {
                val word = filtered[pos]
                dlg.dismiss()
                showWordDialog(word)
            }
        }
        dlg.show()
    }

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

    fun pickVaultFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        try {
            startActivityForResult(i, REQ_VAULT)
        } catch (e: Exception) {
            toast("打不开文件夹选择器：${e.message}")
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

    /** v1.6 / v1.10：学习页与词库页共用的多选范围。回调 null 表示「全部词组」。 */
    fun showMultiGroupPicker(
        selected: Set<String>,
        title: String = "选择学习范围（可多选，不选=全部）",
        onPick: (Set<String>?) -> Unit
    ) {
        val gs = db.groups()
        if (gs.isEmpty()) {
            toast("还没有分组，先导入带 Unit / Day 标题的词表，或用「自动划组」")
            return
        }
        val labels = gs.map {
            "${db.groupLabel(it.name)} · ${it.total} 词 · 已解锁 ${it.unlocked}"
        }
        val allChecked = selected.isEmpty() || gs.all { it.name in selected }
        val checked = BooleanArray(gs.size) { allChecked || gs[it].name in selected }
        val builder = AlertDialog.Builder(this)
            .setTitle(title)
            .setMultiChoiceItems(labels.toTypedArray(), checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setNegativeButton("清空", null)
            .setNeutralButton("全选", null)
            .setPositiveButton("确定") { _, _ ->
                val picked = gs.indices.filter { checked[it] }.map { gs[it].name }.toSet()
                onPick(if (picked.isEmpty() || picked.size == gs.size) null else picked)
            }
        val dlg = builder.create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
                for (i in checked.indices) {
                    checked[i] = false
                    dlg.listView?.setItemChecked(i, false)
                }
            }
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {
                for (i in checked.indices) {
                    checked[i] = true
                    dlg.listView?.setItemChecked(i, true)
                }
            }
        }
        dlg.show()
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

    private data class Incoming(val name: String, val items: List<Imported>, val restored: Int? = null)

    /** 读一个外部 Uri 并解析成词条。content:// / file:// 一律走 ContentResolver，
     *  不拼文件路径——微信给的是 FileProvider 的临时授权 Uri。 */
    private fun readIncoming(uri: Uri): Incoming {
        val name = displayName(uri) ?: guessName(uri)
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("打不开这个文件（可能已被微信清理，或没有读取授权）")

        val legacy = (name.endsWith(".doc", true) || name.endsWith(".xls", true)) &&
            !DocxParser.isZip(bytes) && !DocxParser.isPdf(bytes)
        if (legacy) {
            throw IllegalArgumentException("旧版 .doc / .xls 读不了，请另存为 .docx / .xlsx，或复制内容用「粘贴导入」")
        }

        val plainText = !DocxParser.isZip(bytes) && !DocxParser.isPdf(bytes)
        // 微信「分享」里选到图片 / 音频时别把二进制当 UTF-8 文本，生成一堆垃圾词条
        if (plainText && !looksLikeText(bytes)) {
            throw IllegalArgumentException("这不是文字型词表（可能是图片、音频或加密文件）。请把文档保存到手机后用「选择文档并导入」，或复制内容用「粘贴导入」")
        }
        val text = String(bytes, Charsets.UTF_8).trimStart()
        // 只有纯文本文件才按内容猜 JSON，避免 docx/xlsx/pdf 的二进制流被误判成备份
        if (name.endsWith(".json", true) ||
            (plainText && text.startsWith("{") && text.contains("\"words\""))
        ) {
            try {
                return Incoming(name, emptyList(), db.importBackup(text))
            } catch (e: Exception) {
                // 不是本应用的备份，按普通文本继续处理
            }
        }
        return Incoming(name, DocxParser.parseBytes(bytes, name))
    }

    /** 用替换字符 / 控制字符比例判断是不是文本，挡住图片被当词表 */
    private fun looksLikeText(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        val s = String(bytes, Charsets.UTF_8)
        if (s.isEmpty()) return false
        val bad = s.count { c ->
            c == '\uFFFD' || (c.code < 0x20 && c != '\n' && c != '\r' && c != '\t')
        }
        return bad.toDouble() / s.length < 0.05
    }

    /** 单文件：系统文件选择器的旧调用行为不变，只加带默认值的参数 */
    private fun importFromUri(uri: Uri, source: String? = null) {
        try {
            val inc = readIncoming(uri)
            if (inc.restored != null) {
                toast("已从${source ?: "备份"}恢复 ${inc.restored} 个单词")
                dataChanged()
                return
            }
            showImportPreview(inc.items, source ?: inc.name)
        } catch (e: Exception) {
            showImportError(e.message ?: "未知错误")
        }
    }

    /** v8：微信多选分享时合并成一次预览，不弹一堆窗口 */
    private fun importFromUris(uris: List<Uri>, source: String) {
        val all = mutableListOf<Imported>()
        val names = mutableListOf<String>()
        val failures = mutableListOf<String>()
        var restored = 0
        for (u in uris) {
            try {
                val inc = readIncoming(u)
                names.add(inc.name)
                if (inc.restored != null) restored += inc.restored else all.addAll(inc.items)
            } catch (e: Exception) {
                failures.add("${displayName(u) ?: guessName(u)}：${e.message}")
            }
        }
        if (restored > 0) toast("已从备份恢复 $restored 个单词")
        if (failures.isNotEmpty()) showImportError(failures.joinToString("\n\n"))
        if (all.isEmpty()) {
            if (restored == 0 && failures.isEmpty()) toast("这些文件里没识别到单词")
            dataChanged()
            return
        }
        showImportPreview(all, "$source（${names.size} 个文件）")
    }

    private fun showImportError(msg: String) {
        AlertDialog.Builder(this)
            .setTitle("读取失败")
            .setMessage(
                "$msg\n\n建议：Word 另存为 .docx、Excel 另存为 .xlsx；" +
                    "扫描版 PDF 请复制内容用「粘贴导入」。"
            )
            .setPositiveButton("知道了", null)
            .show()
    }

    private fun displayName(uri: Uri): String? {
        return try {
            // 微信的 FileProvider 可能不响应 DISPLAY_NAME 查询，甚至直接抛异常
            val c = contentResolver.query(uri, null, null, null, null) ?: return null
            c.use { cur ->
                if (cur.moveToFirst()) {
                    val i = cur.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) cur.getString(i) else null
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 微信常不返回文件名，用 MIME 补一个带扩展名的名字（解析主要靠魔数，名字只用于提示） */
    private fun guessName(uri: Uri): String {
        val seg = uri.lastPathSegment
        if (!seg.isNullOrBlank() && seg.contains('.')) return seg
        val mime = try {
            contentResolver.getType(uri)
        } catch (e: Exception) {
            null
        }
        return when (mime) {
            "application/pdf" -> "微信文档.pdf"
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> "微信文档.docx"
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "微信文档.xlsx"
            "application/json" -> "微信文档.json"
            "text/csv" -> "微信文档.csv"
            "text/plain" -> "微信文档.txt"
            else -> "微信文档"
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, result: Intent?) {
        super.onActivityResult(requestCode, resultCode, result)
        if (resultCode != RESULT_OK || result == null) return
        val uri = result.data ?: return
        when (requestCode) {
            REQ_PICK -> importFromUri(uri)
            REQ_VAULT -> {
                if (!VaultStore.setTree(this, uri)) {
                    toast("没有拿到文件夹的读写权限，无法作为 Obsidian 词库")
                    return
                }
                VaultSync.resetForNewVault(this)
                val files = VaultStore.list(this)
                if (files == null) {
                    VaultStore.clear(this)
                    toast("这个文件夹无法创建 WordCool/，换一个文件夹试试")
                    settingsPage.refresh()
                    return
                }
                val done: (String) -> Unit = { msg ->
                    runOnUiThread {
                        toast(msg)
                        settingsPage.refresh()
                        dataChanged()
                    }
                }
                if (files.isEmpty()) {
                    VaultSync.exportAll(this, db) {
                        done("已连接 Obsidian 文件夹：${VaultStore.displayName(this)} / WordCool")
                    }
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("文件夹里已有 ${files.size} 个 Markdown")
                        .setMessage(
                            "要把本机 ${db.stats().total} 个词写进去，还是用文件夹里的词覆盖本机？\n\n" +
                                "从文件夹导入前，会先把本机词库备份到应用私有目录。"
                        )
                        .setPositiveButton("以本机为准") { _, _ ->
                            VaultSync.exportAll(this, db) {
                                done("已把本机词库写入 Obsidian 文件夹")
                            }
                        }
                        .setNegativeButton("从文件夹导入") { _, _ ->
                            VaultSync.importAll(this, db) {
                                done("已从 Obsidian 文件夹导入")
                            }
                        }
                        .setNeutralButton("取消") { _, _ ->
                            VaultStore.clear(this)
                            settingsPage.refresh()
                        }
                        .show()
                }
            }
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
