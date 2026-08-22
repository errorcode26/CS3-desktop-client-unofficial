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
                val gridScale by AppearanceConfig.gridScale.collectAsState()
                val hasLandscapeItems = items.any { it.type == TvType.Live || it.posterHeaders?.containsKey("landscape") == true }
                val baseMinSize = when (gridScale) {
                    "Compact" -> 150.dp
                    "Large" -> 220.dp
                    else -> 190.dp
                }
                val minSize = if (hasLandscapeItems) (baseMinSize * 1.45f) else baseMinSize
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
                    columns = GridCells.Adaptive(minSize = minSize),
                    state = gridState,
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(items.size, key = { index -> items[index].url }) { index ->
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
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
                ) {
                    items(filteredGrouped.size, key = { filteredGrouped[it].first.name }) { index ->
                        val (provider, items) = filteredGrouped[index]
                        CategoryRowWithHeader(
                            title = provider.name,
                            itemCount = items.size,
                            isInfinite = false,
                            onViewAll = { onViewAll(provider, provider.name, items) },
                        ) {
                            items(items.size, key = { "${provider.name}_${items[it].url}" }) { innerIndex ->
                                val item = items[innerIndex]
                                val heroMeta = heroMetaMap[item.url]
                                PosterCard(
                                    item = item,
                                    provider = provider,
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
        } else if (!isLoadingSearch) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No search results.")
            }
        }
    }
}
