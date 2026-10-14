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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

private const val CHANNEL_URL = "https://max.ru/channel_rasp_spk"
private const val APK_URL = "https://github.com/bbItachi/ScheduleApp/releases/latest"

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

private fun pairStatusText(pairs: List<Lesson>): String? {
    if (pairs.isEmpty()) return null
    val cal = InternetTime.calendar()
    val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)

    for (p in pairs) {
        val s = ScheduleStore.timeToMinutes(p.time.substringBefore("-")) ?: continue
        val e = ScheduleStore.timeToMinutes(p.time.substringAfter("-")) ?: (s + 90)
        if (nowMin in s until e) {
            return "идёт ${p.number}-я пара · до конца ${e - nowMin} мин"
        }
    }
    for (p in pairs) {
        val s = ScheduleStore.timeToMinutes(p.time.substringBefore("-")) ?: continue
        if (s > nowMin) {
            val diff = s - nowMin
            val h = diff / 60
            val m = diff % 60
            val t = if (h > 0) "${h} ч ${m} мин" else "${m} мин"
            return "через $t до ${p.number}-й пары"
        }
    }
    return "пары на сегодня закончились"
}

private fun buildNotesMap(ctx: android.content.Context): Map<String, String> =
    ScheduleStore.notes(ctx).associate { it.key to it.text }

private fun formatLastUpdate(ts: Long): String {
    if (ts <= 0L) return "никогда"
    val diff = System.currentTimeMillis() - ts
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
//  ЧАСЫ (интернет-время)
// ═══════════════════════════════════════════════════════════

@Composable
fun DateClockView() {
    var dateText by remember { mutableStateOf("") }
    var timeText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val dfDate = SimpleDateFormat("EEEE, d MMMM", Locale("ru"))
        val dfTime = SimpleDateFormat("HH:mm", Locale.getDefault())
        dfDate.timeZone = InternetTime.TOMSK_TZ
        dfTime.timeZone = InternetTime.TOMSK_TZ
        while (true) {
            val now = InternetTime.now()
            dateText = dfDate.format(now).replaceFirstChar { it.uppercase() }
            timeText = dfTime.format(now)
            kotlinx.coroutines.delay(30_000)
        }
    }

    Column {
        Text(dateText, color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(timeText, color = AppColors.Accent, fontSize = 22.sp, fontWeight = FontWeight.Bold)
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
                Icon(Icons.Default.Schedule, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(text, color = AppColors.TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
        initialValue = 0.4f, targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ), label = "alpha"
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

    // Ключ: если вечер — показываем завтра. Пересчитываем каждые 60 сек.
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(60_000)
            tick++
        }
    }

    val showingTomorrow = remember(tick) { InternetTime.isDisplayingTomorrow() }
    val dayName = remember(tick) { InternetTime.displayDayNameRu() }
    val week = remember(tick) { ScheduleStore.displayWeek(ctx) }

    var pairs by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var favorites by remember { mutableStateOf<Set<String>>(ScheduleStore.favorites(ctx)) }
    var skipped by remember { mutableStateOf<Set<String>>(ScheduleStore.skipped(ctx)) }
    var isLoading by remember { mutableStateOf(false) }
    var notesMap by remember { mutableStateOf<Map<String, String>>(buildNotesMap(ctx)) }
    var teacherDialog by remember { mutableStateOf<String?>(null) }

    EnsureNotificationPermission()

    LaunchedEffect(dayName, week) {
        pairs = withContext(Dispatchers.IO) {
            if (isTeacher) ScheduleStore.pairsForTeacher(ctx, dayName, week, teacherName)
            else ScheduleStore.pairsForDay(ctx, dayName, week, ScheduleStore.group(ctx))
        }
    }

    teacherDialog?.let { TeacherDialog(it) { teacherDialog = null } }

    // Выбор файла (ручная загрузка)
    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isLoading = true
            status = "Загружаем файл…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { XlsxParser.parse(it) }
                }
                ScheduleStore.save(ctx, parsed)
                ScheduleStore.setLastAutoUpdate(ctx, System.currentTimeMillis())
                pairs = if (isTeacher) ScheduleStore.pairsForTeacher(ctx, dayName, week, teacherName)
                        else ScheduleStore.pairsForDay(ctx, dayName, week, ScheduleStore.group(ctx))
                status = "Загружено пар: ${parsed.size}"
                withContext(Dispatchers.IO) {
                    Notifier.scheduleBeforePairs(ctx)
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

    fun downloadFromSite() {
        if (isLoading) return
        scope.launch {
            isLoading = true
            status = "Скачиваем расписание…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val url = java.net.URL("http://xn--j1ahcbhc.xn--p1ai/rasp.xlsx")
                    val conn = (url.openConnection() as java.net.HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 20_000
                        readTimeout = 45_000
                        setRequestProperty("User-Agent", "ScheduleApp/1.12")
                        instanceFollowRedirects = true
                    }
                    val r = conn.inputStream.use { XlsxParser.parse(it) }
                    conn.disconnect()
                    r
                }
                if (parsed.isEmpty()) {
                    status = "Файл пустой"
                } else {
                    ScheduleStore.save(ctx, parsed)
                    ScheduleStore.setLastAutoUpdate(ctx, System.currentTimeMillis())
                    pairs = if (isTeacher) ScheduleStore.pairsForTeacher(ctx, dayName, week, teacherName)
                            else ScheduleStore.pairsForDay(ctx, dayName, week, ScheduleStore.group(ctx))
                    status = "Обновлено пар: ${parsed.size}"
                    withContext(Dispatchers.IO) {
                        Notifier.scheduleBeforePairs(ctx)
                        Notifier.scheduleDnd(ctx)
                        ScheduleWidget.updateAll(ctx)
                    }
                }
            } catch (e: Exception) {
                status = "Не удалось скачать: ${e.message ?: "ошибка сети"}"
            } finally {
                isLoading = false
            }
        }
    }

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
                Box(Modifier.weight(1f)) { DateClockView() }
                Column(horizontalAlignment = Alignment.End) {
                    Surface(color = AppColors.AccentSoft, shape = RoundedCornerShape(20.dp)) {
                        Text(weekLabel, color = AppColors.Accent, fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (isTeacher) teacherName else group.ifEmpty { "группа не выбрана" },
                        color = AppColors.TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Плашка «завтра», если переключились на завтра
        if (showingTomorrow) {
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
                    Icon(Icons.Default.Info, null, tint = AppColors.Accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Показано расписание на завтра", color = AppColors.TextPrimary,
                        fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            PairStatusBar(pairs)
        }

        Spacer(Modifier.height(10.dp))

        Text(
            "$dayName ($weekLabel)",
            color = AppColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                isLoading -> SkeletonList()
                pairs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.CheckCircle, null, tint = AppColors.Accent, modifier = Modifier.size(48.dp))
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
                            },
                            onTeacherClick = { teacherDialog = it }
                        )
                    }
                }
            }
        }

        Column {
            if (status.isNotEmpty()) {
                Text(status, color = AppColors.Accent, fontSize = 12.sp,
                    modifier = Modifier.padding(bottom = 6.dp))
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                CircleIconButton(icon = Icons.Default.Refresh, filled = false,
                    onClick = { downloadFromSite() })
                Spacer(Modifier.width(8.dp))
                CircleIconButton(icon = Icons.Default.Upload, filled = false,
                    onClick = {
                        filePicker.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel", "*/*"
                        ))
                    })
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  ДИАЛОГ ПРЕПОДАВАТЕЛЯ (только текущая неделя)
// ═══════════════════════════════════════════════════════════

@Composable
fun TeacherDialog(teacher: String, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val lessons = remember(teacher) { ScheduleStore.lessonsByTeacherCurrentWeek(ctx, teacher) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(teacher, color = AppColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text("Пары на текущую неделю", color = AppColors.TextSecondary, fontSize = 12.sp)
            }
        },
        text = {
            if (lessons.isEmpty()) {
                Text("На этой неделе пар нет", color = AppColors.TextSecondary)
            } else {
                LazyColumn(Modifier.heightIn(max = 500.dp)) {
                    items(
                        items = lessons,
                        key = { "${it.day}|${it.week}|${it.number}|${it.group}" }
                    ) { l ->
                        Column(Modifier.padding(vertical = 6.dp)) {
                            Text(
                                "${l.day} · ${l.number} пара · ${l.time}",
                                color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                            )
                            val subj = l.subgroups
                                .firstOrNull { it.teacher.contains(teacher, true) }
                                ?.subject
                                ?: l.subgroups.firstOrNull()?.subject ?: ""
                            Text(subj, color = AppColors.TextPrimary, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium)
                            val room = l.subgroups.firstOrNull()?.room ?: "—"
                            Text("Ауд. $room · гр. ${l.group}",
                                color = AppColors.TextSecondary, fontSize = 12.sp)
                        }
                        Divider(color = AppColors.Divider)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = AppColors.Accent)
            }
        },
        containerColor = AppColors.Card
    )
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
    var expandedDays by remember { mutableStateOf<Set<String>>(setOf(InternetTime.displayDayNameRu())) }
    var favorites by remember { mutableStateOf<Set<String>>(ScheduleStore.favorites(ctx)) }
    var skipped by remember { mutableStateOf<Set<String>>(ScheduleStore.skipped(ctx)) }
    var notesMap by remember { mutableStateOf<Map<String, String>>(buildNotesMap(ctx)) }
    var allPairs by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var teacherDialog by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(week) {
        allPairs = withContext(Dispatchers.IO) {
            if (isTeacher) ScheduleStore.allPairsForTeacher(ctx, week, teacherName)
            else {
                val g = ScheduleStore.group(ctx)
                ScheduleStore.load(ctx)?.pairs
                    ?.filter { it.group == g && it.week == week } ?: emptyList()
            }
        }
    }

    teacherDialog?.let { TeacherDialog(it) { teacherDialog = null } }

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
            Text("Неделя", color = AppColors.TextPrimary,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Surface(color = AppColors.AccentSoft, shape = RoundedCornerShape(20.dp)) {
                Text(if (week == WeekType.EVEN) "чётная" else "нечётная",
                    color = AppColors.Accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp))
            }
        }
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (isTeacher) teacherName else ScheduleStore.group(ctx).ifEmpty { "группа" },
                color = AppColors.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.weight(1f))
            FilterChip(
                selected = showOnlyFavorites,
                onClick = { showOnlyFavorites = !showOnlyFavorites },
                label = { Icon(Icons.Default.Star, null, Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AppColors.Card,
                    selectedContainerColor = AppColors.Accent
                ),
                border = BorderStroke(1.dp, if (showOnlyFavorites) AppColors.Accent else AppColors.Border)
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
                            expandedDays = if (day in expandedDays) expandedDays - day
                            else expandedDays + day
                        }
                    )
                }
                if (day in expandedDays) {
                    items(dayPairs, key = { "${it.group}|${it.day}|${it.week}|${it.number}" }) { p ->
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
                            },
                            onTeacherClick = { teacherDialog = it }
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
//  БЫСТРЫЕ НАСТРОЙКИ (после онбординга)
// ═══════════════════════════════════════════════════════════

@Composable
fun QuickSettingsDialog(onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var morningH by remember { mutableStateOf(ScheduleStore.morningHour(ctx)) }
    var morningM by remember { mutableStateOf(ScheduleStore.morningMinute(ctx)) }
    var beforeMin by remember { mutableStateOf(ScheduleStore.beforeMinutes(ctx)) }
    var dndEnabled by remember { mutableStateOf(ScheduleStore.dndEnabled(ctx)) }
    var notifyEnd by remember { mutableStateOf(ScheduleStore.notifyEndOfPair(ctx)) }
    var themeMode by remember { mutableStateOf(ScheduleStore.themeMode(ctx)) }

    var batteryOk by remember { mutableStateOf(PermissionsHelper.isBatteryOptimizationIgnored(ctx)) }
    var exactAlarmOk by remember { mutableStateOf(PermissionsHelper.hasExactAlarmPermission(ctx)) }
    var dndAccessOk by remember { mutableStateOf(PermissionsHelper.hasDndAccess(ctx)) }

    AlertDialog(
        onDismissRequest = onClose,
        title = {
            Text("Настройки", color = AppColors.TextPrimary,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState())) {

                // Заголовок
                Surface(
                    color = AppColors.AccentSoft,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "Эти настройки настройте под себя",
                        color = AppColors.Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }

                Spacer(Modifier.height(14.dp))

                SectionTitle("Основное")

                QuickSettingRow("Утренняя сводка (часы)", "%02d".format(morningH)) {
                    morningH = (morningH + 1) % 24
                    ScheduleStore.setMorning(ctx, morningH, morningM)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleMorning(ctx) }
                }
                QuickSettingRow("Утренняя сводка (минуты)", "%02d".format(morningM)) {
                    morningM = (morningM + 5) % 60
                    ScheduleStore.setMorning(ctx, morningH, morningM)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleMorning(ctx) }
                }
                QuickSettingRow("Напоминание до пары", "$beforeMin мин") {
                    beforeMin = when (beforeMin) {
                        5 -> 10; 10 -> 15; 15 -> 20; 20 -> 30; else -> 5
                    }
                    ScheduleStore.setBeforeMinutes(ctx, beforeMin)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleBeforePairs(ctx) }
                }
                QuickSettingRow(
                    "Тема",
                    when (themeMode) {
                        ScheduleStore.THEME_LIGHT -> "Светлая"
                        ScheduleStore.THEME_DARK -> "Тёмная"
                        else -> "Авто"
                    }
                ) {
                    themeMode = when (themeMode) {
                        ScheduleStore.THEME_AUTO -> ScheduleStore.THEME_LIGHT
                        ScheduleStore.THEME_LIGHT -> ScheduleStore.THEME_DARK
                        else -> ScheduleStore.THEME_AUTO
                    }
                    ScheduleStore.setThemeMode(ctx, themeMode)
                }

                Spacer(Modifier.height(14.dp))
                SectionTitle("Уведомления")

                QuickSwitchRow("Авто-тишина на паре", dndEnabled) {
                    dndEnabled = it
                    ScheduleStore.setDndEnabled(ctx, it)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleDnd(ctx) }
                }
                QuickSwitchRow("Уведомление о конце пары", notifyEnd) {
                    notifyEnd = it
                    ScheduleStore.setNotifyEndOfPair(ctx, it)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleBeforePairs(ctx) }
                }

                Spacer(Modifier.height(14.dp))

                // Разрешения — выделенный блок
                Surface(
                    color = AppColors.AccentSoft,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, AppColors.Accent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            "Рекомендуем добавить разрешение для качественной работы приложения",
                            color = AppColors.Accent,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        PermissionShortRow("Уведомления", true) {
                            PermissionsHelper.openNotificationSettings(ctx)
                        }
                        PermissionShortRow("Точные будильники", exactAlarmOk) {
                            PermissionsHelper.openExactAlarmSettings(ctx)
                        }
                        PermissionShortRow("Батарея без ограничений", batteryOk) {
                            PermissionsHelper.openBatteryOptimizationSettings(ctx)
                        }
                        PermissionShortRow("Доступ к «Не беспокоить»", dndAccessOk) {
                            PermissionsHelper.openDndSettings(ctx)
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = onClose,
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) { Text("Готово") }
        },
        containerColor = AppColors.Card
    )
}

@Composable
private fun QuickSettingRow(title: String, value: String, onClick: () -> Unit) {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppColors.TextPrimary, fontSize = 15.sp,
                modifier = Modifier.weight(1f))
            Text(value, color = AppColors.Accent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, null, tint = AppColors.TextDim)
        }
    }
    Divider(color = AppColors.Divider)
}

@Composable
private fun QuickSwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = AppColors.TextPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
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

@Composable
private fun PermissionShortRow(title: String, ok: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, null,
            tint = AppColors.Accent, modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(title, color = AppColors.TextPrimary, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Default.KeyboardArrowRight, null, tint = AppColors.TextDim)
    }
}

// ═══════════════════════════════════════════════════════════
//  ЭКРАН «НАСТРОЙКИ» (полные)
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
    var autoUpdate by remember { mutableStateOf(ScheduleStore.autoUpdate(ctx)) }
    var notifyEnd by remember { mutableStateOf(ScheduleStore.notifyEndOfPair(ctx)) }
    var themeMode by remember { mutableStateOf(ScheduleStore.themeMode(ctx)) }
    var lastUpdate by remember { mutableStateOf(ScheduleStore.lastAutoUpdate(ctx)) }
    var updateStatus by remember { mutableStateOf("") }
    var isUpdating by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var showTeacherDialog by remember { mutableStateOf(false) }
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
                                    Notifier.scheduleDnd(ctx)
                                    ScheduleWidget.updateAll(ctx)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(g, color = if (g == group) AppColors.Accent else AppColors.TextPrimary)
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Настройки", color = AppColors.TextPrimary,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))

        LazyColumn(Modifier.fillMaxSize()) {

            item(key = "sec_main") { SectionTitle("Основное") }
            if (isTeacher) {
                item(key = "teacher_name") {
                    SettingRow("Моё ФИО", teacherName.ifEmpty { "не указано" }) {
                        teacherInput = teacherName; showTeacherDialog = true
                    }
                }
            } else {
                item(key = "group") {
                    SettingRow("Группа", group.ifEmpty { "не выбрана" }) { showGroupDialog = true }
                }
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

            item(key = "sec_theme") {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Тема оформления")
            }
            item(key = "theme") {
                SettingRow("Тема", when (themeMode) {
                    ScheduleStore.THEME_LIGHT -> "Светлая"
                    ScheduleStore.THEME_DARK -> "Тёмная"
                    else -> "Авто (8:00–20:00)"
                }) {
                    themeMode = when (themeMode) {
                        ScheduleStore.THEME_AUTO -> ScheduleStore.THEME_LIGHT
                        ScheduleStore.THEME_LIGHT -> ScheduleStore.THEME_DARK
                        else -> ScheduleStore.THEME_AUTO
                    }
                    ScheduleStore.setThemeMode(ctx, themeMode)
                }
            }

            item(key = "sec_notif") {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Уведомления")
            }
            item(key = "notify_end") {
                SwitchRow("Уведомление о конце пары",
                    "Сообщать, когда пара закончилась", notifyEnd) {
                    notifyEnd = it
                    ScheduleStore.setNotifyEndOfPair(ctx, it)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleBeforePairs(ctx) }
                }
            }
            item(key = "dnd_switch") {
                SwitchRow("Авто-тишина на паре",
                    "Включать «Не беспокоить» когда идёт пара", dndEnabled) {
                    dndEnabled = it
                    ScheduleStore.setDndEnabled(ctx, it)
                    scope.launch(Dispatchers.IO) { Notifier.scheduleDnd(ctx) }
                }
            }
            item(key = "dnd_access") {
                PermissionRow("Доступ к «Не беспокоить»",
                    if (dndAccessOk) "Разрешён" else "Нужно разрешить для авто-тишины",
                    dndAccessOk) { PermissionsHelper.openDndSettings(ctx) }
            }

            item(key = "sec_auto") {
                Spacer(Modifier.height(16.dp))
                SectionTitle("Автообновление расписания")
            }
            item(key = "auto_switch") {
                SwitchRow("Автоматически в 7:00 и 18:00",
                    "Скачивать свежее расписание 2 раза в день", autoUpdate) {
                    autoUpdate = it
                    ScheduleStore.setAutoUpdate(ctx, it)
                    ScheduleUpdater.schedule(ctx, it)
                    if (it) Notifier.scheduleBothDailyUpdates(ctx)
                }
            }
            item(key = "auto_last") {
                Row(Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, null, tint = AppColors.TextDim, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Последнее обновление", color = AppColors.TextPrimary, fontSize = 15.sp)
                        Text(formatLastUpdate(lastUpdate), color = AppColors.TextSecondary, fontSize = 12.sp)
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
                                withContext(Dispatchers.IO) { ScheduleUpdater.runNow(ctx) }
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
                        containerColor = AppColors.Accent, contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isUpdating) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isUpdating) "Обновляем…" else "Обновить расписание сейчас")
                }
                if (updateStatus.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(updateStatus, color = AppColors.Accent, fontSize = 12.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "sec_perm") {
                SectionTitle("Разрешения и фоновая работа")
            }
            item(key = "p1") {
                PermissionRow("Уведомления", "Чтобы приходили сводки и напоминания",
                    true) { PermissionsHelper.openNotificationSettings(ctx) }
            }
            item(key = "p2") {
                PermissionRow("Точные будильники",
                    if (exactAlarmOk) "Разрешены" else "Разрешите для точного времени",
                    exactAlarmOk) { PermissionsHelper.openExactAlarmSettings(ctx) }
            }
            item(key = "p3") {
                PermissionRow("Батарея без ограничений",
                    if (batteryOk) "Система не будет усыплять" else "Отключите оптимизацию батареи",
                    batteryOk) { PermissionsHelper.openBatteryOptimizationSettings(ctx) }
            }
            item(key = "p4") {
                PermissionRow("Автозапуск",
                    "Включите в настройках приложения (MIUI/Huawei)", null) {
                    PermissionsHelper.openAppSettings(ctx)
                }
            }

            item(key = "btn_resched") {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        scope.launch(Dispatchers.IO) {
                            InternetTime.sync(ctx)
                            Notifier.scheduleMorning(ctx)
                            Notifier.scheduleBeforePairs(ctx)
                            Notifier.scheduleDnd(ctx)
                            Notifier.scheduleBothDailyUpdates(ctx)
                            RescheduleWorker.schedule(ctx)
                            ScheduleWidget.updateAll(ctx)
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.AccentDark, contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("🔔 Перепланировать всё") }
                Spacer(Modifier.height(16.dp))
            }

            item(key = "version") {
                Surface(
                    color = Color.Transparent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                val start = System.currentTimeMillis()
                                tryAwaitRelease()
                                if (System.currentTimeMillis() - start >= 5000L) debugMode = !debugMode
                            })
                        }
                ) {
                    Text("Версия 1.12" + if (debugMode) "  🐛 DEBUG" else "",
                        color = if (debugMode) AppColors.Accent else AppColors.TextDim,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 12.dp))
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
                            DebugLine("Текущая неделя", if (ScheduleStore.currentWeek(ctx) == WeekType.EVEN) "Чётная" else "Нечётная")
                            DebugLine("Показываем", if (InternetTime.isDisplayingTomorrow()) "Завтра" else "Сегодня")
                            DebugLine("Интернет-время синхр.", if (InternetTime.isSynced()) "да" else "нет")
                            DebugLine("Автообновление", if (autoUpdate) "вкл" else "выкл")
                            DebugLine("DND авто-режим", if (dndEnabled) "вкл" else "выкл")
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { ScheduleStore.clear(ctx); totalPairs = 0 },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = AppColors.AccentDark, contentColor = Color.White
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
fun SectionTitle(text: String) {
    Text(text.uppercase(), color = AppColors.TextDim, fontSize = 11.sp,
        fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
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
        Text(label, color = AppColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text(value, color = AppColors.TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SettingRow(title: String, value: String, onClick: () -> Unit) {
    Surface(color = Color.Transparent, modifier = Modifier.fillMaxWidth().clickable { onClick() }) {
        Row(Modifier.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = AppColors.TextPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Text(value, color = AppColors.Accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Default.KeyboardArrowRight, null, tint = AppColors.TextDim)
        }
    }
    Divider(color = AppColors.Divider)
}

@Composable
fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.TextPrimary, fontSize = 16.sp)
            Text(subtitle, color = AppColors.TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
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
    onNoteChanged: (String) -> Unit,
    onTeacherClick: (String) -> Unit
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
                    Text(p.number.toString(),
                        color = if (isSkipped) AppColors.TextDim else AppColors.Accent,
                        fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(p.time, color = AppColors.Accent, fontSize = 15.sp,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onToggleSkipped) {
                    Icon(if (isSkipped) Icons.Default.PlayArrow else Icons.Default.Close, null,
                        tint = if (isSkipped) AppColors.Accent else AppColors.TextDim)
                }
                IconButton(onClick = onToggleFavorite) {
                    Icon(if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder, null,
                        tint = if (isFavorite) AppColors.Accent else AppColors.TextDim)
                }
                IconButton(onClick = { editing = !editing }) {
                    Icon(Icons.Default.Edit, null, tint = AppColors.TextDim)
                }
            }

            Spacer(Modifier.height(6.dp))

            p.subgroups.forEach { sg ->
                Column(Modifier.padding(top = 4.dp)) {
                    val prefix = if (p.subgroups.size > 1) "Подгр. ${sg.index}: " else ""
                    Text(prefix + sg.subject, color = AppColors.TextPrimary, fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        textDecoration = if (isSkipped) TextDecoration.LineThrough else TextDecoration.None)
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🏛 ", fontSize = 12.sp)
                        Text("Ауд. ${sg.room.ifEmpty { "—" }}",
                            color = AppColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    if (showGroup) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("👥 ", fontSize = 12.sp)
                            Text("Группа ${p.group}", color = AppColors.TextPrimary,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(enabled = sg.teacher.isNotBlank()) {
                                onTeacherClick(sg.teacher)
                            }
                        ) {
                            Text("👤 ", fontSize = 12.sp)
                            Text(sg.teacher.ifEmpty { "—" },
                                color = AppColors.Accent, fontSize = 13.sp,
                                textDecoration = if (sg.teacher.isNotBlank()) TextDecoration.Underline
                                else TextDecoration.None)
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
                    value = noteText, onValueChange = { noteText = it },
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
                    TextButton(onClick = { onNoteChanged(noteText); editing = false }) {
                        Text("Сохранить", color = AppColors.Accent)
                    }
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
