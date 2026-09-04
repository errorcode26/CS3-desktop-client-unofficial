package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun WatchHistoryCard(
    history: WatchHistory,
    provider: MainAPI?,
    modifier: Modifier = Modifier.width(380.dp).height(380.dp * 9f / 16f),
    isContextMenuEnabled: Boolean = true,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val style = LocalPosterCardStyle.current
    val posterHoverGlowEnabled = style.hoverGlowEnabled
    val uiCardOpacity = style.cardOpacity
    val shape = remember(style.roundingDp) { RoundedCornerShape(style.roundingDp.dp) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.03f else 1f,
        animationSpec = androidx.compose.animation.core.tween(200),
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

    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val primary = MaterialTheme.colorScheme.primary

    val currentHistory by rememberUpdatedState(history)
    val currentBounds by rememberUpdatedState(bounds)
    val currentProvider by rememberUpdatedState(provider)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPlayClick by rememberUpdatedState(onPlayClick)

    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val isCleanMode by AppearanceConfig.cleanModeEnabled.collectAsState()
    val displayTitle = remember(history.showName, autoCleanTitles, isCleanMode) {
        if (autoCleanTitles || isCleanMode) {
            CardTitleSanitizer.sanitize(history.showName, autoClean = true).displayTitle
        } else {
            history.showName
        }
    }

    val ratingsSignal by FastRatingEnricher.ratingsUpdateSignal.collectAsState()
    val cachedRating = remember(displayTitle, ratingsSignal) {
        FastRatingEnricher.getCachedRating(displayTitle)
    }
    LaunchedEffect(displayTitle) {
        if (cachedRating == null) {
            FastRatingEnricher.requestRatingAsync(displayTitle, isAnime = false, isSeries = isSeries)
        }
    }

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
                    .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(primary.copy(alpha = 0.65f), shape),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isHovered) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                .clip(shape)
                .hoverable(interactionSource)
                .onGloballyPositioned { coordinates ->
                    val newBounds = Rect(
                        offset = coordinates.positionInWindow(),
                        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                    )
                    if (bounds != newBounds) {
                        bounds = newBounds
                    }
                }
                .pointerInput(isContextMenuEnabled) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Release) {
                                if (isContextMenuEnabled && event.button == PointerButton.Secondary) {
                                    GlobalContextMenuState.showForWatchHistory(
                                        bounds = currentBounds,
                                        history = currentHistory,
                                        provider = currentProvider,
                                        onRemove = currentOnRemove,
                                        onClick = currentOnClick,
                                        onPlayClick = currentOnPlayClick,
                                    )
                                } else if (event.button == PointerButton.Primary) {
                                    currentOnClick()
                                }
                            }
                        }
                    }
                },
        ) {
            val rawImgUrl = provider?.fixUrlNull(history.episodeThumbnailUrl) ?: history.episodeThumbnailUrl
                ?: history.screenshotUrl
                ?: provider?.fixUrlNull(history.posterUrl) ?: history.posterUrl
            val imgUrl = remember(rawImgUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(rawImgUrl) }

            // Full-bleed background image
            if (imgUrl != null) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity)))
            }

            // Dark gradient scrim
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.5f to Color.Black.copy(alpha = 0.25f),
                        1.0f to Color.Black.copy(alpha = 0.95f),
                    ),
                ),
            )

            AnimatedVisibility(
                visible = isHovered,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
            ) {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))
            }

            // Play button overlay on hover
            AnimatedVisibility(
                visible = isHovered,
                modifier = Modifier.align(Alignment.Center),
                enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(150)),
                exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(150)),
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.White.copy(alpha = 0.25f), CircleShape)
                        .border(2.dp, Color.White.copy(alpha = 0.6f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Top-left provider branding (if enabled in appearance settings)
            if (provider != null && !isCleanMode && providerBadgeDisplayMode != ProviderBadgeDisplayMode.HIDDEN) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                ) {
                    when (providerBadgeDisplayMode) {
                        ProviderBadgeDisplayMode.HIDDEN -> {}
                        ProviderBadgeDisplayMode.ICON_ONLY -> {
                            if (pluginIconUrl != null) {
                                AsyncImage(
                                    model = pluginIconUrl,
                                    contentDescription = provider.name,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clip(CircleShape)
                                        .background(primary.copy(alpha = 0.85f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = provider.name.take(1).uppercase(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                    )
                                }
                            }
                        }
                        ProviderBadgeDisplayMode.FULL_BADGE -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = provider.name.uppercase(),
                                    color = Color.White,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom content: Title, Episode Name, and time left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayTitle,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )

                    val timeLeftText = if (history.duration == 0L && history.position == 0L) {
                        "Up Next"
                    } else if (progress >= 1f) {
                        "Completed"
                    } else if (history.duration > 0) {
                        val leftSeconds = maxOf(0L, history.duration - history.position)
                        val leftMins = leftSeconds / 60L
                        if (leftMins >= 60) "${leftMins / 60}h ${leftMins % 60}m left" else if (leftMins > 0) "${leftMins}m left" else "<1m left"
                    } else {
                        "${(progress * 100).toInt()}%"
                    }

                    Text(
                        text = timeLeftText,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                val isSeries = history.season != null || history.episode != null
                val seText = if (isSeries) {
                    listOf(
                        history.season?.let { "S$it" } ?: "",
                        history.episode?.let { "E$it" } ?: "",
                    ).filter { it.isNotBlank() }.joinToString(" ")
                } else ""
                val epSub = buildString {
                    if (seText.isNotBlank()) append(seText)
                    val epName = history.episodeName
                    if (!epName.isNullOrBlank()) {
                        if (isNotEmpty()) append(" • ")
                        append(epName)
                    }
                }
                if (epSub.isNotBlank()) {
                    Text(
                        text = epSub,
                        color = Color.White.copy(alpha = 0.80f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Floating progress bar with padding
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .height(4.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp))
                    .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .fillMaxHeight()
                        .background(DesktopUi.Accent),
                )
            }
        }
    }
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun WatchHistoryCardDetailed(
    history: com.lagradost.common.storage.WatchHistory,
    provider: MainAPI?,
    modifier: Modifier = Modifier.width(440.dp).height(145.dp),
    isContextMenuEnabled: Boolean = true,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val style = LocalPosterCardStyle.current
    val posterHoverGlowEnabled = style.hoverGlowEnabled
    val uiCardOpacity = style.cardOpacity
    val shape = remember(style.roundingDp) { RoundedCornerShape(style.roundingDp.dp) }
    val cardShape = remember(style.roundingDp) { RoundedCornerShape(style.roundingDp.dp + 2.dp) }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.025f else 1f,
        animationSpec = androidx.compose.animation.core.tween(200),
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

    val currentHistory by rememberUpdatedState(history)
    val currentBounds by rememberUpdatedState(bounds)
    val currentProvider by rememberUpdatedState(provider)
    val currentOnRemove by rememberUpdatedState(onRemove)
    val currentOnClick by rememberUpdatedState(onClick)
    val currentOnPlayClick by rememberUpdatedState(onPlayClick)

    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val isCleanMode by AppearanceConfig.cleanModeEnabled.collectAsState()
    val displayTitle = remember(history.showName, autoCleanTitles, isCleanMode) {
        if (autoCleanTitles || isCleanMode) {
            CardTitleSanitizer.sanitize(history.showName, autoClean = true).displayTitle
        } else {
            history.showName
        }
    }

    val ratingsSignal by FastRatingEnricher.ratingsUpdateSignal.collectAsState()
    val cachedRating = remember(displayTitle, ratingsSignal) {
        FastRatingEnricher.getCachedRating(displayTitle)
    }
    LaunchedEffect(displayTitle) {
        if (cachedRating == null) {
            FastRatingEnricher.requestRatingAsync(displayTitle, isAnime = false, isSeries = isSeries)
        }
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
                    .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                    .background(primary.copy(alpha = 0.60f), cardShape),
            )
        }

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
                                        bounds = currentBounds,
                                        history = currentHistory,
                                        provider = currentProvider,
                                        onRemove = currentOnRemove,
                                        onClick = currentOnClick,
                                        onPlayClick = currentOnPlayClick,
                                    )
                                } else if (event.button == PointerButton.Primary) {
                                    currentOnClick()
                                }
                            }
                        }
                    }
                },
        ) {
            // Left: 16:9 Landscape Still
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f)
                    .clip(shape),
            ) {
                val rawImgUrl = provider?.fixUrlNull(history.episodeThumbnailUrl) ?: history.episodeThumbnailUrl
                    ?: history.screenshotUrl
                    ?: provider?.fixUrlNull(history.posterUrl) ?: history.posterUrl
                val imgUrl = remember(rawImgUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(rawImgUrl) }

                if (imgUrl != null) {
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)))
                }

                // Dark gradient
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.6f to Color.Black.copy(alpha = 0.3f),
                            1.0f to Color.Black.copy(alpha = 0.9f),
                        ),
                    ),
                )

                // Play overlay
                if (isHovered) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = primary.copy(alpha = 0.9f),
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(40.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }

                // Progress Bar at bottom of thumbnail
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Color.Black.copy(alpha = 0.5f)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .background(primary),
                    )
                }
            }

            // Right: Content Details & Synopsis
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Show Title Row
                    Text(
                        text = displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    // Episode Title
                    val epHeader = buildString {
                        if (seText.isNotBlank()) append(seText)
                        val epName = history.episodeName
                        if (!epName.isNullOrBlank()) {
                            if (isNotEmpty()) append(" • ")
                            append(epName)
                        }
                    }
                    if (epHeader.isNotBlank()) {
                        Text(
                            text = epHeader,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Bottom: Time Remaining
                val timeLeftText = if (history.duration == 0L && history.position == 0L) {
                    "Up Next"
                } else if (progress >= 1f) {
                    "Completed"
                } else if (history.duration > 0) {
                    val leftSeconds = maxOf(0L, history.duration - history.position)
                    val leftMins = leftSeconds / 60L
                    if (leftMins >= 60) "${leftMins / 60}h ${leftMins % 60}m left" else if (leftMins > 0) "${leftMins}m left" else "<1m left"
                } else {
                    "${(progress * 100).toInt()}%"
                }

                Text(
                    text = timeLeftText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.75f),
                )
            }
        }
    }
}
