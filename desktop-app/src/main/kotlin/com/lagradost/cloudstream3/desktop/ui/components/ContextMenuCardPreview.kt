package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

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
    // 1. Poster / Thumbnail Surface
    Surface(
        modifier = Modifier
            .width(posterWidth)
            .height(posterHeight),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF18181A),
        tonalElevation = 8.dp,
        shadowElevation = 16.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
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

            // Release badge or date (Top-Left)
            if (epDate != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Text(
                        text = epDate,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            // Runtime / Score Badge (Bottom-Right)
            if (epRuntimeText != null) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = if (progress > 0f) 12.dp else 10.dp, end = 10.dp),
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.75f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                ) {
                    Text(
                        text = epRuntimeText,
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
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

            // Progress bar at bottom
            if (progress > 0f && !isWatched) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.White.copy(alpha = 0.2f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                            .fillMaxHeight()
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
        if (subtitleText.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = subtitleText,
                color = Color.White.copy(alpha = 0.70f),
                fontSize = 16.sp,
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
