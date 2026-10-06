package com.example.schedule

import android.content.Context
import androidx.work.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class ScheduleUpdater(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {

    override fun doWork(): Result {
        return try {
            val ctx = applicationContext
            val url = URL("http://xn--j1ahcbhc.xn--p1ai/rasp.xlsx")

            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "ScheduleApp/1.5")
                instanceFollowRedirects = true
            }

            val parsed = conn.inputStream.use { XlsxParser.parse(it) }
            conn.disconnect()

            if (parsed.isEmpty()) return Result.retry()

            ScheduleStore.save(ctx, parsed)
            ScheduleStore.setLastAutoUpdate(ctx, System.currentTimeMillis())

            // Обновить виджет
            ScheduleWidget.updateAll(ctx)

            // Перепланировать уведомления
            Notifier.scheduleBeforePairs(ctx)
            Notifier.scheduleDnd(ctx)

            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "auto_update_schedule"

        fun schedule(ctx: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(ctx)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }

            val request = PeriodicWorkRequestBuilder<ScheduleUpdater>(1, TimeUnit.DAYS)
                .setInitialDelay(30, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()

            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun runNow(ctx: Context) {
            val request = OneTimeWorkRequestBuilder<ScheduleUpdater>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .build()
            WorkManager.getInstance(ctx).enqueue(request)
        }
    }
}
