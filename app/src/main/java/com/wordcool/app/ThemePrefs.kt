package com.wordcool.app

import android.content.Context
import android.content.res.Configuration

/**
 * 亮色 / 暗色 / 跟随系统。
 *
 * 仅保存偏好；真正切资源发生在 Activity 的 attachBaseContext：
 * 通过 uiMode 让 values-night 生效，整个 App（不止设置页）一起换主题。
 */
object ThemePrefs {
    const val SYSTEM = 0
    const val LIGHT = 1
    const val DARK = 2

    private const val PREF = "wordcool"
    private const val KEY = "ui_mode"

    fun mode(ctx: Context): Int =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getInt(KEY, SYSTEM).coerceIn(0, 2)

    fun set(ctx: Context, mode: Int) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putInt(KEY, mode.coerceIn(0, 2)).apply()
    }

    fun wrap(base: Context): Context {
        val m = mode(base)
        if (m == SYSTEM) return base
        val config = Configuration(base.resources.configuration)
        val night = if (m == DARK) {
            Configuration.UI_MODE_NIGHT_YES
        } else {
            Configuration.UI_MODE_NIGHT_NO
        }
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return base.createConfigurationContext(config)
    }
}
