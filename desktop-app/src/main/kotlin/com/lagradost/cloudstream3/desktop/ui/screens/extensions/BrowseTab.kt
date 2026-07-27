package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var categoryFilter by remember { mutableStateOf("All") }
    var repoFilter by remember { mutableStateOf("All") }

    val uiState by viewModel.uiState.collectAsState()
    val plugins = uiState.plugins
    val isFetching = uiState.isFetching
    val statusText = uiState.statusText
    val pluginRequiringBypass = uiState.pluginRequiringBypass
    val pluginRequiringPermission = uiState.pluginRequiringPermission

    val languages = remember(plugins) {
        listOf("All") + plugins.mapNotNull { it.second.language?.takeIf { l -> l.isNotBlank() } }.distinct().sorted()
    }
    val categories = remember(plugins) {
        listOf("All") + plugins.flatMap { it.second.tvTypes ?: emptyList() }.distinct().sorted()
    }
    val reposList = remember(plugins) {
        listOf("All") + plugins.map { it.first }.distinct().sorted()
    }

    var showLangDropdown by remember { mutableStateOf(false) }
    var showCatDropdown by remember { mutableStateOf(false) }
    var showRepoDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(syncGeneration) {
        if (syncGeneration > 0) {
            viewModel.onEvent(ExtensionsUiEvent.OnLoadPluginsFromManager)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Row 1: Search Bar & Fetch Actions
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search plugins...") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                ),
            )

            Spacer(modifier = Modifier.width(16.dp))

            Button(
                onClick = { viewModel.onEvent(ExtensionsUiEvent.OnFetchPlugins) },
                enabled = !isFetching,
                modifier = Modifier.height(52.dp),
            ) {
                if (isFetching) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Fetch Repos")
                }
            }
        }

        // Row 2: Clean Filter Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box {
                FilledTonalButton(
                    onClick = { showLangDropdown = true },
                    modifier = Modifier.height(44.dp).widthIn(max = 180.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    if (languageFilter == "All") {
                        Text("Language: All", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagImage(languageFilter, modifier = Modifier.padding(end = 6.dp))
                            Text(languageFilter.uppercase(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                AppDropdownMenu(expanded = showLangDropdown, onDismissRequest = { showLangDropdown = false }) {
                    languages.forEach { lang ->
                        DropdownMenuItem(
                            text = {
                                if (lang == "All") {
                                    Text("All Languages")
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FlagImage(lang, modifier = Modifier.padding(end = 8.dp))
                                        Text(lang.uppercase())
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

            Box {
                FilledTonalButton(
                    onClick = { showCatDropdown = true },
                    modifier = Modifier.height(44.dp).widthIn(max = 180.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text(
                        if (categoryFilter == "All") "Category: All" else categoryFilter,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                AppDropdownMenu(expanded = showCatDropdown, onDismissRequest = { showCatDropdown = false }) {
                    categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(if (cat == "All") "All Categories" else cat) },
                            onClick = {
                                categoryFilter = cat
                                showCatDropdown = false
                            },
                        )
                    }
                }
            }

            Box {
                FilledTonalButton(
                    onClick = { showRepoDropdown = true },
                    modifier = Modifier.height(44.dp).widthIn(max = 200.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                ) {
                    Text(
                        if (repoFilter == "All") "Repo: All" else repoFilter,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                AppDropdownMenu(expanded = showRepoDropdown, onDismissRequest = { showRepoDropdown = false }) {
                    reposList.forEach { r ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (r != "All") {
                                        val colorHash = kotlin.math.abs(r.hashCode())
                                        val hue = (colorHash % 360).toFloat()
                                        val avatarColor = androidx.compose.ui.graphics.Color.hsv(hue, 0.6f, 0.8f)
                                        val initial = r.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 8.dp)
                                                .size(24.dp)
                                                .clip(androidx.compose.foundation.shape.CircleShape)
                                                .background(avatarColor),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = initial,
                                                color = androidx.compose.ui.graphics.Color.White,
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold,
                                            )
                                        }
                                    }
                                    Text(if (r == "All") "All Repos" else r, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            },
                            onClick = {
                                repoFilter = r
                                showRepoDropdown = false
                            },
                        )
                    }
                }
            }
        }

        Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))

        val filteredPlugins = plugins.filter {
            val matchesSearch = it.second.name.contains(searchQuery, ignoreCase = true) ||
                it.second.internalName.contains(searchQuery, ignoreCase = true)
            val matchesLang = languageFilter == "All" || it.second.language == languageFilter
            val matchesCat = categoryFilter == "All" || (it.second.tvTypes?.contains(categoryFilter) == true)
            val matchesRepo = repoFilter == "All" || it.first == repoFilter
            matchesSearch && matchesLang && matchesCat && matchesRepo
        }

        val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
        val extMinSize = (posterWidthDp * 1.5f).dp

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
                        isInstalling = true
                        installStatus = "Installing..."
                        viewModel.onEvent(
                            ExtensionsUiEvent.OnInstallPlugin(repoName, plugin) { result ->
                                isInstalling = false
                                installStatus = result
                            },
                        )
                    },
                    onUninstallClick = {
                        viewModel.onEvent(ExtensionsUiEvent.OnUninstallByInternalName(plugin.internalName))
                        installStatus = ""
                    },
                    description = plugin.description,
                    fileSize = plugin.fileSize,
                    onRepoClick = { viewModel.onEvent(ExtensionsUiEvent.OnInspectRepository(repoName)) },
                )
            }
        }

        pluginRequiringBypass?.let { (bypassRepo, bypassPlugin) ->
            var isDialogInstalling by remember { mutableStateOf(false) }
            CloudstreamAlertDialog(
                show = true,
                onDismissRequest = { if (!isDialogInstalling) viewModel.onEvent(ExtensionsUiEvent.OnClearBypass) },
                title = { Text("Advanced Bytecode Detected") },
                text = {
                    if (isDialogInstalling) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(16.dp))
                            Text("Installing, please wait (this may take a moment)...")
                        }
                    } else {
                        Text("Advanced or unverified bytecode patterns were detected in ${bypassPlugin.name}.\n\nThis plugin uses reflection or APIs outside standard verified CloudStream templates. While this is common in complex or third-party plugins, our desktop runtime will continue to run it inside the secure sandbox.\n\nWould you like to trust and install this plugin?")
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
                            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
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
