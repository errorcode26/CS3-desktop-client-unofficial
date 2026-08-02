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
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController

enum class SettingsTab(val title: String) {
    ACCOUNTS("Accounts"),
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

@Composable
fun ComposeSettingsScreen(navController: NavController) {
    var selectedTab by remember { mutableStateOf(SettingsTab.PLAYER) }
    var activeSubScreen by remember { mutableStateOf<SettingsSubScreen?>(null) }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 32.dp),
    ) {
        // Left Pane: Sidebar Navigation
        Column(
            modifier = Modifier
                .width(260.dp)
                .fillMaxHeight()
                .padding(end = 24.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 32.dp, start = 8.dp),
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
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
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
            modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        )

        // Right Pane: Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 32.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Box(modifier = Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
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
                            SettingsTab.ACCOUNTS -> SettingsAccounts()
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
