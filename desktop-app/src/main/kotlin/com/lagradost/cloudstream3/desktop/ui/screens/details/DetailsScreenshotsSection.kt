package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage

@Composable
fun DetailsScreenshotsSection(
    screenshots: List<String>,
    onThumbnailClick: (Int) -> Unit,
    horizontalPadding: Dp = 24.dp,
) {
    if (screenshots.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Screenshots",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
            ) {
                Text(
                    text = "${screenshots.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                )
            }
        }

        // Cinematic 16:9 Locked Mosaic
        ScreenshotMosaic(
            screenshots = screenshots,
            onThumbnailClick = onThumbnailClick,
            horizontalPadding = horizontalPadding,
        )
    }
}

// ─── 16:9 Mosaic ─────────────────────────────────────────────────────────────

@Composable
private fun ScreenshotMosaic(
    screenshots: List<String>,
    onThumbnailClick: (Int) -> Unit,
    horizontalPadding: Dp,
) {
    val gap = 6.dp
    val cornerRadius = 14.dp

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
    ) {
        // Lock max width to prevent absurd ultra-wide stretching on 1440p+ monitors
        val effectiveWidth = minOf(maxWidth, 1360.dp)
        // Both left and right halves have width = (effectiveWidth - gap) / 2
        val halfWidth = (effectiveWidth - gap) / 2
        // Left slot has native 16:9 aspect ratio => height = halfWidth * (9 / 16)
        val mosaicHeight = halfWidth * (9f / 16f)

        Box(
            modifier = Modifier
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(
                modifier = Modifier
                    .width(effectiveWidth)
                    .height(mosaicHeight)
                    .clip(RoundedCornerShape(cornerRadius)),
                horizontalArrangement = Arrangement.spacedBy(gap),
            ) {
                // Featured left slot (16:9 aspect ratio)
                if (screenshots.isNotEmpty()) {
                    MosaicCell(
                        url = screenshots[0],
                        modifier = Modifier
                            .width(halfWidth)
                            .fillMaxHeight(),
                        onClick = { onThumbnailClick(0) },
                    )
                }

                // Right 2x2 grid (each cell is also 16:9 aspect ratio)
                if (screenshots.size > 1) {
                    val rightItems = screenshots.drop(1).take(3)
                    val remainder = screenshots.size - 1 - rightItems.size

                    Column(
                        modifier = Modifier
                            .width(halfWidth)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(gap),
                    ) {
                        // Top row of right side
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(gap),
                        ) {
                            MosaicCell(
                                url = rightItems.getOrNull(0) ?: "",
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                onClick = { onThumbnailClick(1) },
                            )
                            if (rightItems.size >= 2) {
                                MosaicCell(
                                    url = rightItems[1],
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = { onThumbnailClick(2) },
                                )
                            }
                        }

                        // Bottom row of right side
                        if (rightItems.size >= 3) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(gap),
                            ) {
                                MosaicCell(
                                    url = rightItems[2],
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                    onClick = { onThumbnailClick(3) },
                                )

                                if (remainder > 0) {
                                    // +N more cell with dark frosted glass overlay
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .clickable { onThumbnailClick(4) },
                                    ) {
                                        if (screenshots.size > 4) {
                                            AsyncImage(
                                                model = screenshots[4],
                                                contentDescription = null,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.68f)),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(2.dp),
                                            ) {
                                                Text(
                                                    text = "+${remainder + 1}",
                                                    style = MaterialTheme.typography.titleLarge,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 22.sp,
                                                )
                                                Text(
                                                    text = "more",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color.White.copy(alpha = 0.75f),
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
        }
    }
}

@Composable
private fun MosaicCell(
    url: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (url.isBlank()) return

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.025f else 1.0f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
    )

    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        )
        // Subtle hover brightness lift and edge vignette
        if (isHovered) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.08f)),
            )
        }
    }
}

// ─── Theater-Grade Lightbox ───────────────────────────────────────────────────

@Composable
fun ScreenshotLightbox(
    screenshots: List<String>,
    initialIndex: Int,
    onDismiss: () -> Unit,
) {
    var currentIndex by remember(initialIndex) {
        mutableStateOf(initialIndex.coerceIn(0, screenshots.lastIndex))
    }
    val filmstripState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(currentIndex) {
        filmstripState.animateScrollToItem(
            index = currentIndex,
            scrollOffset = -180,
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090B).copy(alpha = 0.97f))
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.DirectionRight, Key.D -> {
                            if (currentIndex < screenshots.lastIndex) currentIndex++
                            true
                        }
                        Key.DirectionLeft, Key.A -> {
                            if (currentIndex > 0) currentIndex--
                            true
                        }
                        Key.Escape -> {
                            onDismiss()
                            true
                        }
                        else -> false
                    }
                } else {
                    false
                }
            },
    ) {
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        // Top Gradient Scrim
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.75f), Color.Transparent),
                    ),
                ),
        )

        // Bottom Gradient Scrim behind dock
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    ),
                ),
        )

        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // ── Top Bar ──────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Left Placeholder for balance
                Spacer(modifier = Modifier.width(42.dp))

                // Centered Capsule Pill
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                        )
                        Text(
                            text = "${currentIndex + 1} of ${screenshots.size}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.35f),
                        )
                        Text(
                            text = "Screenshots",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.65f),
                        )
                    }
                }

                // Close Button
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                ) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(38.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }

            // ── Main Stage ───────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = currentIndex,
                    transitionSpec = {
                        val isNext = targetState > initialState
                        val slideOffset = if (isNext) 80 else -80
                        (
                            slideInHorizontally(tween(220, easing = FastOutSlowInEasing)) { slideOffset } +
                                fadeIn(tween(200))
                            )
                            .togetherWith(
                                slideOutHorizontally(tween(180, easing = FastOutSlowInEasing)) { -slideOffset } +
                                    fadeOut(tween(160)),
                            )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 80.dp, vertical = 8.dp),
                ) { index ->
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = screenshots[index],
                            contentDescription = "Screenshot ${index + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(12.dp)),
                        )
                    }
                }

                // Left Arrow
                NavArrow(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    enabled = currentIndex > 0,
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 18.dp),
                    onClick = { if (currentIndex > 0) currentIndex-- },
                )

                // Right Arrow
                NavArrow(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    enabled = currentIndex < screenshots.lastIndex,
                    modifier = Modifier.align(Alignment.CenterEnd).padding(end = 18.dp),
                    onClick = { if (currentIndex < screenshots.lastIndex) currentIndex++ },
                )
            }

            // ── Docked Filmstrip Shelf ───────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LazyRow(
                    state = filmstripState,
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                ) {
                    itemsIndexed(screenshots, key = { index, url -> "${url}_$index" }) { index, url ->
                        val isActive = index == currentIndex

                        Box(
                            modifier = Modifier
                                .height(52.dp)
                                .aspectRatio(16f / 9f)
                                .clip(RoundedCornerShape(8.dp))
                                .border(
                                    width = if (isActive) 2.dp else 1.dp,
                                    color = if (isActive) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { currentIndex = index },
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (!isActive) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Discreet Keyboard Shortcut Hint
                Text(
                    text = "← → Navigate  •  Esc Close",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.38f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp,
                )
            }
        }
    }
}

@Composable
private fun NavArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    if (!enabled) return

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        shape = CircleShape,
        color = if (isHovered) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = if (isHovered) 0.25f else 0.12f)),
        modifier = modifier
            .size(46.dp)
            .hoverable(interactionSource)
            .clickable(interactionSource = interactionSource, indication = null) { onClick() },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}
