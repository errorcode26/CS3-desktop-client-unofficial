package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
        contentAlignment = Alignment.Center
    ) {
        // Main center card
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f) // Matches approximate width of coverflow center item
                .clip(RoundedCornerShape(12.dp))
                .shimmerBackground()
        )
    }
}

@Composable
fun CategoryRowPlaceholder(
    title: String,
    maxWidthConstraint: Dp,
    showLargeHeader: Boolean = false
) {
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.widthIn(max = maxWidthConstraint)) {
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
                verticalAlignment = Alignment.CenterVertically
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
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                repeat(10) {
                    Box(
                        modifier = Modifier
                            .width(115.dp) // standard poster width
                            .height(170.dp) // standard poster height
                            .padding(4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .shimmerBackground()
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
