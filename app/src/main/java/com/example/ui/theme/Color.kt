package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

data class GlassColors(
    val backgroundPrimary: Color,
    val backgroundSecondary: Color,
    val surfacePrimary: Color,
    val surfaceElevated: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val accentTertiary: Color,
    val borderSubtle: Color,
    val glassTint: Color,
    val glassHighlight: Color,
    val success: Color,
    val warning: Color,
    val error: Color
)

val DarkGlassColors = GlassColors(
    backgroundPrimary = Color(0xFF000000),
    backgroundSecondary = Color(0xFF1C1C1E),
    surfacePrimary = Color(0xFF1C1C1E),
    surfaceElevated = Color(0xFF2C2C2E),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0xFFB7B7C2),
    textMuted = Color(0xFF9898A4),
    accentPrimary = Color(0xFF0A84FF),
    accentSecondary = Color(0xFF5AC8FA),
    accentTertiary = Color(0xFFBF5AF2),
    borderSubtle = Color(0x33FFFFFF),
    glassTint = Color(0x1A0A84FF),
    glassHighlight = Color(0x26FFFFFF),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    error = Color(0xFFFF453A)
)

val LightGlassColors = GlassColors(
    backgroundPrimary = Color(0xFFF2F2F7),
    backgroundSecondary = Color(0xFFFFFFFF),
    surfacePrimary = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFE8EBF2),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0xFF54545E),
    textMuted = Color(0xFF64646F),
    accentPrimary = Color(0xFF005CC8),
    accentSecondary = Color(0xFF5AC8FA),
    accentTertiary = Color(0xFFAF52DE),
    borderSubtle = Color(0x33000000),
    glassTint = Color(0x1A007AFF),
    glassHighlight = Color(0x26FFFFFF),
    success = Color(0xFF19743A),
    warning = Color(0xFF955000),
    error = Color(0xFFC52C25)
)

val LocalGlassColors = staticCompositionLocalOf { DarkGlassColors }

val GlassBackground: Color
    @Composable get() = LocalGlassColors.current.backgroundPrimary
val GlassSurface: Color
    @Composable get() = LocalGlassColors.current.surfacePrimary
val GlassSurfaceElevated: Color
    @Composable get() = LocalGlassColors.current.surfaceElevated
val GlassBorder: Color
    @Composable get() = LocalGlassColors.current.borderSubtle
val GlassBorderHighlight: Color
    @Composable get() = LocalGlassColors.current.glassHighlight
val CyanNeon: Color
    @Composable get() = LocalGlassColors.current.accentPrimary
val BlueNeon: Color
    @Composable get() = LocalGlassColors.current.accentSecondary
val PurpleNeon: Color
    @Composable get() = LocalGlassColors.current.accentTertiary
val TextPrimary: Color
    @Composable get() = LocalGlassColors.current.textPrimary
val TextSecondary: Color
    @Composable get() = LocalGlassColors.current.textSecondary
val TextMuted: Color
    @Composable get() = LocalGlassColors.current.textMuted
val VerdictTrueColor = DarkGlassColors.success
val VerdictFalseColor = DarkGlassColors.error
val VerdictPartialColor = DarkGlassColors.warning
val VerdictMisleadingColor = DarkGlassColors.warning
val VerdictInsufficientColor = DarkGlassColors.accentTertiary
val VerdictUnverifiableColor = DarkGlassColors.textMuted

val LiquidGlassGradient = Brush.verticalGradient(
    colors = listOf(DarkGlassColors.surfacePrimary, DarkGlassColors.backgroundPrimary.copy(alpha = 0.5f))
)
val LiquidGlowGradient = Brush.radialGradient(
    colors = listOf(DarkGlassColors.glassTint, Color(0x00000000))
)
val HeroHeaderGradient = Brush.horizontalGradient(
    colors = listOf(DarkGlassColors.accentPrimary, DarkGlassColors.accentSecondary)
)