package com.lagradost.cloudstream3.desktop.ui.screens.home

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
fun HomeSearchResults(
    searchResultsGrouped: List<Pair<MainAPI, List<SearchResponse>>>?,
    isLoadingSearch: Boolean,
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
        if (searchResultsGrouped.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 130.dp, bottom = 16.dp, start = 20.dp, end = 20.dp),
            ) {
                items(searchResultsGrouped.size) { index ->
                    val (provider, items) = searchResultsGrouped[index]
                    val isLoop = items.size >= 4
                    CategoryRowWithHeader(
                        title = provider.name,
                        itemCount = items.size,
                        isInfinite = isLoop,
                        onViewAll = { onViewAll(provider, provider.name, items) },
                    ) {
                        items(if (isLoop) Int.MAX_VALUE else items.size) { index ->
                            val itemIndex = if (isLoop) index % items.size else index
                            val item = items[itemIndex]
                            val heroMeta = HeroCache.cache["${provider.name}_${item.url}"]
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
