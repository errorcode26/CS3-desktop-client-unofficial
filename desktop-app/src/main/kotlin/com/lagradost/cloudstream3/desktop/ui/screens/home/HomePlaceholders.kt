package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.components.shimmerBackground

@Composable
fun HomeHeroCarouselPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(550.dp)
            .padding(top = 16.dp, bottom = 0.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Main center card
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f) // Matches approximate width of coverflow center item
                .clip(RoundedCornerShape(12.dp))
                .shimmerBackground(),
        )
    }
}

@Composable
fun CategoryRowPlaceholder(
    title: String,
    maxWidthConstraint: Dp,
    showLargeHeader: Boolean = false,
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        BoxWithConstraints(modifier = Modifier.widthIn(max = maxWidthConstraint).fillMaxWidth()) {
            val availableWidth = this.maxWidth
            val gridScale by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.gridScale.collectAsState()
            val baseWidth = when (gridScale) {
                "Compact" -> 150.dp
                "Large" -> 220.dp
                else -> 190.dp
            }

            val netWidth = availableWidth - 8.dp
            val exactColumns = (netWidth + 12.dp) / (baseWidth + 12.dp)
            val columns = exactColumns.toInt().coerceAtLeast(1)
            val optimalItemWidth = ((netWidth + 12.dp) / columns) - 12.dp

            Column(modifier = Modifier.fillMaxWidth()) {
                if (showLargeHeader) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 0.dp),
                    )
                }

                // Header for the row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }

                // Row of shimmering posters
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(columns) {
                        Box(
                            modifier = Modifier
                                .width(optimalItemWidth)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(8.dp))
                                .shimmerBackground(),
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
