package com.example.schedule

import android.content.Context
import kotlinx.serialization.json.Json

object ScheduleStore {
    private const val FILE = "schedule.json"
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

    fun currentWeek(ctx: Context): WeekType {
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        return if (prefs.getString("week", "EVEN") == "ODD") WeekType.ODD else WeekType.EVEN
    }

    fun setCurrentWeek(ctx: Context, week: WeekType) {
        ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .edit().putString("week", week.name).apply()
    }

    fun pairsFor(ctx: Context, day: String, week: WeekType): List<Lesson> =
        load(ctx)?.pairs?.filter { it.day == day && it.week == week }?.sortedBy { it.number } ?: emptyList()

    fun dayNameRu(): String = when (java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)) {
        java.util.Calendar.MONDAY -> "ПОНЕДЕЛЬНИК"
        java.util.Calendar.TUESDAY -> "ВТОРНИК"
        java.util.Calendar.WEDNESDAY -> "СРЕДА"
        java.util.Calendar.THURSDAY -> "ЧЕТВЕРГ"
        java.util.Calendar.FRIDAY -> "ПЯТНИЦА"
        java.util.Calendar.SATURDAY -> "СУББОТА"
        else -> "ВОСКРЕСЕНЬЕ"
    }
}
