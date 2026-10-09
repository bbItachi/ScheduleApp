package com.example.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val GITHUB_API =
        "https://api.github.com/repos/bbItachi/ScheduleApp/releases/latest"

    data class UpdateInfo(
        val version: String,        // например "1.8"
        val tagName: String,        // например "v1.8"
        val downloadUrl: String,    // ссылка на .apk в assets релиза
        val notes: String           // описание релиза
    )

    // ─── Проверка обновления ───

    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty("User-Agent", "ScheduleApp")
                setRequestProperty("Accept", "application/vnd.github+json")
            }

            if (conn.responseCode != 200) return@withContext null

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val root = Json.parseToJsonElement(text).jsonObject
            val tagName = root["tag_name"]?.jsonPrimitive?.content ?: return@withContext null
            val version = tagName.trimStart('v', 'V')
            val notes = root["body"]?.jsonPrimitive?.content ?: ""

            // Ищем APK среди assets
            val assets = root["assets"]?.jsonArray ?: return@withContext null
            var apkUrl: String? = null
            for (a in assets) {
                val name = a.jsonObject["name"]?.jsonPrimitive?.content ?: continue
                if (name.endsWith(".apk", ignoreCase = true)) {
                    apkUrl = a.jsonObject["browser_download_url"]?.jsonPrimitive?.content
                    break
                }
            }
            val urlApk = apkUrl ?: return@withContext null

            // Сравниваем с текущей версией
            val current = BuildConfig.VERSION_NAME
            if (!isNewer(version, current)) return@withContext null

            UpdateInfo(version, tagName, urlApk, notes)
        } catch (_: Exception) {
            null
        }
    }

    // Сравнение "1.8" > "1.7"
    private fun isNewer(newVer: String, oldVer: String): Boolean {
        try {
            val n = newVer.split(".").map { it.toIntOrNull() ?: 0 }
            val o = oldVer.split(".").map { it.toIntOrNull() ?: 0 }
            val size = maxOf(n.size, o.size)
            for (i in 0 until size) {
                val nv = n.getOrElse(i) { 0 }
                val ov = o.getOrElse(i) { 0 }
                if (nv > ov) return true
                if (nv < ov) return false
            }
            return false
        } catch (_: Exception) {
            return false
        }
    }

    // ─── Скачивание APK ───

    suspend fun downloadApk(
        ctx: Context,
        url: String,
        onProgress: (percent: Int) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        try {
            val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
            // Удалим старые файлы
            dir.listFiles()?.forEach { it.delete() }
            val outFile = File(dir, "schedule-update.apk")

            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "ScheduleApp")
                instanceFollowRedirects = true
            }

            if (conn.responseCode != 200) return@withContext null

            val total = conn.contentLength.toLong()
            var downloaded = 0L

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } > 0) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (total > 0) {
                            val percent = ((downloaded * 100) / total).toInt()
                            withContext(Dispatchers.Main) { onProgress(percent) }
                        }
                    }
                }
            }
            conn.disconnect()
            outFile
        } catch (_: Exception) {
            null
        }
    }

    // ─── Запуск установщика ───

    fun installApk(ctx: Context, apkFile: File): Boolean {
        return try {
            // Проверяем разрешение «Установка из неизвестных источников»
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!ctx.packageManager.canRequestPackageInstalls()) {
                    // Отправляем в настройки
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${ctx.packageName}")
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    ctx.startActivity(intent)
                    return false
                }
            }

            val apkUri = FileProvider.getUriForFile(
                ctx,
                "${ctx.packageName}.fileprovider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            ctx.startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }
}
