package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
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
    val scale by animateFloatAsState(if (isHovered) 1.02f else 1f, animationSpec = tween(180))
    val overlayAlpha by animateFloatAsState(if (isHovered) 1f else 0f, tween(180))

    val epImg = provider.fixUrlNull(ep.posterUrl)?.takeIf { it.isNotBlank() }
    val fallbackImg = provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }

    val rawTitle = ep.name ?: "Episode ${ep.episode ?: "?"}"
    val titleCleaned = rawTitle
        .replace(Regex("^(?i)(E[0-9]+[\\s\\-:]*)+"), "")
        .replace(Regex("^(?i)(Episode[\\s]*[0-9]+[\\s\\-:]*)+"), "")
        .trim()
    val finalTitle = if (titleCleaned.isBlank()) "Episode ${ep.episode ?: "?"}" else titleCleaned

    val progress = if (history != null && history.duration > 0) {
        if (PlayerLinkHandler.isCompleted(history.position, history.duration)) 1f
        else (history.position.toFloat() / history.duration.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val epRunTime = ep.runTime ?: data.duration
    val runTimeStr = epRunTime?.let { if (it > 300) "${it / 60}m" else "${it}m" }

    Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Enter -> isHovered = true
                            androidx.compose.ui.input.pointer.PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .scale(scale)
            .clip(RoundedCornerShape(12.dp))
            .clickable { navigateToPlay(provider, data, ep, onPlay) },
    ) {
        Column {
            // ── 16:9 Thumbnail ──────────────────────────────────────────────────────
            Box(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            ) {
                // Background image
                if (epImg != null || fallbackImg != null) {
                    SubcomposeAsyncImage(
                        model = epImg ?: fallbackImg,
                        contentDescription = ep.name,
                        contentScale = ContentScale.Crop,
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        loading = {
                            if (fallbackImg != null) {
                                AsyncImage(
                                    model = fallbackImg,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = androidx.compose.ui.Modifier.fillMaxSize().blur(8.dp)
                                )
                            }
                        },
                        error = {
                            if (fallbackImg != null) {
                                AsyncImage(
                                    model = fallbackImg,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = androidx.compose.ui.Modifier.fillMaxSize()
                                )
                            } else {
                                Box(
                                    modifier = androidx.compose.ui.Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = androidx.compose.ui.Modifier.size(40.dp))
                                }
                            }
                        }
                    )
                } else {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = androidx.compose.ui.Modifier.size(40.dp))
                    }
                }

                // Bottom gradient scrim for readability
                Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.5f to Color.Transparent,
                                1.0f to Color.Black.copy(alpha = 0.65f),
                            )
                        )
                )

                // Top-left: Episode number badge
                ep.episode?.let { epNum ->
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (isLatest) MaterialTheme.colorScheme.primary
                                else Color.Black.copy(alpha = 0.72f)
                            )
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "E$epNum",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                // Top-right: runtime + rating
                Row(
                    modifier = androidx.compose.ui.Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ep.score?.takeIf { it.toFloat(10) > 0f }?.let { score ->
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text("⭐ %.1f".format(score.toFloat(10)), style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                    runTimeStr?.let {
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.72f))
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Hover play button (matches WatchHistoryCard exactly)
                androidx.compose.animation.AnimatedVisibility(
                    visible = isHovered,
                    modifier = androidx.compose.ui.Modifier.matchParentSize(),
                    enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
                    exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)),
                ) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .size(56.dp)
                                .background(Color.White.copy(alpha = 0.25f), CircleShape)
                                .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = androidx.compose.ui.Modifier.size(32.dp),
                            )
                        }
                    }
                }

                // Bottom progress bar (inline, at very bottom of thumbnail)
                if (progress > 0f) {
                    Box(
                        modifier = androidx.compose.ui.Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                }
            }

            // ── Text block below thumbnail ───────────────────────────────────────
            Column(
                modifier = androidx.compose.ui.Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.0f))
                    .padding(horizontal = 2.dp, vertical = 10.dp),
            ) {
                Text(
                    text = finalTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isLatest) FontWeight.ExtraBold else FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isHovered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                ep.description?.let {
                    Spacer(modifier = androidx.compose.ui.Modifier.height(3.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
