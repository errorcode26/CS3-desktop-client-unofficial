package com.lagradost.cloudstream3.desktop.ui.screens.extensions

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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.AppDropdownMenu
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.components.FlagImage
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun BrowseTab(viewModel: ExtensionsViewModel, syncGeneration: Int) {
    var searchQuery by remember { mutableStateOf("") }
    var languageFilter by remember { mutableStateOf("All") }
    var categoryFilter by remember { mutableStateOf("All") }
    var repoFilter by remember { mutableStateOf("All") }

    val plugins by viewModel.plugins.collectAsState()
    val isFetching by viewModel.isFetching.collectAsState()
    val statusText by viewModel.statusText.collectAsState()
    val pluginRequiringBypass by viewModel.pluginRequiringBypass.collectAsState()
    val pluginRequiringPermission by viewModel.pluginRequiringPermission.collectAsState()

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
            viewModel.loadPluginsFromManager()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
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

            Spacer(modifier = Modifier.width(12.dp))

            Box {
                FilledTonalButton(
                    onClick = { showLangDropdown = true },
                    modifier = Modifier.height(56.dp),
                ) {
                    if (languageFilter == "All") {
                        Text("All Languages")
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FlagImage(languageFilter, modifier = Modifier.padding(end = 6.dp))
                            Text(languageFilter.uppercase())
                        }
                    }
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

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                FilledTonalButton(
                    onClick = { showCatDropdown = true },
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(if (categoryFilter == "All") "All Categories" else categoryFilter)
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

            Spacer(modifier = Modifier.width(8.dp))

            Box {
                FilledTonalButton(
                    onClick = { showRepoDropdown = true },
                    modifier = Modifier.height(56.dp),
                ) {
                    Text(if (repoFilter == "All") "All Repos" else repoFilter)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                AppDropdownMenu(expanded = showRepoDropdown, onDismissRequest = { showRepoDropdown = false }) {
                    reposList.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(if (r == "All") "All Repos" else r) },
                            onClick = {
                                repoFilter = r
                                showRepoDropdown = false
                            },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
            Button(
                onClick = { viewModel.fetchPlugins() },
                enabled = !isFetching,
                modifier = Modifier.height(56.dp),
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

        val gridScale by AppearanceConfig.gridScale.collectAsState()
        val extMinSize = when (gridScale) {
            "Compact" -> 280.dp
            "Large" -> 400.dp
            else -> 340.dp
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = extMinSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(filteredPlugins, key = { "${it.first}-${it.second.internalName}" }) { (repoName, plugin) ->
                val iconUrl = plugin.iconUrl
                    ?: DesktopRepositoryManager.remotePluginIcons.value[plugin.internalName]
                    ?: DesktopRepositoryManager.remotePluginIcons.value[plugin.name]

                var isInstalling by remember { mutableStateOf(false) }
                val isPluginInstalled = remember(plugin, syncGeneration) {
                    val ext = DesktopRepositoryManager.getExtensionsDir()
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
                        viewModel.installPlugin(repoName, plugin) { result ->
                            isInstalling = false
                            installStatus = result
                        }
                    },
                )
            }
        }

        pluginRequiringBypass?.let { (bypassRepo, bypassPlugin) ->
            AlertDialog(
                onDismissRequest = { viewModel.clearBypass() },
                title = { Text("Security Sandbox Warning") },
                text = { Text("Potentially unsafe code was detected in ${bypassPlugin.name}.\n\nThis usually means the plugin uses dangerous operations (e.g., executing system commands or untracked network requests) that could compromise the app's stability or your privacy. Installing this is NOT recommended.\n\nAre you sure you want to bypass the sandbox and install it anyway?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.bypassSecurityAndInstall(bypassRepo, bypassPlugin)
                        },
                    ) {
                        Text("Install Anyway", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearBypass() }) {
                        Text("Cancel")
                    }
                },
            )
        }

        pluginRequiringPermission?.let { (reqRepo, reqPlugin, reqPermission) ->
            AlertDialog(
                onDismissRequest = { viewModel.clearPermissionRequest() },
                title = { Text("Permission Required") },
                text = { Text("The plugin '${reqPlugin.name}' requires the following permission to function:\n\n• $reqPermission\n\nDo you want to grant this permission and install the plugin?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.grantPermissionAndInstall(reqRepo, reqPlugin, reqPermission)
                        },
                    ) {
                        Text("Grant & Install", color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.clearPermissionRequest() }) {
                        Text("Cancel")
                    }
                },
            )
        }
    }
}
