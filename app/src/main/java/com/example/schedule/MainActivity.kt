package com.example.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Тяжёлые операции — в фоне, чтобы UI стартовал мгновенно
        Notifier.ensureChannels(this)
        RescheduleWorker.schedule(this)
        setContent {
            MaterialTheme(colorScheme = ScheduleDarkScheme) {
                App()
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun App() {
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 3 })

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
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize(),
                    userScrollEnabled = true,
                    beyondViewportPageCount = 0   // не держим соседние экраны в памяти
                ) { page ->
                    when (page) {
                        0 -> TodayScreen()
                        1 -> WeekScreen()
                        2 -> SettingsScreen()
                    }
                }
            }
            NavigationBar(
                containerColor = AppColors.Card,
                contentColor = AppColors.TextPrimary
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Сегодня") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    icon = { Icon(Icons.Default.DateRange, null) },
                    label = { Text("Неделя") },
                    colors = navColors
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == 2,
                    onClick = { scope.launch { pagerState.animateScrollToPage(2) } },
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
