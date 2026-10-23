package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.common.storage.WatchHistory

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
    lockUnreleasedEpisodes: Boolean = true,
    uiCardOpacity: Float = 0.65f,
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

    val currentHistory by rememberUpdatedState(history)
    val currentData by rememberUpdatedState(data)
    val currentProvider by rememberUpdatedState(provider)
    val currentUiState by rememberUpdatedState(uiState)
    val currentOnPlay by rememberUpdatedState(onPlay)
    val currentOnDownload by rememberUpdatedState(onDownload)
    val currentOnToggleWatched by rememberUpdatedState(onToggleWatched)
    val currentOnRemoveEpisodeWatched by rememberUpdatedState(onRemoveEpisodeWatched)
    val currentOnMarkPreviousWatched by rememberUpdatedState(onMarkPreviousWatched)

    val p = rememberEpisodePresentation(
        ep = ep,
        history = history,
        provider = provider,
        data = data,
        uiState = uiState,
        isAntiSpoiler = isAntiSpoiler,
        lockUnreleasedEpisodes = lockUnreleasedEpisodes,
        thumbnailVersion = thumbnailVersion,
    )
    val releaseStatus = p.releaseStatus
    val isEpisodeLocked = p.isEpisodeLocked
    val targetUrl = p.targetUrl
    val progress = p.progress
    val isWatched = p.isWatched
    val shouldHideSpoilers = p.shouldHideSpoilers
    val finalTitle = p.finalTitle
    val runTimeStr = p.runTimeStr
    val cleanDesc = p.cleanDesc
    val hasDesc = p.hasDesc
    val formattedDate = p.formattedDate
    val rating10p = ep.score?.toFloat(10)?.takeIf { it > 0.0f }

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
                                            val latestHistory = currentHistory
                                            com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState.showForEpisode(
                                                episode = ep,
                                                loadResponse = currentData,
                                                history = latestHistory,
                                                provider = currentProvider,
                                                isAntiSpoiler = isAntiSpoiler,
                                                enableDownloadButtons = enableDownloadButtons,
                                                onPlay = currentOnPlay,
                                                onDownload = currentOnDownload,
                                                onToggleWatched = currentOnToggleWatched,
                                                onRemoveEpisodeWatched = currentOnRemoveEpisodeWatched,
                                                onMarkPreviousWatched = currentOnMarkPreviousWatched,
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
                        val hasCachedLinks = com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache.get(ep.data) != null
                        if (isEpisodeLocked && !hasCachedLinks) {
                            val dateText = releaseStatus.formattedDate ?: releaseStatus.statusBadgeText ?: "a future date"
                            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Episode is unreleased (Scheduled for $dateText)")
                        } else if (releaseStatus.isMissingFromProvider && !hasCachedLinks) {
                            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showWarning("Episode is not available on ${provider.name}.")
                        } else {
                            onPlay(ep)
                        }
                    },
                )
            },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // 1. Thumbnail
            Box(
                modifier = Modifier
                    .width(340.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp))
                    .then(
                        if (targetUrl == null && uiState?.isEnriching == true) {
                            Modifier.shimmerBackground()
                        } else {
                            Modifier.background(
                                androidx.compose.ui.graphics.Brush.verticalGradient(
                                    listOf(Color(0xFF1E222B), Color(0xFF121418)),
                                ),
                            )
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
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
                } else if (uiState?.isEnriching != true) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(44.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        if (ep.episode != null) {
                            Text(
                                text = "EPISODE ${ep.episode}",
                                color = Color.White.copy(alpha = 0.45f),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                            )
                        }
                    }
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

                // Bottom progress bar
                if (progress > 0f) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.5.dp)
                            .background(Color.White.copy(alpha = 0.25f)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(heroColor),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            // 2. Details
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Row 1: Title and rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = if (shouldHideSpoilers) "Episode title hidden" else finalTitle,
                        style = MaterialTheme.typography.titleMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.Bold),
                        color = if (isEpisodeLocked) {
                            Color.White.copy(alpha = 0.75f)
                        } else if (isHovered) {
                            heroColor
                        } else {
                            Color.White
                        },
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

                        val (logoRes, logoWidth, logoHeight) = when {
                            isAnime -> Triple("badges/rating_mal.png", 27.dp, 16.dp)
                            hasImdb -> Triple("badges/rating_imdb.png", 33.dp, 16.dp)
                            else -> Triple("badges/rating_tmdb.png", 28.dp, 16.dp)
                        }
                        val badgePainter = com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.rememberSharpBadgePainter(logoRes)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.45f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.5.dp),
                        ) {
                            androidx.compose.foundation.Image(
                                painter = badgePainter,
                                contentDescription = null,
                                modifier = Modifier.size(width = logoWidth, height = logoHeight),
                            )
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", rating10p),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                                color = Color.White,
                            )
                        }
                    }
                }

                // Row 2: Synopsis
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

            // 3. Right-Aligned Metadata
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(end = 12.dp),
            ) {
                ep.episode?.let { epNum ->
                    Text(
                        text = if (ep.season != null) "S${ep.season} E$epNum" else "Ep $epNum",
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
                        color = heroColor.copy(alpha = 0.9f),
                    )
                }
                if (runTimeStr != null) {
                    Text(
                        text = runTimeStr,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = Color.White.copy(alpha = 0.60f),
                    )
                }
                if (formattedDate != null) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.5.sp),
                        color = if (isEpisodeLocked) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.60f),
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
                                currentOnRemoveEpisodeWatched(ep)
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
