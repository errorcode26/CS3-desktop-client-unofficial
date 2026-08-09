package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.components.FlagImage
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun BrowseTab(viewModel: ExtensionsViewModel, syncGeneration: Int) {
    var searchQuery by remember { mutableStateOf("") }
    var languageFilter by remember { mutableStateOf("All") }
    var selectedCategories by remember { mutableStateOf(emptySet<String>()) }
    var repoFilter by remember { mutableStateOf("All") }

    val uiState by viewModel.uiState.collectAsState()
    val plugins = uiState.plugins
    val isFetching = uiState.isFetching
    val statusText = uiState.statusText
    val pluginRequiringBypass = uiState.pluginRequiringBypass
    val pluginRequiringPermission = uiState.pluginRequiringPermission

    val isLightMode by AppearanceConfig.isLightMode.collectAsState()

    val languages = remember(plugins) {
        listOf("All") + plugins.mapNotNull { it.second.language?.takeIf { l -> l.isNotBlank() } }.distinct().sorted()
    }
    val categories = remember(plugins) {
        plugins.flatMap { it.second.tvTypes ?: emptyList() }.distinct().sorted()
    }
    val reposList = remember(plugins) {
        listOf("All") + plugins.map { it.first }.distinct().sorted()
    }

    var showLangDropdown by remember { mutableStateOf(false) }
    var showRepoDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(syncGeneration) {
        if (syncGeneration > 0) {
            viewModel.onEvent(ExtensionsUiEvent.OnLoadPluginsFromManager)
        }
    }

    val filteredPlugins = remember(plugins, searchQuery, languageFilter, selectedCategories, repoFilter) {
        plugins.filter {
            val matchesSearch = it.second.name.contains(searchQuery, ignoreCase = true) ||
                it.second.internalName.contains(searchQuery, ignoreCase = true)
            val matchesLang = languageFilter == "All" || it.second.language == languageFilter
            val matchesCat = selectedCategories.isEmpty() || it.second.tvTypes?.any { t -> t in selectedCategories } == true
            val matchesRepo = repoFilter == "All" || it.first == repoFilter
            matchesSearch && matchesLang && matchesCat && matchesRepo
        }
    }

    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val extMinSize = (posterWidthDp * 1.65f).dp

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Modern Glassmorphic Search Toolbar ──────────────────────
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // High-End Custom Search Bar
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isLightMode) Color(0xFFF0F2F6) else Color.White.copy(alpha = 0.05f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isLightMode) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.10f),
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )

                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search ${plugins.size} plugins by name, language, or provider...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        ) {
                            Text(
                                text = "${filteredPlugins.size} matches",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .clickable { searchQuery = "" },
                        )
                    }
                }
            }

            // Language Filter Pill
            Box {
                FilledTonalButton(
                    onClick = { showLangDropdown = true },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    if (languageFilter == "All") {
                        Text("Lang: All", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagImage(languageFilter, modifier = Modifier.padding(end = 4.dp).size(14.dp))
                            Text(languageFilter.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                AppDropdownMenu(expanded = showLangDropdown, onDismissRequest = { showLangDropdown = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                if (lang == "All") {
                                    Text("All Languages", fontSize = 12.sp)
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FlagImage(lang, modifier = Modifier.padding(end = 6.dp).size(14.dp))
                                        Text(lang.uppercase(), fontSize = 12.sp)
                                    }
                                }
                            },
                            onClick = {
                                languageFilter = lang
                                showLangDropdown = false
                            },
                        )
                    }
                }
            }

            // Repository Filter Pill
            Box {
                FilledTonalButton(
                    onClick = { showRepoDropdown = true },
                    modifier = Modifier.height(44.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                ) {
                    Text(
                        if (repoFilter == "All") "Repo: All" else repoFilter,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                AppDropdownMenu(expanded = showRepoDropdown, onDismissRequest = { showRepoDropdown = false }) {
                    reposList.forEach { r ->
                        DropdownMenuItem(
                            text = {
                                Text(if (r == "All") "All Repositories" else r, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            onClick = {
                                repoFilter = r
                                showRepoDropdown = false
                            },
                        )
                    }
                }
            }

            // Fetch Repos Action
            FilledTonalButton(
                onClick = { viewModel.onEvent(ExtensionsUiEvent.OnFetchPlugins) },
                enabled = !isFetching,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Fetch", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ── Sleek Category Filter Chips Row ─────────────────────────
        if (categories.isNotEmpty()) {
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                item {
                    val isAllSelected = selectedCategories.isEmpty()
                    Surface(
                        onClick = { selectedCategories = emptySet() },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isAllSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isAllSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            text = "All",
                            color = if (isAllSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isAllSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }

                items(categories) { category ->
                    val isSelected = category in selectedCategories
                    Surface(
                        onClick = {
                            selectedCategories = if (isSelected) selectedCategories - category else selectedCategories + category
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isSelected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        ),
                    ) {
                        Text(
                            text = category,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        // ── Extension Cards Grid ────────────────────────────────────
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = extMinSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filteredPlugins, key = { "${it.first}-${it.second.internalName}" }) { (repoName, plugin) ->
                val iconUrl = plugin.iconUrl
                    ?: uiState.remotePluginIcons[plugin.internalName]
                    ?: uiState.remotePluginIcons[plugin.name]

                var isInstalling by remember { mutableStateOf(false) }
                val isPluginInstalled = remember(plugin, syncGeneration) {
                    val ext = uiState.extensionsDir
                    val subDir = java.io.File(ext, repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
                    java.io.File(subDir, "${plugin.internalName}.jar").exists()
                }
                var installStatus by remember(plugin, syncGeneration) {
                    mutableStateOf(if (isPluginInstalled) "Installed" else "")
                }

                ExtensionCard(
                    name = plugin.name,
                    internalName = plugin.internalName,
                    version = plugin.version,
                    repoName = repoName,
                    language = plugin.language,
                    tvTypes = plugin.tvTypes,
                    iconUrl = iconUrl,
                    isInstalled = isPluginInstalled,
                    installStatus = installStatus,
                    isInstalling = isInstalling,
                    onInstallClick = {
                        if (!isInstalling && !isPluginInstalled) {
                            isInstalling = true
                            installStatus = "Installing..."
                            viewModel.onEvent(
                                ExtensionsUiEvent.OnInstallPlugin(repoName, plugin) { err ->
                                    isInstalling = false
                                    installStatus = if (err.isEmpty()) "Installed" else "Failed: $err"
                                },
                            )
                        }
                    },
                    description = plugin.description,
                    fileSize = plugin.fileSize,
                )
            }
        }

        // ── Security & Permission Dialogs ───────────────────────────
        pluginRequiringBypass?.let { (bypassRepo, bypassPlugin) ->
            var isDialogInstalling by remember { mutableStateOf(false) }
            CloudstreamAlertDialog(
                show = true,
                onDismissRequest = { if (!isDialogInstalling) viewModel.onEvent(ExtensionsUiEvent.OnClearBypass) },
                title = { Text("Unverified Repository") },
                text = {
                    if (isDialogInstalling) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(16.dp))
                            Text("Installing, please wait...")
                        }
                    } else {
                        Text("The repository '$bypassRepo' is not in the verified repository list.\n\nInstalling third-party extensions can pose security risks. Do you want to proceed?")
                    }
                },
                confirmButton = {
                    if (!isDialogInstalling) {
                        TextButton(
                            onClick = {
                                isDialogInstalling = true
                                viewModel.onEvent(
                                    ExtensionsUiEvent.OnBypassSecurityAndInstall(bypassRepo, bypassPlugin) {
                                        isDialogInstalling = false
                                    },
                                )
                            },
                        ) {
                            Text("Trust & Install", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                dismissButton = {
                    if (!isDialogInstalling) {
                        TextButton(onClick = { viewModel.onEvent(ExtensionsUiEvent.OnClearBypass) }) {
                            Text("Cancel")
                        }
                    }
                },
            )
        }

        pluginRequiringPermission?.let { (reqRepo, reqPlugin, reqPermission) ->
            var isDialogInstalling by remember { mutableStateOf(false) }
            CloudstreamAlertDialog(
                show = true,
                onDismissRequest = { if (!isDialogInstalling) viewModel.onEvent(ExtensionsUiEvent.OnClearPermissionRequest) },
                title = { Text("Permission Required") },
                text = {
                    if (isDialogInstalling) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(16.dp))
                            Text("Installing, please wait...")
                        }
                    } else {
                        Text("The plugin '${reqPlugin.name}' requires the following permission to function:\n\n• $reqPermission\n\nDo you want to grant this permission and install the plugin?")
                    }
                },
                confirmButton = {
                    if (!isDialogInstalling) {
                        TextButton(
                            onClick = {
                                isDialogInstalling = true
                                viewModel.onEvent(
                                    ExtensionsUiEvent.OnGrantPermissionAndInstall(reqRepo, reqPlugin, reqPermission) {
                                        isDialogInstalling = false
                                    },
                                )
                            },
                        ) {
                            Text("Grant & Install", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                dismissButton = {
                    if (!isDialogInstalling) {
                        TextButton(onClick = { viewModel.onEvent(ExtensionsUiEvent.OnClearPermissionRequest) }) {
                            Text("Cancel")
                        }
                    }
                },
            )
        }
    }
}
