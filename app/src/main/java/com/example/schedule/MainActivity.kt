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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        RescheduleWorker.schedule(this)

        val enabled = ScheduleStore.autoUpdate(this)
        ScheduleUpdater.schedule(this, enabled)
        ScheduleWidget.updateAll(this)

        maybeAutoUpdateSchedule()

        setContent {
            MaterialTheme(colorScheme = ScheduleDarkScheme) {
                RootApp()
            }
        }
    }

    private fun maybeAutoUpdateSchedule() {
        val last = ScheduleStore.lastAutoUpdate(this)
        val hasData = ScheduleStore.load(this)?.pairs?.isNotEmpty() == true
        val ageMs = System.currentTimeMillis() - last
        val oneDayMs = 24 * 60 * 60 * 1000L
        if (!hasData || last == 0L || ageMs > oneDayMs) {
            ScheduleUpdater.runNow(this)
        }
    }
}

@Composable
fun RootApp() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var showOnboarding by remember {
        mutableStateOf(ScheduleStore.role(ctx).isEmpty())
    }

    // ─── Состояние диалога обновления ───
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadPercent by remember { mutableStateOf(0) }
    var downloadError by remember { mutableStateOf<String?>(null) }

    // Проверка обновления при старте
    LaunchedEffect(Unit) {
        val update = UpdateChecker.check()
        if (update != null) {
            updateInfo = update
        }
    }

    // ─── Диалог обновления ───
    updateInfo?.let { info ->
        AlertDialog(
            onDismissRequest = {
                if (!isDownloading) updateInfo = null
            },
            title = {
                Text(
                    "🆕 Доступно обновление v${info.version}",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "Текущая версия: ${BuildConfig.VERSION_NAME}",
                        color = AppColors.TextSecondary,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))

                    if (info.notes.isNotBlank()) {
                        Text(
                            "Что нового:",
                            color = AppColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                            Text(
                                info.notes,
                                color = AppColors.TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    if (isDownloading) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Скачиваем… $downloadPercent%",
                            color = AppColors.Accent,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { downloadPercent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = AppColors.Accent,
                            trackColor = AppColors.CardElevated
                        )
                    }

                    downloadError?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = AppColors.Accent, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDownloading) return@Button
                        scope.launch {
                            isDownloading = true
                            downloadError = null
                            downloadPercent = 0
                            val apk = UpdateChecker.downloadApk(ctx, info.downloadUrl) { p ->
                                downloadPercent = p
                            }
                            isDownloading = false
                            if (apk != null) {
                                val ok = UpdateChecker.installApk(ctx, apk)
                                if (!ok) {
                                    downloadError = "Разреши установку из этого источника и нажми ещё раз"
                                } else {
                                    updateInfo = null
                                }
                            } else {
                                downloadError = "Не удалось скачать. Проверь интернет."
                            }
                        }
                    },
                    enabled = !isDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Accent,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(if (isDownloading) "Скачиваем…" else "Обновить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!isDownloading) updateInfo = null },
                    enabled = !isDownloading
                ) {
                    Text("Позже", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Card
        )
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
