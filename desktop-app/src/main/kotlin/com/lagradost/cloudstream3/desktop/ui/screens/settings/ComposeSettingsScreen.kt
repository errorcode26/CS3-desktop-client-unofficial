package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.navigation.Config

enum class SettingsTab(val title: String) {
    TRACKERS("Trackers"),
    EXTENSIONS("Extensions"),
    APPEARANCE("Appearance"),
    PLAYER("Playback Engine"),
    NETWORK("Network"),
    ADVANCED("Advanced"),
    DEVELOPER("Developer Tools"),
    UPDATES("Updates"),
    ABOUT("About"),
}

enum class SettingsSubScreen(val title: String) {
    SUBTITLE_EDITOR("Subtitle Appearance"),
    POSTER_EDITOR("Poster Layout"),
}

object SettingsSession {
    var selectedTab by mutableStateOf(SettingsTab.TRACKERS)
    var activeSubScreen by mutableStateOf<SettingsSubScreen?>(null)
}

@Composable
fun ComposeSettingsScreen(
    onNavigate: (Config) -> Unit,
    viewModel: com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsViewModel? = null
) {
    var selectedTab by SettingsSession::selectedTab
    var activeSubScreen by SettingsSession::activeSubScreen

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        // Left Pane: Sidebar Navigation
        Column(
            modifier = Modifier
                .width(230.dp)
                .fillMaxHeight()
                .padding(end = 16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 24.dp, start = 8.dp),
            )

            SettingsTab.values().forEach { tab ->
                val isSelected = selectedTab == tab && activeSubScreen == null
                Surface(
                    onClick = {
                        selectedTab = tab
                        activeSubScreen = null
                    },
                    shape = MaterialTheme.shapes.medium,
                    color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tab.title,
                            color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }

        // Vertical Divider
        VerticalDivider(
            modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )

        // Right Pane: Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 24.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                val currentSubScreen = activeSubScreen
                if (currentSubScreen != null) {
                    // Sub-screen mode: render dedicated editor with back header
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Back header
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { activeSubScreen = null }
                                .padding(bottom = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = currentSubScreen.title,
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                            )
                        }

                        // Sub-screen content
                        when (currentSubScreen) {
                            SettingsSubScreen.SUBTITLE_EDITOR -> SettingsSubtitleEditorScreen()
                            SettingsSubScreen.POSTER_EDITOR -> SettingsPosterEditorScreen()
                        }
                    }
                } else {
                    // Normal tab mode
                    androidx.compose.animation.Crossfade(
                        targetState = selectedTab,
                        animationSpec = androidx.compose.animation.core.tween(200),
                        label = "settings_crossfade",
                    ) { tab ->
                        when (tab) {
                            SettingsTab.TRACKERS -> SettingsAccounts()
                            SettingsTab.EXTENSIONS -> SettingsExtensions(onNavigate = onNavigate)
                            SettingsTab.APPEARANCE -> SettingsAppearance(
                                onNavigateToSubScreen = { activeSubScreen = it },
                            )
                            SettingsTab.PLAYER -> SettingsPlayer(
                                onNavigateToSubScreen = { activeSubScreen = it },
                            )
                            SettingsTab.NETWORK -> SettingsNetwork()
                            SettingsTab.ADVANCED -> SettingsAdvanced()
                            SettingsTab.DEVELOPER -> SettingsDeveloper()
                            SettingsTab.UPDATES -> SettingsUpdates()
                            SettingsTab.ABOUT -> SettingsAbout()
                        }
                    }
                }
            }
        }
    }
}
