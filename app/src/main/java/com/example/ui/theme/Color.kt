package com.example.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// iOS-style Liquid Glass Core Palette (dark mode)
val GlassBackground = Color(0xFF000000)
val GlassSurface = Color(0x1FFFFFFF)
val GlassSurfaceElevated = Color(0x29FFFFFF)
val GlassBorder = Color(0x33FFFFFF)
val GlassBorderHighlight = Color(0x66FFFFFF)

// iOS System Accents
val CyanNeon = Color(0xFF0A84FF)
val BlueNeon = Color(0xFF5AC8FA)
val PurpleNeon = Color(0xFFBF5AF2)

// Text Colors (iOS labels)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0x99EBEBF5)
val TextMuted = Color(0x66EBEBF5)

// Verdict Accents (iOS system colors)
val VerdictTrueColor = Color(0xFF30D158)
val VerdictFalseColor = Color(0xFFFF453A)
val VerdictPartialColor = Color(0xFFFF9F0A)
val VerdictMisleadingColor = Color(0xFFFF9F0A)
val VerdictInsufficientColor = Color(0xFFBF5AF2)
val VerdictUnverifiableColor = Color(0x99EBEBF5)

// Liquid Glass Gradients
val LiquidGlassGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0x26FFFFFF),
        Color(0x0DFFFFFF)
    )
)

val LiquidGlowGradient = Brush.radialGradient(
    colors = listOf(
        Color(0x330A84FF),
        Color(0x00000000)
    )
)

val HeroHeaderGradient = Brush.horizontalGradient(
    colors = listOf(
        Color(0xFF0A84FF),
        Color(0xFF5AC8FA)
    )
)