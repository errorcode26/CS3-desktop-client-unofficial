package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
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

    val displayItems = remember(items, heroMetaMap) {
        items.filter { item ->
            val meta = heroMetaMap[item.url]
            // Keep if still loading (null) OR if it successfully found a backdrop
            meta == null || meta.backdropUrl != null
        }.take(10)
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
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(dynamicHeight)
            .graphicsLayer { clip = false },
    ) {
        val safeArea = com.lagradost.cloudstream3.desktop.ui.LocalSafeArea.current
        val safeStart = safeArea.calculateStartPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
        val safeEnd = safeArea.calculateEndPadding(androidx.compose.ui.platform.LocalLayoutDirection.current)
        val safeBottom = safeArea.calculateBottomPadding()

        // Convert auto-slide delay to ms for the progress bar animation
        val autoAdvanceIntervalMs = autoSlideDelay * 1000L

        // 5% proportional safe edge, guaranteeing at least 48dp buffer on top of any dock.
        val proportionalEdge = (maxWidth * 0.05f).coerceAtLeast(48.dp)

        val paddingStart = safeStart + proportionalEdge
        val paddingEnd = safeEnd + proportionalEdge

        // Thumbnails dynamically sized based on height
        val thumbnailHeight = (maxHeight * 0.22f).coerceIn(160.dp, 280.dp)
        val thumbnailWidth = thumbnailHeight * (2f / 3f)
        val thumbnailsMaxWidth = maxWidth * 0.55f

        // Push info block up exactly above the thumbnails
        val infoBlockBottomPadding = safeBottom + (maxHeight * 0.05f) + thumbnailHeight + 48.dp

        // The info block should take about 45% of the screen width for optimal readability
        val infoBlockMaxWidth = (maxWidth * 0.45f).coerceIn(400.dp, 750.dp)

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
                                .size(1280, 720)
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = infoBlockBottomPadding),
                    contentAlignment = Alignment.BottomStart,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = paddingStart, end = paddingEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        Column(
                            modifier = Modifier.widthIn(max = infoBlockMaxWidth), // Wrap text properly with proportional max width
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
                                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                                                        Text(
                                                            text = displayTitle,
                                                            style = MaterialTheme.typography.displayLarge.copy(
                                                                shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
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
                                            imageVector = if (currentBookmark != null) Icons.Default.Check else Icons.Default.Add,
                                            contentDescription = "Library",
                                            tint = Color.White,
                                            modifier = Modifier.size(26.dp),
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

        // --- Cinematic Filmstrip ---
        // Anchored to BottomEnd. We remove the hard right padding from the container
        // and instead add it to the LazyRow's max width and contentPadding.
        // This keeps the left edge (selected poster) exactly in the same position,
        // but allows the right side to extend all the way to the screen edge so posters
        // don't clip abruptly.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { clip = false }
                .padding(bottom = safeBottom + (maxHeight * 0.04f)),
            contentAlignment = Alignment.BottomEnd,
        ) {
            val listState = androidx.compose.foundation.lazy.rememberLazyListState(
                initialFirstVisibleItemIndex = if (displayItems.isNotEmpty()) displayItems.size * 1000 else 0,
            )
            LaunchedEffect(globalIndex) {
                listState.animateScrollToItem(maxOf(0, globalIndex - 1))
            }

            LazyRow(
                state = listState,
                modifier = Modifier.widthIn(max = thumbnailsMaxWidth + paddingEnd).graphicsLayer { clip = false },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Bottom,
                contentPadding = PaddingValues(end = paddingEnd),
            ) {
                if (displayItems.isNotEmpty()) {
                    items(Int.MAX_VALUE) { globalThumbIndex ->
                        val itemIndex = globalThumbIndex % displayItems.size
                        val item = displayItems[itemIndex]
                        val posterUrl = provider?.fixUrlNull(item.posterUrl)
                        val thumbUrl = posterUrl ?: heroMetaMap[item.url]?.backdropUrl
                        val isSelected = globalThumbIndex == globalIndex

                        if (thumbUrl != null) {
                            // Animate the actual Dp height — this correctly affects layout bounds
                            val posterHeight by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (isSelected) thumbnailHeight else thumbnailHeight * 0.72f,
                                animationSpec = tween(350),
                                label = "poster_height",
                            )
                            val thumbAlpha by animateFloatAsState(
                                targetValue = if (isSelected) 1f else 0.45f,
                                animationSpec = tween(350),
                                label = "thumb_alpha",
                            )

                            // Progress tracking for the selected poster
                            var progressFraction by remember { mutableFloatStateOf(0f) }
                            LaunchedEffect(isSelected, globalIndex) {
                                if (isSelected) {
                                    progressFraction = 0f
                                    val steps = 80
                                    val stepDelay = autoAdvanceIntervalMs / steps
                                    repeat(steps) {
                                        delay(stepDelay)
                                        progressFraction = (it + 1f) / steps
                                    }
                                } else {
                                    progressFraction = 0f
                                }
                            }
                            val animatedProgress by animateFloatAsState(
                                targetValue = progressFraction,
                                animationSpec = tween(80, easing = LinearEasing),
                                label = "progress",
                            )

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                modifier = Modifier.alpha(thumbAlpha),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .height(posterHeight)
                                        .aspectRatio(2f / 3f),
                                ) {
                                    AsyncImage(
                                        model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                            .data(thumbUrl)
                                            .size(240, 360)
                                            .build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(8.dp))
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        width = 1.5.dp,
                                                        brush = Brush.verticalGradient(
                                                            listOf(
                                                                Color.White.copy(alpha = 0.95f),
                                                                Color.White.copy(alpha = 0.25f),
                                                            ),
                                                        ),
                                                        shape = RoundedCornerShape(8.dp),
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable { globalIndex = globalThumbIndex },
                                    )
                                }

                                // Progress bar — only rendered for the selected item
                                val posterWidth = posterHeight * (2f / 3f)
                                Box(
                                    modifier = Modifier
                                        .width(posterWidth)
                                        .height(2.dp)
                                        .clip(RoundedCornerShape(1.dp))
                                        .background(Color.White.copy(alpha = if (isSelected) 0.2f else 0.08f)),
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .fillMaxWidth(animatedProgress)
                                                .clip(RoundedCornerShape(1.dp))
                                                .background(Color.White),
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
