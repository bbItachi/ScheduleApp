package com.example.schedule

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object PermissionsHelper {

    // ─── Батарея ───

    fun isBatteryOptimizationIgnored(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun openBatteryOptimizationSettings(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            intent.data = Uri.parse("package:${ctx.packageName}")
            ctx.startActivity(intent)
        } catch (_: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (_: Exception) { }
        }
    }

    // ─── Точные будильники ───

    fun hasExactAlarmPermission(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 31) return true
        return try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
            am.canScheduleExactAlarms()
        } catch (_: Exception) { true }
    }

    fun openExactAlarmSettings(ctx: Context) {
        if (Build.VERSION.SDK_INT < 31) return
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
            intent.data = Uri.parse("package:${ctx.packageName}")
            ctx.startActivity(intent)
        } catch (_: Exception) { }
    }

    // ─── Уведомления ───

    fun openNotificationSettings(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            ctx.startActivity(intent)
        } catch (_: Exception) { }
    }

    // ─── Страница приложения (для автозапуска и др.) ───

    fun openAppSettings(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${ctx.packageName}")
            ctx.startActivity(intent)
        } catch (_: Exception) { }
    }
}
