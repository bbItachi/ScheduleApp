package com.example.schedule

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════
//  ОБЩИЕ КОМПОНЕНТЫ
// ═══════════════════════════════════════════════════════════

@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    val bg = if (filled) AppColors.Accent else AppColors.Card
    val fg = if (filled) Color.White else AppColors.Accent
    Box(
        modifier = Modifier.size(44.dp).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = bg,
            shape = CircleShape,
            border = BorderStroke(1.dp, if (filled) AppColors.Accent else AppColors.Border),
            modifier = Modifier.size(42.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private fun parseStartMinutes(time: String): Int? {
    val start = time.substringBefore("-").trim().replace(':', '.')
    val parts = start.split(".")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun parseEndMinutes(time: String): Int? {
    val end = time.substringAfter("-").trim().replace(':', '.')
    val parts = end.split(".")
    if (parts.size < 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun pairStatusText(pairs: List<Lesson>): String? {
    if (pairs.isEmpty()) return null
    val now = Calendar.getInstance()
    val nowMin = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

    for (p in pairs) {
        val s = parseStartMinutes(p.time) ?: continue
        val e = parseEndMinutes(p.time) ?: continue
        if (nowMin in s until e) {
            val left = e - nowMin
            return "идёт ${p.number}-я пара · до конца $left мин"
        }
    }
    for (p in pairs) {
        val s = parseStartMinutes(p.time) ?: continue
        if (s > nowMin) {
            val diff = s - nowMin
            val h = diff / 60
            val m = diff % 60
            val time = if (h > 0) "${h} ч ${m} мин" else "${m} мин"
            return "через $time до ${p.number}-й пары"
        }
    }
    return "пары на сегодня закончились"
}

private fun buildNotesMap(ctx: android.content.Context): Map<String, String> =
    ScheduleStore.notes(ctx).associate { it.key to it.text }

private fun formatLastUpdate(ts: Long): String {
    if (ts <= 0L) return "никогда"
    val now = System.currentTimeMillis()
    val diff = now - ts
    val min = diff / 60_000
    val hour = diff / 3_600_000
    val day = diff / 86_400_000
    return when {
        min < 1 -> "только что"
        min < 60 -> "$min мин назад"
        hour < 24 -> "$hour ч назад"
        day < 7 -> "$day дн назад"
        else -> SimpleDateFormat("d MMMM", Locale("ru")).format(Date(ts))
    }
}

// ═══════════════════════════════════════════════════════════
//  ЧАСЫ
// ═══════════════════════════════════════════════════════════

@Composable
fun DateClockView() {
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val dfDate = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        val dfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        while (true) {
            val now = Date()
            dateText = dfDate.format(now).replaceFirstChar { it.uppercase() }
            timeText = dfTime.format(now)
            kotlinx.coroutines.delay(30_000)
        }
    }

    Column {
        Text(dateText, color = AppColors.TextPrimary, fontSize = 16.sp,
            fontWeight = FontWeight.Bold)
        Text(timeText, color = AppColors.Accent, fontSize = 22.sp,
            fontWeight = FontWeight.Bold)
    }
}

// ═══════════════════════════════════════════════════════════
//  ПЛАШКА «СКОЛЬКО ДО ПАРЫ»
// ═══════════════════════════════════════════════════════════

@Composable
fun PairStatusBar(pairs: List<Lesson>) {
    var statusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pairs) {
        while (true) {
            statusText = pairStatusText(pairs)
            kotlinx.coroutines.delay(60_000)
        }
    }

    val text = statusText ?: return
    Column {
        Spacer(Modifier.height(8.dp))
        Surface(
            color = AppColors.AccentSoft,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, AppColors.Accent),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Schedule, null,
                    tint = AppColors.Accent, modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(text, color = AppColors.TextPrimary, fontSize = 13.sp,
                    fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  СКЕЛЕТОН
// ═══════════════════════════════════════════════════════════

@Composable
fun SkeletonCard() {
    val infinite = rememberInfiniteTransition(label = "shimmer")
    val alpha by infinite.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(14.dp).alpha(alpha)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).background(AppColors.CardElevated, RoundedCornerShape(8.dp)))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.height(14.dp).width(100.dp).background(AppColors.CardElevated, RoundedCornerShape(4.dp)))
            }
            Spacer(Modifier.height(10.dp))
            Box(Modifier.height(16.dp).fillMaxWidth(0.85f).background(AppColors.CardElevated, RoundedCornerShape(4.dp)))
            Spacer(Modifier.height(6.dp))
            Box(Modifier.height(12.dp).fillMaxWidth(0.55f).background(AppColors.CardElevated, RoundedCornerShape(4.dp)))
        }
    }
}

@Composable
fun SkeletonList() {
    Column(Modifier.fillMaxSize()) {
        repeat(4) { SkeletonCard() }
    }
}

// ═══════════════════════════════════════════════════════════
//  ЭКРАН «СЕГОДНЯ»
// ═══════════════════════════════════════════════════════════

@Composable
fun TodayScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    val isTeacher = ScheduleStore.isTeacher(ctx)
    val teacherName = ScheduleStore.teacherName(ctx)

    var pairs by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var week by remember { mutableStateOf(ScheduleStore.currentWeek(ctx)) }
    var status by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf<Set<String>>(ScheduleStore.favorites(ctx)) }
    var skipped by remember { mutableStateOf<Set<String>>(ScheduleStore.skipped(ctx)) }
    var isLoading by remember { mutableStateOf(false) }
    var notesMap by remember { mutableStateOf<Map<String, String>>(buildNotesMap(ctx)) }

    EnsureNotificationPermission()

    LaunchedEffect(Unit) {
        while (true) {
            week = ScheduleStore.currentWeek(ctx)
            kotlinx.coroutines.delay(60_000)
        }
    }

    LaunchedEffect(week) {
        pairs = withContext(Dispatchers.IO) {
            if (isTeacher) {
                ScheduleStore.pairsForTeacher(ctx, ScheduleStore.dayNameRu(), week, teacherName)
            } else {
                ScheduleStore.pairsFor(ctx, ScheduleStore.dayNameRu(), week)
            }
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            status = "Парсим…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { XlsxParser.parse(it) }
                }
                ScheduleStore.save(ctx, parsed)
                pairs = if (isTeacher) {
                    ScheduleStore.pairsForTeacher(ctx, ScheduleStore.dayNameRu(), week, teacherName)
                } else {
                    ScheduleStore.pairsFor(ctx, ScheduleStore.dayNameRu(), week)
                }
                status = "Загружено пар: ${parsed.size}"
                withContext(Dispatchers.IO) {
                    Notifier.scheduleBeforePairs(ctx)
                    Notifier.scheduleEndOfPairs(ctx)
                    Notifier.scheduleDnd(ctx)
                    ScheduleWidget.updateAll(ctx)
                }
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    val dayName = ScheduleStore.dayNameRu()
    val group = ScheduleStore.group(ctx)
    val weekLabel = if (week == WeekType.EVEN) "чётная" else "нечётная"

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {

        Card(
            colors = CardDefaults.cardColors(containerColor = AppColors.Card),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, AppColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.weight(1f)) {
                    DateClockView()
                }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(
                        color = AppColors.AccentSoft,
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            weekLabel,
                            color = AppColors.Accent, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isTeacher) teacherName else group.ifEmpty { "группа не выбрана" },
                        color = AppColors.TextSecondary, fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        PairStatusBar(pairs)

        Spacer(Modifier.height(10.dp))

        Text(
            "$dayName ($weekLabel)",
            color = AppColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> SkeletonList()
                pairs.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.CheckCircle, null,
                            tint = AppColors.Accent,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("Пар нет 🎉", color = AppColors.TextSecondary, fontSize = 16.sp)
                    }
                }
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(
                        items = pairs,
                        key = { "${it.group}|${it.day}|${it.week}|${it.number}" }
                    ) { p ->
                        val key = "${p.group}|${p.day}|${p.week}|${p.number}"
                        PairCard(
                            p = p,
                            isFavorite = key in favorites,
                            isSkipped = key in skipped,
                            note = notesMap[key] ?: "",
                            showGroup = isTeacher,
                            onToggleFavorite = {
                                ScheduleStore.toggleFavorite(ctx, key)
                                favorites = ScheduleStore.favorites(ctx)
                            },
                            onToggleSkipped = {
                                ScheduleStore.toggleSkipped(ctx, key)
                                skipped = ScheduleStore.skipped(ctx)
                                scope.launch(Dispatchers.IO) { Notifier.scheduleBeforePairs(ctx) }
                            },
                            onNoteChanged = { newText ->
                                ScheduleStore.setNote(ctx, key, newText)
                                notesMap = buildNotesMap(ctx)
                            }
                        )
                    }
                }
            }
        }

        Column {
            if (status.isNotEmpty()) {
                Text(
                    status, color = AppColors.Accent, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))

                CircleIconButton(
                    icon = Icons.Default.Refresh,
                    filled = false,
                    onClick = {
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                Notifier.scheduleBeforePairs(ctx)
                                Notifier.scheduleEndOfPairs(ctx)
                                Notifier.scheduleDnd(ctx)
                                ScheduleWidget.updateAll(ctx)
                            }
                            status = "Обновлено"
                        }
                    }
                )
                Spacer(Modifier.width(4.dp))
                CircleIconButton(
                    icon = Icons.Default.Upload,
                    filled = false,
                    onClick = {
                        picker.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel", "*/*"
                        ))
                    }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  ЭКРАН «НЕДЕЛЯ»
// ═══════════════════════════════════════════════════════════

@Composable
fun WeekScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTeacher = ScheduleStore.isTeacher(ctx)
    val teacherName = ScheduleStore.teacherName(ctx)

    var week by remember { mutableStateOf(ScheduleStore.currentWeek(ctx)) }
    var search by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var expandedDays by remember { mutableStateOf<Set<String>>(setOf(ScheduleStore.dayNameRu())) }
    var favorites by remember { mutableStateOf<Set<String>>(ScheduleStore.favorites(ctx)) }
    var skipped by remember { mutableStateOf<Set<String>>(ScheduleStore.skipped(ctx)) }
    var notesMap by remember { mutableStateOf<Map<String, String>>(buildNotesMap(ctx)) }
    var allPairs by remember { mutableStateOf<List<Lesson>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            week = ScheduleStore.currentWeek(ctx)
            kotlinx.coroutines.delay(60_000)
        }
    }

    LaunchedEffect(week) {
        allPairs = withContext(Dispatchers.IO) {
            if (isTeacher) {
                ScheduleStore.allPairsForTeacher(ctx, week, teacherName)
            } else {
                val g = ScheduleStore.group(ctx)
                ScheduleStore.load(ctx)?.pairs
                    ?.filter { it.group == g && it.week == week } ?: emptyList()
            }
        }
    }

    val days = listOf("ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА")

    val filtered = remember(allPairs, search, showOnlyFavorites, favorites) {
        allPairs.filter { p ->
            val matchSearch = search.isBlank() ||
                p.subgroups.any { sg ->
                    sg.subject.contains(search, true) ||
                    sg.teacher.contains(search, true) ||
                    sg.room.contains(search, true) ||
                    (isTeacher && p.group.contains(search, true))
                }
            val matchFav = !showOnlyFavorites ||
                "${p.group}|${p.day}|${p.week}|${p.number}" in favorites
            matchSearch && matchFav
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Неделя",
                color = AppColors.TextPrimary,
                fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(10.dp))
            Surface(
                color = AppColors.AccentSoft,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    if (week == WeekType.EVEN) "чётная" else "нечётная",
                    color = AppColors.Accent, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (isTeacher) teacherName else ScheduleStore.group(ctx).ifEmpty { "группа" },
                color = AppColors.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = showOnlyFavorites,
                onClick = { showOnlyFavorites = !showOnlyFavorites },
                label = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AppColors.Card,
                    selectedContainerColor = AppColors.Accent
                ),
                border = BorderStroke(
                    1.dp,
                    if (showOnlyFavorites) AppColors.Accent else AppColors.Border
                )
            )
        }

        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Поиск: предмет, преподаватель, аудитория") },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.TextDim) },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AppColors.Accent,
                unfocusedBorderColor = AppColors.Border,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary,
                focusedPlaceholderColor = AppColors.TextDim,
                unfocusedPlaceholderColor = AppColors.TextDim,
                cursorColor = AppColors.Accent
            )
        )

        Spacer(Modifier.height(12.dp))

        LazyColumn {
            days.forEach { day ->
                val dayPairs = filtered.filter { it.day == day }.sortedBy { it.number }
                if (dayPairs.isEmpty() && search.isNotBlank()) return@forEach
                item(key = "header_$day") {
                    DayHeader(
                        day = day,
                        count = dayPairs.size,
                        expanded = day in expandedDays,
                        onClick = {
                            expandedDays = if (day in expandedDays)
                                expandedDays - day else expandedDays + day
                        }
                    )
                }
                if (day in expandedDays) {
                    items(
                        items = dayPairs,
                        key = { "${it.group}|${it.day}|${it.week}|${it.number}" }
                    ) { p ->
                        val key = "${p.group}|${p.day}|${p.week}|${p.number}"
                        PairCard(
                            p = p,
                            isFavorite = key in favorites,
                            isSkipped = key in skipped,
                            note = notesMap[key] ?: "",
                            showGroup = isTeacher,
                            onToggleFavorite = {
                                ScheduleStore.toggleFavorite(ctx, key)
                                favorites = ScheduleStore.favorites(ctx)
                            },
                            onToggleSkipped = {
                                ScheduleStore.toggleSkipped(ctx, key)
                                skipped = ScheduleStore.skipped(ctx)
                                scope.launch(Dispatchers.IO) { Notifier.scheduleBeforePairs(ctx) }
                            },
                            onNoteChanged = { newText ->
                                ScheduleStore.setNote(ctx, key, newText)
                                notesMap = buildNotesMap(ctx)
                            }
                        )
                    }
                    if (dayPairs.isEmpty()) {
                        item(key = "empty_$day") {
                            Text("Пар нет", color = AppColors.TextDim, fontSize = 14.sp,
                                modifier = Modifier.padding(start = 16.dp, bottom = 8.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayHeader(day: String, count: Int, expanded: Boolean, onClick: () -> Unit) {
    Surface(
        color = AppColors.Card,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() }
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                if (expanded) Icons.Default.KeyboardArrowDown
                else Icons.Default.KeyboardArrowRight,
                null, tint = AppColors.Accent
            )
            Spacer(Modifier.width(10.dp))
            Text(day, color = AppColors.TextPrimary, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Surface(color = AppColors.AccentSoft, shape = RoundedCornerShape(20.dp)) {
                Text("$count", color = AppColors.Accent, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  ЭКРАН «НАСТРОЙКИ»
// ═══════════════════════════════════════════════════════════

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val isTeacher = ScheduleStore.isTeacher(ctx)

    var group by remember { mutableStateOf(ScheduleStore.group(ctx)) }
    var groups by remember { mutableStateOf<List<String>>(ScheduleStore.allGroups(ctx)) }
    var teacherName by remember { mutableStateOf(ScheduleStore.teacherName(ctx)) }
    var morningH by remember { mutableStateOf(ScheduleStore.morningHour(ctx)) }
    var morningM by remember { mutableStateOf(ScheduleStore.morningMinute(ctx)) }
    var beforeMin by remember { mutableStateOf(ScheduleStore.beforeMinutes(ctx)) }
    var dndEnabled by remember { mutableStateOf(ScheduleStore.dndEnabled(ctx)) }
    var notifyEnd by remember { mutableStateOf(ScheduleStore.notifyEndOfPair(ctx)) }
    var autoUpdate by remember { mutableStateOf(ScheduleStore.autoUpdate(ctx)) }
    var lastUpdate by remember { mutableStateOf(ScheduleStore.lastAutoUpdate(ctx)) }
    var themeMode by remember { mutableStateOf(ScheduleStore.themeMode(ctx)) }
    var updateStatus by remember { mutableStateOf("") }
    var isUpdating by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showTeacherDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var teacherInput by remember { mutableStateOf(teacherName) }
    var debugMode by remember { mutableStateOf(false) }
    var totalPairs by remember { mutableStateOf(0) }

    var batteryOk by remember { mutableStateOf(PermissionsHelper.isBatteryOptimizationIgnored(ctx)) }
    var exactAlarmOk by remember { mutableStateOf(PermissionsHelper.hasExactAlarmPermission(ctx)) }
    var dndAccessOk by remember { mutableStateOf(PermissionsHelper.hasDndAccess(ctx)) }

    LaunchedEffect(Unit) {
        groups = ScheduleStore.allGroups(ctx)
        group = ScheduleStore.group(ctx)
        totalPairs = ScheduleStore.load(ctx)?.pairs?.size ?: 0
        batteryOk = PermissionsHelper.isBatteryOptimizationIgnored(ctx)
        exactAlarmOk = PermissionsHelper.hasExactAlarmPermission(ctx)
        dndAccessOk = PermissionsHelper.hasDndAccess(ctx)
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Выбор группы", color = AppColors.TextPrimary) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(groups, key = { it }) { g ->
                        TextButton(
                            onClick = {
                                ScheduleStore.setGroup(ctx, g)
                                group = g
                                showGroupDialog = false
                                scope.launch(Dispatchers.IO) {
                                    Notifier.scheduleBeforePairs(ctx)
                                    Notifier.scheduleEndOfPairs(ctx)
                                    Notifier.scheduleDnd(ctx)
                                    ScheduleWidget.updateAll(ctx)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                g,
                                color = if (g == group) AppColors.Accent else AppColors.TextPrimary
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showGroupDialog = false }) {
                    Text("Закрыть", color = AppColors.Accent)
                }
            },
            containerColor = AppColors.Card
        )
    }

    if (showTeacherDialog) {
        AlertDialog(
            onDismissRequest = { showTeacherDialog = false },
            title = { Text("ФИО преподавателя", color = AppColors.TextPrimary) },
            text = {
                OutlinedTextField(
                    value = teacherInput,
                    onValueChange = { teacherInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Лизнева К.А.") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Accent,
                        unfocusedBorderColor = AppColors.Border,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedPlaceholderColor = AppColors.TextDim,
                        unfocusedPlaceholderColor = AppColors.TextDim,
                        cursorColor = AppColors.Accent
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    ScheduleStore.setTeacherName(ctx, teacherInput.trim())
                    teacherName = teacherInput.trim()
                    showTeacherDialog = false
                    scope.launch(Dispatchers.IO) {
                        Notifier.scheduleBeforePairs(ctx)
                        ScheduleWidget.updateAll(ctx)
                    }
                }) { Text("Сохранить", color = AppColors.Accent) }
            },
            dismissButton = {
                TextButton(onClick = { showTeacherDialog = false }) {
                    Text("Отмена", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Card
        )
    }

    // ─── Диалог выбора темы ───
    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            title = { Text("Тема оформления", color = AppColors.TextPrimary) },
            text = {
                Column {
                    ThemeOption(
                        title = "Авто",
                        subtitle = "Светлая с 8:00 до 20:00, иначе тёмная",
                        selected = themeMode == ScheduleStore.THEME_AUTO
                    ) {
                        themeMode = ScheduleStore.THEME_AUTO
                        ScheduleStore.setThemeMode(ctx, ScheduleStore.THEME_AUTO)
                        showThemeDialog = false
                    }
                    ThemeOption(
                        title = "Светлая",
                        subtitle = "Всегда светлая тема",
                        selected = themeMode == ScheduleStore.THEME_LIGHT
                    ) {
                        themeMode = ScheduleStore.THEME_LIGHT
                        ScheduleStore.setThemeMode(ctx, ScheduleStore.THEME_LIGHT)
                        showThemeDialog = false
                    }
                    ThemeOption(
                        title = "Тёмная",
                        subtitle = "Всегда тёмная тема",
                        selected = themeMode == ScheduleStore.THEME_DARK
                    ) {
                        themeMode = ScheduleStore.THEME_DARK
                        ScheduleStore.setThemeMode(ctx, ScheduleStore.THEME_DARK)
                        showThemeDialog = false
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showThemeDialog = false }) {
                    Text("Закрыть", color = AppColors.TextSecondary)
                }
            },
            containerColor = AppColors.Card
        )
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Настройки", color = AppColors.TextPrimary,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxSize()) {

            item(key = "sec_main") { SectionTitle("Основное") }
            if (isTeacher) {
                item(key = "teacher_name") {
                    SettingRow("Моё ФИО", teacherName.ifEmpty { "не указано" }) {
                        teacherInput = teacherName
                        showTeacherDialog = true
                    }
                }
            } else {
                item(key = "group") {
                    SettingRow("Группа", group.ifEmpty { "не выбрана" }) { showGroupDialog = true }
                }
            }

            // ─── Тема ───
            item(key = "theme") {
                SettingRow("Тема", themeLabel(themeMode)) { showThemeDialog = true }
            }

            item(key = "mh") {
                SettingRow("Утренняя сводка (часы)", "%02d".format(morningH)) {
                    morningH = (morningH + 1) % 24
                    ScheduleStore.setMorning(ctx, morningH, morningM)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleMorning(ctx) }
                }
            }
            item(key = "mm") {
                SettingRow("Утренняя сводка (минуты)", "%02d".format(morningM)) {
                    morningM = (morningM + 5) % 60
                    ScheduleStore.setMorning(ctx, morningH, morningM)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleMorning(ctx) }
                }
            }
            item(key = "bm") {
                SettingRow("Напоминание до пары", "$beforeMin мин") {
                    beforeMin = when (beforeMin) {
                        5 -> 10; 10 -> 15; 15 -> 20; 20 -> 30; else -> 5
                    }
                    ScheduleStore.setBeforeMinutes(ctx, beforeMin)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleBeforePairs(ctx) }
                }
            }
            item(key = "notify_end") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Уведомлять о конце пары", color = AppColors.TextPrimary, fontSize = 16.sp)
                        Text("Присылать уведомление, когда пара закончилась",
                            color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = notifyEnd,
                        onCheckedChange = {
                            notifyEnd = it
                            ScheduleStore.setNotifyEndOfPair(ctx, it)
                            scope.launch(Dispatchers.IO) { Notifier.scheduleEndOfPairs(ctx) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.Accent,
                            uncheckedThumbColor = AppColors.TextDim,
                            uncheckedTrackColor = AppColors.CardElevated,
                            uncheckedBorderColor = AppColors.Border
                        )
                    )
                }
                Divider(color = AppColors.Divider)
            }

            item(key = "sec_auto") {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Автообновление с сайта колледжа")
            }
            item(key = "auto_switch") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Обновлять автоматически", color = AppColors.TextPrimary, fontSize = 16.sp)
                        Text("Раз в сутки с сайта споспк.рф",
                            color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = autoUpdate,
                        onCheckedChange = {
                            autoUpdate = it
                            ScheduleStore.setAutoUpdate(ctx, it)
                            ScheduleUpdater.schedule(ctx, it)
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.Accent,
                            uncheckedThumbColor = AppColors.TextDim,
                            uncheckedTrackColor = AppColors.CardElevated,
                            uncheckedBorderColor = AppColors.Border
                        )
                    )
                }
                Divider(color = AppColors.Divider)
            }
            item(key = "auto_last") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Refresh, null,
                        tint = AppColors.TextDim, modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Последнее обновление", color = AppColors.TextPrimary, fontSize = 15.sp)
                        Text(formatLastUpdate(lastUpdate),
                            color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                }
                Divider(color = AppColors.Divider)
            }
            item(key = "auto_now") {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        isUpdating = true
                        updateStatus = "Скачиваем…"
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    ScheduleUpdater.runNow(ctx)
                                }
                                kotlinx.coroutines.delay(3000)
                                lastUpdate = ScheduleStore.lastAutoUpdate(ctx)
                                totalPairs = ScheduleStore.load(ctx)?.pairs?.size ?: 0
                                updateStatus = "Готово · пар: $totalPairs"
                            } catch (e: Exception) {
                                updateStatus = "Ошибка: ${e.message}"
                            } finally {
                                isUpdating = false
                            }
                        }
                    },
                    enabled = !isUpdating,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isUpdating) "Обновляем…" else "Обновить сейчас")
                }
                if (updateStatus.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(updateStatus, color = AppColors.Accent, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "sec_dnd") {
                SectionTitle("Не беспокоить")
            }
            item(key = "dnd_switch") {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Авто-тишина на паре", color = AppColors.TextPrimary, fontSize = 16.sp)
                        Text("Включать «Не беспокоить» когда идёт пара",
                            color = AppColors.TextSecondary, fontSize = 12.sp)
                    }
                    Switch(
                        checked = dndEnabled,
                        onCheckedChange = {
                            dndEnabled = it
                            ScheduleStore.setDndEnabled(ctx, it)
                            scope.launch(Dispatchers.IO) { Notifier.scheduleDnd(ctx) }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = AppColors.Accent,
                            uncheckedThumbColor = AppColors.TextDim,
                            uncheckedTrackColor = AppColors.CardElevated,
                            uncheckedBorderColor = AppColors.Border
                        )
                    )
                }
                Divider(color = AppColors.Divider)
            }
            item(key = "dnd_access") {
                PermissionRow(
                    title = "Доступ к «Не беспокоить»",
                    subtitle = if (dndAccessOk) "Разрешён" else "Нужно разрешить для авто-тишины",
                    ok = dndAccessOk,
                    onClick = { PermissionsHelper.openDndSettings(ctx) }
                )
            }

            item(key = "sec_perm") {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Разрешения и фоновая работа")
            }
            item(key = "p1") {
                PermissionRow("Уведомления", "Чтобы приходили сводки и напоминания",
                    ok = true) { PermissionsHelper.openNotificationSettings(ctx) }
            }
            item(key = "p2") {
                PermissionRow("Точные будильники",
                    if (exactAlarmOk) "Разрешены" else "Разрешите для точного времени",
                    ok = exactAlarmOk) { PermissionsHelper.openExactAlarmSettings(ctx) }
            }
            item(key = "p3") {
                PermissionRow("Батарея без ограничений",
                    if (batteryOk) "Система не будет усыплять" else "Отключите оптимизацию батареи",
                    ok = batteryOk) { PermissionsHelper.openBatteryOptimizationSettings(ctx) }
            }
            item(key = "p4") {
                PermissionRow("Автозапуск",
                    "Включите в настройках приложения (MIUI/Huawei)",
                    ok = null) { PermissionsHelper.openAppSettings(ctx) }
            }
            item(key = "p5") {
                PermissionRow("Закрепить в недавних",
                    "Откройте многозадачность и закрепите приложение",
                    ok = null) { PermissionsHelper.openAppSettings(ctx) }
            }
            item(key = "btn_resched") {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            Notifier.scheduleMorning(ctx)
                            Notifier.scheduleBeforePairs(ctx)
                            Notifier.scheduleEndOfPairs(ctx)
                            Notifier.scheduleDnd(ctx)
                            RescheduleWorker.schedule(ctx)
                            ScheduleWidget.updateAll(ctx)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.AccentDark,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🔔 Перепланировать уведомления") }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "version") {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    val start = System.currentTimeMillis()
                                    tryAwaitRelease()
                                    if (System.currentTimeMillis() - start >= 5000L) {
                                        debugMode = !debugMode
                                    }
                                }
                            )
                        }
                ) {
                    Text(
                        "Версия 1.10" + if (debugMode) "  🐛 DEBUG" else "",
                        color = if (debugMode) AppColors.Accent else AppColors.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            }
            if (debugMode) {
                item(key = "debug") {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.Accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text("🐛 Отладочная информация", color = AppColors.Accent,
                                fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            DebugLine("Роль", ScheduleStore.role(ctx))
                            DebugLine("Всего пар в базе", "$totalPairs")
                            DebugLine("Групп", "${groups.size}")
                            DebugLine("Текущая неделя",
                                if (ScheduleStore.currentWeek(ctx) == WeekType.EVEN) "Чётная" else "Нечётная")
                            DebugLine("Тема", themeLabel(themeMode))
                            DebugLine("Напоминание за", "$beforeMin мин")
                            DebugLine("Увед. о конце", if (notifyEnd) "вкл" else "выкл")
                            DebugLine("DND авто-режим", if (dndEnabled) "включен" else "выключен")
                            DebugLine("Автообновление", if (autoUpdate) "включено" else "выключено")
                            DebugLine("Последнее обновление", formatLastUpdate(lastUpdate))
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    ScheduleStore.clear(ctx)
                                    totalPairs = 0
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.AccentDark,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("🗑 Сбросить расписание") }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
fun ThemeOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppColors.Accent,
                unselectedColor = AppColors.TextDim
            )
        )
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (selected) AppColors.Accent else AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 12.sp)
        }
    }
}

private fun themeLabel(mode: String): String = when (mode) {
    ScheduleStore.THEME_LIGHT -> "Светлая"
    ScheduleStore.THEME_DARK -> "Тёмная"
    else -> "Авто"
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        color = AppColors.TextDim,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun PermissionRow(title: String, subtitle: String, ok: Boolean?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val (icon, tint) = when (ok) {
            true -> Icons.Default.CheckCircle to AppColors.Accent
            false -> Icons.Default.Warning to AppColors.Accent
            null -> Icons.Default.Info to AppColors.TextDim
        }
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.TextPrimary, fontSize = 15.sp)
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 12.sp)
        }
        Icon(Icons.Default.KeyboardArrowRight, null, tint = AppColors.TextDim)
    }
    Divider(color = AppColors.Divider)
}

@Composable
fun DebugLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp,
            modifier = Modifier.weight(1f))
        Text(value, color = AppColors.TextPrimary, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Surface(
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            Modifier.padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = AppColors.TextPrimary, fontSize = 16.sp,
                modifier = Modifier.weight(1f))
            Text(value, color = AppColors.Accent, fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, null, tint = AppColors.TextDim)
        }
    }
    Divider(color = AppColors.Divider)
}

// ═══════════════════════════════════════════════════════════
//  КАРТОЧКА ПАРЫ
// ═══════════════════════════════════════════════════════════

@Composable
fun PairCard(
    p: Lesson,
    isFavorite: Boolean,
    isSkipped: Boolean,
    note: String,
    showGroup: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleSkipped: () -> Unit,
    onNoteChanged: (String) -> Unit
) {
    var noteText by remember(p) { mutableStateOf(note) }
    var editing by remember(p) { mutableStateOf(false) }

    val contentAlpha = if (isSkipped) 0.5f else 1f

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (isSkipped) AppColors.TextDim else AppColors.Border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(14.dp).alpha(contentAlpha)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = if (isSkipped) AppColors.CardElevated else AppColors.AccentSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        p.number.toString(),
                        color = if (isSkipped) AppColors.TextDim else AppColors.Accent,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    p.time, color = AppColors.Accent, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleSkipped) {
                    Icon(
                        if (isSkipped) Icons.Default.PlayArrow else Icons.Default.Close, null,
                        tint = if (isSkipped) AppColors.Accent else AppColors.TextDim
                    )
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null,
                        tint = if (isFavorite) AppColors.Accent else AppColors.TextDim
                    )
                }
                IconButton(onClick = { editing = !editing }) {
                    Icon(Icons.Default.Edit, null, tint = AppColors.TextDim)
                }
            }

            Spacer(Modifier.height(6.dp))

            p.subgroups.forEach { sg ->
                Column(Modifier.padding(top = 4.dp)) {
                    val prefix = if (p.subgroups.size > 1) "Подгр. ${sg.index}: " else ""
                    Text(
                        prefix + sg.subject,
                        color = AppColors.TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isSkipped) TextDecoration.LineThrough else TextDecoration.None
                    )
                    Spacer(Modifier.height(4.dp))

                    // ─── Аудитория или Онлайн ───
                    val isOnline = sg.room.equals("Онлайн", ignoreCase = true)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isOnline) {
                            Text("🌐 ", fontSize = 12.sp)
                            Text(
                                "Онлайн",
                                color = AppColors.Accent, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else {
                            Text("🏛 ", fontSize = 12.sp)
                            Text(
                                "Ауд. ${sg.room.ifEmpty { "—" }}",
                                color = AppColors.Accent, fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (showGroup) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👥 ", fontSize = 12.sp)
                            Text(
                                "Группа ${p.group}",
                                color = AppColors.TextPrimary, fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    } else {
                        if (sg.teacher.isNotBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("👤 ", fontSize = 12.sp)
                                Text(
                                    sg.teacher,
                                    color = AppColors.TextSecondary, fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }

            if (isSkipped) {
                Spacer(Modifier.height(6.dp))
                Text("⏭ Пропущена", color = AppColors.TextDim, fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold)
            }

            if (editing) {
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Заметка к паре…") },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AppColors.Accent,
                        unfocusedBorderColor = AppColors.Border,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary,
                        focusedPlaceholderColor = AppColors.TextDim,
                        unfocusedPlaceholderColor = AppColors.TextDim,
                        cursorColor = AppColors.Accent
                    )
                )
                Spacer(Modifier.height(6.dp))
                Row {
                    TextButton(onClick = {
                        onNoteChanged(noteText)
                        editing = false
                    }) { Text("Сохранить", color = AppColors.Accent) }
                    TextButton(onClick = { editing = false }) {
                        Text("Отмена", color = AppColors.TextSecondary)
                    }
                }
            } else if (noteText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(color = AppColors.AccentSoft, shape = RoundedCornerShape(8.dp)) {
                    Text("📝 $noteText", color = AppColors.TextPrimary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}
