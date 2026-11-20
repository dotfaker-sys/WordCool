package com.wordcool.app

import android.graphics.Color
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.BaseAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView

class LibraryView(private val a: MainActivity, private val root: View) {

    private val list = root.findViewById<ListView>(R.id.listWords)
    private val tvEmpty = root.findViewById<TextView>(R.id.tvEmpty)

    // 这些控件在 ListView 的 header 里；滚动列表时会跟着滚出屏幕（折叠效果）
    private lateinit var etSearch: EditText
    private lateinit var tvCount: TextView
    private lateinit var bar: ProgressBar
    private lateinit var btnGroupFilter: TextView
    private lateinit var chips: List<TextView>

    private var filter = -1                 // -1 全部 / 0 未解锁 / 1 学习中 / 2 已掌握
    private var groupFilter: Set<String>? = null // null=全部；空串代表「未分组」
    private var items: List<Word> = emptyList()

    // v1.8：词库按 Unit → Day 两层折叠，折叠状态只保存在本页面实例里
    private val foldedUnits = mutableSetOf<String>()
    private val foldedDays = mutableSetOf<String>()

    private val unitRegex = Regex("""\bunit\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val dayRegex = Regex("""\bday\s*(\d+)""", RegexOption.IGNORE_CASE)

    private class DaySection(val title: String, val key: String) {
        val words = mutableListOf<Word>()
        var dayNum: Long? = null
    }

    private class UnitSection(val title: String, val unitNum: Long?) {
        val days = mutableListOf<DaySection>()
    }

    /** 列表里混杂三种行：Unit 大标题、Day 小标题、单词行 */
    private sealed class Row {
        class UnitHeader(
            val title: String,
            val total: Int,
            val unlocked: Int,
            val folded: Boolean
        ) : Row()

        class DayHeader(
            val title: String,
            val total: Int,
            val unlocked: Int,
            val folded: Boolean,
            val key: String
        ) : Row()

        class WordRow(val word: Word) : Row()
    }

    private var rows: List<Row> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = rows.size
        override fun getItem(position: Int): Any = rows[position]
        override fun getItemId(position: Int): Long = position.toLong()
        override fun getViewTypeCount(): Int = 3

        override fun getItemViewType(position: Int): Int = when (rows[position]) {
            is Row.UnitHeader -> 0
            is Row.DayHeader -> 1
            else -> 2
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View =
            when (val row = rows[position]) {
                is Row.UnitHeader -> bindUnit(convertView, row, parent)
                is Row.DayHeader -> bindDay(convertView, row, parent)
                is Row.WordRow -> bindWord(convertView, row.word, parent)
            }
    }

    private fun inflate(convertView: View?, layout: Int, parent: ViewGroup?): View =
        convertView ?: LayoutInflater.from(a).inflate(layout, parent, false)

    private fun bindUnit(convertView: View?, row: Row.UnitHeader, parent: ViewGroup?): View {
        val v = inflate(convertView, R.layout.item_library_unit, parent)
        v.findViewById<TextView>(R.id.tvUnitTitle).text = row.title
        v.findViewById<TextView>(R.id.tvUnitInfo).text =
            "${row.total} 词 · 已解锁 ${row.unlocked}"
        v.findViewById<TextView>(R.id.tvUnitArrow).text = if (row.folded) "▸" else "▾"
        return v
    }

    private fun bindDay(convertView: View?, row: Row.DayHeader, parent: ViewGroup?): View {
        val v = inflate(convertView, R.layout.item_library_day, parent)
        v.findViewById<TextView>(R.id.tvDayTitle).text = row.title
        v.findViewById<TextView>(R.id.tvDayInfo).text =
            "${row.total} 词 · 已解锁 ${row.unlocked}"
        v.findViewById<TextView>(R.id.tvDayArrow).text = if (row.folded) "▸" else "▾"
        return v
    }

    private fun bindWord(convertView: View?, w: Word, parent: ViewGroup?): View {
        val v = inflate(convertView, R.layout.item_word, parent)
        v.findViewById<TextView>(R.id.tvWord).text = w.word
        v.findViewById<TextView>(R.id.tvPhonetic).text = phoneticShort(w)
        v.findViewById<TextView>(R.id.btnItemSpeak).setOnClickListener {
            a.speak(w.word)
        }
        v.findViewById<TextView>(R.id.tvMeaning).text =
            if (w.meaning.isBlank()) "（未填释义，点进来补一下）" else w.meaning
        // Unit / Day 已经由上面的分组标题交代，行内只留学习数据，避免重复
        v.findViewById<TextView>(R.id.tvMeta).text =
            "连对 ${w.streak} · 答对 ${w.right} / 答错 ${w.wrong}"

        val left = w.nextReview - WordDb.today()
        // 词库列表不再显示锁定状态；只给已经开始的词显示复习时间
        v.findViewById<TextView>(R.id.tvReview).text = when {
            w.state == 0 -> ""
            left > 0 -> "$left 天后复习"
            else -> "今天该复习"
        }
        return v
    }

    init {
        // 把搜索 / 筛选 / 进度 / 解锁按钮放进 ListView 的 header，
        // 往下滑时它们会随列表一起滚出去，而不是永远占着上半屏。
        val header = LayoutInflater.from(a).inflate(R.layout.view_library_header, list, false)
        list.addHeaderView(header, null, false)

        etSearch = header.findViewById(R.id.etSearch)
        tvCount = header.findViewById(R.id.tvCount)
        bar = header.findViewById(R.id.unlockBar)
        btnGroupFilter = header.findViewById(R.id.btnGroupFilter)
        chips = listOf(
            header.findViewById(R.id.chipAll),
            header.findViewById(R.id.chipLocked),
            header.findViewById(R.id.chipLearning),
            header.findViewById(R.id.chipMastered)
        )

        chips.forEachIndexed { i, c ->
            c.setOnClickListener {
                filter = i - 1
                refresh()
            }
        }
        btnGroupFilter.setOnClickListener {
            a.showMultiGroupPicker(
                groupFilter ?: emptySet(),
                "选择组别（可多选，不选=全部）"
            ) { picked ->
                groupFilter = picked
                refresh()
            }
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
            override fun afterTextChanged(s: Editable?) {}
        })
        header.findViewById<TextView>(R.id.btnAdd).setOnClickListener {
            a.showWordDialog(null)
        }
        header.findViewById<TextView>(R.id.btnUnlock).setOnClickListener {
            // 按组筛选时，只解锁当前组的词，别把别的组一起放出来
            val n = a.db.unlockNewForGroups(a.db.dailyGoal, groupFilter)
            a.toast(if (n > 0) "解锁了 $n 个新词，去「学习」页开始吧" else "这一组没有可解锁的新词了")
            a.dataChanged()
        }
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            val row = rows.getOrNull(pos - list.headerViewsCount)
            if (row != null) when (row) {
                is Row.UnitHeader -> {
                    if (!foldedUnits.add(row.title)) foldedUnits.remove(row.title)
                    renderRows(keepScroll = true)
                }
                is Row.DayHeader -> {
                    if (!foldedDays.add(row.key)) foldedDays.remove(row.key)
                    renderRows(keepScroll = true)
                }
                // 词库列表不再直接编辑 / 删除；编辑入口在「导入 → 编辑单词」
                is Row.WordRow -> Unit
            }
        }
        list.adapter = adapter
    }

    fun refresh() {
        items = a.db.getWordsForGroups(filter, etSearch.text.toString(), groupFilter)
        val s = a.db.stats()
        val scope = when {
            groupFilter == null -> "全部词组"
            groupFilter!!.size == 1 -> a.db.groupLabel(groupFilter!!.first())
            else -> "${groupFilter!!.size} 个组"
        }
        btnGroupFilter.text = "组别：$scope"
        tvCount.text = "共 ${s.total} 个单词 · 当前显示 ${items.size} 个"
        bar.max = 100
        bar.progress = if (s.total == 0) 0 else s.unlocked * 100 / s.total
        tvEmpty.show(items.isEmpty())
        chips.forEachIndexed { i, c ->
            val on = (i - 1) == filter
            c.background = a.rounded(if (on) Pal.primary else Pal.softGrey, 18f)
            c.setTextColor(if (on) Color.WHITE else Pal.textSub)
        }
        renderRows(keepScroll = false)
    }

    /** 把当前筛选结果组装成 Unit → Day → 单词 的行列表；搜索时临时全部展开，保证结果可见 */
    private fun renderRows(keepScroll: Boolean) {
        val first = if (keepScroll) list.firstVisiblePosition else 0
        val top = if (keepScroll) (list.getChildAt(0)?.top ?: 0) else 0
        val searching = etSearch.text.toString().isNotBlank()
        val out = mutableListOf<Row>()

        for (unit in buildSections(items)) {
            var unitTotal = 0
            var unitUnlocked = 0
            unit.days.forEach { d ->
                unitTotal += d.words.size
                unitUnlocked += d.words.count { it.state > 0 }
            }
            val unitFolded = !searching && foldedUnits.contains(unit.title)
            out.add(Row.UnitHeader(unit.title, unitTotal, unitUnlocked, unitFolded))
            if (unitFolded) continue

            for (day in unit.days) {
                val hasDayHeader = day.title.isNotEmpty()
                val dayFolded = hasDayHeader && !searching && foldedDays.contains(day.key)
                if (hasDayHeader) {
                    out.add(
                        Row.DayHeader(
                            day.title,
                            day.words.size,
                            day.words.count { it.state > 0 },
                            dayFolded,
                            day.key
                        )
                    )
                }
                if (!dayFolded) day.words.forEach { out.add(Row.WordRow(it)) }
            }
        }

        rows = out
        adapter.notifyDataSetChanged()
        if (keepScroll) list.setSelectionFromTop(first, top)
    }

    /** 组名 → Unit / Day 两层。兼容「Unit 1 Day 2」「Unit 1」「Day 1」「未分组」和自定义组名。 */
    private fun buildSections(words: List<Word>): List<UnitSection> {
        val byGroup = LinkedHashMap<String, MutableList<Word>>()
        for (w in words) {
            val g = w.groupName.trim()
            byGroup.getOrPut(g) { mutableListOf() }.add(w)
        }

        val unitMap = LinkedHashMap<String, UnitSection>()
        for ((group, ws) in byGroup) {
            val unitNum = unitRegex.find(group)?.groupValues?.get(1)?.toLongOrNull()
            val dayNum = dayRegex.find(group)?.groupValues?.get(1)?.toLongOrNull()
            val unitTitle: String
            val dayTitle: String
            when {
                unitNum != null -> {
                    unitTitle = "Unit $unitNum"
                    dayTitle = if (dayNum != null) "Day $dayNum" else ""
                }
                dayNum != null -> {
                    unitTitle = "未分单元"
                    dayTitle = "Day $dayNum"
                }
                group.isEmpty() -> {
                    unitTitle = "未分组"
                    dayTitle = ""
                }
                else -> {
                    unitTitle = group
                    dayTitle = ""
                }
            }

            val unit = unitMap.getOrPut(unitTitle) { UnitSection(unitTitle, unitNum) }
            val key = "$unitTitle\u0000$dayTitle"
            val day = unit.days.firstOrNull { it.key == key }
                ?: DaySection(dayTitle, key).also {
                    it.dayNum = dayNum
                    unit.days.add(it)
                }
            day.words.addAll(ws)
        }

        unitMap.values.forEach { unit ->
            unit.days.sortWith(compareBy<DaySection>({ it.dayNum ?: -1L }, { it.title }))
        }
        return unitMap.values.sortedWith(
            compareBy<UnitSection>(
                { unitRank(it.title) },
                { it.unitNum ?: Long.MAX_VALUE },
                { it.title }
            )
        )
    }

    private fun unitRank(title: String): Int = when {
        title == "未分组" -> 3
        title == "未分单元" -> 2
        title.startsWith("Unit ") -> 0
        else -> 1
    }
}
