package com.example.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

object Weather {

    // Северск, Томская область
    private const val LAT = 56.6003
    private const val LON = 84.8503

    data class Info(val temp: Int, val wind: Int, val desc: String)

    suspend fun fetch(): Info? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$LAT&longitude=$LON&current_weather=true"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val cw = Json.parseToJsonElement(text).jsonObject["current_weather"]!!.jsonObject
            val t = cw["temperature"]!!.jsonPrimitive.content.toDouble().toInt()
            val w = cw["windspeed"]!!.jsonPrimitive.content.toDouble().toInt()
            val code = cw["weathercode"]!!.jsonPrimitive.content.toInt()
            Info(t, w, describe(code))
        } catch (_: Exception) { null }
    }

    private fun describe(code: Int) = when (code) {
        0 -> "Ясно ☀️"
        1, 2 -> "Малооблачно 🌤"
        3 -> "Пасмурно ☁️"
        45, 48 -> "Туман 🌫"
        51, 53, 55 -> "Морось 🌦"
        61, 63, 65 -> "Дождь 🌧"
        71, 73, 75, 77 -> "Снег ❄️"
        80, 81, 82 -> "Ливень 🌧"
        85, 86 -> "Снегопад 🌨"
        95, 96, 99 -> "Гроза ⛈"
        else -> "—"
    }
}
