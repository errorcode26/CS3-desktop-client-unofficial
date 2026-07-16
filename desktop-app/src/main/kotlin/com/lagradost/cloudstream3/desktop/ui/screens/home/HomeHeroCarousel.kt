package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.screens.details.GlobalDetailsCache
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class HeroMeta(
    val title: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val tags: List<String>,
    val plot: String?,
    val score: String?,
    val year: Int?,
    val type: com.lagradost.cloudstream3.TvType?,
)

object HeroCache {
    val cache = java.util.concurrent.ConcurrentHashMap<String, HeroMeta>()
}

fun cleanHeroTitle(raw: String): String {
    var cleaned = raw
    
    // 1. Strip trailing year and anything after: "Ragnarok (2020) 1080p" -> "Ragnarok"
    cleaned = cleaned.replace(Regex("""\s*\(\d{4}\).*"""), "")
    
    // 2. Strip bracketed content anywhere: "[Dub] Ragnarok [1080p]" -> " Ragnarok "
    cleaned = cleaned.replace(Regex("""\[.*?\]|\(.*?\)|\{.*?\}"""), " ")
    
    // 3. Strip common quality words
    cleaned = cleaned.replace(Regex("""(?i)\b(dual audio|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray)\b.*"""), "")
    
    // 4. Remove extra spaces
    cleaned = cleaned.replace(Regex("""\s+"""), " ").trim()
    
    // 5. Safely split by | and take the first valid part (e.g., "Ragnarok | HD")
    cleaned = cleaned.split("|").firstOrNull()?.trim() ?: cleaned
    
    return cleaned.takeIf { it.isNotBlank() } ?: raw.trim()
}

@OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.ui.ExperimentalComposeUiApi::class
)
@Composable
fun HomeHeroCarousel(items: List<SearchResponse>, provider: MainAPI?, viewModel: HomeViewModel, onItemClick: (SearchResponse, String?, Boolean) -> Unit) {
    if (items.isEmpty()) return

    val displayItems = items.take(10)
    val maxPages = displayItems.size * 1000
    val initialPage = maxPages / 2
    val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { maxPages })
    val heroMetaMap by viewModel.heroMetaMap.collectAsState()
    val scope = rememberCoroutineScope()
    var isHovered by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    LaunchedEffect(isHovered) {
        if (isHovered) {
            // If the user hovers while it's mid-scroll or somehow stuck offset,
            // snap smoothly to the closest page so it doesn't stay stuck.
            if (pagerState.currentPageOffsetFraction != 0f || pagerState.isScrollInProgress) {
                try {
                    pagerState.animateScrollToPage(pagerState.currentPage, animationSpec = tween(durationMillis = 300))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    if (!isActive) throw e
                }
            }
        } else {
            while (true) {
                delay(5000)
                if (!pagerState.isScrollInProgress) {
                    try {
                        val next = pagerState.currentPage + 1
                        pagerState.animateScrollToPage(
                            page = next,
                            animationSpec = tween(durationMillis = 1200),
                        )
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        if (!isActive) throw e
                    }
                }
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val pagesToFetch = listOf(
            pagerState.currentPage % displayItems.size,
            (pagerState.currentPage + 1) % displayItems.size,
        ).distinct()

        for (pageIdx in pagesToFetch) {
            val item = displayItems[pageIdx]
            viewModel.prefetchHeroItem(provider, item)
        }
    }

    // The actual surface color the content rows sit on (SurfaceCard via MaterialTheme.colorScheme.surface)
    val surfaceColor = LocalDesktopTheme.current.SurfaceCard
    val heroFade = surfaceColor

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp) // Fixed height instead of aspect ratio so it doesn't get ridiculously tall on ultra-wides
            .padding(top = 16.dp, bottom = 0.dp)
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Enter) { isHovered = true }
            .onPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Exit) { isHovered = false }
            .graphicsLayer { clip = false },
    ) {
        val currentRealPage = if (displayItems.isNotEmpty()) pagerState.currentPage % displayItems.size else 0
        val currentItem = displayItems.getOrNull(currentRealPage)
        val currentMeta = currentItem?.let { heroMetaMap[it.url] }
        val ambientBg = currentMeta?.backdropUrl ?: provider?.fixUrlNull(currentItem?.posterUrl)
        
        val isLightMode by AppearanceConfig.isLightMode.collectAsState()
        
        // Ambient Glow behind the carousel (disabled in light mode)
        if (ambientBg != null && !isLightMode) {
            AsyncImage(
                model = ambientBg,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .offset(y = (-40).dp)
                    .blur(140.dp)
                    .graphicsLayer { alpha = 0.65f },
                alignment = Alignment.TopCenter,
            )
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 220.dp), // Massive padding for significant peeking effect
            pageSpacing = 32.dp,
        ) { page ->
            val realPage = page % displayItems.size
            val item = displayItems[realPage]
            val posterUrl = provider?.fixUrlNull(item.posterUrl)
            val meta = heroMetaMap[item.url]

            val backdropAlpha by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (meta?.backdropUrl != null) 1f else 0f,
                animationSpec = androidx.compose.animation.core.tween(1000),
                label = "backdropFade",
            )

            // Calculate scale and alpha for coverflow effect
            val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            val pageOffset = rawOffset.absoluteValue

            val cardScale = 1f - (pageOffset * 0.15f).coerceIn(0f, 0.15f)
            val cardAlpha = 1f - (pageOffset * 0.3f).coerceIn(0f, 0.3f)

            val density = androidx.compose.ui.platform.LocalDensity.current.density

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(1f - pageOffset) // Center card gets the highest zIndex so it stacks on top
                    .graphicsLayer {
                        scaleX = cardScale
                        scaleY = cardScale
                        alpha = cardAlpha

                        // Coverflow stacking logic:
                        // Pull the side cards strongly towards the center so they slide underneath
                        translationX = rawOffset * 150f * density
                    }
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(32.dp),
                        ambientColor = Color.Black,
                        spotColor = Color.Black
                    )
                    .border(
                        width = 1.dp,
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .clip(RoundedCornerShape(32.dp)), // Large rounded corners like the screenshot
            ) {
                // Blurred portrait poster — immediate placeholder
                if (posterUrl != null) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(0.20f),
                    )
                }

                // Widescreen backdrop — full image visible, no crop
                if (meta?.backdropUrl != null) {
                    AsyncImage(
                        model = meta.backdropUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop, // Crop to fill the rounded card beautifully
                        modifier = Modifier.fillMaxSize().alpha(backdropAlpha),
                    )
                }

                val isLightMode by AppearanceConfig.isLightMode.collectAsState()

                // Vertical gradient — fade only needed in dark/AMOLED to blend into dark bg; none in light mode
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0.0f to Color.Transparent,
                            0.40f to Color.Transparent,
                            1.0f to if (isLightMode) Color.Transparent else heroFade.copy(alpha = 0.9f),
                        ),
                    ),
                )
                // Horizontal vignette — only in dark/AMOLED for text legibility
                Box(
                    modifier = Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            0.0f to if (isLightMode) Color.Transparent else heroFade.copy(alpha = 0.85f),
                            0.35f to if (isLightMode) Color.Transparent else heroFade.copy(alpha = 0.5f),
                            0.60f to Color.Transparent,
                        ),
                    ),
                )

                // Foreground content (Metadata text directly on backdrop)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 56.dp, end = 56.dp, bottom = 48.dp, top = 24.dp), // Adjusted padding for rounded card
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.Start,
                ) {
                    if (posterUrl != null) {
                        AsyncImage(
                            model = posterUrl,
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
                        modifier = Modifier.weight(1f), // Take up remaining space comfortably
                    ) {


                        // Title or Logo
                        val isLightMode by AppearanceConfig.isLightMode.collectAsState()
                        if (!meta?.logoUrl.isNullOrBlank()) {
                            val displayTitle = meta?.title ?: cleanHeroTitle(item.name)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .heightIn(max = 120.dp)
                                    .then(
                                        if (isLightMode) {
                                            Modifier.background(
                                                Brush.radialGradient(
                                                    colors = listOf(Color.Black.copy(alpha = 0.25f), Color.Transparent),
                                                    radius = 300f
                                                )
                                            )
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.BottomStart
                            ) {
                                coil3.compose.SubcomposeAsyncImage(
                                    model = meta!!.logoUrl,
                                    contentDescription = "Logo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 120.dp),
                                    contentScale = ContentScale.Fit,
                                    alignment = Alignment.BottomStart,
                                    error = {
                                        if (displayTitle.isNotBlank()) {
                                            Text(
                                                text = displayTitle,
                                                style = MaterialTheme.typography.displayMedium,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                lineHeight = 48.sp,
                                            )
                                        }
                                    }
                                )
                            }
                        } else {
                            val displayTitle = meta?.title ?: cleanHeroTitle(item.name)
                            if (displayTitle.isNotBlank()) {
                                Text(
                                    text = displayTitle,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 48.sp,
                                )
                            }
                        }

                        // Rating & Year
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (meta?.score != null && meta.score.toDoubleOrNull()?.let { it > 0.0 } == true) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = Color(0xFFFFD700), // Gold
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = meta.score,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Spacer(Modifier.width(16.dp))
                            }
                            if (meta?.year != null) {
                                Text(
                                    text = meta.year.toString(),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }

                        // Genre pills
                        if (!meta?.tags.isNullOrEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                meta!!.tags.distinct().take(4).forEach { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                                            .padding(horizontal = 12.dp, vertical = 6.dp),
                                    ) {
                                        Text(tag, color = MaterialTheme.colorScheme.onSurface, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }

                        // Plot synopsis
                        if (!meta?.plot.isNullOrBlank()) {
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = meta!!.plot!!,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 22.sp,
                            )
                        }

                        Spacer(Modifier.height(26.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = { onItemClick(item, meta?.backdropUrl, true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLightMode) MaterialTheme.colorScheme.primary else Color.White,
                                    contentColor = if (isLightMode) Color.White else Color.Black
                                ),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Play Now", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            
                            Button(
                                onClick = { onItemClick(item, meta?.backdropUrl, false) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isLightMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f),
                                    contentColor = if (isLightMode) MaterialTheme.colorScheme.onSurface else Color.White
                                ),
                                shape = RoundedCornerShape(24.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isLightMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.5f)
                                ),
                                contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
                            ) {
                                Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("More Info", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }

                            val bookmarkId = if (provider != null) "${provider.name}_${item.url.hashCode()}" else ""
                            var isBookmarked by remember(bookmarkId) { mutableStateOf(if (bookmarkId.isNotEmpty()) DesktopDataStore.isBookmarked(bookmarkId) else false) }

                            IconButton(
                                onClick = {
                                    if (provider == null) return@IconButton
                                    if (isBookmarked) {
                                        DesktopDataStore.removeBookmark(bookmarkId)
                                    } else {
                                        DesktopDataStore.addBookmark(
                                            DesktopBookmark(
                                                id = bookmarkId,
                                                name = item.name,
                                                url = item.url,
                                                apiName = provider.name,
                                                posterUrl = item.posterUrl,
                                            ),
                                        )
                                    }
                                    isBookmarked = !isBookmarked
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(
                                        if (isLightMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (isLightMode) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.5f),
                                        CircleShape
                                    ),
                            ) {
                                Icon(
                                    imageVector = com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library,
                                    contentDescription = "Bookmark",
                                    tint = if (isBookmarked) MaterialTheme.colorScheme.primary else if (isLightMode) MaterialTheme.colorScheme.onSurface else Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Left/Right buttons
        IconButton(
            onClick = {
                scope.launch {
                    val next = if (pagerState.isScrollInProgress) pagerState.targetPage - 1 else pagerState.currentPage - 1
                    pagerState.animateScrollToPage(
                        page = next,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000),
                    )
                }
            },
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 104.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape),
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(32.dp))
        }

        IconButton(
            onClick = {
                scope.launch {
                    val next = if (pagerState.isScrollInProgress) pagerState.targetPage + 1 else pagerState.currentPage + 1
                    pagerState.animateScrollToPage(
                        page = next,
                        animationSpec = androidx.compose.animation.core.tween(durationMillis = 1000),
                    )
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp).background(Color.Black.copy(alpha = 0.3f), CircleShape),
        ) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(32.dp))
        }

        // Pill-style page indicators
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(displayItems.size) { dotIndex ->
                val isSelected = (pagerState.currentPage % displayItems.size) == dotIndex
                val dotWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isSelected) 24.dp else 6.dp,
                    animationSpec = androidx.compose.animation.core.spring(stiffness = 600f),
                    label = "dot_w_$dotIndex",
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .width(dotWidth)
                        .clip(CircleShape)
                        .background(if (isSelected) DesktopUi.Accent else DesktopUi.TextMuted.copy(alpha = 0.5f)),
                )
            }
        }
    }
}
