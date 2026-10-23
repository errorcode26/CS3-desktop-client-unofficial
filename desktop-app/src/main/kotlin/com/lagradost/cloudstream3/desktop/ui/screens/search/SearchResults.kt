package com.lagradost.cloudstream3.desktop.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun SearchResults(
    searchResultsGrouped: List<Pair<MainAPI, List<SearchResponse>>>?,
    selectedCategories: Set<TvType> = emptySet(),
    isLoadingSearch: Boolean,
    isLoadingMore: Boolean = false,
    canPaginate: Boolean = true,
    isGlobalSearchEnabled: Boolean = false,
    heroMetaMap: Map<String, com.lagradost.cloudstream3.desktop.repo.HeroMeta> = emptyMap(),
    onLoadMore: () -> Unit = {},
    onViewAll: (MainAPI, String, List<SearchResponse>) -> Unit,
    onItemClick: (MainAPI, SearchResponse, String?, Boolean) -> Unit,
) {
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    val dockPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 22.dp
    val dockPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 22.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp
        val spacingDp = if (isCompact) 8.dp else homeSpacingDp.dp
        val availableWidth = maxWidth - (if (isCompact) 12.dp else (dockPaddingStart + dockPaddingEnd))

        if (isLoadingSearch && searchResultsGrouped.isNullOrEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Loading...", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        } else if (searchResultsGrouped != null) {
            // Multi-category filter: if set is empty show all, otherwise match any selected type
            val filteredGrouped = if (selectedCategories.isNotEmpty()) {
                searchResultsGrouped.mapNotNull { (provider, items) ->
                    val filteredItems = items.filter { item -> item.type in selectedCategories }
                    if (filteredItems.isNotEmpty()) Pair(provider, filteredItems) else null
                }
            } else {
                searchResultsGrouped
            }

            if (filteredGrouped.isNotEmpty()) {
                val isSingleProvider = !isGlobalSearchEnabled && filteredGrouped.size == 1

                if (isSingleProvider) {
                    // ── Single Provider: Full Vertical Grid with Infinite Scrolling ──
                    val (provider, items) = filteredGrouped.first()
                    val hasLandscapeItems = items.any { it.type == TvType.Live || it.posterHeaders?.containsKey("landscape") == true }
                    val baseMinSize = if (isCompact) {
                        if (hasLandscapeItems) 150.dp else 105.dp
                    } else {
                        if (hasLandscapeItems) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp
                    }
                    val gridState = rememberLazyGridState()

                    val shouldLoadMore by remember(items.size, isLoadingSearch, isLoadingMore, canPaginate) {
                        derivedStateOf {
                            val total = items.size
                            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                            total > 0 && lastVisible >= total - 6
                        }
                    }

                    LaunchedEffect(shouldLoadMore) {
                        if (shouldLoadMore && !isLoadingSearch && !isLoadingMore && canPaginate) {
                            onLoadMore()
                        }
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = baseMinSize),
                        state = gridState,
                        contentPadding = PaddingValues(
                            start = if (isCompact) 8.dp else dockPaddingStart,
                            end = if (isCompact) 8.dp else dockPaddingEnd,
                            top = 8.dp,
                            bottom = 32.dp,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(spacingDp),
                        verticalArrangement = Arrangement.spacedBy(if (homeVerticalSpacingDp > 0) homeVerticalSpacingDp.dp else 16.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(items.size, key = { index -> "${items[index].url}_$index" }) { index ->
                            val item = items[index]
                            val heroMeta = heroMetaMap[item.url]
                            PosterCard(
                                item = item,
                                provider = provider,
                                aspectRatio = if (hasLandscapeItems) 16f / 9f else null,
                                onClick = {
                                    onItemClick(provider, item, heroMeta?.backdropUrl, false)
                                },
                                onPlayClick = {
                                    onItemClick(provider, item, heroMeta?.backdropUrl, true)
                                },
                            )
                        }

                        if (isLoadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 24.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                                }
                            }
                        }
                    }
                } else {
                    // ── Global Search / Multi-Provider: Horizontal Categorized Rows ──
                    val duplicateRowNames = remember(filteredGrouped) {
                        filteredGrouped.map { it.first }.groupBy { it.name }.filterValues { it.size > 1 }.keys
                    }
                    val rowVerticalPadding = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp)

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            top = 8.dp,
                            bottom = 16.dp,
                            start = if (isCompact) 6.dp else dockPaddingStart,
                            end = if (isCompact) 6.dp else dockPaddingEnd,
                        ),
                        verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
                    ) {
                        items(filteredGrouped.size, key = { index -> "${filteredGrouped[index].first.name}::${filteredGrouped[index].first.sourcePlugin ?: ""}_$index" }) { index ->
                            val (provider, items) = filteredGrouped[index]
                            val repoTag = if (provider.name in duplicateRowNames) {
                                provider.sourcePlugin?.let {
                                    try {
                                        java.io.File(it).parentFile?.name?.replace("_", " ")
                                    } catch (_: Exception) {
                                        null
                                    }
                                }
                            } else {
                                null
                            }

                            val rowTitle = if (!repoTag.isNullOrBlank()) "${provider.name} ($repoTag)" else provider.name
                            val isLandscapeRow = items.any { it.type == TvType.Live || it.posterHeaders?.containsKey("landscape") == true }

                            val optimalItemWidth = if (isCompact) {
                                if (isLandscapeRow) 160.dp else 115.dp
                            } else {
                                val baseWidth = if (isLandscapeRow) (posterWidthDp.dp * 1.45f) else posterWidthDp.dp
                                val netWidth = availableWidth - 20.dp
                                val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
                                val columns = exactColumns.toInt().coerceAtLeast(1)
                                ((netWidth + spacingDp) / columns) - spacingDp
                            }

                            CategoryRowWithHeader(
                                title = rowTitle,
                                itemCount = items.size,
                                onViewAll = { onViewAll(provider, provider.name, items) },
                                rowContentPadding = PaddingValues(
                                    horizontal = if (isCompact) 4.dp else 10.dp,
                                    vertical = rowVerticalPadding,
                                ),
                                headerPadding = PaddingValues(
                                    start = 10.dp,
                                    end = 10.dp,
                                    top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                                    bottom = 4.dp,
                                ),
                                itemSpacing = spacingDp,
                            ) {
                                items(items.size, key = { itemIdx -> "${items[itemIdx].url}_$itemIdx" }) { itemIdx ->
                                    val item = items[itemIdx]
                                    val heroMeta = heroMetaMap[item.url]
                                    val isLand = item.type == TvType.Live || item.posterHeaders?.containsKey("landscape") == true

                                    Box(modifier = Modifier.width(optimalItemWidth)) {
                                        PosterCard(
                                            item = item,
                                            provider = provider,
                                            itemWidth = optimalItemWidth,
                                            aspectRatio = if (isLand) 16f / 9f else null,
                                            onClick = {
                                                onItemClick(provider, item, heroMeta?.backdropUrl, false)
                                            },
                                            onPlayClick = {
                                                onItemClick(provider, item, heroMeta?.backdropUrl, true)
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No results found for selected filter.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
