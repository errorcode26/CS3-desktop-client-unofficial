package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
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
                                viewModel.fetchPlugins()
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
            items(repos, key = { it.url }) { repo ->
                Card(
                    modifier = Modifier.fillMaxWidth().height(90.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (!repo.iconUrl.isNullOrEmpty()) {
                                AsyncImage(
                                    model = repo.iconUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp).padding(end = 12.dp),
                                )
                            } else {
                                Box(
                                    modifier = Modifier.size(48.dp).padding(end = 12.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Folder,
                                        contentDescription = "Repository",
                                        modifier = Modifier.size(32.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Column {
                                Text(repo.name, fontWeight = FontWeight.Bold, maxLines = 1, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    repo.url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                )
                            }
                        }
                        TextButton(onClick = {
                            DesktopRepositoryManager.removeRepository(repo.url)
                        }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
