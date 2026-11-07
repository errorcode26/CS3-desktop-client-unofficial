package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.components.CategoryFilterChips
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamAlertDialog
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.P2pTorrentDisclaimerDialog
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Composable
fun HomeManagementDialog(
    show: Boolean,
    allProviders: List<MainAPI>,
    activeProviders: List<String>,
    disabledCatalogs: Map<String, Set<String>>,
    orderedCatalogs: Map<String, List<String>> = emptyMap(),
    pluginIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onSetSingleProvider: (String) -> Unit,
    onToggleProviderActive: (String, Boolean) -> Unit,
    onMoveProvider: (Int, Int) -> Unit,
    onToggleCatalog: (String, String, Boolean) -> Unit,
    onMoveCatalog: (String, Int, Int) -> Unit = { _, _, _ -> },
    onResetCatalogOrder: (String) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var isAdvancedMode by remember { mutableStateOf(activeProviders.size > 1) }
    var catalogProvider by remember { mutableStateOf<MainAPI?>(null) }
    var providerTypeFilter by remember { mutableStateOf(emptySet<TvType>()) }
    var pendingTorrentProviderKey by remember { mutableStateOf<String?>(null) }
    var pendingTorrentProviderName by remember { mutableStateOf<String?>(null) }
    var showTorrentDisclaimer by remember { mutableStateOf(false) }
    var showHybridTorrentDialog by remember { mutableStateOf(false) }

    val handleSelectProvider: (MainAPI, String) -> Unit = { provider, pKey ->
        if (DesktopTorrentEngine.isPureTorrentProvider(provider) && !DesktopTorrentEngine.isP2pEnabled) {
            pendingTorrentProviderKey = pKey
            pendingTorrentProviderName = provider.name
            showTorrentDisclaimer = true
        } else if (DesktopTorrentEngine.isHybridTorrentProvider(provider) && !DesktopTorrentEngine.isP2pEnabled) {
            pendingTorrentProviderKey = pKey
            pendingTorrentProviderName = provider.name
            showHybridTorrentDialog = true
        } else {
            onSetSingleProvider(pKey)
            onDismissRequest()
        }
    }

    val handleAddProvider: (MainAPI, String) -> Unit = { provider, pKey ->
        if (DesktopTorrentEngine.isPureTorrentProvider(provider) && !DesktopTorrentEngine.isP2pEnabled) {
            pendingTorrentProviderKey = pKey
            pendingTorrentProviderName = provider.name
            showTorrentDisclaimer = true
        } else if (DesktopTorrentEngine.isHybridTorrentProvider(provider) && !DesktopTorrentEngine.isP2pEnabled) {
            pendingTorrentProviderKey = pKey
            pendingTorrentProviderName = provider.name
            showHybridTorrentDialog = true
        } else {
            onToggleProviderActive(pKey, true)
        }
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = {
            if (!isAdvancedMode && activeProviders.size > 1) {
                val topProvider = activeProviders.firstOrNull() ?: allProviders.firstOrNull()?.name
                if (topProvider != null) {
                    onSetSingleProvider(topProvider)
                }
            }
            catalogProvider = null
            onDismissRequest()
        },
        modifier = Modifier.fillMaxWidth(0.85f).fillMaxHeight(0.85f),
    ) {
        val activeCatalogProvider = catalogProvider
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (catalogProvider != null) {
                            "Customize ${catalogProvider?.name}"
                        } else if (isAdvancedMode) {
                            "Manage Home Screen Feed"
                        } else {
                            "Select Provider"
                        },
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (catalogProvider != null) {
                            "Enable, disable, and drag to reorder catalogs."
                        } else if (isAdvancedMode) {
                            "Mix, reorder, and customize multiple providers."
                        } else {
                            "Choose a provider to display on your Home Screen."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (activeCatalogProvider != null) {
                    val p = activeCatalogProvider
                    val hasCustomOrder = orderedCatalogs[p.name]?.isNotEmpty() == true
                    if (hasCustomOrder) {
                        OutlinedButton(
                            onClick = { onResetCatalogOrder(p.name) },
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Icon(Icons.Default.RestartAlt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Reset Order", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Enable Multi-Provider Feed", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = isAdvancedMode,
                            onCheckedChange = { enabled ->
                                isAdvancedMode = enabled
                                if (!enabled) {
                                    val topProvider = activeProviders.firstOrNull() ?: allProviders.firstOrNull()?.name
                                    if (topProvider != null) {
                                        onSetSingleProvider(topProvider)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))

            if (catalogProvider == null) {
                val providerFilterCategories = listOf(
                    TvType.Movie to "Movies",
                    TvType.TvSeries to "Series",
                    TvType.Anime to "Anime",
                    TvType.Documentary to "Docs",
                    TvType.Live to "Live",
                )
                CategoryFilterChips(
                    categories = providerFilterCategories.map { it.second },
                    selected = providerTypeFilter.mapNotNullTo(mutableSetOf()) { t ->
                        providerFilterCategories.firstOrNull { it.first == t }?.second
                    },
                    onToggle = { label ->
                        val tvType = providerFilterCategories.firstOrNull { it.second == label }?.first
                        if (tvType != null) {
                            providerTypeFilter = if (tvType in providerTypeFilter) {
                                providerTypeFilter - tvType
                            } else {
                                providerTypeFilter + tvType
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                )
            }

            // MAIN CONTENT (Takes remaining space)
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (activeCatalogProvider != null) {
                    HomeCatalogCustomizationView(
                        provider = activeCatalogProvider,
                        disabledCatalogs = disabledCatalogs[activeCatalogProvider.name] ?: emptySet(),
                        orderedCatalogs = orderedCatalogs[activeCatalogProvider.name] ?: emptyList(),
                        onToggleCatalog = { catalogName, isEnabled ->
                            onToggleCatalog(activeCatalogProvider.name, catalogName, isEnabled)
                        },
                        onMoveCatalog = { fromIndex, toIndex ->
                            onMoveCatalog(activeCatalogProvider.name, fromIndex, toIndex)
                        },
                    )
                } else if (!isAdvancedMode) {
                    HomeSimpleProviderGrid(
                        allProviders = allProviders,
                        activeProviders = activeProviders,
                        providerTypeFilter = providerTypeFilter,
                        pluginIcons = pluginIcons,
                        onSelectProvider = handleSelectProvider,
                        onCustomizeProvider = { provider -> catalogProvider = provider },
                    )
                } else {
                    HomeAdvancedFeedView(
                        allProviders = allProviders,
                        activeProviders = activeProviders,
                        disabledCatalogs = disabledCatalogs,
                        orderedCatalogs = orderedCatalogs,
                        providerTypeFilter = providerTypeFilter,
                        pluginIcons = pluginIcons,
                        isAdvancedMode = isAdvancedMode,
                        onMoveProvider = onMoveProvider,
                        onToggleProviderActive = onToggleProviderActive,
                        onToggleCatalog = onToggleCatalog,
                        onMoveCatalog = onMoveCatalog,
                        onAddProvider = handleAddProvider,
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BOTTOM ACTION BAR
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (activeCatalogProvider != null) {
                    OutlinedButton(onClick = { catalogProvider = null }) {
                        Text("Back")
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Button(onClick = {
                    if (!isAdvancedMode && activeProviders.size > 1) {
                        val topProvider = activeProviders.firstOrNull() ?: allProviders.firstOrNull()?.name
                        if (topProvider != null) {
                            onSetSingleProvider(topProvider)
                        }
                    }
                    catalogProvider = null
                    onDismissRequest()
                }) {
                    Text("Save & Close")
                }
            }
        }
    }

    P2pTorrentDisclaimerDialog(
        show = showTorrentDisclaimer,
        isSettingsContext = false,
        onDismiss = {
            showTorrentDisclaimer = false
            pendingTorrentProviderKey = null
            pendingTorrentProviderName = null
            AppToastManager.showWarning("P2P is disabled. Enable it to use torrent providers.")
        },
        onConfirm = {
            showTorrentDisclaimer = false
            val key = pendingTorrentProviderKey
            coroutineScope.launch(Dispatchers.IO) {
                DesktopDataStore.setKey(DesktopDataStore.PREF_P2P_ENABLED, true)
            }
            AppToastManager.showSuccess("P2P Torrent Streaming enabled")
            if (key != null) {
                if (isAdvancedMode) {
                    onToggleProviderActive(key, true)
                } else {
                    onSetSingleProvider(key)
                    onDismissRequest()
                }
            }
            pendingTorrentProviderKey = null
            pendingTorrentProviderName = null
        },
    )

    CloudstreamAlertDialog(
        show = showHybridTorrentDialog,
        onDismissRequest = {
            showHybridTorrentDialog = false
            pendingTorrentProviderKey = null
            pendingTorrentProviderName = null
        },
        title = { Text("Hybrid Provider (Direct + P2P)") },
        text = {
            Text(
                "The provider '${pendingTorrentProviderName ?: "Selected"}' contains both direct streaming and torrent scrapers.\n\n" +
                    "TorrServer is currently disabled. Would you like to enable P2P to use all sources, or continue with direct streams only (torrents will be auto-skipped in real time)?",
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    showHybridTorrentDialog = false
                    val key = pendingTorrentProviderKey
                    coroutineScope.launch(Dispatchers.IO) {
                        DesktopDataStore.setKey(DesktopDataStore.PREF_P2P_ENABLED, true)
                    }
                    AppToastManager.showSuccess("P2P Torrent Streaming enabled")
                    if (key != null) {
                        if (isAdvancedMode) {
                            onToggleProviderActive(key, true)
                        } else {
                            onSetSingleProvider(key)
                            onDismissRequest()
                        }
                    }
                    pendingTorrentProviderKey = null
                    pendingTorrentProviderName = null
                },
            ) {
                Text("Enable P2P & Use All Sources", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        showHybridTorrentDialog = false
                        pendingTorrentProviderKey = null
                        pendingTorrentProviderName = null
                    },
                ) {
                    Text("Cancel")
                }
                TextButton(
                    onClick = {
                        showHybridTorrentDialog = false
                        val key = pendingTorrentProviderKey
                        AppToastManager.showInfo("Using direct streams only (torrents skipped)")
                        if (key != null) {
                            if (isAdvancedMode) {
                                onToggleProviderActive(key, true)
                            } else {
                                onSetSingleProvider(key)
                                onDismissRequest()
                            }
                        }
                        pendingTorrentProviderKey = null
                        pendingTorrentProviderName = null
                    },
                ) {
                    Text("Direct Streams Only")
                }
            }
        },
    )
}
