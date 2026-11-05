package com.example.ui.theme

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
    surfacePrimary = Color(0x1FFFFFFF),
    surfaceElevated = Color(0x29FFFFFF),
    textPrimary = Color(0xFFFFFFFF),
    textSecondary = Color(0x99EBEBF5),
    textMuted = Color(0x66EBEBF5),
    accentPrimary = Color(0xFF0A84FF),
    accentSecondary = Color(0xFF5AC8FA),
    accentTertiary = Color(0xFFBF5AF2),
    borderSubtle = Color(0x33FFFFFF),
    glassTint = Color(0x1A0A84FF),
    glassHighlight = Color(0x66FFFFFF),
    success = Color(0xFF30D158),
    warning = Color(0xFFFF9F0A),
    error = Color(0xFFFF453A)
)

val LightGlassColors = GlassColors(
    backgroundPrimary = Color(0xFFF2F2F7),
    backgroundSecondary = Color(0xFFFFFFFF),
    surfacePrimary = Color(0x1A000000),
    surfaceElevated = Color(0x26000000),
    textPrimary = Color(0xFF000000),
    textSecondary = Color(0x993C3C43),
    textMuted = Color(0x663C3C43),
    accentPrimary = Color(0xFF007AFF),
    accentSecondary = Color(0xFF5AC8FA),
    accentTertiary = Color(0xFFAF52DE),
    borderSubtle = Color(0x33000000),
    glassTint = Color(0x1A007AFF),
    glassHighlight = Color(0x66FFFFFF),
    success = Color(0xFF34C759),
    warning = Color(0xFFFF9500),
    error = Color(0xFFFF3B30)
)

val LocalGlassColors = staticCompositionLocalOf { DarkGlassColors }

val GlassBackground = DarkGlassColors.backgroundPrimary
val GlassSurface = DarkGlassColors.surfacePrimary
val GlassSurfaceElevated = DarkGlassColors.surfaceElevated
val GlassBorder = DarkGlassColors.borderSubtle
val GlassBorderHighlight = DarkGlassColors.glassHighlight
val CyanNeon = DarkGlassColors.accentPrimary
val BlueNeon = DarkGlassColors.accentSecondary
val PurpleNeon = DarkGlassColors.accentTertiary
val TextPrimary = DarkGlassColors.textPrimary
val TextSecondary = DarkGlassColors.textSecondary
val TextMuted = DarkGlassColors.textMuted
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