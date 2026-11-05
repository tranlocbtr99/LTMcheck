package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderHighlight
import com.example.ui.theme.GlassSurface

@Composable
fun LiquidGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderColor: Color = GlassBorder,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = GlassSurface,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = ripple(color = GlassBorderHighlight),
            onClick = onClick
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .shadow(8.dp, shape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
            .clip(shape)
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        backgroundColor.copy(alpha = 0.50f),
                        backgroundColor.copy(alpha = 0.20f)
                    )
                )
            )
            .border(
                BorderStroke(
                    0.5.dp,
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0x99FFFFFF),
                            Color(0x22FFFFFF)
                        )
                    )
                ),
                shape = shape
            )
            .then(clickableModifier)
            .padding(16.dp),
        content = content
    )
}
