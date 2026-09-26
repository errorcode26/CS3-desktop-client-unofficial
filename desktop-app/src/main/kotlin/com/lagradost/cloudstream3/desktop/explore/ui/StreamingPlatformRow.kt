package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.explore.models.StreamingPlatform
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
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

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(4.dp, 16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "Streaming Networks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = theme.TextPrimary,
                    fontSize = 17.sp,
                )
                Text(
                    text = "Browse by provider",
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.TextMuted,
                    fontSize = 12.sp,
                )
            }
        }

        // Horizontal Platform Cards Rail
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            LazyRow(
                state = listState,
                modifier = Modifier.fillMaxWidth(),
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

            // Scroll Nav Left
            val canScrollLeft by remember { derivedStateOf { listState.canScrollBackward } }
            androidx.compose.animation.AnimatedVisibility(
                visible = canScrollLeft,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 2.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(30.dp),
                    onClick = {
                        coroutineScope.launch { listState.animateScrollBy(-400f) }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Scroll Left",
                            tint = theme.TextPrimary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            // Scroll Nav Right
            val canScrollRight by remember { derivedStateOf { listState.canScrollForward } }
            androidx.compose.animation.AnimatedVisibility(
                visible = canScrollRight,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 2.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(30.dp),
                    onClick = {
                        coroutineScope.launch { listState.animateScrollBy(400f) }
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Scroll Right",
                            tint = theme.TextPrimary,
                            modifier = Modifier.size(18.dp),
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
                    .background(platform.brandColor.copy(alpha = glowAlpha), cardShape)
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
                        if (isHovered) platform.brandColor.copy(alpha = borderAlpha) else Color.White.copy(alpha = borderAlpha)
                    ),
                    cardShape
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
                            )
                        )
                    )
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                // Official Packaged Vector Brand Hub Logo
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
            }
        }
    }
}
