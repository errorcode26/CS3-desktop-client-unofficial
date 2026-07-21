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
import com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard

@Composable
fun SearchResults(
    searchResultsGrouped: List<Pair<MainAPI, List<SearchResponse>>>?,
    selectedCategory: com.lagradost.cloudstream3.TvType? = null,
    isLoadingSearch: Boolean,
    heroMetaMap: Map<String, com.lagradost.cloudstream3.desktop.repo.HeroMeta> = emptyMap(),
    onViewAll: (MainAPI, String, List<SearchResponse>) -> Unit,
    onItemClick: (MainAPI, SearchResponse, String?) -> Unit,
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
        val filteredGrouped = if (selectedCategory != null) {
            searchResultsGrouped.mapNotNull { (provider, items) ->
                val filteredItems = items.filter { item ->
                    try {
                        val method = item.javaClass.getMethod("getType")
                        val type = method.invoke(item)
                        type == selectedCategory
                    } catch (e: Exception) {
                        false
                    }
                }
                if (filteredItems.isNotEmpty()) Pair(provider, filteredItems) else null
            }
        } else {
            searchResultsGrouped
        }

        if (filteredGrouped.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 130.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
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
                            PosterCard(item, provider) {
                                onItemClick(provider, item, heroMeta?.backdropUrl)
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
