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

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun PosterCard(
    item: SearchResponse,
    provider: MainAPI?,
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp? = null,
    aspectRatio: Float? = null,
    gridScale: String = AppearanceConfig.gridScale.value,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val posterCornerRadius by AppearanceConfig.posterRoundingDp.collectAsState()
    val shape = RoundedCornerShape(posterCornerRadius.dp)
    val imgUrl = provider?.fixUrlNull(item.posterUrl) ?: item.posterUrl

    val effectiveAspectRatio = aspectRatio
        ?: if (item.type == com.lagradost.cloudstream3.TvType.Live || item.posterHeaders?.containsKey("landscape") == true) {
            16f / 9f
        } else {
            2f / 3f
        }

    val width = itemWidth ?: when (gridScale) {
        "Compact" -> if (effectiveAspectRatio > 1f) 220.dp else 150.dp
        "Large" -> if (effectiveAspectRatio > 1f) 320.dp else 220.dp
        else -> if (effectiveAspectRatio > 1f) 270.dp else 190.dp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val showPosterRating by AppearanceConfig.showPosterRating.collectAsState()
    val showPosterQuality by AppearanceConfig.showPosterQuality.collectAsState()
    val showPosterLanguage by AppearanceConfig.showPosterLanguage.collectAsState()

    var bounds by remember { mutableStateOf(androidx.compose.ui.geometry.Rect.Zero) }

    Column(modifier = modifier.width(width)) {
        Surface(
            modifier = Modifier
                .width(width)
                .posterHoverEffect(shape)
                .clip(shape)
                .hoverable(interactionSource)
                .onGloballyPositioned { coordinates ->
                    bounds = Rect(
                        offset = coordinates.positionInWindow(),
                        size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                    )
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
                    AsyncImage(
                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                            .data(imgUrl)
                            .build(),
                        contentDescription = item.name,
                        contentScale = ContentScale.Crop,
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

        if (posterTitlePosition == com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelLarge.copy(
                    shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                ),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@kotlin.OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun WatchHistoryCard(
    history: WatchHistory,
    provider: MainAPI?,
    modifier: Modifier = Modifier.width(380.dp).height(380.dp * 9f / 16f),
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
) {
    val posterCornerRadius by AppearanceConfig.posterRoundingDp.collectAsState()
    val shape = RoundedCornerShape(posterCornerRadius.dp)

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

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .hoverable(interactionSource)
            .onGloballyPositioned { coordinates ->
                bounds = Rect(
                    offset = coordinates.positionInWindow(),
                    size = Size(coordinates.size.width.toFloat(), coordinates.size.height.toFloat()),
                )
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Release) {
                            if (event.button == PointerButton.Secondary) {
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
        val imgUrl = provider?.fixUrlNull(history.episodeThumbnailUrl) ?: history.episodeThumbnailUrl
            ?: history.screenshotUrl
            ?: provider?.fixUrlNull(history.posterUrl) ?: history.posterUrl

        // Full-bleed background image
        if (imgUrl != null) {
            AsyncImage(
                model = imgUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(modifier = Modifier.fillMaxSize().background(DesktopUi.SurfaceElevated))
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = seText.uppercase(),
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

            val timeLeftText = if (progress >= 1f) {
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

@Composable
fun BoxScope.PosterBadges(
    item: SearchResponse,
    showRating: Boolean,
    showQuality: Boolean,
    showLanguage: Boolean,
) {
    // Top Left: Rating
    val ratingText = item.score?.let { score ->
        "%.1f".format(score.toFloat(10))
    }

    if (showRating && ratingText != null) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = null,
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(10.dp),
                )
                Text(
                    text = ratingText,
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }

    // Bottom Badges
    Row(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        // Bottom Left: Language
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (showLanguage && item is com.lagradost.cloudstream3.AnimeSearchResponse) {
                // Subs
                val subCount = item.episodes[com.lagradost.cloudstream3.DubStatus.Subbed]
                if (subCount != null || item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Subbed) == true) {
                    PosterBadge(text = if (subCount != null) "SUB $subCount" else "SUB", color = DesktopUi.Accent)
                }
                // Dubs
                val dubCount = item.episodes[com.lagradost.cloudstream3.DubStatus.Dubbed]
                if (dubCount != null || item.dubStatus?.contains(com.lagradost.cloudstream3.DubStatus.Dubbed) == true) {
                    PosterBadge(text = if (dubCount != null) "DUB $dubCount" else "DUB", color = Color(0xFF9C27B0))
                }
            }
        }

        // Bottom Right: Quality
        if (showQuality && item.quality != null) {
            PosterBadge(text = item.quality!!.name, color = Color.White.copy(alpha = 0.3f), textColor = Color.White)
        }
    }
}

@Composable
private fun PosterBadge(text: String, color: Color, textColor: Color = Color.White) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color)
            .border(0.5.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 4.dp, vertical = 2.dp),
    ) {
        Text(
            text = text.uppercase(),
            color = textColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
