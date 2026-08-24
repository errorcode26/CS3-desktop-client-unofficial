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
import androidx.compose.material.icons.filled.Star
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
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@androidx.compose.runtime.Immutable
data class PosterCardStyle(
    val roundingDp: Int = 12,
    val titlePosition: com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition = com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW,
    val showRating: Boolean = true,
    val showQuality: Boolean = true,
    val showLanguage: Boolean = true,
    val hoverGlowEnabled: Boolean = true,
    val cardOpacity: Float = 1.0f,
    val shadowMultiplier: Float = 1.0f,
)

val LocalPosterCardStyle = staticCompositionLocalOf { PosterCardStyle() }

@Composable
fun rememberPosterCardStyle(): PosterCardStyle {
    val roundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val titlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val showRating by AppearanceConfig.showPosterRating.collectAsState()
    val showQuality by AppearanceConfig.showPosterQuality.collectAsState()
    val showLanguage by AppearanceConfig.showPosterLanguage.collectAsState()
    val hoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()
    val cardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
    val shadowMultiplier by AppearanceConfig.elementShadowMultiplier.collectAsState()
    return remember(roundingDp, titlePosition, showRating, showQuality, showLanguage, hoverGlowEnabled, cardOpacity, shadowMultiplier) {
        PosterCardStyle(
            roundingDp = roundingDp,
            titlePosition = titlePosition,
            showRating = showRating,
            showQuality = showQuality,
            showLanguage = showLanguage,
            hoverGlowEnabled = hoverGlowEnabled,
            cardOpacity = cardOpacity,
            shadowMultiplier = shadowMultiplier,
        )
    }
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PosterCard(
    item: SearchResponse,
    provider: MainAPI?,
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp? = null,
    aspectRatio: Float? = null,
    gridScale: String? = null,
    isHoverEnabled: Boolean = true,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val style = LocalPosterCardStyle.current
    val currentGridScale by AppearanceConfig.gridScale.collectAsState()
    val effectiveGridScale = gridScale ?: currentGridScale
    val shape = remember(style.roundingDp) { RoundedCornerShape(style.roundingDp.dp) }
    val rawImgUrl = provider?.fixUrlNull(item.posterUrl) ?: item.posterUrl
    val imgUrl = remember(rawImgUrl) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(rawImgUrl) }

    val effectiveAspectRatio = aspectRatio
        ?: if (item.type == com.lagradost.cloudstream3.TvType.Live || item.posterHeaders?.containsKey("landscape") == true) {
            16f / 9f
        } else {
            2f / 3f
        }

    val width = itemWidth ?: when (effectiveGridScale) {
        "Compact" -> if (effectiveAspectRatio > 1f) 220.dp else 150.dp
        "Large" -> if (effectiveAspectRatio > 1f) 320.dp else 220.dp
        else -> if (effectiveAspectRatio > 1f) 270.dp else 190.dp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHoveredRaw by interactionSource.collectIsHoveredAsState()
    val isHovered = isHoveredRaw && isHoverEnabled

    val posterTitlePosition = style.titlePosition
    val showPosterRating = style.showRating
    val showPosterQuality = style.showQuality
    val showPosterLanguage = style.showLanguage
    val posterHoverGlowEnabled = style.hoverGlowEnabled

    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }
    val primary = MaterialTheme.colorScheme.primary

    Column(modifier = modifier.width(width)) {
        Box {
            if (isHovered && posterHoverGlowEnabled) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .blur(32.dp, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                        .background(primary.copy(alpha = 0.65f), shape),
                )
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (isHoverEnabled) Modifier.posterHoverEffect(shape) else Modifier)
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
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.type == PointerEventType.Release) {
                                    if (event.button == PointerButton.Secondary) {
                                        GlobalContextMenuState.showForPoster(
                                            bounds = bounds,
                                            item = item,
                                            provider = provider,
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
                shape = shape,
                color = DesktopUi.SurfaceCard,
                tonalElevation = 2.dp,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(effectiveAspectRatio),
                ) {
                    if (imgUrl != null) {
                        // Actual poster — Crop to fill the entire box with explicit downsampled memory footprint
                        val context = coil3.compose.LocalPlatformContext.current
                        val imageRequest = remember(imgUrl) {
                            coil3.request.ImageRequest.Builder(context)
                                .data(imgUrl)
                                .build()
                        }
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        // No image placeholder
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DesktopUi.SurfaceElevated),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                item.name.take(2).uppercase(),
                                color = DesktopUi.Accent,
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                    ) {
                        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                    }

                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered,
                        enter = androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(200)) +
                            androidx.compose.animation.scaleIn(initialScale = 0.8f, animationSpec = androidx.compose.animation.core.tween(200)),
                        exit = androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(200)) +
                            androidx.compose.animation.scaleOut(targetScale = 0.8f, animationSpec = androidx.compose.animation.core.tween(200)),
                        modifier = Modifier.align(Alignment.Center),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(Color.White.copy(alpha = 0.15f))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), androidx.compose.foundation.shape.CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp).padding(start = 2.dp),
                            )
                        }
                    }

                    // Gradient at the bottom with the title
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isHovered && posterTitlePosition == com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.INSIDE,
                        modifier = Modifier.align(Alignment.BottomCenter),
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0f to Color.Transparent,
                                            0.35f to Color.Black.copy(alpha = 0.7f),
                                            1f to Color.Black.copy(alpha = 0.92f),
                                        ),
                                    ),
                                )
                                .padding(horizontal = 10.dp, vertical = 10.dp),
                        ) {
                            Column {
                                Text(
                                    text = item.name,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    lineHeight = 16.sp,
                                )
                            }
                        }
                    }

                    PosterBadges(
                        item = item,
                        showRating = showPosterRating,
                        showQuality = showPosterQuality,
                        showLanguage = showPosterLanguage,
                    )
                } // end box
            } // end surface
        } // end outer box

        if (posterTitlePosition == com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(38.dp)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.TopStart,
            ) {
                Text(
                    text = item.name,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelLarge.copy(
                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

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

            // Top-left badges (Provider and Episode)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
            ) {
                if (provider != null) {
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
                            style = androidx.compose.ui.text.TextStyle(
                                lineHeight = 9.sp,
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                                ),
                            ),
                        )
                    }
                }
                if (seText.isNotBlank()) {
                    val isUpNext = history.duration == 0L && history.position == 0L
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isUpNext) primary.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.6f))
                            .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = (if (isUpNext) "UP NEXT $seText" else seText).uppercase(),
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            style = androidx.compose.ui.text.TextStyle(
                                lineHeight = 9.sp,
                                lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                    alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                    trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both,
                                ),
                            ),
                        )
                    }
                }
            }

            // Bottom content: Title and time left
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = history.showName,
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
                    val leftSeconds = history.duration - history.position
                    val leftMins = leftSeconds / 60L
                    if (leftMins > 0) "${leftMins}m left" else "<1m left"
                } else {
                    "${(progress * 100).toInt()}%"
                }

                Text(
                    text = timeLeftText,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            // Floating progress bar with padding
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
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

@Composable
fun BoxScope.PosterBadges(
    item: SearchResponse,
    showRating: Boolean,
    showQuality: Boolean,
    showLanguage: Boolean,
) {
    val ratingText = item.score?.let { score ->
        val v = score.toFloat(10)
        if (v > 0.0f) String.format(java.util.Locale.US, "%.1f", v) else null
    }

    val isAnime = item is com.lagradost.cloudstream3.AnimeSearchResponse
    val hasSub = showLanguage && isAnime && (
        item.episodes[com.lagradost.cloudstream3.DubStatus.Subbed] != null ||
        item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Subbed) == true
    )
    val hasDub = showLanguage && isAnime && (
        item.episodes[com.lagradost.cloudstream3.DubStatus.Dubbed] != null ||
        item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Dubbed) == true
    )

    val qualityText = if (showQuality && item.quality != null) {
        val qName = item.quality!!.name
        when {
            qName == "FourK" || qName.contains("UHD", ignoreCase = true) -> "4K"
            qName.contains("BlueRay", ignoreCase = true) || qName.contains("BluRay", ignoreCase = true) -> "BD"
            qName.contains("HD", ignoreCase = true) -> "HD"
            else -> qName
        }
    } else null

    val hasTopStart = showRating && ratingText != null
    val hasTopEnd = hasSub || hasDub || qualityText != null

    if (hasTopStart || hasTopEnd) {
        Row(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 7.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Top Left: Rating
            if (hasTopStart && ratingText != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .border(0.5.dp, Color(0xFFFFD700).copy(alpha = 0.35f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color(0xFFFFD700),
                            modifier = Modifier.size(10.dp),
                        )
                        Text(
                            text = ratingText,
                            color = Color(0xFFFFE082),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.2.sp,
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            // Top Right: Language (SUB / DUB) and Quality (4K / HD / BD)
            if (hasTopEnd) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (hasSub && hasDub) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.55f))
                                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                            ) {
                                Text(
                                    text = "SUB",
                                    color = DesktopUi.Accent,
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                                Text(
                                    text = "•",
                                    color = Color.White.copy(alpha = 0.4f),
                                    fontSize = 8.sp,
                                )
                                Text(
                                    text = "DUB",
                                    color = Color(0xFFCE93D8),
                                    fontSize = 8.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                )
                            }
                        }
                    } else {
                        if (hasSub) {
                            PosterBadge(
                                text = "SUB",
                                textColor = DesktopUi.Accent,
                                borderColor = DesktopUi.Accent.copy(alpha = 0.35f),
                            )
                        }
                        if (hasDub) {
                            PosterBadge(
                                text = "DUB",
                                textColor = Color(0xFFCE93D8),
                                borderColor = Color(0xFFCE93D8).copy(alpha = 0.35f),
                            )
                        }
                    }

                    if (qualityText != null) {
                        val is4k = qualityText == "4K"
                        PosterBadge(
                            text = qualityText,
                            textColor = if (is4k) Color(0xFFFFD54F) else Color.White.copy(alpha = 0.85f),
                            borderColor = if (is4k) Color(0xFFFFD54F).copy(alpha = 0.40f) else Color.White.copy(alpha = 0.20f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PosterBadge(
    text: String,
    textColor: Color,
    borderColor: Color = Color.White.copy(alpha = 0.20f),
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.55f))
            .border(0.5.dp, borderColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 4.5.dp, vertical = 2.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 0.3.sp,
        )
    }
}
