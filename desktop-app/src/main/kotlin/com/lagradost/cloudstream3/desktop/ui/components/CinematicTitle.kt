package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.theme.OutfitFontFamily

/**
 * Renders a high-end cinematic typography title for media when a transparent PNG logo is unavailable.
 * Uses geometric Outfit font with a subtle metallic pearl gradient and atmospheric multi-layer backing shadow.
 */
@Composable
fun CinematicTitle(
    text: String,
    modifier: Modifier = Modifier,
    isCompact: Boolean = false,
    fontSize: TextUnit = if (isCompact) 26.sp else 44.sp,
    maxLines: Int = 2,
    textAlign: TextAlign = TextAlign.Start,
) {
    if (text.isBlank()) return

    Text(
        text = text,
        style = TextStyle(
            fontFamily = OutfitFontFamily,
            fontWeight = FontWeight.Black,
            fontSize = fontSize,
            lineHeight = fontSize * 1.12f,
            letterSpacing = (-0.8).sp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFFFFFFFF),
                    Color(0xFFFFFFFF),
                    Color(0xFFF1F5F9),
                    Color(0xFFCBD5E1),
                ),
            ),
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.85f),
                offset = Offset(0f, 4f),
                blurRadius = 14f,
            ),
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = textAlign,
        modifier = modifier,
    )
}
