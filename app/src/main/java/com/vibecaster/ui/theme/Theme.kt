package com.vibecaster.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Palette
val DeepSpace = Color(0xFF0B0614)
val SurfaceDark = Color(0xFF17102A)
val SurfaceCard = Color(0xFF1E1536)
val Violet = Color(0xFFA78BFA)
val VioletDeep = Color(0xFF7C3AED)
val Pink = Color(0xFFF472B6)
val Cyan = Color(0xFF22D3EE)
val TextPrimary = Color(0xFFF4F0FF)
val TextSecondary = Color(0xFF9E93BF)

private val DarkColors = darkColorScheme(
    primary = Violet,
    onPrimary = Color(0xFF1A0B2E),
    secondary = Pink,
    onSecondary = Color(0xFF2A0A1C),
    tertiary = Cyan,
    background = DeepSpace,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceCard,
    onSurfaceVariant = TextSecondary,
    outline = Color(0xFF3A2D5C)
)

private val AppTypography = Typography(
    headlineMedium = Typography().headlineMedium.copy(fontWeight = FontWeight.Bold),
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.SemiBold),
    bodyMedium = Typography().bodyMedium.copy(lineHeight = 20.sp)
)

@Composable
fun VibeCasterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColors,
        typography = AppTypography,
        content = content
    )
}
