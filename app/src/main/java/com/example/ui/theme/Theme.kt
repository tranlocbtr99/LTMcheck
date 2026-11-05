package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkGlassColors.accentPrimary,
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFBAE6FD),
    secondary = DarkGlassColors.accentTertiary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF581C87),
    onSecondaryContainer = Color(0xFFF3E8FF),
    background = DarkGlassColors.backgroundPrimary,
    onBackground = DarkGlassColors.textPrimary,
    surface = DarkGlassColors.backgroundSecondary,
    onSurface = DarkGlassColors.textPrimary,
    surfaceVariant = Color(0xFF1E293B),
    onSurfaceVariant = DarkGlassColors.textSecondary,
    outline = DarkGlassColors.borderSubtle,
    outlineVariant = DarkGlassColors.glassHighlight
)

private val LightColorScheme = lightColorScheme(
    primary = LightGlassColors.accentPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E4FF),
    onPrimaryContainer = Color(0xFF001A33),
    secondary = LightGlassColors.accentTertiary,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEADDFF),
    onSecondaryContainer = Color(0xFF21005D),
    background = LightGlassColors.backgroundPrimary,
    onBackground = LightGlassColors.textPrimary,
    surface = LightGlassColors.backgroundSecondary,
    onSurface = LightGlassColors.textPrimary,
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = LightGlassColors.textSecondary,
    outline = LightGlassColors.borderSubtle,
    outlineVariant = LightGlassColors.glassHighlight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val glassColors = if (darkTheme) DarkGlassColors else LightGlassColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            val bg = if (darkTheme) DarkGlassColors.backgroundPrimary else LightGlassColors.backgroundPrimary
            window.statusBarColor = bg.toArgb()
            window.navigationBarColor = bg.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalGlassColors provides glassColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}