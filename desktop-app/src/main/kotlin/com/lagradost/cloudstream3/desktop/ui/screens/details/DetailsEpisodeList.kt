package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.lagradost.cloudstream3.*
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun EpisodeCard(ep: Episode, isLatest: Boolean, history: WatchHistory?, provider: MainAPI, data: LoadResponse, onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isHovered) 1.02f else 1f, animationSpec = tween(200))
    val elevation by animateDpAsState(if (isHovered) 8.dp else 2.dp, animationSpec = tween(200))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        isHovered = event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                            isHovered = false
                        }
                    }
                }
            }
            .scale(scale)
            .clickable { navigateToPlay(provider, data, ep, onPlay) },
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
        ) {
            val epImg = provider.fixUrlNull(ep.posterUrl)?.takeIf { it.isNotBlank() }
            val fallbackImg = provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }

            Box {
                if (epImg != null || fallbackImg != null) {
                    SubcomposeAsyncImage(
                        model = epImg ?: fallbackImg,
                        contentDescription = ep.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        loading = {
                            if (fallbackImg != null) {
                                AsyncImage(
                                    model = fallbackImg,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize().blur(8.dp)
                                )
                            }
                        },
                        error = {
                            if (fallbackImg != null) {
                                AsyncImage(
                                    model = fallbackImg,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                // Top Left: Episode Number Badge
                ep.episode?.let { epNum ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.7f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "E$epNum",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Bottom Right: Runtime & Rating
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ep.score?.let { score ->
                        val ratingText = "⭐ %.1f".format(score.toFloat(10))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = ratingText,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    val epRunTime = ep.runTime ?: data.duration
                    epRunTime?.let { rt ->
                        val runTimeStr = if (rt > 300) "${rt / 60}m" else "${rt}m"
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = runTimeStr,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            val rawTitle = ep.name ?: "Episode ${ep.episode ?: "?"}"

            // Clean up titles that have generic prefixes like "E1 - ", "Episode 1: ", etc.
            val titleCleaned = rawTitle
                .replace(Regex("^(?i)(E[0-9]+[\\s\\-:]*)+"), "")
                .replace(Regex("^(?i)(Episode[\\s]*[0-9]+[\\s\\-:]*)+"), "")
                .trim()
            val finalTitle = if (titleCleaned.isBlank()) "Episode ${ep.episode ?: "?"}" else titleCleaned

            Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = finalTitle,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = if (isLatest) FontWeight.ExtraBold else FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            ep.description?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (history != null && history.duration > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                com.lagradost.cloudstream3.desktop.ui.components.WatchProgressIndicator(
                    position = history.position,
                    duration = history.duration,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

fun navigateToPlay(provider: MainAPI, data: LoadResponse, ep: Episode, onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit) {
    val parentId = DesktopDataStore.watchHistoryId(
        apiName = provider.name,
        showUrl = data.url,
    )
    val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)
    val resumePos = PlayerLinkHandler.resumeStartSeconds(
        saved?.position ?: 0L,
        saved?.duration ?: 0L,
    )
    val history = WatchHistory(
        parentId = parentId,
        showName = data.name,
        showUrl = data.url,
        apiName = provider.name,
        posterUrl = data.posterUrl,
        episode = ep.episode,
        season = ep.season,
        episodeId = ep.data,
        position = resumePos,
        duration = saved?.duration ?: 0L,
    )
    var patchedData = ep.data
    if (patchedData.startsWith("{") && patchedData.endsWith("}")) {
        if (!patchedData.contains("\"title\"")) {
            val titleStr = data.name.replace("\"", "\\\"")
            patchedData = patchedData.replaceFirst("{", "{\"title\":\"\$titleStr\",")
        }
        if (!patchedData.contains("\"tvtype\"")) {
            patchedData = patchedData.replaceFirst("{", "{\"tvtype\":\"\",")
        }
    }
    onPlay(Triple(provider, patchedData, history))
}

@Composable
fun MoviePlayCard(ep: Episode, history: WatchHistory?, provider: MainAPI, data: LoadResponse, onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isHovered) 1.02f else 1f, animationSpec = tween(200))
    val elevation by animateDpAsState(if (isHovered) 12.dp else 4.dp, animationSpec = tween(200))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 300.dp, max = 500.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        isHovered = event.type == androidx.compose.ui.input.pointer.PointerEventType.Enter
                        if (event.type == androidx.compose.ui.input.pointer.PointerEventType.Exit) {
                            isHovered = false
                        }
                    }
                }
            }
            .scale(scale)
            .clickable { navigateToPlay(provider, data, ep, onPlay) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = elevation,
        shadowElevation = elevation,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val epImg = provider.fixUrlNull(ep.posterUrl)?.takeIf { it.isNotBlank() }
            val fallbackImg = provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }
            
            if (epImg != null || fallbackImg != null) {
                SubcomposeAsyncImage(
                    model = epImg ?: fallbackImg,
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().run {
                        val noBackdrop = (data as? MovieLoadResponse)?.backgroundPosterUrl == null &&
                            (data as? LiveStreamLoadResponse)?.backgroundPosterUrl == null &&
                            (data as? TvSeriesLoadResponse)?.backgroundPosterUrl == null &&
                            (data as? AnimeLoadResponse)?.backgroundPosterUrl == null
                        if (noBackdrop) this.blur(100.dp) else this
                    },
                    loading = {
                        if (fallbackImg != null) {
                            AsyncImage(
                                model = fallbackImg,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(8.dp)
                            )
                        }
                    },
                    error = {
                        if (fallbackImg != null) {
                            AsyncImage(
                                model = fallbackImg,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                )
                // Gradient overlay so text is readable
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f)),
                                startY = 100f,
                            ),
                        ),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }

            // Play Icon centered
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(80.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onPrimary)
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(32.dp),
            ) {
                val canResume = history != null && PlayerLinkHandler.resumeStartSeconds(history.position, history.duration) > 0
                val actionText = if (canResume) "Resume Playing" else "Play"

                Text(
                    text = actionText,
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val runTime = ep.runTime ?: data.duration
                runTime?.let { rt ->
                    val runTimeStr = if (rt > 300) "${rt / 60}m" else "${rt}m"
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = runTimeStr,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.7f),
                    )
                }

                if (history != null && history.duration > 0) {
                    Spacer(modifier = Modifier.height(16.dp))
                    com.lagradost.cloudstream3.desktop.ui.components.WatchProgressIndicator(
                        position = history.position,
                        duration = history.duration,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
