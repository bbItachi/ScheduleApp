package com.example.schedule

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

object PermissionsHelper {

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

    fun openNotificationSettings(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            intent.putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
            ctx.startActivity(intent)
        } catch (_: Exception) { }
    }

    fun openAppSettings(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${ctx.packageName}")
            ctx.startActivity(intent)
        } catch (_: Exception) { }
    }

    // ─── Не беспокоить ───

    fun hasDndAccess(ctx: Context): Boolean {
        return try {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.isNotificationPolicyAccessGranted
        } catch (_: Exception) { false }
    }

    fun openDndSettings(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        } catch (_: Exception) { }
    }
}
