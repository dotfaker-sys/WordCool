package com.wordcool.app

import android.app.AlertDialog
import android.view.View
import android.widget.EditText
import android.widget.TextView

/** 设置页：把原先散在导入页 / 统计页的学习、备份、发音、音标、更新入口集中到这里。 */
class SettingsView(private val a: MainActivity, private val root: View) {

    private val etDaily = root.findViewById<EditText>(R.id.etDaily)
    private val tvTtsStatus = root.findViewById<TextView>(R.id.tvTtsStatus)
    private val tvIpaStatus = root.findViewById<TextView>(R.id.tvIpaStatus)
    private val tvVersion = root.findViewById<TextView>(R.id.tvVersion)
    private val btnAutoUpdate = root.findViewById<TextView>(R.id.btnAutoUpdate)
    private val tvVaultStatus = root.findViewById<TextView>(R.id.tvVaultStatus)
    private val btnVault = root.findViewById<TextView>(R.id.btnVault)
    private val btnThemeSystem = root.findViewById<TextView>(R.id.btnThemeSystem)
    private val btnThemeLight = root.findViewById<TextView>(R.id.btnThemeLight)
    private val btnThemeDark = root.findViewById<TextView>(R.id.btnThemeDark)

    private val btnAccentUs = root.findViewById<TextView>(R.id.btnAccentUs)
    private val btnAccentUk = root.findViewById<TextView>(R.id.btnAccentUk)
    private val btnRateSlow = root.findViewById<TextView>(R.id.btnRateSlow)
    private val btnRateNormal = root.findViewById<TextView>(R.id.btnRateNormal)
    private val btnRateFast = root.findViewById<TextView>(R.id.btnRateFast)
    private val btnAutoIpa = root.findViewById<TextView>(R.id.btnAutoIpa)

    init {
        root.findViewById<TextView>(R.id.btnSaveDaily).setOnClickListener {
            val n = etDaily.text.toString().trim().toIntOrNull()
            if (n == null || n < 1 || n > 100) {
                a.toast("请填 1~100 之间的数字")
                return@setOnClickListener
            }
            a.db.dailyGoal = n
            a.toast("已设为每天解锁 $n 个新词")
            a.dataChanged()
        }

        root.findViewById<TextView>(R.id.btnExport).setOnClickListener { a.exportBackup() }
        listOf(
            btnThemeSystem to ThemePrefs.SYSTEM,
            btnThemeLight to ThemePrefs.LIGHT,
            btnThemeDark to ThemePrefs.DARK
        ).forEach { (button, mode) ->
            button.setOnClickListener {
                if (ThemePrefs.mode(a) != mode) {
                    ThemePrefs.set(a, mode)
                    a.recreate()
                }
            }
        }
        btnVault.setOnClickListener { a.pickVaultFolder() }

        root.findViewById<TextView>(R.id.btnReset).setOnClickListener {
            AlertDialog.Builder(a)
                .setTitle("重置学习进度？")
                .setMessage("所有词回到「未解锁」，并清空连对、答题记录和打卡。单词与释义会保留。")
                .setNegativeButton("取消", null)
                .setPositiveButton("重置") { _, _ ->
                    a.db.resetProgress()
                    a.dataChanged()
                    a.toast("进度已重置")
                }.show()
        }

        root.findViewById<TextView>(R.id.btnClear).setOnClickListener {
            AlertDialog.Builder(a)
                .setTitle("清空整个词库？")
                .setMessage("所有单词、释义、组别和学习记录都会被删除，无法撤销。建议先「导出词库备份」。")
                .setNegativeButton("取消", null)
                .setPositiveButton("确认清空") { _, _ ->
                    a.db.clearAll()
                    a.toast("词库已清空")
                    a.dataChanged()
                }.show()
        }

        btnAccentUs.setOnClickListener { a.setAccent(MainActivity.ACCENT_US) }
        btnAccentUk.setOnClickListener { a.setAccent(MainActivity.ACCENT_UK) }
        root.findViewById<TextView>(R.id.btnTtsSettings).setOnClickListener { a.openTtsSettings() }
        btnRateSlow.setOnClickListener { a.setSpeechRate(0.7f) }
        btnRateNormal.setOnClickListener { a.setSpeechRate(1.0f) }
        btnRateFast.setOnClickListener { a.setSpeechRate(1.3f) }
        root.findViewById<TextView>(R.id.btnSpeakSample).setOnClickListener { a.speakSample() }
        btnAutoIpa.setOnClickListener { a.setAutoIpa(!a.db.autoIpa) }
        root.findViewById<TextView>(R.id.btnFillIpa).setOnClickListener { a.fillAllPhonetics() }

        // 版本与更新原来在统计页底部，现在统一放到设置
        tvVersion.text = "当前版本 v${UpdateChecker.currentVersion(a)}"
        renderAutoLabel()
        root.findViewById<TextView>(R.id.btnCheckUpdate).setOnClickListener {
            UpdateChecker.check(a, silent = false)
        }
        btnAutoUpdate.setOnClickListener {
            val on = UpdateChecker.toggleAuto(a)
            renderAutoLabel()
            a.toast(
                if (on) "已开启：每次打开应用会静默检查一次，只有发现新版才弹窗"
                else "已关闭：只有点「检查更新」才会联网"
            )
        }
    }

    private fun renderAutoLabel() {
        btnAutoUpdate.text =
            "启动时自动检查：${if (UpdateChecker.isAutoEnabled(a)) "开" else "关"}"
    }

    fun refresh() {
        val n = a.db.dailyGoal.toString()
        if (etDaily.text.toString() != n) {
            etDaily.setText(n)
            etDaily.setSelection(etDaily.text.length)
        }
        tvTtsStatus.text = a.ttsStatusText()
        tvIpaStatus.text = a.ipaDictStatusText()
        tvVersion.text = "当前版本 v${UpdateChecker.currentVersion(a)}"
        val vaultOn = VaultStore.enabled(a)
        tvVaultStatus.text = if (vaultOn) {
            "当前：${VaultStore.displayName(a)} / ${VaultStore.FOLDER}（Obsidian）"
        } else {
            "当前：本机数据库（未绑定 Obsidian 文件夹）"
        }
        btnVault.text = if (vaultOn) "更改" else "选择"
        renderAutoLabel()

        val accent = a.db.accent
        val usOn = accent == MainActivity.ACCENT_US
        btnAccentUs.background = a.rounded(if (usOn) Pal.primary else Pal.card, 3f, Pal.line)
        btnAccentUs.setTextColor(if (usOn) Pal.white else Pal.primary)
        btnAccentUk.background = a.rounded(if (!usOn) Pal.primary else Pal.card, 3f, Pal.line)
        btnAccentUk.setTextColor(if (!usOn) Pal.white else Pal.primary)

        val rates = listOf(0.7f, 1.0f, 1.3f)
        listOf(btnRateSlow, btnRateNormal, btnRateFast).forEachIndexed { i, b ->
            val on = a.db.speechRate == rates[i]
            b.background = a.rounded(if (on) Pal.primary else Pal.card, 3f, Pal.line)
            b.setTextColor(if (on) Pal.white else Pal.primary)
        }

        val autoOn = a.db.autoIpa
        btnAutoIpa.text = "导入时自动补全音标：" + (if (autoOn) "开" else "关")
        btnAutoIpa.background = a.rounded(if (autoOn) Pal.softBlue else Pal.card, 3f, Pal.line)
        btnAutoIpa.setTextColor(if (autoOn) Pal.primary else Pal.textSub)

        val theme = ThemePrefs.mode(a)
        listOf(btnThemeSystem, btnThemeLight, btnThemeDark).forEachIndexed { i, b ->
            val on = theme == i
            b.background = a.rounded(if (on) Pal.primary else Pal.card, 3f, Pal.line)
            b.setTextColor(if (on) Pal.white else Pal.textSub)
        }
    }
}
