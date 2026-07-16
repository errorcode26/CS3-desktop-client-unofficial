package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
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
    val contentRating: String?,
    val duration: Int?,
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
    val heroMetaMap by viewModel.heroMetaMap.collectAsState()
    val scope = rememberCoroutineScope()
    var currentIndex by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(6000)
            currentIndex = (currentIndex + 1) % displayItems.size
        }
    }

    LaunchedEffect(displayItems) {
        // Prefetch all hero items ASAP so backdrops are ready
        for (item in displayItems) {
            viewModel.prefetchHeroItem(provider, item)
        }
    }

    LaunchedEffect(currentIndex) {
        // Trigger dominant color extraction for the current hero item
        val currentItem = displayItems.getOrNull(currentIndex)
        val currentMeta = currentItem?.let { heroMetaMap[it.url] }
        val colorSourceUrl = currentMeta?.backdropUrl ?: provider?.fixUrlNull(currentItem?.posterUrl)
        viewModel.updateHeroColor(colorSourceUrl)
    }

    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dynamicHeight = with(density) { (windowInfo.containerSize.height * 0.85f).toDp() }.coerceIn(400.dp, 1000.dp)
    
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val layoutWidthSetting by AppearanceConfig.layoutWidth.collectAsState()
    val maxWidthConstraint = when (layoutWidthSetting) {
        "Compact" -> 1000.dp
        "Modern" -> 1400.dp
        else -> androidx.compose.ui.unit.Dp.Unspecified
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dynamicHeight)
    ) {
        AnimatedContent(
            targetState = currentIndex,
            transitionSpec = {
                fadeIn(animationSpec = tween(1000)) togetherWith fadeOut(animationSpec = tween(1000))
            },
            modifier = Modifier.fillMaxSize(),
            label = "hero_fade"
        ) { page ->
            val item = displayItems[page]
            val posterUrl = provider?.fixUrlNull(item.posterUrl)
            val meta = heroMetaMap[item.url]
            val ambientBg = meta?.backdropUrl ?: posterUrl
            
            Box(modifier = Modifier.fillMaxSize()) {
                // Group the background image and its overlay together to apply a unified alpha fade mask at the bottom
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen }
                        .drawWithContent {
                            drawContent()
                            // Smooth alpha fade for the ambient glow blend
                            drawRect(
                                brush = Brush.verticalGradient(
                                    0.0f to Color.Black,
                                    0.82f to Color.Black, // Hold opaque behind text
                                    1.0f to Color.Transparent // 18% smooth fade into ambient glow
                                ),
                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                            )
                        }
                ) {
                    // Background Image
                    if (ambientBg != null) {
                        AsyncImage(
                            model = ambientBg,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().then(
                                if (meta?.backdropUrl == null) Modifier.blur(24.dp) else Modifier
                            )
                        )
                    }

                    // Horizontal scrim from the left (provides contrast for the logo without creating a box)
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.horizontalGradient(
                                0.0f to if (isLightMode) Color.Transparent else Color.Black.copy(alpha = 0.5f), // Moderately dark on the far left edge
                                0.6f to Color.Transparent // Smoothly fades to transparent by the middle
                            )
                        )
                    )

                    // Gentle, full-height vertical scrim to eliminate any visible gradient "starting line"
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.0f to Color.Transparent,
                                0.40f to if (isLightMode) Color.Transparent else Color.Black.copy(alpha = 0.15f), // Imperceptible bridge
                                0.85f to if (isLightMode) Color.Transparent else Color.Black.copy(alpha = 0.85f), // Smoothly darkens for text
                                1.0f to if (isLightMode) Color.Transparent else Color.Black,
                            )
                        )
                    )

                }

                // Foreground content (Metadata text directly on backdrop)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        modifier = Modifier
                            .widthIn(max = maxWidthConstraint)
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 56.dp, top = 24.dp),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.Start,
                    ) {
                        if (posterUrl != null && meta?.backdropUrl == null) {
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
                            modifier = Modifier.fillMaxWidth(0.5f), // Leave right side for thumbnails
                        ) {
                            // Title or Logo
                            if (!meta?.logoUrl.isNullOrBlank()) {
                                val displayTitle = meta?.title ?: cleanHeroTitle(item.name)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.9f)
                                        .heightIn(max = 240.dp),
                                    contentAlignment = Alignment.BottomStart
                                ) {
                                    coil3.compose.SubcomposeAsyncImage(
                                        model = meta!!.logoUrl,
                                        contentDescription = "Logo",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 240.dp),
                                        contentScale = ContentScale.Fit,
                                        alignment = Alignment.BottomStart,
                                        error = {
                                            if (displayTitle.isNotBlank()) {
                                                Text(
                                                    text = displayTitle,
                                                    style = MaterialTheme.typography.displayLarge.copy(
                                                        shadow = androidx.compose.ui.graphics.Shadow(
                                                            color = Color.Black.copy(alpha = 0.69f),
                                                            offset = androidx.compose.ui.geometry.Offset(0f, 4f),
                                                            blurRadius = 8f
                                                        )
                                                    ),
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
                                        style = MaterialTheme.typography.displayLarge.copy(
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black.copy(alpha = 0.69f),
                                                offset = androidx.compose.ui.geometry.Offset(0f, 4f),
                                                blurRadius = 8f
                                            )
                                        ),
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 48.sp,
                                    )
                                }
                            }

                            // Rating, Year, and Genres on one line
                            Spacer(Modifier.height(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
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
                                        style = androidx.compose.ui.text.TextStyle(
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black.copy(alpha = 0.69f),
                                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                                blurRadius = 4f
                                            )
                                        )
                                    )
                                    Spacer(Modifier.width(14.dp))
                                }
                                if (meta?.year != null) {
                                    Text(
                                        text = meta.year.toString(),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        style = androidx.compose.ui.text.TextStyle(
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black.copy(alpha = 0.69f),
                                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                                blurRadius = 4f
                                            )
                                        )
                                    )
                                    Spacer(Modifier.width(14.dp))
                                }
                                if (!meta?.contentRating.isNullOrBlank()) {
                                    Box(
                                        modifier = Modifier
                                            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = meta!!.contentRating!!,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                }
                                if (meta?.duration != null) {
                                    Text(
                                        text = "${meta.duration}m",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        style = androidx.compose.ui.text.TextStyle(
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black.copy(alpha = 0.69f),
                                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                                blurRadius = 4f
                                            )
                                        )
                                    )
                                    Spacer(Modifier.width(14.dp))
                                }
                                if (!meta?.tags.isNullOrEmpty()) {
                                    val tagsText = meta!!.tags.distinct().take(4).joinToString(" • ")
                                    Text(
                                        text = tagsText,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        style = androidx.compose.ui.text.TextStyle(
                                            shadow = androidx.compose.ui.graphics.Shadow(
                                                color = Color.Black.copy(alpha = 0.69f),
                                                offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                                blurRadius = 4f
                                            )
                                        )
                                    )
                                }
                            }

                            // Plot synopsis
                            if (!meta?.plot.isNullOrBlank()) {
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    text = meta!!.plot!!,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                                    fontSize = 16.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    lineHeight = 24.sp,
                                    style = androidx.compose.ui.text.TextStyle(
                                        shadow = androidx.compose.ui.graphics.Shadow(
                                            color = Color.Black.copy(alpha = 0.69f),
                                            offset = androidx.compose.ui.geometry.Offset(0f, 2f),
                                            blurRadius = 4f
                                        )
                                    )
                                )
                            }

                            Spacer(Modifier.height(24.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = { onItemClick(item, meta?.backdropUrl, true) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White,
                                        contentColor = Color.Black
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 32.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Play", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                                }
                                
                                Button(
                                    onClick = { onItemClick(item, meta?.backdropUrl, false) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color.White.copy(alpha = 0.2f),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 24.dp),
                                    modifier = Modifier.height(56.dp)
                                ) {
                                    Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(22.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Details", fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
                                        .size(56.dp)
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                                ) {
                                    Icon(
                                        imageVector = com.lagradost.cloudstream3.desktop.ui.PremiumIcons.Library,
                                        contentDescription = "Bookmark",
                                        tint = if (isBookmarked) MaterialTheme.colorScheme.primary else Color.White,
                                        modifier = Modifier.size(24.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Thumbnail Row Navigation
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 48.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = maxWidthConstraint)
                    .fillMaxWidth(),
                contentAlignment = Alignment.BottomEnd
            ) {
                Row(
                    modifier = Modifier.padding(end = 16.dp),
                    verticalAlignment = Alignment.CenterVertically, 
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = { currentIndex = (currentIndex - 1 + displayItems.size) % displayItems.size },
                        modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous", tint = Color.White)
                    }

                    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
                    LaunchedEffect(currentIndex) {
                        listState.animateScrollToItem(maxOf(0, currentIndex - 2))
                    }

                    LazyRow(
                        state = listState,
                        modifier = Modifier.widthIn(max = 440.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        itemsIndexed(displayItems) { index, item ->
                            val posterUrl = provider?.fixUrlNull(item.posterUrl)
                            val thumbUrl = posterUrl ?: heroMetaMap[item.url]?.backdropUrl
                            
                            val isSelected = index == currentIndex
                            
                            if (thumbUrl != null) {
                                val thumbHeight by androidx.compose.animation.core.animateDpAsState(
                                    targetValue = if (isSelected) 140.dp else 110.dp,
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                )
                                val thumbAlpha by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = if (isSelected) 1f else 0.5f,
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                )
                                val borderWidth by androidx.compose.animation.core.animateDpAsState(
                                    targetValue = if (isSelected) 2.dp else 0.dp,
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                )
                                val borderColor by androidx.compose.animation.animateColorAsState(
                                    targetValue = if (isSelected) Color.White else Color.Transparent,
                                    animationSpec = androidx.compose.animation.core.tween(300)
                                )

                                AsyncImage(
                                    model = thumbUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .height(thumbHeight)
                                        .aspectRatio(2f/3f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(
                                            width = borderWidth,
                                            color = borderColor,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .alpha(thumbAlpha)
                                        .clickable {
                                            currentIndex = index
                                        }
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = { currentIndex = (currentIndex + 1) % displayItems.size },
                        modifier = Modifier.size(36.dp).background(Color.Black.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next", tint = Color.White)
                    }
                }
            }
        }
    }
}
