package com.example.schedule

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import java.util.TimeZone

/**
 * Интернет-время для Северска (Томская область, часовой пояс Asia/Tomsk, UTC+7).
 * Синхронизируется с worldtimeapi.org, при неудаче — с timeapi.io.
 * Хранит смещение между системным временем телефона и интернет-временем.
 */
object InternetTime {

    private const val API_WORLDTIME = "https://worldtimeapi.org/api/timezone/Asia/Tomsk"
    private const val API_TIMEAPI_IO = "https://timeapi.io/api/Time/current/zone?timeZone=Asia/Tomsk"
    private const val PREF_OFFSET = "internet_time_offset"
    private const val PREF_SYNCED_AT = "internet_time_synced_at"

    val TOMSK_TZ: TimeZone = TimeZone.getTimeZone("Asia/Tomsk")

    private var offsetMs: Long = 0L
    private var lastSyncMs: Long = 0L

    fun loadCached(ctx: Context) {
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        offsetMs = prefs.getLong(PREF_OFFSET, 0L)
        lastSyncMs = prefs.getLong(PREF_SYNCED_AT, 0L)
    }

    suspend fun sync(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val internetMs = tryFetchWorldTime() ?: tryFetchTimeApi()
        if (internetMs != null) {
            offsetMs = internetMs - System.currentTimeMillis()
            lastSyncMs = System.currentTimeMillis()
            ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                .putLong(PREF_OFFSET, offsetMs)
                .putLong(PREF_SYNCED_AT, lastSyncMs)
                .apply()
            true
        } else false
    }

    private fun tryFetchWorldTime(): Long? {
        return try {
            val conn = (URL(API_WORLDTIME).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "ScheduleApp/1.11")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = Json.parseToJsonElement(text).jsonObject
            val dt = root["datetime"]?.jsonPrimitive?.content ?: return null
            // ISO: "2026-10-15T20:00:00.123456+07:00"
            OffsetDateTime.parse(dt).toInstant().toEpochMilli()
        } catch (_: Exception) { null }
    }

    private fun tryFetchTimeApi(): Long? {
        return try {
            val conn = (URL(API_TIMEAPI_IO).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                setRequestProperty("User-Agent", "ScheduleApp/1.11")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val root = Json.parseToJsonElement(text).jsonObject
            val dt = root["dateTime"]?.jsonPrimitive?.content ?: return null
            // "2026-10-15T20:00:00.000" — интерпретируем как Asia/Tomsk
            LocalDateTime.parse(dt)
                .atZone(ZoneId.of("Asia/Tomsk"))
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) { null }
    }

    fun now(): Date = Date(System.currentTimeMillis() + offsetMs)

    fun calendar(): Calendar {
        val cal = Calendar.getInstance(TOMSK_TZ)
        cal.timeInMillis = now().time
        return cal
    }

    fun isSynced(): Boolean = lastSyncMs > 0L
    fun lastSyncAgoMs(): Long =
        if (lastSyncMs == 0L) Long.MAX_VALUE else System.currentTimeMillis() - lastSyncMs

    /**
     * Логика «следующего дня»:
     * - Понедельник: если час >= 22:00 → показываем вторник
     * - Остальные дни: если время >= 21:30 → показываем следующий день
     */
    fun isDisplayingTomorrow(): Boolean {
        val cal = calendar()
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        val minute = cal.get(Calendar.MINUTE)

        return if (dow == Calendar.MONDAY) {
            hour >= 22
        } else {
            hour > 21 || (hour == 21 && minute >= 30)
        }
    }

    /** Календарь с уже учтённым сдвигом на завтра (если надо). */
    fun displayCalendar(): Calendar {
        val cal = calendar()
        if (isDisplayingTomorrow()) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal
    }

    /** Название дня недели на русском, с учётом «завтра после 21:30». */
    fun displayDayNameRu(): String {
        val cal = displayCalendar()
        return when (cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "ПОНЕДЕЛЬНИК"
            Calendar.TUESDAY -> "ВТОРНИК"
            Calendar.WEDNESDAY -> "СРЕДА"
            Calendar.THURSDAY -> "ЧЕТВЕРГ"
            Calendar.FRIDAY -> "ПЯТНИЦА"
            Calendar.SATURDAY -> "СУББОТА"
            else -> "ВОСКРЕСЕНЬЕ"
        }
    }
}
