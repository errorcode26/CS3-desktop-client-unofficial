package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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

sealed class SettingsNav {
    data class Leaf(val id: LeafTab) : SettingsNav()
    data class Group(val id: GroupTab, val children: List<LeafTab>) : SettingsNav()
}

enum class LeafTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null) {
    THEME("Themes & Colors"),
    LAYOUT("Layout & Dock"),
    DETAILS("Details Page"),
    EFFECTS("Backdrop & Effects"),
    ACCOUNTS("Profiles & Sync", Icons.Default.AccountCircle),
    PLAYER("Video & Audio Engine"),
    SUBTITLES_LEAF("Subtitles & Styling"),
    EXTENSIONS("Plugins & Repos"),
    ADDONS("External Addons"),
    INTEGRATIONS("Metadata & Scrapers"),
    NETWORK("Network & DNS", Icons.Default.Router),
    ADVANCED("Storage & Cache"),
    DEVELOPER("Diagnostics & Logs"),
    ABOUT("Updates & About"),
}

enum class GroupTab(val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    APPEARANCE("Appearance & UI", Icons.Default.Palette),
    PLAYBACK("Playback & Media", Icons.Default.PlayCircle),
    EXTENSIONS_GROUP("Plugins & Scrapers", Icons.Default.Extension),
    SYSTEM_GROUP("System & Tools", Icons.Default.Build),
}

// Keeps SettingsSearchIndex compiling without changes
typealias SettingsTab = LeafTab

enum class SettingsSubScreen(val title: String) {
    DETAILS_LAYOUT("Details Page Layout"),
    POSTER_EDITOR("Poster Editor"),
    SUBTITLES("Subtitle Styling"),
}

object SettingsSession {
    var selectedLeaf by mutableStateOf(LeafTab.THEME)
    var expandedGroups by mutableStateOf<Set<GroupTab>>(emptySet())
    var activeSubScreen by mutableStateOf<SettingsSubScreen?>(null)
    var highlightedSetting by mutableStateOf<String?>(null)
    val settingsViewModel by lazy { SettingsViewModel() }
}

private val NAV_STRUCTURE: List<SettingsNav> = listOf(
    SettingsNav.Group(GroupTab.APPEARANCE, listOf(LeafTab.THEME, LeafTab.LAYOUT, LeafTab.DETAILS, LeafTab.EFFECTS)),
    SettingsNav.Leaf(LeafTab.ACCOUNTS),
    SettingsNav.Group(GroupTab.PLAYBACK, listOf(LeafTab.PLAYER, LeafTab.SUBTITLES_LEAF)),
    SettingsNav.Group(GroupTab.EXTENSIONS_GROUP, listOf(LeafTab.EXTENSIONS, LeafTab.ADDONS, LeafTab.INTEGRATIONS)),
    SettingsNav.Leaf(LeafTab.NETWORK),
    SettingsNav.Group(GroupTab.SYSTEM_GROUP, listOf(LeafTab.ADVANCED, LeafTab.DEVELOPER, LeafTab.ABOUT)),
)



@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
@Composable
fun ComposeSettingsScreen(
    onNavigate: (Config) -> Unit,
    viewModel: com.lagradost.cloudstream3.desktop.ui.screens.settings.PluginSettingsViewModel? = null,
) {
    var selectedLeaf by SettingsSession::selectedLeaf
    var expandedGroups by SettingsSession::expandedGroups
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
                            val parentGroup = NAV_STRUCTURE.filterIsInstance<SettingsNav.Group>().find { it.children.contains(result.tab) }
                            val breadcrumb = when {
                                result.subScreen != null -> "${result.tab.title} > ${result.subScreen.title}"
                                parentGroup != null -> "${parentGroup.id.title} > ${result.tab.title}"
                                else -> result.tab.title
                            }

                            Surface(
                                onClick = {
                                    selectedLeaf = result.tab
                                    activeSubScreen = result.subScreen
                                    if (parentGroup != null && parentGroup.id !in expandedGroups) {
                                        expandedGroups = expandedGroups + parentGroup.id
                                    }
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
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    NAV_STRUCTURE.forEach { nav ->
                        when (nav) {
                            is SettingsNav.Leaf -> {
                                if (nav.id == LeafTab.NETWORK) {
                                    HorizontalDivider(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                                }
                                SidebarLeafItem(
                                    title = nav.id.title,
                                    icon = nav.id.icon,
                                    isSelected = selectedLeaf == nav.id && activeSubScreen == null,
                                    isChild = false,
                                    onClick = { selectedLeaf = nav.id; activeSubScreen = null },
                                )
                            }
                            is SettingsNav.Group -> {
                                val isExpanded = nav.id in expandedGroups
                                val isGroupActive = nav.children.any { it == selectedLeaf } && activeSubScreen == null
                                SidebarGroupItem(
                                    title = nav.id.title,
                                    icon = nav.id.icon,
                                    isExpanded = isExpanded,
                                    isActive = isGroupActive,
                                    onClick = {
                                        expandedGroups = if (isExpanded) expandedGroups - nav.id
                                                         else expandedGroups + nav.id
                                        if (!isExpanded) {
                                            selectedLeaf = nav.children.first()
                                            activeSubScreen = null
                                        }
                                    },
                                )
                                if (isExpanded) {
                                    nav.children.forEach { child ->
                                        SidebarLeafItem(
                                            title = child.title,
                                            icon = null,
                                            isSelected = selectedLeaf == child && activeSubScreen == null,
                                            isChild = true,
                                            onClick = { selectedLeaf = child; activeSubScreen = null },
                                        )
                                    }
                                }
                            }
                        }
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
                                modifier = Modifier.fillMaxWidth().clickable { activeSubScreen = null }.padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back",
                                    tint = MaterialTheme.colorScheme.onSurface)
                                Text(currentSubScreen.title, style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                            }
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (currentSubScreen) {
                                    SettingsSubScreen.DETAILS_LAYOUT -> SettingsDetailsSectionsScreen()
                                    SettingsSubScreen.POSTER_EDITOR -> SettingsPosterEditorScreen()
                                    SettingsSubScreen.SUBTITLES -> SettingsSubtitleEditorScreen(viewModel = settingsViewModel)
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
                                LeafTab.THEME          -> SettingsAppearanceThemeScreen()
                                LeafTab.LAYOUT         -> SettingsAppearanceLayoutScreen(onNavigateToSubScreen = { activeSubScreen = it })
                                LeafTab.DETAILS        -> SettingsDetailsSectionsScreen()
                                LeafTab.EFFECTS        -> SettingsAppearanceEffectsScreen()
                                LeafTab.ACCOUNTS       -> SettingsAccounts(viewModel = settingsViewModel)
                                LeafTab.PLAYER         -> SettingsPlayer(viewModel = settingsViewModel, onNavigateToSubScreen = { activeSubScreen = it })
                                LeafTab.SUBTITLES_LEAF -> SettingsSubtitleEditorScreen(viewModel = settingsViewModel)
                                LeafTab.EXTENSIONS     -> SettingsExtensions(onNavigate = onNavigate)
                                LeafTab.ADDONS         -> SettingsAddons()
                                LeafTab.INTEGRATIONS   -> SettingsIntegrations()
                                LeafTab.NETWORK        -> SettingsNetworkScreen(viewModel = settingsViewModel)
                                LeafTab.ADVANCED       -> SettingsAdvancedScreen(viewModel = settingsViewModel)
                                LeafTab.DEVELOPER      -> SettingsDeveloper()
                                LeafTab.ABOUT          -> SettingsAboutAndUpdates()
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
private fun SidebarGroupItem(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    isActive: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f) else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp),
                tint = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f))
            Spacer(Modifier.width(12.dp))
            Text(text = title, modifier = Modifier.weight(1f),
                color = if (isActive) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                style = MaterialTheme.typography.bodyMedium)
            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun SidebarLeafItem(
    title: String,
    icon: ImageVector?,
    isSelected: Boolean,
    isChild: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (isChild) 32.dp else 12.dp,
                end = 12.dp,
                top = if (isChild) 7.dp else 10.dp,
                bottom = if (isChild) 7.dp else 10.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (icon != null) {
                Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(18.dp),
                    tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f))
            } else if (isChild) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                    modifier = Modifier.size(4.dp),
                ) {}
            }
            Text(
                text = title,
                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                style = if (isChild) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

