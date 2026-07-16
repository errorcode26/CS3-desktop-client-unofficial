package com.lagradost.cloudstream3.desktop.ui.screens.player.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState

@Composable
fun PausedDetailsOverlay(
    playerState: PlayerState,
    launchData: VideoLaunchData,
    modifier: Modifier = Modifier,
) {
    val isPaused by playerState.isPaused.collectAsState()
    val showControls by playerState.showControls.collectAsState()

    AnimatedVisibility(
        visible = isPaused && showControls,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        val loadResponse = launchData.loadResponse

        // Smooth Vignette background for readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.horizontalGradient(
                        0.0f to Color.Black.copy(alpha = 0.85f),
                        0.25f to Color.Black.copy(alpha = 0.6f),
                        0.60f to Color.Black.copy(alpha = 0.1f),
                        1.0f to Color.Transparent
                    )
                )
        )
        
        Column(
            modifier = Modifier
                .width(500.dp)
                .padding(start = 32.dp, top = 64.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.Start
        ) {
            Text(
                text = "You Are Watching",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 2.sp,
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // Attempt to load custom logo PNG if the provider supports it.
            val logoUrl = loadResponse?.logoUrl
            if (!logoUrl.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = logoUrl,
                    contentDescription = "Show Logo",
                    modifier = Modifier.heightIn(max = 140.dp).fillMaxWidth(0.8f),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    alignment = Alignment.CenterStart
                )
            } else {
                // Text Title Fallback
                Text(
                    text = loadResponse?.name?.uppercase() ?: launchData.title?.uppercase() ?: "UNKNOWN",
                    color = Color(0xFFFFD700), // Yellow/Gold tint
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 48.sp,
                    letterSpacing = (-1).sp,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Episode Subtitle
            val epData = launchData.history
            val seasonStr = if (epData.season != null) "S${epData.season}" else ""
            val epStr = if (epData.episode != null) "E${epData.episode}" else ""
            val combinedMeta = listOf(seasonStr, epStr).filter { it.isNotBlank() }.joinToString(" ")
            
            var episodeTitle = launchData.title?.substringAfter(" - ") ?: epData.showName
            // If the scraped title is literally just "S1E1" or similar, don't show it redundantly
            val redundantName = "S${epData.season}E${epData.episode}"
            if (episodeTitle.equals(redundantName, ignoreCase = true) || episodeTitle.equals(combinedMeta.replace(" ", ""), ignoreCase = true)) {
                episodeTitle = ""
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (combinedMeta.isNotBlank()) {
                    Text(
                        text = combinedMeta,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    if (episodeTitle.isNotBlank()) {
                        Text(
                            text = " • ",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 16.sp,
                        )
                    }
                }
                if (episodeTitle.isNotBlank()) {
                    Text(
                        text = episodeTitle,
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            if (loadResponse?.plot?.isNotBlank() == true) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = loadResponse.plot ?: "",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
