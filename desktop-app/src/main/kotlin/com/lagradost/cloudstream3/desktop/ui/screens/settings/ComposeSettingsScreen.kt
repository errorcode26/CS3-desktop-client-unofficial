package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.`with`
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.navigation.Config

import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class SettingsTab(val title: String, val icon: ImageVector) {
    APPEARANCE("Appearance", Icons.Default.Palette),
    PLAYER("Player", Icons.Default.PlayCircle),
    SERVICES("Services", Icons.Default.Hub),
    SYSTEM("System", Icons.Default.Settings),
}

enum class SettingsSubScreen(val title: String) {
    // Appearance
    THEME("Theme & Colors"),
    LAYOUT("Display & Layout"),
    DETAILS_LAYOUT("Details Page Layout"),
    EFFECTS("Effects & Blur"),
    POSTER_EDITOR("Poster Editor"),

    // Player
    PLAYER_CONTROLS("Player & Controls"),
    SUBTITLES("Subtitle Styling"),

    // Services
    TRACKERS("Accounts & Trackers"),
    INTEGRATIONS("Metadata & Services"),
    EXTENSIONS("Extensions & Plugins"),

    // System
    NETWORK("Network & Connection"),
    ADVANCED("Storage & Advanced"),
    DEVELOPER("Developer & Logs"),
    UPDATES("Updates"),
    ABOUT("About"),
}

object SettingsSession {
    var selectedTab by mutableStateOf(SettingsTab.APPEARANCE)
    var activeSubScreen by mutableStateOf<SettingsSubScreen?>(null)
    var highlightedSetting by mutableStateOf<String?>(null)
}

@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun ComposeSettingsScreen(
    onNavigate: (Config) -> Unit,
    viewModel: com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsViewModel? = null,
) {
    var selectedTab by SettingsSession::selectedTab
    var activeSubScreen by SettingsSession::activeSubScreen
    val settingsViewModel = remember { SettingsViewModel() }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp),
    ) {
        // Left Pane
        Column(
            modifier = Modifier
                .width(270.dp)
                .fillMaxHeight()
                .padding(end = 16.dp),
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp, start = 8.dp),
            )

            var searchQuery by remember { mutableStateOf("") }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search settings...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            if (searchQuery.isNotBlank()) {
                val query = searchQuery.lowercase()
                val results = remember(query) {
                    SettingsSearchIndex.searchIndex.filter {
                        it.title.lowercase().contains(query) || it.keywords.any { kw -> kw.lowercase().contains(query) }
                    }
                }

                if (results.isEmpty()) {
                    Text(
                        text = "No results found.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp, top = 8.dp),
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        results.forEach { result ->
                            Surface(
                                onClick = {
                                    selectedTab = result.tab
                                    activeSubScreen = result.subScreen
                                    SettingsSession.highlightedSetting = result.uiLabel
                                    searchQuery = ""
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                                    Text(
                                        text = result.title,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                    Text(
                                        text = "${result.tab.title} > ${result.subScreen?.title ?: ""}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsTab.values().forEach { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            onClick = {
                                selectedTab = tab
                                activeSubScreen = null
                            },
                            shape = MaterialTheme.shapes.medium,
                            color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.title,
                                    modifier = Modifier.size(20.dp),
                                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Text(
                                    text = tab.title,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }

        // Vertical Divider
        VerticalDivider(
            modifier = Modifier.fillMaxHeight().padding(vertical = 12.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )

        // Right Pane
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(start = 24.dp),
            contentAlignment = Alignment.TopStart,
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = activeSubScreen,
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally { width -> width } + fadeIn()) `with` (slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()) `with` (slideOutHorizontally { width -> width } + fadeOut())
                        }
                    },
                    label = "SettingsSubScreenTransition",
                ) { currentSubScreen ->
                    if (currentSubScreen != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
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
                            when (currentSubScreen) {
                                SettingsSubScreen.THEME -> SettingsAppearanceThemeScreen()
                                SettingsSubScreen.LAYOUT -> SettingsAppearanceLayoutScreen(onNavigateToSubScreen = { activeSubScreen = it })
                                SettingsSubScreen.DETAILS_LAYOUT -> SettingsDetailsSectionsScreen()
                                SettingsSubScreen.EFFECTS -> SettingsAppearanceEffectsScreen()
                                SettingsSubScreen.POSTER_EDITOR -> SettingsPosterEditorScreen()
                                
                                SettingsSubScreen.PLAYER_CONTROLS -> SettingsPlayer(viewModel = settingsViewModel, onNavigateToSubScreen = { activeSubScreen = it })
                                SettingsSubScreen.SUBTITLES -> SettingsSubtitleEditorScreen(viewModel = settingsViewModel)
                                
                                SettingsSubScreen.TRACKERS -> SettingsAccounts(viewModel = settingsViewModel)
                                SettingsSubScreen.INTEGRATIONS -> SettingsIntegrations()
                                SettingsSubScreen.EXTENSIONS -> SettingsExtensions(onNavigate = onNavigate)
                                
                                SettingsSubScreen.NETWORK -> SettingsNetwork(viewModel = settingsViewModel)
                                SettingsSubScreen.ADVANCED -> SettingsAdvanced(viewModel = settingsViewModel)
                                SettingsSubScreen.DEVELOPER -> SettingsDeveloper()
                                SettingsSubScreen.UPDATES -> SettingsUpdates()
                                SettingsSubScreen.ABOUT -> SettingsAbout()
                            }
                        }
                    } else {
                        Crossfade(
                            targetState = selectedTab,
                            animationSpec = tween(200),
                            label = "settings_crossfade",
                        ) { tab ->
                            when (tab) {
                                SettingsTab.APPEARANCE -> SettingsAppearanceMainScreen(onNavigate = { activeSubScreen = it })
                                SettingsTab.PLAYER -> SettingsPlayerMainScreen(onNavigate = { activeSubScreen = it })
                                SettingsTab.SERVICES -> SettingsServicesMainScreen(onNavigate = { activeSubScreen = it })
                                SettingsTab.SYSTEM -> SettingsSystemMainScreen(onNavigate = { activeSubScreen = it })
                            }
                        }
                    }
                }
            }
        }
    }
}
