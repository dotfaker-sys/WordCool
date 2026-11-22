package com.wordcool.app

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Toast

/**
 * 设计系统 v2「墨卡 / INK CARD」的唯一色板来源。
 * 本次视觉重设计只更换数值与圆角收敛系数，公开函数签名、参数与返回值一律不变，
 * 因此各页面控制器无需改动一行调用代码。
 */
object Pal {
    val primary: Int = Color.parseColor("#C2402F")      // 朱红，唯一强调色
    val accent: Int = Color.parseColor("#16181D")      // 墨黑，进度与强调
    val text: Int = Color.parseColor("#16181D")
    val textSub: Int = Color.parseColor("#6B6F76")    // 对纸底 ≈ 4.9:1
    val white: Int = Color.parseColor("#FFFFFF")
    val softBlue: Int = Color.parseColor("#EDE9E1")   // 选中态浅米（保留原名，避免动接口）
    val softGrey: Int = Color.parseColor("#EFECE6")
    val track: Int = Color.parseColor("#DDD8CF")
    val locked: Int = Color.parseColor("#5C6067")     // 深石墨，保证白字 ≥ 4.5:1
    val learning: Int = Color.parseColor("#C2402F")
    val mastered: Int = Color.parseColor("#16181D")
    val danger: Int = Color.parseColor("#B23A2E")
    val card: Int = Color.parseColor("#FDFCF9")
    val line: Int = Color.parseColor("#D8D3C9")
}

/** Obsidian 深色页专用色板（只给设置页 / 导入页用） */
object Obs {
    val bg: Int = Color.parseColor("#1E1E1E")
    val surface: Int = Color.parseColor("#262626")
    val surfaceAlt: Int = Color.parseColor("#2D2D2D")
    val text: Int = Color.parseColor("#DADADA")
    val sub: Int = Color.parseColor("#9A9A9A")
    val line: Int = Color.parseColor("#3A3A3A")
    val accent: Int = Color.parseColor("#7F6DF2")
    val accentSoft: Int = Color.parseColor("#2F2A45")
    val danger: Int = Color.parseColor("#E06C75")
    val white: Int = Color.parseColor("#FFFFFF")
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/**
 * 全站圆角统一收敛为微圆角（约原值的 1/3，最小 2dp）。
 * 签名与默认值保持不变，旧调用点（12f / 18f / 10f）自动获得一致的编辑式观感。
 */
fun Context.rounded(
    fill: Int,
    radiusDp: Float = 12f,
    strokeColor: Int? = null,
    strokePx: Int = 1
): GradientDrawable = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = (radiusDp * 0.35f).coerceAtLeast(2f) * resources.displayMetrics.density
    if (strokeColor != null) setStroke(strokePx, strokeColor)
}

fun Context.toast(message: String) {
    Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
}

fun View.show(visible: Boolean) {
    visibility = if (visible) View.VISIBLE else View.GONE
}

fun stateName(state: Int): String = when (state) {
    0 -> "未解锁"
    1 -> "学习中"
    else -> "已掌握"
}

/** 状态色均为深底，配合白色状态文字满足对比度要求 */
fun stateColor(state: Int): Int = when (state) {
    0 -> Pal.locked
    1 -> Pal.learning
    else -> Pal.mastered
}

/** v5：学习卡片 / 词条详情用的双音标文本。
 *  两套都有 → 「UK /…/    US /…/」；只有一套 → 只显示主音标，不编造另一套。 */
fun phoneticText(w: Word): String {
    val uk = w.phoneticUk.trim()
    val us = w.phoneticUs.trim()
    return when {
        uk.isNotEmpty() && us.isNotEmpty() -> "UK  $uk      US  $us"
        uk.isNotEmpty() -> "UK  $uk"
        us.isNotEmpty() -> "US  $us"
        w.phonetic.isNotBlank() -> w.phonetic.trim()
        else -> ""
    }
}

/** 词库列表只展示英音；没有英音时才回退到美音 / 通用音标，不编造 */
fun phoneticShort(w: Word): String = when {
    w.phoneticUk.isNotBlank() -> w.phoneticUk.trim()
    w.phoneticUs.isNotBlank() -> w.phoneticUs.trim()
    else -> w.phonetic.trim()
}
