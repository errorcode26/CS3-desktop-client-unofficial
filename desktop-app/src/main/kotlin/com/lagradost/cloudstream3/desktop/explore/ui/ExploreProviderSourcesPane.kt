package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme

@Composable
internal fun ExploreProviderSourcesPane(
    item: ExploreItem,
    matches: List<ProviderMatch>,
    isSearching: Boolean,
    onDismissRequest: () -> Unit,
    onSelectMatch: (ProviderMatch) -> Unit,
    onOpenAddonMode: ((ExploreItem) -> Unit)?,
    onNavigateToExtensions: ((initialTab: Int) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val theme = LocalDesktopTheme.current
    val stremioAddons by StremioAddonManager.addons.collectAsState()
    val hasStreamAddons = remember(stremioAddons) { stremioAddons.any { it.enabled && it.providesStreams } }

    var selectedQualityFilter by remember { mutableStateOf("All") }
    val count4K = remember(matches) { matches.count { it.qualityText?.contains("4k", ignoreCase = true) == true } }
    val count1080p = remember(matches) { matches.count { it.qualityText?.contains("1080", ignoreCase = true) == true } }

    val filteredMatches = remember(matches, selectedQualityFilter) {
        when (selectedQualityFilter) {
            "4K" -> matches.filter { it.qualityText?.contains("4k", ignoreCase = true) == true }
            "1080p" -> matches.filter { it.qualityText?.contains("1080", ignoreCase = true) == true }
            else -> matches
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = if (theme.isAmoled) Color(0xFF0A0C10) else theme.SurfaceElevated,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top Header Row with Close Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .width(3.dp)
                            .height(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primary),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "AVAILABLE EXTENSION SOURCES",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp,
                            color = theme.TextPrimary,
                        )
                        Text(
                            text = "Select an extension below to browse episodes and stream options",
                            fontSize = 11.5.sp,
                            color = theme.TextMuted,
                        )
                    }
                }

                IconButton(
                    onClick = onDismissRequest,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.06f))
                        .border(0.5.dp, Color.White.copy(alpha = 0.15f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }

            // Quality Filter Pills Bar
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ProviderFilterPill(
                    label = "All (${matches.size})",
                    selected = selectedQualityFilter == "All",
                    onClick = { selectedQualityFilter = "All" },
                )
                if (count4K > 0) {
                    ProviderFilterPill(
                        label = "4K UHD ($count4K)",
                        selected = selectedQualityFilter == "4K",
                        onClick = { selectedQualityFilter = "4K" },
                    )
                }
                if (count1080p > 0) {
                    ProviderFilterPill(
                        label = "1080p ($count1080p)",
                        selected = selectedQualityFilter == "1080p",
                        onClick = { selectedQualityFilter = "1080p" },
                    )
                }

                if (isSearching) {
                    Spacer(modifier = Modifier.weight(1f))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(11.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "Searching...",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Sources List Area
            if (isSearching && matches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White.copy(alpha = 0.02f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(32.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.5.dp,
                        )
                        Text(
                            text = "Searching extension providers...",
                            fontSize = 13.sp,
                            color = theme.TextMuted,
                        )
                    }
                }
            } else if (!isSearching && matches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.02f))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            text = "No Extension Matches Found",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.TextPrimary,
                        )
                        Text(
                            text = "None of your installed extensions returned results for this title. You can stream directly via Addon Mode, or install additional extension repositories.",
                            fontSize = 12.sp,
                            color = theme.TextMuted,
                            textAlign = TextAlign.Center,
                            lineHeight = 18.sp,
                            modifier = Modifier.fillMaxWidth(0.85f),
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 4.dp),
                        ) {
                            if (onOpenAddonMode != null && hasStreamAddons) {
                                Button(
                                    onClick = { onOpenAddonMode(item) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.Black,
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Play with Addon Instead", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
                                }
                            }
                            if (onNavigateToExtensions != null) {
                                OutlinedButton(
                                    onClick = {
                                        onDismissRequest()
                                        onNavigateToExtensions.invoke(0)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Install Extension Sources", fontSize = 12.5.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(filteredMatches) { match ->
                            ProviderStreamCard(
                                match = match,
                                onClick = { onSelectMatch(match) },
                            )
                        }
                    }

                    // Sleek Dual Streaming Engines Guide
                    if (filteredMatches.size <= 3) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color.White.copy(alpha = 0.03f),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.08f)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                                    modifier = Modifier.size(18.dp).padding(top = 1.dp),
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "PLAYBACK ENGINE OVERVIEW",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.8.sp,
                                        color = theme.TextPrimary,
                                    )
                                    Text(
                                        text = "• Addon Mode (left): Fast torrent & debrid playback via configured Stremio addons.\n• Extension Sources (above): Direct scrapers from installed CloudStream plugins.",
                                        fontSize = 11.5.sp,
                                        lineHeight = 17.sp,
                                        color = theme.TextMuted,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Bottom info footnote
            Text(
                text = "Discovered ${matches.size} total sources across your installed extension repositories.",
                fontSize = 11.sp,
                color = theme.TextMuted.copy(alpha = 0.7f),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}
