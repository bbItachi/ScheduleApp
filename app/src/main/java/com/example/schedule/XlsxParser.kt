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

    fun parse(input: InputStream, targetGroup: String): List<Lesson> {
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

        val anchor = cells.entries.firstOrNull { it.value.trim() == targetGroup }
            ?: error("Группа $targetGroup не найдена в файле")

        val groupRow = rowOf(anchor.key)
        val groupCol = colOf(anchor.key)

        val maxRow = cells.keys.maxOf { rowOf(it) }

        val result = mutableListOf<Lesson>()
        var currentDay: String? = null
        var week = WeekType.ODD

        var r = groupRow + 1
        while (r <= maxRow) {
            val a = cell(cells, r, 1).trim()

            if (a.contains("ЧЕТНАЯ", ignoreCase = true)) week = WeekType.EVEN
            if (a.uppercase() in DAYS) currentDay = a.uppercase()

            val pairNum = cell(cells, r, 2).trim().toIntOrNull()

            if (pairNum != null && currentDay != null) {
                val time = cell(cells, r, 4).trim()

                val s1 = cell(cells, r, groupCol).trim()
                val r1 = cell(cells, r, groupCol + 1).trim()
                val s2 = cell(cells, r, groupCol + 2).trim()
                val r2 = cell(cells, r, groupCol + 3).trim()

                val t1 = cell(cells, r + 1, groupCol).trim()
                val tr1 = cell(cells, r + 1, groupCol + 1).trim()
                val t2 = cell(cells, r + 1, groupCol + 2).trim()
                val tr2 = cell(cells, r + 1, groupCol + 3).trim()

                val subs = mutableListOf<SubgroupPair>()
                when {
                    s1.isNotEmpty() && s2.isNotEmpty() -> {
                        subs += SubgroupPair(1, s1, t1, r1.ifEmpty { tr1 })
                        subs += SubgroupPair(2, s2, t2, r2.ifEmpty { tr2 })
                    }
                    s1.isNotEmpty() -> {
                        val room = r1.ifEmpty { r2 }.ifEmpty { tr1 }.ifEmpty { tr2 }
                        val teacher = t1.ifEmpty { t2 }
                        subs += SubgroupPair(1, s1, teacher, room)
                    }
                    s2.isNotEmpty() -> {
                        val room = r2.ifEmpty { r1 }.ifEmpty { tr2 }.ifEmpty { tr1 }
                        val teacher = t2.ifEmpty { t1 }
                        subs += SubgroupPair(2, s2, teacher, room)
                    }
                }

                if (subs.isNotEmpty()) {
                    result += Lesson(pairNum, time, currentDay, week, subs)
                }
            }
            r++
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
            buildString {
                for (j in 0 until ts.length) append(ts.item(j).textContent)
            }
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

    private fun rowOf(ref: String): Int =
        ref.dropWhile { !it.isDigit() }.toIntOrNull() ?: 0

    private fun colOf(ref: String): Int {
        var n = 0
        for (c in ref) {
            if (c.isDigit()) break
            n = n * 26 + (c.uppercaseChar() - 'A' + 1)
        }
        return n
    }

    private fun colLetter(col: Int): String {
        var n = col
        val sb = StringBuilder()
        while (n > 0) {
            n--
            sb.insert(0, ('A' + (n % 26)))
            n /= 26
        }
        return sb.toString()
    }

    private fun cell(cells: Map<String, String>, row: Int, col: Int): String =
        cells[colLetter(col) + row] ?: ""
}
