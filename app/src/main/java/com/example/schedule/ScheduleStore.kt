package com.example.schedule

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar

object ScheduleStore {
    private const val FILE = "schedule.json"
    private const val NOTES_FILE = "notes.json"
    private val json = Json { ignoreUnknownKeys = true }

    private var scheduleCache: SavedSchedule? = null
    private var scheduleLoaded = false
    private var notesCache: List<Note>? = null
    private var prefsInstance: SharedPreferences? = null
    private val pairsCache = HashMap<String, List<Lesson>>()

    private fun prefs(ctx: Context): SharedPreferences {
        prefsInstance?.let { return it }
        val p = ctx.applicationContext.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        prefsInstance = p
        return p
    }

    fun save(ctx: Context, pairs: List<Lesson>) {
        val data = SavedSchedule(pairs, System.currentTimeMillis())
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE)
            .use { it.write(json.encodeToString(SavedSchedule.serializer(), data).toByteArray()) }
        scheduleCache = data
        scheduleLoaded = true
        pairsCache.clear()
    }

    fun load(ctx: Context): SavedSchedule? {
        if (scheduleLoaded) return scheduleCache
        return try {
            val bytes = ctx.openFileInput(FILE).use { it.readBytes() }
            val parsed = json.decodeFromString(SavedSchedule.serializer(), bytes.decodeToString())
            scheduleCache = parsed
            scheduleLoaded = true
            parsed
        } catch (_: Exception) {
            scheduleCache = null
            scheduleLoaded = true
            null
        }
    }

    fun clear(ctx: Context) {
        ctx.deleteFile(FILE)
        scheduleCache = null
        scheduleLoaded = true
        pairsCache.clear()
    }

    fun currentWeek(ctx: Context): WeekType =
        if (prefs(ctx).getString("week", "EVEN") == "ODD") WeekType.ODD else WeekType.EVEN
    fun setCurrentWeek(ctx: Context, week: WeekType) {
        prefs(ctx).edit().putString("week", week.name).apply()
    }

    fun group(ctx: Context): String = prefs(ctx).getString("group", "") ?: ""
    fun setGroup(ctx: Context, g: String) {
        prefs(ctx).edit().putString("group", g).apply()
        pairsCache.clear()
    }

    fun morningHour(ctx: Context): Int = prefs(ctx).getInt("morning_hour", 7)
    fun morningMinute(ctx: Context): Int = prefs(ctx).getInt("morning_minute", 0)
    fun setMorning(ctx: Context, h: Int, m: Int) {
        prefs(ctx).edit().putInt("morning_hour", h).putInt("morning_minute", m).apply()
    }

    fun beforeMinutes(ctx: Context): Int = prefs(ctx).getInt("before_min", 15)
    fun setBeforeMinutes(ctx: Context, m: Int) {
        prefs(ctx).edit().putInt("before_min", m).apply()
    }

    fun autoWeek(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_week", false)
    fun setAutoWeek(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("auto_week", v).apply()
    }

    fun semesterStart(ctx: Context): Long = prefs(ctx).getLong("sem_start", 0)
    fun setSemesterStart(ctx: Context, t: Long) {
        prefs(ctx).edit().putLong("sem_start", t).apply()
    }

    fun dndEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean("dnd_enabled", false)
    fun setDndEnabled(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("dnd_enabled", v).apply()
    }

    // ─── Автообновление ───

    fun autoUpdate(ctx: Context): Boolean = prefs(ctx).getBoolean("auto_update", false)
    fun setAutoUpdate(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean("auto_update", v).apply()
    }

    fun lastAutoUpdate(ctx: Context): Long = prefs(ctx).getLong("last_auto_update", 0)
    fun setLastAutoUpdate(ctx: Context, t: Long) {
        prefs(ctx).edit().putLong("last_auto_update", t).apply()
    }

    fun favorites(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("favorites", emptySet()) ?: emptySet()

    fun toggleFavorite(ctx: Context, key: String): Boolean {
        val cur = favorites(ctx).toMutableSet()
        val added = if (key in cur) { cur.remove(key); false } else { cur.add(key); true }
        prefs(ctx).edit().putStringSet("favorites", cur).apply()
        return added
    }

    fun skipped(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("skipped", emptySet()) ?: emptySet()

    fun toggleSkipped(ctx: Context, key: String): Boolean {
        val cur = skipped(ctx).toMutableSet()
        val added = if (key in cur) { cur.remove(key); false } else { cur.add(key); true }
        prefs(ctx).edit().putStringSet("skipped", cur).apply()
        return added
    }

    fun notes(ctx: Context): List<Note> {
        notesCache?.let { return it }
        val result = try {
            val bytes = ctx.openFileInput(NOTES_FILE).use { it.readBytes() }
            json.decodeFromString(ListSerializer(Note.serializer()), bytes.decodeToString())
        } catch (_: Exception) { emptyList() }
        notesCache = result
        return result
    }

    private fun saveNotes(ctx: Context, notes: List<Note>) {
        ctx.openFileOutput(NOTES_FILE, Context.MODE_PRIVATE)
            .use { it.write(json.encodeToString(ListSerializer(Note.serializer()), notes).toByteArray()) }
        notesCache = notes
    }

    fun getNote(ctx: Context, key: String): String =
        notes(ctx).firstOrNull { it.key == key }?.text ?: ""

    fun setNote(ctx: Context, key: String, text: String) {
        val cur = notes(ctx).toMutableList()
        cur.removeAll { it.key == key }
        if (text.isNotBlank()) cur.add(Note(key, text, System.currentTimeMillis()))
        saveNotes(ctx, cur)
    }

    fun pairsFor(ctx: Context, day: String, week: WeekType): List<Lesson> {
        val g = group(ctx)
        val key = "$day|${week.name}|$g"
        pairsCache[key]?.let { return it }
        val list = load(ctx)?.pairs
            ?.filter { it.group == g && it.day == day && it.week == week }
            ?.sortedBy { it.number } ?: emptyList()
        pairsCache[key] = list
        return list
    }

    fun pairsForDay(ctx: Context, day: String, week: WeekType, group: String): List<Lesson> {
        val key = "$day|${week.name}|$group"
        pairsCache[key]?.let { return it }
        val list = load(ctx)?.pairs
            ?.filter { it.group == group && it.day == day && it.week == week }
            ?.sortedBy { it.number } ?: emptyList()
        pairsCache[key] = list
        return list
    }

    fun allGroups(ctx: Context): List<String> =
        load(ctx)?.pairs?.map { it.group }?.distinct()?.sorted() ?: emptyList()

    fun dayNameRu(): String = dayNameRuFor(Calendar.getInstance())

    fun dayNameRuFor(cal: Calendar): String = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> "ПОНЕДЕЛЬНИК"
        Calendar.TUESDAY -> "ВТОРНИК"
        Calendar.WEDNESDAY -> "СРЕДА"
        Calendar.THURSDAY -> "ЧЕТВЕРГ"
        Calendar.FRIDAY -> "ПЯТНИЦА"
        Calendar.SATURDAY -> "СУББОТА"
        else -> "ВОСКРЕСЕНЬЕ"
    }

    fun weekFor(ctx: Context, cal: Calendar): WeekType {
        if (!autoWeek(ctx)) return currentWeek(ctx)
        val start = semesterStart(ctx)
        if (start == 0L) return currentWeek(ctx)
        val diffDays = ((cal.timeInMillis - start) / (1000L * 60 * 60 * 24)).toInt()
        val weekNum = diffDays / 7
        return if (weekNum % 2 == 0) WeekType.ODD else WeekType.EVEN
    }

    // ─── Утилиты времени ───

    fun timeToMinutes(t: String): Int? {
        val clean = t.trim().replace(':', '.')
        val parts = clean.split(Regex("[.\\s]+")).filter { it.isNotEmpty() }
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h * 60 + m
    }

    // ─── Преподаватели ───

    fun lessonsByTeacher(ctx: Context, teacher: String): List<Lesson> {
        val all = load(ctx)?.pairs ?: return emptyList()
        val unique = LinkedHashMap<String, Lesson>()
        for (l in all) {
            if (!l.subgroups.any { it.teacher.contains(teacher, true) }) continue
            val key = "${l.day}|${l.week.name}|${l.number}|${l.group}"
            if (!unique.containsKey(key)) unique[key] = l
        }
        return unique.values.sortedWith(
            compareBy(
                { if (it.week == WeekType.EVEN) 0 else 1 },
                { dayOrder(it.day) },
                { it.number }
            )
        )
    }

    fun allTeachers(ctx: Context): List<String> {
        val all = load(ctx)?.pairs ?: return emptyList()
        return all.flatMap { l -> l.subgroups.map { it.teacher } }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }

    fun dayOrder(day: String): Int = when (day) {
        "ПОНЕДЕЛЬНИК" -> 1
        "ВТОРНИК" -> 2
        "СРЕДА" -> 3
        "ЧЕТВЕРГ" -> 4
        "ПЯТНИЦА" -> 5
        "СУББОТА" -> 6
        "ВОСКРЕСЕНЬЕ" -> 7
        else -> 99
    }
}
