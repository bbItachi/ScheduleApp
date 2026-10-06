package com.example.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object Weather {

    private const val YANDEX_KEY = "7361d265-d0b7-4f40-bd3a-68723050133a"
    private const val LAT = 56.6003
    private const val LON = 84.8503
    private const val CACHE_TTL_MS = 30 * 60 * 1000L   // 30 минут

    data class Info(
        val temp: Int,
        val feelsLike: Int,
        val wind: Double,
        val humidity: Int,
        val pressure: Int,
        val desc: String
    )

    // ─── Кэш в памяти ───
    private var cached: Info? = null
    private var cacheTime: Long = 0

    suspend fun fetch(force: Boolean = false): Info? = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!force) {
            cached?.let { if (now - cacheTime < CACHE_TTL_MS) return@withContext it }
        }

        try {
            val url = URL(
                "https://api.weather.yandex.ru/v2/forecast" +
                "?lat=$LAT&lon=$LON&lang=ru_RU&limit=1&hours=false"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("X-Yandex-Weather-Key", YANDEX_KEY)

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val root = Json.parseToJsonElement(text).jsonObject
            val fact = root["fact"]!!.jsonObject

            val info = Info(
                temp = fact["temp"]!!.jsonPrimitive.content.toInt(),
                feelsLike = fact["feels_like"]!!.jsonPrimitive.content.toInt(),
                wind = fact["wind_speed"]!!.jsonPrimitive.content.toDouble(),
                humidity = fact["humidity"]!!.jsonPrimitive.content.toInt(),
                pressure = fact["pressure_mm"]!!.jsonPrimitive.content.toInt(),
                desc = describe(fact["condition"]!!.jsonPrimitive.content)
            )
            cached = info
            cacheTime = now
            info
        } catch (_: Exception) {
            // Если не удалось — вернём старый кэш, если есть
            cached
        }
    }

    private fun describe(code: String) = when (code) {
        "clear" -> "Ясно ☀️"
        "partly-cloudy" -> "Малооблачно 🌤"
        "cloudy" -> "Облачно ☁️"
        "overcast" -> "Пасмурно ☁️"
        "drizzle" -> "Морось 🌦"
        "light-rain" -> "Небольшой дождь 🌧"
        "rain" -> "Дождь 🌧"
        "moderate-rain" -> "Умеренный дождь 🌧"
        "heavy-rain" -> "Сильный дождь 🌧"
        "continuous-heavy-rain" -> "Затяжной дождь 🌧"
        "showers" -> "Ливень 🌧"
        "wet-snow" -> "Мокрый снег 🌨"
        "light-snow" -> "Небольшой снег 🌨"
        "snow" -> "Снег ❄️"
        "snow-showers" -> "Снегопад 🌨"
        "hail" -> "Град 🌨"
        "thunderstorm" -> "Гроза ⛈"
        "thunderstorm-with-rain" -> "Гроза с дождём ⛈"
        "thunderstorm-with-hail" -> "Гроза с градом ⛈"
        else -> code
    }
}
