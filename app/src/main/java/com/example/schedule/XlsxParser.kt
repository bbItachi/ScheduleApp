package com.example.schedule

import org.w3c.dom.Element
import java.io.InputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.DocumentBuilderFactory

object XlsxParser {

    private val DAYS = listOf(
        "ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА",
        "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА", "ВОСКРЕСЕНЬЕ"
    )

    fun parse(input: InputStream): List<Lesson> {
        var sharedStrings = emptyList<String>()
        var sheetXml: String? = null

        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when (entry.name) {
                    "xl/sharedStrings.xml" -> sharedStrings = parseSharedStrings(zip.readBytes())
                    "xl/worksheets/sheet1.xml" -> sheetXml = zip.readBytes().decodeToString()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        if (sheetXml == null) error("Не найден лист расписания")
        val cells = parseSheet(sheetXml!!, sharedStrings)
        if (cells.isEmpty()) error("Лист пустой")

        val maxRow = cells.keys.maxOf { rowOf(it) }
        val maxCol = cells.keys.maxOf { colOf(it) }

        var groupRow = -1
        for (r in 1..maxRow) {
            for (c in 1..maxCol) {
                val v = cell(cells, r, c).trim()
                if (v.equals("Группа:", true) || v.equals("Группа", true)) {
                    groupRow = r; break
                }
            }
            if (groupRow > 0) break
        }
        if (groupRow < 0) error("Не найдена строка с группами")

        val groups = mutableListOf<Pair<Int, String>>()
        for (c in 1..maxCol) {
            val v = cell(cells, groupRow, c).trim()
            if (v.isEmpty()) continue
            if (v.equals("Группа:", true) || v == "Группа") continue
            if (v.equals("Подгруппа:", true)) continue
            if (!v.any { it.isLetter() }) continue
            if (v.length < 3 || v.length > 40) continue
            groups += c to v
        }
        if (groups.isEmpty()) error("Группы не найдены")

        val result = mutableListOf<Lesson>()
        var currentDay: String? = null
        var week = WeekType.ODD

        for (r in (groupRow + 1)..maxRow) {
            for (c in 1..maxCol) {
                val v = cell(cells, r, c)
                if (v.contains("ЧЕТНАЯ", true) || v.contains("ЧЁТНАЯ", true)) {
                    week = WeekType.EVEN; break
                }
            }

            for (c in 1..3) {
                val v = cell(cells, r, c).trim().uppercase()
                if (v in DAYS) { currentDay = v; break }
            }

            val pairNum = cell(cells, r, 2).trim().toIntOrNull()
            if (pairNum == null || currentDay == null) continue
            val time = cell(cells, r, 4).trim()
            if (time.isEmpty()) continue

            for (i in groups.indices) {
                val (colStart, groupName) = groups[i]
                val nextCol = groups.getOrNull(i + 1)?.first ?: (colStart + 4)
                val span = (nextCol - colStart).coerceAtMost(4)

                val s1 = cell(cells, r, colStart).trim()
                val r1 = cell(cells, r, colStart + 1).trim()
                val t1 = cell(cells, r + 1, colStart).trim()
                val tr1 = cell(cells, r + 1, colStart + 1).trim()

                val s2 = if (span >= 4) cell(cells, r, colStart + 2).trim() else ""
                val r2 = if (span >= 4) cell(cells, r, colStart + 3).trim() else ""
                val t2 = if (span >= 4) cell(cells, r + 1, colStart + 2).trim() else ""
                val tr2 = if (span >= 4) cell(cells, r + 1, colStart + 3).trim() else ""

                val subs = mutableListOf<SubgroupPair>()
                when {
                    s1.isNotEmpty() && s2.isNotEmpty() -> {
                        subs += SubgroupPair(1, s1, t1.ifEmpty { tr1 }, r1.ifEmpty { tr1 })
                        subs += SubgroupPair(2, s2, t2.ifEmpty { tr2 }, r2.ifEmpty { tr2 })
                    }
                    s1.isNotEmpty() -> {
                        val room = r1.ifEmpty { tr1 }
                        val teacher = t1.ifEmpty { tr1 }
                        subs += SubgroupPair(1, s1, teacher, room)
                    }
                    s2.isNotEmpty() -> {
                        val room = r2.ifEmpty { tr2 }
                        val teacher = t2.ifEmpty { tr2 }
                        subs += SubgroupPair(2, s2, teacher, room)
                    }
                }
                if (subs.isNotEmpty()) {
                    result += Lesson(groupName, pairNum, time, currentDay!!, week, subs)
                }
            }
        }
        return result
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = factory.newDocumentBuilder().parse(bytes.inputStream())
        val sis = doc.getElementsByTagName("si")
        return (0 until sis.length).map { i ->
            val si = sis.item(i) as Element
            val ts = si.getElementsByTagName("t")
            buildString { for (j in 0 until ts.length) append(ts.item(j).textContent) }
        }
    }

    private fun parseSheet(xml: String, strings: List<String>): Map<String, String> {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        val doc = factory.newDocumentBuilder().parse(xml.byteInputStream())
        val cs = doc.getElementsByTagName("c")
        val map = HashMap<String, String>(cs.length)
        for (i in 0 until cs.length) {
            val c = cs.item(i) as Element
            val ref = c.getAttribute("r")
            if (ref.isEmpty()) continue
            val t = c.getAttribute("t")
            val vNode = c.getElementsByTagName("v").item(0) ?: continue
            val raw = vNode.textContent ?: continue
            val text = if (t == "s") strings.getOrElse(raw.toInt()) { "" } else raw
            if (text.isNotBlank()) map[ref] = text
        }
        return map
    }

    private fun rowOf(ref: String): Int = ref.dropWhile { !it.isDigit() }.toIntOrNull() ?: 0
    private fun colOf(ref: String): Int {
        var n = 0
        for (c in ref) { if (c.isDigit()) break; n = n * 26 + (c.uppercaseChar() - 'A' + 1) }
        return n
    }
    private fun colLetter(col: Int): String {
        var n = col; val sb = StringBuilder()
        while (n > 0) { n--; sb.insert(0, ('A' + (n % 26))); n /= 26 }
        return sb.toString()
    }
    private fun cell(cells: Map<String, String>, row: Int, col: Int): String =
        cells[colLetter(col) + row] ?: ""
}
