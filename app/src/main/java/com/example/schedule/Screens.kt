package com.example.schedule

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.IDN
import java.text.SimpleDateFormat
import java.util.*

// ═══════════════════════════════════════════════════════════
//  ОБЩИЕ КОМПОНЕНТЫ
// ═══════════════════════════════════════════════════════════

@Composable
fun AppFilterChip(selected: Boolean, onClick: () -> Unit, label: String) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, fontSize = 13.sp) },
        shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = AppColors.Card,
            labelColor = AppColors.TextSecondary,
            selectedContainerColor = AppColors.Accent,
            selectedLabelColor = Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (selected) AppColors.Accent else AppColors.Border
        )
    )
}

@Composable
fun CircleIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    filled: Boolean = false
) {
    val bg = if (filled) AppColors.Accent else AppColors.Card
    val fg = if (filled) Color.White else AppColors.Accent
    Box(
        modifier = Modifier
            .size(44.dp)
            .clickable { onClick() },
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

// ═══════════════════════════════════════════════════════════
//  ЭКРАН «СЕГОДНЯ»
// ═══════════════════════════════════════════════════════════

@Composable
fun TodayScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var pairs by remember { mutableStateOf<List<Lesson>>(emptyList()) }
    var week by remember { mutableStateOf(ScheduleStore.currentWeek(ctx)) }
    var weather by remember { mutableStateOf<Weather.Info?>(null) }
    var status by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(Date()) }
    var favorites by remember { mutableStateOf(ScheduleStore.favorites(ctx)) }

    EnsureNotificationPermission()

    LaunchedEffect(week) {
        pairs = ScheduleStore.pairsFor(ctx, ScheduleStore.dayNameRu(), week)
    }
    LaunchedEffect(Unit) { weather = Weather.fetch() }
    LaunchedEffect(Unit) {
        while (true) { now = Date(); kotlinx.coroutines.delay(1000) }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Парсим…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { XlsxParser.parse(it) }
                }
                ScheduleStore.save(ctx, parsed)
                if (ScheduleStore.group(ctx).isEmpty()) {
                    ScheduleStore.setGroup(ctx, ScheduleStore.allGroups(ctx).firstOrNull() ?: "")
                }
                pairs = ScheduleStore.pairsFor(ctx, ScheduleStore.dayNameRu(), week)
                status = "Загружено пар: ${parsed.size}, групп: ${ScheduleStore.allGroups(ctx).size}"
                Notifier.scheduleBeforePairs(ctx)
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            }
        }
    }

    val dfDate = remember { SimpleDateFormat("EEEE, d MMMM", Locale("ru")) }
    val dfTime = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val dayName = ScheduleStore.dayNameRu()
    val group = ScheduleStore.group(ctx)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp)) {

        // ── Компактная карточка погоды ──
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
                // Дата/время — слева
                Column(Modifier.weight(1f)) {
                    Text(
                        dfDate.format(now).replaceFirstChar { it.uppercase() },
                        color = AppColors.TextPrimary, fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        dfTime.format(now),
                        color = AppColors.TextSecondary, fontSize = 12.sp
                    )
                }
                // Погода — справа
                if (weather != null) {
                    val w = weather!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${w.temp}°",
                            color = AppColors.Accent,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                w.desc,
                                color = AppColors.TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "ощущ. ${w.feelsLike}° · 💨${w.wind} · 💧${w.humidity}%",
                                color = AppColors.TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                } else {
                    Text("Загрузка…", color = AppColors.TextSecondary, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // ── Заголовок дня ──
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                dayName,
                color = AppColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "(${if (week == WeekType.EVEN) "чётная" else "нечётная"})",
                color = AppColors.TextSecondary, fontSize = 13.sp,
                modifier = Modifier.padding(bottom = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            if (group.isNotEmpty()) {
                Surface(
                    color = AppColors.AccentSoft,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        group,
                        color = AppColors.Accent, fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // ── Расписание (основная часть экрана) ──
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (pairs.isEmpty()) {
                Box(
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
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(pairs) { p ->
                        val key = "${p.group}|${p.day}|${p.week}|${p.number}"
                        PairCard(
                            p = p,
                            isFavorite = key in favorites,
                            note = ScheduleStore.getNote(ctx, key),
                            onToggleFavorite = {
                                ScheduleStore.toggleFavorite(ctx, key)
                                favorites = ScheduleStore.favorites(ctx)
                            }
                        )
                    }
                }
            }
        }

        // ── Нижняя панель: неделя слева, кнопки справа ──
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
                // Слева — выбор недели
                AppFilterChip(
                    selected = week == WeekType.ODD,
                    onClick = {
                        week = WeekType.ODD
                        ScheduleStore.setCurrentWeek(ctx, WeekType.ODD)
                        Notifier.scheduleBeforePairs(ctx)
                    },
                    label = "Нечётная"
                )
                Spacer(Modifier.width(4.dp))
                AppFilterChip(
                    selected = week == WeekType.EVEN,
                    onClick = {
                        week = WeekType.EVEN
                        ScheduleStore.setCurrentWeek(ctx, WeekType.EVEN)
                        Notifier.scheduleBeforePairs(ctx)
                    },
                    label = "Чётная"
                )

                Spacer(Modifier.weight(1f))

                // Справа — кнопки действий
                CircleIconButton(
                    icon = Icons.Default.Upload,
                    filled = true,
                    onClick = {
                        picker.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel", "*/*"
                        ))
                    }
                )
                Spacer(Modifier.width(4.dp))
                CircleIconButton(
                    icon = Icons.Default.Refresh,
                    onClick = {
                        scope.launch {
                            weather = Weather.fetch()
                            Notifier.scheduleBeforePairs(ctx)
                            status = "Обновлено"
                        }
                    }
                )
                Spacer(Modifier.width(4.dp))
                CircleIconButton(
                    icon = Icons.Default.Download,
                    onClick = {
                        try {
                            val host = IDN.toASCII("споспк.рф")
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("http://$host/"))
                            ctx.startActivity(intent)
                        } catch (_: Exception) { }
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
    var week by remember { mutableStateOf(ScheduleStore.currentWeek(ctx)) }
    var search by remember { mutableStateOf("") }
    var showOnlyFavorites by remember { mutableStateOf(false) }
    var expandedDays by remember { mutableStateOf(setOf(ScheduleStore.dayNameRu())) }
    var favorites by remember { mutableStateOf(ScheduleStore.favorites(ctx)) }
    var allPairs by remember { mutableStateOf<List<Lesson>>(emptyList()) }

    LaunchedEffect(week) {
        val g = ScheduleStore.group(ctx)
        allPairs = ScheduleStore.load(ctx)?.pairs
            ?.filter { it.group == g && it.week == week } ?: emptyList()
    }

    val days = listOf("ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА")

    val filtered = allPairs.filter { p ->
        val matchSearch = search.isBlank() ||
            p.subgroups.any { sg ->
                sg.subject.contains(search, true) ||
                sg.teacher.contains(search, true) ||
                sg.room.contains(search, true)
            }
        val matchFav = !showOnlyFavorites ||
            "${p.group}|${p.day}|${p.week}|${p.number}" in favorites
        matchSearch && matchFav
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Расписание на неделю", color = AppColors.TextPrimary,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            AppFilterChip(
                selected = week == WeekType.ODD,
                onClick = { week = WeekType.ODD; ScheduleStore.setCurrentWeek(ctx, WeekType.ODD) },
                label = "Нечётная"
            )
            Spacer(Modifier.width(6.dp))
            AppFilterChip(
                selected = week == WeekType.EVEN,
                onClick = { week = WeekType.EVEN; ScheduleStore.setCurrentWeek(ctx, WeekType.EVEN) },
                label = "Чётная"
            )
            Spacer(Modifier.weight(1f))
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
                item {
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
                    items(dayPairs) { p ->
                        val key = "${p.group}|${p.day}|${p.week}|${p.number}"
                        PairCard(
                            p = p,
                            isFavorite = key in favorites,
                            note = ScheduleStore.getNote(ctx, key),
                            onToggleFavorite = {
                                ScheduleStore.toggleFavorite(ctx, key)
                                favorites = ScheduleStore.favorites(ctx)
                            }
                        )
                    }
                    if (dayPairs.isEmpty()) {
                        item {
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
            Surface(
                color = AppColors.AccentSoft,
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("$count", color = AppColors.Accent, fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
//  ЭКРАН «НАСТРОЙКИ» (с debug-режимом — 5 секунд)
// ═══════════════════════════════════════════════════════════

@Composable
fun SettingsScreen() {
    val ctx = LocalContext.current
    var group by remember { mutableStateOf(ScheduleStore.group(ctx)) }
    var groups by remember { mutableStateOf(ScheduleStore.allGroups(ctx)) }
    var morningH by remember { mutableStateOf(ScheduleStore.morningHour(ctx)) }
    var morningM by remember { mutableStateOf(ScheduleStore.morningMinute(ctx)) }
    var beforeMin by remember { mutableStateOf(ScheduleStore.beforeMinutes(ctx)) }
    var autoWeek by remember { mutableStateOf(ScheduleStore.autoWeek(ctx)) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var debugMode by remember { mutableStateOf(false) }
    var totalPairs by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        groups = ScheduleStore.allGroups(ctx)
        group = ScheduleStore.group(ctx)
        totalPairs = ScheduleStore.load(ctx)?.pairs?.size ?: 0
    }

    if (showGroupDialog) {
        AlertDialog(
            onDismissRequest = { showGroupDialog = false },
            title = { Text("Выбор группы", color = AppColors.TextPrimary) },
            text = {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(groups) { g ->
                        TextButton(
                            onClick = {
                                ScheduleStore.setGroup(ctx, g)
                                group = g
                                showGroupDialog = false
                                Notifier.scheduleBeforePairs(ctx)
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

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Настройки", color = AppColors.TextPrimary,
            fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))

        SettingRow(
            title = "Группа",
            value = group.ifEmpty { "не выбрана" },
            onClick = { showGroupDialog = true }
        )

        SettingRow(
            title = "Утренняя сводка (часы)",
            value = "%02d".format(morningH),
            onClick = {
                morningH = (morningH + 1) % 24
                ScheduleStore.setMorning(ctx, morningH, morningM)
                Notifier.scheduleMorning(ctx)
            }
        )

        SettingRow(
            title = "Утренняя сводка (минуты)",
            value = "%02d".format(morningM),
            onClick = {
                morningM = (morningM + 5) % 60
                ScheduleStore.setMorning(ctx, morningH, morningM)
                Notifier.scheduleMorning(ctx)
            }
        )

        SettingRow(
            title = "Напоминание до пары",
            value = "$beforeMin мин",
            onClick = {
                beforeMin = when (beforeMin) {
                    5 -> 10
                    10 -> 15
                    15 -> 20
                    20 -> 30
                    else -> 5
                }
                ScheduleStore.setBeforeMinutes(ctx, beforeMin)
                Notifier.scheduleBeforePairs(ctx)
            }
        )

        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Авто-определение недели", color = AppColors.TextPrimary, fontSize = 16.sp)
                Text("Требуется дата начала семестра",
                    color = AppColors.TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = autoWeek,
                onCheckedChange = {
                    autoWeek = it
                    ScheduleStore.setAutoWeek(ctx, it)
                    Notifier.scheduleBeforePairs(ctx)
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

        Spacer(Modifier.height(24.dp))

        // ⚙️ DEBUG — долгое нажатие 5 секунд (без подсказки)
        Surface(
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onPress = {
                            val start = System.currentTimeMillis()
                            tryAwaitRelease()
                            val duration = System.currentTimeMillis() - start
                            if (duration >= 5000L) {
                                debugMode = !debugMode
                            }
                        }
                    )
                }
        ) {
            Text(
                text = "Версия 1.0" + if (debugMode) "  🐛 DEBUG" else "",
                color = if (debugMode) AppColors.Accent else AppColors.TextDim,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
        }

        if (debugMode) {
            Spacer(Modifier.height(12.dp))
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
                    DebugLine("Всего пар в базе", "$totalPairs")
                    DebugLine("Групп", "${groups.size}")
                    DebugLine("Текущая неделя",
                        if (ScheduleStore.currentWeek(ctx) == WeekType.EVEN) "Чётная" else "Нечётная")
                    DebugLine("Координаты погоды", "56.60, 84.85 (Северск)")
                    DebugLine("Напоминание за", "$beforeMin мин")
                    DebugLine("Утренняя сводка", "%02d:%02d".format(morningH, morningM))
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
        }
    }
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
    note: String,
    onToggleFavorite: () -> Unit
) {
    val ctx = LocalContext.current
    var noteText by remember { mutableStateOf(note) }
    var editing by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = AppColors.Card),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.Border),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = AppColors.AccentSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        p.number.toString(),
                        color = AppColors.Accent, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    p.time, color = AppColors.TextPrimary, fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        null,
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
                    Text(prefix + sg.subject, color = AppColors.TextPrimary,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Row {
                        Text("👤 ", fontSize = 12.sp)
                        Text(sg.teacher.ifEmpty { "—" },
                            color = AppColors.TextSecondary, fontSize = 13.sp)
                    }
                    Row {
                        Text("🏛 ", fontSize = 12.sp)
                        Text(sg.room.ifEmpty { "—" },
                            color = AppColors.Accent, fontSize = 13.sp,
                            fontWeight = FontWeight.Medium)
                    }
                }
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
                        val key = "${p.group}|${p.day}|${p.week}|${p.number}"
                        ScheduleStore.setNote(ctx, key, noteText)
                        editing = false
                    }) { Text("Сохранить", color = AppColors.Accent) }
                    TextButton(onClick = { editing = false }) {
                        Text("Отмена", color = AppColors.TextSecondary)
                    }
                }
            } else if (noteText.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = AppColors.AccentSoft,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("📝 $noteText", color = AppColors.TextPrimary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}
