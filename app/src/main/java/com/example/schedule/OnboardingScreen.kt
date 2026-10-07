package com.example.schedule

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

private const val CHANNEL_URL = "https://max.ru/channel_rasp_spk"
private const val SCHEDULE_URL = "http://xn--j1ahcbhc.xn--p1ai/rasp.xlsx"

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var role by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var teacherName by remember { mutableStateOf("") }
    var isDownloading by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }
    var downloadFailed by remember { mutableStateOf(false) }

    // Автоскачивание расписания с сайта
    fun autoDownload() {
        if (isDownloading) return
        scope.launch {
            isDownloading = true
            downloadFailed = false
            statusMsg = "Скачиваем расписание с сайта…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    val url = URL(SCHEDULE_URL)
                    val conn = (url.openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"
                        connectTimeout = 20_000
                        readTimeout = 45_000
                        setRequestProperty("User-Agent", "ScheduleApp/1.7")
                        instanceFollowRedirects = true
                    }
                    val result = conn.inputStream.use { XlsxParser.parse(it) }
                    conn.disconnect()
                    result
                }
                if (parsed.isEmpty()) {
                    statusMsg = "Файл пустой"
                    downloadFailed = true
                } else {
                    ScheduleStore.save(ctx, parsed)
                    ScheduleStore.setLastAutoUpdate(ctx, System.currentTimeMillis())
                    groups = parsed.map { it.group }.distinct().sorted()
                    statusMsg = "Загружено! Найдено групп: ${groups.size}"
                }
            } catch (e: Exception) {
                statusMsg = "Не удалось скачать: ${e.message ?: "ошибка сети"}"
                downloadFailed = true
            } finally {
                isDownloading = false
            }
        }
    }

    // Ручная загрузка (запасной вариант)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isDownloading = true
            downloadFailed = false
            statusMsg = "Обрабатываем файл…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { XlsxParser.parse(it) }
                }
                ScheduleStore.save(ctx, parsed)
                groups = parsed.map { it.group }.distinct().sorted()
                statusMsg = "Загружено! Найдено групп: ${groups.size}"
            } catch (e: Exception) {
                statusMsg = "Ошибка: ${e.message}"
                downloadFailed = true
            } finally {
                isDownloading = false
            }
        }
    }

    Surface(Modifier.fillMaxSize(), color = AppColors.Background) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            Text(
                "Добро пожаловать!",
                color = AppColors.TextPrimary,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "расписание СПК",
                color = AppColors.Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(Modifier.height(28.dp))

            Text(
                "1. Кто ты?",
                color = AppColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            Row(Modifier.fillMaxWidth()) {
                RoleCard(
                    title = "Ученик",
                    icon = Icons.Default.School,
                    selected = role == ScheduleStore.ROLE_STUDENT,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        role = ScheduleStore.ROLE_STUDENT
                        if (groups.isEmpty() && !isDownloading) autoDownload()
                    }
                )
                Spacer(Modifier.width(10.dp))
                RoleCard(
                    title = "Преподаватель",
                    icon = Icons.Default.Person,
                    selected = role == ScheduleStore.ROLE_TEACHER,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        role = ScheduleStore.ROLE_TEACHER
                        if (groups.isEmpty() && !isDownloading) autoDownload()
                    }
                )
            }

            if (role != null) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "2. Расписание",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                // Статус скачивания
                if (isDownloading) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = AppColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            statusMsg,
                            color = AppColors.TextPrimary,
                            fontSize = 14.sp
                        )
                    }
                } else if (statusMsg.isNotEmpty()) {
                    Surface(
                        color = if (downloadFailed) AppColors.CardElevated else AppColors.AccentSoft,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            statusMsg,
                            color = if (downloadFailed) AppColors.TextSecondary else AppColors.Accent,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Кнопки
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { autoDownload() },
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AppColors.Accent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Скачать с сайта")
                    }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            picker.launch(arrayOf(
                                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "application/vnd.ms-excel", "*/*"
                            ))
                        },
                        enabled = !isDownloading,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = AppColors.TextSecondary
                        )
                    ) {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                    }
                }

                // Преподаватель: поле ФИО
                if (role == ScheduleStore.ROLE_TEACHER) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "3. Твоё ФИО",
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Точно как в расписании, например: Иванов И.И.",
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = teacherName,
                        onValueChange = { teacherName = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Лизнева К.А.") },
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
                }

                // Студент: выбор группы
                if (role == ScheduleStore.ROLE_STUDENT && groups.isNotEmpty()) {
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "3. Выбери свою группу",
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    groups.forEach { g ->
                        val selected = g == selectedGroup
                        Surface(
                            color = if (selected) AppColors.AccentSoft else AppColors.Card,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(
                                1.dp,
                                if (selected) AppColors.Accent else AppColors.Border
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .clickable { selectedGroup = g }
                        ) {
                            Row(
                                Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected,
                                    onClick = { selectedGroup = g },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = AppColors.Accent,
                                        unselectedColor = AppColors.TextDim
                                    )
                                )
                                Text(
                                    g,
                                    color = if (selected) AppColors.Accent else AppColors.TextPrimary,
                                    fontSize = 15.sp,
                                    fontWeight = if (selected) FontWeight.SemiBold
                                        else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Подпишись на канал",
                        color = AppColors.TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Актуальная информация и изменения в расписании",
                        color = AppColors.TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            try {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(CHANNEL_URL))
                                )
                            } catch (_: Exception) { }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, AppColors.Accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.OpenInNew, null,
                            tint = AppColors.Accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Открыть канал", color = AppColors.Accent,
                            fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))

            val canStart = when (role) {
                ScheduleStore.ROLE_STUDENT -> selectedGroup != null && groups.isNotEmpty()
                ScheduleStore.ROLE_TEACHER -> teacherName.isNotBlank() &&
                    (ScheduleStore.load(ctx)?.pairs?.isNotEmpty() == true)
                else -> false
            }

            Button(
                onClick = {
                    when (role) {
                        ScheduleStore.ROLE_STUDENT -> {
                            ScheduleStore.setRole(ctx, ScheduleStore.ROLE_STUDENT)
                            ScheduleStore.setGroup(ctx, selectedGroup ?: "")
                        }
                        ScheduleStore.ROLE_TEACHER -> {
                            ScheduleStore.setRole(ctx, ScheduleStore.ROLE_TEACHER)
                            ScheduleStore.setTeacherName(ctx, teacherName.trim())
                        }
                    }
                    scope.launch(Dispatchers.IO) {
                        Notifier.scheduleBeforePairs(ctx)
                        Notifier.scheduleMorning(ctx)
                        Notifier.scheduleDnd(ctx)
                        ScheduleWidget.updateAll(ctx)
                    }
                    onDone()
                },
                enabled = canStart,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.Accent,
                    contentColor = Color.White,
                    disabledContainerColor = AppColors.CardElevated,
                    disabledContentColor = AppColors.TextDim
                ),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text("Начать", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
fun RoleCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        color = if (selected) AppColors.AccentSoft else AppColors.Card,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(2.dp, if (selected) AppColors.Accent else AppColors.Border),
        modifier = modifier.clickable { onClick() }
    ) {
        Column(
            Modifier.padding(vertical = 20.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon, null,
                tint = if (selected) AppColors.Accent else AppColors.TextSecondary,
                modifier = Modifier.size(36.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = if (selected) AppColors.Accent else AppColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}