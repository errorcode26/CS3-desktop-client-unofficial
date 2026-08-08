package com.lagradost.cloudstream3.desktop.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun ComposeCategoryGridScreen(
    onNavigate: (Config) -> Unit,
    onBack: () -> Unit,
    provider: MainAPI,
    title: String,
    items: List<SearchResponse>,
) {
    val gridScale by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.gridScale.collectAsState()
    val hasLandscapeItems = items.any { it.type == com.lagradost.cloudstream3.TvType.Live || it.posterHeaders?.containsKey("landscape") == true }
    val baseMinSize = when (gridScale) {
        "Compact" -> 150.dp
        "Large" -> 220.dp
        else -> 190.dp
    }
    val minSize = if (hasLandscapeItems) (baseMinSize * 1.45f) else baseMinSize

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = minSize),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(items.size) { index ->
                val item = items[index]
                PosterCard(
                    item = item,
                    provider = provider,
                    aspectRatio = if (hasLandscapeItems) 16f / 9f else null,
                    onClick = {
                        onNavigate(Config.Details(provider.name, item.url, item.name, item.posterUrl, null, false))
                    },
                    onPlayClick = {
                        onNavigate(Config.Details(provider.name, item.url, item.name, item.posterUrl, null, true))
                    },
                )
            }
        }
    }
}
