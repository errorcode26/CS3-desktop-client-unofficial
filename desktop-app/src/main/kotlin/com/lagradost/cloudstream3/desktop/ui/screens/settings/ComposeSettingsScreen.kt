package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController

enum class SettingsTab(val title: String) {
    PLAYER("Playback Engine"),
    APPEARANCE("Appearance"),
    NETWORK("Network"),
    ADVANCED("Advanced"),
    LOGCAT("Logcat"),
    ABOUT("About"),
    ACCOUNTS("Accounts"),
}

@Composable
fun ComposeSettingsScreen(navController: NavController, onErrorLogs: () -> Unit) {
    var selectedTab by remember { mutableStateOf(SettingsTab.PLAYER) }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        // Container to keep max width reasonable on ultra-wide screens
        Row(modifier = Modifier.widthIn(max = 1600.dp).fillMaxSize()) {
            // Left Pane: Sidebar Navigation
            Column(
                modifier = Modifier
                    .widthIn(min = 160.dp, max = 220.dp)
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
                    val isSelected = selectedTab == tab
                    Surface(
                        onClick = { selectedTab = tab },
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
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = selectedTab,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "settings_crossfade",
                ) { tab ->
                    when (tab) {
                        SettingsTab.PLAYER -> SettingsPlayer()
                        SettingsTab.APPEARANCE -> SettingsAppearance()
                        SettingsTab.NETWORK -> SettingsNetwork()
                        SettingsTab.ADVANCED -> SettingsAdvanced(onErrorLogs)
                        SettingsTab.LOGCAT -> SettingsLogcat()
                        SettingsTab.ABOUT -> SettingsAbout()
                        SettingsTab.ACCOUNTS -> SettingsAccounts()
                    }
                }
            }
        }
    }
}
