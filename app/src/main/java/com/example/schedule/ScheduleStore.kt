package com.example.schedule

import android.content.Context
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.Calendar

object ScheduleStore {
    private const val FILE = "schedule.json"
    private const val NOTES_FILE = "notes.json"
    private val json = Json { ignoreUnknownKeys = true }

    fun save(ctx: Context, pairs: List<Lesson>) {
        val data = SavedSchedule(pairs, System.currentTimeMillis())
        ctx.openFileOutput(FILE, Context.MODE_PRIVATE)
            .use { it.write(json.encodeToString(SavedSchedule.serializer(), data).toByteArray()) }
    }

    fun load(ctx: Context): SavedSchedule? = try {
        val bytes = ctx.openFileInput(FILE).use { it.readBytes() }
        json.decodeFromString(SavedSchedule.serializer(), bytes.decodeToString())
    } catch (_: Exception) { null }

    fun clear(ctx: Context) { ctx.deleteFile(FILE) }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    fun currentWeek(ctx: Context): WeekType =
        if (prefs(ctx).getString("week", "EVEN") == "ODD") WeekType.ODD else WeekType.EVEN
    fun setCurrentWeek(ctx: Context, week: WeekType) {
        prefs(ctx).edit().putString("week", week.name).apply()
    }

    fun group(ctx: Context): String = prefs(ctx).getString("group", "") ?: ""
    fun setGroup(ctx: Context, g: String) {
        prefs(ctx).edit().putString("group", g).apply()
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

    fun favorites(ctx: Context): Set<String> =
        prefs(ctx).getStringSet("favorites", emptySet()) ?: emptySet()

    fun toggleFavorite(ctx: Context, key: String): Boolean {
        val cur = favorites(ctx).toMutableSet()
        val added = if (key in cur) { cur.remove(key); false } else { cur.add(key); true }
        prefs(ctx).edit().putStringSet("favorites", cur).apply()
        return added
    }

    fun notes(ctx: Context): List<Note> = try {
        val bytes = ctx.openFileInput(NOTES_FILE).use { it.readBytes() }
        json.decodeFromString(ListSerializer(Note.serializer()), bytes.decodeToString())
    } catch (_: Exception) { emptyList() }

    private fun saveNotes(ctx: Context, notes: List<Note>) {
        ctx.openFileOutput(NOTES_FILE, Context.MODE_PRIVATE)
            .use { it.write(json.encodeToString(ListSerializer(Note.serializer()), notes).toByteArray()) }
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
        return load(ctx)?.pairs?.filter { it.group == g && it.day == day && it.week == week }
            ?.sortedBy { it.number } ?: emptyList()
    }

    fun pairsForDay(ctx: Context, day: String, week: WeekType, group: String): List<Lesson> =
        load(ctx)?.pairs?.filter { it.group == group && it.day == day && it.week == week }
            ?.sortedBy { it.number } ?: emptyList()

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
}
