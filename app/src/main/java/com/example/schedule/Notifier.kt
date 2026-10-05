package com.example.schedule

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.*

object Notifier {

    private const val CH_MORNING = "morning"
    private const val CH_BEFORE = "before"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_MORNING, "Утренняя сводка", NotificationManager.IMPORTANCE_DEFAULT)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_BEFORE, "Перед парой", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    private fun parseStartMinutes(time: String): Pair<Int, Int>? {
        // "9.30-11.05" или "09:30-11:05"
        val start = time.substringBefore("-").trim().replace(':', '.')
        val parts = start.split(".")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }

    fun scheduleMorning(ctx: Context, hour: Int = 7, minute: Int = 0) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        val intent = Intent(ctx, AlarmReceiver::class.java).apply {
            action = "morning"
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 1000, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setInexactRepeating(AlarmManager.RTC_WAKEUP, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    fun scheduleBeforePairs(ctx: Context, minutesBefore: Int = 15) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val today = ScheduleStore.dayNameRu()
        val week = ScheduleStore.currentWeek(ctx)
        val pairs = ScheduleStore.pairsFor(ctx, today, week)

        // отменим старые
        for (i in 0..20) {
            val pi = PendingIntent.getBroadcast(
                ctx, 2000 + i,
                Intent(ctx, AlarmReceiver::class.java).apply { action = "before" },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) am.cancel(pi)
        }

        pairs.forEachIndexed { idx, pair ->
            val (h, m) = parseStartMinutes(pair.time) ?: return@forEachIndexed
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0)
                add(Calendar.MINUTE, -minutesBefore)
            }
            if (cal.timeInMillis <= System.currentTimeMillis()) return@forEachIndexed

            val intent = Intent(ctx, AlarmReceiver::class.java).apply {
                action = "before"
                putExtra("subject", pair.subgroups.joinToString(" / ") { it.subject })
                putExtra("room", pair.subgroups.joinToString(" / ") { it.room })
                putExtra("teacher", pair.subgroups.joinToString(" / ") { it.teacher })
                putExtra("time", pair.time)
                putExtra("num", pair.number)
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 2000 + idx, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } catch (_: SecurityException) {
                am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        }
    }

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            ensureChannels(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            when (intent.action) {
                "morning" -> {
                    val today = ScheduleStore.dayNameRu()
                    val week = ScheduleStore.currentWeek(ctx)
                    val pairs = ScheduleStore.pairsFor(ctx, today, week)
                    val body = if (pairs.isEmpty()) "Пар нет 🎉"
                        else pairs.joinToString("\n") { p ->
                            "${p.number}. ${p.time} — ${p.subgroups.joinToString("/") { it.subject }} (ауд. ${p.subgroups.first().room})"
                        }
                    nm.notify(1, NotificationCompat.Builder(ctx, CH_MORNING)
                        .setSmallIcon(android.R.drawable.ic_dialog_info)
                        .setContentTitle("Доброе утро! Пары на сегодня")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                        .setAutoCancel(true)
                        .build())
                }
                "before" -> {
                    val subject = intent.getStringExtra("subject") ?: ""
                    val room = intent.getStringExtra("room") ?: ""
                    val teacher = intent.getStringExtra("teacher") ?: ""
                    val time = intent.getStringExtra("time") ?: ""
                    val num = intent.getIntExtra("num", 0)
                    nm.notify(100 + num, NotificationCompat.Builder(ctx, CH_BEFORE)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Пара $num через 15 минут")
                        .setContentText("$subject • ауд. $room")
                        .setStyle(NotificationCompat.BigTextStyle()
                            .bigText("$subject\nАудитория: $room\nПреподаватель: $teacher\nВремя: $time"))
                        .setAutoCancel(true)
                        .build())
                }
            }
        }
    }

    class BootReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                scheduleMorning(ctx)
                scheduleBeforePairs(ctx)
            }
        }
    }
}
