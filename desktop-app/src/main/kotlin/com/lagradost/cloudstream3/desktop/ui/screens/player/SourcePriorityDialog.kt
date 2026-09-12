package com.lagradost.cloudstream3.desktop.ui.screens.player

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.player.LanguagePriorityHelper
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.settings.*
import com.lagradost.cloudstream3.desktop.ui.screens.settings.ComposeSettingsScreen
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSession
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.SettingsUiEvent
import com.lagradost.cloudstream3.utils.Qualities

@Composable
fun SourcePriorityDialog(
    show: Boolean,
    initialTab: Int = 0,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val settingsViewModel = SettingsSession.settingsViewModel
    var selectedTab by remember(initialTab, show) { mutableStateOf(initialTab) }
    val qualityPriorities by QualityDataHelper.qualityPriorities.collectAsState()
    val audioStack by LanguagePriorityHelper.audioLanguageStack.collectAsState()
    val subtitleStack by LanguagePriorityHelper.subtitleLanguageStack.collectAsState()

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
                            text = "Customize automatic stream ranking, resolution preferences, and audio/subtitle language priority queues",
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

                // Quick Presets
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (selectedTab) {
                        0 -> {
                            OutlinedButton(
                                onClick = {
                                    settingsViewModel.onEvent(SettingsUiEvent.SetQualityPreset4K)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer 4K", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    settingsViewModel.onEvent(SettingsUiEvent.SetQualityPreset1080p)
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer 1080p", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    settingsViewModel.onEvent(SettingsUiEvent.ResetQualityDefaults)
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
                                    settingsViewModel.onEvent(SettingsUiEvent.SetAudioLanguagePreset(listOf("eng,en", "original")))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer English", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    settingsViewModel.onEvent(SettingsUiEvent.SetAudioLanguagePreset(listOf("jpn,ja", "original", "eng,en")))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer Japanese", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    settingsViewModel.onEvent(SettingsUiEvent.ResetAudioDefaults)
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
                                    settingsViewModel.onEvent(SettingsUiEvent.SetSubtitleLanguagePreset(listOf("eng,en")))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text("Prefer English", fontSize = 12.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    settingsViewModel.onEvent(SettingsUiEvent.ResetSubtitleDefaults)
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
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)),
            ) {
                when (selectedTab) {
                    0 -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(qualityList, key = { it.first }) { (qualVal, qualLabel) ->
                                val currentPriority = qualityPriorities[qualVal] ?: 4
                                StreamPriorityRow(
                                    title = qualLabel,
                                    subtitle = "Score weight: +${currentPriority * 10} pts",
                                    priority = currentPriority,
                                    onPriorityChange = { newPriority ->
                                        settingsViewModel.onEvent(SettingsUiEvent.SetQualityPriority(qualVal, newPriority))
                                    },
                                )
                            }
                        }
                    }
                    1 -> {
                        PriorityStackManager(
                            activeStack = audioStack,
                            allOptions = PlayerConfig.GLOBAL_LANGUAGE_OPTIONS,
                            onReorder = { fromIdx, toIdx ->
                                settingsViewModel.onEvent(SettingsUiEvent.MoveAudioLanguage(fromIdx, toIdx))
                            },
                            onRemove = { code ->
                                settingsViewModel.onEvent(SettingsUiEvent.RemoveAudioLanguage(code))
                            },
                            onAdd = { code ->
                                settingsViewModel.onEvent(SettingsUiEvent.AddAudioLanguage(code))
                            },
                            searchPlaceholder = "Search audio languages...",
                        )
                    }
                    else -> {
                        val subOptions = remember {
                            PlayerConfig.GLOBAL_LANGUAGE_OPTIONS.filter {
                                it.first != "auto" && it.first != "off" && it.first != "original"
                            }
                        }
                        PriorityStackManager(
                            activeStack = subtitleStack,
                            allOptions = subOptions,
                            onReorder = { fromIdx, toIdx ->
                                settingsViewModel.onEvent(SettingsUiEvent.MoveSubtitleLanguage(fromIdx, toIdx))
                            },
                            onRemove = { code ->
                                settingsViewModel.onEvent(SettingsUiEvent.RemoveSubtitleLanguage(code))
                            },
                            onAdd = { code ->
                                settingsViewModel.onEvent(SettingsUiEvent.AddSubtitleLanguage(code))
                            },
                            searchPlaceholder = "Search subtitle languages...",
                        )
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
