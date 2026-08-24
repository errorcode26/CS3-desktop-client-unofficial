package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun ComposeExtensionScreen(
    onNavigate: (Config) -> Unit,
    initialTab: Int = 0,
    viewModel: ExtensionsViewModel,
    isInsideSettings: Boolean = false,
) {
    var selectedTab by remember(initialTab) { mutableStateOf(initialTab.coerceIn(0, 3)) }
    val tabs = listOf("Browse Catalog", "Installed Plugins", "Repositories & Sources", "Update History")
    val coroutineScope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val syncGen = uiState.syncGeneration
    val inspectedRepoName = uiState.inspectedRepoName
    var isSyncing by remember { mutableStateOf(false) }

    LaunchedEffect(inspectedRepoName) {
        if (!inspectedRepoName.isNullOrBlank()) {
            selectedTab = 2
        }
    }

    LaunchedEffect(Unit) {
        viewModel.onEvent(ExtensionsUiEvent.OnLoadPluginsFromManager)
        viewModel.onEvent(ExtensionsUiEvent.OnRefreshInstalled)
    }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isInsideSettings) PaddingValues(0.dp) else PaddingValues(horizontal = 24.dp, vertical = 16.dp)),
    ) {
        // Horizontal Top Bar: Tabs as Rows on Top + Sync All Action
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEachIndexed { index, title ->
                    val isSelected = selectedTab == index
                    Surface(
                        onClick = { selectedTab = index },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        } else null,
                    ) {
                        Text(
                            text = title,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
                        )
                    }
                }
            }

            // Sync All Action Button
            FilledTonalButton(
                onClick = {
                    if (isSyncing) return@FilledTonalButton
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
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Syncing...")
                } else {
                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sync All")
                }
            }
        }

        HorizontalDivider(
            modifier = Modifier.padding(bottom = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
        )

        // Full Width Content Area
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
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
                    3 -> UpdateHistoryTab()
                }
            }
        }

        // ── Global Security & Permission Dialogs (Available on all tabs) ─────
        uiState.pluginRequiringBypass?.let { (bypassRepo, bypassPlugin, reason) ->
            var isDialogInstalling by remember { mutableStateOf(false) }
            val cleanReason = reason
                .removePrefix("Plugin Security Notice: ")
                .removePrefix("Plugin Security: ")
                .trim()

            com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
                show = true,
                onDismissRequest = { if (!isDialogInstalling) viewModel.onEvent(ExtensionsUiEvent.OnClearBypass) },
                title = { Text("Trust & Install Extension?") },
                text = {
                    if (isDialogInstalling) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 12.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(16.dp))
                            Text("Installing, please wait...")
                        }
                    } else {
                        val devInfo = bypassPlugin.authorName?.let { " by $it" } ?: ""
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "You are installing '${bypassPlugin.name}'$devInfo from repository '$bypassRepo'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                            )

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.35f)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        text = "⚠️ Flagged API Access",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = cleanReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                        fontSize = 12.sp,
                                    )
                                }
                            }

                            Text(
                                text = "If you trust this developer and repository, click 'Trust & Install' to proceed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                confirmButton = {
                    if (!isDialogInstalling) {
                        Button(
                            onClick = {
                                isDialogInstalling = true
                                viewModel.onEvent(
                                    ExtensionsUiEvent.OnBypassSecurityAndInstall(bypassRepo, bypassPlugin) {
                                        isDialogInstalling = false
                                    },
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        ) {
                            Text("Trust & Install")
                        }
                    }
                },
                dismissButton = {
                    if (!isDialogInstalling) {
                        OutlinedButton(onClick = { viewModel.onEvent(ExtensionsUiEvent.OnClearBypass) }) {
                            Text("Cancel")
                        }
                    }
                },
            )
        }

        uiState.pluginRequiringPermission?.let { (reqRepo, reqPlugin, reqPermission) ->
            var isDialogInstalling by remember { mutableStateOf(false) }
            com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog(
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
