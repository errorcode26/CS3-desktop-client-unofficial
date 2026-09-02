package com.lagradost.cloudstream3.desktop.ui.screens.settings.appearance

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Refresh
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
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsDropdownItem
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsGroupCard
import com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsToggleItem
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey

@Composable
fun SettingsNavDockScreen() {
    val navigationStyle by AppearanceConfig.navigationStyle.collectAsState()
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val globalUiScale by AppearanceConfig.globalUiScale.collectAsState()
    val dockOrder by AppearanceConfig.dockItemOrder.collectAsState()
    val dockDisabled by AppearanceConfig.dockDisabledItems.collectAsState()
    val topBarProviderStyle by AppearanceConfig.topBarProviderStyle.collectAsState()
    val topBarShowProfile by AppearanceConfig.topBarShowProfile.collectAsState()
    val topBarShowProfileName by AppearanceConfig.topBarShowProfileName.collectAsState()
    val clockMode by AppearanceConfig.clockMode.collectAsState()
    val clockTimeFormat by AppearanceConfig.clockTimeFormat.collectAsState()
    val clockDateFormat by AppearanceConfig.clockDateFormat.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Navigation & Dock Placement") {
            SettingsDropdownItem(
                label = "Navigation Dock Style",
                subtitle = "Switch between floating island dock and seamless edge navbar",
                options = listOf(
                    com.lagradost.cloudstream3.desktop.ui.theme.NavigationStyle.FLOATING_DOCK to "Floating Dock (Island)",
                    com.lagradost.cloudstream3.desktop.ui.theme.NavigationStyle.SEAMLESS_BAR to "Seamless Navigation Bar (Edge)",
                ),
                currentValue = navigationStyle,
                onSelectionChanged = { AppearanceConfig.setNavigationStyle(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsDropdownItem(
                label = "Dock & Sidebar Position",
                subtitle = "Anchor the main navigation dock to any side of your display",
                options = listOf(
                    DockPosition.LEFT to "Left Sidebar (Default)",
                    DockPosition.TOP to "Top Bar (Header)",
                    DockPosition.BOTTOM to "Bottom Bar",
                    DockPosition.RIGHT to "Right Sidebar",
                ),
                currentValue = dockPosition,
                onSelectionChanged = { AppearanceConfig.setDockPosition(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            // Global UI Scale / Zoom
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Global UI Scale / Zoom", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Scale all windows and components (Ctrl + / Ctrl - to zoom, Ctrl 0 to reset)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "${(globalUiScale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (globalUiScale != 1.0f) {
                            OutlinedButton(
                                onClick = { AppearanceConfig.resetZoom() },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                            ) {
                                Text("Reset (100%)", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Slider(
                    value = globalUiScale,
                    onValueChange = { AppearanceConfig.setGlobalUiScale(it, notify = false) },
                    valueRange = 0.70f..1.80f,
                    steps = 10,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        SettingsGroupCard(title = "Dock Buttons & Drag Reorder") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Reorder Navigation Items", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Drag the handles (⠿) to rearrange buttons on your dock, or toggle optional tabs on/off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { AppearanceConfig.resetDockItemOrder() }) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset Order")
                }
            }

            DraggableDockOrderList(
                dockOrder = dockOrder,
                dockDisabled = dockDisabled,
            )
        }

        SettingsGroupCard(title = "Header & Clock Configuration") {
            SettingsDropdownItem(
                label = "Top Bar Provider Button Style",
                subtitle = "Choose between a compact 42dp icon-only button and a full badge with provider name",
                options = listOf(
                    com.lagradost.cloudstream3.desktop.ui.theme.TopBarProviderStyle.ICON_ONLY to "Icon Only (Clean)",
                    com.lagradost.cloudstream3.desktop.ui.theme.TopBarProviderStyle.ICON_AND_NAME to "Icon & Name",
                ),
                currentValue = topBarProviderStyle,
                onSelectionChanged = { AppearanceConfig.setTopBarProviderStyle(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Show Profile in Top Bar",
                subtitle = "Display active profile avatar and switcher in the top bar",
                checked = topBarShowProfile,
                onCheckedChange = { AppearanceConfig.setTopBarShowProfile(it) },
            )

            if (topBarShowProfile) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsToggleItem(
                    label = "Show Profile Name",
                    subtitle = "Display active profile name next to avatar",
                    checked = topBarShowProfileName,
                    onCheckedChange = { AppearanceConfig.setTopBarShowProfileName(it) },
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsDropdownItem(
                label = "Clock & Date Display Mode",
                subtitle = "Configure clock visibility in the top-right corner",
                options = listOf(
                    com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.HIDDEN to "Hidden",
                    com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.TIME_ONLY to "Time Only",
                    com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.DATE_ONLY to "Date Only",
                    com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.BOTH to "Time & Date",
                ),
                currentValue = clockMode,
                onSelectionChanged = { AppearanceConfig.setClockMode(it) },
            )

            if (clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.TIME_ONLY ||
                clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.BOTH
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Time Format",
                    subtitle = "Pattern used to format the clock",
                    options = listOf(
                        "hh:mm a" to "12-hour (09:30 AM)",
                        "HH:mm" to "24-hour (09:30)",
                        "hh:mm:ss a" to "12-hour with seconds",
                        "HH:mm:ss" to "24-hour with seconds",
                    ),
                    currentValue = clockTimeFormat,
                    onSelectionChanged = { AppearanceConfig.setClockTimeFormat(it) },
                )
            }

            if (clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.DATE_ONLY ||
                clockMode == com.lagradost.cloudstream3.desktop.ui.theme.ClockDisplayMode.BOTH
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Date Format",
                    subtitle = "Pattern used to format the date",
                    options = listOf(
                        "EEE, d MMM" to "Short (Sun, 30 Aug)",
                        "d MMMM yyyy" to "Full (30 August 2026)",
                        "yyyy-MM-dd" to "ISO (2026-08-30)",
                        "MM/dd/yyyy" to "US (08/30/2026)",
                        "dd/MM/yyyy" to "EU (30/08/2026)",
                    ),
                    currentValue = clockDateFormat,
                    onSelectionChanged = { AppearanceConfig.setClockDateFormat(it) },
                )
            }
        }
    }
}

@Composable
private fun DraggableDockOrderList(
    dockOrder: List<DockItemKey>,
    dockDisabled: Set<DockItemKey>,
) {
    var draggingDockKey by remember { mutableStateOf<DockItemKey?>(null) }
    var dragDockAccumulatedY by remember { mutableStateOf(0f) }
    var dragDockInitialIndex by remember { mutableStateOf(0) }
    var dockSlotHeightPx by remember { mutableStateOf(0f) }
    val fallbackSlotHeight = with(LocalDensity.current) { 54.dp.toPx() }
    val effectiveSlotHeight = if (dockSlotHeightPx > 0f) dockSlotHeightPx else fallbackSlotHeight

    val currentTargetIndex = if (draggingDockKey != null && effectiveSlotHeight > 0f) {
        (dragDockInitialIndex + kotlin.math.round(dragDockAccumulatedY / effectiveSlotHeight).toInt())
            .coerceIn(0, dockOrder.lastIndex)
    } else dragDockInitialIndex

    val currentDockOrder by rememberUpdatedState(dockOrder)
    val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
    val currentDragAccumulatedY by rememberUpdatedState(dragDockAccumulatedY)
    val currentDragInitialIndex by rememberUpdatedState(dragDockInitialIndex)

    val onDropDockItem by rememberUpdatedState {
        val fromIdx = currentDragInitialIndex
        val slotH = currentEffectiveSlotHeight
        val accY = currentDragAccumulatedY
        val toIdx = if (slotH > 0f) {
            (fromIdx + kotlin.math.round(accY / slotH).toInt())
                .coerceIn(0, currentDockOrder.lastIndex)
        } else fromIdx
        draggingDockKey = null
        dragDockAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in currentDockOrder.indices && toIdx in currentDockOrder.indices) {
            AppearanceConfig.moveDockItem(fromIdx, toIdx)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        dockOrder.forEachIndexed { index, item ->
            val isEnabled = item !in dockDisabled
            val isDraggingThis = draggingDockKey == item

            val targetShiftY = when {
                isDraggingThis -> dragDockAccumulatedY
                draggingDockKey != null && dragDockInitialIndex < currentTargetIndex && index in (dragDockInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                draggingDockKey != null && dragDockInitialIndex > currentTargetIndex && index in currentTargetIndex until dragDockInitialIndex -> effectiveSlotHeight
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
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                } else if (isEnabled) {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                },
                border = BorderStroke(
                    if (isDraggingThis) 1.5.dp else 0.5.dp,
                    if (isDraggingThis) MaterialTheme.colorScheme.primary else if (isEnabled) MaterialTheme.colorScheme.outline.copy(alpha = 0.2f) else Color.Transparent,
                ),
                shadowElevation = elevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        if (coordinates.size.height > 0 && dockSlotHeightPx == 0f) {
                            dockSlotHeightPx = coordinates.size.height.toFloat() + 6f
                        }
                    }
                    .zIndex(if (isDraggingThis) 100f else 1f)
                    .scale(scale)
                    .graphicsLayer {
                        translationY = if (isDraggingThis) dragDockAccumulatedY else animatedShiftY
                    },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Drag grip handle
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                            .pointerInput(item) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingDockKey = item
                                        dragDockInitialIndex = currentDockOrder.indexOf(item)
                                        dragDockAccumulatedY = 0f
                                    },
                                    onDragEnd = { onDropDockItem() },
                                    onDragCancel = {
                                        draggingDockKey = null
                                        dragDockAccumulatedY = 0f
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    dragDockAccumulatedY += dragAmount.y
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = "Drag to reorder",
                            tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = item.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                        )
                        if (item.isRequired) {
                            Text("(Always Active)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        } else {
                            Text(item.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    if (!item.isRequired) {
                        Switch(
                            checked = isEnabled,
                            onCheckedChange = { AppearanceConfig.toggleDockItem(item, it) },
                            modifier = Modifier.scale(0.85f),
                        )
                    }
                }
            }
        }
    }
}
