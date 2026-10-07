package com.example.schedule

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        RescheduleWorker.schedule(this)

        val enabled = ScheduleStore.autoUpdate(this)
        ScheduleUpdater.schedule(this, enabled)
        ScheduleWidget.updateAll(this)

        setContent {
            MaterialTheme(colorScheme = ScheduleDarkScheme) {
                RootApp()
            }
        }
    }
}

@Composable
fun RootApp() {
    val ctx = LocalContext.current
    var showOnboarding by remember {
        mutableStateOf(ScheduleStore.role(ctx).isEmpty())
    }

    if (showOnboarding) {
        OnboardingScreen(onDone = { showOnboarding = false })
    } else {
        App()
    }
}

@Composable
fun App() {
    var tab by remember { mutableStateOf(0) }
    var dragAccum by remember { mutableStateOf(0f) }

    val navColors = NavigationBarItemDefaults.colors(
        selectedIconColor = AppColors.Accent,
        selectedTextColor = AppColors.Accent,
        unselectedIconColor = AppColors.TextDim,
        unselectedTextColor = AppColors.TextDim,
        indicatorColor = AppColors.AccentSoft
    )

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (dragAccum < -120f && tab < 2) tab++
                                else if (dragAccum > 120f && tab > 0) tab--
                                dragAccum = 0f
                            },
                            onDragCancel = { dragAccum = 0f },
                            onHorizontalDrag = { _, dragAmount ->
                                dragAccum += dragAmount
                            }
                        )
                    }
            ) {
                AnimatedContent(
                    targetState = tab,
                    transitionSpec = {
                        val forward = targetState > initialState
                        val offset = if (forward) 1 else -1
                        (slideInHorizontally(animationSpec = tween(220)) { full ->
                            offset * full
                        } + fadeIn(animationSpec = tween(180))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { full ->
                            -offset * full
                        } + fadeOut(animationSpec = tween(180)))
                    },
                    label = "tab"
                ) { target ->
                    when (target) {
                        0 -> TodayScreen()
                        1 -> WeekScreen()
                        else -> SettingsScreen()
                    }
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