package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchEngine
import com.lagradost.cloudstream3.desktop.explore.search.ExploreSearchResults
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory

@Composable
internal fun ExploreSearchResultsView(
    searchQuery: String,
    searchResults: ExploreSearchResults?,
    isSearching: Boolean,
    isShelvesMode: Boolean,
    drilledCategory: String?,
    recommendations: List<ExploreItem> = emptyList(),
    watchHistoryMap: Map<String, WatchHistory>,
    autoCleanTitles: Boolean,
    onDrillCategory: (String) -> Unit,
    onReturnFromDrill: () -> Unit,
    onClearSearch: () -> Unit,
    onItemClick: (ExploreItem) -> Unit,
    contentTopPadding: Dp = 104.dp,
) {
    val theme = LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()

    val dockPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 24.dp
    val dockPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 24.dp

    val searchRowPaddingStart = if (dockPosition == DockPosition.LEFT) 88.dp else 22.dp
    val searchRowPaddingEnd = if (dockPosition == DockPosition.RIGHT) 88.dp else 22.dp

    val results = searchResults
    val hasAnyResults = results != null && (
        results.movies.isNotEmpty() ||
            results.series.isNotEmpty() ||
            results.anime.isNotEmpty() ||
            results.addonGroups.any { it.items.isNotEmpty() }
        )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp
        val spacingDp = if (isCompact) 8.dp else homeSpacingDp.dp
        val minPosterSize = if (isCompact) 105.dp else posterWidthDp.dp
        val rowVerticalPadding = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp)
        val availableWidth = maxWidth - (if (isCompact) 12.dp else (searchRowPaddingStart + searchRowPaddingEnd))
        val optimalItemWidth = if (isCompact) {
            115.dp
        } else {
            val baseWidth = posterWidthDp.dp
            val netWidth = availableWidth - 20.dp
            val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
            val columns = exactColumns.toInt().coerceAtLeast(1)
            ((netWidth + spacingDp) / columns) - spacingDp
        }

        if ((results == null || !hasAnyResults) && isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentTopPadding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                        strokeWidth = 3.dp,
                    )
                    Text(
                        text = "Searching sources for \"$searchQuery\"...",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = theme.TextMuted,
                    )
                }
            }
        } else if (!hasAnyResults && !isSearching) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
                contentPadding = PaddingValues(
                    top = contentTopPadding,
                    bottom = 40.dp,
                ),
            ) {
                item(key = "search_no_results_header") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = dockPaddingStart, end = dockPaddingEnd, top = 20.dp, bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null,
                                tint = theme.TextMuted,
                                modifier = Modifier.size(40.dp),
                            )
                            Text(
                                text = "No results found for \"$searchQuery\"",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.TextPrimary,
                            )
                            Text(
                                text = if (recommendations.isNotEmpty()) "Check spelling or explore recommended titles below." else "Check spelling or try searching for another title.",
                                fontSize = 12.5.sp,
                                color = theme.TextMuted,
                            )
                            Button(
                                onClick = onClearSearch,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = Color.Black,
                                ),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Text("Clear Search", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }

                if (recommendations.isNotEmpty()) {
                    item(key = "search_recommendations_row") {
                        CategoryRowWithHeader(
                            modifier = Modifier.fillMaxWidth().padding(start = searchRowPaddingStart, end = searchRowPaddingEnd),
                            title = "Trending & Recommended",
                            itemCount = recommendations.size,
                            onViewAll = null,
                            rowContentPadding = PaddingValues(
                                horizontal = 10.dp,
                                vertical = rowVerticalPadding,
                            ),
                            headerPadding = PaddingValues(
                                start = 10.dp,
                                end = 10.dp,
                                top = 12.dp,
                                bottom = 4.dp,
                            ),
                            itemSpacing = spacingDp,
                        ) {
                            items(
                                count = recommendations.size,
                                key = { i -> "search_rec_${recommendations[i].id}_$i" },
                            ) { i ->
                                val item = recommendations[i]
                                val watchHistory = watchHistoryMap[item.id]
                                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                                Box(modifier = Modifier.width(optimalItemWidth)) {
                                    ExplorePosterCard(
                                        item = item,
                                        watchHistory = watchHistory,
                                        autoCleanTitles = autoCleanTitles,
                                        isCatalogLandscape = false,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } else if (drilledCategory != null) {
            val drilledItems = when (drilledCategory) {
                "Movies" -> results?.movies.orEmpty()
                "TV Series" -> results?.series.orEmpty()
                "Anime" -> results?.anime.orEmpty()
                else -> results?.addonGroups?.firstOrNull { it.addonName == drilledCategory }?.items.orEmpty()
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minPosterSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dockPaddingStart,
                    end = dockPaddingEnd,
                    top = contentTopPadding,
                    bottom = 40.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacingDp),
                verticalArrangement = Arrangement.spacedBy(spacingDp),
            ) {
                items(
                    count = drilledItems.size,
                    key = { i -> "search_drilled_${drilledCategory}_${drilledItems[i].id}" },
                ) { i ->
                    val item = drilledItems[i]
                    val watchHistory = watchHistoryMap[item.id]
                        ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                    ExplorePosterCard(
                        item = item,
                        watchHistory = watchHistory,
                        autoCleanTitles = autoCleanTitles,
                        isCatalogLandscape = false,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        } else if (!isShelvesMode) {
            val allSearchItems = remember(results) {
                if (results == null) {
                    emptyList()
                } else {
                    val allRaw = results.movies + results.series + results.anime + results.addonGroups.flatMap { it.items }
                    val initialList = if (results.topMatch != null) listOf(results.topMatch) + allRaw else allRaw
                    ExploreSearchEngine.deduplicateAndMerge(initialList)
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = minPosterSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = dockPaddingStart,
                    end = dockPaddingEnd,
                    top = contentTopPadding,
                    bottom = 40.dp,
                ),
                horizontalArrangement = Arrangement.spacedBy(spacingDp),
                verticalArrangement = Arrangement.spacedBy(spacingDp),
            ) {
                items(
                    count = allSearchItems.size,
                    key = { i -> "search_all_grid_${allSearchItems[i].id}" },
                ) { i ->
                    val item = allSearchItems[i]
                    val watchHistory = watchHistoryMap[item.id]
                        ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                    ExplorePosterCard(
                        item = item,
                        watchHistory = watchHistory,
                        autoCleanTitles = autoCleanTitles,
                        isCatalogLandscape = false,
                        onClick = { onItemClick(item) },
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy((homeVerticalSpacingDp * 0.5f).dp),
                contentPadding = PaddingValues(
                    top = contentTopPadding,
                    bottom = 40.dp,
                ),
            ) {
                // 1. Movies Row
                if (results != null && results.movies.isNotEmpty()) {
                    val movies = results.movies
                    item(key = "search_row_movies") {
                        CategoryRowWithHeader(
                            modifier = Modifier.fillMaxWidth().padding(start = searchRowPaddingStart, end = searchRowPaddingEnd),
                            title = "Movies",
                            itemCount = movies.size,
                            onViewAll = { onDrillCategory("Movies") },
                            rowContentPadding = PaddingValues(
                                horizontal = 10.dp,
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
                            items(
                                count = movies.size,
                                key = { i -> "search_movie_${movies[i].id}_$i" },
                            ) { i ->
                                val item = movies[i]
                                val watchHistory = watchHistoryMap[item.id]
                                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                                Box(modifier = Modifier.width(optimalItemWidth)) {
                                    ExplorePosterCard(
                                        item = item,
                                        watchHistory = watchHistory,
                                        autoCleanTitles = autoCleanTitles,
                                        isCatalogLandscape = false,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. TV Series Row
                if (results != null && results.series.isNotEmpty()) {
                    val series = results.series
                    item(key = "search_row_series") {
                        CategoryRowWithHeader(
                            modifier = Modifier.fillMaxWidth().padding(start = searchRowPaddingStart, end = searchRowPaddingEnd),
                            title = "TV Series",
                            itemCount = series.size,
                            onViewAll = { onDrillCategory("TV Series") },
                            rowContentPadding = PaddingValues(
                                horizontal = 10.dp,
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
                            items(
                                count = series.size,
                                key = { i -> "search_series_${series[i].id}_$i" },
                            ) { i ->
                                val item = series[i]
                                val watchHistory = watchHistoryMap[item.id]
                                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                                Box(modifier = Modifier.width(optimalItemWidth)) {
                                    ExplorePosterCard(
                                        item = item,
                                        watchHistory = watchHistory,
                                        autoCleanTitles = autoCleanTitles,
                                        isCatalogLandscape = false,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Anime Row
                if (results != null && results.anime.isNotEmpty()) {
                    val anime = results.anime
                    item(key = "search_row_anime") {
                        CategoryRowWithHeader(
                            modifier = Modifier.fillMaxWidth().padding(start = searchRowPaddingStart, end = searchRowPaddingEnd),
                            title = "Anime",
                            itemCount = anime.size,
                            onViewAll = { onDrillCategory("Anime") },
                            rowContentPadding = PaddingValues(
                                horizontal = 10.dp,
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
                            items(
                                count = anime.size,
                                key = { i -> "search_anime_${anime[i].id}_$i" },
                            ) { i ->
                                val item = anime[i]
                                val watchHistory = watchHistoryMap[item.id]
                                    ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                                Box(modifier = Modifier.width(optimalItemWidth)) {
                                    ExplorePosterCard(
                                        item = item,
                                        watchHistory = watchHistory,
                                        autoCleanTitles = autoCleanTitles,
                                        isCatalogLandscape = false,
                                        onClick = { onItemClick(item) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 4. Addon-specific Groups
                results?.addonGroups?.forEach { group ->
                    if (group.items.isNotEmpty()) {
                        item(key = "search_row_addon_${group.addonName}") {
                            CategoryRowWithHeader(
                                modifier = Modifier.fillMaxWidth().padding(start = searchRowPaddingStart, end = searchRowPaddingEnd),
                                title = group.addonName,
                                itemCount = group.items.size,
                                onViewAll = { onDrillCategory(group.addonName) },
                                rowContentPadding = PaddingValues(
                                    horizontal = 10.dp,
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
                                items(
                                    count = group.items.size,
                                    key = { i -> "search_${group.addonName}_${group.items[i].id}_$i" },
                                ) { i ->
                                    val item = group.items[i]
                                    val watchHistory = watchHistoryMap[item.id]
                                        ?: watchHistoryMap[DesktopDataStore.watchHistoryId("Stremio", "stremio://${item.type}/${item.id}")]

                                    Box(modifier = Modifier.width(optimalItemWidth)) {
                                        ExplorePosterCard(
                                            item = item,
                                            watchHistory = watchHistory,
                                            autoCleanTitles = autoCleanTitles,
                                            isCatalogLandscape = false,
                                            onClick = { onItemClick(item) },
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
