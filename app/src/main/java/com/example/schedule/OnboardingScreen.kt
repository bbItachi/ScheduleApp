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

private const val CHANNEL_URL = "https://max.ru/channel_rasp_spk"

@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var role by remember { mutableStateOf<String?>(null) }
    var groups by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedGroup by remember { mutableStateOf<String?>(null) }
    var teacherName by remember { mutableStateOf("") }
    var isParsing by remember { mutableStateOf(false) }
    var statusMsg by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            isParsing = true
            statusMsg = "Парсим файл…"
            try {
                val parsed = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(uri)!!.use { XlsxParser.parse(it) }
                }
                ScheduleStore.save(ctx, parsed)
                groups = parsed.map { it.group }.distinct().sorted()
                statusMsg = "Найдено групп: ${groups.size}"
            } catch (e: Exception) {
                statusMsg = "Ошибка: ${e.message}"
            } finally {
                isParsing = false
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
                    onClick = { role = ScheduleStore.ROLE_STUDENT }
                )
                Spacer(Modifier.width(10.dp))
                RoleCard(
                    title = "Преподаватель",
                    icon = Icons.Default.Person,
                    selected = role == ScheduleStore.ROLE_TEACHER,
                    modifier = Modifier.weight(1f),
                    onClick = { role = ScheduleStore.ROLE_TEACHER }
                )
            }

            if (role == ScheduleStore.ROLE_STUDENT) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "2. Загрузи расписание",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Файл .xlsx можно скачать на сайте споспк.рф",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        picker.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel", "*/*"
                        ))
                    },
                    enabled = !isParsing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isParsing) "Обработка…" else "Загрузить .xlsx")
                }

                if (statusMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(statusMsg, color = AppColors.Accent, fontSize = 13.sp)
                }

                if (groups.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
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
                }

                Spacer(Modifier.height(24.dp))
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

            if (role == ScheduleStore.ROLE_TEACHER) {
                Spacer(Modifier.height(28.dp))
                Text(
                    "2. Введи своё ФИО",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Точно как в расписании, например: Сидоров И.И.",
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

                Spacer(Modifier.height(24.dp))
                Text(
                    "3. Загрузи расписание",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Файл .xlsx можно скачать на сайте споспк.рф",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(10.dp))

                Button(
                    onClick = {
                        picker.launch(arrayOf(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                            "application/vnd.ms-excel", "*/*"
                        ))
                    },
                    enabled = !isParsing,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AppColors.Accent,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isParsing) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(Icons.Default.Upload, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (isParsing) "Обработка…" else "Загрузить .xlsx")
                }
                if (statusMsg.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(statusMsg, color = AppColors.Accent, fontSize = 13.sp)
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