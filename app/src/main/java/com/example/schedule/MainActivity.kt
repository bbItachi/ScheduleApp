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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannels(this)
        Notifier.scheduleMorning(this)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { App() } }
    }
}

@Composable
fun App() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var pairs by remember { mutableStateOf<List<Pair>>(emptyList()) }
    var week by remember { mutableStateOf(ScheduleStore.currentWeek(ctx)) }
    var weather by remember { mutableStateOf<Weather.Info?>(null) }
    var status by remember { mutableStateOf("") }
    var now by remember { mutableStateOf(Date()) }

    // Загрузка расписания из памяти
    LaunchedEffect(Unit) {
        pairs = ScheduleStore.load(ctx)?.pairs ?: emptyList()
        weather = Weather.fetch()
        if (pairs.isNotEmpty()) {
            Notifier.scheduleBeforePairs(ctx)
        }
    }

    // Тик каждую секунду
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Запрос разрешения на уведомления
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // Выбор файла
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = "Парсим…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use {
                        XlsxParser.parse(it, "Д043")
                    }
                }
                ScheduleStore.save(ctx, parsed)
                pairs = parsed
                status = "Загружено пар: ${parsed.size}"
                Notifier.scheduleBeforePairs(ctx)
            } catch (e: Exception) {
                status = "Ошибка: ${e.message}"
            }
        }
    }

    val dayName = ScheduleStore.dayNameRu()
    val todayPairs = pairs.filter { it.day == dayName && it.week == week }
        .sortedBy { it.number }

    val df = remember { SimpleDateFormat("EEEE, d MMMM", Locale("ru")) }

    Surface(Modifier.fillMaxSize(), color = Color(0xFF1E1E2E)) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {

            // Заголовок: дата, время, погода
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF313244)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(df.format(now), color = Color.White, fontSize = 18.sp,
                        fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(now),
                        color = Color(0xFFA6ADC8), fontSize = 14.sp)
                    Spacer(Modifier.height(8.dp))
                    if (weather != null) {
                        Text("${weather!!.temp}°C, ${weather!!.desc}",
                            color = Color(0xFF89DCEB), fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold)
                        Text("Ветер ${weather!!.wind} м/с, Северск",
                            color = Color(0xFFA6ADC8), fontSize = 12.sp)
                    } else {
                        Text("Погода загружается…", color = Color(0xFFA6ADC8), fontSize = 14.sp)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // Переключатель недели
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Неделя: ", color = Color.White, fontSize = 15.sp)
                Spacer(Modifier.width(4.dp))
                FilterChip(
                    selected = week == WeekType.ODD,
                    onClick = {
                        week = WeekType.ODD
                        ScheduleStore.setCurrentWeek(ctx, WeekType.ODD)
                    },
                    label = { Text("Нечётная") }
                )
                Spacer(Modifier.width(6.dp))
                FilterChip(
                    selected = week == WeekType.EVEN,
                    onClick = {
                        week = WeekType.EVEN
                        ScheduleStore.setCurrentWeek(ctx, WeekType.EVEN)
                    },
                    label = { Text("Чётная") }
                )
            }

            Spacer(Modifier.height(12.dp))

            Row {
                Button(onClick = {
                    picker.launch(arrayOf(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "application/vnd.ms-excel",
                        "*/*"
                    ))
                }) { Text("Загрузить .xlsx") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        weather = Weather.fetch()
                        Notifier.scheduleBeforePairs(ctx)
                        status = "Обновлено"
                    }
                }) { Text("Обновить") }
            }

            if (status.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(status, color = Color(0xFFF9E2AF), fontSize = 13.sp)
            }

            Spacer(Modifier.height(12.dp))

            Text("$dayName (${if (week == WeekType.EVEN) "чётная" else "нечётная"})",
                color = Color(0xFFCBA6F7), fontSize = 18.sp, fontWeight = FontWeight.Bold)

            Spacer(Modifier.height(8.dp))

            if (todayPairs.isEmpty()) {
                Text("Пар нет 🎉", color = Color(0xFFA6E3A1), fontSize = 16.sp,
                    modifier = Modifier.padding(top = 20.dp))
            } else {
                LazyColumn {
                    items(todayPairs) { p -> PairCard(p) }
                }
            }
        }
    }
}

@Composable
fun PairCard(p: Pair) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF313244)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p.number.toString(), color = Color(0xFFCBA6F7),
                    fontSize = 22.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(36.dp))
                Text(p.time, color = Color(0xFFF9E2AF), fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            p.subgroups.forEach { sg ->
                Column(Modifier.padding(start = 36.dp, top = 4.dp)) {
                    val prefix = if (p.subgroups.size > 1) "Подгр. ${sg.index}: " else ""
                    Text(prefix + sg.subject, color = Color.White,
                        fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Преп. ${sg.teacher.ifEmpty { "—" }}",
                        color = Color(0xFFA6ADC8), fontSize = 13.sp)
                    Text("Ауд. ${sg.room.ifEmpty { "—" }}",
                        color = Color(0xFFA6E3A1), fontSize = 13.sp)
                }
            }
        }
    }
}
