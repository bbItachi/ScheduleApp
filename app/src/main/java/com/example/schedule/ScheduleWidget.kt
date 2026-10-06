package com.example.schedule

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.util.Calendar

class ScheduleWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) {
            val views = buildViews(context)
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE) {
            updateAll(context)
        }
    }

    companion object {

        fun updateAll(ctx: Context) {
            try {
                val mgr = AppWidgetManager.getInstance(ctx)
                val ids = mgr.getAppWidgetIds(
                    ComponentName(ctx, ScheduleWidget::class.java)
                )
                if (ids.isEmpty()) return
                val views = buildViews(ctx)
                for (id in ids) mgr.updateAppWidget(id, views)
            } catch (_: Exception) { }
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val views = RemoteViews(ctx.packageName, R.layout.widget_layout)

            // Открыть приложение при тапе
            val openIntent = Intent(ctx, MainActivity::class.java)
            val pi = PendingIntent.getActivity(
                ctx, 0, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pi)

            val group = ScheduleStore.group(ctx)

            if (group.isEmpty()) {
                views.setTextViewText(R.id.widget_header, "Расписание СПК")
                views.setTextViewText(R.id.widget_time, "Открой приложение")
                views.setTextViewText(R.id.widget_subject, "Выбери группу и загрузи расписание")
                views.setTextViewText(R.id.widget_room, "")
                views.setTextViewText(R.id.widget_footer, "Нажми, чтобы открыть")
                return views
            }

            val cal = Calendar.getInstance()
            val dayName = ScheduleStore.dayNameRuFor(cal)
            val week = ScheduleStore.weekFor(ctx, cal)
            val pairs = ScheduleStore.pairsForDay(ctx, dayName, week, group)

            val weekLabel = if (week == WeekType.EVEN) "чётная" else "нечётная"
            views.setTextViewText(R.id.widget_header, "Сегодня · ${shortDay(dayName)} · $weekLabel")

            if (pairs.isEmpty()) {
                views.setTextViewText(R.id.widget_time, "Пар нет 🎉")
                views.setTextViewText(R.id.widget_subject, "Отдыхай!")
                views.setTextViewText(R.id.widget_room, "")
                views.setTextViewText(R.id.widget_footer, "Группа $group")
                return views
            }

            val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

            // Ищем текущую или ближайшую пару
            var current: Lesson? = null
            var next: Lesson? = null
            var upcomingCount = 0

            for (p in pairs) {
                val s = ScheduleStore.timeToMinutes(p.time.substringBefore("-")) ?: continue
                val e = ScheduleStore.timeToMinutes(p.time.substringAfter("-")) ?: (s + 90)

                if (nowMin in s until e) {
                    current = p
                } else if (s > nowMin && next == null) {
                    next = p
                }
                if (s > nowMin) upcomingCount++
            }

            val target = current ?: next

            if (target != null) {
                val subj = target.subgroups.joinToString(" / ") { it.subject }
                val rooms = target.subgroups.joinToString(" / ") { it.room }
                val teachers = target.subgroups.joinToString(" / ") { it.teacher }

                views.setTextViewText(R.id.widget_time, target.time)
                views.setTextViewText(R.id.widget_subject, subj.take(60))
                views.setTextViewText(R.id.widget_room, "Ауд. $rooms · $teachers".take(50))

                val footer = when {
                    current != null -> "Идёт сейчас · всего ${pairs.size} пар"
                    upcomingCount > 0 -> "До пары · всего сегодня ${pairs.size}"
                    else -> "Всего сегодня ${pairs.size}"
                }
                views.setTextViewText(R.id.widget_footer, footer)
            } else {
                views.setTextViewText(R.id.widget_time, "Пары закончились")
                views.setTextViewText(R.id.widget_subject, "Свободен!")
                views.setTextViewText(R.id.widget_room, "")
                views.setTextViewText(R.id.widget_footer, "Сегодня было ${pairs.size} пар")
            }

            return views
        }

        private fun shortDay(day: String): String = when (day) {
            "ПОНЕДЕЛЬНИК" -> "Пн"
            "ВТОРНИК" -> "Вт"
            "СРЕДА" -> "Ср"
            "ЧЕТВЕРГ" -> "Чт"
            "ПЯТНИЦА" -> "Пт"
            "СУББОТА" -> "Сб"
            else -> "Вс"
        }
    }
}
