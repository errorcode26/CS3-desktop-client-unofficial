package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.plugins.vega.VegaBridgeManager
import com.lagradost.cloudstream3.desktop.plugins.vega.VegaAvailableProvider
import com.lagradost.cloudstream3.desktop.plugins.vega.VegaExtensionManager
import com.lagradost.cloudstream3.desktop.ui.components.ExtensionCard
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import kotlinx.coroutines.launch

@Composable
fun VegaTab(viewModel: ExtensionsViewModel, syncGeneration: Int) {
    var installedProviders by remember { mutableStateOf(emptySet<String>()) }
    var availableProviders by remember { mutableStateOf(emptyList<VegaAvailableProvider>()) }
    var installingProviders by remember { mutableStateOf(emptySet<String>()) }
    
    val coroutineScope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    fun refreshList() {
        coroutineScope.launch {
            isRefreshing = true
            try {
                installedProviders = VegaBridgeManager.getInstalledProviders().toSet()
                availableProviders = VegaBridgeManager.getAvailableProviders()
            } catch (e: Exception) {
                println("Failed to refresh Vega providers list: ${e.message}")
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(syncGeneration) {
        refreshList()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Available Vega Providers (${availableProviders.size})", style = MaterialTheme.typography.titleMedium)
            
            Button(
                onClick = { refreshList() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                enabled = !isRefreshing
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Refresh")
                }
            }
        }

        val gridScale by AppearanceConfig.gridScale.collectAsState()
        val extMinSize = when (gridScale) {
            "Compact" -> 360.dp
            "Large" -> 480.dp
            else -> 420.dp
        }

        if (availableProviders.isEmpty() && !isRefreshing) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "No Vega providers available. Add a Vega repository in the 'Vega Repos' tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = extMinSize),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(availableProviders, key = { "${it.value}_${it.baseUrl}" }) { provider ->
                    val isInstalled = installedProviders.contains(provider.value)
                    val isInstalling = installingProviders.contains(provider.value)

                    ExtensionCard(
                        name = provider.display_name,
                        internalName = provider.value,
                        version = provider.version,
                        repoName = "Vega Repository",
                        language = "javascript",
                        tvTypes = emptyList(),
                        iconUrl = provider.icon.takeIf { it.isNotEmpty() },
                        isInstalled = isInstalled,
                        installStatus = if (isInstalled) "Installed" else "Not Installed",
                        isInstalling = isInstalling,
                        onInstallClick = {
                            coroutineScope.launch {
                                installingProviders = installingProviders + provider.value
                                val success = VegaExtensionManager.installProvider(provider.value, provider.baseUrl)
                                if (success) {
                                    refreshList()
                                }
                                installingProviders = installingProviders - provider.value
                            }
                        },
                        onUninstallClick = {
                            coroutineScope.launch {
                                val success = VegaBridgeManager.uninstallProvider(provider.value)
                                if (success) {
                                    refreshList()
                                }
                            }
                        },
                        description = "JavaScript dynamic provider bridged through the local Node.js sidecar.",
                        fileSize = 0,
                        onRepoClick = { },
                        showCheckbox = false,
                        isChecked = false,
                        onCheckedChange = { },
                        showSettings = false,
                        onSettingsClick = { }
                    )
                }
            }
        }
    }
}
