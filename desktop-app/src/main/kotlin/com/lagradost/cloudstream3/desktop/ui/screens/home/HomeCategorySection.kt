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
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.screens.home.contract.HomeCategoryUiState

object HomeCategorySectionCache {
    val categoryCache = java.util.concurrent.ConcurrentHashMap<String, HomePageResponse>()
    val categoryMutex = java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.sync.Mutex>()
    fun clear() {
        categoryCache.clear()
        categoryMutex.clear()
    }
}

@Composable
fun HomeCategorySection(
    pageData: MainPageData,
    provider: MainAPI,
    categoryState: HomeCategoryUiState?,
    onLoadCategory: () -> Unit,
    isFirstPage: Boolean = false,
    heroMetaMap: Map<String, com.lagradost.cloudstream3.desktop.repo.HeroMeta>,
    allBookmarks: Map<String, com.lagradost.common.storage.DesktopBookmark>,
    onPrefetchHeroItem: (MainAPI?, SearchResponse) -> Unit,
    onHeroBackgroundChanged: (String?) -> Unit,
    outerPadding: androidx.compose.ui.unit.Dp = 0.dp,
    afterHeroContent: @Composable () -> Unit = {},
    isHistoryVisible: Boolean = false,
    onViewAll: (MainAPI, String, List<SearchResponse>) -> Unit,
    onItemClick: (MainAPI, SearchResponse, String?, Boolean) -> Unit,
) {
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(pageData, provider) {
        visible = true
        if (categoryState == null || (categoryState.response == null && categoryState.error == null)) {
            onLoadCategory()
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(300),
        label = "alpha",
    )
    val heroEnabled by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.heroEnabled.collectAsState()
    val homeVerticalSpacingDp by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.homeVerticalSpacingDp.collectAsState()

    val hp = categoryState?.response
    val isLoading = categoryState?.isLoading ?: (hp == null && categoryState?.error == null)
    val errorMessage = categoryState?.error

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(alpha),
        verticalArrangement = Arrangement.spacedBy(homeVerticalSpacingDp.dp),
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
                            allBookmarks = allBookmarks,
                            onPrefetchHeroItem = onPrefetchHeroItem,
                            onHeroBackgroundChanged = onHeroBackgroundChanged,
                            onItemClick = { item, backdrop, autoPlay -> onItemClick(provider, item, backdrop, autoPlay) },
                        )
                        afterHeroContent()
                    } else {
                        val isFirstRowOfFirstPage = isFirstPage && sectionIndex == 0
                        if (isFirstRowOfFirstPage) {
                            afterHeroContent()
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

                        val isHorizontalCategory = pageData.horizontalImages || section.list.any { it.type == com.lagradost.cloudstream3.TvType.Live || it.posterHeaders?.containsKey("landscape") == true }
                        val categoryAspectRatio = if (isHorizontalCategory) 16f / 9f else 2f / 3f

                        val isCompactScreen = paddingStart < 50.dp
                        val effectivePaddingStart = if (isCompactScreen) 8.dp else paddingStart
                        val effectivePaddingEnd = if (isCompactScreen) 8.dp else paddingEnd

                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = effectivePaddingStart, end = effectivePaddingEnd),
                        ) {
                            val availableWidth = this.maxWidth
                            val isCompact = availableWidth < 600.dp

                            val posterWidthDp by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.posterWidthDp.collectAsState()
                            val homeSpacingDp by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.homeSpacingDp.collectAsState()

                            val spacingDp = if (isCompact) 8.dp else homeSpacingDp.dp

                            val optimalItemWidth = if (isCompact) {
                                if (isHorizontalCategory) 160.dp else 115.dp
                            } else {
                                val baseWidth = if (isHorizontalCategory) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp
                                // Subtract 20.dp (10.dp start + 10.dp end horizontal content padding) from availableWidth
                                val netWidth = availableWidth - 20.dp
                                val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
                                val columns = exactColumns.toInt().coerceAtLeast(1)
                                ((netWidth + spacingDp) / columns) - spacingDp
                            }

                            CategoryRowWithHeader(
                                modifier = Modifier.fillMaxWidth(),
                                title = titleStr,
                                itemCount = section.list.size,
                                isInfinite = isLoop,
                                onViewAll = { onViewAll(provider, section.name, section.list) },
                                rowContentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    horizontal = if (isCompact) 4.dp else 10.dp,
                                    vertical = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                                ),
                                headerPadding = androidx.compose.foundation.layout.PaddingValues(
                                    start = 10.dp,
                                    end = 10.dp,
                                    top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                                    bottom = 4.dp,
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
                                        aspectRatio = categoryAspectRatio,
                                        onClick = { onItemClick(provider, posterItem, null, false) },
                                        onPlayClick = { onItemClick(provider, posterItem, null, true) },
                                    )
                                }
                            }
                        }
                    }

                }
            } else if (errorMessage != null) {
                val dockPosition by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.dockPosition.collectAsState()
                val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 22.dp
                val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 22.dp

                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = paddingStart + 10.dp, end = paddingEnd + 10.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${pageData.name}: $errorMessage",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    androidx.compose.material3.TextButton(
                        onClick = onLoadCategory,
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text("Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
