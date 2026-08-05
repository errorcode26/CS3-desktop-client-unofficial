package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
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
import com.lagradost.cloudstream3.desktop.ui.DesktopDimens
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import kotlinx.coroutines.delay

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class,
)
@Composable
fun HomeHeroCarousel(
    items: List<SearchResponse>,
    provider: MainAPI?,
    heroMetaMap: Map<String, com.lagradost.cloudstream3.desktop.repo.HeroMeta>,
    allBookmarks: Map<String, DesktopBookmark>,
    onPrefetchHeroItem: (MainAPI?, SearchResponse) -> Unit,
    onHeroBackgroundChanged: (String?) -> Unit,
    onItemClick: (SearchResponse, String?, Boolean) -> Unit,
) {
    if (items.isEmpty()) return

    val displayItems by remember(items, heroMetaMap) {
        derivedStateOf {
            items.filter { item ->
                val meta = heroMetaMap[item.url]
                // Keep if still loading (null) OR if it successfully found a backdrop
                meta == null || meta.backdropUrl != null
            }.take(10)
        }
    }


    val autoSlideDelay by AppearanceConfig.heroAutoSlideDelaySeconds.collectAsState()
    val scope = rememberCoroutineScope()
    var globalIndex by androidx.compose.runtime.saveable.rememberSaveable {
        mutableStateOf(if (displayItems.isNotEmpty()) displayItems.size * 1000 else 0)
    }

    val currentIndex = if (displayItems.isNotEmpty()) globalIndex % displayItems.size else 0

    LaunchedEffect(displayItems.size, autoSlideDelay, globalIndex) {
        if (displayItems.isNotEmpty()) {
            delay(autoSlideDelay * 1000L)
            globalIndex++
        }
    }

    LaunchedEffect(items) {
        // Prefetch candidates sequentially. The first 10 will load in ~8 seconds.
        // If any fail and are filtered out, the later candidates will naturally fill the gaps.
        items.forEachIndexed { index, item ->
            if (index > 0) delay(800L)
            onPrefetchHeroItem(provider, item)
        }
    }

    LaunchedEffect(currentIndex) {
        val currentItem = displayItems.getOrNull(currentIndex)
        val currentMeta = currentItem?.let { heroMetaMap[it.url] }
        val colorSourceUrl = currentMeta?.backdropUrl ?: provider?.fixUrlNull(currentItem?.posterUrl)
        onHeroBackgroundChanged(colorSourceUrl)
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dynamicHeight = with(density) { windowInfo.containerSize.height.toDp() }.coerceAtLeast(400.dp)

    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    // 88.dp base padding + 10.dp internal padding = 98.dp
    val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 140.dp else 80.dp
    val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 98.dp else 32.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dynamicHeight),
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                fadeIn(animationSpec = tween(1000)) togetherWith fadeOut(animationSpec = tween(1000))
            },
            modifier = Modifier.fillMaxSize(),
            label = "hero_fade",
        ) { page ->
            val item = displayItems[page]
            val posterUrl = provider?.fixUrlNull(item.posterUrl)
            val meta = heroMetaMap[item.url]
            val ambientBg = meta?.backdropUrl ?: posterUrl

            Box(modifier = Modifier.fillMaxSize()) {
                // Per-page color: just use black since we rely on massive blur for aesthetics
                val pageScrimColor = Color.Black
                val verticalFadeBrush = remember {
                    Brush.verticalGradient(
                        0.0f to Color.Black,
                        0.65f to Color.Black,
                        1.0f to Color.Transparent,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithContent {
                            drawContent()
                            drawRect(
                                brush = verticalFadeBrush,
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
                            )
                        },
                ) {
                    if (ambientBg != null) {
                        AsyncImage(
                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                .data(ambientBg)
                                .size(2560, 1440)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize().then(
                                if (meta?.backdropUrl == null) Modifier.blur(24.dp) else Modifier,
                            ),
                        )
                    }

                    val hScrimColor = if (isLightMode) Color.Transparent else pageScrimColor.copy(alpha = 0.80f)
                    val hScrimBrush = remember(hScrimColor) {
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.0f to hScrimColor,
                                0.60f to Color.Transparent,
                            ),
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(hScrimBrush))

                    val vBottomAlpha = if (isLightMode) 0f else 0.35f
                    val vScrimBrush = remember(pageScrimColor, isLightMode) {
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.0f to Color.Transparent,
                                0.40f to Color.Transparent,
                                0.75f to pageScrimColor.copy(alpha = if (isLightMode) 0f else 0.25f),
                                1.0f to pageScrimColor.copy(alpha = if (isLightMode) 0f else vBottomAlpha),
                            ),
                        )
                    }
                    Box(modifier = Modifier.fillMaxSize().background(vScrimBrush))
                }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = paddingStart, end = paddingEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        if (posterUrl != null && meta?.backdropUrl == null) {
                            AsyncImage(
                                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(posterUrl)
                                    .size(320, 480)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .width(160.dp)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                    .shadow(16.dp, RoundedCornerShape(12.dp)),
                            )
                            Spacer(modifier = Modifier.width(32.dp))
                        }

                        Column(
                            modifier = Modifier.widthIn(max = 500.dp), // Wrap text properly, don't stretch to middle
                        ) {
                            Column(modifier = Modifier.height(350.dp), verticalArrangement = Arrangement.Bottom) {
                                if (!meta?.logoUrl.isNullOrBlank()) {
                                    val displayTitle = meta?.title ?: com.lagradost.cloudstream3.desktop.repo.HeroRepository.cleanHeroTitle(item.name)
                                    Box(
                                        modifier = Modifier
                                            .widthIn(
                                                min = DesktopDimens.HeroLogoMinWidth,
                                                max = DesktopDimens.HeroLogoMaxWidth,
                                            )
                                            .heightIn(max = DesktopDimens.HeroLogoMaxHeight),
                                        contentAlignment = Alignment.BottomStart,
                                    ) {
                                        coil3.compose.AsyncImage(
                                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                                .data(meta?.logoUrl)
                                                .size(1600, 800)
                                                .build(),
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .offset(
                                                    x = DesktopDimens.LogoShadowOffsetX,
                                                    y = DesktopDimens.LogoShadowOffsetY,
                                                )
                                                .blur(
                                                    DesktopDimens.LogoShadowBlur,
                                                    edgeTreatment = androidx.compose.ui.draw.BlurredEdgeTreatment.Unbounded,
                                                ),
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.BottomStart,
                                            colorFilter = DesktopDimens.LogoShadowFilter,
                                        )
                                        coil3.compose.SubcomposeAsyncImage(
                                            model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                                .data(meta?.logoUrl)
                                                .size(1600, 800)
                                                .build(),
                                            contentDescription = "Logo",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                            alignment = Alignment.BottomStart,
                                            error = {
                                                if (displayTitle.isNotBlank()) {
                                                    Text(
                                                        text = displayTitle,
                                                        style = MaterialTheme.typography.displayLarge.copy(
                                                            shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                                            fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                                            letterSpacing = (-1).sp,
                                                        ),
                                                        fontWeight = FontWeight.Black,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis,
                                                        lineHeight = 48.sp,
                                                    )
                                                }
                                            },
                                        )
                                    }
                                } else {
                                    val displayTitle = meta?.title ?: com.lagradost.cloudstream3.desktop.repo.HeroRepository.cleanHeroTitle(item.name)
                                    if (displayTitle.isNotBlank()) {
                                        Text(
                                            text = displayTitle,
                                            style = MaterialTheme.typography.displayLarge.copy(
                                                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                                fontFamily = androidx.compose.ui.text.font.FontFamily.Serif,
                                                letterSpacing = (-1).sp,
                                            ),
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            lineHeight = 48.sp,
                                        )
                                    }
                                }

                                // Rating, Year, and Genres on one line
                                Spacer(Modifier.height(20.dp))
                                Row(verticalAlignment = Alignment.Bottom) {
                                    if (meta?.score != null && meta.score.toDoubleOrNull()?.let { it > 0.0 } == true) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = "Rating",
                                            tint = Color(0xFFFFD700), // Gold
                                            modifier = Modifier.size(20.dp),
                                        )
                                        Spacer(Modifier.width(4.dp))
                                        Text(
                                            text = meta.score,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            style = androidx.compose.material3.LocalTextStyle.current.copy(
                                                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                            ),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                    }
                                    if (meta?.year != null) {
                                        Text(
                                            text = meta.year.toString(),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = androidx.compose.material3.LocalTextStyle.current.copy(
                                                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                            ),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                    }
                                    if (!meta?.contentRating.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                                .padding(horizontal = 6.dp, vertical = 2.dp),
                                        ) {
                                            Text(
                                                text = meta?.contentRating ?: "",
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                        Spacer(Modifier.width(14.dp))
                                    }
                                    if (meta?.duration != null && meta.duration > 0) {
                                        Text(
                                            text = "${meta.duration}m",
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            style = androidx.compose.material3.LocalTextStyle.current.copy(
                                                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                            ),
                                        )
                                        Spacer(Modifier.width(14.dp))
                                    }
                                    if (!meta?.tags.isNullOrEmpty()) {
                                        val tagsText = meta?.tags?.distinct()?.take(3)?.joinToString(" • ") ?: ""
                                        Text(
                                            text = tagsText,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = androidx.compose.material3.LocalTextStyle.current.copy(
                                                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                            ),
                                        )
                                    }
                                }

                                if (!meta?.plot.isNullOrBlank()) {
                                    Spacer(Modifier.height(16.dp))
                                    Text(
                                        text = meta?.plot ?: "",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                        fontSize = 15.sp,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 22.sp,
                                        style = androidx.compose.material3.LocalTextStyle.current.copy(
                                            shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                        ),
                                    )
                                }
                            } // End fixed height container

                            Spacer(Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onItemClick(item, meta?.backdropUrl, true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    contentPadding = PaddingValues(horizontal = 32.dp),
                                    modifier = Modifier.height(56.dp).widthIn(min = 190.dp),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(26.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                                }

                                Button(
                                    onClick = { onItemClick(item, meta?.backdropUrl, false) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.18f),
                                        contentColor = Color.White,
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.2.dp, Color.White.copy(alpha = 0.35f)),
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    modifier = Modifier.height(56.dp).widthIn(min = 160.dp),
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Details", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                }

                                val bookmarkId = if (provider != null) "${provider.name}_${item.url.hashCode()}" else ""
                                var showBookmarkMenu by remember { mutableStateOf(false) }
                                val currentBookmark = if (bookmarkId.isNotEmpty()) allBookmarks[bookmarkId] else null

                                Box {
                                    IconButton(
                                        onClick = { if (provider != null) showBookmarkMenu = true },
                                        modifier = Modifier
                                            .size(56.dp)
                                            .background(
                                                if (currentBookmark != null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.18f),
                                                RoundedCornerShape(12.dp),
                                            )
                                            .border(
                                                1.2.dp,
                                                if (currentBookmark != null) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f),
                                                RoundedCornerShape(12.dp),
                                            ),
                                    ) {
                                        Icon(
                                            imageVector = com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library,
                                            contentDescription = "Bookmark",
                                            tint = Color.White,
                                            modifier = Modifier.size(22.dp),
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
                                                        apiName = provider?.name ?: "",
                                                        posterUrl = item.posterUrl,
                                                        watchType = type.id,
                                                    )
                                                    com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.addBookmark(newBookmark)
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
                                                    com.lagradost.cloudstream3.desktop.repo.BookmarksRepository.removeBookmark(bookmarkId)
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
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 64.dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd,
            ) {
                Row(
                    modifier = Modifier.padding(end = paddingEnd),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    val listState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = if (displayItems.isNotEmpty()) displayItems.size * 1000 else 0)
                    LaunchedEffect(globalIndex) {
                        listState.animateScrollToItem(maxOf(0, globalIndex - 2))
                    }

                    LazyRow(
                        state = listState,
                        modifier = Modifier.widthIn(max = 816.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (displayItems.isNotEmpty()) {
                            items(Int.MAX_VALUE) { globalThumbIndex ->
                                val itemIndex = globalThumbIndex % displayItems.size
                                val item = displayItems[itemIndex]
                                val posterUrl = provider?.fixUrlNull(item.posterUrl)
                                val thumbUrl = posterUrl ?: heroMetaMap[item.url]?.backdropUrl

                                val isSelected = globalThumbIndex == globalIndex

                                if (thumbUrl != null) {
                                    val thumbHeight by androidx.compose.animation.core.animateDpAsState(
                                        targetValue = if (isSelected) 240.dp else 180.dp,
                                        animationSpec = androidx.compose.animation.core.tween(300),
                                    )
                                    val thumbAlpha by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isSelected) 1f else 0.5f,
                                        animationSpec = androidx.compose.animation.core.tween(300),
                                    )
                                    val borderWidth by androidx.compose.animation.core.animateDpAsState(
                                        targetValue = if (isSelected) 2.dp else 0.dp,
                                        animationSpec = androidx.compose.animation.core.tween(300),
                                    )
                                    val borderColor by androidx.compose.animation.animateColorAsState(
                                        targetValue = if (isSelected) Color.White else Color.Transparent,
                                        animationSpec = androidx.compose.animation.core.tween(300),
                                    )

                                    AsyncImage(
                                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                            .data(thumbUrl)
                                            .size(240, 360)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .height(thumbHeight)
                                            .aspectRatio(2f / 3f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(
                                                width = borderWidth,
                                                color = borderColor,
                                                shape = RoundedCornerShape(8.dp),
                                            )
                                            .alpha(thumbAlpha)
                                            .clickable {
                                                globalIndex = globalThumbIndex
                                            },
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
