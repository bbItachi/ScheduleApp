package com.example.schedule

import android.app.*
import android.content.*
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.*

object Notifier {

    private const val CH_MORNING = "morning"
    private const val CH_BEFORE = "before"
    private const val DAYS_AHEAD = 7          // было 30 — снижаем нагрузку в 4 раза
    private const val MAX_REQ = 200           // было 800

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
        val start = time.substringBefore("-").trim().replace(':', '.')
        val parts = start.split(".")
        if (parts.size < 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        return h to m
    }

    fun scheduleMorning(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val h = ScheduleStore.morningHour(ctx)
        val m = ScheduleStore.morningMinute(ctx)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, h)
            set(Calendar.MINUTE, m)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        val intent = Intent(ctx, AlarmReceiver::class.java).apply { action = "morning" }
        val pi = PendingIntent.getBroadcast(
            ctx, 1000, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        } catch (_: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    // ⚠️ Тяжёлая операция — всегда вызывать из фонового потока
    fun scheduleBeforePairs(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val group = ScheduleStore.group(ctx)
        if (group.isEmpty()) return
        val before = ScheduleStore.beforeMinutes(ctx)

        // Отмена старых — только 200 ID (реально используется максимум ~100)
        for (i in 0..MAX_REQ) {
            val pi = PendingIntent.getBroadcast(
                ctx, 3000 + i,
                Intent(ctx, AlarmReceiver::class.java).apply { action = "before" },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) am.cancel(pi)
        }

        val today = Calendar.getInstance()
        var reqCode = 3000

        for (dayOffset in 0 until DAYS_AHEAD) {
            val dayCal = today.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, dayOffset)
            val dayName = ScheduleStore.dayNameRuFor(dayCal)
            val week = ScheduleStore.weekFor(ctx, dayCal)
            val pairs = ScheduleStore.pairsForDay(ctx, dayName, week, group)

            pairs.forEach { pair ->
                val (ph, pm) = parseStartMinutes(pair.time) ?: return@forEach
                val pairCal = dayCal.clone() as Calendar
                pairCal.set(Calendar.HOUR_OF_DAY, ph)
                pairCal.set(Calendar.MINUTE, pm)
                pairCal.set(Calendar.SECOND, 0)
                pairCal.set(Calendar.MILLISECOND, 0)
                pairCal.add(Calendar.MINUTE, -before)
                if (pairCal.timeInMillis <= System.currentTimeMillis()) return@forEach

                val intent = Intent(ctx, AlarmReceiver::class.java).apply {
                    action = "before"
                    putExtra("subject", pair.subgroups.joinToString(" / ") { it.subject })
                    putExtra("room", pair.subgroups.joinToString(" / ") { it.room })
                    putExtra("teacher", pair.subgroups.joinToString(" / ") { it.teacher })
                    putExtra("time", pair.time)
                    putExtra("num", pair.number)
                    putExtra("before", before)
                }
                val pi = PendingIntent.getBroadcast(
                    ctx, reqCode, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, pairCal.timeInMillis, pi)
                } catch (_: SecurityException) {
                    am.set(AlarmManager.RTC_WAKEUP, pairCal.timeInMillis, pi)
                }
                reqCode++
            }
        }
    }

    class AlarmReceiver : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            ensureChannels(ctx)
            val nm = ctx.getSystemService(NotificationManager::class.java)
            when (intent.action) {
                "morning" -> {
                    val group = ScheduleStore.group(ctx)
                    val today = Calendar.getInstance()
                    val dayName = ScheduleStore.dayNameRuFor(today)
                    val week = ScheduleStore.weekFor(ctx, today)
                    val pairs = ScheduleStore.pairsForDay(ctx, dayName, week, group)
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
                    // Перепланировать через background-thread через WorkManager
                    scheduleMorning(ctx)
                }
                "before" -> {
                    val subject = intent.getStringExtra("subject") ?: ""
                    val room = intent.getStringExtra("room") ?: ""
                    val teacher = intent.getStringExtra("teacher") ?: ""
                    val time = intent.getStringExtra("time") ?: ""
                    val num = intent.getIntExtra("num", 0)
                    val before = intent.getIntExtra("before", 15)
                    nm.notify(200 + num, NotificationCompat.Builder(ctx, CH_BEFORE)
                        .setSmallIcon(android.R.drawable.ic_dialog_alert)
                        .setContentTitle("Пара $num через $before мин")
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
                RescheduleWorker.schedule(ctx)
            }
        }
    }
}
