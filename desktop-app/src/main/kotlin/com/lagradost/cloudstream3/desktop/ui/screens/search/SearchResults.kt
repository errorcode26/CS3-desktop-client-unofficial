package com.lagradost.cloudstream3.desktop.ui.screens.search

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard

@Composable
fun SearchResults(
    searchResultsGrouped: List<Pair<MainAPI, List<SearchResponse>>>?,
    selectedCategories: Set<TvType> = emptySet(),
    isLoadingSearch: Boolean,
    heroMetaMap: Map<String, com.lagradost.cloudstream3.desktop.repo.HeroMeta> = emptyMap(),
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
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 8.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
            ) {
                items(filteredGrouped.size) { index ->
                    val (provider, items) = filteredGrouped[index]
                    CategoryRowWithHeader(
                        title = provider.name,
                        itemCount = items.size,
                        isInfinite = false,
                        onViewAll = { onViewAll(provider, provider.name, items) },
                    ) {
                        items(items.size) { index ->
                            val item = items[index]
                            val heroMeta = heroMetaMap[item.url]
                            PosterCard(
                                item = item,
                                provider = provider,
                                onClick = {
                                    onItemClick(provider, item, heroMeta?.backdropUrl, false)
                                },
                                onPlayClick = {
                                    onItemClick(provider, item, heroMeta?.backdropUrl, true)
                                }
                            )
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
