package com.wordcool.app

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.Toast

/**
 * 设计系统 v2「墨卡 / INK CARD」的唯一色板来源。
 * 本次视觉重设计只更换数值与圆角收敛系数，公开函数签名、参数与返回值一律不变，
 * 因此各页面控制器无需改动一行调用代码。
 */
object Pal {
    private var res: android.content.res.Resources? = null

    fun init(context: Context) {
        res = context.resources
    }

    private fun c(id: Int, fallback: Int): Int = res?.getColor(id) ?: fallback

    val primary get() = c(R.color.primary, 0xFFC2402F.toInt())      // 朱红（亮）/ 紫（暗）
    val accent get() = c(R.color.accent, 0xFF16181D.toInt())
    val text get() = c(R.color.text, 0xFF16181D.toInt())
    val textSub get() = c(R.color.text_sub, 0xFF6B6F76.toInt())
    val white get() = c(R.color.white, 0xFFFFFFFF.toInt())
    val softBlue get() = c(R.color.soft_blue, 0xFFEDE9E1.toInt())
    val softGrey get() = c(R.color.soft_grey, 0xFFEFECE6.toInt())
    val track get() = c(R.color.track, 0xFFDDD8CF.toInt())
    val locked get() = c(R.color.locked, 0xFF5C6067.toInt())
    val learning get() = c(R.color.learning, 0xFFC2402F.toInt())
    val mastered get() = c(R.color.mastered, 0xFF16181D.toInt())
    val danger get() = c(R.color.danger, 0xFFB23A2E.toInt())
    val card get() = c(R.color.card, 0xFFFDFCF9.toInt())
    val line get() = c(R.color.line, 0xFFD8D3C9.toInt())
}

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

/**
 * 全站圆角统一收敛为玻璃圆角；签名与默认值保持不变，
 * 旧调用点（12f / 18f / 10f）自动获得一致的圆润观感。
 */
fun Context.rounded(
    fill: Int,
    radiusDp: Float = 12f,
    strokeColor: Int? = null,
    strokePx: Int = 1
): GradientDrawable = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radiusDp * resources.displayMetrics.density
    if (strokeColor != null) setStroke(strokePx, strokeColor)
}

/** 设置页 / 学习页的程序化开关：选中用品牌渐变，未选中用半透明玻璃渐变 */
fun Context.glassToggle(selected: Boolean, radiusDp: Float = 12f): GradientDrawable {
    val density = resources.displayMetrics.density
    val colors = if (selected) {
        intArrayOf(getColor(R.color.primary), getColor(R.color.primary_dark))
    } else {
        intArrayOf(getColor(R.color.glass_card_top), getColor(R.color.glass_card_bottom))
    }
    return GradientDrawable(GradientDrawable.Orientation.TL_BR, colors).apply {
        cornerRadius = radiusDp * density
        setStroke(
            density.toInt().coerceAtLeast(1),
            getColor(if (selected) R.color.glass_border_bright else R.color.glass_border)
        )
    }
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
