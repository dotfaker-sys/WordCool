package com.wordcool.app

import android.app.AlertDialog
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

    private val etSearch = root.findViewById<EditText>(R.id.etSearch)
    private val list = root.findViewById<ListView>(R.id.listWords)
    private val tvEmpty = root.findViewById<TextView>(R.id.tvEmpty)
    private val tvCount = root.findViewById<TextView>(R.id.tvCount)
    private val bar = root.findViewById<ProgressBar>(R.id.unlockBar)
    private val btnGroupFilter = root.findViewById<TextView>(R.id.btnGroupFilter)
    private val chips: List<TextView> = listOf(
        root.findViewById<TextView>(R.id.chipAll),
        root.findViewById<TextView>(R.id.chipLocked),
        root.findViewById<TextView>(R.id.chipLearning),
        root.findViewById<TextView>(R.id.chipMastered)
    )
    private var filter = -1                 // -1 全部 / 0 未解锁 / 1 学习中 / 2 已掌握
    private var groupFilter: String? = null // null 全部 / "" 未分组 / 其他精确组名
    private var items: List<Word> = emptyList()

    private val adapter = object : BaseAdapter() {
        override fun getCount(): Int = items.size
        override fun getItem(position: Int): Word = items[position]
        override fun getItemId(position: Int): Long = items[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val v = convertView ?: LayoutInflater.from(a).inflate(R.layout.item_word, parent, false)
            val w = items[position]
            v.findViewById<TextView>(R.id.tvWord).text = w.word
            v.findViewById<TextView>(R.id.tvPhonetic).text = phoneticShort(w)
            v.findViewById<TextView>(R.id.tvMeaning).text =
                if (w.meaning.isBlank()) "（未填释义，点进来补一下）" else w.meaning
            v.findViewById<TextView>(R.id.tvMeta).text =
                "${a.db.groupLabel(w.groupName)} · 连对 ${w.streak} · 答对 ${w.right} / 答错 ${w.wrong}"

            val left = w.nextReview - WordDb.today()
            v.findViewById<TextView>(R.id.tvReview).text = when {
                w.state == 0 -> "待解锁"
                left > 0 -> "$left 天后复习"
                else -> "今天该复习"
            }

            val st = v.findViewById<TextView>(R.id.tvState)
            st.text = stateName(w.state)
            st.background = a.rounded(stateColor(w.state), 18f)
            st.setTextColor(Color.WHITE)
            return v
        }
    }

    init {
        list.adapter = adapter
        chips.forEachIndexed { i, c ->
            c.setOnClickListener {
                filter = i - 1
                refresh()
            }
        }
        btnGroupFilter.setOnClickListener {
            a.showGroupPicker(groupFilter) { picked ->
                groupFilter = picked
                refresh()
            }
        }
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = refresh()
            override fun afterTextChanged(s: Editable?) {}
        })
        root.findViewById<TextView>(R.id.btnAdd).setOnClickListener {
            a.showWordDialog(null)
        }
        root.findViewById<TextView>(R.id.btnUnlock).setOnClickListener {
            // 按组筛选时，只解锁当前组的词，别把别的组一起放出来
            val n = a.db.unlockNew(a.db.dailyGoal, groupFilter)
            a.toast(if (n > 0) "解锁了 $n 个新词，去「学习」页开始吧" else "这一组没有待解锁的新词了")
            a.dataChanged()
        }
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, pos, _ ->
            if (pos < items.size) a.showWordDialog(items[pos])
        }
        list.setOnItemLongClickListener { _, _, pos, _ ->
            if (pos < items.size) {
                val w = items[pos]
                AlertDialog.Builder(a)
                    .setTitle("删除 ${w.word}？")
                    .setMessage("会连同这个单词的学习记录一起删除。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("删除") { _, _ ->
                        a.db.deleteWord(w.id)
                        a.dataChanged()
                    }.show()
            }
            true
        }
    }

    fun refresh() {
        items = a.db.getWords(filter, etSearch.text.toString(), groupFilter)
        val s = a.db.stats()
        btnGroupFilter.text = "组别：" +
            (if (groupFilter == null) "全部词组" else a.db.groupLabel(groupFilter!!))
        tvCount.text = "共 ${s.total} 个单词 · 当前显示 ${items.size} 个"
        bar.max = 100
        bar.progress = if (s.total == 0) 0 else s.unlocked * 100 / s.total
        tvEmpty.show(items.isEmpty())
        chips.forEachIndexed { i, c ->
            val on = (i - 1) == filter
            c.background = a.rounded(if (on) Pal.primary else Pal.softGrey, 18f)
            c.setTextColor(if (on) Color.WHITE else Pal.textSub)
        }
        adapter.notifyDataSetChanged()
    }
}
