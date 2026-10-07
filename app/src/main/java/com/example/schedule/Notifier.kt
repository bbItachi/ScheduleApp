package com.example.schedule

import android.app.*
import android.content.*
import android.graphics.Bitmap
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import java.util.*

object Notifier {

    private const val CH_MORNING = "morning"
    private const val CH_BEFORE = "before"
    private const val DAYS_AHEAD = 7
    private const val GROUP_KEY = "com.example.schedule.GROUP"

    fun ensureChannels(ctx: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val nm = ctx.getSystemService(NotificationManager::class.java)

        val morning = NotificationChannel(
            CH_MORNING, "Утренняя сводка", NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Пары и погода на сегодня"
            enableLights(true)
            lightColor = 0xFF3B82F6.toInt()
            setShowBadge(true)
        }
        nm.createNotificationChannel(morning)

        val before = NotificationChannel(
            CH_BEFORE, "Перед парой", NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Напоминание за несколько минут до пары"
            enableLights(true)
            lightColor = 0xFF3B82F6.toInt()
            enableVibration(true)
            setShowBadge(true)
        }
        nm.createNotificationChannel(before)
    }

    private fun getLargeIcon(ctx: Context): Bitmap? {
        return try {
            val drawable = ContextCompat.getDrawable(ctx, R.drawable.ic_launcher_app)
            drawable?.toBitmap(width = 128, height = 128)
        } catch (_: Exception) { null }
    }

    private fun openAppPendingIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, 777, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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

    private fun parseEndMinutes(time: String): Pair<Int, Int>? {
        val end = time.substringAfter("-").trim().replace(':', '.')
        val parts = end.split(".")
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

    fun scheduleBeforePairs(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)
        val group = ScheduleStore.group(ctx)
        if (group.isEmpty()) return
        val before = ScheduleStore.beforeMinutes(ctx)

        for (i in 0..200) {
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

    fun scheduleDnd(ctx: Context) {
        val am = ctx.getSystemService(AlarmManager::class.java)

        for (i in 0..600) {
            val pi = PendingIntent.getBroadcast(
                ctx, 5000 + i,
                Intent(ctx, AlarmReceiver::class.java).apply { action = "dnd_on" },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) am.cancel(pi)
            val pi2 = PendingIntent.getBroadcast(
                ctx, 5700 + i,
                Intent(ctx, AlarmReceiver::class.java).apply { action = "dnd_off" },
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi2 != null) am.cancel(pi2)
        }

        if (!ScheduleStore.dndEnabled(ctx)) return
        val group = ScheduleStore.group(ctx)
        if (group.isEmpty()) return

        val today = Calendar.getInstance()
        var onCode = 5000
        var offCode = 5700

        for (dayOffset in 0 until DAYS_AHEAD) {
            val dayCal = today.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, dayOffset)
            val dayName = ScheduleStore.dayNameRuFor(dayCal)
            val week = ScheduleStore.weekFor(ctx, dayCal)
            val pairs = ScheduleStore.pairsForDay(ctx, dayName, week, group)

            for (pair in pairs) {
                val start = parseStartMinutes(pair.time) ?: continue
                val end = parseEndMinutes(pair.time) ?: continue

                val startCal = dayCal.clone() as Calendar
                startCal.set(Calendar.HOUR_OF_DAY, start.first)
                startCal.set(Calendar.MINUTE, start.second)
                startCal.set(Calendar.SECOND, 0)
                startCal.set(Calendar.MILLISECOND, 0)

                if (startCal.timeInMillis > System.currentTimeMillis()) {
                    val pi = PendingIntent.getBroadcast(
                        ctx, onCode++,
                        Intent(ctx, AlarmReceiver::class.java).apply { action = "dnd_on" },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, startCal.timeInMillis, pi) }
                    catch (_: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, startCal.timeInMillis, pi) }
                }

                val endCal = dayCal.clone() as Calendar
                endCal.set(Calendar.HOUR_OF_DAY, end.first)
                endCal.set(Calendar.MINUTE, end.second)
                endCal.set(Calendar.SECOND, 0)
                endCal.set(Calendar.MILLISECOND, 0)

                if (endCal.timeInMillis > System.currentTimeMillis()) {
                    val pi = PendingIntent.getBroadcast(
                        ctx, offCode++,
                        Intent(ctx, AlarmReceiver::class.java).apply { action = "dnd_off" },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    try { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endCal.timeInMillis, pi) }
                    catch (_: SecurityException) { am.set(AlarmManager.RTC_WAKEUP, endCal.timeInMillis, pi) }
                }
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

                    val largeIcon = getLargeIcon(ctx)
                    val contentPi = openAppPendingIntent(ctx)

                    if (pairs.isEmpty()) {
                        val n = NotificationCompat.Builder(ctx, CH_MORNING)
                            .setSmallIcon(R.drawable.ic_notification)
                            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
                            .setColor(0xFF3B82F6.toInt())
                            .setContentTitle("Доброе утро!")
                            .setContentText("Сегодня пар нет 🎉")
                            .setContentIntent(contentPi)
                            .setAutoCancel(true)
                            .setGroup(GROUP_KEY)
                            .build()
                        nm.notify(1, n)
                    } else {
                        val style = NotificationCompat.InboxStyle()
                        for (p in pairs.take(10)) {
                            val subj = p.subgroups.joinToString("/") { it.subject }
                                .let { if (it.length > 45) it.take(42) + "…" else it }
                            style.addLine("${p.number}. ${p.time}  ·  $subj")
                        }
                        if (pairs.size > 10) {
                            style.setSummaryText("Всего ${pairs.size} пар")
                        } else {
                            style.setSummaryText("Всего ${pairs.size} ${plural(pairs.size, "пара", "пары", "пар")}")
                        }

                        val n = NotificationCompat.Builder(ctx, CH_MORNING)
                            .setSmallIcon(R.drawable.ic_notification)
                            .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
                            .setColor(0xFF3B82F6.toInt())
                            .setContentTitle("Доброе утро! Пары на сегодня")
                            .setContentText("${pairs.size} ${plural(pairs.size, "пара", "пары", "пар")} · нажми, чтобы открыть")
                            .setStyle(style)
                            .setContentIntent(contentPi)
                            .setAutoCancel(true)
                            .setGroup(GROUP_KEY)
                            .setShowWhen(true)
                            .build()
                        nm.notify(1, n)
                    }
                    scheduleMorning(ctx)
                }

                "before" -> {
                    val subject = intent.getStringExtra("subject") ?: ""
                    val room = intent.getStringExtra("room") ?: ""
                    val teacher = intent.getStringExtra("teacher") ?: ""
                    val time = intent.getStringExtra("time") ?: ""
                    val num = intent.getIntExtra("num", 0)
                    val before = intent.getIntExtra("before", 15)

                    val largeIcon = getLargeIcon(ctx)
                    val contentPi = openAppPendingIntent(ctx)

                    val bigText = buildString {
                        append(subject).append("\n\n")
                        append("🏛  Аудитория: ").append(room).append("\n")
                        append("👤  Преподаватель: ").append(teacher).append("\n")
                        append("🕐  Время: ").append(time).append("\n")
                    }

                    val n = NotificationCompat.Builder(ctx, CH_BEFORE)
                        .setSmallIcon(R.drawable.ic_notification)
                        .apply { if (largeIcon != null) setLargeIcon(largeIcon) }
                        .setColor(0xFF3B82F6.toInt())
                        .setContentTitle("Через $before мин · пара $num")
                        .setContentText("$subject · ауд. $room")
                        .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                        .setContentIntent(contentPi)
                        .setAutoCancel(true)
                        .setGroup(GROUP_KEY)
                        .setCategory(NotificationCompat.CATEGORY_EVENT)
                        .setShowWhen(true)
                        .build()
                    nm.notify(200 + num, n)
                }

                "dnd_on" -> {
                    if (ScheduleStore.dndEnabled(ctx)) {
                        try { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE) }
                        catch (_: SecurityException) { }
                    }
                }

                "dnd_off" -> {
                    if (ScheduleStore.dndEnabled(ctx)) {
                        try { nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL) }
                        catch (_: SecurityException) { }
                    }
                }
            }
        }
    }

    private fun plural(n: Int, one: String, few: String, many: String): String = when {
        n % 10 == 1 && n % 100 != 11 -> one
        n % 10 in 2..4 && (n % 100 < 10 || n % 100 >= 20) -> few
        else -> many
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
