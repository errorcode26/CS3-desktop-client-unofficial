package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DragIndicator
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

@Composable
internal fun HomeCatalogCustomizationView(
    provider: MainAPI,
    disabledCatalogs: Set<String>,
    orderedCatalogs: List<String>,
    onToggleCatalog: (catalogName: String, isEnabled: Boolean) -> Unit,
    onMoveCatalog: (fromIndex: Int, toIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (provider.mainPage.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No catalogs available for this provider.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val sortedCatalogs = remember(provider.mainPage, orderedCatalogs) {
        if (orderedCatalogs.isNotEmpty()) {
            val orderMap = orderedCatalogs.withIndex().associate { it.value to it.index }
            provider.mainPage.sortedBy { orderMap[it.name] ?: Int.MAX_VALUE }
        } else {
            provider.mainPage
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
    } else {
        catalogDragInitialIndex
    }

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
        } else {
            fromIdx
        }
        draggingCatalogKey = null
        catalogDragAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in currentSortedCatalogs.indices && toIdx in currentSortedCatalogs.indices) {
            onMoveCatalog(fromIdx, toIdx)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(sortedCatalogs, key = { index, cat -> "${provider.name}_${cat.name}_${cat.data}_$index" }) { index, catalog ->
            val catalogKey = "${provider.name}_${catalog.name}_${catalog.data}_$index"
            val isEnabled = catalog.name !in disabledCatalogs
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
                            onClick = { onMoveCatalog(index, index - 1) },
                            enabled = index > 0,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", modifier = Modifier.size(16.dp))
                        }
                        IconButton(
                            onClick = { onMoveCatalog(index, index + 1) },
                            enabled = index < sortedCatalogs.lastIndex,
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", modifier = Modifier.size(16.dp))
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Switch(
                        checked = isEnabled,
                        onCheckedChange = { onToggleCatalog(catalog.name, it) },
                    )
                }
            }
        }
    }
}
