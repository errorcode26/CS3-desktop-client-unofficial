package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.crossfade
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.ui.components.applyShadowMultiplier
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

data class EpisodeReleaseStatus(
    val isUnreleased: Boolean,
    val formattedDate: String?,
    val rawDate: String?,
    val statusBadgeText: String?,
    val daysUntilRelease: Long?,
)

private val EPISODE_DATE_REGEX = Regex("""\|\|DATE:(.*?)\|\|""")
private val EPISODE_E_PREFIX_REGEX = Regex("""^(?i)(E[0-9]+[\s\-:]*)+""")
private val EPISODE_WORD_PREFIX_REGEX = Regex("""^(?i)(Episode[\s]*[0-9]+[\s\-:]*)+""")

private const val MAX_RELEASE_STATUS_CACHE_SIZE = 500
private val releaseStatusCache = object : java.util.LinkedHashMap<String, EpisodeReleaseStatus>(128, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, EpisodeReleaseStatus>?): Boolean {
        return size > MAX_RELEASE_STATUS_CACHE_SIZE
    }
}
private val releaseStatusLock = Any()

fun parseEpisodeReleaseStatus(ep: Episode): EpisodeReleaseStatus {
    val rawDesc = ep.description ?: ""
    val dateMatch = EPISODE_DATE_REGEX.find(rawDesc)
    val rawDate = dateMatch?.groupValues?.get(1)?.trim()

    if (rawDate.isNullOrBlank()) {
        return EpisodeReleaseStatus(
            isUnreleased = false,
            formattedDate = null,
            rawDate = null,
            statusBadgeText = null,
            daysUntilRelease = null,
        )
    }

    synchronized(releaseStatusLock) {
        return releaseStatusCache.getOrPut(rawDate) {
            computeEpisodeReleaseStatus(rawDate)
        }
    }
}

private val OUTPUT_DATE_FORMATTER = java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy", java.util.Locale.US)
    .withZone(java.time.ZoneOffset.UTC)

private val ISO_DATE_FORMATTERS = listOf(
    java.time.format.DateTimeFormatter.ISO_DATE_TIME.withZone(java.time.ZoneOffset.UTC) to false,
    java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(java.time.ZoneOffset.UTC) to false,
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(java.time.ZoneOffset.UTC) to false,
    java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.withZone(java.time.ZoneOffset.UTC) to true,
)

private fun computeEpisodeReleaseStatus(rawDate: String): EpisodeReleaseStatus {
    var releaseEpochMs: Long? = null
    var formattedOut: String? = null

    for ((formatter, isDateOnly) in ISO_DATE_FORMATTERS) {
        try {
            val temporal = formatter.parseBest(rawDate, java.time.Instant::from, java.time.LocalDate::from)
            val instant = when (temporal) {
                is java.time.Instant -> temporal
                is java.time.LocalDate -> temporal.atStartOfDay(java.time.ZoneOffset.UTC).toInstant()
                else -> null
            }
            if (instant != null) {
                formattedOut = OUTPUT_DATE_FORMATTER.format(instant)
                releaseEpochMs = if (isDateOnly) {
                    instant.toEpochMilli() + 86_400_000L
                } else {
                    instant.toEpochMilli()
                }
                break
            }
        } catch (_: Exception) {
        }
    }

    val now = System.currentTimeMillis()
    val rEpoch = releaseEpochMs
    val isFuture = rEpoch != null && rEpoch > now
    val daysUntil = if (rEpoch != null && rEpoch > now) {
        val diffMs = rEpoch - now
        maxOf(1L, diffMs / 86_400_000L)
    } else {
        null
    }

    val badgeText = when {
        !isFuture -> null
        daysUntil != null && daysUntil > 1 -> "Airs in $daysUntil days"
        daysUntil == 1L -> "Airs tomorrow"
        formattedOut != null -> "Airs $formattedOut"
        else -> "Unreleased"
    }

    return EpisodeReleaseStatus(
        isUnreleased = isFuture,
        formattedDate = formattedOut ?: rawDate,
        rawDate = rawDate,
        statusBadgeText = badgeText,
        daysUntilRelease = daysUntil,
    )
}

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

    val releaseStatus = remember(ep.description) { parseEpisodeReleaseStatus(ep) }
    val lockUnreleasedEpisodes by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
    val isEpisodeLocked = releaseStatus.isUnreleased && lockUnreleasedEpisodes

    val scale by animateFloatAsState(if (isHovered && isContextMenuEnabled && !isEpisodeLocked) 1.02f else 1f, animationSpec = tween(180))

    // thumbnailVersion is intentionally read here so Compose re-evaluates epImg when episode
    // thumbnails are enriched in-place (plain field mutations don't trigger recompose otherwise).
    @Suppress("UNUSED_EXPRESSION")
    thumbnailVersion
    val epImg = (provider.fixUrlNull(ep.posterUrl) ?: ep.posterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() && !it.contains("imgbb") }
    val fallbackPoster = (provider.fixUrlNull(data.posterUrl) ?: data.posterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() && !it.contains("imgbb") }
    val fallbackBackdrop = (uiState?.enrichedBackdropUrl ?: provider.fixUrlNull(data.backgroundPosterUrl) ?: data.backgroundPosterUrl)?.let { if (it.startsWith("//")) "https:$it" else it }?.takeIf { it.isNotBlank() }
    val targetUrl = epImg ?: fallbackPoster ?: fallbackBackdrop

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
    val formattedDate = releaseStatus.formattedDate
    val cleanDesc = rawDesc.replace(EPISODE_DATE_REGEX, "").trim()
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

    // Full-Bleed Cinematic Card (Hero Section Architecture)
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()

    Box(
        modifier = modifier
            .aspectRatio(16f / 12f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (isHovered && posterHoverGlowEnabled && isContextMenuEnabled && !isEpisodeLocked) {
            Box(
                modifier = Modifier
                    .matchParentSize()
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
                            }
                        }
                    )
                }
                .shadow(
                    elevation = if (isHovered && isContextMenuEnabled && !isEpisodeLocked) 12.dp else 4.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = if (isHovered && isContextMenuEnabled && !isEpisodeLocked) heroColor else Color.Black,
                    ambientColor = if (isHovered && isContextMenuEnabled && !isEpisodeLocked) heroColor else Color.Black,
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

            // Background image & true downward ambient extension
            if (targetUrl != null) {
                val context = coil3.compose.LocalPlatformContext.current
                val imageRequest = remember(targetUrl) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(targetUrl)
                        .crossfade(true)
                        .build()
                }
                val blurImageRequest = remember(targetUrl) {
                    coil3.request.ImageRequest.Builder(context)
                        .data(targetUrl)
                        .crossfade(false)
                        .build()
                }

                // Layer 1: Downward Ambient Color Extension (Fills the bottom text canvas with blurred scene colors)
                if (!shouldHideSpoilers) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        0.00f to Color.Transparent,
                                        0.40f to Color.Transparent,
                                        0.58f to Color.Black.copy(alpha = 0.50f),
                                        0.75f to Color.Black.copy(alpha = 0.90f),
                                        1.00f to Color.Black,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                    ) {
                        coil3.compose.AsyncImage(
                            model = blurImageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.BottomCenter,
                            modifier = Modifier
                                .fillMaxSize()
                                .blur(36.dp),
                        )
                    }
                }

                // Layer 2: Complete Uncropped 16:9 Thumbnail (Top frame, 100% sharp with soft bottom edge dissolve)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.00f to Color.Black,
                                    0.70f to Color.Black,
                                    0.88f to Color.Black.copy(alpha = 0.50f),
                                    1.00f to Color.Transparent,
                                ),
                                blendMode = BlendMode.DstIn,
                            )
                        },
                ) {
                    coil3.compose.SubcomposeAsyncImage(
                        model = imageRequest,
                        contentDescription = ep.name,
                        contentScale = ContentScale.Crop,
                        alignment = if (epImg == null && fallbackPoster != null) Alignment.TopCenter else Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .run { if (shouldHideSpoilers) this.blur(16.dp) else this },
                        error = {
                            val secondaryFallback = fallbackBackdrop ?: fallbackPoster
                            if (secondaryFallback != null) {
                                coil3.compose.AsyncImage(
                                    model = secondaryFallback,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    alignment = Alignment.Center,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        },
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(if (isNarrow) 28.dp else 40.dp))
                }
            }

            // Layer 3: Subtle Frosted Glass Text Shield (Ensures crisp white text readability while letting ambient scene colors pop)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.00f to Color.Transparent,
                            0.45f to Color.Transparent,
                            0.65f to Color(0xFF0A0B0E).copy(alpha = 0.35f),
                            0.82f to Color(0xFF07080B).copy(alpha = 0.65f),
                            1.00f to Color(0xFF050608).copy(alpha = 0.85f),
                        ),
                    ),
            )

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
                    border = BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.70f)),
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

        // Top-Left: Gold Star Rating Badge
        if (rating10p != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(if (isNarrow) 8.dp else 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(0.5.dp, Color(0xFFFFD700).copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                    .padding(horizontal = if (isNarrow) 5.dp else 7.dp, vertical = 2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = "Rating",
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(if (isNarrow) 10.dp else 12.dp),
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1f", rating10p),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = if (isNarrow) 10.sp else 11.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                        ),
                        color = Color(0xFFFFD700),
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
                    .clip(CircleShape)
                    .background(Color(0xFF4CAF50).copy(alpha = 0.92f))
                    .pointerInput(ep) {
                        detectTapGestures {
                            onRemoveEpisodeWatched(ep)
                        }
                    }
                    .padding(if (isNarrow) 4.dp else 6.dp),
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Watched - Click to unmark",
                    tint = Color.White,
                    modifier = Modifier.size(if (isNarrow) 11.dp else 14.dp),
                )
            }
        } else if (isEpisodeLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(if (isNarrow) 8.dp else 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.70f))
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
                color = Color(0xFFE2E8F0).copy(alpha = 0.62f),
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

            // Row 2: Synopsis / Plot with comfortable line height
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
                color = Color(0xFFE2E8F0).copy(alpha = if (hasDesc && !shouldHideSpoilers) 0.72f else 0.45f),
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
                        color = Color.White.copy(alpha = 0.55f),
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
                        color = Color.White.copy(alpha = 0.45f),
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

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    onPlay: (com.lagradost.cloudstream3.Episode) -> Unit,
    onDownload: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
    onToggleWatched: (com.lagradost.cloudstream3.Episode, Boolean) -> Unit,
    onRemoveEpisodeWatched: (com.lagradost.cloudstream3.Episode) -> Unit,
    onMarkPreviousWatched: ((com.lagradost.cloudstream3.Episode) -> Unit)? = null,
) {
    var isHovered by remember { mutableStateOf(false) }

    val releaseStatus = remember(ep.description) { parseEpisodeReleaseStatus(ep) }
    val lockUnreleasedEpisodes by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
    val isEpisodeLocked = releaseStatus.isUnreleased && lockUnreleasedEpisodes

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

    val isWatched = progress > 0.9f
    val shouldHideSpoilers = isAntiSpoiler && !isWatched

    val rawTitle = ep.name ?: "Episode ${ep.episode ?: "?"}"
    val titleCleaned = rawTitle
        .replace(EPISODE_E_PREFIX_REGEX, "")
        .replace(EPISODE_WORD_PREFIX_REGEX, "")
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
                if (epImg != null || fallbackImg != null) {
                    val targetUrl = epImg ?: fallbackImg
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

                    if (rating10p != null) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFFFD700).copy(alpha = 0.15f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(13.dp))
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f", rating10p),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.5.sp, fontWeight = FontWeight.Bold),
                                color = Color(0xFFFFD700),
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
