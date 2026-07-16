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
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import com.lagradost.cloudstream3.*
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun EpisodeCard(
    ep: Episode,
    isLatest: Boolean,
    history: WatchHistory?,
    provider: MainAPI,
    data: LoadResponse,
    isAntiSpoiler: Boolean = false,
    modifier: Modifier = Modifier,
    onPlay: (Triple<MainAPI, String, WatchHistory>) -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isHovered) 1.02f else 1f, animationSpec = tween(180))

    val epImg = provider.fixUrlNull(ep.posterUrl)?.takeIf { it.isNotBlank() }
    val fallbackImg = provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }

    val progress = if (history != null && history.duration > 0) {
        if (PlayerLinkHandler.isCompleted(history.position, history.duration)) {
            1f
        } else {
            (history.position.toFloat() / history.duration.toFloat()).coerceIn(0f, 1f)
        }
    } else {
        0f
    }

    val shouldHideSpoilers = isAntiSpoiler && progress < 0.9f

    val rawTitle = ep.name ?: "Episode ${ep.episode ?: "?"}"
    val titleCleaned = rawTitle
        .replace(Regex("^(?i)(E[0-9]+[\\s\\-:]*)+"), "")
        .replace(Regex("^(?i)(Episode[\\s]*[0-9]+[\\s\\-:]*)+"), "")
        .trim()
    val finalTitle = if (titleCleaned.isBlank()) "Episode ${ep.episode ?: "?"}" else titleCleaned

    val epRunTime = ep.runTime ?: data.duration
    val runTimeStr = epRunTime?.let { if (it > 300) "${it / 60}m" else "${it}m" }

    // Card is a pure 16:9 thumbnail — caller supplies width via modifier (weight for grid)
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
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
            .border(
                width = 1.dp,
                color = if (isHovered) Color.White.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(10.dp),
            )
            .clip(RoundedCornerShape(10.dp))
            .clickable { navigateToPlay(provider, data, ep, onPlay) },
    ) {
        // ── Background image ────────────────────────────────────────────────
        if (epImg != null || fallbackImg != null) {
            SubcomposeAsyncImage(
                model = epImg ?: fallbackImg,
                contentDescription = ep.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .run { if (shouldHideSpoilers) this.blur(16.dp) else this }
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                loading = {
                    if (fallbackImg != null) {
                        AsyncImage(
                            model = fallbackImg,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(if (shouldHideSpoilers) 16.dp else 8.dp),
                        )
                    }
                },
                error = {
                    if (fallbackImg != null) {
                        AsyncImage(
                            model = fallbackImg,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
                        }
                    }
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(40.dp))
            }
        }

        // ── Gradient scrim: clear → black at bottom ──────────────────────────
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.35f to Color.Transparent,
                        0.62f to Color.Black.copy(alpha = 0.60f),
                        1.0f to Color.Black.copy(alpha = 0.97f),
                    ),
                ),
        )

        // ── Hover play overlay ───────────────────────────────────────────────
        AnimatedVisibility(
            visible = isHovered,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.18f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }

        // ── Anti-spoiler overlay ─────────────────────────────────────────────
        if (shouldHideSpoilers && !isHovered) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text("Hidden by Anti-spoiler", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ── Top-Right: EP number pill ────────────────────────────────────────
        ep.episode?.let { epNum ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Color.Black.copy(alpha = 0.75f))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "EP $epNum",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }

        // ── Top-Left: Mark as Watched toggle ─────────────────────────────────
        val isWatched = progress > 0.9f
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
                .clip(CircleShape)
                .background(
                    if (isWatched) {
                        Color(0xFF4CAF50).copy(alpha = 0.85f)
                    } else {
                        Color.Black.copy(alpha = 0.5f)
                    },
                )
                .clickable {
                    if (isWatched) {
                        // Unwatch: Delete history completely to cleanly remove watch status
                        val parentId = DesktopDataStore.watchHistoryId(provider.name, data.url)
                        DesktopDataStore.removeEpisodeWatched(parentId, ep.data)
                    } else {
                        // Watch: Mark completely watched
                        toggleEpisodeWatched(provider, data, ep, isWatched = false)
                    }
                }
                .padding(6.dp),
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = if (isWatched) "Unmark as watched" else "Mark as watched",
                tint = if (isWatched) Color.White else Color.White.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp),
            )
        }

        // ── Bottom overlay: Title + plot description ─────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = if (shouldHideSpoilers) "Episode title hidden" else finalTitle,
                style = MaterialTheme.typography.titleSmall.copy(fontSize = 17.sp),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isHovered) Color(0xFFB0C4FF) else Color.White,
                modifier = Modifier.run { if (shouldHideSpoilers) this.blur(2.dp) else this },
            )
            val hasDesc = !ep.description.isNullOrBlank()
            Text(
                text = when {
                    shouldHideSpoilers -> "Description hidden."
                    hasDesc -> ep.description!!
                    runTimeStr != null -> "Runtime: $runTimeStr"
                    else -> "No description available."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 17.sp),
                color = Color.White.copy(alpha = if (hasDesc && !shouldHideSpoilers) 0.72f else 0.42f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.run { if (shouldHideSpoilers && hasDesc) this.blur(5.dp) else this },
            )
        }

        // ── Bottom progress bar ──────────────────────────────────────────────
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.2f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

fun toggleEpisodeWatched(provider: MainAPI, data: LoadResponse, ep: Episode, isWatched: Boolean) {
    val parentId = DesktopDataStore.watchHistoryId(
        apiName = provider.name,
        showUrl = data.url,
    )
    val saved = DesktopDataStore.getEpisodeWatched(parentId, ep.data)
    val dur = if ((saved?.duration ?: 0L) > 0L) saved!!.duration else 60_000L
    val newPos = if (isWatched) 0L else dur
    val history = WatchHistory(
        parentId = parentId,
        showName = data.name,
        showUrl = data.url,
        apiName = provider.name,
        posterUrl = data.posterUrl,
        episode = ep.episode,
        season = ep.season,
        episodeId = ep.data,
        position = newPos,
        duration = dur,
    )
    DesktopDataStore.setLastWatched(history)
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
            patchedData = patchedData.replaceFirst("{", "{\"title\":\"$titleStr\",")
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
                                modifier = Modifier.fillMaxSize().blur(8.dp),
                            )
                        }
                    },
                    error = {
                        if (fallbackImg != null) {
                            AsyncImage(
                                model = fallbackImg,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    },
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
