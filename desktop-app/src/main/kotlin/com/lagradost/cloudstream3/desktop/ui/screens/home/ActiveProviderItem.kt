package com.lagradost.cloudstream3.desktop.ui.screens.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.lagradost.cloudstream3.MainAPI

@Composable
internal fun ActiveProviderItem(
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
