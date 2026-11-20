package com.wordcool.app

import android.content.Context
import java.text.Normalizer

/**
 * v6 离线 IPA 音标索引；v7 按同行做法补上词形归一与英美拼写别名。
 *
 * assets/ipa.tsv 每行格式为「小写词<TAB>英音<TAB>美音」，按词字典序排序，UTF-8。
 * 数据来源、许可与修改说明见 LICENSES/THIRD_PARTY_NOTICES.md。
 *
 * 不为 14 万词条建 HashMap（省内存）：整份文本常驻一个 String，用 "\n词\t"
 * 精确命中行首，String.indexOf 是原生实现，单次查询耗时可忽略。
 * 只在真正需要补音标时才加载，平时学习页不占这份内存。
 */
object IpaDictionary {

    data class Ipa(val uk: String, val us: String) {
        fun hasAny(): Boolean = uk.isNotEmpty() || us.isNotEmpty()
    }

    /** 与 tools/build_ipa.py 的 normalize() 逐字符一致 */
    private const val STRIP = " \t\"'.,;:!?()[]{}"

    /** 英美拼写变体：只用于查表，绝不改写用户词形、也不覆盖已有音标 */
    private val ALIAS_PAIRS = listOf(
        "color" to "colour", "honor" to "honour", "favor" to "favour",
        "labor" to "labour", "behavior" to "behaviour", "neighbor" to "neighbour",
        "center" to "centre", "theater" to "theatre", "meter" to "metre",
        "liter" to "litre", "fiber" to "fibre", "caliber" to "calibre",
        "organize" to "organise", "realize" to "realise", "recognize" to "recognise",
        "analyze" to "analyse", "apologize" to "apologise", "summarize" to "summarise",
        "memorize" to "memorise", "modernize" to "modernise", "emphasize" to "emphasise",
        "defense" to "defence", "offense" to "offence", "licence" to "license",
        "practice" to "practise", "program" to "programme", "catalog" to "catalogue",
        "dialog" to "dialogue", "traveled" to "travelled", "modeled" to "modelled",
        "labeled" to "labelled", "canceled" to "cancelled", "jewelry" to "jewellery",
        "aluminum" to "aluminium", "gray" to "grey", "check" to "cheque",
        "tire" to "tyre", "plow" to "plough", "pajamas" to "pyjamas"
    )
    private val ALIASES: Map<String, String> = ALIAS_PAIRS
        .flatMap { listOf(it.first to it.second, it.second to it.first) }
        .toMap()

    private var text: String? = null
    private var loaded = false
    private var count = 0

    @Synchronized
    private fun ensure(ctx: Context) {
        if (loaded) return
        loaded = true // 加载失败也不反复重试，避免每次点按钮都读一次 4MB
        try {
            val bytes = ctx.applicationContext.assets.open("ipa.tsv").use { it.readBytes() }
            val t = String(bytes, Charsets.UTF_8)
            text = t
            count = t.count { it == '\n' }
        } catch (e: Exception) {
            text = null
            count = 0
        }
    }

    fun isAvailable(ctx: Context): Boolean {
        ensure(ctx)
        return text != null
    }

    fun entryCount(ctx: Context): Int {
        ensure(ctx)
        return count
    }

    private fun normalize(word: String): String =
        Normalizer.normalize(word.trim().lowercase(), Normalizer.Form.NFC)
            .trim { STRIP.indexOf(it) >= 0 }

    /** 精确取一整行，避免前缀误命中（如 "a" 命中 "abandon"） */
    private fun findLine(t: String, key: String): Ipa? {
        if (key.isEmpty() || '\t' in key || '\n' in key) return null
        val needle = "\n$key\t"
        var start = t.indexOf(needle)
        if (start >= 0) {
            start += 1
        } else if (t.startsWith("$key\t")) {
            start = 0
        } else {
            return null
        }
        val nl = t.indexOf('\n', start)
        val end = if (nl < 0) t.length else nl
        val cols = t.substring(start, end).split('\t')
        if (cols.size < 3) return null
        return Ipa(cols[1], cols[2])
    }

    /** 查不到返回 null；查到时 uk / us 可能有一边为空串（该词典没收录那种口音）。
     *  主词没命中时走英美拼写别名；主词命中但缺一列时，用别名把那一列补上。 */
    fun lookup(ctx: Context, word: String): Ipa? {
        ensure(ctx)
        val t = text ?: return null
        val key = normalize(word)
        if (key.isEmpty()) return null
        val direct = findLine(t, key)
        val altKey = ALIASES[key]
        if (direct == null) {
            return altKey?.let { findLine(t, it) }
        }
        if (altKey != null && (!direct.hasBoth())) {
            val alt = findLine(t, altKey)
            if (alt != null) {
                return Ipa(
                    if (direct.uk.isEmpty()) alt.uk else direct.uk,
                    if (direct.us.isEmpty()) alt.us else direct.us
                )
            }
        }
        return direct
    }

    private fun Ipa.hasBoth(): Boolean = uk.isNotEmpty() && us.isNotEmpty()
}
