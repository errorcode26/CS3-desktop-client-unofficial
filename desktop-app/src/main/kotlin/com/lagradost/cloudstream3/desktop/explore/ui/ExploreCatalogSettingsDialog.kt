package com.lagradost.cloudstream3.desktop.explore.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
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
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.explore.settings.ExploreCatalogSettingsManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme

@Composable
fun ExploreCatalogSettingsDialog(
    show: Boolean,
    catalogs: List<ManifestCatalogDescriptor>,
    onDismissRequest: () -> Unit,
) {
    if (!show) return

    val theme = LocalDesktopTheme.current
    val isLightMode = theme.isLightMode
    val preferences by ExploreCatalogSettingsManager.preferences.collectAsState()

    // Filter strictly to user-installed add-ons
    val userCatalogs = remember(catalogs) { catalogs.filter { !it.isBuiltIn } }

    val sortedCatalogs = remember(userCatalogs, preferences) {
        userCatalogs.sortedBy { descriptor ->
            preferences[descriptor.key]?.order ?: Int.MAX_VALUE
        }
    }

    val orderedKeys = remember(sortedCatalogs) { sortedCatalogs.map { it.key } }

    // Drag-and-drop reordering state
    var draggingCatalogKey by remember { mutableStateOf<String?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    var dragInitialIndex by remember { mutableStateOf(0) }
    var slotHeightPx by remember { mutableStateOf(0f) }
    val fallbackSlotHeight = with(LocalDensity.current) { 56.dp.toPx() }
    val effectiveSlotHeight = if (slotHeightPx > 0f) slotHeightPx else fallbackSlotHeight

    val currentTargetIndex = if (draggingCatalogKey != null && effectiveSlotHeight > 0f) {
        (dragInitialIndex + kotlin.math.round(dragAccumulatedY / effectiveSlotHeight).toInt())
            .coerceIn(0, sortedCatalogs.lastIndex)
    } else {
        dragInitialIndex
    }

    val currentSortedCatalogs by rememberUpdatedState(sortedCatalogs)
    val currentOrderedKeys by rememberUpdatedState(orderedKeys)
    val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
    val currentDragAccumulatedY by rememberUpdatedState(dragAccumulatedY)
    val currentDragInitialIndex by rememberUpdatedState(dragInitialIndex)

    val onDropCatalog by rememberUpdatedState {
        val fromIdx = currentDragInitialIndex
        val slotH = currentEffectiveSlotHeight
        val accY = currentDragAccumulatedY
        val toIdx = if (slotH > 0f) {
            (fromIdx + kotlin.math.round(accY / slotH).toInt())
                .coerceIn(0, currentSortedCatalogs.lastIndex)
        } else {
            fromIdx
        }
        draggingCatalogKey = null
        dragAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in currentOrderedKeys.indices && toIdx in currentOrderedKeys.indices) {
            ExploreCatalogSettingsManager.moveByIndex(currentOrderedKeys, fromIdx, toIdx)
        }
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismissRequest,
        modifier = Modifier
            .fillMaxWidth(0.60f)
            .fillMaxHeight(0.75f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Column {
                        Text(
                            text = "Customize Add-on Shelves",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = theme.TextPrimary,
                        )
                        Text(
                            text = "Reorder or toggle visibility of your installed add-on catalogs.",
                            fontSize = 12.5.sp,
                            color = theme.TextMuted,
                        )
                    }
                }

                IconButton(onClick = onDismissRequest) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = theme.TextMuted,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action row: Reset to defaults & count
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${sortedCatalogs.size} Add-on Shelves",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = theme.TextMuted,
                )

                TextButton(
                    onClick = {
                        ExploreCatalogSettingsManager.resetToDefaults(userCatalogs)
                    },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Reset Order",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // List of Add-on Shelves
            if (sortedCatalogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No user-installed add-on catalogs found.",
                        color = theme.TextMuted,
                        fontSize = 13.sp,
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    sortedCatalogs.forEachIndexed { index, catalog ->
                        val pref = preferences[catalog.key]
                        val isEnabled = pref?.enabled != false
                        val isDraggingThis = draggingCatalogKey == catalog.key

                        val targetShiftY = when {
                            isDraggingThis -> dragAccumulatedY
                            draggingCatalogKey != null && dragInitialIndex < currentTargetIndex && index in (dragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                            draggingCatalogKey != null && dragInitialIndex > currentTargetIndex && index in currentTargetIndex until dragInitialIndex -> effectiveSlotHeight
                            else -> 0f
                        }
                        val animatedShiftY by animateFloatAsState(
                            targetValue = targetShiftY,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        )

                        val elevation by animateDpAsState(if (isDraggingThis) 14.dp else 0.dp)
                        val scale by animateFloatAsState(if (isDraggingThis) 1.02f else 1.0f)

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isDraggingThis) {
                                if (isLightMode) theme.SurfaceElevated else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                            } else if (isLightMode) {
                                theme.SurfaceElevated
                            } else {
                                Color.White.copy(alpha = 0.04f)
                            },
                            border = BorderStroke(
                                if (isDraggingThis) 1.5.dp else 0.5.dp,
                                if (isDraggingThis) {
                                    MaterialTheme.colorScheme.primary
                                } else if (isLightMode) {
                                    theme.Divider
                                } else {
                                    Color.White.copy(alpha = 0.08f)
                                },
                            ),
                            shadowElevation = elevation,
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { coordinates ->
                                    if (coordinates.size.height > 0 && slotHeightPx == 0f) {
                                        slotHeightPx = coordinates.size.height.toFloat() + 8f
                                    }
                                }
                                .zIndex(if (isDraggingThis) 100f else 1f)
                                .scale(scale)
                                .graphicsLayer {
                                    translationY = if (isDraggingThis) dragAccumulatedY else animatedShiftY
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                // Drag grip handle (hold and drag)
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (isDraggingThis) {
                                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                                            } else if (isLightMode) {
                                                theme.Divider.copy(alpha = 0.4f)
                                            } else {
                                                Color.White.copy(alpha = 0.06f)
                                            },
                                        )
                                        .pointerInput(catalog.key) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggingCatalogKey = catalog.key
                                                    dragInitialIndex = currentSortedCatalogs.indexOfFirst { it.key == catalog.key }
                                                    dragAccumulatedY = 0f
                                                },
                                                onDragEnd = { onDropCatalog() },
                                                onDragCancel = {
                                                    draggingCatalogKey = null
                                                    dragAccumulatedY = 0f
                                                },
                                            ) { change, dragAmount ->
                                                change.consume()
                                                dragAccumulatedY += dragAmount.y
                                            }
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DragHandle,
                                        contentDescription = "Hold and drag to reorder",
                                        tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else theme.TextMuted,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                // Discrete Up/Down arrow buttons
                                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                    IconButton(
                                        onClick = {
                                            if (index > 0) {
                                                ExploreCatalogSettingsManager.moveByIndex(orderedKeys, index, index - 1)
                                            }
                                        },
                                        enabled = index > 0,
                                        modifier = Modifier.size(26.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "Move Up",
                                            tint = if (index > 0) theme.TextPrimary else theme.TextMuted.copy(alpha = 0.3f),
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            if (index < sortedCatalogs.size - 1) {
                                                ExploreCatalogSettingsManager.moveByIndex(orderedKeys, index, index + 1)
                                            }
                                        },
                                        enabled = index < sortedCatalogs.size - 1,
                                        modifier = Modifier.size(26.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = "Move Down",
                                            tint = if (index < sortedCatalogs.size - 1) theme.TextPrimary else theme.TextMuted.copy(alpha = 0.3f),
                                            modifier = Modifier.size(15.dp),
                                        )
                                    }
                                }

                                // Catalog info
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = catalog.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isEnabled) theme.TextPrimary else theme.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${catalog.addonName}  •  ${catalog.type.uppercase()}",
                                        fontSize = 11.5.sp,
                                        color = theme.TextMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }

                                // Visibility switch
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        ExploreCatalogSettingsManager.setEnabled(catalog.key, checked)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.Black,
                                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    ),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Footer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Button(
                    onClick = onDismissRequest,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(
                        text = "Done",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
