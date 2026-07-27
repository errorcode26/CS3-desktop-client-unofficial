package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun ComposeCategoryGridScreen(
    navController: NavController,
    provider: MainAPI,
    title: String,
    items: List<SearchResponse>,
) {
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val minSize = posterWidthDp.dp

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = minSize),
        contentPadding = PaddingValues(bottom = 32.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items.size) { index ->
            val item = items[index]
            PosterCard(
                item = item,
                provider = provider,
                onClick = {
                    navController.navigate(Screen.Details(provider.name, item.url, item.name, item.posterUrl, null))
                },
            )
        }
    }
}
