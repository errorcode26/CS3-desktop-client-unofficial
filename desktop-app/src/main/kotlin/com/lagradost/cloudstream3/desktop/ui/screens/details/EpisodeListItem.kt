package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
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
import coil3.request.crossfade
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.desktop.ui.components.applyShadowMultiplier
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MoviePlayCard(
    ep: Episode,
    history: WatchHistory?,
    provider: MainAPI,
    data: LoadResponse,
    onPlay: (Episode) -> Unit,
) {
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
                val context = coil3.compose.LocalPlatformContext.current
                val imageRequest = remember(targetUrl) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(targetUrl)
                        .size(640, 360)
                        .crossfade(true)
                        .build()
                }
                AsyncImage(
                    model = imageRequest,
                    contentDescription = ep.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().run {
                        val noBackdrop = (data as? MovieLoadResponse)?.backgroundPosterUrl == null &&
                            (data as? LiveStreamLoadResponse)?.backgroundPosterUrl == null &&
                            (data as? TvSeriesLoadResponse)?.backgroundPosterUrl == null &&
                            (data as? AnimeLoadResponse)?.backgroundPosterUrl == null
                        if (noBackdrop) this.blur(32.dp) else this
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
                    .clip(CircleShape)
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun EpisodeListItem(
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
    onPlay: (Episode) -> Unit,
    onDownload: ((Episode) -> Unit)? = null,
    onToggleWatched: (Episode, Boolean) -> Unit,
    onRemoveEpisodeWatched: (Episode) -> Unit,
    onMarkPreviousWatched: ((Episode) -> Unit)? = null,
) {
    var isHovered by remember { mutableStateOf(false) }

    val releaseStatus = remember(ep.description) { parseEpisodeReleaseStatus(ep) }
    val lockUnreleasedEpisodes by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
    val isEpisodeLocked = releaseStatus.isUnreleased && lockUnreleasedEpisodes

    val epImg = (provider.fixUrlNull(ep.posterUrl) ?: ep.posterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() }
    val fallbackBackdrop = (uiState?.enrichedBackdropUrl ?: provider.fixUrlNull(data.backgroundPosterUrl) ?: data.backgroundPosterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() }
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

    val isWatched = progress > 0.9f
    val hasStartedPlayback = progress > 0f || (history != null && history.position > 5)
    val shouldHideSpoilers = isAntiSpoiler && !hasStartedPlayback && !isWatched

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

    val rawDesc = ep.description ?: ""
    val formattedDate = releaseStatus.formattedDate
    val cleanDesc = rawDesc.replace(EPISODE_DATE_REGEX, "").trim()
    val hasDesc = cleanDesc.isNotBlank()
    val rating10p = ep.score?.toFloat(10)?.takeIf { it > 0.0f }

    val uiCardOpacity by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.uiCardOpacity.collectAsState()
    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val heroColor = MaterialTheme.colorScheme.primary
    val cardBg = if (isEpisodeLocked) {
        baseColor.copy(alpha = (uiCardOpacity * 0.7f).coerceAtLeast(0.35f))
    } else if (isHovered) {
        baseColor.copy(alpha = (uiCardOpacity + 0.15f).coerceAtMost(1f))
    } else {
        baseColor.copy(alpha = uiCardOpacity)
    }
    val borderColor = if (isEpisodeLocked) {
        Color(0xFFFFB74D).copy(alpha = 0.35f)
    } else if (isHovered) {
        heroColor.copy(alpha = 0.55f)
    } else if (isLatest) {
        heroColor.copy(alpha = 0.35f)
    } else {
        Color.White.copy(alpha = 0.08f)
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardBg,
        border = BorderStroke(1.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(ep, isContextMenuEnabled) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            androidx.compose.ui.input.pointer.PointerEventType.Enter -> isHovered = true
                            androidx.compose.ui.input.pointer.PointerEventType.Exit -> isHovered = false
                            androidx.compose.ui.input.pointer.PointerEventType.Release -> {
                                if (event.button == androidx.compose.ui.input.pointer.PointerButton.Secondary) {
                                    if (!event.changes.any { it.isConsumed }) {
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
                                    }
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(ep, isEpisodeLocked) {
                detectTapGestures(
                    onTap = {
                        if (!isEpisodeLocked) {
                            onPlay(ep)
                        } else {
                            val dateText = releaseStatus.formattedDate ?: releaseStatus.statusBadgeText ?: "a future date"
                            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Episode is unreleased (Scheduled for $dateText)")
                        }
                    }
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Large 16:9 Thumbnail (340dp x 191dp)
            Box(
                modifier = Modifier
                    .width(340.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                val targetUrl = epImg ?: fallbackBackdrop ?: fallbackImg
                if (targetUrl != null) {
                    val context = coil3.compose.LocalPlatformContext.current
                    val imageRequest = remember(targetUrl) {
                        coil3.request.ImageRequest.Builder(context)
                            .data(targetUrl)
                            .crossfade(true)
                            .build()
                    }
                    AsyncImage(
                        model = imageRequest,
                        contentDescription = ep.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .run { if (shouldHideSpoilers) this.blur(16.dp) else this }
                            .run { if (isEpisodeLocked) this.blur(4.dp) else this },
                    )
                }

                // Lock Overlay or Hover Play Icon Overlay
                if (isEpisodeLocked) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.50f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color(0xFF1C1914).copy(alpha = 0.92f),
                            border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.70f)),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    Icons.Default.Lock,
                                    contentDescription = "Unreleased",
                                    tint = Color(0xFFFFB74D),
                                    modifier = Modifier.size(15.dp),
                                )
                                Text(
                                    text = releaseStatus.statusBadgeText ?: "Unreleased",
                                    color = Color(0xFFFFB74D),
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                    ),
                                )
                            }
                        }
                    }
                } else if (isHovered) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(heroColor.copy(alpha = 0.95f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(30.dp),
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // 2. Middle Content (Title, Metadata chips, Paragraph-bounded Synopsis)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Row 1: Title + Gold Star Rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (shouldHideSpoilers) "Episode title hidden" else finalTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = if (isEpisodeLocked) Color.White.copy(alpha = 0.75f) else if (isHovered) heroColor else Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (rating10p != null && !shouldHideSpoilers) {
                        val isAnime = data is com.lagradost.cloudstream3.AnimeLoadResponse ||
                            data.type == com.lagradost.cloudstream3.TvType.Anime ||
                            data.type == com.lagradost.cloudstream3.TvType.AnimeMovie ||
                            data.type == com.lagradost.cloudstream3.TvType.OVA
                        val hasImdb = uiState?.enrichedImdbRating != null ||
                            data.syncData["imdb"]?.startsWith("tt") == true ||
                            data.syncData.values.any { it.startsWith("tt") }

                        val brandLabel = when {
                            isAnime -> "MAL"
                            hasImdb -> "IMDb"
                            else -> "TMDB"
                        }
                        val brandBg = when {
                            isAnime -> Color(0xFF02A9FF)
                            hasImdb -> Color(0xFFF5C518)
                            else -> Color(0xFF01B4E4)
                        }
                        val brandTextColor = if (hasImdb) Color.Black else Color.White

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(brandBg)
                                    .padding(horizontal = 3.5.dp, vertical = 1.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = brandLabel,
                                    color = brandTextColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = (-0.2).sp,
                                )
                            }
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", rating10p),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                }

                // Row 2: Metadata row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ep.episode?.let { epNum ->
                        Text(
                            text = if (ep.season != null) "Season ${ep.season} Episode $epNum" else "Episode $epNum",
                            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp, fontWeight = FontWeight.SemiBold),
                            color = heroColor.copy(alpha = 0.9f),
                        )
                    }

                    if (runTimeStr != null) {
                        Text(
                            text = "•  $runTimeStr",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                            color = Color.White.copy(alpha = 0.60f),
                        )
                    }

                    if (formattedDate != null) {
                        Text(
                            text = "•  $formattedDate",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                            color = if (isEpisodeLocked) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.60f),
                        )
                    }
                }

                // Row 3: Synopsis
                Box(modifier = Modifier.widthIn(max = 750.dp)) {
                    Text(
                        text = when {
                            shouldHideSpoilers -> "Description hidden by Anti-spoiler."
                            hasDesc -> cleanDesc
                            else -> "No description available for this episode."
                        },
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 14.sp, lineHeight = 21.5.sp),
                        color = Color.White.copy(alpha = if (hasDesc && !shouldHideSpoilers) 0.78f else 0.45f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // 3. Right Status Indicator
            if (isWatched && !isEpisodeLocked) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Color(0xFF4CAF50).copy(alpha = 0.92f))
                        .pointerInput(ep) {
                            detectTapGestures {
                                onRemoveEpisodeWatched(ep)
                            }
                        }
                        .padding(8.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}
