package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.navigation.Config

@Composable
fun DetailsCollectionSection(
    collName: String,
    collBg: String?,
    collItems: List<SearchResponse>,
    provider: MainAPI,
    onNavigate: (Config) -> Unit,
) {
    val collScrollState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 250.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (collBg != null) {
                coil3.compose.AsyncImage(
                    model = collBg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(24.dp),
                )
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.65f)))
                Box(
                    modifier = Modifier.matchParentSize().background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color(0xFF0F0F0F), Color.Transparent, Color.Transparent, Color(0xFF0F0F0F)),
                        ),
                    ),
                )
            } else {
                Box(modifier = Modifier.matchParentSize().background(Color(0xFF161618)))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "COLLECTION / SAGA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = collName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                if (collItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    LazyRow(
                        state = collScrollState,
                        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                        contentPadding = PaddingValues(horizontal = 32.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    collScrollState.dispatchRawDelta(-dragAmount)
                                }
                            },
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
        }
    }
}
