package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons

import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class LeafTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    APPEARANCE("Appearance & Theme", Icons.Outlined.Palette),
    PLAYER("Playback & Video", Icons.Outlined.PlayCircle),
    STREAM_PRIORITIES("Stream Priorities", Icons.Outlined.Tune),
    AUDIO("Audio & Equalizer", Icons.Outlined.GraphicEq),
    SUBTITLES("Subtitles & Styling", Icons.Outlined.Subtitles),
    DOWNLOADS("Downloads Engine", Icons.Outlined.Download),
    ACCOUNTS("Profiles & Accounts", Icons.Outlined.AccountCircle),
    INTEGRATIONS("Metadata & Providers", Icons.Outlined.AutoAwesome),
    EXTENSIONS("Extensions & Sources", Icons.Outlined.Extension),
    NETWORK("Network & DNS", Icons.Outlined.Router),
    DEVELOPER("Developer & Logs", Icons.Outlined.Terminal),
    ADVANCED("Advanced & Storage", Icons.Outlined.FolderOpen),
    ABOUT("About & Updates", Icons.Outlined.Info),

    // Compatibility aliases for SettingsSearchIndex and legacy references
    THEME("Theme & Colors", Icons.Outlined.Palette),
    LAYOUT("Layout & Dock", Icons.Outlined.Palette),
    DETAILS("Details Page", Icons.Outlined.Palette),
    EFFECTS("Backdrop & Effects", Icons.Outlined.Palette),
    SHORTCUTS("Keyboard Shortcuts", Icons.Outlined.Keyboard),
    SUBTITLES_LEAF("Subtitles & Styling", Icons.Outlined.Subtitles),
    ADDONS("External Addons", Icons.Outlined.Extension),
}

// Keeps SettingsSearchIndex compiling without changes
typealias SettingsTab = LeafTab

enum class SettingsSubScreen(val title: String) {
    APPEARANCE_NAV_DOCK("Navigation & Dock"),
    APPEARANCE_THEME_WALLPAPER("Theme, Colors & Wallpaper"),
    APPEARANCE_POSTERS_BADGES("Posters & Provider Branding"),
    APPEARANCE_HOME_FEED("Home Feed & Cinema"),
    DETAILS_LAYOUT("Details Page Layout & Sections"),
    POSTER_EDITOR("Poster Workshop Studio"),
    PLAYER_RENDERING_ENGINE("Video & Hardware Engine"),
    PLAYER_AUDIO_EQ("Audio Processing & Equalizer"),
    PLAYER_AUTOPLAY_SKIP("Auto-Play & Skip Automation"),
    PLAYER_DOWNLOADS("Downloads & Storage Engine"),
    SUBTITLES("Subtitle Styling Studio"),
    KEYBOARD_SHORTCUTS("Keyboard Shortcuts & Hotkeys"),
    INTEGRATIONS_TMDB("TMDB Engine Studio"),
    INTEGRATIONS_ANIME("Anime Engines Studio"),
}

object SettingsSession {
    var selectedLeaf by mutableStateOf(LeafTab.APPEARANCE)
    var activeSubScreen by mutableStateOf<SettingsSubScreen?>(null)
    var highlightedSetting by mutableStateOf<String?>(null)
    val settingsViewModel by lazy { SettingsViewModel() }
}

private val MAIN_NAV_ITEMS: List<LeafTab> = listOf(
    LeafTab.APPEARANCE,
    LeafTab.PLAYER,
    LeafTab.STREAM_PRIORITIES,
    LeafTab.AUDIO,
    LeafTab.SUBTITLES,
    LeafTab.DOWNLOADS,
    LeafTab.ACCOUNTS,
    LeafTab.INTEGRATIONS,
    LeafTab.EXTENSIONS,
    LeafTab.NETWORK,
    LeafTab.DEVELOPER,
    LeafTab.ADVANCED,
)

private val BOTTOM_NAV_ITEMS: List<LeafTab> = listOf(
    LeafTab.ABOUT,
)



@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun ComposeSettingsScreen(
    onNavigate: (Config) -> Unit,
    viewModel: com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsViewModel? = null,
) {
    var selectedLeaf by SettingsSession::selectedLeaf
    var activeSubScreen by SettingsSession::activeSubScreen
    val settingsViewModel = SettingsSession.settingsViewModel

    if (activeSubScreen == SettingsSubScreen.POSTER_EDITOR) {
        SettingsPosterEditorScreen(onBack = { activeSubScreen = null })
    } else {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp)) {

            // ── Left Pane ─────────────────────────────────────────────
            Column(modifier = Modifier.width(210.dp).fillMaxHeight().padding(end = 12.dp)) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 20.dp, start = 8.dp),
            )

            var searchQuery by remember { mutableStateOf("") }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text("Search...", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), fontSize = 13.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(13.dp))
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp),
                shape = RoundedCornerShape(10.dp),
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
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
                    Text("No results.", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, top = 4.dp))
                } else {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                        results.forEach { result ->
                            val breadcrumb = if (result.subScreen != null) "${result.tab.title} > ${result.subScreen.title}" else result.tab.title

                            Surface(
                                onClick = {
                                    selectedLeaf = result.tab
                                    activeSubScreen = result.subScreen
                                    SettingsSession.highlightedSetting = result.uiLabel
                                    searchQuery = ""
                                },
                                shape = MaterialTheme.shapes.medium,
                                color = Color.Transparent,
                                modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                    Text(result.title, color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                                    Text(breadcrumb, color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 1.dp))
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    MAIN_NAV_ITEMS.forEach { tab ->
                        SidebarLeafItem(
                            title = tab.title,
                            icon = tab.icon,
                            isSelected = selectedLeaf == tab && activeSubScreen == null,
                            onClick = { selectedLeaf = tab; activeSubScreen = null },
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    )

                    BOTTOM_NAV_ITEMS.forEach { tab ->
                        SidebarLeafItem(
                            title = tab.title,
                            icon = tab.icon,
                            isSelected = selectedLeaf == tab && activeSubScreen == null,
                            onClick = { selectedLeaf = tab; activeSubScreen = null },
                        )
                    }
                }
            }
        }

        // ── Divider ───────────────────────────────────────────────
        VerticalDivider(
            modifier = Modifier.fillMaxHeight().padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f),
        )

        // ── Right Pane ────────────────────────────────────────────
        Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp),
            contentAlignment = Alignment.TopStart) {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = activeSubScreen,
                    modifier = Modifier.fillMaxSize(),
                    transitionSpec = {
                        if (targetState != null) {
                            (slideInHorizontally { width -> width } + fadeIn()) togetherWith (slideOutHorizontally { width -> -width } + fadeOut())
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn()) togetherWith (slideOutHorizontally { width -> width } + fadeOut())
                        }
                    },
                    label = "SettingsSubScreenTransition",
                ) { currentSubScreen ->
                    if (currentSubScreen != null) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { activeSubScreen = null },
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(
                                            Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back",
                                            tint = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            text = "Back",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }

                                Column {
                                    Text(
                                        text = "${SettingsSession.selectedLeaf.title}  ›  ${currentSubScreen.title}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Text(
                                        text = currentSubScreen.title,
                                        style = MaterialTheme.typography.titleLarge,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (currentSubScreen) {
                                    SettingsSubScreen.APPEARANCE_NAV_DOCK        -> SettingsNavDockScreen()
                                    SettingsSubScreen.APPEARANCE_THEME_WALLPAPER -> SettingsThemeWallpaperScreen()
                                    SettingsSubScreen.APPEARANCE_POSTERS_BADGES  -> SettingsPostersBadgesScreen(onNavigateToSubScreen = { activeSubScreen = it })
                                    SettingsSubScreen.APPEARANCE_HOME_FEED       -> SettingsHomeFeedScreen(onNavigateToSubScreen = { activeSubScreen = it })
                                    SettingsSubScreen.DETAILS_LAYOUT             -> SettingsDetailsSectionsScreen()
                                    SettingsSubScreen.POSTER_EDITOR              -> SettingsPosterEditorScreen(onBack = { activeSubScreen = null })
                                    SettingsSubScreen.PLAYER_RENDERING_ENGINE    -> SettingsPlayerRenderingScreen(viewModel = settingsViewModel)
                                    SettingsSubScreen.PLAYER_AUDIO_EQ            -> SettingsPlayerAudioScreen(viewModel = settingsViewModel)
                                    SettingsSubScreen.PLAYER_AUTOPLAY_SKIP       -> SettingsPlayerAutoPlayScreen(viewModel = settingsViewModel)
                                    SettingsSubScreen.PLAYER_DOWNLOADS           -> SettingsPlayerDownloadsScreen(viewModel = settingsViewModel)
                                    SettingsSubScreen.SUBTITLES                  -> SettingsSubtitleEditorScreen(viewModel = settingsViewModel)
                                    SettingsSubScreen.KEYBOARD_SHORTCUTS         -> SettingsShortcutsScreen()
                                    SettingsSubScreen.INTEGRATIONS_TMDB          -> SettingsTmdbScreen()
                                    SettingsSubScreen.INTEGRATIONS_ANIME         -> SettingsAnimeScreen()
                                }
                            }
                        }
                    } else {
                        AnimatedContent(
                            targetState = selectedLeaf,
                            transitionSpec = {
                                (fadeIn(tween(160)) + slideInVertically(tween(160)) { it / 24 })
                                    .togetherWith(fadeOut(tween(90)))
                            },
                            label = "SettingsLeafTransition",
                            modifier = Modifier.fillMaxSize(),
                        ) { currentLeaf ->
                            when (currentLeaf) {
                                LeafTab.APPEARANCE     -> SettingsAppearanceScreen(onNavigateToSubScreen = { activeSubScreen = it })
                                LeafTab.PLAYER         -> SettingsPlayerPlaybackScreen(viewModel = settingsViewModel, onNavigateToSubScreen = { activeSubScreen = it })
                                LeafTab.STREAM_PRIORITIES -> SettingsStreamPrioritiesScreen(viewModel = settingsViewModel)
                                LeafTab.AUDIO          -> SettingsPlayerAudioScreen(viewModel = settingsViewModel)
                                LeafTab.SUBTITLES,
                                LeafTab.SUBTITLES_LEAF -> SettingsSubtitleEditorScreen(viewModel = settingsViewModel)
                                LeafTab.DOWNLOADS      -> SettingsPlayerDownloadsScreen(viewModel = settingsViewModel)
                                LeafTab.ADVANCED       -> SettingsAdvancedScreen(viewModel = settingsViewModel)
                                LeafTab.ACCOUNTS       -> SettingsAccounts(viewModel = settingsViewModel)
                                LeafTab.INTEGRATIONS   -> SettingsIntegrations(onNavigateToSubScreen = { activeSubScreen = it })
                                LeafTab.EXTENSIONS,
                                LeafTab.ADDONS         -> SettingsExtensions(onNavigate = onNavigate)
                                LeafTab.NETWORK        -> SettingsNetworkScreen(viewModel = settingsViewModel)
                                LeafTab.DEVELOPER      -> SettingsDeveloper(viewModel = settingsViewModel)
                                LeafTab.ABOUT          -> SettingsAboutAndUpdates(viewModel = settingsViewModel)
                                LeafTab.THEME          -> SettingsAppearanceThemeScreen()
                                LeafTab.LAYOUT         -> SettingsAppearanceLayoutScreen(onNavigateToSubScreen = { activeSubScreen = it })
                                LeafTab.DETAILS        -> SettingsDetailsSectionsScreen()
                                LeafTab.EFFECTS        -> SettingsAppearanceEffectsScreen()
                                LeafTab.SHORTCUTS      -> SettingsShortcutsScreen()
                            }
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun SidebarLeafItem(
    title: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
            Text(
                text = title,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

