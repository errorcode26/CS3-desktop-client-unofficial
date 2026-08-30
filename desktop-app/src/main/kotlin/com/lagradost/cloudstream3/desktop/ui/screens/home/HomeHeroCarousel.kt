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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
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
import coil3.request.crossfade
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.DesktopDimens
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.common.storage.DesktopBookmark
import kotlinx.coroutines.delay

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
            meta == null || meta.backdropUrl != null
        }.take(10)
    }

    val autoSlideDelay by AppearanceConfig.heroAutoSlideDelaySeconds.collectAsState()
    val heroBannerStyle by AppearanceConfig.heroBannerStyle.collectAsState()
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
    val rawHeight = with(density) { windowInfo.containerSize.height.toDp() }
    val rawWidth = with(density) { windowInfo.containerSize.width.toDp() }
    val isWindowCompact = rawWidth < 600.dp
    val dynamicHeight = if (isWindowCompact) {
        460.dp
    } else if (heroBannerStyle == com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.CINEMA_PEEKING) {
        (rawHeight * 0.72f).coerceIn(480.dp, 680.dp)
    } else {
        rawHeight.coerceAtLeast(400.dp)
    }

    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(dynamicHeight)
            .graphicsLayer { clip = false },
    ) {
        val safeArea = com.lagradost.cloudstream3.desktop.ui.LocalSafeArea.current
        val safeBottom = safeArea.calculateBottomPadding()
        val isCompact = maxWidth < 600.dp
        val autoAdvanceIntervalMs = autoSlideDelay * 1000L

        val paddingStart = if (isCompact) {
            12.dp
        } else if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) {
            98.dp
        } else {
            32.dp
        }

        val paddingEnd = if (isCompact) {
            12.dp
        } else if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) {
            98.dp
        } else {
            32.dp
        }

        val thumbnailHeight = (maxHeight * 0.22f).coerceIn(160.dp, 280.dp)
        val thumbnailsMaxWidth = if (isCompact) maxWidth - paddingStart - paddingEnd else maxWidth * 0.55f

        val infoBlockBottomPadding = if (isCompact) {
            16.dp
        } else if (heroBannerStyle == com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.CINEMA_PEEKING) {
            safeBottom + 36.dp
        } else {
            safeBottom + (maxHeight * 0.05f) + thumbnailHeight + 48.dp
        }

        val infoBlockMaxWidth = if (isCompact) (maxWidth - paddingStart - paddingEnd).coerceAtLeast(200.dp) else (maxWidth * 0.45f).coerceIn(400.dp, 750.dp)

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
                HeroBackdropLayer(
                    ambientBg = ambientBg,
                    isBackdropNull = meta?.backdropUrl == null,
                    isLightMode = isLightMode,
                )

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
                            modifier = Modifier.widthIn(max = infoBlockMaxWidth),
                        ) {
                            HeroMetadataLayer(
                                item = item,
                                meta = meta,
                                isCompact = isCompact,
                                heroBannerStyle = heroBannerStyle,
                            )

                            HeroActionButtons(
                                item = item,
                                meta = meta,
                                isCompact = isCompact,
                                displayItemsCount = displayItems.size,
                                currentIndex = currentIndex,
                                onSelect = { targetItem, backdrop ->
                                    onItemClick(targetItem, backdrop, false)
                                },
                            )
                        }
                    }
                }
            }
        }

        if (!isCompact && heroBannerStyle == com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.THUMBNAIL_STRIP) {
            HeroFilmstrip(
                displayItems = displayItems,
                provider = provider,
                heroMetaMap = heroMetaMap,
                globalIndex = globalIndex,
                thumbnailHeight = thumbnailHeight,
                thumbnailsMaxWidth = thumbnailsMaxWidth,
                paddingEnd = paddingEnd,
                safeBottom = safeBottom,
                autoAdvanceIntervalMs = autoAdvanceIntervalMs,
                onSelectThumb = { selectedGlobalIdx ->
                    globalIndex = selectedGlobalIdx
                },
            )
        } else {
            HeroDashes(
                displayItems = displayItems,
                currentIndex = currentIndex,
                globalIndex = globalIndex,
                isCompact = isCompact,
                paddingEnd = paddingEnd,
                safeBottom = safeBottom,
                autoAdvanceIntervalMs = autoAdvanceIntervalMs,
                onSelectIndex = { targetIdx ->
                    val diff = targetIdx - currentIndex
                    globalIndex += diff
                },
            )
        }
    }
}

@Composable
private fun HeroBackdropLayer(
    ambientBg: String?,
    isBackdropNull: Boolean,
    isLightMode: Boolean,
) {
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
        val enhancedAmbientBg = remember(ambientBg) { com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhanceBackdropUrl(ambientBg) }
        if (enhancedAmbientBg != null) {
            AsyncImage(
                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                    .data(enhancedAmbientBg)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                filterQuality = androidx.compose.ui.graphics.FilterQuality.High,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize().then(
                    if (isBackdropNull) Modifier.blur(24.dp) else Modifier,
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
}

@Composable
private fun HeroMetadataLayer(
    item: SearchResponse,
    meta: com.lagradost.cloudstream3.desktop.repo.HeroMeta?,
    isCompact: Boolean,
    heroBannerStyle: com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle,
) {
    Column(
        modifier = if (isCompact || heroBannerStyle == com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.CINEMA_PEEKING) Modifier.wrapContentHeight() else Modifier.height(350.dp),
        verticalArrangement = Arrangement.Bottom,
    ) {
        if (!meta?.logoUrl.isNullOrBlank()) {
            val displayTitle = meta.title ?: com.lagradost.cloudstream3.desktop.repo.HeroRepository.cleanHeroTitle(item.name)
            Box(
                modifier = Modifier
                    .widthIn(
                        min = if (isCompact) 120.dp else DesktopDimens.HeroLogoMinWidth,
                        max = if (isCompact) 220.dp else DesktopDimens.HeroLogoMaxWidth,
                    )
                    .heightIn(max = if (isCompact) 85.dp else DesktopDimens.HeroLogoMaxHeight),
                contentAlignment = Alignment.BottomStart,
            ) {
                var isDarkLogo by remember(meta.logoUrl) { mutableStateOf(false) }
                val platformContext = coil3.compose.LocalPlatformContext.current

                val logoRequest = remember(meta.logoUrl, platformContext) {
                    coil3.request.ImageRequest.Builder(platformContext)
                        .data(meta.logoUrl)
                        .size(1600, 800)
                        .crossfade(true)
                        .listener(
                            onSuccess = { _, result ->
                                isDarkLogo = com.lagradost.cloudstream3.desktop.utils.ImageUtils.isDarkImage(result.image)
                            },
                        )
                        .build()
                }
                coil3.compose.AsyncImage(
                    model = logoRequest,
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
                    model = logoRequest,
                    contentDescription = "Logo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    colorFilter = if (isDarkLogo) androidx.compose.ui.graphics.ColorFilter.colorMatrix(com.lagradost.cloudstream3.desktop.utils.ImageUtils.InvertColorMatrix) else null,
                    error = {
                        if (displayTitle.isNotBlank()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                                Text(
                                    text = displayTitle,
                                    style = (if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayLarge).copy(
                                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                                        letterSpacing = (-0.5).sp,
                                        fontSize = if (isCompact) 24.sp else 44.sp,
                                        lineHeight = if (isCompact) 28.sp else 48.sp,
                                    ),
                                    fontWeight = FontWeight.Black,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
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
                    style = (if (isCompact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.displayLarge).copy(
                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                        letterSpacing = (-0.5).sp,
                        fontSize = if (isCompact) 24.sp else 44.sp,
                        lineHeight = if (isCompact) 28.sp else 48.sp,
                    ),
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(if (isCompact) 8.dp else 20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            if (meta?.score != null && meta.score.toDoubleOrNull()?.let { it > 0.0 } == true) {
                Icon(
                    Icons.Default.Star,
                    contentDescription = "Rating",
                    tint = Color(0xFFFFD700),
                    modifier = Modifier.size(if (isCompact) 16.dp else 20.dp),
                )
                Spacer(Modifier.width(3.dp))
                Text(
                    text = meta.score,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (isCompact) 12.5.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    style = androidx.compose.material3.LocalTextStyle.current.copy(
                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                    ),
                )
                Spacer(Modifier.width(if (isCompact) 8.dp else 14.dp))
            }
            if (meta?.year != null) {
                Text(
                    text = meta.year.toString(),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (isCompact) 12.5.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.LocalTextStyle.current.copy(
                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                    ),
                )
                Spacer(Modifier.width(if (isCompact) 8.dp else 14.dp))
            }
            if (!meta?.contentRating.isNullOrBlank()) {
                com.lagradost.cloudstream3.desktop.ui.badges.DesktopBadgeComponents.ContentRatingBadge(
                    rating = meta.contentRating,
                    isLarge = !isCompact,
                )
                Spacer(Modifier.width(if (isCompact) 8.dp else 14.dp))
            }
            if (meta?.duration != null && meta.duration > 0) {
                Text(
                    text = "${meta.duration}m",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (isCompact) 12.5.sp else 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = androidx.compose.material3.LocalTextStyle.current.copy(
                        shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                    ),
                )
                Spacer(Modifier.width(if (isCompact) 8.dp else 14.dp))
            }
            if (!meta?.tags.isNullOrEmpty()) {
                val tagsText = meta.tags.distinct().take(2).joinToString(" • ")
                Text(
                    text = tagsText,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = if (isCompact) 12.5.sp else 15.sp,
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
            Spacer(Modifier.height(if (isCompact) 6.dp else 16.dp))
            Text(
                text = meta.plot,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                fontSize = if (isCompact) 12.5.sp else 15.sp,
                maxLines = if (isCompact) 2 else 3,
                overflow = TextOverflow.Ellipsis,
                lineHeight = if (isCompact) 17.sp else 22.sp,
                style = androidx.compose.material3.LocalTextStyle.current.copy(
                    shadow = com.lagradost.cloudstream3.desktop.ui.components.getTextShadow(),
                ),
            )
        }
    }
}

@Composable
private fun HeroActionButtons(
    item: SearchResponse,
    meta: com.lagradost.cloudstream3.desktop.repo.HeroMeta?,
    isCompact: Boolean,
    displayItemsCount: Int,
    currentIndex: Int,
    onSelect: (SearchResponse, String?) -> Unit,
) {
    if (isCompact && displayItemsCount > 1) {
        Spacer(Modifier.height(14.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            repeat(displayItemsCount) { dotIndex ->
                val isSelected = dotIndex == currentIndex
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(if (isSelected) 18.dp else 5.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.35f)),
                )
            }
        }
    }

    Spacer(Modifier.height(if (isCompact) 14.dp else 24.dp))

    Button(
        onClick = { onSelect(item, meta?.backdropUrl) },
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.White,
            contentColor = Color.Black,
        ),
        shape = RoundedCornerShape(if (isCompact) 10.dp else 12.dp),
        contentPadding = PaddingValues(horizontal = if (isCompact) 16.dp else 24.dp),
        modifier = if (isCompact) Modifier.height(40.dp).fillMaxWidth() else Modifier.height(48.dp).widthIn(min = 160.dp),
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = null,
            modifier = Modifier.size(if (isCompact) 18.dp else 22.dp),
        )
        Spacer(Modifier.width(if (isCompact) 6.dp else 8.dp))
        Text(
            "Watch Now",
            fontWeight = FontWeight.ExtraBold,
            fontSize = if (isCompact) 13.sp else 15.sp,
        )
    }
}

@Composable
private fun BoxScope.HeroFilmstrip(
    displayItems: List<SearchResponse>,
    provider: MainAPI?,
    heroMetaMap: Map<String, com.lagradost.cloudstream3.desktop.repo.HeroMeta>,
    globalIndex: Int,
    thumbnailHeight: androidx.compose.ui.unit.Dp,
    thumbnailsMaxWidth: androidx.compose.ui.unit.Dp,
    paddingEnd: androidx.compose.ui.unit.Dp,
    safeBottom: androidx.compose.ui.unit.Dp,
    autoAdvanceIntervalMs: Long,
    onSelectThumb: (Int) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { clip = false }
            .padding(bottom = safeBottom + 32.dp),
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
                                        .clickable { onSelectThumb(globalThumbIndex) },
                                )
                            }

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

@Composable
private fun BoxScope.HeroDashes(
    displayItems: List<SearchResponse>,
    currentIndex: Int,
    globalIndex: Int,
    isCompact: Boolean,
    paddingEnd: androidx.compose.ui.unit.Dp,
    safeBottom: androidx.compose.ui.unit.Dp,
    autoAdvanceIntervalMs: Long,
    onSelectIndex: (Int) -> Unit,
) {
    var progressFraction by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(globalIndex) {
        progressFraction = 0f
        val steps = 80
        val stepDelay = autoAdvanceIntervalMs / steps
        repeat(steps) {
            delay(stepDelay)
            progressFraction = (it + 1f) / steps
        }
    }
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction,
        animationSpec = tween(80, easing = LinearEasing),
        label = "dash_progress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = safeBottom + 32.dp, end = if (isCompact) 0.dp else paddingEnd),
        contentAlignment = if (isCompact) Alignment.BottomCenter else Alignment.BottomEnd,
    ) {
        Row(
            modifier = Modifier
                .background(
                    Color.Black.copy(alpha = 0.35f),
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            displayItems.forEachIndexed { index, _ ->
                val isSelected = index == currentIndex
                val dashWidth by androidx.compose.animation.core.animateDpAsState(
                    targetValue = if (isSelected) 36.dp else 12.dp,
                    animationSpec = tween(300),
                    label = "dash_width",
                )
                val dashAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1f else 0.4f,
                    animationSpec = tween(300),
                    label = "dash_alpha",
                )

                Box(
                    modifier = Modifier
                        .width(dashWidth)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White.copy(alpha = if (isSelected) 0.25f else 0.12f))
                        .clickable { onSelectIndex(index) },
                ) {
                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(animatedProgress)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White),
                        )
                    }
                }
            }
        }
    }
}
