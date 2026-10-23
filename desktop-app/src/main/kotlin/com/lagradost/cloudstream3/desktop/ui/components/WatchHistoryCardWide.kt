package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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
    modifier: Modifier = Modifier.width(340.dp).height(140.dp),
    isContextMenuEnabled: Boolean = true,
    providerBadgeDisplayMode: ProviderBadgeDisplayMode? = null,
    autoCleanTitles: Boolean? = null,
    isCleanMode: Boolean? = null,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    onPlayClick: (() -> Unit)? = null,
    onReLink: (() -> Unit)? = null,
) {
    val style = LocalPosterCardStyle.current
    val posterHoverGlowEnabled = style.hoverGlowEnabled
    val uiCardOpacity = style.cardOpacity
    val cardShape = remember(style.roundingDp) { RoundedCornerShape(style.roundingDp.dp + 4.dp) }

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

    val boundsHolder = remember { BoundsHolder() }
    val primary = MaterialTheme.colorScheme.primary
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity)

    val effectiveBadgeMode = providerBadgeDisplayMode
        ?: AppearanceConfig.providerBadgeDisplayMode.collectAsState().value
    val effectiveAutoClean = autoCleanTitles
        ?: CardMetadataConfig.autoCleanTitles.collectAsState().value
    val effectiveCleanMode = isCleanMode
        ?: AppearanceConfig.cleanModeEnabled.collectAsState().value

    val displayTitle = remember(history.showName, effectiveAutoClean, effectiveCleanMode) {
        if (effectiveAutoClean || effectiveCleanMode) {
            CardTitleSanitizer.sanitize(history.showName, autoClean = true).displayTitle
        } else {
            history.showName
        }
    }

    val isUpNext = history.duration == 0L && history.position == 0L
    val isNewSeason = false
    val isNewEpisode = false

    // Remaining Time Calculation
    val remainingText = remember(history.position, history.duration, progress, isUpNext, isNewSeason, isNewEpisode) {
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
        } else if (isNewSeason) {
            "New Season"
        } else if (isNewEpisode) {
            "New Episode"
        } else if (isUpNext) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isHovered) Modifier.border(1.5.dp, primary, cardShape) else Modifier.border(0.5.dp, Color.White.copy(alpha = 0.08f), cardShape))
                .clip(cardShape)
                .background(backgroundColor)
                .hoverable(interactionSource)
                .onGloballyPositioned { coordinates ->
                    boundsHolder.bounds = Rect(
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
                                        bounds = boundsHolder.bounds,
                                        history = history,
                                        provider = provider,
                                        onRemove = onRemove,
                                        onClick = onClick,
                                        onPlayClick = onPlayClick,
                                        onReLink = onReLink,
                                    )
                                } else if (event.button == PointerButton.Primary) {
                                    onClick()
                                }
                            }
                        }
                    }
                },
        ) {
            // Ambient Backdrop Glow
            val rawBackdrop = provider?.fixUrlNull(history.episodeThumbnailUrl ?: history.posterUrl) ?: history.posterUrl
            if (!rawBackdrop.isNullOrBlank()) {
                AsyncImage(
                    model = rawBackdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp)
                        .alpha(0.20f),
                )
            }

            // Dark Scrim over ambient backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.40f),
                                Color(0xFF0F1015).copy(alpha = 0.85f),
                                Color(0xFF0F1015).copy(alpha = 0.95f),
                            ),
                        ),
                    ),
            )

            Row(modifier = Modifier.fillMaxSize()) {
                // Left: Vertical Poster with Play Button Overlay (Flush against left edge)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(2f / 3f),
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

                    // NEW EPISODE / NEW SEASON Badge Overlay
                    if (isNewSeason || isNewEpisode) {
                        val badgeBg = if (isNewSeason) Color(0xFFB45309) else Color(0xFF1D4ED8)
                        val badgeLabel = if (isNewSeason) "NEW SEASON" else "NEW EPISODE"
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = badgeBg,
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(6.dp),
                        ) {
                            Text(
                                text = badgeLabel,
                                color = Color.White,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                            )
                        }
                    }

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
                                modifier = Modifier.size(44.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = "Play",
                                        tint = Color.White,
                                        modifier = Modifier.size(26.dp),
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

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = if (isNarrow) 10.dp else 14.dp,
                                end = if (isNarrow) 10.dp else 14.dp,
                                top = if (isNarrow) 8.dp else 12.dp,
                                bottom = if (isNarrow) 8.dp else 12.dp,
                            ),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        // Top & Middle Content Group
                        Column(verticalArrangement = Arrangement.spacedBy(if (isNarrow) 3.dp else 4.dp)) {
                            // 🔌 Provider Branding (if enabled in appearance settings)
                            if (provider == null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFFE65100).copy(alpha = 0.85f),
                                        border = BorderStroke(1.dp, Color(0xFFFFA726).copy(alpha = 0.5f)),
                                    ) {
                                        Text(
                                            text = "Re-Link Source",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                                        )
                                    }
                                }
                            } else if (!effectiveCleanMode && effectiveBadgeMode != ProviderBadgeDisplayMode.HIDDEN) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    when (effectiveBadgeMode) {
                                        ProviderBadgeDisplayMode.HIDDEN -> {}
                                        ProviderBadgeDisplayMode.ICON_ONLY -> {
                                            if (pluginIconUrl != null) {
                                                AsyncImage(
                                                    model = pluginIconUrl,
                                                    contentDescription = provider.name,
                                                    modifier = Modifier
                                                        .size(16.dp)
                                                        .clip(CircleShape)
                                                        .border(0.5.dp, Color.White.copy(alpha = 0.2f), CircleShape),
                                                )
                                            }
                                        }
                                        ProviderBadgeDisplayMode.FULL_BADGE -> {
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(primary.copy(alpha = 0.80f))
                                                    .padding(horizontal = 5.dp, vertical = 1.5.dp),
                                            ) {
                                                Text(
                                                    text = provider.name,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Show Title
                            Text(
                                text = displayTitle,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (isNarrow) 13.5.sp else 15.sp,
                                lineHeight = if (isNarrow) 17.sp else 19.sp,
                                color = Color.White,
                                maxLines = if (isSeries) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                            )

                            // Episode Subtitle (if series)
                            if (isSeries && (seText.isNotBlank() || !history.episodeName.isNullOrBlank())) {
                                val epHeader = buildString {
                                    if (seText.isNotBlank()) append(seText)
                                    val epName = history.episodeName
                                    if (!epName.isNullOrBlank()) {
                                        if (isNotEmpty()) append(" • ")
                                        append(epName)
                                    }
                                }
                                Text(
                                    text = epHeader,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = if (isNarrow) 11.sp else 12.sp,
                                    color = primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        // Bottom Progress & Time Remaining Section (Single clean metric, zero redundancy)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (remainingText != null || (history.duration > 0 && history.position > 0)) {
                                val statusColor = when {
                                    isNewSeason -> Color(0xFFF59E0B)
                                    isNewEpisode -> Color(0xFF60A5FA)
                                    isUpNext -> primary
                                    else -> Color.White.copy(alpha = 0.85f)
                                }

                                if (!remainingText.isNullOrBlank()) {
                                    Text(
                                        text = remainingText,
                                        fontSize = if (isNarrow) 10.5.sp else 11.5.sp,
                                        fontWeight = if (isUpNext) FontWeight.Bold else FontWeight.SemiBold,
                                        color = statusColor,
                                    )
                                }
                            }

                            // Progress Bar (hidden for newly queued / up next episodes)
                            if (!isUpNext && history.duration > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(if (isNarrow) 3.5.dp else 4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color.White.copy(alpha = 0.18f)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progress.coerceIn(0f, 1f))
                                            .fillMaxHeight()
                                            .clip(RoundedCornerShape(2.dp))
                                            .background(primary),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
