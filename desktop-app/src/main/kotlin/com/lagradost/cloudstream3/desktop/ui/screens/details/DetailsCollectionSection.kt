package com.lagradost.cloudstream3.desktop.ui.screens.details

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.ui.components.PosterCard
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen

@Composable
fun DetailsCollectionSection(
    collName: String,
    collBg: String?,
    collItems: List<SearchResponse>,
    provider: MainAPI,
    onNavigate: (Screen) -> Unit,
) {
    val collScrollState = rememberLazyListState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 8.dp)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f), RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF161618)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            if (collBg != null) {
                coil3.compose.AsyncImage(
                    model = collBg,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize().blur(16.dp),
                )
                Box(modifier = Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.65f)))
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 18.dp),
            ) {
                Text(
                    text = "COLLECTION / SAGA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = collName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
                if (collItems.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(
                        state = collScrollState,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures { change, dragAmount ->
                                    change.consume()
                                    collScrollState.dispatchRawDelta(-dragAmount)
                                }
                            },
                    ) {
                        items(collItems) { partItem ->
                            PosterCard(
                                item = partItem,
                                provider = provider,
                                itemWidth = 125.dp,
                                onClick = {
                                    onNavigate(Screen.Details(provider.name, partItem.url, partItem.name, partItem.posterUrl, null, false))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
