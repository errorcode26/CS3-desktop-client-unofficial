package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private val categoryCache = java.util.concurrent.ConcurrentHashMap<String, HomePageResponse>()
private val categoryMutex = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()

@Composable
fun HomeCategorySection(
    pageData: MainPageData,
    provider: MainAPI,
    isFirstPage: Boolean = false,
    parentScope: CoroutineScope,
    heroMetaMap: Map<String, com.lagradost.cloudstream3.desktop.repo.HeroMeta>,
    heroColorMap: Map<String, androidx.compose.ui.graphics.Color>,
    allBookmarks: Map<String, com.lagradost.common.storage.DesktopBookmark>,
    onPrefetchHeroItem: (MainAPI?, SearchResponse) -> Unit,
    onSetCurrentHeroColor: (String?) -> Unit,
    onUpdateHeroColor: (String?) -> Unit,
    afterHeroContent: @Composable () -> Unit = {},
    isHistoryVisible: Boolean = false,
    onViewAll: (MainAPI, String, List<SearchResponse>) -> Unit,
    onItemClick: (MainAPI, SearchResponse, String?, Boolean) -> Unit,
) {
    val cacheKey = "${provider.name}_${pageData.name}"
    var homePage by remember(cacheKey) { mutableStateOf<HomePageResponse?>(categoryCache[cacheKey]) }
    var isLoading by remember(cacheKey) { mutableStateOf(homePage == null) }
    var visible by remember { mutableStateOf(false) }
    var errorMessage by remember(cacheKey) { mutableStateOf<String?>(null) }

    val fetchPage = {
        parentScope.launch {
            val mutex = categoryMutex.getOrPut(cacheKey) { kotlinx.coroutines.sync.Mutex() }
            isLoading = true
            mutex.withLock {
                if (categoryCache[cacheKey] == null) {
                    errorMessage = null
                    try {
                        val request = MainPageRequest(pageData.name, pageData.data, pageData.horizontalImages)
                        val response = withContext(Dispatchers.IO) { provider.getMainPage(1, request) }
                        if (response != null && response.items.isNotEmpty()) {
                            categoryCache[cacheKey] = response
                        } else {
                            errorMessage = "No items found."
                        }
                    } catch (e: Throwable) {
                        DesktopErrorReporter.report("getMainPage failed for ${provider.name} - ${pageData.name.ifBlank { "Unknown Category" }}", e)
                        errorMessage = e.localizedMessage ?: "Connection error"
                    }
                }
                homePage = categoryCache[cacheKey]
            }
            isLoading = false
        }
    }

    LaunchedEffect(pageData, provider) {
        visible = true
        if (homePage == null) {
            fetchPage()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha",
    )
    val heroEnabled by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.heroEnabled.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
    ) {
        if (isLoading) {
            if (isFirstPage) {
                HomeHeroCarouselPlaceholder()
            } else {
                CategoryRowPlaceholder(
                    title = pageData.name,
                    showLargeHeader = !isFirstPage,
                )
            }
        } else {
            val hp = homePage
            if (hp != null && hp.items.isNotEmpty()) {
                hp.items.forEachIndexed { sectionIndex, section ->
                    if (heroEnabled && isFirstPage && sectionIndex == 0 && section.list.size >= 3) {
                        val heroCandidates = remember(hp.items) {
                            hp.items.flatMap { it.list }.distinctBy { it.url }.take(30)
                        }
                        HomeHeroCarousel(
                            items = heroCandidates,
                            provider = provider,
                            heroMetaMap = heroMetaMap,
                            heroColorMap = heroColorMap,
                            allBookmarks = allBookmarks,
                            onPrefetchHeroItem = onPrefetchHeroItem,
                            onSetCurrentHeroColor = onSetCurrentHeroColor,
                            onUpdateHeroColor = onUpdateHeroColor,
                            onItemClick = { item, backdrop, autoPlay -> onItemClick(provider, item, backdrop, autoPlay) },
                        )
                        afterHeroContent()
                    } else {
                        val isFirstRowOfFirstPage = isFirstPage && sectionIndex == 0
                        if (isFirstRowOfFirstPage) {
                            val heroPadding = if (!heroEnabled && isHistoryVisible) 72.dp else 0.dp
                            androidx.compose.foundation.layout.Box(modifier = Modifier.padding(top = heroPadding)) {
                                afterHeroContent()
                            }
                        }
                        val titleStr = section.name.takeIf { it.isNotBlank() } ?: pageData.name
                        val showLargeHeader = sectionIndex == 0 && !isFirstPage && !titleStr.equals(pageData.name, ignoreCase = true)

                        val isLoop = section.list.size >= 4

                        val dockPosition by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.dockPosition.collectAsState()
                        // 88.dp base + 10.dp internal (used by Category headers) => visually aligns with 98.dp
                        val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 22.dp
                        val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 22.dp

                        if (showLargeHeader) {
                            Text(
                                text = pageData.name,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = paddingStart + 10.dp, top = 24.dp, bottom = 0.dp),
                            )
                        }

                        val topPadding = if (isFirstRowOfFirstPage && !heroEnabled && !isHistoryVisible) 72.dp else 0.dp

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = paddingStart, end = paddingEnd, top = topPadding),
                        ) {
                            val availableWidth = this.maxWidth
                            val posterWidthDp by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.posterWidthDp.collectAsState()
                            val homeSpacingDp by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.homeSpacingDp.collectAsState()

                            val baseWidth = posterWidthDp.dp
                            val spacingDp = homeSpacingDp.dp

                            // Subtract 20.dp (10.dp horizontal content padding) from availableWidth
                            val netWidth = availableWidth - 20.dp
                            val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
                            val columns = exactColumns.toInt().coerceAtLeast(1)
                            val optimalItemWidth = ((netWidth + spacingDp) / columns) - spacingDp

                            CategoryRowWithHeader(
                                title = titleStr,
                                itemCount = section.list.size,
                                isInfinite = isLoop,
                                onViewAll = { onViewAll(provider, section.name, section.list) },
                                rowContentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = 10.dp,
                                    vertical = 16.dp,
                                ),
                                headerPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 10.dp,
                                    end = 10.dp,
                                    top = 12.dp,
                                    bottom = 8.dp,
                                ),
                                itemSpacing = spacingDp,
                            ) {
                                items(
                                    count = if (isLoop) Int.MAX_VALUE else section.list.size,
                                    key = { index ->
                                        val itemIndex = if (isLoop) index % section.list.size else index
                                        "${section.list[itemIndex].url}_$index"
                                    },
                                ) { index ->
                                    val itemIndex = if (isLoop) index % section.list.size else index
                                    val posterItem = section.list[itemIndex]
                                    PosterCard(
                                        item = posterItem,
                                        provider = provider,
                                        itemWidth = optimalItemWidth,
                                        onClick = { onItemClick(provider, posterItem, null, false) },
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val minHeight = if (isFirstPage) 350.dp else 150.dp
                val topPadding = if (isFirstPage) 80.dp else 0.dp
                Box(modifier = Modifier.fillMaxWidth().height(minHeight).padding(top = topPadding), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            errorMessage ?: "Failed to load category.",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        androidx.compose.material3.OutlinedButton(onClick = { fetchPage() }) {
                            Text("Retry")
                        }
                    }
                }
            }
        }
    }
}
