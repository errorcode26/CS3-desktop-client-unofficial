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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun EpisodeCard(
    ep: Episode,
    isLatest: Boolean,
    history: WatchHistory?,
    provider: MainAPI,
    data: LoadResponse,
    uiState: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsUiState?,
    isAntiSpoiler: Boolean = false,
    thumbnailVersion: Int = 0,
    modifier: Modifier = Modifier,
    enableDownloadButtons: Boolean = false,
    isContextMenuEnabled: Boolean = true,
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onDownload: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
    onMarkPreviousWatched: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
) {
    var isHovered by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (isHovered && isContextMenuEnabled) 1.02f else 1f, animationSpec = tween(180))

    // thumbnailVersion is intentionally read here so Compose re-evaluates epImg when episode
    // thumbnails are enriched in-place (plain field mutations don't trigger recompose otherwise).
    @Suppress("UNUSED_EXPRESSION")
    thumbnailVersion
    val epImg = (provider.fixUrlNull(ep.posterUrl) ?: ep.posterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() }
    val fallbackImg = (provider.fixUrlNull(data.posterUrl) ?: data.posterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() }

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

    val rawDesc = ep.description ?: ""
    val dateMatch = Regex("\\|\\|DATE:(.*?)\\|\\|").find(rawDesc)
    val releaseDate = dateMatch?.groupValues?.get(1)
    val formattedDate = releaseDate?.let { raw ->
        try {
            val inFormat = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            val outFormat = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
            val parsed = inFormat.parse(raw)
            if (parsed != null) outFormat.format(parsed) else raw
        } catch (e: Exception) {
            raw
        }
    }
    val cleanDesc = rawDesc.replace(Regex("\\|\\|DATE:(.*?)\\|\\|"), "").trim()
    val hasDesc = cleanDesc.isNotBlank()

    val durationText = if (history != null && history.duration > 0) {
        if (progress > 0f && progress < 1f) {
            val leftSeconds = history.duration - history.position
            val leftMins = leftSeconds / 60L
            if (leftMins >= 60) {
                "${leftMins / 60}h ${leftMins % 60}m left"
            } else if (leftMins > 0) {
                "${leftMins}m left"
            } else {
                "<1m left"
            }
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

    val rating10p = ep.score?.toFloat(10)?.takeIf { it > 0.0f }
    val isWatched = progress > 0.9f

    // 16:9 On-Thumbnail Overlay Card (Clean Studio Design)
    Box(
        modifier = modifier
            .aspectRatio(16f / 9f)
            .pointerInput(ep, isContextMenuEnabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Enter -> isHovered = true
                            androidx.compose.ui.input.pointer.PointerEventType.Exit -> isHovered = false
                            androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                if (event.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                                    if (isContextMenuEnabled) {
                                        com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState.showForEpisode(
                                            episode = ep,
                                            loadResponse = data,
                                            history = history,
                                            provider = provider,
                                            isAntiSpoiler = isAntiSpoiler,
                                            enableDownloadButtons = enableDownloadButtons,
                                            onPlay = onPlay,
                                            onDownload = onDownload,
                                            onToggleWatched = onToggleWatched,
                                            onRemoveEpisodeWatched = onRemoveEpisodeWatched,
                                            onMarkPreviousWatched = onMarkPreviousWatched,
                                        )
                                    }
                                } else if (event.button == androidx.compose.ui.input.pointer.PointerButton.Primary) {
                                    onPlay(ep)
                                }
                            }
                        }
                    }
                }
            }
            .scale(scale)
            .shadow(
                elevation = if (isHovered && isContextMenuEnabled) 16.dp else 6.dp,
                shape = RoundedCornerShape(16.dp),
                spotColor = if (isHovered && isContextMenuEnabled) heroColor else Color.Black,
                ambientColor = if (isHovered && isContextMenuEnabled) heroColor else Color.Black,
            )
            .border(
                width = if (isHovered && isContextMenuEnabled) 1.5.dp else 0.5.dp,
                color = if (isHovered && isContextMenuEnabled) heroColor else Color.White.copy(alpha = 0.18f),
                shape = RoundedCornerShape(16.dp),
            )
            .clip(RoundedCornerShape(16.dp)),
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

        // Subtle bottom gradient scrim (starts at lower 50%, gentle soft shadow)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.50f to Color.Transparent,
                        0.70f to Color.Black.copy(alpha = 0.35f),
                        0.88f to Color.Black.copy(alpha = 0.65f),
                        1.0f to Color.Black.copy(alpha = 0.82f),
                    ),
                ),
        )

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

        // Top-Left: Gold Star Rating Badge
        if (rating10p != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(0.5.dp, Color(0xFFFFD700).copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", rating10p),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                        ),
                        color = Color(0xFFFFD700),
                    )
                }
            }
        }

        // Top-Right: Mark as Watched toggle + optional Download button
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (enableDownloadButtons && onDownload != null) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                        .clickable { onDownload(ep) }
                        .padding(7.dp),
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(
                        if (isWatched) {
                            Color(0xFF4CAF50).copy(alpha = 0.90f)
                        } else {
                            Color.Black.copy(alpha = 0.45f)
                        },
                    )
                    .clickable {
                        if (isWatched) {
                            onRemoveEpisodeWatched(ep)
                        } else {
                            onToggleWatched(ep, true)
                        }
                    }
                    .padding(6.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = if (isWatched) "Unmark as watched" else "Mark as watched",
                    tint = if (isWatched) Color.White else Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.size(14.dp),
                )
            }
        }

        // Bottom overlay: Episode badge, Title, Synopsis & Metadata in a clean safe zone
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = if (progress > 0f) 22.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // Row 0: Episode Code Badge (e.g. S1E1 / EP 1)
            ep.episode?.let { epNum ->
                val epText = if (ep.season != null) "S${ep.season}E$epNum" else "EP $epNum"
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.50f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = epText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                        ),
                        color = Color.White.copy(alpha = 0.95f),
                    )
                }
            }

            // Row 1: Full-Width Episode Title with refined shadow
            Text(
                text = if (shouldHideSpoilers) "Episode title hidden" else finalTitle,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 19.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        blurRadius = 6f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                    ),
                ),
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (isHovered) Color(0xFFB0C4FF) else Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .run { if (shouldHideSpoilers) this.blur(2.dp) else this },
            )

            // Subtle breathing room between Title and Description
            Spacer(modifier = Modifier.height(2.dp))

            // Row 2: Synopsis / Plot with comfortable line height and subtle shadow
            Text(
                text = when {
                    shouldHideSpoilers -> "Description hidden."
                    hasDesc -> cleanDesc
                    runTimeStr != null -> "Runtime: $runTimeStr"
                    else -> "No description available."
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 14.5.sp,
                    lineHeight = 20.5.sp,
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = Color.Black.copy(alpha = 0.85f),
                        blurRadius = 5f,
                        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                    ),
                ),
                color = Color.White.copy(alpha = if (hasDesc && !shouldHideSpoilers) 0.95f else 0.70f),
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.run { if (shouldHideSpoilers && hasDesc) this.blur(5.dp) else this },
            )

            // Row 3: Duration on Left & Air Date on Right
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 18.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (durationText != null) {
                    Text(
                        text = durationText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.5.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.85f),
                                blurRadius = 4f,
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                            ),
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                if (formattedDate != null) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 12.5.sp,
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.Black.copy(alpha = 0.85f),
                                blurRadius = 4f,
                                offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                            ),
                        ),
                        color = Color.White.copy(alpha = 0.85f),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        // Bottom progress bar (floating higher inside the card)
        if (progress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                    .height(4.5.dp)
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(Color.White.copy(alpha = 0.22f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(2.5.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
            val epImg = (provider.fixUrlNull(ep.posterUrl) ?: ep.posterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() }
            val fallbackImg = (provider.fixUrlNull(data.posterUrl) ?: data.posterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() }

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
