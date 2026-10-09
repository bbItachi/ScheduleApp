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

    private val KNOWN_ROOMS = setOf(
        "ЦРК", "М", "СЗ", "СЗ/1", "СЗ/2", "М/1", "М/2"
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

                val rowCells = (0..3).map { cell(cells, r, colStart + it).trim() }
                val teachCells = (0..3).map { cell(cells, r + 1, colStart + it).trim() }

                val subjectPos = mutableListOf<Int>()
                val roomPos = mutableListOf<Int>()
                for (k in 0..3) {
                    val v = rowCells[k]
                    if (v.isEmpty()) continue
                    if (looksLikeRoom(v)) roomPos += k else subjectPos += k
                }

                val subs = mutableListOf<SubgroupPair>()

                if (subjectPos.size >= 2 && roomPos.size >= 2) {
                    for (k in 0..1) {
                        val si = subjectPos[k]
                        val ri = roomPos[k]
                        val teacher = teachCells.getOrElse(si) { "" }
                            .ifEmpty { teachCells.firstOrNull { it.isNotEmpty() } ?: "" }
                        val (t, r) = cleanOnline(teacher, rowCells[ri])
                        subs += SubgroupPair(k + 1, rowCells[si], t, r)
                    }
                } else if (subjectPos.size == 1) {
                    val si = subjectPos[0]
                    val ri = roomPos.firstOrNull()
                    val teacher = teachCells.getOrElse(si) { "" }
                        .ifEmpty { teachCells.firstOrNull { it.isNotEmpty() } ?: "" }
                    val (t, r) = cleanOnline(teacher, if (ri != null) rowCells[ri] else "")
                    subs += SubgroupPair(1, rowCells[si], t, r)
                } else if (subjectPos.size >= 2) {
                    val rawRoom = roomPos.firstOrNull()?.let { rowCells[it] } ?: ""
                    for (k in 0..1) {
                        val si = subjectPos[k]
                        val teacher = teachCells.getOrElse(si) { "" }
                        val (t, r) = cleanOnline(teacher, rawRoom)
                        subs += SubgroupPair(k + 1, rowCells[si], t, r)
                    }
                }

                if (subs.isNotEmpty()) {
                    result += Lesson(groupName, pairNum, time, currentDay!!, week, subs)
                }
            }
        }
        return result
    }

    // Если в преподе/аудитории есть «онлайн» — заменяем аудиторию на «Онлайн»
    private fun cleanOnline(teacher: String, room: String): Pair<String, String> {
        val t = teacher.trim()
        val r = room.trim()
        val hasOnline = t.contains("онлайн", ignoreCase = true) ||
                r.contains("онлайн", ignoreCase = true) ||
                t.equals("онлайн", true) ||
                r.equals("онлайн", true)
        if (!hasOnline) return t to r
        val cleanT = t.replace(Regex("(?i)\\s*онлайн\\s*"), " ").trim()
            .trim(',', '.', ';', '-', '_').trim()
        return cleanT to "Онлайн"
    }

    private fun looksLikeRoom(v: String): Boolean {
        if (v.isEmpty()) return false
        val t = v.trim()
        val upper = t.uppercase()
        if (upper in KNOWN_ROOMS) return true
        if (upper.startsWith("СЗ/") || upper.startsWith("М/")) return true
        if (upper.startsWith("ЦРК")) return true
        if (upper.contains("онлайн", ignoreCase = true)) return true
        val digits = t.count { it.isDigit() }
        val letters = t.count { it.isLetter() }
        return digits >= 1 && letters <= 2 && t.length <= 10 && !t.contains(' ')
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
