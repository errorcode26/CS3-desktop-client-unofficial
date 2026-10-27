package com.lagradost.cloudstream3.desktop.ui.screens.extensions

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.PremiumIcons
import com.lagradost.cloudstream3.desktop.ui.navigation.Config
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.contract.ExtensionsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsAddons
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers

@Composable
fun ComposeExtensionScreen(
    onNavigate: (Config) -> Unit,
    initialTab: Int = 0,
    viewModel: ExtensionsViewModel,
    isInsideSettings: Boolean = false,
) {
    data class ExtensionTabItem(
        val title: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
        val isExternal: Boolean = false,
    )

    var selectedTab by remember(initialTab) { mutableStateOf(initialTab.coerceIn(0, 4)) }
    val tabs = remember {
        listOf(
            ExtensionTabItem("Browse Plugins", Icons.Default.Explore),
            ExtensionTabItem("Installed", PremiumIcons.Extensions),
            ExtensionTabItem("Repositories", Icons.Default.Folder),
            ExtensionTabItem("Stremio Addons", Icons.Default.Public, isExternal = true),
            ExtensionTabItem("Update History", Icons.Default.Update),
        )
    }
    val uiState by viewModel.uiState.collectAsState()
    val syncGen = uiState.syncGeneration
    val inspectedRepoName = uiState.inspectedRepoName

    LaunchedEffect(inspectedRepoName) {
        if (!inspectedRepoName.isNullOrBlank()) {
            selectedTab = 2
        }
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
                is ExtensionsUiEffect.ShowNotification -> {
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(effect.message)
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .padding(if (isInsideSettings) PaddingValues(0.dp) else PaddingValues(horizontal = 24.dp, vertical = 16.dp)),
    ) {
        val isCompactHeight = maxHeight < 780.dp
        val isCompactWidth = maxWidth < 950.dp
        val tabHorizontalPadding = if (isCompactWidth) 10.dp else 14.dp
        val tabVerticalPadding = if (isCompactHeight) 6.dp else 9.dp
        val topBarBottomPadding = if (isCompactHeight) 8.dp else 16.dp
        val dividerBottomPadding = if (isCompactHeight) 8.dp else 16.dp
        val bannerBottomPadding = if (isCompactHeight) 8.dp else 14.dp

        var isRecommendationDismissed by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxSize()) {
            // Horizontal Top Bar: Tabs as Rows on Top + Sync All Action
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = topBarBottomPadding),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(if (isCompactWidth) 6.dp else 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    tabs.forEachIndexed { index, tab ->
                        val isSelected = selectedTab == index
                        val isStremioTab = tab.isExternal

                        Surface(
                            onClick = { selectedTab = index },
                            shape = RoundedCornerShape(12.dp),
                            color = when {
                                isSelected && isStremioTab -> Color(0xFF00B4D8).copy(alpha = 0.20f)
                                isSelected -> MaterialTheme.colorScheme.primaryContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            },
                            border = when {
                                isSelected && isStremioTab -> BorderStroke(1.dp, Color(0xFF00B4D8).copy(alpha = 0.6f))
                                isSelected -> BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                                else -> null
                            },
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = tabHorizontalPadding, vertical = tabVerticalPadding),
                            ) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = when {
                                        isSelected && isStremioTab -> Color(0xFF00B4D8)
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    modifier = Modifier.size(16.dp),
                                )

                                Text(
                                    text = tab.title,
                                    color = when {
                                        isSelected && isStremioTab -> Color(0xFF00B4D8)
                                        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    style = MaterialTheme.typography.bodyMedium,
                                )

                                if (isStremioTab) {
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF00B4D8).copy(alpha = 0.15f),
                                    ) {
                                        Text(
                                            text = "External",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF00B4D8),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Sync All Action Button (Visible on CS3 tabs)
                if (selectedTab != 3) {
                    FilledTonalButton(
                        onClick = {
                            if (!uiState.isSyncing) {
                                viewModel.onEvent(ExtensionsUiEvent.OnSyncAllRepos)
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = if (isCompactHeight) PaddingValues(horizontal = 10.dp, vertical = 6.dp) else ButtonDefaults.ContentPadding,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        if (uiState.isSyncing) {
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
            }

            HorizontalDivider(
                modifier = Modifier.padding(bottom = dividerBottomPadding),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
            )

            if (!isRecommendationDismissed && selectedTab in 0..2) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.30f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = bannerBottomPadding),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = if (isCompactHeight) 5.dp else 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Recommendation: To maintain optimal responsiveness, lower memory overhead, and system stability, install only the plugins you actively use.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { isRecommendationDismissed = true },
                            modifier = Modifier.size(20.dp),
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                        }
                    }
                }
            }

            // Full Width Content Area with Fluid Crossfade
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        (fadeIn(tween(180)) + slideInVertically(tween(180)) { it / 20 })
                            .togetherWith(fadeOut(tween(100)))
                    },
                    label = "ExtensionTabTransition",
                    modifier = Modifier.fillMaxSize(),
                ) { targetTab ->
                    when (targetTab) {
                        0 -> BrowseTab(viewModel = viewModel, syncGeneration = syncGen)
                        1 -> InstalledTab(viewModel = viewModel, syncGeneration = syncGen)
                        2 -> RepositoriesTab(viewModel = viewModel)
                        3 -> SettingsAddons(viewModel = viewModel)
                        4 -> UpdateHistoryTab(viewModel = viewModel)
                    }
                }
            }
        }

        // ── Global Security & Permission Dialogs (Available on all tabs) ─────
        uiState.pluginRequiringBypass?.let { (bypassRepo, bypassPlugin, reason) ->
            val isDialogInstalling = uiState.isDialogInstalling
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

                            if (cleanReason.isNotBlank()) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
                                ) {
                                    Text(
                                        text = cleanReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(10.dp),
                                    )
                                }
                            }

                            Text(
                                text = "Only install extensions from sources you trust. Untrusted plugins may read app data.",
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
                                viewModel.onEvent(
                                    ExtensionsUiEvent.OnBypassSecurityAndInstall(bypassRepo, bypassPlugin),
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
            val isDialogInstalling = uiState.isDialogInstalling
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
                                viewModel.onEvent(
                                    ExtensionsUiEvent.OnGrantPermissionAndInstall(reqRepo, reqPlugin, reqPermission),
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
