package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import kotlinx.coroutines.launch

@Composable
fun DetailsRecommendationsSection(
    validRecs: List<SearchResponse>,
    onNavigate: (Screen) -> Unit,
) {
    val similarScrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .padding(vertical = 24.dp),
    ) {
        Text(
            text = "Similar Content",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
        )
        LazyRow(
            state = similarScrollState,
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxWidth().pointerInput(Unit) {
                detectHorizontalDragGestures { change, dragAmount ->
                    change.consume()
                    similarScrollState.dispatchRawDelta(-dragAmount)
                }
            },
        ) {
            items(validRecs.take(18)) { rec ->
                val recProvider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(rec.apiName)
                if (recProvider != null) {
                        PosterCard(
                            item = rec,
                            provider = recProvider,
                            itemWidth = 150.dp,
                            onClick = {
                                onNavigate(Screen.Details(recProvider.name, rec.url, rec.name, rec.posterUrl, null, false))
                            },
                            onPlayClick = {
                                onNavigate(Screen.Details(recProvider.name, rec.url, rec.name, rec.posterUrl, null, true))
                            }
                        )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.End) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            ) {
                Row {
                    IconButton(onClick = { coroutineScope.launch { similarScrollState.animateScrollBy(-500f) } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Scroll Left", tint = MaterialTheme.colorScheme.onSurface)
                    }
                    IconButton(onClick = { coroutineScope.launch { similarScrollState.animateScrollBy(500f) } }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Scroll Right", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}
