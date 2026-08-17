package com.veritas.ai.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// --- Veritas Color Palette ---

val VeritasDarkBg = Color(0xFF0A0F1C)
val VeritasSurface = Color(0xFF111827)
val VeritasSurfaceLight = Color(0xFF1E293B)
val VeritasAccent = Color(0xFF50C878)
val VeritasAccentDark = Color(0xFF04140D)
val VeritasTextPrimary = Color(0xFFE2E8F0)
val VeritasTextSecondary = Color(0xFF94A3B8)
val VeritasTextMuted = Color(0xFF64748B)
val VeritasBorder = Color(0xFF2A3A5C)
val VeritasError = Color(0xFFF87171)
val VeritasWarning = Color(0xFFFBBF24)

private val DarkColorScheme = darkColorScheme(
    primary = VeritasAccent,
    onPrimary = VeritasAccentDark,
    secondary = VeritasSurfaceLight,
    onSecondary = VeritasTextPrimary,
    background = VeritasDarkBg,
    onBackground = VeritasTextPrimary,
    surface = VeritasSurface,
    onSurface = VeritasTextPrimary,
    error = VeritasError,
    onError = VeritasDarkBg,
    outline = VeritasBorder,
    outlineVariant = VeritasSurfaceLight
)

@Composable
fun VeritasTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = VeritasTypography,
        content = content
    )
}

val VeritasTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        letterSpacing = 0.2.sp,
        color = VeritasAccent
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.15.sp,
        color = VeritasAccent
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = VeritasTextPrimary
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        color = VeritasTextPrimary
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        color = VeritasTextPrimary
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = VeritasTextPrimary
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = VeritasTextSecondary
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 0.3.sp,
        color = VeritasAccent
    )
)
