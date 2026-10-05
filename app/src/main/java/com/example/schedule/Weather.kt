package com.example.schedule

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

object Weather {

    // Северск, Томская область
    private const val CITY = "Северск"

    data class Info(val temp: Int, val wind: Int, val desc: String)

    suspend fun fetch(): Info? = withContext(Dispatchers.IO) {
        try {
            val cityEncoded = URLEncoder.encode(CITY, "UTF-8")
            val url = URL("https://wttr.in/$cityEncoded?format=j1&lang=ru")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", "curl/8.0")
            val text = conn.inputStream.bufferedReader().use { it.readText() }

            val root = Json.parseToJsonElement(text).jsonObject
            val current = root["current_condition"]!!.jsonArray[0].jsonObject
            val temp = current["temp_C"]!!.jsonPrimitive.content.toInt()
            val wind = current["windspeedKmph"]!!.jsonPrimitive.content.toInt()
            val descList = current["lang_ru"]?.jsonArray
            val desc = if (descList != null && descList.isNotEmpty())
                descList[0].jsonObject["value"]!!.jsonPrimitive.content
            else
                current["weatherDesc"]!!.jsonArray[0].jsonObject["value"]!!.jsonPrimitive.content

            Info(temp, wind, desc)
        } catch (_: Exception) { null }
    }
}
