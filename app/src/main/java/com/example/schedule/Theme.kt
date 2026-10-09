package com.example.schedule

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

object AppColors {
    // Текущие цвета (по умолчанию — тёмная)
    var Background = Color(0xFF0A0E14)
    var Surface = Color(0xFF131A22)
    var Card = Color(0xFF1B2330)
    var CardElevated = Color(0xFF232D3D)
    var Border = Color(0xFF2A3545)
    var Divider = Color(0xFF222B38)
    var TextPrimary = Color(0xFFFFFFFF)
    var TextSecondary = Color(0xFFA8C0E0)
    var TextDim = Color(0xFF6688AA)
    var Accent = Color(0xFF3B82F6)
    var AccentDark = Color(0xFF1E4FA8)
    var AccentSoft = Color(0xFF1A2F55)
    var Success = Color(0xFF60A5FA)
    var Warning = Color(0xFF93C5FD)
    var Danger = Color(0xFF1E4FA8)

    fun applyDark() {
        Background = Color(0xFF0A0E14)
        Surface = Color(0xFF131A22)
        Card = Color(0xFF1B2330)
        CardElevated = Color(0xFF232D3D)
        Border = Color(0xFF2A3545)
        Divider = Color(0xFF222B38)
        TextPrimary = Color(0xFFFFFFFF)
        TextSecondary = Color(0xFFA8C0E0)
        TextDim = Color(0xFF6688AA)
        Accent = Color(0xFF3B82F6)
        AccentDark = Color(0xFF1E4FA8)
        AccentSoft = Color(0xFF1A2F55)
        Success = Color(0xFF60A5FA)
        Warning = Color(0xFF93C5FD)
        Danger = Color(0xFF1E4FA8)
    }

    fun applyLight() {
        Background = Color(0xFFF5F7FA)
        Surface = Color(0xFFFFFFFF)
        Card = Color(0xFFFFFFFF)
        CardElevated = Color(0xFFF0F3F8)
        Border = Color(0xFFE2E8F0)
        Divider = Color(0xFFEDF1F7)
        TextPrimary = Color(0xFF0A0E14)
        TextSecondary = Color(0xFF4A5568)
        TextDim = Color(0xFF94A3B8)
        Accent = Color(0xFF2563EB)
        AccentDark = Color(0xFF1E40AF)
        AccentSoft = Color(0xFFDBEAFE)
        Success = Color(0xFF2563EB)
        Warning = Color(0xFF3B82F6)
        Danger = Color(0xFF1E40AF)
    }
}

val ScheduleDarkScheme = darkColorScheme(
    primary = Color(0xFF3B82F6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E4FA8),
    onPrimaryContainer = Color.White,
    secondary = Color(0xFF2563EB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1A2F55),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF60A5FA),
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF1E4FA8),
    onTertiaryContainer = Color.White,
    background = Color(0xFF0A0E14),
    onBackground = Color.White,
    surface = Color(0xFF131A22),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF1B2330),
    onSurfaceVariant = Color(0xFFA8C0E0),
    surfaceTint = Color(0xFF3B82F6),
    inverseSurface = Color(0xFFEEEEEE),
    inverseOnSurface = Color(0xFF0A0E14),
    error = Color(0xFF93C5FD),
    onError = Color.Black,
    errorContainer = Color(0xFF1E4FA8),
    onErrorContainer = Color.White,
    outline = Color(0xFF2A3545),
    outlineVariant = Color(0xFF222B38),
    scrim = Color.Black
)

val ScheduleLightScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF3B82F6),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEFF6FF),
    onSecondaryContainer = Color(0xFF1E3A8A),
    tertiary = Color(0xFF3B82F6),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDBEAFE),
    onTertiaryContainer = Color(0xFF1E3A8A),
    background = Color(0xFFF5F7FA),
    onBackground = Color(0xFF0A0E14),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0A0E14),
    surfaceVariant = Color(0xFFF0F3F8),
    onSurfaceVariant = Color(0xFF4A5568),
    surfaceTint = Color(0xFF2563EB),
    inverseSurface = Color(0xFF0A0E14),
    inverseOnSurface = Color(0xFFFFFFFF),
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Color(0xFFE2E8F0),
    outlineVariant = Color(0xFFEDF1F7),
    scrim = Color.Black
)
