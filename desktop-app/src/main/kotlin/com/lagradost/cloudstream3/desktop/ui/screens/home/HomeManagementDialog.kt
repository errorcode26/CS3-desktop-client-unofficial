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
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.ui.components.CategoryFilterChips
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog

@Composable
fun HomeManagementDialog(
    show: Boolean,
    allProviders: List<MainAPI>,
    activeProviders: List<String>,
    disabledCatalogs: Map<String, Set<String>>,
    pluginIcons: Map<String, String>,
    onDismissRequest: () -> Unit,
    onSetSingleProvider: (String) -> Unit,
    onToggleProviderActive: (String, Boolean) -> Unit,
    onMoveProvider: (Int, Int) -> Unit,
    onToggleCatalog: (String, String, Boolean) -> Unit,
) {
    var isAdvancedMode by remember { mutableStateOf(activeProviders.size > 1) }
    var catalogProvider by remember { mutableStateOf<MainAPI?>(null) }
    var providerTypeFilter by remember { mutableStateOf(emptySet<TvType>()) }

    fun fuzzyMatchIcon(providerName: String): String? {
        val pName = providerName.lowercase().replace(Regex("[^a-z0-9]"), "").replace("provider", "").replace("plugin", "")
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
                            "Enable or disable specific catalogs."
                        } else if (isAdvancedMode) {
                            "Mix, reorder, and customize multiple providers."
                        } else {
                            "Choose a provider to display on your Home Screen."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (catalogProvider == null) {
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
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 350.dp),
                            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            items(p.mainPage.size) { i ->
                                val catalog = p.mainPage[i]
                                val isEnabled = catalog.name !in provDisabled
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isEnabled) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant)
                                        .clickable { onToggleCatalog(p.name, catalog.name, !isEnabled) }
                                        .padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Text(
                                        text = catalog.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Switch(checked = isEnabled, onCheckedChange = { onToggleCatalog(p.name, catalog.name, it) })
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
                                        onSetSingleProvider(pKey)
                                        onDismissRequest()
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
                                            )
                                        }
                                    }
                                    Text("${provider.mainPage.size} catalogs", style = MaterialTheme.typography.bodySmall, color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant)
                                }

                                IconButton(onClick = { catalogProvider = provider }) {
                                    Icon(Icons.Default.Tune, contentDescription = "Customize Catalogs", tint = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
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
                                    itemsIndexed(activeProviders, key = { _, name -> name }) { index, providerName ->
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
                                                        )
                                                    }
                                                }
                                                Text("${provider.mainPage.size} catalogs available", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            FilledTonalIconButton(onClick = { onToggleProviderActive(pKey, true) }) {
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
}

@Composable
private fun ActiveProviderItem(
    index: Int,
    totalActive: Int,
    provider: MainAPI,
    iconUrl: String?,
    disabledCatalogs: Set<String>,
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

                if (provider.mainPage.isEmpty()) {
                    Text("No catalogs available.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    provider.mainPage.forEach { catalog ->
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
                                text = catalog.name,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { onToggleCatalog(catalog.name, it) },
                                modifier = Modifier.padding(start = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
}
