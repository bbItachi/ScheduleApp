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

    // Известные аудитории, которые не похожи на числа
    private val KNOWN_ROOMS = setOf(
        "ЦРК", "М", "СЗ", "СЗ/1", "СЗ/2", "М/1", "М/2", "ОНЛАЙН"
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

        // ─── Находим строку с "Группа:" ───
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

        // ─── Собираем все группы ───
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

        // ─── Идём по строкам начиная с группы ───
        for (r in (groupRow + 1)..maxRow) {
            // Проверяем метку «ЧЕТНАЯ НЕДЕЛЯ» в любой колонке
            for (c in 1..maxCol) {
                val v = cell(cells, r, c)
                if (v.contains("ЧЕТНАЯ", true) || v.contains("ЧЁТНАЯ", true)) {
                    week = WeekType.EVEN; break
                }
            }

            // Определяем день недели (ищем в 1-3 колонках)
            for (c in 1..3) {
                val v = cell(cells, r, c).trim().uppercase()
                if (v in DAYS) { currentDay = v; break }
            }

            // Определяем номер пары (2-я колонка)
            val pairNum = cell(cells, r, 2).trim().toIntOrNull()
            if (pairNum == null || currentDay == null) continue

            // Время (4-я колонка)
            val time = cell(cells, r, 4).trim()
            if (time.isEmpty()) continue

            // ─── Разбираем данные для каждой группы ───
            for (i in groups.indices) {
                val (colStart, groupName) = groups[i]

                // Читаем 4 ячейки предмета/аудитории в текущей строке
                val rowCells = (0..3).map { cell(cells, r, colStart + it).trim() }
                // И 4 ячейки преподавателей в следующей строке
                val teachCells = (0..3).map { cell(cells, r + 1, colStart + it).trim() }

                // Классифицируем ячейки: что предмет, что аудитория
                val subjectPos = mutableListOf<Int>()
                val roomPos = mutableListOf<Int>()
                for (k in 0..3) {
                    val v = rowCells[k]
                    if (v.isEmpty()) continue
                    if (looksLikeRoom(v)) roomPos += k else subjectPos += k
                }

                val subs = mutableListOf<SubgroupPair>()

                when {
                    // Две подгруппы: 2 предмета + 2 аудитории
                    subjectPos.size >= 2 && roomPos.size >= 2 -> {
                        for (k in 0..1) {
                            val si = subjectPos[k]
                            val ri = roomPos[k]
                            val teacher = teachCells.getOrElse(si) { "" }
                                .ifEmpty { teachCells.firstOrNull { it.isNotEmpty() } ?: "" }
                            val (t, r) = cleanOnline(teacher, rowCells[ri])
                            subs += SubgroupPair(k + 1, rowCells[si], t, r)
                        }
                    }
                    // Один предмет (общий для всей группы)
                    subjectPos.size == 1 -> {
                        val si = subjectPos[0]
                        val ri = roomPos.firstOrNull()
                        val teacher = teachCells.getOrElse(si) { "" }
                            .ifEmpty { teachCells.firstOrNull { it.isNotEmpty() } ?: "" }
                        val (t, r) = cleanOnline(teacher, if (ri != null) rowCells[ri] else "")
                        subs += SubgroupPair(1, rowCells[si], t, r)
                    }
                    // Два предмета, но одна аудитория
                    subjectPos.size >= 2 -> {
                        val rawRoom = roomPos.firstOrNull()?.let { rowCells[it] } ?: ""
                        for (k in 0..1) {
                            val si = subjectPos[k]
                            val teacher = teachCells.getOrElse(si) { "" }
                            val (t, r) = cleanOnline(teacher, rawRoom)
                            subs += SubgroupPair(k + 1, rowCells[si], t, r)
                        }
                    }
                    // Только аудитории, без предмета (на всякий случай)
                    roomPos.size >= 1 -> {
                        for (k in roomPos.indices) {
                            val ri = roomPos[k]
                            val teacher = teachCells.getOrElse(ri) { "" }
                            subs += SubgroupPair(k + 1, "", teacher, rowCells[ri])
                        }
                    }
                }

                if (subs.isNotEmpty()) {
                    result += Lesson(groupName, pairNum, time, currentDay!!, week, subs)
                }
            }
        }

        return result
    }

    /**
     * Обработка «онлайн»:
     *   «Лизнева К.А. онлайн» → teacher=«Лизнева К.А.», room=«Онлайн»
     *   «онлайн» → teacher=«», room=«Онлайн»
     * Обычные данные не трогает.
     */
    private fun cleanOnline(teacher: String, room: String): Pair<String, String> {
        val t = teacher.trim()
        val r = room.trim()

        val tHasOnline = t.contains("онлайн", ignoreCase = true)
        val rHasOnline = r.contains("онлайн", ignoreCase = true)

        if (!tHasOnline && !rHasOnline) return t to r

        // Если онлайн в преподе — чистим имя, аудитория становится «Онлайн»
        if (tHasOnline) {
            val cleanT = t.replace(Regex("(?i)\\s*онлайн\\s*"), " ")
                .trim()
                .trim(',', '.', ';', '-', '_', ' ')
            return cleanT to "Онлайн"
        }

        // Если онлайн только в аудитории — она превращается в «Онлайн»
        if (rHasOnline) {
            return t to "Онлайн"
        }

        return t to r
    }

    /**
     * Похоже ли значение на аудиторию.
     * ВАЖНО: если в ячейке есть «онлайн» И какие-то буквы помимо «онлайн» —
     * это НЕ аудитория (а имя преподавателя). Но если только «онлайн» — аудитория.
     */
    private fun looksLikeRoom(v: String): Boolean {
        if (v.isEmpty()) return false
        val t = v.trim()

        // Спецслучай: содержит «онлайн»
        if (t.contains("онлайн", ignoreCase = true)) {
            val withoutOnline = t
                .replace(Regex("(?i)онлайн"), " ")
                .trim()
                .trim(',', '.', ';', '-', '_', ' ')
            // Если после удаления «онлайн» остались буквы — это препод
            if (withoutOnline.any { it.isLetter() }) return false
            // Только «онлайн» — это аудитория
            return true
        }

        val upper = t.uppercase()
        if (upper in KNOWN_ROOMS) return true
        if (upper.startsWith("СЗ/") || upper.startsWith("М/")) return true
        if (upper.startsWith("ЦРК")) return true

        // Классическая аудитория: 1+ цифра, ≤2 буквы, короткое, без пробелов
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
