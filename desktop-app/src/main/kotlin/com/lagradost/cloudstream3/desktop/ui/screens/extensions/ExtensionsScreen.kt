package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ComposeExtensionScreen(onNavigate: (Config) -> Unit, initialTab: Int = 0) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab) }
    val tabs = listOf("Browse", "Installed", "Repositories")
    val coroutineScope = rememberCoroutineScope()
    val viewModel = remember { ExtensionsViewModel() }
    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }
    val uiState by viewModel.uiState.collectAsState()
    val syncGen = uiState.syncGeneration
    val inspectedRepoName = uiState.inspectedRepoName

    LaunchedEffect(inspectedRepoName) {
        if (!inspectedRepoName.isNullOrBlank()) {
            selectedTab = 2
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onEvent(ExtensionsUiEvent.OnLoadPluginsFromManager)
        viewModel.onEvent(ExtensionsUiEvent.OnRefreshInstalled)
    }

    // Collect one-shot effects from the ViewModel.
    // ClearActiveProvider: the ViewModel detected the removed plugin owned the active provider,
    // so we do the actual DataStore write here in the UI layer to stay within MVI boundaries.
    LaunchedEffect(viewModel.effectFlow) {
        viewModel.effectFlow.collect { effect ->
            when (effect) {
                is ExtensionsUiEffect.ClearActiveProvider -> {
                    kotlinx.coroutines.withContext(Dispatchers.IO) {
                        DesktopDataStore.removeKey("preferred_provider_name")
                    }
                    com.lagradost.common.logging.AppLogger.i(
                        "ExtensionsScreen: cleared active provider '${effect.removedProviderName}' after plugin removal.",
                    )
                }
                is ExtensionsUiEffect.ShowNotification -> { /* future: show snackbar */ }
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Row(modifier = Modifier.widthIn(max = 1600.dp).fillMaxSize()) {
            // Left Pane: Sidebar Navigation
            Column(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .padding(end = 24.dp),
            ) {
                Text(
                    text = "Extensions",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 32.dp, start = 8.dp),
                )

                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Surface(
                        onClick = { selectedTab = index },
                        shape = MaterialTheme.shapes.medium,
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = title,
                                color = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                var isSyncing by remember { mutableStateOf(false) }
                Button(
                    onClick = {
                        if (isSyncing) return@Button
                        coroutineScope.launch(Dispatchers.IO) {
                            isSyncing = true
                            try {
                                viewModel.onEvent(ExtensionsUiEvent.OnSyncAllRepos)
                            } catch (e: Exception) {
                                // ignore
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                ) {
                    if (isSyncing) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                    } else {
                        Icon(androidx.compose.material.icons.Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                    }
                    Text("Update All")
                }
            }

            // Vertical Divider
            VerticalDivider(
                modifier = Modifier.fillMaxHeight().padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )

            // Right Pane: Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 32.dp),
            ) {
                androidx.compose.animation.Crossfade(
                    targetState = selectedTab,
                    animationSpec = androidx.compose.animation.core.tween(200),
                    label = "extensions_crossfade",
                ) { tabIndex ->
                    when (tabIndex) {
                        0 -> BrowseTab(viewModel = viewModel, syncGeneration = syncGen)
                        1 -> InstalledTab(viewModel = viewModel, syncGeneration = syncGen)
                        2 -> RepositoriesTab(viewModel = viewModel)
                    }
                }
            }
        }
    }
}
