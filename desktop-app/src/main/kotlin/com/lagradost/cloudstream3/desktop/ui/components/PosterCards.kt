package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.player.impl.PlayerLinkHandler

@Composable
fun PosterCard(
    item: SearchResponse,
    provider: MainAPI?,
    modifier: Modifier = Modifier,
    itemWidth: androidx.compose.ui.unit.Dp? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    val imgUrl = provider?.fixUrlNull(item.posterUrl) ?: item.posterUrl
    val gridScale by AppearanceConfig.gridScale.collectAsState()
    val width = itemWidth ?: when (gridScale) {
        "Compact" -> 150.dp
        "Large" -> 220.dp
        else -> 190.dp
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        modifier = modifier
            .width(width)
            .posterHoverEffect()
            .clip(shape)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
        shape = shape,
        color = DesktopUi.SurfaceCard,
        tonalElevation = 2.dp,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
        ) {
            if (imgUrl != null) {
                // Actual poster — Crop to fill the entire box
                AsyncImage(
                    model = imgUrl,
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

            AnimatedVisibility(
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

            val bookmarkId = if (provider != null) "${provider.name}_${item.url.hashCode()}" else ""
            var showBookmarkMenu by remember { mutableStateOf(false) }
            var currentBookmark by remember(bookmarkId) {
                mutableStateOf(if (bookmarkId.isNotEmpty()) DesktopDataStore.getBookmarks().find { it.id == bookmarkId } else null)
            }

            val bookmarkAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isHovered || currentBookmark != null || showBookmarkMenu) 1f else 0f,
                label = "bookmarkAlpha",
            )

            if (bookmarkAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .graphicsLayer { alpha = bookmarkAlpha },
                ) {
                    IconButton(
                        onClick = { if (provider != null) showBookmarkMenu = true },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            imageVector = com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library,
                            contentDescription = "Bookmark",
                            tint = if (currentBookmark != null) MaterialTheme.colorScheme.primary else Color.White,
                            modifier = Modifier.size(24.dp),
                        )
                    }

                    DropdownMenu(
                        expanded = showBookmarkMenu,
                        onDismissRequest = { showBookmarkMenu = false },
                        modifier = Modifier
                            .background(DesktopUi.SurfaceElevated, RoundedCornerShape(8.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                            .padding(4.dp),
                    ) {
                        Text(
                            "Add to Library",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        )
                        com.lagradost.common.storage.DesktopWatchType.entries.forEach { type ->
                            val isSelected = currentBookmark?.watchType == type.id
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        type.stringRes,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.White,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    )
                                },
                                onClick = {
                                    val newBookmark = DesktopBookmark(
                                        id = bookmarkId,
                                        name = item.name,
                                        url = item.url,
                                        apiName = provider!!.name,
                                        posterUrl = item.posterUrl,
                                        watchType = type.id,
                                    )
                                    DesktopDataStore.addBookmark(newBookmark)
                                    currentBookmark = newBookmark
                                    showBookmarkMenu = false
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent),
                            )
                        }
                        if (currentBookmark != null) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = Color.White.copy(alpha = 0.1f))
                            DropdownMenuItem(
                                text = {
                                    Text("Remove from Library", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold)
                                },
                                onClick = {
                                    DesktopDataStore.removeBookmark(bookmarkId)
                                    currentBookmark = null
                                    showBookmarkMenu = false
                                },
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f)),
                            )
                        }
                    }
                }
            }

            // Gradient at the bottom with the title
            AnimatedVisibility(
                visible = isHovered,
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
                        // Type badge
                        val typeLabel = item.quality?.name?.uppercase()
                            ?: if (item is com.lagradost.cloudstream3.AnimeSearchResponse && !item.dubStatus.isNullOrEmpty()) {
                                item.dubStatus!!.joinToString(" | ") {
                                    it.name.uppercase().replace("DUBBED", "DUB").replace("SUBBED", "SUB")
                                }
                            } else {
                                null
                            }
                        if (typeLabel != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(Color.White.copy(alpha = 0.25f))
                                    .border(0.5.dp, Color.White.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = typeLabel,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 0.5.sp,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                        }
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
        }
    }
}

@Composable
fun WatchHistoryCard(
    history: WatchHistory,
    provider: MainAPI?,
    modifier: Modifier = Modifier,
    onRemove: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)

    val gridScale by AppearanceConfig.gridScale.collectAsState()
    // Landscape 16:9 card configuration (decoupled from grid scale for a cinematic look)
    val cardWidth = 380.dp
    val cardHeight = cardWidth * 9f / 16f

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

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(shape)
            .hoverable(interactionSource)
            .clickable(onClick = onClick),
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
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(DesktopUi.Accent.copy(alpha = 0.9f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = provider.name,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            if (seText.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = seText,
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
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

        // Full-width progress bar touching the bottom edge
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
                    .background(DesktopUi.Accent),
            )
        }

        // Dismiss X — top-right corner
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(26.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape),
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove",
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(14.dp),
            )
        }
    }
}
