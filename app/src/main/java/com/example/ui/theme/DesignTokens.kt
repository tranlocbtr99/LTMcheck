package com.example.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iOS 26 Liquid Glass — Design Tokens
 * Centralized, adjustable globally. All components should consume these.
 */
object DesignTokens {

    // Spacing (4dp base grid)
    val SpaceMicro: Dp = 4.dp
    val SpaceSmall: Dp = 8.dp
    val SpaceMedium: Dp = 12.dp
    val SpaceStandard: Dp = 16.dp
    val SpaceGroup: Dp = 20.dp
    val SpaceScreen: Dp = 24.dp
    val SpaceSection: Dp = 32.dp

    // Corner Radius
    val RadiusSmall: Dp = 10.dp
    val RadiusMedium: Dp = 16.dp
    val RadiusLarge: Dp = 24.dp
    val RadiusXLarge: Dp = 32.dp
    val RadiusCapsule: Dp = 999.dp

    // Motion (ms) — reference values, tune as needed
    const val MotionMicroMs: Int = 140
    const val MotionComponentMs: Int = 240
    const val MotionNavigationMs: Int = 360

    // Typography scale (sp)
    val TypeLargeTitle: Float = 32f
    val TypeScreenTitle: Float = 26f
    val TypeSectionTitle: Float = 20f
    val TypeBody: Float = 16f
    val TypeSecondary: Float = 14f
    val TypeCaption: Float = 12f

    // Minimum touch target (Android accessibility)
    val MinTouchTarget: Dp = 48.dp
}
