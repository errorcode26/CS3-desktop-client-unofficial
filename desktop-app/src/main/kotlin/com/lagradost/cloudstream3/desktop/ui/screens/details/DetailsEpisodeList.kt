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
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import com.lagradost.cloudstream3.desktop.ui.components.applyShadowMultiplier
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun EpisodeCard(
    ep: Episode,
    isLatest: Boolean,
    history: WatchHistory?,
    provider: MainAPI,
    data: LoadResponse,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    isAntiSpoiler: Boolean = false,
    modifier: Modifier = Modifier,
    enableDownloadButtons: Boolean = false,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onDownload: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
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
    val runTimeStr = epRunTime?.let { dur ->
        val mins = if (dur > 1000) dur / 60 else dur
        if (mins >= 60) {
            val h = mins / 60
            val m = mins % 60
            if (m > 0) "${h}h ${m}m" else "${h}h"
        } else {
            "${mins}m"
        }
    }

    val heroColor = MaterialTheme.colorScheme.primary

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
            .shadow(
                elevation = if (isHovered) 16.dp else 6.dp,
                shape = RoundedCornerShape(12.dp),
                spotColor = if (isHovered) heroColor else Color.Black,
                ambientColor = if (isHovered) heroColor else Color.Black,
            )
            .border(
                width = if (isHovered) 1.5.dp else 0.5.dp,
                color = if (isHovered) heroColor else Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
            )
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPlay(ep) },
    ) {
        // Background image
        if (epImg != null || fallbackImg != null) {
            val targetUrl = epImg ?: fallbackImg
            SubcomposeAsyncImage(
                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                    .data(targetUrl)
                    .build(),
                contentDescription = ep.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .run { if (shouldHideSpoilers) this.blur(16.dp) else this }
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                loading = {
                    if (fallbackImg != null) {
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                .data(fallbackImg)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().blur(if (shouldHideSpoilers) 16.dp else 8.dp),
                        )
                    }
                },
                error = {
                    if (fallbackImg != null) {
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                .data(fallbackImg)
                                .build(),
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

        // Gradient scrim: clear -> black at bottom
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

        // Hover play overlay
        AnimatedVisibility(
            visible = isHovered,
            modifier = Modifier.matchParentSize(),
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(150)),
        ) {
            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(heroColor.copy(alpha = 0.7f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp).offset(x = 2.dp),
                    )
                }
            }
        }

        // Anti-spoiler overlay
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

        val rating10p = ep.score?.toFloat(10)?.takeIf { it > 0.0f }

        // Top-Right: Actions and EP number pill
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enableDownloadButtons && onDownload != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.65f))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable { onDownload(ep) }
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            ep.episode?.let { epNum ->
                val epText = if (ep.season != null) "S${ep.season}E$epNum" else "EP $epNum"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(percent = 50))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = epText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }

        // Top-Left: Mark as Watched toggle
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
                        onRemoveEpisodeWatched(ep)
                    } else {
                        // Watch: Mark completely watched
                        onToggleWatched(ep, false)
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

        // Bottom overlay: Title + plot description
        val rawDesc = ep.description ?: ""
        val dateMatch = Regex("\\|\\|DATE:(.*?)\\|\\|").find(rawDesc)
        val releaseDate = dateMatch?.groupValues?.get(1)
        val cleanDesc = rawDesc.replace(Regex("\\|\\|DATE:(.*?)\\|\\|"), "").trim()
        val hasDesc = cleanDesc.isNotBlank()
        // Calculate duration text
        val durationText = if (history != null && history.duration > 0) {
            if (progress > 0f && progress < 1f) {
                val leftSeconds = history.duration - history.position
                val leftMins = leftSeconds / 60L
                if (leftMins >= 60) "${leftMins / 60}h ${leftMins % 60}m left"
                else if (leftMins > 0) "${leftMins}m left"
                else "<1m left"
            } else {
                val totalMins = history.duration / 60L
                if (totalMins >= 60) "${totalMins / 60}h ${totalMins % 60}m" else "${totalMins}m"
            }
        } else if (epRunTime != null) {
            val totalMins = if (epRunTime > 1000) epRunTime / 60 else epRunTime
            if (totalMins >= 60) "${totalMins / 60}h ${totalMins % 60}m" else "${totalMins}m"
        } else {
            null
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 120.dp, bottom = 22.dp), // Safe buffer to prevent overlap with bottom-right pills
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (shouldHideSpoilers) "Episode title hidden" else finalTitle,
                    style = MaterialTheme.typography.titleSmall.copy(fontSize = 21.sp),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isHovered) Color(0xFFB0C4FF) else Color.White,
                    modifier = Modifier.run { if (shouldHideSpoilers) this.blur(2.dp) else this }.weight(1f, fill = false),
                )
                if (rating10p != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Rating",
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", rating10p),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 16.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            Text(
                text = when {
                    shouldHideSpoilers -> "Description hidden."
                    hasDesc -> cleanDesc
                    runTimeStr != null -> "Runtime: $runTimeStr"
                    else -> "No description available."
                },
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 16.sp, lineHeight = 22.sp),
                color = Color.White.copy(alpha = if (hasDesc && !shouldHideSpoilers) 0.72f else 0.42f),
                minLines = 2,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.run { if (shouldHideSpoilers && hasDesc) this.blur(5.dp) else this },
            )
        }

        // Bottom-Right: Duration / Time Left pill and Release Date
        if (releaseDate != null || durationText != null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 22.dp, end = 16.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (releaseDate != null) {
                    Text(
                        text = releaseDate,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 14.sp),
                        color = Color.White.copy(alpha = 0.6f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (durationText != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color.Black.copy(alpha = 0.8f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Bottom progress bar
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .height(3.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(1.5.dp))
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

@Composable
fun MoviePlayCard(ep: Episode, history: WatchHistory?, provider: MainAPI, data: LoadResponse, onPlay: (com.lagradost.cloudstream3.Episode) -> Unit) {
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
            .clickable { onPlay(ep) },
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = elevation,
        shadowElevation = elevation.applyShadowMultiplier(),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val epImg = provider.fixUrlNull(ep.posterUrl)?.takeIf { it.isNotBlank() }
            val fallbackImg = provider.fixUrlNull(data.posterUrl)?.takeIf { it.isNotBlank() }

            if (epImg != null || fallbackImg != null) {
                val targetUrl = epImg ?: fallbackImg
                SubcomposeAsyncImage(
                    model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                        .data(targetUrl)
                        .size(2560, 1440)
                        .build(),
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
                                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(fallbackImg)
                                    .size(2560, 1440)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().blur(8.dp),
                            )
                        }
                    },
                    error = {
                        if (fallbackImg != null) {
                            AsyncImage(
                                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(fallbackImg)
                                    .size(2560, 1440)
                                    .build(),
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
