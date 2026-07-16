package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.plugins.vega.VegaRepository
import com.lagradost.cloudstream3.desktop.plugins.vega.VegaRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun VegaRepositoriesTab(viewModel: ExtensionsViewModel, syncGeneration: Int) {
    var repoUrl by remember { mutableStateOf("") }
    val repos by VegaRepositoryManager.savedRepositories.collectAsState()
    var statusText by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    var isAdding by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = repoUrl,
                onValueChange = { repoUrl = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Vega Shortcode (e.g. vega-org) or JSON URL") },
                singleLine = true,
                enabled = !isAdding
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (repoUrl.isNotBlank()) {
                        coroutineScope.launch {
                            isAdding = true
                            statusText = "Downloading Vega repository..."
                            try {
                                val success = VegaRepositoryManager.addRepository(repoUrl)
                                if (success) {
                                    repoUrl = ""
                                    statusText = "Vega repository added and loaded successfully!"
                                } else {
                                    statusText = "Failed to load Vega repository. Verify the URL or shortcode."
                                }
                            } catch (e: Exception) {
                                statusText = "Error: ${e.message}"
                            } finally {
                                isAdding = false
                            }
                        }
                    }
                },
                enabled = !isAdding && repoUrl.isNotBlank()
            ) {
                if (isAdding) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        }

        if (statusText.isNotEmpty()) {
            Text(statusText, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            Spacer(modifier = Modifier.height(16.dp))
        }

        Text("Saved Vega Repositories (${repos.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))

        if (repos.isEmpty()) {
            Text(
                "No Vega repositories saved.",
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

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = repoMinSize),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(repos, key = { it.url }) { repo ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(repo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            repo.url,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        VegaRepositoryManager.removeRepository(repo.url)
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                // Default repo vega-org can be deleted by the user
                                Text("Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}
