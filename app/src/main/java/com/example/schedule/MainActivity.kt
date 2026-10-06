package com.example.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        Notifier.scheduleMorning(this)
        Notifier.scheduleBeforePairs(this)
        setContent {
            MaterialTheme(colorScheme = ScheduleDarkScheme) {
                App()
            }
        }
    }
}

@Composable
fun App() {
    var tab by remember { mutableStateOf(0) }

    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = AppColors.Accent,
        selectedTextColor = AppColors.Accent,
        unselectedIconColor = AppColors.TextDim,
        unselectedTextColor = AppColors.TextDim,
        indicatorColor = AppColors.AccentSoft
    )

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                when (tab) {
                    0 -> TodayScreen()
                    1 -> WeekScreen()
                    2 -> SettingsScreen()
                }
            }
            NavigationBar(
                containerColor = AppColors.Card,
                contentColor = AppColors.TextPrimary
            ) {
                NavigationBarItem(
                    selected = tab == 0, onClick = { tab = 0 },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Сегодня") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = tab == 1, onClick = { tab = 1 },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("Неделя") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = tab == 2, onClick = { tab = 2 },
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Настройки") },
                    colors = navColors
                )
            }
        }
    }
}

@Composable
fun EnsureNotificationPermission() {
    val ctx = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
