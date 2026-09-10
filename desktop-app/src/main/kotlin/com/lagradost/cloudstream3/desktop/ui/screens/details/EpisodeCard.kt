package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Episode card component.
 */
@OptIn(ExperimentalComposeUiApi::class)
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
    enableDownloadButtons: Boolean = true,
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

    val lockUnreleasedEpisodes by AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
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
    val durationText = p.durationText

    val scale by animateFloatAsState(if (isHovered && isContextMenuEnabled && !isEpisodeLocked) 1.02f else 1f, animationSpec = tween(180))

    // thumbnailVersion is intentionally read here so Compose re-evaluates epImg when episode
    // thumbnails are enriched in-place (plain field mutations don't trigger recompose otherwise).
    @Suppress("UNUSED_EXPRESSION")
    thumbnailVersion

    val heroColor = MaterialTheme.colorScheme.primary

    val rating10p = ep.score?.toFloat(10)?.takeIf { it > 0.0f }

    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()

    Box(
        modifier = modifier
            .aspectRatio(16f / 13.5f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        val showGlow = isHovered && posterHoverGlowEnabled && isContextMenuEnabled && !isEpisodeLocked
        val glowAlpha by animateFloatAsState(if (showGlow) 1f else 0f, animationSpec = tween(180))
        if (posterHoverGlowEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = glowAlpha }
                    .blur(26.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(heroColor.copy(alpha = 0.55f), RoundedCornerShape(16.dp)),
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
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
                                                    ?: currentUiState?.watchHistory?.values?.find { ep.matchesHistory(it) }
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
                .pointerInput(ep, isEpisodeLocked, releaseStatus.isMissingFromProvider) {
                    detectTapGestures(
                        onTap = {
                            if (isEpisodeLocked) {
                                val dateText = releaseStatus.formattedDate ?: releaseStatus.statusBadgeText ?: "a future date"
                                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Episode is unreleased (Scheduled for $dateText)")
                            } else if (releaseStatus.isMissingFromProvider) {
                                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showWarning("Episode is not available on ${provider.name}.")
                            } else {
                                onPlay(ep)
                            }
                        }
                    )
                }
                .shadow(
                    elevation = 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = if (isHovered && isContextMenuEnabled && !isEpisodeLocked) heroColor else Color.Black,
                    ambientColor = Color.Transparent,
                )
                .border(
                    width = if (isHovered && isContextMenuEnabled && !isEpisodeLocked) 1.5.dp else if (isEpisodeLocked) 1.dp else 0.5.dp,
                    color = if (isEpisodeLocked) Color(0xFFFFB74D).copy(alpha = 0.45f) else if (isHovered && isContextMenuEnabled) heroColor else Color.White.copy(alpha = 0.18f),
                    shape = RoundedCornerShape(16.dp),
                )
                .clip(RoundedCornerShape(16.dp)),
        ) {
            val isNarrow = maxWidth < 320.dp

            // Base dark container background
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF141518)),
            )

            // Card background image
            val context = coil3.compose.LocalPlatformContext.current
            var bakedBitmap by remember(targetUrl, shouldHideSpoilers) {
                mutableStateOf(targetUrl?.let { EpisodeCardBaker.getFromCache(it, shouldHideSpoilers) })
            }

            if (targetUrl != null) {
                LaunchedEffect(targetUrl, shouldHideSpoilers) {
                    if (bakedBitmap == null) {
                        withContext(Dispatchers.IO) {
                            try {
                                val request = coil3.request.ImageRequest.Builder(context)
                                    .data(targetUrl)
                                    .crossfade(false)
                                    .build()
                                val result = coil3.SingletonImageLoader.get(context).execute(request)
                                if (result is coil3.request.SuccessResult) {
                                    val skiaBitmap = (result.image as? coil3.BitmapImage)?.bitmap
                                    if (skiaBitmap != null) {
                                        bakedBitmap = EpisodeCardBaker.getOrBake(targetUrl, skiaBitmap, shouldHideSpoilers)
                                    }
                                }
                            } catch (_: Throwable) {}
                        }
                    }
                }
            }

            Crossfade(
                targetState = bakedBitmap,
                animationSpec = tween(280),
                modifier = Modifier.fillMaxSize(),
            ) { bitmap ->
                if (bitmap != null) {
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = ep.name,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (uiState?.isEnriching == true) Modifier.shimmerBackground()
                                else Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.50f),
                            modifier = Modifier.size(if (isNarrow) 28.dp else 40.dp),
                        )
                    }
                }
            }

            // Anti-spoiler overlay
            if (shouldHideSpoilers && !isHovered && !isEpisodeLocked) {
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

            // Lock & Unreleased Center Overlay
            if (isEpisodeLocked) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.50f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1C1914).copy(alpha = 0.92f),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.70f)),
                        shadowElevation = 8.dp,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                Icons.Default.Lock,
                                contentDescription = "Unreleased",
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = releaseStatus.statusBadgeText ?: "Unreleased",
                                color = Color(0xFFFFB74D),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.3.sp,
                                ),
                            )
                        }
                    }
                }
            }

            // Rating badge
            if (rating10p != null && !shouldHideSpoilers) {
                val isAnime = data is com.lagradost.cloudstream3.AnimeLoadResponse ||
                    data.type == com.lagradost.cloudstream3.TvType.Anime ||
                    data.type == com.lagradost.cloudstream3.TvType.AnimeMovie ||
                    data.type == com.lagradost.cloudstream3.TvType.OVA
                val hasImdb = uiState?.enrichedImdbRating != null ||
                    data.syncData["imdb"]?.startsWith("tt") == true ||
                    data.syncData.values.any { it.startsWith("tt") }

                val (logoRes, logoWidth, logoHeight) = when {
                    isAnime -> Triple("badges/rating_mal.png", if (isNarrow) 24.dp else 27.dp, if (isNarrow) 14.dp else 16.dp)
                    hasImdb -> Triple("badges/rating_imdb.png", if (isNarrow) 29.dp else 33.dp, if (isNarrow) 14.dp else 16.dp)
                    else -> Triple("badges/rating_tmdb.png", if (isNarrow) 25.dp else 28.dp, if (isNarrow) 14.dp else 16.dp)
                }
                val badgePainter = com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.rememberSharpBadgePainter(logoRes)

                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(if (isNarrow) 8.dp else 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                        .padding(horizontal = if (isNarrow) 5.dp else 6.5.dp, vertical = 3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.5.dp),
                    ) {
                        androidx.compose.foundation.Image(
                            painter = badgePainter,
                            contentDescription = null,
                            modifier = Modifier.size(width = logoWidth, height = logoHeight),
                        )
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", rating10p),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isNarrow) 10.5.sp else 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.2.sp,
                            ),
                            color = Color.White,
                        )
                    }
                }
            }

            // Top-Right: Watched completion indicator or Unreleased indicator
            if (isWatched && !isEpisodeLocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (isNarrow) 8.dp else 12.dp)
                        .size(if (isNarrow) 22.dp else 26.dp)
                        .clip(androidx.compose.foundation.shape.CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .border(1.dp, Color.White.copy(alpha = 0.30f), androidx.compose.foundation.shape.CircleShape)
                        .pointerInput(ep) {
                            detectTapGestures {
                                currentOnRemoveEpisodeWatched(ep)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = "Watched",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(if (isNarrow) 13.dp else 16.dp),
                    )
                }
            } else if (releaseStatus.isUnreleased && !lockUnreleasedEpisodes) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(if (isNarrow) 8.dp else 12.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1C1914).copy(alpha = 0.90f))
                        .border(0.5.dp, Color(0xFFFFB74D).copy(alpha = 0.60f), RoundedCornerShape(6.dp))
                        .padding(horizontal = if (isNarrow) 5.dp else 7.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Default.Lock,
                            contentDescription = "Locked",
                            tint = Color(0xFFFFB74D),
                            modifier = Modifier.size(if (isNarrow) 9.dp else 11.dp),
                        )
                        Text(
                            text = "Upcoming",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isNarrow) 9.5.sp else 11.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = Color(0xFFFFB74D),
                        )
                    }
                }
            }

            // Bottom overlay: Episode badge, Title, Synopsis & Metadata in a clean safe zone
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = if (isNarrow) 12.dp else 18.dp, end = if (isNarrow) 12.dp else 18.dp, bottom = if (progress > 0f) (if (isNarrow) 14.dp else 20.dp) else (if (isNarrow) 8.dp else 14.dp)),
                verticalArrangement = Arrangement.spacedBy(if (isNarrow) 2.dp else 4.dp),
            ) {
                // Row 0: Episode Code Badge (e.g. S4 • EPISODE 1)
                val epNum = ep.episode
                val epText = if (epNum != null) {
                    if (ep.season != null) "S${ep.season} • EPISODE $epNum" else "EPISODE $epNum"
                } else "EPISODE"

                Text(
                    text = epText,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = if (isNarrow) 11.sp else 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                    ),
                    color = Color(0xFFE2E8F0).copy(alpha = 0.80f),
                )

                // Row 1: Full-Width Episode Title
                Text(
                    text = if (shouldHideSpoilers) "Episode title hidden" else finalTitle,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = if (isNarrow) 16.sp else 19.5.sp,
                        lineHeight = if (isNarrow) 20.sp else 24.sp,
                    ),
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isHovered) Color(0xFFB0C4FF) else Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .run { if (shouldHideSpoilers) this.blur(2.dp) else this },
                )

                // Row 2: Synopsis / Plot
                Text(
                    text = when {
                        shouldHideSpoilers -> "Description hidden."
                        hasDesc -> cleanDesc
                        runTimeStr != null -> "Runtime: $runTimeStr"
                        else -> "No description available."
                    },
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = if (isNarrow) 12.5.sp else 14.5.sp,
                        lineHeight = if (isNarrow) 16.sp else 20.sp,
                    ),
                    color = Color(0xFFD1D5DB).copy(alpha = if (hasDesc && !shouldHideSpoilers) 0.88f else 0.45f),
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.run { if (shouldHideSpoilers && hasDesc) this.blur(5.dp) else this },
                )

                // Row 3: Duration on Left & Air Date on Right
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (durationText != null) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isNarrow) 11.sp else 12.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    if (formattedDate != null) {
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isNarrow) 11.sp else 12.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = Color.White.copy(alpha = 0.55f),
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
                        .padding(start = if (isNarrow) 10.dp else 14.dp, end = if (isNarrow) 10.dp else 14.dp, bottom = if (isNarrow) 4.dp else 6.dp)
                        .height(if (isNarrow) 3.dp else 3.5.dp)
                        .clip(RoundedCornerShape(2.dp))
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
}
