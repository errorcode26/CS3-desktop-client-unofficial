package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine

@Composable
internal fun HomeAdvancedFeedView(
    allProviders: List<MainAPI>,
    activeProviders: List<String>,
    disabledCatalogs: Map<String, Set<String>>,
    orderedCatalogs: Map<String, List<String>>,
    providerTypeFilter: Set<TvType>,
    pluginIcons: Map<String, String>,
    isAdvancedMode: Boolean,
    onMoveProvider: (fromIndex: Int, toIndex: Int) -> Unit,
    onToggleProviderActive: (pKey: String, isActive: Boolean) -> Unit,
    onToggleCatalog: (providerName: String, catalogName: String, isEnabled: Boolean) -> Unit,
    onMoveCatalog: (providerName: String, fromIndex: Int, toIndex: Int) -> Unit,
    onAddProvider: (provider: MainAPI, pKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left Column: Active Providers Feed
        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
            Text("Active Providers Feed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))

            if (activeProviders.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
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
                } else {
                    providerDragInitialIndex
                }

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
                    } else {
                        fromIdx
                    }
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
                                iconUrl = fuzzyMatchPluginIcon(providerName, pluginIcons),
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
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
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
                            val url = fuzzyMatchPluginIcon(provider.name, pluginIcons)
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
                                        } catch (_: Exception) {
                                            null
                                        }
                                    }
                                } else {
                                    null
                                }

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
                                onAddProvider(provider, pKey)
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
