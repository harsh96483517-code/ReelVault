package com.reelvault.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Black & white palette. Accent = white on black, black on white. */
object Mono { var dark by mutableStateOf(true) }

val Violet: Color get() = if (Mono.dark) Color.White else Color(0xFF111111)     // primary accent
val Blue: Color get() = if (Mono.dark) Color(0xFFB5B5B5) else Color(0xFF555555)  // secondary accent
val Pink: Color get() = Violet
val OnAccent: Color get() = if (Mono.dark) Color.Black else Color.White
val AccentBrush: Brush
    get() = if (Mono.dark) Brush.linearGradient(listOf(Color.White, Color(0xFFC8C8C8)))
    else Brush.linearGradient(listOf(Color(0xFF111111), Color(0xFF3A3A3A)))

val LocalDark = compositionLocalOf { true }

private val DarkScheme = darkColorScheme(
    primary = Color.White, onPrimary = Color.Black, secondary = Color(0xFFB5B5B5),
    background = Color.Black, surface = Color(0xFF0E0E0E), surfaceTint = Color.Transparent,
    onBackground = Color.White, onSurface = Color.White, onSurfaceVariant = Color(0xFFBDBDBD),
    surfaceContainerLowest = Color(0xFF050505), surfaceContainerLow = Color(0xFF0E0E0E), surfaceContainer = Color(0xFF161616),
    surfaceContainerHigh = Color(0xFF1C1C1C), surfaceContainerHighest = Color(0xFF222222),
    outline = Color(0xFF555555), outlineVariant = Color(0xFF333333),
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF111111), onPrimary = Color.White, secondary = Color(0xFF555555),
    background = Color(0xFFF2F2F2), surface = Color.White, surfaceTint = Color.Transparent,
    onBackground = Color(0xFF111111), onSurface = Color(0xFF111111), onSurfaceVariant = Color(0xFF444444),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFAFAFA), surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFF0F0F0),
    outline = Color(0xFF888888), outlineVariant = Color(0xFFDDDDDD),
)

private val AppTypography = Typography(
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp, letterSpacing = (-0.4).sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp, letterSpacing = (-0.2).sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.2.sp),
)

@Composable
fun ReelVaultTheme(mode: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> true; 2 -> false; else -> isSystemInDarkTheme() }
    Mono.dark = dark
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, content = content)
    }
}
