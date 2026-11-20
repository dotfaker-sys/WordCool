package com.wordcool.app

import android.graphics.Color
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LearnView(private val a: MainActivity, private val root: View) {

    companion object {
        private const val IDLE = 0
        private const val STUDY = 1
        private const val QUIZ = 2
        private val LETTERS = listOf("A", "B", "C", "D")
    }

    private val tvTodayTitle = root.findViewById<TextView>(R.id.tvTodayTitle)
    private val tvTodayInfo = root.findViewById<TextView>(R.id.tvTodayInfo)
    private val todayBar = root.findViewById<ProgressBar>(R.id.todayBar)
    private val btnPickGroup = root.findViewById<TextView>(R.id.btnPickGroup)
    private val btnUnlockNew = root.findViewById<TextView>(R.id.btnUnlockNew)
    private val btnStart = root.findViewById<TextView>(R.id.btnStart)

    private val cardStudy = root.findViewById<View>(R.id.cardStudy)
    private val cardQuiz = root.findViewById<View>(R.id.cardQuiz)
    private val learnEmpty = root.findViewById<View>(R.id.learnEmpty)
    private val tvEmptyTitle = root.findViewById<TextView>(R.id.tvEmptyTitle)
    private val tvEmptyHint = root.findViewById<TextView>(R.id.tvEmptyHint)
    private val btnMore = root.findViewById<TextView>(R.id.btnMore)

    private val tvCardLabel = root.findViewById<TextView>(R.id.tvCardLabel)
    private val tvCardWord = root.findViewById<TextView>(R.id.tvCardWord)
    private val tvCardPhonetic = root.findViewById<TextView>(R.id.tvCardPhonetic)
    private val btnSpeak = root.findViewById<TextView>(R.id.btnSpeak)
    private val btnSpeakUk = root.findViewById<TextView>(R.id.btnSpeakUk)
    private val answerBox = root.findViewById<View>(R.id.answerBox)
    private val tvCardMeaning = root.findViewById<TextView>(R.id.tvCardMeaning)
    private val tvCardExample = root.findViewById<TextView>(R.id.tvCardExample)
    private val tvCardNote = root.findViewById<TextView>(R.id.tvCardNote)
    private val btnReveal = root.findViewById<TextView>(R.id.btnReveal)
    private val gradeRow = root.findViewById<View>(R.id.gradeRow)
    private val gradeBad = root.findViewById<TextView>(R.id.gradeBad)
    private val gradeFuzzy = root.findViewById<TextView>(R.id.gradeFuzzy)
    private val gradeGood = root.findViewById<TextView>(R.id.gradeGood)
    private val tvGradeResult = root.findViewById<TextView>(R.id.tvGradeResult)
    private val btnStudyNext = root.findViewById<TextView>(R.id.btnStudyNext)

    private val tvQuizWord = root.findViewById<TextView>(R.id.tvQuizWord)
    private val options: List<TextView> = listOf(
        root.findViewById<TextView>(R.id.opt0),
        root.findViewById<TextView>(R.id.opt1),
        root.findViewById<TextView>(R.id.opt2),
        root.findViewById<TextView>(R.id.opt3)
    )
    private val tvQuizResult = root.findViewById<TextView>(R.id.tvQuizResult)
    private val btnQuizNext = root.findViewById<TextView>(R.id.btnQuizNext)

    private var mode = IDLE
    private var queue: MutableList<Word> = mutableListOf()
    private var current: Word? = null
    private var quizWord: Word? = null
    private var answerIndex = 0
    private var quizAnswered = false
    private var studiedSinceQuiz = 0
    private var learnGroup: String? = null   // null=全部词组；""=未分组

    init {
        btnPickGroup.setOnClickListener {
            a.showGroupPicker(learnGroup) { picked ->
                learnGroup = picked
                queue.clear()
                mode = IDLE
                current = null
                studiedSinceQuiz = 0
                a.dataChanged()
            }
        }
        btnUnlockNew.setOnClickListener {
            val n = a.db.unlockNew(a.db.dailyGoal, learnGroup)
            // 清掉缓存队列，否则刚解锁的词要等旧队列排完才出现
            queue.clear()
            a.toast(if (n > 0) "解锁了 $n 个新词，开始学吧" else "这一组没有待解锁的新词了")
            a.dataChanged()
        }
        btnStart.setOnClickListener { startOrNext() }
        btnMore.setOnClickListener {
            val n = a.db.unlockNew(a.db.dailyGoal, learnGroup)
            if (n > 0) {
                queue.clear()
                a.toast("又解锁了 $n 个新词")
            }
            startOrNext()
        }
        btnSpeakUk.setOnClickListener {
            val w = current?.word.orEmpty()
            if (w.isNotEmpty()) a.speak(w, MainActivity.ACCENT_UK)
        }
        btnSpeak.setOnClickListener {
            val w = current?.word.orEmpty()
            if (w.isNotEmpty()) a.speak(w, MainActivity.ACCENT_US)
        }
        btnReveal.setOnClickListener {
            answerBox.show(true)
            btnReveal.show(false)
            gradeRow.show(true)
        }
        gradeBad.setOnClickListener { grade(0) }
        gradeFuzzy.setOnClickListener { grade(1) }
        gradeGood.setOnClickListener { grade(2) }
        btnStudyNext.setOnClickListener { startOrNext() }
        options.forEachIndexed { i, t -> t.setOnClickListener { onOption(i) } }
        btnQuizNext.setOnClickListener { startOrNext() }
    }

    /** 只更新概览；正在看卡片/做题时不被打断 */
    fun refresh() {
        val s = a.db.stats()
        val due = a.db.dueWords(groupFilter = learnGroup)
        btnPickGroup.text = "组别：" +
            (if (learnGroup == null) "全部词组" else a.db.groupLabel(learnGroup!!))
        tvTodayTitle.text = "今日计划 · " +
            SimpleDateFormat("M月d日", Locale.getDefault()).format(Date())
        tvTodayInfo.text = "待学 ${due.size} 个 · 今日已学 ${s.today} 个 · 连续 ${s.streak} 天"
        val goal = (s.today + due.size).coerceAtLeast(1)
        todayBar.max = 100
        todayBar.progress = s.today * 100 / goal
        if (mode == IDLE) showIdle(due.isEmpty(), s.today)
    }

    private fun showIdle(noDue: Boolean, todayCount: Int) {
        cardStudy.show(false)
        cardQuiz.show(false)
        learnEmpty.show(true)
        val scope = if (learnGroup == null) "" else "「${a.db.groupLabel(learnGroup!!)}」"
        when {
            noDue && todayCount > 0 -> {
                tvEmptyTitle.text = "今天的词都学完啦"
                tvEmptyHint.text = "学过的词会按 1/2/4/7/15/30 天回来复习。想多学点，可以再解锁一批新词。"
                btnMore.text = "再解锁一批"
            }
            noDue -> {
                tvEmptyTitle.text = "这一组还没有可学的词"
                tvEmptyHint.text = "先去「导入」页放进你的单词表，回来解锁；也可以点下面的按钮解锁${scope}新词。"
                btnMore.text = "解锁今日新词"
            }
            else -> {
                tvEmptyTitle.text = "准备好了，开始吧"
                tvEmptyHint.text = "一次只看一个词：先看卡片自评，自己点「下一个」再换词；每学 3 张穿插一道小测。连对 3 次升级为「已掌握」。"
                btnMore.text = "开始学习"
            }
        }
    }

    private fun startOrNext() {
        // 顶部的「开始学习」在卡片/测验显示时仍然可见，别让它把当前这一张悄悄跳过去
        if (mode == QUIZ && !quizAnswered) {
            a.toast("先答完这道小测")
            return
        }
        if (mode == STUDY && btnStudyNext.visibility != View.VISIBLE) {
            a.toast("先看答案并自评，再点「下一个」")
            return
        }
        if (queue.isEmpty()) queue = a.db.dueWords(groupFilter = learnGroup).toMutableList()
        if (studiedSinceQuiz >= 3) {
            if (tryQuiz()) return
            // 释义不足以出小测时别每次翻页都白跑一遍查询
            studiedSinceQuiz = 0
        }
        if (queue.isEmpty()) {
            mode = IDLE
            studiedSinceQuiz = 0
            current = null
            a.dataChanged()
            a.toast("这一轮学完啦")
            return
        }
        showStudy(queue.removeAt(0))
    }

    private fun showStudy(w: Word) {
        mode = STUDY
        current = w
        cardStudy.show(true)
        cardQuiz.show(false)
        learnEmpty.show(false)
        answerBox.show(false)
        btnReveal.show(true)
        gradeRow.show(false)
        tvGradeResult.show(false)
        btnStudyNext.show(false)
        tvCardWord.text = w.word
        tvCardPhonetic.text = phoneticText(w)
        tvCardLabel.text = "${a.db.groupLabel(w.groupName)} · ${stateName(w.state)} · 连对 ${w.streak} 次"
        tvCardMeaning.text = if (w.meaning.isBlank()) "（还没填释义，去词库里补一下）" else w.meaning
        tvCardExample.text = w.example
        tvCardNote.text = w.note
    }

    /** 自评后停在当前这张卡，由用户点「下一个」控制节奏 */
    private fun grade(g: Int) {
        val w = current ?: return
        a.db.recordGrade(w.id, g)
        studiedSinceQuiz++
        val fresh = a.db.getWord(w.id)
        val left = (fresh?.nextReview ?: WordDb.today()) - WordDb.today()
        tvGradeResult.text = when (g) {
            0 -> "已记录「不认识」· 今天会再出现"
            1 -> "已记录「有点模糊」· $left 天后复习"
            else -> if (fresh?.state == 2) "已升级为「已掌握」· $left 天后复习"
            else "已记录「认识」· $left 天后复习"
        }
        tvGradeResult.setTextColor(if (g == 0) Pal.danger else Pal.textSub)
        gradeRow.show(false)
        tvGradeResult.show(true)
        btnStudyNext.show(true)
        a.dataChanged()
    }

    /** 每学 3 张卡插入一道 4 选 1；释义不足时自动跳过 */
    private fun tryQuiz(): Boolean {
        val pool = a.db.quizWords(learnGroup).filter { it.id != current?.id }
        if (pool.isEmpty()) return false
        val w = pool.random()
        val distractors = a.db.randomMeanings(w.word, 8)
            .filter { it.isNotBlank() && it != w.meaning }
            .distinct()
            .take(3)
        if (distractors.size < 3) return false

        quizWord = w
        val all = (distractors + w.meaning).shuffled()
        answerIndex = all.indexOf(w.meaning)
        quizAnswered = false
        mode = QUIZ
        cardStudy.show(false)
        cardQuiz.show(true)
        learnEmpty.show(false)
        tvQuizWord.text = w.word
        options.forEachIndexed { i, t ->
            t.text = "${LETTERS[i]}. ${all[i]}"
            t.background = a.rounded(Pal.card, 12f, Pal.line)
            t.setTextColor(Pal.text)
        }
        tvQuizResult.text = ""
        btnQuizNext.show(false)
        return true
    }

    private fun onOption(i: Int) {
        if (quizAnswered || mode != QUIZ) return
        quizAnswered = true
        val w = quizWord ?: return
        val correct = i == answerIndex
        a.db.recordGrade(w.id, if (correct) 2 else 0)
        options.forEachIndexed { idx, t ->
            when (idx) {
                answerIndex -> {
                    t.background = a.rounded(Pal.mastered, 12f)
                    t.setTextColor(Color.WHITE)
                }
                i -> {
                    t.background = a.rounded(Pal.danger, 12f)
                    t.setTextColor(Color.WHITE)
                }
                else -> {}
            }
        }
        tvQuizResult.text = if (correct) "答对了 · 连对 +1" else "正确答案：${w.meaning}"
        tvQuizResult.setTextColor(if (correct) Pal.mastered else Pal.danger)
        btnQuizNext.show(true)
        studiedSinceQuiz = 0
        a.dataChanged()
    }
}
