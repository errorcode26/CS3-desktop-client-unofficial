package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

private fun Modifier.contextCardDepth(
    shape: RoundedCornerShape,
    edgeStrength: Float = 28f,
    sheenStrength: Float = 10f,
): Modifier {
    val edgeTop = edgeStrength.coerceIn(0f, 100f) / 100f
    val sheen = sheenStrength.coerceIn(0f, 100f) / 100f

    val withEdge = if (edgeTop > 0f) {
        border(
            width = 1.dp,
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.White.copy(alpha = edgeTop),
                    Color.White.copy(alpha = edgeTop * 0.45f),
                    Color.White.copy(alpha = edgeTop * 0.15f),
                ),
            ),
            shape = shape,
        )
    } else {
        this
    }

    return if (sheen > 0f) {
        withEdge.drawWithContent {
            drawContent()
            val sheenHeight = size.height * 0.22f
            if (sheenHeight > 0f) {
                val outline = shape.createOutline(size, layoutDirection, this)
                val shapePath = when (outline) {
                    is Outline.Rectangle -> Path().apply { addRect(outline.rect) }
                    is Outline.Rounded -> Path().apply { addRoundRect(outline.roundRect) }
                    is Outline.Generic -> outline.path
                }
                clipPath(shapePath) {
                    drawRect(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                Color.White.copy(alpha = sheen),
                                Color.Transparent,
                            ),
                            startY = 0f,
                            endY = sheenHeight,
                        ),
                        size = Size(size.width, sheenHeight),
                    )
                }
            }
        }
    } else {
        withEdge
    }
}

@Composable
internal fun ContextMenuCardPreview(
    isEpisode: Boolean,
    posterUrl: String?,
    epDate: String?,
    epRuntimeText: String?,
    isWatched: Boolean,
    progress: Float,
    posterWidth: Dp,
    posterHeight: Dp,
    actionCardWidth: Dp,
    titleText: String?,
    subtitleText: String,
    epCleanPlot: String?,
    isAntiSpoiler: Boolean,
) {
    // Proportional rounded rectangle geometry: landscape episodes use tighter radii, portrait posters use deeper radii
    val cardCornerRadius = remember(isEpisode, posterHeight) {
        if (isEpisode) {
            (posterHeight * 0.055f).coerceIn(14.dp, 16.dp)
        } else {
            (posterHeight * 0.052f).coerceIn(18.dp, 22.dp)
        }
    }
    val cardShape = remember(cardCornerRadius) { RoundedCornerShape(cardCornerRadius) }

    // 1. Poster / Thumbnail Surface with directional bevel lighting and top glass sheen
    Surface(
        modifier = Modifier
            .width(posterWidth)
            .height(posterHeight)
            .shadow(
                elevation = 22.dp,
                shape = cardShape,
                spotColor = Color.Black.copy(alpha = 0.70f),
                ambientColor = Color.Black.copy(alpha = 0.40f),
            )
            .contextCardDepth(cardShape),
        shape = cardShape,
        color = Color(0xFF18181A),
        tonalElevation = 8.dp,
        shadowElevation = 0.dp,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (!posterUrl.isNullOrBlank()) {
                AsyncImage(
                    model = posterUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Watched Check Badge (Top-Right)
            if (isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE24A4A)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // Floating Pill Progress Bar (inset organically from borders with rounded caps)
            if (progress > 0f && !isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    // 2. Standalone Centered Title, Subtitle & Plot Summary
    if (!titleText.isNullOrBlank()) {
        Text(
            text = titleText,
            color = Color.White,
            fontSize = if (isEpisode) 24.sp else 23.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 29.sp,
            letterSpacing = (-0.3).sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .widthIn(max = posterWidth.coerceAtLeast(actionCardWidth).coerceAtLeast(360.dp))
                .padding(horizontal = 12.dp),
        )
        val formattedDate = remember(epDate) {
            epDate?.trim()?.let { raw ->
                try {
                    val parsed = java.time.LocalDate.parse(raw)
                    parsed.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.ENGLISH))
                } catch (_: Exception) {
                    raw
                }
            }
        }

        val displaySubtitle = remember(subtitleText, epRuntimeText, formattedDate, isEpisode) {
            if (isEpisode) {
                listOfNotNull(
                    subtitleText.takeIf { it.isNotBlank() },
                    epRuntimeText.takeIf { !it.isNullOrBlank() },
                    formattedDate.takeIf { !it.isNullOrBlank() },
                ).joinToString(" • ")
            } else {
                subtitleText
            }
        }

        if (displaySubtitle.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = displaySubtitle,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .widthIn(max = posterWidth.coerceAtLeast(actionCardWidth).coerceAtLeast(360.dp))
                    .padding(horizontal = 12.dp),
            )
        }

        // Full Episode Plot Summary
        if (isEpisode && !epCleanPlot.isNullOrBlank()) {
            var isSpoilerRevealed by remember { mutableStateOf(!isAntiSpoiler || isWatched) }
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                modifier = Modifier
                    .widthIn(max = posterWidth.coerceAtMost(520.dp))
                    .clickable(enabled = isAntiSpoiler && !isSpoilerRevealed) {
                        isSpoilerRevealed = true
                    },
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.05f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (!isSpoilerRevealed) {
                        Text(
                            text = "⚠️ Spoiler Hidden (Click to reveal synopsis)",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(
                            text = epCleanPlot,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            maxLines = 5,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                        )
                    }
                }
            }
        }
    }
}
