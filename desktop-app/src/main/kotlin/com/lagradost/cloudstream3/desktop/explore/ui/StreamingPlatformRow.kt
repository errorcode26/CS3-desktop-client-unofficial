package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.components.ScrollChevron
import com.lagradost.cloudstream3.desktop.ui.components.desktopDragScroll
import com.lagradost.cloudstream3.desktop.ui.components.posterDepthEffect
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.launch

@Composable
fun StreamingPlatformRow(
    platforms: List<StreamingPlatform> = StreamingPlatform.entries,
    onSelectPlatform: (StreamingPlatform) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var isViewAllDialogOpen by remember { mutableStateOf(false) }

    val canScrollLeft by remember { derivedStateOf { listState.canScrollBackward } }
    val canScrollRight by remember { derivedStateOf { listState.canScrollForward } }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Section Header (Matching CategoryRowWithHeader style)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Left: Title + Subtitle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = "Streaming Networks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.TextPrimary,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "•",
                    color = theme.TextMuted.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
                Text(
                    text = "Browse by provider",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Right: View All Pill Button + Navigation Chevrons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Surface(
                    onClick = { isViewAllDialogOpen = true },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                    shadowElevation = 0.dp,
                ) {
                    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(
                            text = "View All",
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                ScrollChevron(
                    enabled = canScrollLeft,
                    onClick = {
                        coroutineScope.launch {
                            val target = (listState.firstVisibleItemIndex - 3).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                )

                ScrollChevron(
                    enabled = canScrollRight,
                    onClick = {
                        coroutineScope.launch {
                            val last = (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) + 3
                            val maxBound = (platforms.size - 1).coerceAtLeast(0)
                            listState.animateScrollToItem(last.coerceAtMost(maxBound))
                        }
                    },
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                )
            }
        }

        // Horizontal Platform Cards Rail (Clean, zero floating overlay buttons)
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .desktopDragScroll(listState),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            items(platforms, key = { it.id }) { platform ->
                StreamingPlatformCard(
                    platform = platform,
                    onClick = { onSelectPlatform(platform) },
                )
            }
        }
    }

    // "View All" Modal Grid Dialog (Unified CloudstreamCustomDialog)
    if (isViewAllDialogOpen) {
        CloudstreamCustomDialog(
            show = true,
            onDismissRequest = { isViewAllDialogOpen = false },
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.85f),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Dialog Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Streaming Networks",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = theme.TextPrimary,
                        )
                        Text(
                            text = "Select any streaming network to browse its curated collection",
                            style = MaterialTheme.typography.bodyMedium,
                            color = theme.TextMuted,
                        )
                    }

                    IconButton(
                        onClick = { isViewAllDialogOpen = false },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = theme.TextMuted,
                        )
                    }
                }

                // Grid of all 14 platforms
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 200.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    items(platforms, key = { it.id }) { platform ->
                        StreamingPlatformCard(
                            platform = platform,
                            onClick = {
                                isViewAllDialogOpen = false
                                onSelectPlatform(platform)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingPlatformCard(
    platform: StreamingPlatform,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val posterDepthEffectEnabled by AppearanceConfig.posterDepthEffectEnabled.collectAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.04f else 1.0f,
        animationSpec = tween(durationMillis = 180),
        label = "platform_card_scale",
    )

    val borderAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.75f else 0.12f,
        animationSpec = tween(durationMillis = 180),
        label = "platform_card_border",
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.35f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "platform_card_glow",
    )

    val cardShape = RoundedCornerShape(14.dp)
    val unhoveredBorderColor = if (posterDepthEffectEnabled) Color.Transparent else Color.White.copy(alpha = borderAlpha)

    Box(
        modifier = modifier
            .width(210.dp)
            .height(118.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        // Ambient Bloom Glow when hovered
        if (glowAlpha > 0f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .padding(3.dp)
                    .blur(22.dp)
                    .background(platform.brandColor.copy(alpha = glowAlpha), cardShape),
            )
        }

        // Main Glass Branded Surface
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .clip(cardShape)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                )
                .border(
                    BorderStroke(
                        1.dp,
                        if (isHovered) platform.brandColor.copy(alpha = borderAlpha) else unhoveredBorderColor,
                    ),
                    cardShape,
                ),
            shape = cardShape,
            color = Color.Transparent,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                platform.gradientColors.first().copy(alpha = 0.90f),
                                platform.gradientColors.last().copy(alpha = 0.98f),
                            ),
                        ),
                    )
                    .posterDepthEffect(cardShape, enabled = posterDepthEffectEnabled)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Determine whether local packaged SVG resource exists
                val hasLocalSvg = remember(platform.resourcePath) {
                    try {
                        platform.resourcePath.isNotBlank() &&
                            Thread.currentThread().contextClassLoader.getResource(platform.resourcePath) != null
                    } catch (e: Exception) {
                        false
                    }
                }

                var isRemoteImageError by remember(platform.logoImageUrl) { mutableStateOf(false) }

                if (hasLocalSvg) {
                    Image(
                        painter = painterResource(platform.resourcePath),
                        contentDescription = platform.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(46.dp)
                            .graphicsLayer {
                                alpha = if (isHovered) 1.0f else 0.88f
                            },
                    )
                } else if (platform.logoImageUrl.isNotBlank() && !isRemoteImageError) {
                    AsyncImage(
                        model = platform.logoImageUrl,
                        contentDescription = platform.displayName,
                        contentScale = ContentScale.Fit,
                        onError = { isRemoteImageError = true },
                        modifier = Modifier
                            .fillMaxWidth(0.82f)
                            .height(46.dp)
                            .graphicsLayer {
                                alpha = if (isHovered) 1.0f else 0.88f
                            },
                    )
                } else {
                    // Clean, centered typographic brand wordmark fallback
                    Text(
                        text = platform.logoText,
                        color = platform.brandColor.copy(alpha = if (isHovered) 1.0f else 0.85f),
                        fontWeight = FontWeight.Black,
                        fontSize = 22.sp,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
