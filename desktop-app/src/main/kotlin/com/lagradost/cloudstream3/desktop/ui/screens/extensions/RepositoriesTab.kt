package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig

@Composable
fun RepositoriesTab(viewModel: ExtensionsViewModel) {
    var repoUrl by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()
    val repos = uiState.savedRepositories

    val allPlugins = uiState.plugins
    val installedPlugins = uiState.installedPlugins
    val inspectedRepoName = uiState.inspectedRepoName
    var selectedRepoForDetail by remember { mutableStateOf<com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData?>(null) }
    var repoSearchQuery by remember { mutableStateOf("") }

    LaunchedEffect(inspectedRepoName, repos) {
        if (!inspectedRepoName.isNullOrBlank()) {
            val match = repos.find { it.name.equals(inspectedRepoName, ignoreCase = true) }
            if (match != null) {
                selectedRepoForDetail = match
            }
        }
    }

    val repo = selectedRepoForDetail
    if (repo != null) {
        val repoPlugins = remember(allPlugins, repo.name, repoSearchQuery) {
            allPlugins
                .filter { it.first == repo.name }
                .map { it.second }
                .filter {
                    if (repoSearchQuery.isBlank()) {
                        true
                    } else {
                        it.name.contains(repoSearchQuery, ignoreCase = true) ||
                            it.description?.contains(repoSearchQuery, ignoreCase = true) == true
                    }
                }
        }

        // Inline Repository Inspection Sub-view (Master-Detail, no modal)
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    FilledTonalButton(
                        onClick = {
                            selectedRepoForDetail = null
                            repoSearchQuery = ""
                            viewModel.onEvent(ExtensionsUiEvent.OnInspectRepository(""))
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("All Repositories")
                    }

                    Text(
                        repo.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                    )

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            "${repoPlugins.size} Plugins",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    var copied by remember(repo.url) { mutableStateOf(false) }
                    Surface(
                        onClick = {
                            val installUrl = com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.getPluginsJsonUrl(repo.url)
                            val selection = java.awt.datatransfer.StringSelection(installUrl)
                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                            copied = true
                        },
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = if (copied) androidx.compose.ui.graphics.Color(0xFF81C784) else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (copied) "Copied JSON URL!" else "Copy JSON URL",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (copied) androidx.compose.ui.graphics.Color(0xFF81C784) else MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            viewModel.onEvent(ExtensionsUiEvent.OnRemoveRepository(repo.url))
                            selectedRepoForDetail = null
                            viewModel.onEvent(ExtensionsUiEvent.OnInspectRepository(""))
                        },
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Remove")
                    }
                }
            }

            // Search inside repo
            OutlinedTextField(
                value = repoSearchQuery,
                onValueChange = { repoSearchQuery = it },
                placeholder = { Text("Search plugins inside ${repo.name}...") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                singleLine = true,
                shape = RoundedCornerShape(8.dp),
            )

            // Content
            Box(modifier = Modifier.fillMaxSize()) {
                if (repoPlugins.isEmpty()) {
                    Text(
                        "No plugins found matching search.",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val cleanRepo = remember(repo.name) { repo.name.replace(Regex("[^a-zA-Z0-9.-]"), "_") }
                    val installedPluginKeys = remember(installedPlugins) {
                        val set = mutableSetOf<String>()
                        installedPlugins.forEach { installed ->
                            val parent = installed.file.parentFile?.name ?: ""
                            set.add("$parent:${installed.internalName}")
                            set.add(installed.internalName)
                        }
                        set
                    }

                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 380.dp),
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(repoPlugins, key = { it.internalName }) { plugin ->
                            val iconUrl = plugin.iconUrl
                                ?: uiState.remotePluginIcons[plugin.internalName]
                                ?: uiState.remotePluginIcons[plugin.name]

                            val isInstalled = remember(plugin.internalName, cleanRepo, installedPluginKeys) {
                                installedPluginKeys.contains("$cleanRepo:${plugin.internalName}") ||
                                    installedPluginKeys.contains("${repo.name}:${plugin.internalName}") ||
                                    installedPluginKeys.contains(plugin.internalName)
                            }
                            val isInstalling = uiState.installingPlugins.contains(plugin.internalName)
                            val installStatus = when {
                                isInstalled -> "Installed"
                                isInstalling -> "Installing..."
                                else -> ""
                            }

                            com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard(
                                name = plugin.name,
                                internalName = plugin.internalName,
                                version = plugin.version,
                                repoName = repo.name,
                                language = plugin.language,
                                tvTypes = plugin.tvTypes,
                                iconUrl = iconUrl,
                                isInstalled = isInstalled,
                                installStatus = installStatus,
                                isInstalling = isInstalling,
                                onInstallClick = {
                                    if (!isInstalling && !isInstalled) {
                                        viewModel.onEvent(ExtensionsUiEvent.OnInstallPlugin(repo.name, plugin))
                                    }
                                },
                                onUninstallClick = {
                                    viewModel.onEvent(ExtensionsUiEvent.OnUninstallPlugin(repo.name, plugin.internalName))
                                },
                                description = plugin.description,
                                fileSize = plugin.fileSize,
                            )
                        }
                    }
                }
            }
        }
    } else {
        val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
        val repoMinSize = (posterWidthDp * 1.8f).dp

        Column(modifier = Modifier.fillMaxSize()) {
            // Compact inline add-repo bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = repoUrl,
                    onValueChange = { repoUrl = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Repository URL or short code...") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
                Button(
                    onClick = {
                        if (repoUrl.isNotBlank()) {
                            viewModel.onEvent(ExtensionsUiEvent.OnAddRepositoryFromInput(repoUrl))
                            repoUrl = ""
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add")
                }
            }

            AnimatedVisibility(visible = uiState.statusText.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                ) {
                    Text(
                        uiState.statusText,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                    )
                }
            }

            Text("Saved Repositories (${repos.size})", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            if (repos.isEmpty()) {
                Text(
                    "No repositories yet. Add a valid repo.json URL to browse plugins.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = repoMinSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(repos, key = { it.url }) { repo ->
                val pluginCount = allPlugins.count { it.first == repo.name }

                Card(
                    onClick = { selectedRepoForDetail = repo },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            val manifest = remember(repo.url) { com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.getRepositoryManifest(repo.url) }
                            val iconUrl = manifest?.iconUrl
                            if (!iconUrl.isNullOrEmpty() && !com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.isIconFailed(iconUrl)) {
                                coil3.compose.SubcomposeAsyncImage(
                                    model = iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 14.dp).size(44.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    loading = {
                                        RepoAvatarBox(repo.name)
                                    },
                                    error = {
                                        com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.markIconFailed(iconUrl)
                                        RepoAvatarBox(repo.name)
                                    },
                                )
                            } else {
                                RepoAvatarBox(repo.name)
                            }
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(repo.name, fontWeight = FontWeight.ExtraBold, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    repo.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "$pluginCount plugins available • Click to inspect",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            var cardCopied by remember(repo.url) { mutableStateOf(false) }
                            IconButton(
                                onClick = {
                                    val installUrl = com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.getPluginsJsonUrl(repo.url)
                                    val selection = java.awt.datatransfer.StringSelection(installUrl)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    cardCopied = true
                                },
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = "Copy URL",
                                    modifier = Modifier.size(18.dp),
                                    tint = if (cardCopied) androidx.compose.ui.graphics.Color(0xFF81C784) else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = { viewModel.onEvent(ExtensionsUiEvent.OnRemoveRepository(repo.url)) },
                            ) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Remove",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                )
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
fun RepoAvatarBox(name: String) {
    val colorHash = kotlin.math.abs(name.hashCode())
    val hue = (colorHash % 360).toFloat()
    val avatarColor = androidx.compose.ui.graphics.Color.hsv(hue, 0.65f, 0.75f)
    val initial = name.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

    Box(
        modifier = Modifier
            .padding(end = 14.dp)
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = androidx.compose.ui.graphics.Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}
