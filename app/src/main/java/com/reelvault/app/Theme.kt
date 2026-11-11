package com.reelvault.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Violet = Color(0xFF8B5CF6)
val Blue = Color(0xFF3B82F6)
val Pink = Color(0xFFFF4D8D)
val AccentBrush = Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF5B6CF6), Color(0xFF3B82F6)))

val LocalDark = compositionLocalOf { true }

private val DarkScheme = darkColorScheme(
    primary = Violet, secondary = Blue, background = Color(0xFF07050F), surface = Color(0xFF110C20),
    onBackground = Color.White, onSurface = Color.White,
)
private val LightScheme = lightColorScheme(
    primary = Color(0xFF6D3EF0), secondary = Blue, background = Color(0xFFF3F0FF), surface = Color.White,
    onBackground = Color(0xFF14102B), onSurface = Color(0xFF14102B),
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
    CompositionLocalProvider(LocalDark provides dark) {
        MaterialTheme(colorScheme = if (dark) DarkScheme else LightScheme, typography = AppTypography, content = content)
    }
}
