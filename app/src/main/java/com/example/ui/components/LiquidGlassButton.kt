package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.ui.theme.DesignTokens
import com.example.ui.theme.LocalGlassColors
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurfaceElevated
import com.example.ui.theme.TextPrimary

@Composable
fun LiquidGlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    content: @Composable RowScope.() -> Unit
) {
    val colors = LocalGlassColors.current
    val containerColor = if (isPrimary) colors.accentPrimary else colors.surfaceElevated
    val contentColor = if (isPrimary) androidx.compose.material3.MaterialTheme.colorScheme.onPrimary else colors.textPrimary
    val borderColor = if (isPrimary) colors.accentPrimary else colors.glassHighlight

    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = DesignTokens.MinTouchTarget),
        enabled = enabled,
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = colors.surfaceElevated.copy(alpha = 0.5f),
            disabledContentColor = colors.textPrimary.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.8f)),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        interactionSource = remember { MutableInteractionSource() },
        content = content
    )
}
