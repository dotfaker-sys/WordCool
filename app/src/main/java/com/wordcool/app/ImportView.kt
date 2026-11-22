package com.wordcool.app

import android.app.AlertDialog
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** 导入页只放「把词弄进来」和「划组」；发音、音标、备份、重置、版本统一在设置页。 */
class ImportView(private val a: MainActivity, private val root: View) {

    private val etPaste = root.findViewById<EditText>(R.id.etPaste)
    private val tvStatus = root.findViewById<TextView>(R.id.tvImportStatus)

    private val tvGroupSummary = root.findViewById<TextView>(R.id.tvGroupSummary)
    private val groupBox = root.findViewById<LinearLayout>(R.id.groupBox)
    private val etGroupPrefix = root.findViewById<EditText>(R.id.etGroupPrefix)
    private val etGroupSize = root.findViewById<EditText>(R.id.etGroupSize)
    private val btnAutoGroup = root.findViewById<TextView>(R.id.btnAutoGroup)

    init {
        root.findViewById<TextView>(R.id.btnPickDoc).setOnClickListener {
            a.pickDocument()
        }
        root.findViewById<TextView>(R.id.btnWechatHelp).setOnClickListener {
            showWechatHelp()
        }
        root.findViewById<TextView>(R.id.btnEditWord).setOnClickListener {
            a.showWordPicker()
        }

        root.findViewById<TextView>(R.id.btnPasteImport).setOnClickListener {
            val text = etPaste.text.toString()
            if (text.isBlank()) {
                a.toast("先把单词表粘贴进来")
                return@setOnClickListener
            }
            a.showImportPreview(DocxParser.parseText(text), "粘贴内容")
        }

        btnAutoGroup.setOnClickListener {
            val prefix = etGroupPrefix.text.toString().trim().ifEmpty { "Unit" }
            val size = etGroupSize.text.toString().trim().toIntOrNull()
            if (size == null || size < 1 || size > 200) {
                a.toast("每组个数请填 1~200")
                return@setOnClickListener
            }
            AlertDialog.Builder(a, R.style.ObsidianDialog)
                .setTitle("自动划组")
                .setMessage("把全部单词按顺序每 $size 个一组，组名为「$prefix 1」「$prefix 2」…\n会覆盖现有组名，学习进度不受影响。")
                .setNegativeButton("取消", null)
                .setPositiveButton("开始划组") { _, _ ->
                    val n = a.db.assignSequentialGroups(prefix, size)
                    a.toast(if (n > 0) "已划成 $n 组" else "词库还是空的，先导入单词")
                    a.dataChanged()
                }.show()
        }

        root.findViewById<TextView>(R.id.btnLoadDemo).setOnClickListener {
            AlertDialog.Builder(a, R.style.ObsidianDialog)
                .setTitle("载入示例词库？")
                .setMessage("加入 12 个带音标、释义、例句、记忆提示和组别的示例词（Unit 1 / Day 1 / Day 2 / Unit 2）。已存在的词不会重复。")
                .setNegativeButton("取消", null)
                .setPositiveButton("载入") { _, _ ->
                    val n = a.db.loadDemo()
                    a.toast("加入了 $n 个示例词")
                    a.dataChanged()
                }.show()
        }

        root.findViewById<TextView>(R.id.btnLoadKaoyan).setOnClickListener {
            AlertDialog.Builder(a, R.style.ObsidianDialog)
                .setTitle("载入考研词库？")
                .setMessage(
                    "加入 711 个考研词，按 Unit 1 Day 1 … Unit 5 Day 3 分成 23 组。\n\n" +
                        "会先打开导入预览：自动补英 / 美音标、列出未命中词，确认后才写入词库。\n" +
                        "已存在的词只补空字段，不覆盖你改过的释义与学习进度。\n\n" +
                        "词表来自你提供的文件，仅保存在本机，不上传。"
                )
                .setNegativeButton("取消", null)
                .setPositiveButton("查看导入预览") { _, _ -> a.loadKaoyanPack() }
                .show()
        }
    }

    fun refresh() {
        val s = a.db.stats()
        tvStatus.text = "当前词库共 ${s.total} 个单词 · 已解锁 ${s.unlocked} 个 · 已掌握 ${s.mastered} 个"
        renderGroups()
    }

    /** v8：微信 / QQ 分享入口的详细操作与排障 */
    private fun showWechatHelp() {
        val msg = StringBuilder()
            .append("【正常路径】\n")
            .append("1. 在微信聊天里点开那个 .docx / .xlsx / .pdf 文件，先等它下载完成\n")
            .append("2. 点右上角「…」→「用其他应用打开」；或长按文件 →「分享」→ 展开更多应用\n")
            .append("3. 在应用列表里选「单词酷」（首次可能藏在「更多」里）\n")
            .append("4. 会自动跳到本应用的导入预览，确认划组和音标后点「导入 N 个」\n\n")
            .append("【列表里找不到「单词酷」】\n")
            .append("· 微信各版本给出的文件类型不一样，不一定会列出所有能打开的应用\n")
            .append("· 先在微信里把文件「保存到手机」，再回本应用点「选择文档并导入」，从 下载 / Weixin / Documents 里选\n")
            .append("· 或干脆复制词表内容，用本页「粘贴文本导入」\n\n")
            .append("【收不到的情况】\n")
            .append("· 扫描图片版 PDF 没有文字层\n")
            .append("· 旧版 .doc / .xls，请另存为 .docx / .xlsx\n")
            .append("· 发的是图片、语音或链接，不是词表文件\n")
            .append("· 文件已被微信自动清理，重新下载一次\n\n")
            .append("本应用不读微信私有目录、不联网、不申请任何权限；只有你主动选它，系统才会把这一个文件的临时读取权交给它。")
            .toString()
        AlertDialog.Builder(a, R.style.ObsidianDialog)
            .setTitle("从微信 / QQ 导入")
            .setMessage(msg)
            .setPositiveButton("知道了", null)
            .setNeutralButton("选择文档并导入") { _, _ -> a.pickDocument() }
            .show()
    }

    /** 每行一个组，点击即可重命名；不用 ListView，避免和外层 ScrollView 抢滚动 */
    private fun renderGroups() {
        val gs = a.db.groups()
        tvGroupSummary.text = if (gs.isEmpty()) {
            "还没有分组 · 导入带 Unit / Day 标题的文档会自动成组"
        } else {
            "共 ${gs.size} 组 · ${gs.sumOf { it.total }} 个单词"
        }
        groupBox.removeAllViews()
        gs.forEach { g ->
            val row = TextView(a).apply {
                text = "${a.db.groupLabel(g.name)} · ${g.total} 词 · " +
                    "已解锁 ${g.unlocked} · 已掌握 ${g.mastered}"
                textSize = 12f
                setTextColor(Obs.text)
                background = a.rounded(Obs.surface, 3f, Obs.line)
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = a.dp(6) }
                minimumHeight = a.dp(40)
                setPadding(a.dp(10), a.dp(8), a.dp(10), a.dp(8))
                setOnClickListener {
                    a.showGroupNameInput(
                        "重命名组",
                        g.name,
                        "例如 Unit 1 / Day 2 / Unit 1 Day 1"
                    ) { newName ->
                        val changed = a.db.renameGroup(g.name, newName)
                        a.toast("已更新 $changed 个词为「${newName.ifBlank { "未分组" }}」")
                        a.dataChanged()
                    }
                }
            }
            groupBox.addView(row)
        }
    }
}
