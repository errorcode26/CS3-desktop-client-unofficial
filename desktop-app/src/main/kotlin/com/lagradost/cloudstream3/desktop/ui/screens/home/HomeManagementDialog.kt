package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
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

    fun fuzzyMatchIcon(providerName: String): String? {
        val cleanName = if (providerName.contains("::")) providerName.substringAfter("::") else providerName
        pluginIcons[cleanName]?.let { return it }
        pluginIcons[providerName]?.let { return it }
        val pName = cleanName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
        return pluginIcons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
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
        Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
            // HEADER
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
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
                if (catalogProvider != null) {
                    val p = catalogProvider!!
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
                if (catalogProvider != null) {
                    // CATALOG TWEAKING SUB-SCREEN
                    val p = catalogProvider!!
                    if (p.mainPage.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No catalogs available for this provider.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        val provDisabled = disabledCatalogs[p.name] ?: emptySet()
                        val customOrder = orderedCatalogs[p.name] ?: emptyList()
                        val sortedCatalogs = remember(p.mainPage, customOrder) {
                            if (customOrder.isNotEmpty()) {
                                val orderMap = customOrder.withIndex().associate { it.value to it.index }
                                p.mainPage.sortedBy { orderMap[it.name] ?: Int.MAX_VALUE }
                            } else {
                                p.mainPage
                            }
                        }

                        var draggingCatalogKey by remember { mutableStateOf<String?>(null) }
                        var catalogDragAccumulatedY by remember { mutableStateOf(0f) }
                        var catalogDragInitialIndex by remember { mutableStateOf(0) }
                        var catalogSlotHeightPx by remember { mutableStateOf(0f) }
                        val fallbackCatalogHeight = with(LocalDensity.current) { 56.dp.toPx() }

                        val effectiveSlotHeight = if (catalogSlotHeightPx > 0f) catalogSlotHeightPx else fallbackCatalogHeight
                        val currentTargetIndex = if (draggingCatalogKey != null && effectiveSlotHeight > 0f) {
                            (catalogDragInitialIndex + kotlin.math.round(catalogDragAccumulatedY / effectiveSlotHeight).toInt())
                                .coerceIn(0, sortedCatalogs.lastIndex)
                        } else catalogDragInitialIndex

                        val currentSortedCatalogs by rememberUpdatedState(sortedCatalogs)
                        val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
                        val currentCatalogDragAccumulatedY by rememberUpdatedState(catalogDragAccumulatedY)
                        val currentCatalogDragInitialIndex by rememberUpdatedState(catalogDragInitialIndex)

                        val onDropCatalog by rememberUpdatedState {
                            val fromIdx = currentCatalogDragInitialIndex
                            val slotH = currentEffectiveSlotHeight
                            val accY = currentCatalogDragAccumulatedY
                            val toIdx = if (slotH > 0f) {
                                (fromIdx + kotlin.math.round(accY / slotH).toInt())
                                    .coerceIn(0, currentSortedCatalogs.lastIndex)
                            } else fromIdx
                            draggingCatalogKey = null
                            catalogDragAccumulatedY = 0f
                            if (fromIdx != toIdx && fromIdx in currentSortedCatalogs.indices && toIdx in currentSortedCatalogs.indices) {
                                onMoveCatalog(p.name, fromIdx, toIdx)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            itemsIndexed(sortedCatalogs, key = { index, cat -> "${p.name}_${cat.name}_${cat.data}_$index" }) { index, catalog ->
                                val catalogKey = "${p.name}_${catalog.name}_${catalog.data}_$index"
                                val isEnabled = catalog.name !in provDisabled
                                val isDraggingThis = draggingCatalogKey == catalogKey

                                val targetShiftY = when {
                                    isDraggingThis -> catalogDragAccumulatedY
                                    draggingCatalogKey != null && catalogDragInitialIndex < currentTargetIndex && index in (catalogDragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                                    draggingCatalogKey != null && catalogDragInitialIndex > currentTargetIndex && index in currentTargetIndex until catalogDragInitialIndex -> effectiveSlotHeight
                                    else -> 0f
                                }
                                val animatedShiftY by androidx.compose.animation.core.animateFloatAsState(
                                    targetValue = targetShiftY,
                                    animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                                )

                                val elevation by androidx.compose.animation.core.animateDpAsState(if (isDraggingThis) 12.dp else 0.dp)
                                val scale by androidx.compose.animation.core.animateFloatAsState(if (isDraggingThis) 1.02f else 1.0f)

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isDraggingThis) {
                                        MaterialTheme.colorScheme.surfaceVariant
                                    } else if (isEnabled) {
                                        MaterialTheme.colorScheme.surface
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    },
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isDraggingThis) 1.5.dp else 0.5.dp,
                                        if (isDraggingThis) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
                                    ),
                                    shadowElevation = elevation,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coordinates ->
                                            if (coordinates.size.height > 0 && catalogSlotHeightPx == 0f) {
                                                catalogSlotHeightPx = coordinates.size.height.toFloat() + 8f
                                            }
                                        }
                                        .zIndex(if (isDraggingThis) 100f else 1f)
                                        .scale(scale)
                                        .graphicsLayer {
                                            translationY = if (isDraggingThis) catalogDragAccumulatedY else animatedShiftY
                                        },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Drag Grip Handle
                                        Box(
                                            modifier = Modifier
                                                .size(32.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                                                .pointerInput(catalogKey) {
                                                    detectDragGestures(
                                                        onDragStart = {
                                                            draggingCatalogKey = catalogKey
                                                            catalogDragInitialIndex = index
                                                            catalogDragAccumulatedY = 0f
                                                        },
                                                        onDragEnd = { onDropCatalog() },
                                                        onDragCancel = {
                                                            draggingCatalogKey = null
                                                            catalogDragAccumulatedY = 0f
                                                        },
                                                    ) { change, dragAmount ->
                                                        change.consume()
                                                        catalogDragAccumulatedY += dragAmount.y
                                                    }
                                                },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                Icons.Default.DragIndicator,
                                                contentDescription = "Hold and drag to reorder",
                                                tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Text(
                                            text = "${index + 1}.",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                            modifier = Modifier.width(32.dp),
                                        )

                                        Text(
                                            text = catalog.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f),
                                        )

                                        // Move Up / Down Buttons
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                                                .padding(horizontal = 4.dp),
                                        ) {
                                            IconButton(
                                                onClick = { onMoveCatalog(p.name, index, index - 1) },
                                                enabled = index > 0,
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                                            }
                                            IconButton(
                                                onClick = { onMoveCatalog(p.name, index, index + 1) },
                                                enabled = index < sortedCatalogs.lastIndex,
                                                modifier = Modifier.size(28.dp),
                                            ) {
                                                Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                                            }
                                        }

                                        Spacer(modifier = Modifier.width(16.dp))

                                        Switch(
                                            checked = isEnabled,
                                            onCheckedChange = { onToggleCatalog(p.name, catalog.name, it) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else if (!isAdvancedMode) {
                    // SIMPLE MODE: Just a grid of all providers
                    val filteredProviders = if (providerTypeFilter.isEmpty()) {
                        allProviders
                    } else {
                        allProviders.filter { p -> p.supportedTypes.any { it in providerTypeFilter } }
                    }
                    val sortedProviders = filteredProviders.sortedBy { it.name }
                    val duplicateHomeNames = remember(sortedProviders) {
                        sortedProviders.groupBy { it.name }.filterValues { it.size > 1 }.keys
                    }
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 250.dp),
                        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(sortedProviders.size, key = { index -> "${sortedProviders[index].name}_${sortedProviders[index].mainUrl}_${sortedProviders[index].sourcePlugin}_$index" }) { index ->
                            val provider = sortedProviders[index]
                            val pKey = if (provider.sourcePlugin != null && provider.sourcePlugin != "built-in") {
                                "${java.io.File(provider.sourcePlugin).parentFile?.name ?: ""}::${provider.name}"
                            } else {
                                provider.name
                            }
                            val isSingleNameUnique = sortedProviders.count { it.name == provider.name } == 1
                            val isActive = activeProviders.contains(pKey) || (isSingleNameUnique && activeProviders.contains(provider.name))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)
                                    .clickable {
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
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                val url = fuzzyMatchIcon(provider.name)
                                if (url != null) {
                                    coil3.compose.AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                } else {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(if (isActive) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Default.Extension, contentDescription = null, tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                    Spacer(modifier = Modifier.width(12.dp))
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    val repoTag = if (provider.name in duplicateHomeNames) {
                                        provider.sourcePlugin?.let {
                                            try {
                                                java.io.File(it).parentFile?.name?.replace("_", " ")
                                            } catch (_: Exception) { null }
                                        }
                                    } else null

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            provider.name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                            color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f, fill = false),
                                        )
                                        if (!repoTag.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                "($repoTag)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.primary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                        }
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (!isActive && provider.mainPage.isEmpty()) {
                                            Text(
                                                "Select to load catalogs",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                            )
                                        } else {
                                            Text("${provider.mainPage.size} catalogs", style = MaterialTheme.typography.bodySmall, color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (DesktopTorrentEngine.isTorrentProvider(provider)) {
                                            val isHybrid = DesktopTorrentEngine.isHybridTorrentProvider(provider)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Surface(
                                                shape = RoundedCornerShape(4.dp),
                                                color = (if (isHybrid) Color(0xFF2563EB) else Color(0xFFF59E0B)).copy(alpha = 0.2f),
                                            ) {
                                                Text(
                                                    text = if (isHybrid) "⚡ Hybrid" else "⚡ Torrent",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isHybrid) Color(0xFF60A5FA) else Color(0xFFF59E0B),
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                                                    fontSize = 10.sp,
                                                )
                                            }
                                        }
                                    }
                                }

                                IconButton(onClick = {
                                    if (!isActive && provider.mainPage.isEmpty()) {
                                        AppToastManager.showInfo("Select this plugin first to load its catalogs.")
                                    } else {
                                        catalogProvider = provider
                                    }
                                }) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = "Customize Catalogs",
                                        tint = if (!isActive && provider.mainPage.isEmpty())
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                                        else if (isActive)
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // ADVANCED MODE: Two-column customizable feed
                    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Left Column: Active Providers Feed
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Text("Active Providers Feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))

                            if (activeProviders.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Text("No active providers", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                var draggingProviderName by remember { mutableStateOf<String?>(null) }
                                var providerDragAccumulatedY by remember { mutableStateOf(0f) }
                                var providerDragInitialIndex by remember { mutableStateOf(0) }
                                var providerSlotHeightPx by remember { mutableStateOf(0f) }
                                val fallbackProviderHeight = with(LocalDensity.current) { 68.dp.toPx() }

                                val effectiveSlotHeight = if (providerSlotHeightPx > 0f) providerSlotHeightPx else fallbackProviderHeight
                                val currentTargetIndex = if (draggingProviderName != null && effectiveSlotHeight > 0f) {
                                    (providerDragInitialIndex + kotlin.math.round(providerDragAccumulatedY / effectiveSlotHeight).toInt())
                                        .coerceIn(0, activeProviders.lastIndex)
                                } else providerDragInitialIndex

                                val currentActiveProviders by rememberUpdatedState(activeProviders)
                                val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
                                val currentProviderDragAccumulatedY by rememberUpdatedState(providerDragAccumulatedY)
                                val currentProviderDragInitialIndex by rememberUpdatedState(providerDragInitialIndex)

                                val onDropProvider by rememberUpdatedState {
                                    val fromIdx = currentProviderDragInitialIndex
                                    val slotH = currentEffectiveSlotHeight
                                    val accY = currentProviderDragAccumulatedY
                                    val toIdx = if (slotH > 0f) {
                                        (fromIdx + kotlin.math.round(accY / slotH).toInt())
                                            .coerceIn(0, currentActiveProviders.lastIndex)
                                    } else fromIdx
                                    draggingProviderName = null
                                    providerDragAccumulatedY = 0f
                                    if (fromIdx != toIdx && fromIdx in currentActiveProviders.indices && toIdx in currentActiveProviders.indices) {
                                        onMoveProvider(fromIdx, toIdx)
                                    }
                                }

                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    itemsIndexed(activeProviders, key = { index, name -> "${name}_$index" }) { index, providerName ->
                                        val provider = allProviders.find {
                                            val pKey = if (it.sourcePlugin != null && it.sourcePlugin != "built-in") {
                                                "${java.io.File(it.sourcePlugin).parentFile?.name ?: ""}::${it.name}"
                                            } else {
                                                it.name
                                            }
                                            pKey == providerName || it.name == providerName
                                        }
                                        if (provider != null) {
                                            val isDraggingThis = draggingProviderName == providerName

                                            val targetShiftY = when {
                                                isDraggingThis -> providerDragAccumulatedY
                                                draggingProviderName != null && providerDragInitialIndex < currentTargetIndex && index in (providerDragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                                                draggingProviderName != null && providerDragInitialIndex > currentTargetIndex && index in currentTargetIndex until providerDragInitialIndex -> effectiveSlotHeight
                                                else -> 0f
                                            }
                                            val animatedShiftY by androidx.compose.animation.core.animateFloatAsState(
                                                targetValue = targetShiftY,
                                                animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow),
                                            )

                                            ActiveProviderItem(
                                                index = index,
                                                totalActive = activeProviders.size,
                                                provider = provider,
                                                iconUrl = fuzzyMatchIcon(providerName),
                                                disabledCatalogs = disabledCatalogs[providerName] ?: emptySet(),
                                                orderedCatalogs = orderedCatalogs[providerName] ?: emptyList(),
                                                isAdvancedMode = isAdvancedMode,
                                                isDraggingThis = isDraggingThis,
                                                dragOffsetY = if (isDraggingThis) providerDragAccumulatedY else animatedShiftY,
                                                onHeightMeasured = { h ->
                                                    if (providerSlotHeightPx == 0f && h > 0f) {
                                                        providerSlotHeightPx = h + 8f
                                                    }
                                                },
                                                onDragStart = {
                                                    draggingProviderName = providerName
                                                    providerDragInitialIndex = currentActiveProviders.indexOf(providerName)
                                                    providerDragAccumulatedY = 0f
                                                },
                                                onDragEnd = {
                                                    onDropProvider()
                                                },
                                                onDragCancel = {
                                                    draggingProviderName = null
                                                    providerDragAccumulatedY = 0f
                                                },
                                                onDragDelta = { dy ->
                                                    providerDragAccumulatedY += dy
                                                },
                                                onMoveUp = { onMoveProvider(index, index - 1) },
                                                onMoveDown = { onMoveProvider(index, index + 1) },
                                                onRemove = { onToggleProviderActive(providerName, false) },
                                                onToggleCatalog = { catalog, isEnabled -> onToggleCatalog(providerName, catalog, isEnabled) },
                                                onMoveCatalog = { from, to -> onMoveCatalog(providerName, from, to) },
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right Column: Available Plugins
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            Text("Available Plugins", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(12.dp))

                            val filteredInactive = if (providerTypeFilter.isEmpty()) {
                                allProviders.filter { p ->
                                    val pKey = if (p.sourcePlugin != null && p.sourcePlugin != "built-in") {
                                        "${java.io.File(p.sourcePlugin).parentFile?.name ?: ""}::${p.name}"
                                    } else {
                                        p.name
                                    }
                                    pKey !in activeProviders && p.name !in activeProviders
                                }
                            } else {
                                allProviders.filter { p ->
                                    val pKey = if (p.sourcePlugin != null && p.sourcePlugin != "built-in") {
                                        "${java.io.File(p.sourcePlugin).parentFile?.name ?: ""}::${p.name}"
                                    } else {
                                        p.name
                                    }
                                    (pKey !in activeProviders && p.name !in activeProviders) && p.supportedTypes.any { t -> t in providerTypeFilter }
                                }
                            }
                            val inactiveProviders = filteredInactive.sortedBy { it.name }
                            val duplicateInactiveNames = remember(inactiveProviders) {
                                inactiveProviders.groupBy { it.name }.filterValues { it.size > 1 }.keys
                            }

                            if (inactiveProviders.isEmpty()) {
                                Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                                    Text("All plugins are active", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)).padding(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(inactiveProviders.size, key = { index -> "${inactiveProviders[index].name}_${inactiveProviders[index].mainUrl}_${inactiveProviders[index].sourcePlugin ?: "none"}_$index" }) { index ->
                                        val provider = inactiveProviders[index]
                                        val pKey = if (provider.sourcePlugin != null && provider.sourcePlugin != "built-in") {
                                            "${java.io.File(provider.sourcePlugin).parentFile?.name ?: ""}::${provider.name}"
                                        } else {
                                            provider.name
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surface).padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            val url = fuzzyMatchIcon(provider.name)
                                            if (url != null) {
                                                coil3.compose.AsyncImage(
                                                    model = url,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)),
                                                )
                                                Spacer(modifier = Modifier.width(12.dp))
                                            } else {
                                                Box(
                                                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                                                    contentAlignment = Alignment.Center,
                                                ) {
                                                    Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                                                }
                                                Spacer(modifier = Modifier.width(12.dp))
                                            }

                                            Column(modifier = Modifier.weight(1f)) {
                                                val repoTag = if (provider.name in duplicateInactiveNames) {
                                                    provider.sourcePlugin?.let {
                                                        try {
                                                            java.io.File(it).parentFile?.name?.replace("_", " ")
                                                        } catch (_: Exception) { null }
                                                    }
                                                } else null

                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                ) {
                                                    Text(
                                                        provider.name,
                                                        style = MaterialTheme.typography.bodyLarge,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                    )
                                                    if (!repoTag.isNullOrBlank()) {
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            "($repoTag)",
                                                            style = MaterialTheme.typography.labelSmall,
                                                            color = MaterialTheme.colorScheme.primary,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                            modifier = Modifier.weight(1f, fill = false),
                                                        )
                                                    }
                                                }
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text("${provider.mainPage.size} catalogs available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    if (DesktopTorrentEngine.isTorrentProvider(provider)) {
                                                        val isHybrid = DesktopTorrentEngine.isHybridTorrentProvider(provider)
                                                        Spacer(modifier = Modifier.width(6.dp))
                                                        Surface(
                                                            shape = RoundedCornerShape(4.dp),
                                                            color = (if (isHybrid) Color(0xFF2563EB) else Color(0xFFF59E0B)).copy(alpha = 0.2f),
                                                        ) {
                                                            Text(
                                                                text = if (isHybrid) "⚡ Hybrid" else "⚡ Torrent",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = if (isHybrid) Color(0xFF60A5FA) else Color(0xFFF59E0B),
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 0.dp),
                                                                fontSize = 10.sp,
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                            FilledTonalIconButton(onClick = {
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
                                            }) {
                                                Icon(Icons.Default.Add, contentDescription = "Add")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BOTTOM ACTION BAR
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (catalogProvider != null) {
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
                "TorrServer is currently disabled. Would you like to enable P2P to use all sources, or continue with direct streams only (torrents will be auto-skipped in real time)?"
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
                }
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
                    }
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
                    }
                ) {
                    Text("Direct Streams Only")
                }
            }
        },
    )
}

@Composable
private fun ActiveProviderItem(
    index: Int,
    totalActive: Int,
    provider: MainAPI,
    iconUrl: String?,
    disabledCatalogs: Set<String>,
    orderedCatalogs: List<String> = emptyList(),
    isAdvancedMode: Boolean,
    isDraggingThis: Boolean = false,
    dragOffsetY: Float = 0f,
    onHeightMeasured: (Float) -> Unit = {},
    onDragStart: () -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDragDelta: (Float) -> Unit = {},
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onToggleCatalog: (String, Boolean) -> Unit,
    onMoveCatalog: (Int, Int) -> Unit = { _, _ -> },
) {
    var isExpanded by remember { mutableStateOf(false) }
    val elevation by animateDpAsState(if (isDraggingThis) 24.dp else 0.dp)
    val scale by animateFloatAsState(if (isDraggingThis) 1.03f else 1.0f)

    val currentOnDragStart by rememberUpdatedState(onDragStart)
    val currentOnDragEnd by rememberUpdatedState(onDragEnd)
    val currentOnDragCancel by rememberUpdatedState(onDragCancel)
    val currentOnDragDelta by rememberUpdatedState(onDragDelta)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (isDraggingThis) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.98f) else MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            if (isDraggingThis) 2.dp else 0.5.dp,
            if (isDraggingThis) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
        ),
        shadowElevation = elevation,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { coordinates ->
                if (coordinates.size.height > 0) {
                    onHeightMeasured(coordinates.size.height.toFloat())
                }
            }
            .zIndex(if (isDraggingThis) 100f else 1f)
            .scale(scale)
            .graphicsLayer {
                translationY = dragOffsetY
            },
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().clickable(enabled = isAdvancedMode && !isDraggingThis) { isExpanded = !isExpanded },
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isAdvancedMode) {
                    // Drag Grip Handle
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent)
                            .pointerInput(provider.name) {
                                detectDragGestures(
                                    onDragStart = { currentOnDragStart() },
                                    onDragEnd = { currentOnDragEnd() },
                                    onDragCancel = { currentOnDragCancel() },
                                ) { change, dragAmount ->
                                    change.consume()
                                    currentOnDragDelta(dragAmount.y)
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DragIndicator,
                            contentDescription = "Hold and drag to reorder",
                            tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                Text(
                    text = "${index + 1}.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 10.dp, start = 4.dp),
                )

                if (iconUrl != null) {
                    coil3.compose.AsyncImage(
                        model = iconUrl,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                } else {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Extension, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (isAdvancedMode) {
                        Text("Catalogs: ${provider.mainPage.size - disabledCatalogs.size}/${provider.mainPage.size} active", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (isAdvancedMode) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 4.dp),
                    ) {
                        IconButton(onClick = onMoveUp, enabled = index > 0, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = onMoveDown, enabled = index < totalActive - 1, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Expand",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }

                IconButton(onClick = onRemove, modifier = Modifier.background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f), RoundedCornerShape(50))) {
                    Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
                }
            }

        // Expandable Catalogs Section
        if (isExpanded && isAdvancedMode) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text("Toggle Catalogs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(8.dp))

                val sortedCatalogs = remember(provider.mainPage, orderedCatalogs) {
                    if (orderedCatalogs.isNotEmpty()) {
                        val orderMap = orderedCatalogs.withIndex().associate { it.value to it.index }
                        provider.mainPage.sortedBy { orderMap[it.name] ?: Int.MAX_VALUE }
                    } else {
                        provider.mainPage
                    }
                }

                if (sortedCatalogs.isEmpty()) {
                    Text("No catalogs available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    sortedCatalogs.forEachIndexed { catIdx, catalog ->
                        val isEnabled = catalog.name !in disabledCatalogs
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onToggleCatalog(catalog.name, !isEnabled) }
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${catIdx + 1}. ${catalog.name}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(50))
                                    .padding(horizontal = 4.dp),
                            ) {
                                IconButton(
                                    onClick = { onMoveCatalog(catIdx, catIdx - 1) },
                                    enabled = catIdx > 0,
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(14.dp))
                                }
                                IconButton(
                                    onClick = { onMoveCatalog(catIdx, catIdx + 1) },
                                    enabled = catIdx < sortedCatalogs.lastIndex,
                                    modifier = Modifier.size(24.dp),
                                ) {
                                    Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(14.dp))
                                }
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { onToggleCatalog(catalog.name, it) },
                            )
                        }
                    }
                }
            }
        }
    }
}
}
