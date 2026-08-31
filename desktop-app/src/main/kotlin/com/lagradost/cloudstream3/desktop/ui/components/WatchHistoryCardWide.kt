package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.badges.FastRatingEnricher
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun WatchHistoryCardWide(
    history: WatchHistory,
    provider: MainAPI?,
    modifier: Modifier = Modifier.width(380.dp).height(180.dp),
    isContextMenuEnabled: Boolean = true,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val posterCornerRadius by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

    val shape = remember(posterCornerRadius) { RoundedCornerShape(posterCornerRadius.dp) }
    val cardShape = remember(posterCornerRadius) { RoundedCornerShape(posterCornerRadius.dp + 4.dp) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.03f else 1f,
        animationSpec = tween(200),
        label = "scale",
    )

    val progress = if (history.duration > 0) {
        if (PlayerLinkHandler.isCompleted(history.position, history.duration)) {
            1f
        } else {
            (history.position.toFloat() / history.duration.toFloat()).coerceIn(0f, 1f)
        }
    } else {
        0f
    }

    val isSeries = history.season != null || history.episode != null
    val seText = if (isSeries) {
        listOf(
            history.season?.let { "S$it" } ?: "",
            history.episode?.let { "E$it" } ?: "",
        ).filter { it.isNotBlank() }.joinToString(" ")
    } else {
        ""
    }

    var bounds by remember { mutableStateOf(Rect.Zero) }
    val primary = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity)

    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val isCleanMode by AppearanceConfig.cleanModeEnabled.collectAsState()
    val displayTitle = remember(history.showName, autoCleanTitles, isCleanMode) {
        if (autoCleanTitles || isCleanMode) {
            CardTitleSanitizer.sanitize(history.showName, autoClean = true).displayTitle
        } else {
            history.showName
        }
    }

    // Cached Rating Lookup & Background Enrichment
    val ratingsSignal by FastRatingEnricher.ratingsUpdateSignal.collectAsState()
    val cachedRating = remember(displayTitle, ratingsSignal) {
        FastRatingEnricher.getCachedRating(displayTitle)
    }
    LaunchedEffect(displayTitle) {
        if (cachedRating == null) {
            FastRatingEnricher.requestRatingAsync(displayTitle, isAnime = false, isSeries = isSeries)
        }
    }

    // Remaining Time Calculation
    val remainingText = remember(history.position, history.duration, progress) {
        if (history.duration > 0) {
            if (progress >= 0.95f) {
                "Completed"
            } else {
                val leftSeconds = maxOf(0L, history.duration - history.position)
                val leftMins = leftSeconds / 60L
                if (leftMins >= 60) {
                    "${leftMins / 60}h ${leftMins % 60}m left"
                } else if (leftMins > 0) {
                    "${leftMins}m left"
                } else {
                    "<1m left"
                }
            }
        } else if (history.position == 0L) {
            "Up Next"
        } else {
            null
        }
    }

    // Plugin Icon Resolution
    val pluginIconUrl = remember(provider?.name) {
        DesktopRepositoryManager.getPluginIcon(provider?.name)
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        if (isHovered && posterHoverGlowEnabled) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .blur(32.dp, edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded)
                    .background(primary.copy(alpha = 0.65f), cardShape),
            )
        }

        // Outer Card
        Row(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isHovered) Modifier.border(2.dp, primary, cardShape) else Modifier)
                .clip(cardShape)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .onGloballyPositioned { coordinates ->
                    bounds = Rect(
                        offset = coordinates.positionInWindow(),
                        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                    )
                }
                .pointerInput(isContextMenuEnabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                if (isContextMenuEnabled && event.button == PointerButton.Secondary) {
                                    GlobalContextMenuState.showForWatchHistory(
                                        bounds = bounds,
                                        history = history,
                                        provider = provider,
                                        onRemove = onRemove,
                                        onClick = onClick,
                                        onPlayClick = onPlayClick,
                                    )
                                } else if (event.button == PointerButton.Primary) {
                                    onClick()
                                }
                            }
                        }
                    }
                },
        ) {
            // Left: Vertical Poster with Play Button Overlay
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f / 3f)
                    .clip(shape),
            ) {
                val rawPoster = provider?.fixUrlNull(history.posterUrl) ?: history.posterUrl
                val enhancedPoster = remember(rawPoster) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(rawPoster) }
                AsyncImage(
                    model = enhancedPoster,
                    contentDescription = history.showName,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )

                // Play action overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isHovered) 0.35f else 0.05f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (isHovered) {
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.90f),
                            shadowElevation = 8.dp,
                            modifier = Modifier.size(46.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Right: Content Details & Metadata
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                val isNarrow = maxWidth < 190.dp

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(if (isNarrow) 10.dp else 16.dp),
                ) {
                    // Top area: Title and Metadata Badges
                    Column(
                        modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                    ) {
                        Text(
                            text = displayTitle,
                            fontWeight = FontWeight.Bold,
                            fontSize = if (isNarrow) 14.sp else 19.sp,
                            lineHeight = if (isNarrow) 18.sp else 23.sp,
                            color = Color.White,
                            maxLines = if (isNarrow) 2 else 2,
                            overflow = TextOverflow.Ellipsis,
                            style = LocalTextStyle.current.copy(
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.75f),
                                    offset = Offset(0f, 1f),
                                    blurRadius = 4f,
                                ),
                            ),
                        )

                        Spacer(modifier = Modifier.height(if (isNarrow) 4.dp else 8.dp))

                        // Metadata Badges Row (Rating, Type, Provider Icon/Badge)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(if (isNarrow) 5.dp else 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // ⭐ Verified Cached Rating
                            if (cachedRating != null && cachedRating > 0.0) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.60f))
                                        .border(0.5.dp, Color(0xFFFFD700).copy(alpha = 0.50f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFD700),
                                            modifier = Modifier.size(10.dp),
                                        )
                                        Text(
                                            text = String.format(java.util.Locale.US, "%.1f", cachedRating),
                                            fontSize = if (isNarrow) 9.5.sp else 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFFD700),
                                        )
                                    }
                                }
                            }

                            // 🎬 Series / Movie Type Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.White.copy(alpha = 0.12f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = if (isSeries) "SERIES" else "MOVIE",
                                    fontSize = if (isNarrow) 9.sp else 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                    color = Color.White.copy(alpha = 0.85f),
                                )
                            }

                            // 🔌 Provider Branding (Controlled by Appearance Settings)
                            if (provider != null && !isCleanMode) {
                                when (providerBadgeDisplayMode) {
                                    ProviderBadgeDisplayMode.HIDDEN -> {
                                        // Clean mode: Zero scraper clutter
                                    }
                                    ProviderBadgeDisplayMode.ICON_ONLY -> {
                                        if (pluginIconUrl != null) {
                                            AsyncImage(
                                                model = pluginIconUrl,
                                                contentDescription = provider.name,
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                                            )
                                        } else {
                                            Box(
                                                modifier = Modifier
                                                    .size(18.dp)
                                                    .clip(CircleShape)
                                                    .background(primary.copy(alpha = 0.85f)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    text = provider.name.take(1).uppercase(),
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.ExtraBold,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }
                                    ProviderBadgeDisplayMode.FULL_BADGE -> {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(primary.copy(alpha = 0.80f))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            ) {
                                                if (pluginIconUrl != null) {
                                                    AsyncImage(
                                                        model = pluginIconUrl,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(12.dp).clip(CircleShape),
                                                    )
                                                }
                                                Text(
                                                    text = provider.name,
                                                    fontSize = if (isNarrow) 9.sp else 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom area: Episode Badge, Progress Bar & Time Remaining
                    Column(
                        modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(if (isNarrow) 4.dp else 6.dp),
                    ) {
                        // Season & Episode / Time Left Info Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (isSeries && seText.isNotBlank()) {
                                val isUpNext = history.duration == 0L && history.position == 0L
                                Text(
                                    text = if (isUpNext) "Up Next • $seText" else seText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = if (isNarrow) 11.5.sp else 13.5.sp,
                                    color = if (isUpNext) primary else Color.White,
                                )
                            } else {
                                Spacer(modifier = Modifier.width(1.dp))
                            }

                            if (remainingText != null) {
                                Text(
                                    text = remainingText,
                                    fontSize = if (isNarrow) 10.5.sp else 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFFE2E8F0).copy(alpha = 0.75f),
                                )
                            }
                        }

                        // Cinematic Progress Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(if (isNarrow) 4.dp else 5.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White.copy(alpha = 0.20f)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(primary),
                            )
                        }
                    }
                }
            }
        }
    }
}
