package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData
import kotlinx.coroutines.launch

@Composable
fun DetailsTrailersSection(
    trailers: List<TrailerData>,
    trailersExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onTrailerClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val cleanTrailers = remember(trailers) {
        trailers
            .distinctBy { it.rawKey }
            .distinctBy { it.name.lowercase().trim() }
    }
    if (cleanTrailers.isEmpty()) return

    // Group videos by category
    val categoriesMap = remember(cleanTrailers) {
        val map = linkedMapOf<String, MutableList<TrailerData>>()
        cleanTrailers.forEach { t ->
            val cat = when {
                t.type.contains("Teaser", ignoreCase = true) -> "Teasers"
                t.type.contains("Behind", ignoreCase = true) || t.type.contains("Bts", ignoreCase = true) -> "Behind the Scenes"
                t.type.contains("Featurette", ignoreCase = true) -> "Featurettes"
                t.type.contains("Clip", ignoreCase = true) -> "Clips"
                t.type.contains("Blooper", ignoreCase = true) -> "Bloopers"
                else -> "Trailers"
            }
            map.getOrPut(cat) { mutableListOf() }.add(t)
        }
        map
    }

    var selectedCategory by remember(cleanTrailers) { mutableStateOf("All") }

    val displayTrailers = remember(selectedCategory, cleanTrailers, categoriesMap) {
        if (selectedCategory == "All") {
            cleanTrailers
        } else {
            categoriesMap[selectedCategory].orEmpty()
        }
    }

    val scrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Videos & Trailers",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f),
                ) {
                    Text(
                        text = "${cleanTrailers.size}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = if (trailersExpanded) "Collapse" else "Expand",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.rotate(if (trailersExpanded) 180f else 0f),
                )
            }

            // Scroll arrows
            if (trailersExpanded && displayTrailers.size > 2) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                ) {
                    Row {
                        IconButton(
                            onClick = { coroutineScope.launch { scrollState.animateScrollBy(-500f) } },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(
                            onClick = { coroutineScope.launch { scrollState.animateScrollBy(500f) } },
                            modifier = Modifier.size(32.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = trailersExpanded,
            enter = fadeIn(tween(200)) + androidx.compose.animation.expandVertically(tween(200)),
            exit = fadeOut(tween(200)) + androidx.compose.animation.shrinkVertically(tween(200)),
        ) {
            Column {
                // Category Filter Chips
                if (categoriesMap.size > 1) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = horizontalPadding)
                            .padding(bottom = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // "All" chip
                        CategoryFilterChip(
                            label = "All (${cleanTrailers.size})",
                            isSelected = selectedCategory == "All",
                            onClick = { selectedCategory = "All" },
                        )
                        // Individual category chips
                        categoriesMap.forEach { (catName, list) ->
                            CategoryFilterChip(
                                label = "$catName (${list.size})",
                                isSelected = selectedCategory == catName,
                                onClick = { selectedCategory = catName },
                            )
                        }
                    }
                }

                LazyRow(
                    state = scrollState,
                    contentPadding = PaddingValues(horizontal = horizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectHorizontalDragGestures { change, dragAmount ->
                                change.consume()
                                scrollState.dispatchRawDelta(-dragAmount)
                            }
                        },
                ) {
                    items(displayTrailers, key = { it.id }) { trailer ->
                        TrailerCard(
                            trailer = trailer,
                            onClick = { onTrailerClick(trailer.url) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryFilterChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable { onClick() },
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TrailerCard(
    trailer: TrailerData,
    onClick: () -> Unit,
) {
    var isHovered by remember { mutableStateOf(false) }
    val playButtonScale by animateFloatAsState(if (isHovered) 1.15f else 1.0f, animationSpec = tween(200))
    val cardBorderAlpha by animateFloatAsState(if (isHovered) 0.6f else 0.15f, animationSpec = tween(200))

    Surface(
        modifier = Modifier
            .width(320.dp)
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .border(
                1.2.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = cardBorderAlpha),
                RoundedCornerShape(12.dp),
            )
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> isHovered = false
                        }
                    }
                }
            }
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail Image
            if (!trailer.thumbnailUrl.isNullOrBlank()) {
                coil3.compose.AsyncImage(
                    model = trailer.thumbnailUrl,
                    contentDescription = trailer.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF1E1E28)),
                )
            }

            // Dark gradient protection overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.4f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.88f),
                            ),
                            startY = 0f,
                            endY = Float.POSITIVE_INFINITY,
                        ),
                    ),
            )

            // Top Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (trailer.isOfficial) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                        shadowElevation = 2.dp,
                    ) {
                        Text(
                            text = "OFFICIAL",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.8.sp,
                            ),
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(1.dp))
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                ) {
                    Text(
                        text = trailer.type.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        ),
                        color = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            // Center Play Button with Hover Animation
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .scale(playButtonScale)
                    .size(52.dp)
                    .shadow(8.dp, CircleShape)
                    .background(
                        if (isHovered) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.65f),
                        CircleShape,
                    )
                    .border(
                        1.5.dp,
                        if (isHovered) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.7f),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Trailer",
                    tint = if (isHovered) MaterialTheme.colorScheme.onPrimary else Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }

            // Bottom Name and Details
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = trailer.name,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
