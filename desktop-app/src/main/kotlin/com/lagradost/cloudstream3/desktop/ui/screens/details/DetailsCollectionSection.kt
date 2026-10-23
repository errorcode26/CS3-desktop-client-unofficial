package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.components.desktopDragScroll
import com.lagradost.cloudstream3.desktop.ui.navigation.Config

@Composable
fun DetailsCollectionSection(
    collName: String,
    collBg: String?,
    collItems: List<SearchResponse>,
    provider: MainAPI,
    onNavigate: (Config) -> Unit,
    horizontalPadding: Dp = 24.dp,
) {
    if (collItems.isEmpty()) return
    val collScrollState = rememberLazyListState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = 14.dp),
        ) {
            Text(
                text = "COLLECTION / SAGA",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = collName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        LazyRow(
            state = collScrollState,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = horizontalPadding),
            modifier = Modifier
                .fillMaxWidth()
                .desktopDragScroll(collScrollState),
        ) {
            items(
                count = collItems.size,
                key = { index -> "${collItems[index].apiName}_${collItems[index].url}_$index" },
            ) { index ->
                val partItem = collItems[index]
                PosterCard(
                    item = partItem,
                    provider = provider,
                    itemWidth = 200.dp,
                    onClick = {
                        onNavigate(Config.Details(provider.name, partItem.url, partItem.name, partItem.posterUrl, null, false))
                    },
                    onPlayClick = {
                        onNavigate(Config.Details(provider.name, partItem.url, partItem.name, partItem.posterUrl, null, true))
                    },
                )
            }
        }
    }
}
