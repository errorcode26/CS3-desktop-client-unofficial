package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.player.LanguagePriorityHelper
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.utils.Qualities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun SettingsStreamPrioritiesScreen(
    viewModel: SettingsViewModel,
) {
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(0) } // 0 = Resolutions, 1 = Audio Languages, 2 = Subtitle Languages
    val qualityPriorities by QualityDataHelper.qualityPriorities.collectAsState()
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 8.dp, bottom = 24.dp, end = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Top Header Card
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Stream Priorities",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Configure resolution hierarchy, audio track priority, and subtitle track priority for automatic stream selection.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Tab Selector & Action Presets Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            // Category Tabs
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                StreamPriorityTabButton(
                    text = "Resolutions",
                    icon = Icons.Default.HighQuality,
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                )
                StreamPriorityTabButton(
                    text = "Audio Languages",
                    icon = Icons.Default.GraphicEq,
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                )
                StreamPriorityTabButton(
                    text = "Subtitle Languages",
                    icon = Icons.Default.Subtitles,
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                )
            }

            // Quick Preset Buttons
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
                            Text("Reset", fontSize = 12.sp)
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
                            Text("Reset", fontSize = 12.sp)
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
                            Text("Reset", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // List Area
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
        ) {
            when (selectedTab) {
                0 -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(qualityList, key = { it.first }) { (qualVal, qualLabel) ->
                            val currentPriority = qualityPriorities[qualVal] ?: 4
                            StreamPriorityRow(
                                title = qualLabel,
                                subtitle = "Score bonus weight: +${currentPriority * 10} pts",
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
                    val sortedAudioLangs = remember(audioPriorities) {
                        PlayerConfig.GLOBAL_LANGUAGE_OPTIONS.sortedWith(
                            compareByDescending<Pair<String, String>> { audioPriorities[it.first] ?: 0 }
                                .thenBy { it.second }
                        )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(sortedAudioLangs, key = { it.first }) { (langCode, langName) ->
                            val currentPriority = audioPriorities[langCode] ?: 0
                            StreamPriorityRow(
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
                else -> {
                    val sortedSubLangs = remember(subtitlePriorities) {
                        PlayerConfig.GLOBAL_LANGUAGE_OPTIONS
                            .filter { it.first != "auto" && it.first != "off" && it.first != "original" }
                            .sortedWith(
                                compareByDescending<Pair<String, String>> { subtitlePriorities[it.first] ?: 0 }
                                    .thenBy { it.second }
                            )
                    }
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(sortedSubLangs, key = { it.first }) { (langCode, langName) ->
                            val currentPriority = subtitlePriorities[langCode] ?: 0
                            StreamPriorityRow(
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
            }
        }
    }
}

@Composable
private fun StreamPriorityTabButton(
    text: String,
    icon: ImageVector,
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
            Text(
                text = text,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun StreamPriorityRow(
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
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                    Box(contentAlignment = Alignment.Center) {
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
