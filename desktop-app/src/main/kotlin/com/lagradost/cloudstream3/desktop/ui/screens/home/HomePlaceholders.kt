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

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun HomeHeroCarouselPlaceholder() {
    val windowInfo = androidx.compose.ui.platform.LocalWindowInfo.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val dynamicHeight = with(density) { (windowInfo.containerSize.height * 0.85f).toDp() }.coerceIn(400.dp, 1000.dp)
    
    val dockPosition by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.dockPosition.collectAsState()
    val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 98.dp else 32.dp
    val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 98.dp else 32.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(dynamicHeight),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Main backdrop shimmer
        Box(modifier = Modifier.fillMaxSize().shimmerBackground())
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = paddingStart, end = paddingEnd, bottom = 56.dp, top = 24.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.Start,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.5f),
            ) {
                // Title
                Box(
                    modifier = Modifier.width(300.dp).height(50.dp).clip(RoundedCornerShape(8.dp)).shimmerBackground()
                )
                Spacer(Modifier.height(16.dp))
                // Rating/Year
                Box(
                    modifier = Modifier.width(150.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground()
                )
                Spacer(Modifier.height(12.dp))
                // Plot
                Box(modifier = Modifier.fillMaxWidth(0.8f).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                Spacer(Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth(0.7f).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerBackground())
                Spacer(Modifier.height(24.dp))
                // Buttons
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.height(56.dp).width(190.dp).clip(RoundedCornerShape(12.dp)).shimmerBackground())
                    Box(modifier = Modifier.height(56.dp).width(160.dp).clip(RoundedCornerShape(12.dp)).shimmerBackground())
                }
            }
        }
    }
}

@Composable
fun CategoryRowPlaceholder(
    title: String,
    showLargeHeader: Boolean = false,
) {
    val dockPosition by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.dockPosition.collectAsState()
    val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 22.dp
    val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 22.dp

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().padding(start = paddingStart, end = paddingEnd)) {
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
                        .padding(start = 10.dp, end = 10.dp, top = 24.dp, bottom = 8.dp),
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
                        .padding(start = 10.dp),
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
