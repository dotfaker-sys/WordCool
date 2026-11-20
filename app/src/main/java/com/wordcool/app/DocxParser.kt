package com.wordcool.app

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.DataFormatException
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

/**
 * 把 .docx / .xlsx / 文字型 .pdf / .txt / .csv 里的单词表解析成词条。
 * 全部基于系统自带能力（ZipInputStream / XmlPullParser / Inflater），零第三方依赖。
 *
 * v3 新增：自动识别 Unit 1 / Day 2 / 第 3 单元 这类标题行，并给后续词条打上组名。
 */
object DocxParser {

    private val PHON = Regex("""/[^/\n]{2,50}/|\[[^\]\n]{2,50}\]""")
    /** v5：识别带口音标记的音标，如 `UK /ˈɑːftə/  US /ˈæftər/`、`BrE[...] AmE[...]`。
     *  标签与音标必须紧贴，避免把普通英文里的 us / 英 误删。 */
    private val PHON_WITH_TAG = Regex(
        """(?:(?<![A-Za-z])(UK|US|BrE|AmE|英式|美式|英音|美音)\s*[:：.]?\s*)?(/[^/\n]{2,50}/|\[[^\]\n]{2,50}\])""",
        RegexOption.IGNORE_CASE
    )
    /** 清掉没贴在音标上的孤立口音标签。大小写敏感：不误删英文单词 us / uk。 */
    private val ACCENT_WORD = Regex(
        """(?<![A-Za-z])(?:UK|US|BrE|AmE)(?![A-Za-z])\s*[:：.]?\s*|英式|美式|英音|美音"""
    )
    private val NO_PREFIX = Regex("""^\s*\d+\s*[.)、,，]\s*""")
    private val CHINESE = Regex("""[\u4e00-\u9fff]""")
    private val POS_TAIL = Regex(
        """\s+(?:n|v|vt|vi|adj|adv|prep|conj|pron|num|art|int|phr|aux|pl|abbr)\.?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val POS_HEAD = Regex(
        """^(?:n|v|vt|vi|adj|adv|prep|conj|pron|phr)\.?\s+""",
        RegexOption.IGNORE_CASE
    )
    private val HEAD = Regex(
        """^\s*([A-Za-z][A-Za-z0-9\-'.()/ ]*?)(?=[\t:：;；,，\[\]（）]|\s[-–—]\s|\s{2,}|[\u4e00-\u9fff]|$)"""
    )
    private val TITLE = Regex(
        """^(?:unit|list|part|section|chapter|lesson|vocabulary|words?)\s*\d*\.?$""",
        RegexOption.IGNORE_CASE
    )
    private val SKIP_WORDS = setOf(
        "unit", "list", "word", "words", "vocabulary", "vocab", "part", "section",
        "chapter", "lesson", "new words", "term", "terms", "phrase",
        "no", "no.", "no:", "id", "index", "english", "meaning", "phonetic",
        "单词", "词汇", "生词", "序号", "编号", "词性", "释义", "音标",
        "例句", "翻译", "备注", "单词表", "中文", "英文"
    )

    /** v3：组别标题行。Unit 1 / Day 2 / Lesson 3 / 第 4 单元 / 第五课 … */
    // 后缀只放常见标题词，绝不放开成任意 20 字符：
    // 否则「Unit 1 abandon 放弃」会被误判成组名，把同行的词条整行吞掉。
    private val GROUP_EN = Regex(
        """^\s*(Unit|Day|Lesson|Chapter|Part|Week|Set|List|单元|课|天|组)\s*[:：.\-—·]?\s*([0-9]+|[一二三四五六七八九十百零两]+)\s*(?:[·•\-—:：.、/]?\s*(?:New\s+Words?|Words?|Vocabulary|词汇|单词|生词|单元|课文|列表))?\s*$""",
        RegexOption.IGNORE_CASE
    )
    private val GROUP_CN = Regex(
        """^\s*第\s*([0-9]+|[一二三四五六七八九十百零两]+)\s*(单元|课|天|组|章|部分|周)\s*.{0,16}$"""
    )
    /** 长得像组标题的行。认不出标准组名时也整行跳过，避免
     *  「Unit 1 abandon 放弃」被拆成一个叫「Unit 1 abandon」的脏词条。 */
    private val GROUP_HEADER_HINT = Regex(
        """^(?:\s*(?:Unit|Day|Lesson|Chapter|Part|Week|Set|List|单元|课|天|组)\s*[:：.\-—·]?\s*[0-9一二三四五六七八九十百零两].*|\s*第\s*[0-9一二三四五六七八九十百零两].*)$""",
        RegexOption.IGNORE_CASE
    )
    private val CN_DIGITS = mapOf(
        '零' to 0, '〇' to 0, '一' to 1, '二' to 2, '两' to 2, '三' to 3, '四' to 4,
        '五' to 5, '六' to 6, '七' to 7, '八' to 8, '九' to 9
    )

    // ---------- 入口：全部保留旧签名，新增带文件名的重载 ----------

    /** 自动识别：PDF / docx / xlsx / 纯文本。旧调用点不受影响 */
    fun parseBytes(bytes: ByteArray): List<Imported> = parseBytes(bytes, "")

    fun parseBytes(bytes: ByteArray, fileName: String): List<Imported> = when {
        isPdf(bytes) -> parsePdf(bytes)
        isZip(bytes) -> {
            val hasDoc = hasEntry(bytes, "word/document.xml")
            val hasSheet = hasEntry(bytes, "xl/workbook.xml") ||
                hasEntry(bytes, "xl/worksheets/sheet1.xml") ||
                hasMatchingEntry(bytes, Regex("""^xl/worksheets/sheet\d+\.xml$"""))
            when {
                hasDoc -> parseDocx(bytes)
                hasSheet -> parseXlsx(bytes)
                else -> throw IllegalArgumentException(
                    "这个压缩包不是有效的 Word/Excel 文档" +
                        if (fileName.isNotEmpty()) "（$fileName）" else ""
                )
            }
        }
        else -> parseText(String(bytes, Charsets.UTF_8))
    }

    fun isZip(bytes: ByteArray): Boolean =
        bytes.size > 4 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()

    fun isPdf(bytes: ByteArray): Boolean =
        bytes.size > 5 && String(bytes.copyOfRange(0, 5), StandardCharsets.ISO_8859_1) == "%PDF-"

    fun parseText(text: String): List<Imported> = parseLines(text)

    fun parseDocx(bytes: ByteArray): List<Imported> {
        val xml = findEntry(bytes, "word/document.xml")
            ?: throw IllegalArgumentException("这个文件里没有 word/document.xml，可能不是 .docx")
        return parseLines(extractLines(xml).joinToString("\n"))
    }

    /** v3：.xlsx（zip + sharedStrings.xml + 第一张工作表）。
     *  删过工作表后文件名可能是 sheet2.xml 而非 sheet1.xml，所以自动枚举。 */
    fun parseXlsx(bytes: ByteArray): List<Imported> {
        val sheet = firstSheet(bytes)
            ?: throw IllegalArgumentException("这个 Excel 里找不到可读取的工作表")
        val shared = findEntry(bytes, "xl/sharedStrings.xml")
            ?.let { parseSharedStrings(it) } ?: emptyList()
        return parseLines(parseSheet(sheet, shared).joinToString("\n"))
    }

    private fun firstSheet(bytes: ByteArray): ByteArray? {
        findEntry(bytes, "xl/worksheets/sheet1.xml")?.let { return it }
        val candidates = mutableListOf<Pair<String, ByteArray>>()
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        try {
            while (true) {
                val entry = zis.nextEntry ?: break
                if (Regex("""^xl/worksheets/sheet\d+\.xml$""").matches(entry.name)) {
                    candidates.add(entry.name to zis.readBytes())
                }
                zis.closeEntry()
            }
        } finally {
            try {
                zis.close()
            } catch (e: Exception) {
                // 关闭失败不影响已读到的内容
            }
        }
        return candidates
            .sortedWith(
                compareBy<Pair<String, ByteArray>> { sheetNumber(it.first) }
                    .thenBy { it.first }
            )
            .firstOrNull()?.second
    }

    private fun sheetNumber(name: String): Int =
        Regex("""sheet(\d+)\.xml""").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE

    /** v3：文字型 PDF。扫描版（纯图片）无文字层，会给出明确提示 */
    fun parsePdf(bytes: ByteArray): List<Imported> {
        val text = extractPdfText(bytes)
        if (text.isBlank()) {
            throw IllegalArgumentException(
                "没从这个 PDF 里提取到文字，可能是扫描图片版。请改用 Word / Excel，或复制内容用「粘贴导入」。"
            )
        }
        return parseLines(text)
    }

    // ---------- zip 读取 ----------

    private fun hasEntry(bytes: ByteArray, target: String): Boolean {
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        return try {
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name == target) return true
                zis.closeEntry()
            }
            false
        } finally {
            try {
                zis.close()
            } catch (e: Exception) {
                // 关闭失败不影响判断结果
            }
        }
    }

    private fun hasMatchingEntry(bytes: ByteArray, pattern: Regex): Boolean {
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        return try {
            while (true) {
                val entry = zis.nextEntry ?: break
                if (pattern.matches(entry.name)) return true
                zis.closeEntry()
            }
            false
        } finally {
            try {
                zis.close()
            } catch (e: Exception) {
                // 关闭失败不影响判断结果
            }
        }
    }

    private fun findEntry(bytes: ByteArray, target: String): ByteArray? {
        val zis = ZipInputStream(ByteArrayInputStream(bytes))
        return try {
            var result: ByteArray? = null
            while (true) {
                val entry = zis.nextEntry ?: break
                if (entry.name == target) {
                    result = zis.readBytes()
                    break
                }
                zis.closeEntry()
            }
            result
        } finally {
            try {
                zis.close()
            } catch (e: Exception) {
                // 关闭失败不影响已读到的内容
            }
        }
    }

    // ---------- docx 正文抽取 ----------

    /** 逐段/逐表格行抽取文字：表格列之间用 \t 连接，方便后面按列拆词和释义 */
    private fun extractLines(xml: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")

        val lines = mutableListOf<String>()
        val para = StringBuilder()
        val cell = StringBuilder()
        val cells = mutableListOf<String>()
        var inCell = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (local(parser.name)) {
                    "tab" -> para.append('\t')
                    "br", "cr" -> para.append(' ')
                    "tc" -> {
                        inCell = true
                        cell.setLength(0)
                    }
                    "tr" -> cells.clear()
                    else -> {}
                }
                XmlPullParser.TEXT -> { para.append(parser.text ?: "") }
                XmlPullParser.END_TAG -> when (local(parser.name)) {
                    "p" -> {
                        val text = para.toString().trim()
                        para.setLength(0)
                        if (inCell) {
                            if (text.isNotEmpty()) {
                                if (cell.isNotEmpty()) cell.append(' ')
                                cell.append(text)
                            }
                        } else if (text.isNotEmpty()) {
                            lines.add(text)
                        }
                    }
                    "tc" -> {
                        cells.add(cell.toString().trim())
                        inCell = false
                        cell.setLength(0)
                    }
                    "tr" -> {
                        if (cells.any { it.isNotEmpty() }) lines.add(cells.joinToString("\t"))
                        cells.clear()
                    }
                    else -> {}
                }
                else -> {}
            }
            event = parser.next()
        }
        return lines
    }

    private fun local(name: String?): String {
        if (name == null) return ""
        val i = name.indexOf(':')
        return if (i >= 0) name.substring(i + 1) else name
    }

    // ---------- xlsx 抽取 ----------

    /** 共享字符串表：每个 <si> 里的多个 <t> 要拼起来（富文本单元格） */
    private fun parseSharedStrings(xml: ByteArray): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inSi = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (local(parser.name)) {
                    "si" -> {
                        inSi = true
                        sb.setLength(0)
                    }
                    "t" -> if (inSi) {
                        // 文本内容会在 TEXT 事件里取
                    }
                    else -> {}
                }
            } else if (event == XmlPullParser.TEXT && inSi) {
                sb.append(parser.text ?: "")
            } else if (event == XmlPullParser.END_TAG && local(parser.name) == "si") {
                out.add(sb.toString().trim())
                inSi = false
            }
            event = parser.next()
        }
        return out
    }

    /** 工作表：按行聚合单元格，缺列补空，避免 A/C 列错位成 A/B */
    private fun parseSheet(xml: ByteArray, shared: List<String>): List<String> {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xml), "UTF-8")
        val lines = mutableListOf<String>()
        val cells = mutableMapOf<Int, String>()
        var col = 0
        var cellType = ""
        var inV = false
        var inT = false
        val value = StringBuilder()
        var inIs = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val name = if (event == XmlPullParser.START_TAG || event == XmlPullParser.END_TAG) {
                local(parser.name)
            } else ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (name) {
                        "row" -> cells.clear()
                        "c" -> {
                            val ref = attr(parser, "r") ?: ""
                            col = columnIndex(ref)
                            cellType = attr(parser, "t") ?: ""
                            value.setLength(0)
                            inV = false
                            inT = false
                            inIs = false
                        }
                        "is" -> inIs = true
                        "v" -> inV = true
                        "t" -> if (inIs) inT = true
                        else -> {}
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inV || inT) value.append(parser.text ?: "")
                }
                XmlPullParser.END_TAG -> {
                    when (name) {
                        "v", "t" -> {
                            if (inV || inT) {
                                val raw = value.toString().trim()
                                val text = when (cellType) {
                                    "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                                    "inlineStr" -> raw
                                    else -> raw
                                }
                                if (text.isNotEmpty()) cells[col] = text
                            }
                            inV = false
                            inT = false
                        }
                        "c" -> {
                            // 单个单元格结束，值已在 v/t 结束时写入
                        }
                        "is" -> inIs = false
                        "row" -> {
                            if (cells.isNotEmpty()) {
                                val maxCol = cells.keys.maxOrNull() ?: 0
                                val row = (0..maxCol).joinToString("\t") { cells[it] ?: "" }
                                if (row.trim().isNotEmpty()) lines.add(row)
                            }
                            cells.clear()
                        }
                        else -> {}
                    }
                }
                else -> {}
            }
            event = parser.next()
        }
        return lines
    }

    private fun attr(parser: XmlPullParser, name: String): String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) return parser.getAttributeValue(i)
        }
        return null
    }

    private fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (ch.isLetter()) {
                n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
            } else break
        }
        return (n - 1).coerceAtLeast(0)
    }

    // ---------- 文字型 PDF 抽取 ----------

    private val PDF_STREAM_START = Regex("""stream\r?\n""")
    private val PDF_BLOCK = Regex("""BT([\s\S]*?)ET""")
    private val PDF_TOKEN = Regex("""\((?:\\.|[^\\()])*\)|<([0-9A-Fa-f\s]+)>""")
    private val PDF_LENGTH = Regex("""/Length\s+(\d+)""")
    private val PDF_FLATE = Regex("""/Filter\s*(?:/Fl(?:ateDecode)?|\[\s*/Fl(?:ateDecode)?)""", RegexOption.IGNORE_CASE)

    /** 按 /Length 精确截取流字节，再从内容流的文本操作符里取字。
     *  不再用非贪婪正则抓到第一个 endstream —— 二进制压缩流里可能恰好出现这几个字节。 */
    private fun extractPdfText(bytes: ByteArray): String {
        // ISO_8859_1 是 1 字节 = 1 字符，字符串下标与字节下标一一对应
        val raw = String(bytes, StandardCharsets.ISO_8859_1)
        val lines = mutableListOf<String>()
        var searchFrom = 0
        while (searchFrom < raw.length) {
            val sm = PDF_STREAM_START.find(raw, searchFrom) ?: break
            val contentStart = sm.range.last + 1
            // 对象字典就在最近的 obj 关键字与 stream 之间，不要往前翻太多以免读进别的对象
            val objIdx = raw.lastIndexOf("obj", sm.range.first)
            val dictStart = if (objIdx >= 0) (objIdx - 20).coerceAtLeast(0)
            else (contentStart - 600).coerceAtLeast(0)
            val dict = raw.substring(dictStart, sm.range.first)
            val declaredLen = PDF_LENGTH.findAll(dict).lastOrNull()?.groupValues?.get(1)?.toLongOrNull()
            val endStream = raw.indexOf("endstream", contentStart)
                .let { if (it < 0) raw.length else it }
            val byLength = declaredLen?.let { contentStart + it }
                ?.takeIf { it >= contentStart && it <= raw.length }
            val contentEnd = (byLength ?: endStream.toLong())
                .coerceAtMost(raw.length.toLong()).toInt()
            if (contentEnd <= contentStart) {
                searchFrom = (endStream + 9).coerceAtLeast(contentStart + 1)
                continue
            }
            val chunk = bytes.copyOfRange(contentStart, contentEnd)
            val content = if (PDF_FLATE.containsMatchIn(dict)) {
                inflate(chunk)?.let { String(it, StandardCharsets.ISO_8859_1) } ?: ""
            } else {
                String(chunk, StandardCharsets.ISO_8859_1)
            }
            val blocks = PDF_BLOCK.findAll(content).toList()
            if (blocks.isNotEmpty()) {
                for (b in blocks) {
                    val line = pdfTokensToText(b.groupValues[1])
                    if (line.isNotBlank()) lines.add(line)
                }
            } else if (content.isNotBlank()) {
                // 少数 PDF 不写 BT/ET，退化为按定位操作符分行
                content.split(Regex("""\bT\*|\bTd\b|\bTD\b"""))
                    .map { pdfTokensToText(it) }
                    .filter { it.isNotBlank() }
                    .forEach { lines.add(it) }
            }
            val next = (endStream + 9).coerceAtLeast(contentEnd)
            if (next <= searchFrom) break
            searchFrom = next
        }
        return lines.joinToString("\n")
    }

    private fun pdfTokensToText(chunk: String): String {
        val sb = StringBuilder()
        for (t in PDF_TOKEN.findAll(chunk)) {
            val v = t.value
            sb.append(
                if (v.startsWith("(")) {
                    decodePdfLiteral(v.substring(1, v.length - 1))
                } else {
                    decodePdfHex(v.substring(1, v.length - 1))
                }
            )
        }
        return sb.toString()
            .replace(Regex("""[\u0000-\u0008\u000B\u000C\u000E-\u001F]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun decodePdfLiteral(inner: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < inner.length) {
            val c = inner[i]
            if (c != '\\') {
                out.append(c)
                i++
                continue
            }
            i++
            if (i >= inner.length) break
            when (val e = inner[i]) {
                'n' -> out.append('\n')
                'r' -> out.append('\r')
                't' -> out.append('\t')
                'b' -> out.append('\b')
                'f' -> out.append('\u000C')
                '(', ')', '\\' -> out.append(e)
                in '0'..'7' -> {
                    var oct = e.toString()
                    var k = 0
                    while (k < 2 && i + 1 < inner.length && inner[i + 1] in '0'..'7') {
                        i++
                        oct += inner[i]
                        k++
                    }
                    out.append(oct.toInt(8).toChar())
                }
                else -> out.append(e)
            }
            i++
        }
        return latinOrUtf16(out.toString())
    }

    private fun decodePdfHex(hex: String): String {
        val clean = hex.replace(Regex("""\s"""), "")
        val padded = if (clean.length % 2 == 0) clean else clean + "0"
        val bytes = ByteArray(padded.length / 2)
        for (i in bytes.indices) {
            bytes[i] = padded.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes, StandardCharsets.UTF_16BE).removePrefix("\uFEFF")
        } else {
            String(bytes, StandardCharsets.ISO_8859_1)
        }
    }

    private fun latinOrUtf16(s: String): String {
        val bytes = s.toByteArray(StandardCharsets.ISO_8859_1)
        return if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            String(bytes, StandardCharsets.UTF_16BE).removePrefix("\uFEFF")
        } else s
    }

    private fun inflate(data: ByteArray): ByteArray? {
        for (nowrap in listOf(false, true)) {
            try {
                val inf = Inflater(nowrap)
                inf.setInput(data)
                val out = java.io.ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (!inf.finished()) {
                    val n = inf.inflate(buf)
                    if (n == 0) {
                        if (inf.needsInput() || inf.needsDictionary()) break
                        break
                    }
                    out.write(buf, 0, n)
                }
                inf.end()
                if (out.size() > 0) return out.toByteArray()
            } catch (e: DataFormatException) {
                // 试另一种封装
            } catch (e: Exception) {
                // 该流不是可解压内容，跳过
            }
        }
        return null
    }

    // ---------- 组别标题识别 ----------

    /** 返回标准化组名（Unit 1 / Day 2 …）；不是标题行则返回 null */
    fun normalizeGroup(rawLine: String): String? {
        var raw = NO_PREFIX.replace(rawLine.trim(), "")
        if (raw.isEmpty() || raw.length > 48) return null
        val en = GROUP_EN.find(raw)
        if (en != null) {
            val key = en.groupValues[1].lowercase().let {
                when (it) {
                    "单元", "章", "部分" -> "Unit"
                    "课", "天", "组", "周" -> "Day"
                    else -> it.replaceFirstChar { c -> c.uppercase() }
                }
            }
            val num = cnNumber(en.groupValues[2])
            return if (num > 0) "$key $num" else null
        }
        val cn = GROUP_CN.find(raw)
        if (cn != null) {
            val num = cnNumber(cn.groupValues[1])
            val key = if (cn.groupValues[2] in listOf("单元", "章", "部分", "周")) "Unit" else "Day"
            return if (num > 0) "$key $num" else null
        }
        return null
    }

    private fun cnNumber(s: String): Int {
        s.toIntOrNull()?.let { return it }
        if (s.isEmpty()) return 0
        if ('十' in s) {
            val parts = s.split('十')
            val tens = if (parts[0].isEmpty()) 1 else (CN_DIGITS[parts[0].last()] ?: 0)
            val ones = if (parts.size > 1 && parts[1].isNotEmpty()) (CN_DIGITS[parts[1].last()] ?: 0) else 0
            return tens * 10 + ones
        }
        return CN_DIGITS[s.last()] ?: 0
    }

    // ---------- 词条行解析 ----------

    /** 兼容：普通段落、两列表格、CSV、纯单词列表、带音标/词性的写法，并继承当前组名 */
    fun parseLines(text: String): List<Imported> {
        val map = LinkedHashMap<String, Imported>()
        var currentGroup = ""
        for (line in text.replace("\r", "").split("\n")) {
            val group = normalizeGroup(line)
            if (group != null) {
                currentGroup = group
                continue
            }
            // 像标题但不符合标准写法：整行跳过，别让「Unit 1 abandon 放弃」变成脏词条
            if (GROUP_HEADER_HINT.matches(line.trim())) continue
            val parsed = parseLine(line) ?: continue
            val item = if (parsed.group.isEmpty() && currentGroup.isNotEmpty()) {
                parsed.copy(group = currentGroup)
            } else parsed
            val key = item.word.lowercase()
            val old = map[key]
            if (old == null) {
                map[key] = item
            } else {
                val better = old.copy(
                    meaning = if (old.meaning.isBlank() && item.meaning.isNotBlank()) item.meaning else old.meaning,
                    phonetic = if (old.phonetic.isBlank() && item.phonetic.isNotBlank()) item.phonetic else old.phonetic,
                    phoneticUk = if (old.phoneticUk.isBlank() && item.phoneticUk.isNotBlank()) item.phoneticUk else old.phoneticUk,
                    phoneticUs = if (old.phoneticUs.isBlank() && item.phoneticUs.isNotBlank()) item.phoneticUs else old.phoneticUs,
                    group = if (old.group.isBlank() && item.group.isNotBlank()) item.group else old.group
                )
                map[key] = better
            }
        }
        return map.values.toList()
    }

    private fun parseLine(rawLine: String): Imported? {
        var raw = rawLine.trim()
        if (raw.isEmpty() || raw.length > 140) return null
        if (raw.startsWith("//") || raw.startsWith("#")) return null
        // CSV 常见的成对引号："abandon","放弃" → abandon,放弃
        // 只去双引号，保留单引号，避免破坏 don't / it's 这类词条
        raw = raw.replace("\"", "")
        raw = NO_PREFIX.replace(raw, "")

        // 音标：/əˈbændən/ 或 [əˈbændən]；带 UK/US/BrE/AmE 标记时分存两套
        var phonetic = ""
        var phoneticUk = ""
        var phoneticUs = ""
        for (m in PHON_WITH_TAG.findAll(raw)) {
            val tag = m.groupValues[1].lowercase()
            val p = m.groupValues[2].trim()
            when (tag) {
                "uk", "bre", "英式", "英音" -> if (phoneticUk.isEmpty()) phoneticUk = p
                "us", "ame", "美式", "美音" -> if (phoneticUs.isEmpty()) phoneticUs = p
                else -> if (phonetic.isEmpty()) phonetic = p
            }
        }
        // 没有「无标记」音标时，主音标取英式、其次美式，保证词库里一定有音标可显示
        if (phonetic.isEmpty()) phonetic = phoneticUk.ifEmpty { phoneticUs }
        raw = PHON_WITH_TAG.replace(raw, " ")
        raw = PHON.replace(raw, " ")
        raw = ACCENT_WORD.replace(raw, " ")

        var word = ""
        var meaning = ""
        var example = ""

        val parts = raw.split(Regex("""\t+""")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size >= 2) {
            // Word 表格 / Excel 列 / Tab 分隔：找出「英文词」那一列
            val headIdx = parts.indexOfFirst { p ->
                p.any { it.isLetter() } && !CHINESE.containsMatchIn(p) && !p.all { it.isDigit() } &&
                    p.length > 1 && !p.startsWith("/")
            }.let { if (it >= 0) it else 0 }
            word = parts[headIdx]
            val rest = parts.filterIndexed { i, _ -> i != headIdx }
            val cn = rest.filter { CHINESE.containsMatchIn(it) }
            meaning = if (cn.isNotEmpty()) cn.joinToString(" ") else rest.joinToString(" ")
            example = rest.firstOrNull {
                !CHINESE.containsMatchIn(it) && it.length > 14 && it.contains(' ')
            } ?: ""
        } else {
            val m = HEAD.find(raw)
            if (m != null) {
                word = m.groupValues[1]
                meaning = raw.substring(m.range.last + 1)
            } else {
                // 中文在前、英文在后的写法：放弃 abandon
                val en = Regex("""[A-Za-z][A-Za-z0-9\-'. ]{1,40}""").find(raw)
                if (en != null) {
                    word = en.value
                    meaning = raw.replace(en.value, " ")
                }
            }
        }

        word = POS_TAIL.replace(word, "").trim().trimEnd('.', ',', ';', ':', ' ')

        // 「abandon v. 放弃」这种只空一格又带词性的写法，HEAD 会把中文一起吞进 word。
        // 纠偏：只保留最前面的英文词，把剩下的部分并回释义。
        if (CHINESE.containsMatchIn(word)) {
            val en = Regex("""[A-Za-z][A-Za-z0-9\-']*""").find(word)?.value
            if (en.isNullOrBlank()) return null
            val tail = word.substring(word.indexOf(en) + en.length)
            meaning = (tail + " " + meaning).trim()
            word = en
        }
        meaning = clean(meaning)
        example = clean(example)

        if (word.isBlank() || word.length > 60 || !word.any { it.isLetter() }) return null
        val key = word.trim().trimEnd('.', ':').lowercase()
        if (key in SKIP_WORDS || TITLE.matches(word)) return null
        if (word.all { it.isDigit() }) return null
        // 「No.」「ID」「A」这类纯大写/纯符号的短表头，不是单词
        if (word.length <= 3 && word.none { it.isLowerCase() } && word.none { it.isDigit() }) return null
        if (meaning.equals(word, ignoreCase = true)) meaning = ""

        return Imported(
            word = word,
            phonetic = phonetic,
            meaning = meaning,
            example = example,
            phoneticUk = phoneticUk,
            phoneticUs = phoneticUs
        )
    }

    private fun clean(s: String): String = s.trim()
        .trimStart('\t', ' ', ':', '：', ';', '；', ',', '，', '-', '–', '—', '.', '、', '(', ')', '[', ']', '（', '）', '"')
        .let { POS_HEAD.replace(it, "") }
        .trim()
}
