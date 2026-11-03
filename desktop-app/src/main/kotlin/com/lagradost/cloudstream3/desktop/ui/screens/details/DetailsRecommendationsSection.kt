package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.DesktopIconButton
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.components.desktopDragScroll
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import kotlinx.coroutines.launch

@Composable
fun DetailsRecommendationsSection(
    validRecs: List<SearchResponse>,
    onNavigate: (Config) -> Unit,
    horizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
) {
    val similarScrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    ) {
        // Section Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding)
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Similar Content",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        LazyRow(
            state = similarScrollState,
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().desktopDragScroll(similarScrollState),
        ) {
            val displayRecs = validRecs.take(18)
            items(
                count = displayRecs.size,
                key = { index -> "${displayRecs[index].apiName}_${displayRecs[index].url}_$index" },
            ) { index ->
                val rec = displayRecs[index]
                val recProvider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(rec.apiName)
                if (recProvider != null) {
                    PosterCard(
                        item = rec,
                        provider = recProvider,
                        itemWidth = 200.dp,
                        onClick = {
                            onNavigate(Config.Details(recProvider.name, rec.url, rec.name, rec.posterUrl, null, false))
                        },
                        onPlayClick = {
                            onNavigate(Config.Details(recProvider.name, rec.url, rec.name, rec.posterUrl, null, true))
                        },
                    )
                }
            }
        }

        if (validRecs.size > 3) {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding),
                horizontalArrangement = Arrangement.End,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DesktopIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Scroll Left",
                        onClick = { coroutineScope.launch { similarScrollState.animateScrollBy(-600f) } },
                    )
                    DesktopIconButton(
                        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Scroll Right",
                        onClick = { coroutineScope.launch { similarScrollState.animateScrollBy(600f) } },
                    )
                }
            }
        }
    }
}
