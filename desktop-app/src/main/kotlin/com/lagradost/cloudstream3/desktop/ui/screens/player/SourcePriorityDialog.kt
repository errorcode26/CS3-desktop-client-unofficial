package com.lagradost.cloudstream3.desktop.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.player.LanguagePriorityHelper
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SourcePriorityDialog(
    show: Boolean,
    initialTab: Int = 0,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val scope = rememberCoroutineScope()
    var selectedTab by remember(initialTab, show) { mutableStateOf(initialTab) } // 0 = Resolutions, 1 = Audio Languages, 2 = Subtitle Languages, 3 = Server Sources
    val qualityPriorities by QualityDataHelper.qualityPriorities.collectAsState()
    val sourcePriorities by QualityDataHelper.sourcePriorities.collectAsState()
    val discoveredSources by QualityDataHelper.discoveredSources.collectAsState()
    val audioPriorities by LanguagePriorityHelper.audioPriorities.collectAsState()
    val subtitlePriorities by LanguagePriorityHelper.subtitlePriorities.collectAsState()

    val qualityList = remember {
        listOf(
            Qualities.P2160.value to "4K (2160p)",
            Qualities.P1440.value to "1440p (QHD)",
            Qualities.P1080.value to "1080p (Full HD)",
            Qualities.P720.value to "720p (HD)",
            0 to "Auto (Adaptive Stream)",
            Qualities.P480.value to "480p (SD)",
            Qualities.P360.value to "360p (Low)",
            Qualities.P240.value to "240p (Very Low)",
            Qualities.P144.value to "144p (Minimum)",
            Qualities.Unknown.value to "Unknown Resolution",
        )
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column {
                        Text(
                            text = "Stream & Language Priorities",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = "Customize automatic stream ranking, resolution preferences, audio/sub priority, and server sources",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Presets & Tab Switcher
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Tabs
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TabButton(
                        text = "Resolutions",
                        icon = Icons.Default.HighQuality,
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                    )
                    TabButton(
                        text = "Audio Languages",
                        icon = Icons.Default.GraphicEq,
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                    )
                    TabButton(
                        text = "Subtitle Languages",
                        icon = Icons.Default.Subtitles,
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                    )
                    TabButton(
                        text = "Server Sources (${discoveredSources.size})",
                        icon = Icons.Default.Dns,
                        selected = selectedTab == 3,
                        onClick = { selectedTab = 3 },
                    )
                }

                // Quick Presets
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (selectedTab) {
                        0 -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        QualityDataHelper.setQualityPriority(Qualities.P2160.value, 10)
                                        QualityDataHelper.setQualityPriority(Qualities.P1080.value, 8)
                                        QualityDataHelper.setQualityPriority(Qualities.P720.value, 5)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer 4K", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        QualityDataHelper.setQualityPriority(Qualities.P1080.value, 10)
                                        QualityDataHelper.setQualityPriority(Qualities.P720.value, 8)
                                        QualityDataHelper.setQualityPriority(Qualities.P2160.value, 4)
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer 1080p", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        QualityDataHelper.resetToDefaults()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Defaults", fontSize = 12.sp)
                            }
                        }
                        1 -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        LanguagePriorityHelper.setAudioPreset(mapOf("eng,en" to 10, "original" to 8))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer English", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        LanguagePriorityHelper.setAudioPreset(mapOf("jpn,ja" to 10, "original" to 8, "eng,en" to 6))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer Japanese", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        LanguagePriorityHelper.resetAudioDefaults()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Defaults", fontSize = 12.sp)
                            }
                        }
                        2 -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        LanguagePriorityHelper.setSubtitlePreset(mapOf("eng,en" to 10))
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer English", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        LanguagePriorityHelper.resetSubtitleDefaults()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Defaults", fontSize = 12.sp)
                            }
                        }
                        3 -> {
                            OutlinedButton(
                                onClick = {
                                    scope.launch(Dispatchers.IO) {
                                        QualityDataHelper.resetToDefaults()
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reset Defaults", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Content Area
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            ) {
                when (selectedTab) {
                    0 -> {
                        // Resolution Priority List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(qualityList, key = { it.first }) { (qualVal, qualLabel) ->
                                val currentPriority = qualityPriorities[qualVal] ?: 4
                                PriorityRowItem(
                                    title = qualLabel,
                                    subtitle = "Score weight: +${currentPriority * 10} pts",
                                    priority = currentPriority,
                                    onPriorityChange = { newPriority ->
                                        scope.launch(Dispatchers.IO) {
                                            QualityDataHelper.setQualityPriority(qualVal, newPriority)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    1 -> {
                        // Audio Languages Priority List
                        val sortedAudioLangs = remember(audioPriorities) {
                            PlayerConfig.GLOBAL_LANGUAGE_OPTIONS.sortedWith(
                                compareByDescending<Pair<String, String>> { audioPriorities[it.first] ?: 0 }
                                    .thenBy { it.second }
                            )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(sortedAudioLangs, key = { it.first }) { (langCode, langName) ->
                                val currentPriority = audioPriorities[langCode] ?: 0
                                PriorityRowItem(
                                    title = langName,
                                    subtitle = if (currentPriority > 0) "Priority rank weight: +${currentPriority * 40} pts (Active)" else "Inactive (Disabled from auto-select)",
                                    priority = currentPriority,
                                    minPriority = 0,
                                    maxPriority = 15,
                                    onPriorityChange = { newPriority ->
                                        scope.launch(Dispatchers.IO) {
                                            LanguagePriorityHelper.setAudioPriority(langCode, newPriority)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    2 -> {
                        // Subtitle Languages Priority List
                        val sortedSubLangs = remember(subtitlePriorities) {
                            PlayerConfig.GLOBAL_LANGUAGE_OPTIONS
                                .filter { it.first != "auto" && it.first != "off" && it.first != "original" }
                                .sortedWith(
                                    compareByDescending<Pair<String, String>> { subtitlePriorities[it.first] ?: 0 }
                                        .thenBy { it.second }
                                )
                        }
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(sortedSubLangs, key = { it.first }) { (langCode, langName) ->
                                val currentPriority = subtitlePriorities[langCode] ?: 0
                                PriorityRowItem(
                                    title = langName,
                                    subtitle = if (currentPriority > 0) "Priority rank: #$currentPriority (Active)" else "Inactive (Disabled from auto-select)",
                                    priority = currentPriority,
                                    minPriority = 0,
                                    maxPriority = 15,
                                    onPriorityChange = { newPriority ->
                                        scope.launch(Dispatchers.IO) {
                                            LanguagePriorityHelper.setSubtitlePriority(langCode, newPriority)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    else -> {
                        // Server Sources Priority List
                        if (discoveredSources.isEmpty()) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text("No Server Sources Discovered Yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Play or scrape any media title to automatically discover and rank video servers.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        } else {
                            val sortedSources = remember(discoveredSources, sourcePriorities) {
                                discoveredSources.sortedByDescending { sourcePriorities[it] ?: 0 }
                            }
                            LazyColumn(
                                modifier = Modifier.fillMaxSize().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                items(sortedSources, key = { it }) { sourceName ->
                                    val currentPriority = sourcePriorities[sourceName] ?: 0
                                    PriorityRowItem(
                                        title = sourceName,
                                        subtitle = if (currentPriority > 0) "Boosted (+${currentPriority} pts)" else if (currentPriority < 0) "Demoted (${currentPriority} pts)" else "Neutral (0 pts)",
                                        priority = currentPriority,
                                        minPriority = -10,
                                        maxPriority = 20,
                                        onPriorityChange = { newPriority ->
                                            scope.launch(Dispatchers.IO) {
                                                QualityDataHelper.setSourcePriority(sourceName, newPriority)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismissRequest,
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun PriorityRowItem(
    title: String,
    subtitle: String,
    priority: Int,
    minPriority: Int = 0,
    maxPriority: Int = 15,
    onPriorityChange: (Int) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = { if (priority > minPriority) onPriorityChange(priority - 1) },
                    enabled = priority > minPriority,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = when {
                        priority > 6 -> MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        priority < 0 -> MaterialTheme.colorScheme.error.copy(alpha = 0.2f)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    modifier = Modifier.widthIn(min = 44.dp),
                ) {
                    Text(
                        text = "$priority",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            priority > 6 -> MaterialTheme.colorScheme.primary
                            priority < 0 -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }

                IconButton(
                    onClick = { if (priority < maxPriority) onPriorityChange(priority + 1) },
                    enabled = priority < maxPriority,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}
