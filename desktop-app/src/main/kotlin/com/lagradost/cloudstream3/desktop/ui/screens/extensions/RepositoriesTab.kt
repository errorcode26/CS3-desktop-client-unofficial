package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RepositoriesTab(viewModel: ExtensionsViewModel) {
    var repoUrl by remember { mutableStateOf("") }
    val repos by DesktopRepositoryManager.savedRepositories.collectAsState()
    var statusText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = repoUrl,
                onValueChange = { repoUrl = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Short code or URL") },
                singleLine = true,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (repoUrl.isNotBlank()) {
                    coroutineScope.launch {
                        try {
                            val addedRepos = withContext(Dispatchers.IO) {
                                DesktopRepositoryManager.addRepositoryFromInput(repoUrl)
                            }
                            if (addedRepos != null && addedRepos.isNotEmpty()) {
                                repoUrl = ""
                                val repoNames = addedRepos.take(2).joinToString { it.name } + if (addedRepos.size > 2) " and ${addedRepos.size - 2} more" else ""
                                statusText = "Added ${addedRepos.size} repository(s): $repoNames. Syncing..."
                                viewModel.loadPluginsFromManager()
                                statusText = "Repositories added and synced successfully."
                            } else {
                                statusText = "Failed to load repository. Check the URL and try again."
                            }
                        } catch (e: Throwable) {
                            statusText = "Error: ${e.message}"
                        }
                    }
                }
            }) {
                Text("Add")
            }
        }

        if (statusText.isNotEmpty()) {
            Text(statusText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
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

        val gridScale by AppearanceConfig.gridScale.collectAsState()
        val repoMinSize = when (gridScale) {
            "Compact" -> 280.dp
            "Large" -> 380.dp
            else -> 320.dp
        }

        val allPlugins by viewModel.plugins.collectAsState()
        val installedPlugins by viewModel.installedPlugins.collectAsState()
        val inspectedRepoName by viewModel.inspectedRepoName.collectAsState()
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

        if (selectedRepoForDetail != null) {
            val repo = selectedRepoForDetail!!
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

            AlertDialog(
                onDismissRequest = {
                    selectedRepoForDetail = null
                    repoSearchQuery = ""
                    viewModel.inspectRepository("")
                },
                properties = DialogProperties(usePlatformDefaultWidth = false),
                modifier = Modifier.fillMaxWidth(0.92f).fillMaxHeight(0.90f),
                title = {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                                Text(repo.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    var copied by remember(repo.url) { mutableStateOf(false) }
                                    Text(
                                        repo.url,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false),
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Surface(
                                        onClick = {
                                            val installUrl = DesktopRepositoryManager.getPluginsJsonUrl(repo.url)
                                            val selection = java.awt.datatransfer.StringSelection(installUrl)
                                            java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                            copied = true
                                        },
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
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
                                }
                            }
                            Surface(
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Text(
                                    "${repoPlugins.size} Plugins",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                    maxLines = 1,
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = repoSearchQuery,
                            onValueChange = { repoSearchQuery = it },
                            placeholder = { Text("Search inside ${repo.name}...") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                    }
                },
                text = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (repoPlugins.isEmpty()) {
                            Text(
                                "No plugins found matching search.",
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            LazyVerticalGrid(
                                columns = GridCells.Adaptive(minSize = 380.dp),
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(repoPlugins, key = { it.internalName }) { plugin ->
                                    val iconUrl = plugin.iconUrl
                                        ?: DesktopRepositoryManager.remotePluginIcons.value[plugin.internalName]
                                        ?: DesktopRepositoryManager.remotePluginIcons.value[plugin.name]

                                    val isInstalled = remember(plugin, installedPlugins) {
                                        val ext = DesktopRepositoryManager.getExtensionsDir()
                                        val subDir = java.io.File(ext, repo.name.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
                                        java.io.File(subDir, "${plugin.internalName}.jar").exists() ||
                                            java.io.File(ext, "${plugin.internalName}.jar").exists() ||
                                            installedPlugins.any { it.internalName == plugin.internalName || it.name.equals(plugin.name, ignoreCase = true) }
                                    }
                                    var isInstalling by remember(plugin.internalName) { mutableStateOf(false) }
                                    var installStatus by remember(plugin.internalName, isInstalled) {
                                        mutableStateOf(if (isInstalled) "Installed" else "")
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
                                            isInstalling = true
                                            installStatus = "Installing..."
                                            viewModel.installPlugin(repo.name, plugin) { result ->
                                                isInstalling = false
                                                installStatus = result
                                            }
                                        },
                                        onUninstallClick = {
                                            viewModel.uninstallByInternalName(plugin.internalName)
                                            installStatus = ""
                                        },
                                        description = plugin.description,
                                        fileSize = plugin.fileSize,
                                    )
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        selectedRepoForDetail = null
                        repoSearchQuery = ""
                        viewModel.inspectRepository("")
                    }) {
                        Text("Close")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            DesktopRepositoryManager.removeRepository(repo.url)
                            selectedRepoForDetail = null
                            viewModel.inspectRepository("")
                        },
                    ) {
                        Text("Remove Repository", color = MaterialTheme.colorScheme.error)
                    }
                },
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
                            if (!repo.iconUrl.isNullOrEmpty() && !DesktopRepositoryManager.failedIconUrls.contains(repo.iconUrl)) {
                                coil3.compose.SubcomposeAsyncImage(
                                    model = repo.iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.padding(end = 14.dp).size(44.dp).clip(androidx.compose.foundation.shape.CircleShape),
                                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                    loading = {
                                        RepoAvatarBox(repo.name)
                                    },
                                    error = {
                                        DesktopRepositoryManager.failedIconUrls.add(repo.iconUrl)
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
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            var cardCopied by remember(repo.url) { mutableStateOf(false) }
                            TextButton(
                                onClick = {
                                    val installUrl = DesktopRepositoryManager.getPluginsJsonUrl(repo.url)
                                    val selection = java.awt.datatransfer.StringSelection(installUrl)
                                    java.awt.Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                                    cardCopied = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(13.dp),
                                    tint = if (cardCopied) androidx.compose.ui.graphics.Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    if (cardCopied) "Copied!" else "Copy URL",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (cardCopied) androidx.compose.ui.graphics.Color(0xFF81C784) else MaterialTheme.colorScheme.primary,
                                )
                            }
                            TextButton(
                                onClick = {
                                    DesktopRepositoryManager.removeRepository(repo.url)
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                            ) {
                                Text("Remove", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
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
