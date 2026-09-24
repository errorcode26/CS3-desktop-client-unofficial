package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
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
        targetValue = if (isHovered) 0.70f else 0.16f,
        animationSpec = tween(durationMillis = 180),
        label = "platform_card_border",
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isHovered) 0.35f else 0.0f,
        animationSpec = tween(durationMillis = 200),
        label = "platform_card_glow",
    )

    val cardShape = RoundedCornerShape(13.dp)

    Box(
        modifier = modifier
            .width(175.dp)
            .height(74.dp)
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
                    .padding(2.dp)
                    .blur(18.dp)
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
                    BorderStroke(1.dp, if (isHovered) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.08f)),
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
                                Color(0xFF1B1B22),
                                Color(0xFF111115),
                            )
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    // Top Row: Brand Mark
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StreamingPlatformBrandLogo(platform)
                    }

                    // Bottom Row: Refined Subtitle
                    Text(
                        text = platform.subtitle,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White.copy(alpha = if (isHovered) 0.85f else 0.45f),
                        maxLines = 1,
                        letterSpacing = 0.2.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StreamingPlatformBrandLogo(platform: StreamingPlatform) {
    when (platform) {
        StreamingPlatform.NETFLIX -> {
            Text(
                text = "NETFLIX",
                color = Color.White,
                fontSize = 18.5.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = 2.sp,
            )
        }
        StreamingPlatform.DISNEY_PLUS -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Disney",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.4.sp,
                )
                Text(
                    text = "+",
                    color = Color(0xFFE2E8F0),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.offset(y = (-1).dp),
                )
            }
        }
        StreamingPlatform.PRIME_VIDEO -> {
            Column {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "prime",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = (-0.4).sp,
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "video",
                        color = Color(0xFFCBD5E1),
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Box(
                    modifier = Modifier
                        .padding(top = 1.dp)
                        .width(34.dp)
                        .height(2.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color.White.copy(alpha = 0.70f))
                )
            }
        }
        StreamingPlatform.APPLE_TV -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = "",
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "tv+",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-0.5).sp,
                )
            }
        }
        StreamingPlatform.HULU -> {
            Text(
                text = "hulu",
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-0.5).sp,
            )
        }
        StreamingPlatform.MAX -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "MAX",
                    color = Color.White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                )
                Box(
                    modifier = Modifier
                        .padding(start = 5.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.70f))
                )
            }
        }
        StreamingPlatform.PARAMOUNT_PLUS -> {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Paramount",
                    color = Color.White,
                    fontSize = 17.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.2).sp,
                )
                Text(
                    text = "+",
                    color = Color(0xFFE2E8F0),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.offset(y = (-1).dp),
                )
            }
        }
        StreamingPlatform.CRUNCHYROLL -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(15.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.5.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF141418))
                    )
                }
                Text(
                    text = "crunchyroll",
                    color = Color.White,
                    fontSize = 15.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.3).sp,
                )
            }
        }
    }
}
