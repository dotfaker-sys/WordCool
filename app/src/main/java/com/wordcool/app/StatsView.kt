package com.wordcool.app

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/** 只放统计数据；重置、版本更新等操作已移到设置页。 */
class StatsView(private val a: MainActivity, private val root: View) {

    private val statTotal = root.findViewById<TextView>(R.id.statTotal)
    private val statUnlocked = root.findViewById<TextView>(R.id.statUnlocked)
    private val statMastered = root.findViewById<TextView>(R.id.statMastered)
    private val statToday = root.findViewById<TextView>(R.id.statToday)
    private val tvUnlockDetail = root.findViewById<TextView>(R.id.tvUnlockDetail)
    private val barUnlock = root.findViewById<ProgressBar>(R.id.barUnlock)
    private val tvAccuracy = root.findViewById<TextView>(R.id.tvAccuracy)
    private val barAccuracy = root.findViewById<ProgressBar>(R.id.barAccuracy)
    private val tvStreak = root.findViewById<TextView>(R.id.tvStreak)
    private val chartWeek = root.findViewById<LinearLayout>(R.id.chartWeek)
    private val tvDistribution = root.findViewById<TextView>(R.id.tvDistribution)

    fun refresh() {
        val s = a.db.stats()
        statTotal.text = s.total.toString()
        statUnlocked.text = s.unlocked.toString()
        statMastered.text = s.mastered.toString()
        statToday.text = s.today.toString()

        barUnlock.max = 100
        barUnlock.progress = if (s.total == 0) 0 else s.unlocked * 100 / s.total
        tvUnlockDetail.text = "已解锁 ${s.unlocked} / ${s.total} 个（未解锁 ${s.locked}）"

        val ans = s.right + s.wrong
        barAccuracy.max = 100
        barAccuracy.progress = if (ans == 0) 0 else s.right * 100 / ans
        tvAccuracy.text = if (ans == 0) "还没有答题记录"
        else "答对 ${s.right} 次 / 共 $ans 次 · ${s.right * 100 / ans}%"

        tvStreak.text = "连续打卡 ${s.streak} 天 · 今日已学 ${s.today} 个"
        tvDistribution.text = "未解锁 ${s.locked} · 学习中 ${s.learning} · 已掌握 ${s.mastered}"
        drawWeek(s.week)
    }

    private fun drawWeek(week: List<Pair<String, Int>>) {
        chartWeek.removeAllViews()
        val maxV = (week.maxOfOrNull { it.second } ?: 0).coerceAtLeast(1)
        for ((label, count) in week) {
            val col = LinearLayout(a).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.MATCH_PARENT, 1f
                )
            }
            val num = TextView(a).apply {
                text = if (count > 0) count.toString() else ""
                textSize = 10f
                gravity = Gravity.CENTER
                setTextColor(Pal.textSub)
            }
            val heightPx = a.dp(4 + (72f * count / maxV).toInt())
            val bar = View(a).apply {
                background = a.rounded(if (count > 0) Pal.accent else Pal.track, 5f)
                layoutParams = LinearLayout.LayoutParams(a.dp(16), heightPx).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            }
            val lab = TextView(a).apply {
                text = label
                textSize = 9f
                gravity = Gravity.CENTER
                setTextColor(Pal.textSub)
            }
            col.addView(num)
            col.addView(bar)
            col.addView(lab)
            chartWeek.addView(col)
        }
    }
}
