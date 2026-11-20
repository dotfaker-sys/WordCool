package com.wordcool.app

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * v9：应用内检查更新。
 *
 * 这是全应用**唯一**会联网的地方，且只在用户点「检查更新」、或用户自己打开
 * 「启动时自动检查」开关时才发一次请求。词库、导入、音标、发音、复习依旧全程离线。
 *
 * 数据只读取 GitHub 公开 Release 接口（只读 GET，不传任何账号 / token / 本地词库内容），
 * 用系统自带的 HttpURLConnection + org.json，零第三方依赖。本应用**不自动下载、
 * 不自动安装** APK：发现新版只是弹窗，点「去下载」才用系统浏览器打开下载页。
 */
object UpdateChecker {

    private const val PREF = "wordcool_settings"
    private const val KEY_AUTO = "auto_check_update"
    private const val API =
        "https://api.github.com/repos/dotfaker-sys/WordCool/releases/latest"
    private const val RELEASES_PAGE = "https://github.com/dotfaker-sys/WordCool/releases"

    data class Info(
        val version: String,      // tag_name，如 v1.2.0
        val name: String,
        val notes: String,
        val pageUrl: String,
        val apkUrl: String
    )

    sealed class Result {
        data class Ok(val info: Info) : Result()
        data class Fail(val reason: String) : Result()
    }

    /** 当前安装的版本名，来自 PackageManager，不依赖 BuildConfig */
    fun currentVersion(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "1.0"
    } catch (e: Exception) {
        "1.0"
    }

    /** 把 v1.2.0 / 1.2 / 1.2.0-beta 归一成数字三段，逐段比较。
     *  返回 >0 表示远端版本更新，=0 相同，<0 表示装着的反而更新（如本地编译版）。 */
    fun compare(local: String, remote: String): Int {
        fun parts(v: String): List<Int> {
            val core = v.trim()
                .removePrefix("v").removePrefix("V")
                .substringBefore('-').substringBefore('+')
            return core.split('.').map {
                it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0
            }.take(4)
        }
        val a = parts(local)
        val b = parts(remote)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return y.compareTo(x)
        }
        return 0
    }

    fun isAutoEnabled(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean(KEY_AUTO, false)

    fun toggleAuto(ctx: Context): Boolean {
        val p = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        val next = !p.getBoolean(KEY_AUTO, false)
        p.edit().putBoolean(KEY_AUTO, next).apply()
        return next
    }

    /** 启动时静默检查：只有开了开关、且真的有新版本才弹窗；没更新不打扰 */
    fun autoCheck(a: MainActivity) {
        if (isAutoEnabled(a)) check(a, silent = true)
    }

    /** 手动检查：一定会给反馈（已是最新 / 发现新版 / 失败原因） */
    fun check(a: MainActivity, silent: Boolean) {
        if (!silent) toast(a, "正在检查更新…")
        Thread {
            val r = fetch()
            a.runOnUiThread {
                if (a.isFinishing || a.isDestroyed) return@runOnUiThread
                when (r) {
                    is Result.Fail -> {
                        if (silent) return@runOnUiThread
                        AlertDialog.Builder(a)
                            .setTitle("检查更新失败")
                            .setMessage(
                                "${r.reason}\n\n这只影响「检查更新」这一个按钮，" +
                                    "背单词、导入、发音全部不受影响，也不需要联网。\n\n" +
                                    "可以直接打开 Releases 页面看有没有新版。"
                            )
                            .setNegativeButton("稍后", null)
                            .setPositiveButton("打开下载页") { _, _ -> openUrl(a, RELEASES_PAGE) }
                            .show()
                    }
                    is Result.Ok -> {
                        val cur = currentVersion(a)
                        if (compare(cur, r.info.version) <= 0) {
                            if (!silent) toast(a, "已是最新版本 v$cur")
                            return@runOnUiThread
                        }
                        showNewVersion(a, cur, r.info)
                    }
                }
            }
        }.start()
    }

    private fun showNewVersion(a: MainActivity, cur: String, info: Info) {
        val notes = info.notes
            .lineSequence()
            .filter { it.isNotBlank() && !it.trimStart().startsWith("http") }
            .take(6)
            .joinToString("\n")
            .take(360)
        val msg = buildString {
            append("当前版本：v$cur\n最新版本：${info.name.ifBlank { info.version }}\n")
            if (notes.isNotBlank()) append("\n$notes\n")
            append(
                "\n本应用不会自动下载或安装。点「去下载」会用系统浏览器打开 Release 页面，" +
                    "你自己下载 APK 后手动安装。"
            )
        }
        AlertDialog.Builder(a)
            .setTitle("发现新版本 ${info.version}")
            .setMessage(msg)
            .setNegativeButton("稍后", null)
            .setPositiveButton("去下载") { _, _ ->
                openUrl(a, info.apkUrl.ifBlank { info.pageUrl.ifBlank { RELEASES_PAGE } })
            }
            .show()
    }

    private fun openUrl(a: MainActivity, url: String) {
        try {
            a.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast(a, "打不开浏览器：${e.message}")
        }
    }

    private fun fetch(): Result = try {
        val conn = URL(API).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "GET"
            // GitHub API 要求 User-Agent，不设会直接 403
            conn.setRequestProperty("User-Agent", "WordCool-Android")
            conn.setRequestProperty("Accept", "application/vnd.github+json")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.instanceFollowRedirects = true
            val code = conn.responseCode
            if (code != 200) {
                val hint = if (code == 403) "GitHub 接口限流（未登录每小时 60 次），稍后再试"
                else "HTTP $code"
                Result.Fail(hint)
            } else {
                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                parse(body)
            }
        } finally {
            conn.disconnect()
        }
    } catch (e: java.net.SocketTimeoutException) {
        Result.Fail("连接超时，检查网络后重试")
    } catch (e: java.net.UnknownHostException) {
        Result.Fail("连不上 GitHub，当前没有可用网络")
    } catch (e: Exception) {
        Result.Fail(e.message ?: "未知错误")
    }

    /** 解析公开 Release JSON；任何字段缺失都降级，不让应用崩。
     *  注意：表达式体函数里不能写 return，所以空版本号走 if/else 分支而不是提前返回。 */
    private fun parse(body: String): Result = try {
        val o = JSONObject(body)
        val tag = o.optString("tag_name").trim()
        if (tag.isBlank()) {
            Result.Fail("返回数据里没有版本号")
        } else {
            var apk = ""
            val arr = o.optJSONArray("assets")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val u = arr.optJSONObject(i)?.optString("browser_download_url").orEmpty()
                    if (u.endsWith(".apk", true)) { apk = u; break }
                }
            }
            Result.Ok(
                Info(
                    version = tag,
                    name = o.optString("name").trim(),
                    notes = o.optString("body").trim(),
                    pageUrl = o.optString("html_url").ifBlank { RELEASES_PAGE },
                    apkUrl = apk
                )
            )
        }
    } catch (e: Exception) {
        Result.Fail("返回数据格式不对：${e.message}")
    }

    private fun toast(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
