package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
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
    val shape = RoundedCornerShape(posterCornerRadius.dp)
    val cardShape = RoundedCornerShape(posterCornerRadius.dp + 4.dp) // slightly larger for the outer card

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

    var bounds by remember { mutableStateOf(Rect.Zero) }
    val primary = MaterialTheme.colorScheme.primary
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()
    val backgroundColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = uiCardOpacity)

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
                }
        ) {
            // Left: Vertical Poster
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(2f/3f)
                    .clip(shape)
            ) {
                val rawPoster = provider?.fixUrlNull(history.posterUrl) ?: history.posterUrl
                val enhancedPoster = remember(rawPoster) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhancePosterUrl(rawPoster) }
                AsyncImage(
                    model = enhancedPoster,
                    contentDescription = history.showName,
                    contentScale = ContentScale.Crop,
                    filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                    modifier = Modifier.fillMaxSize()
                )
                // Play overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = if (isHovered) 0.3f else 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isHovered) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // Right: Content Details
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(16.dp)
            ) {
                val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
                val displayTitle = remember(history.showName, autoCleanTitles) {
                    if (autoCleanTitles) {
                        CardTitleSanitizer.sanitize(history.showName, autoClean = true).displayTitle
                    } else {
                        history.showName
                    }
                }

                // Top area: Title and Tags
                Column(
                    modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(),
                ) {
                    Text(
                        text = displayTitle,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (provider != null) {
                            // Provider badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(primary.copy(alpha = 0.8f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = provider.name,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                        if (isSeries) {
                            // Series badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "SERIES",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                // Bottom area: Episode & Progress
                Column(
                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                ) {
                    if (isSeries) {
                        val isUpNext = history.duration == 0L && history.position == 0L
                        val prefix = if (isUpNext) "Up Next • " else ""
                        Text(
                            text = "$prefix$seText",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = if (isUpNext) primary else Color.White,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    
                    // Progress Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color.White.copy(alpha = 0.2f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .background(primary)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Progress text
                    val progressPercentage = (progress * 100).toInt()
                    Text(
                        text = if (progressPercentage >= 95) "Completed" else "$progressPercentage% watched",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
