package com.example.schedule

import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

object AppColors {
    val Background = Color(0xFF0A0E14)
    val Surface = Color(0xFF131A22)
    val Card = Color(0xFF1B2330)
    val CardElevated = Color(0xFF232D3D)
    val Border = Color(0xFF2A3545)
    val Divider = Color(0xFF222B38)

    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFA8C0E0)
    val TextDim = Color(0xFF6688AA)

    val Accent = Color(0xFF3B82F6)
    val AccentDark = Color(0xFF1E4FA8)
    val AccentSoft = Color(0xFF1A2F55)

    val Success = Color(0xFF60A5FA)
    val Warning = Color(0xFF93C5FD)
    val Danger = Color(0xFF1E4FA8)
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
