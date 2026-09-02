package com.lagradost.cloudstream3.desktop.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.cloudstream3.desktop.ui.badges.CardMetadataConfig
import com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager
import com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.ui.theme.DockItemKey
import com.lagradost.cloudstream3.desktop.ui.theme.ThemeMode
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.SearchQuality
import com.lagradost.common.storage.WatchHistory

// ─────────────────────────────────────────────────────────────────────────────
// 1. MAIN APPEARANCE & THEME HUB SCREEN (Clean Dashboard - No Infinite Scroll)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsAppearanceScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Card 1: Navigation & Dock
        AppearanceHubCard(
            icon = Icons.Default.Dashboard,
            title = "Navigation & Dock",
            subtitle = "Dock position, floating island vs edge navbar, draggable dock button reordering, UI zoom scale, and clock formats.",
            badge = "${dockPosition.label} Dock",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_NAV_DOCK) },
        )

        // Card 2: Theme, Colors & Wallpaper
        AppearanceHubCard(
            icon = Icons.Default.Palette,
            title = "Theme, Colors & Wallpaper",
            subtitle = "Light/Dark mode, AMOLED pure black, curated accent swatches, custom hex color picker, typography fonts, ambient glow, and custom wallpaper.",
            badge = if (isLightMode) "Light Theme" else if (amoledMode) "AMOLED Black" else "$themeAccent Accent",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_THEME_WALLPAPER) },
        )

        // Card 3: Posters & Provider Branding
        AppearanceHubCard(
            icon = Icons.Default.Wallpaper,
            title = "Posters & Provider Branding",
            subtitle = "Provider badges on cards, auto-clean messy release titles, 4K/1080p quality tags, SUB/DUB badges, rating star pills, and Poster Workshop Studio.",
            badge = providerBadgeDisplayMode.label,
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_POSTERS_BADGES) },
        )

        // Card 4: Home Feed & Cinema
        AppearanceHubCard(
            icon = Icons.Default.PlayArrow,
            title = "Home Feed & Cinema",
            subtitle = "Hero spotlight trending slider, banner layout styles (Cinema/Fullscreen/Filmstrip), auto-slide transitions, and Continue Watching row.",
            badge = if (heroEnabled) "Hero Active" else "Hero Off",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.APPEARANCE_HOME_FEED) },
        )

        // Card 5: Details Page & Modular Sections
        AppearanceHubCard(
            icon = Icons.Default.Edit,
            title = "Details Page & Modular Sections",
            subtitle = "Modular sections drag reordering, atmospheric backdrop blur & softening, unreleased episode locking, and anti-spoiler mode.",
            badge = "Modular Layout",
            onClick = { onNavigateToSubScreen(SettingsSubScreen.DETAILS_LAYOUT) },
        )
    }
}

@Composable
private fun AppearanceHubCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
    onClick: () -> Unit,
) {
    val theme = com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable { onClick() },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f).padding(end = 16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (badge != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
                    ) {
                        Text(
                            text = badge,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 2. SUB-SCREEN: NAVIGATION & DOCK (With Drag-and-Drop Reordering)
// ─────────────────────────────────────────────────────────────────────────────
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

// ─────────────────────────────────────────────────────────────────────────────
// 3. SUB-SCREEN: THEME, COLORS & WALLPAPER
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsThemeWallpaperScreen() {
    val isLightMode by AppearanceConfig.isLightMode.collectAsState()
    val amoledMode by AppearanceConfig.amoledMode.collectAsState()
    val themeAccent by AppearanceConfig.themeAccent.collectAsState()
    val customThemeAccent by AppearanceConfig.customThemeAccent.collectAsState()
    val appThemeBackground by AppearanceConfig.appThemeBackground.collectAsState()
    val customAppThemeBackground by AppearanceConfig.customAppThemeBackground.collectAsState()
    val selectedFont by AppearanceConfig.selectedFont.collectAsState()
    val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
    val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
    val backgroundGradientEnabled by AppearanceConfig.backgroundGradientEnabled.collectAsState()
    val backgroundGradientType by AppearanceConfig.backgroundGradientType.collectAsState()
    val backgroundGradientIntensity by AppearanceConfig.backgroundGradientIntensity.collectAsState()
    val bgImagePath by AppearanceConfig.backgroundImagePath.collectAsState()
    val bgImageBlur by AppearanceConfig.backgroundImageBlur.collectAsState()
    val bgImageBrightness by AppearanceConfig.backgroundImageBrightness.collectAsState()
    val bgImageOpacity by AppearanceConfig.backgroundImageOpacity.collectAsState()
    val bgImageVignetteEnabled by AppearanceConfig.backgroundImageVignetteEnabled.collectAsState()
    val bgImageVignetteIntensity by AppearanceConfig.backgroundImageVignetteIntensity.collectAsState()
    val bgImageTintEnabled by AppearanceConfig.backgroundImageTintEnabled.collectAsState()
    val bgImageTintAlpha by AppearanceConfig.backgroundImageTintAlpha.collectAsState()
    val uiCardOpacity by AppearanceConfig.uiCardOpacity.collectAsState()

    val availableFonts by CustomFontManager.availableFonts.collectAsState()
    val userInstalledFonts by CustomFontManager.userInstalledFonts.collectAsState()
    var fontInstallFeedback by remember { mutableStateOf<String?>(null) }

    var showCustomAccentDialog by remember { mutableStateOf(false) }
    var showCustomBgDialog by remember { mutableStateOf(false) }

    val presetAccents = remember {
        listOf(
            "Purple" to Color(0xFF7C6BFF),
            "Blue" to Color(0xFF3B82F6),
            "Green" to Color(0xFF10B981),
            "Red" to Color(0xFFEF4444),
            "Orange" to Color(0xFFF59E0B),
        )
    }

    val curatedAccentColors = remember {
        listOf(
            "Neon Pink" to "#FF007F",
            "Cyber Cyan" to "#00F0FF",
            "Emerald" to "#10B981",
            "Electric Violet" to "#8B5CF6",
            "Sunset Gold" to "#F59E0B",
            "Crimson" to "#EF4444",
            "Mint" to "#6EE7B7",
            "Lavender" to "#C084FC",
        )
    }

    val curatedBackgroundColors = remember {
        listOf(
            "Deep Carbon" to "#0E0E10",
            "Dark Slate" to "#18181B",
            "Midnight Navy" to "#0B1120",
            "Dark Mocha" to "#1E1815",
            "Deep Forest" to "#0F1714",
            "Deep Amethyst" to "#130C1C",
        )
    }

    val scrollState = rememberScrollState()

    CustomColorStudioDialog(
        show = showCustomAccentDialog,
        title = "Custom Accent Color Studio",
        initialHex = customThemeAccent,
        defaultHex = "#7C6BFF",
        curatedColors = curatedAccentColors,
        onDismiss = { showCustomAccentDialog = false },
        onColorConfirmed = { hex ->
            AppearanceConfig.setCustomThemeAccent(hex)
            AppearanceConfig.setThemeAccent("Custom")
        },
    )

    CustomColorStudioDialog(
        show = showCustomBgDialog,
        title = "Custom Background Tone Studio",
        initialHex = customAppThemeBackground,
        defaultHex = "#0C0C16",
        curatedColors = curatedBackgroundColors,
        onDismiss = { showCustomBgDialog = false },
        onColorConfirmed = { hex ->
            AppearanceConfig.setCustomAppThemeBackground(hex)
            AppearanceConfig.setAppThemeBackground("Custom")
        },
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "App Mode & Palette") {
            val currentThemeMode = when {
                isLightMode -> ThemeMode.LIGHT
                amoledMode -> ThemeMode.AMOLED
                else -> ThemeMode.DARK
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Theme Mode",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Select base visual style and contrast profile",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ThemeMode.entries.forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        Surface(
                            onClick = { AppearanceConfig.setThemeMode(mode) },
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            border = BorderStroke(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            ),
                            modifier = Modifier.weight(1f).height(46.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                            ) {
                                Icon(
                                    imageVector = when (mode) {
                                        ThemeMode.LIGHT -> Icons.Default.LightMode
                                        ThemeMode.DARK -> Icons.Default.DarkMode
                                        ThemeMode.AMOLED -> Icons.Default.Contrast
                                    },
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Accent Color", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text("Primary tint used across buttons, indicators, and focus highlights", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp),
                ) {
                    presetAccents.forEach { (name, color) ->
                        val isSelected = themeAccent == name
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(
                                    width = if (isSelected) 3.dp else 1.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                    shape = CircleShape,
                                )
                                .clickable { AppearanceConfig.setThemeAccent(name) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isSelected) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                            }
                        }
                    }

                    Spacer(Modifier.width(4.dp))

                    val isCustomAccent = themeAccent == "Custom"
                    val customColor = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customThemeAccent, Color(0xFF7C6BFF))

                    Surface(
                        onClick = {
                            showCustomAccentDialog = true
                        },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCustomAccent) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        border = BorderStroke(
                            width = if (isCustomAccent) 1.5.dp else 1.dp,
                            color = if (isCustomAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        ),
                        modifier = Modifier.height(38.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(horizontal = 12.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clip(CircleShape)
                                    .background(customColor)
                                    .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                            )
                            Text(
                                text = if (isCustomAccent) "Custom ($customThemeAccent)" else "Custom Color Picker...",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isCustomAccent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCustomAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "Edit Custom Color",
                                tint = if (isCustomAccent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            if (currentThemeMode == ThemeMode.AMOLED) {
                SettingsDropdownItem(
                    label = "App Background Palette",
                    subtitle = "Locked to pure #000000 black in AMOLED mode",
                    options = listOf("Pure Black" to "Pure Black (#000000)"),
                    currentValue = "Pure Black",
                    enabled = false,
                    onSelectionChanged = { },
                )
            } else {
                SettingsDropdownItem(
                    label = "App Background Palette",
                    subtitle = "Base canvas color tone across all screens",
                    options = listOf(
                        "Navy" to "Deep Navy",
                        "Midnight" to "Midnight Blue",
                        "Slate" to "Dark Slate",
                        "Mocha" to "Warm Mocha",
                        "Pure Black" to "Pure Black (#000000)",
                        "Custom" to "Custom Hex Tint",
                    ),
                    currentValue = appThemeBackground,
                    onSelectionChanged = {
                        AppearanceConfig.setAppThemeBackground(it)
                        if (it == "Custom") {
                            showCustomBgDialog = true
                        }
                    },
                )

                if (appThemeBackground == "Custom") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            onClick = { showCustomBgDialog = true },
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.height(36.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 12.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(customAppThemeBackground, Color(0xFF0C0C16)))
                                        .border(1.dp, Color.White.copy(alpha = 0.4f), CircleShape),
                                )
                                Text(
                                    text = "Custom Background ($customAppThemeBackground)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Icon(
                                    Icons.Default.Edit,
                                    contentDescription = "Edit Background Color",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SettingsGroupCard(title = "App Typography & Custom Font Studio") {
            SettingsDropdownItem(
                label = "App Typography & Font",
                subtitle = "Font family applied globally across all titles, cards, and UI components",
                options = availableFonts.map { it to it },
                currentValue = selectedFont,
                onSelectionChanged = { AppearanceConfig.setSelectedFont(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        try {
                            val chosenFile = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                title = "Select Font File (.ttf, .otf, .woff)",
                                allowedExtensions = listOf("ttf", "otf", "woff"),
                                category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.FONT,
                            )
                            if (chosenFile != null) {
                                val result = CustomFontManager.installFont(chosenFile)
                                result.onSuccess { familyName ->
                                    AppearanceConfig.setSelectedFont(familyName)
                                    fontInstallFeedback = "Successfully installed & applied font: $familyName"
                                }.onFailure { err ->
                                    fontInstallFeedback = "Font installation failed: ${err.message}"
                                }
                            }
                        } catch (e: Exception) {
                            fontInstallFeedback = "Error opening file picker: ${e.message}"
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Install Custom Font (.ttf / .otf)")
                }

                OutlinedButton(
                    onClick = { CustomFontManager.openFontsDirectory() },
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Open Fonts Folder")
                }
            }

            if (fontInstallFeedback != null) {
                Text(
                    text = fontInstallFeedback!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (fontInstallFeedback!!.startsWith("Success")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            if (userInstalledFonts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "User-Installed Fonts (${userInstalledFonts.size})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    userInstalledFonts.forEach { fontName ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = fontName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = "The quick brown fox jumps over the lazy dog 1234567890",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        CustomFontManager.deleteFont(fontName)
                                        if (selectedFont == fontName) {
                                            AppearanceConfig.setSelectedFont("Inter")
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = "Delete Font",
                                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SettingsGroupCard(title = "Atmospheric Canvas & Background Gradient") {
            SettingsToggleItem(
                label = "Canvas Background Gradient",
                subtitle = "Renders dynamic depth gradient across application backgrounds",
                checked = backgroundGradientEnabled,
                onCheckedChange = { AppearanceConfig.setBackgroundGradientEnabled(it) },
            )

            if (backgroundGradientEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsDropdownItem(
                    label = "Gradient Geometry",
                    subtitle = "Style and projection of the background depth gradient",
                    options = listOf(
                        "Radial" to "Radial Ambient Glow (Cinematic Center)",
                        "Linear" to "Linear Horizon Flow (Top to Bottom)",
                    ),
                    currentValue = backgroundGradientType,
                    onSelectionChanged = { AppearanceConfig.setBackgroundGradientType(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSliderItem(
                    label = "Gradient Depth Intensity",
                    subtitle = "${(backgroundGradientIntensity * 100).toInt()}% depth intensity",
                    value = backgroundGradientIntensity,
                    onValueChange = { AppearanceConfig.setBackgroundGradientIntensity(it) },
                    valueRange = 0.10f..1.00f,
                    steps = 17,
                )
            }
        }

        SettingsGroupCard(title = "Atmospheric Ambient Glow Lighting") {
            SettingsToggleItem(
                label = "Ambient Glow (Cinematic Backlight)",
                subtitle = "Renders soft adaptive atmospheric lighting behind active hero content",
                checked = ambientGlowEnabled,
                onCheckedChange = { AppearanceConfig.setAmbientGlowEnabled(it) },
            )

            if (ambientGlowEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Ambient Glow Intensity",
                    subtitle = "${(ambientGlowIntensity * 100).toInt()}% intensity",
                    value = ambientGlowIntensity,
                    onValueChange = { AppearanceConfig.setAmbientGlowIntensity(it) },
                    valueRange = 0.05f..0.60f,
                    steps = 11,
                )
            }
        }

        SettingsGroupCard(title = "Custom Background Wallpaper") {
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                        Text("Wallpaper Image", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text("Set a custom local image as app background with blur and tint controls", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (bgImagePath.isNotBlank()) {
                        OutlinedButton(
                            onClick = { AppearanceConfig.clearBackgroundImage() },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Clear Wallpaper", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                if (bgImagePath.isNotBlank() && java.io.File(bgImagePath).exists()) {
                    val wallpaperFile = remember(bgImagePath) { java.io.File(bgImagePath) }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth().height(160.dp),
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            coil3.compose.AsyncImage(
                                model = coil3.request.ImageRequest.Builder(coil3.compose.LocalPlatformContext.current)
                                    .data(wallpaperFile)
                                    .size(coil3.size.Size(1280, 720))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = "Active Wallpaper",
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        androidx.compose.ui.graphics.Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)),
                                            startY = 50f,
                                        )
                                    ),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = wallpaperFile.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = wallpaperFile.parent ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Button(
                                    onClick = {
                                        val chosen = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                            title = "Choose Wallpaper Image",
                                            allowedExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp"),
                                            category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.WALLPAPER,
                                        )
                                        if (chosen != null) {
                                            AppearanceConfig.setBackgroundImagePath(chosen.absolutePath)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                ) {
                                    Text("Change Image")
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(
                                Icons.Default.Wallpaper,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Text(
                                text = "No Custom Wallpaper Active",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "Select a high-resolution image to use as your desktop app canvas backdrop",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Button(
                                onClick = {
                                    val chosen = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.open(
                                        title = "Choose Wallpaper Image",
                                        allowedExtensions = listOf("jpg", "jpeg", "png", "webp", "bmp"),
                                        category = com.lagradost.cloudstream3.desktop.utils.NativeFileDialog.Category.WALLPAPER,
                                    )
                                    if (chosen != null) {
                                        AppearanceConfig.setBackgroundImagePath(chosen.absolutePath)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.padding(top = 4.dp),
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Choose Wallpaper Image (.png, .jpg, .webp)")
                            }
                        }
                    }
                }

                if (bgImagePath.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SettingsSliderItem(
                        label = "Wallpaper Blur",
                        subtitle = "${bgImageBlur.toInt()} dp blur",
                        value = bgImageBlur,
                        onValueChange = { AppearanceConfig.setBackgroundImageBlur(it) },
                        valueRange = 0f..50f,
                        steps = 10,
                    )

                    SettingsSliderItem(
                        label = "Wallpaper Brightness",
                        subtitle = "${(bgImageBrightness * 100).toInt()}% brightness",
                        value = bgImageBrightness,
                        onValueChange = { AppearanceConfig.setBackgroundImageBrightness(it) },
                        valueRange = 0.05f..1.0f,
                        steps = 19,
                    )

                    SettingsSliderItem(
                        label = "Wallpaper Opacity",
                        subtitle = "${(bgImageOpacity * 100).toInt()}% opacity",
                        value = bgImageOpacity,
                        onValueChange = { AppearanceConfig.setBackgroundImageOpacity(it) },
                        valueRange = 0.1f..1.0f,
                        steps = 9,
                    )

                    SettingsToggleItem(
                        label = "Wallpaper Vignette",
                        subtitle = "Darkens image edges for a focused cinema look",
                        checked = bgImageVignetteEnabled,
                        onCheckedChange = { AppearanceConfig.setBackgroundImageVignetteEnabled(it) },
                    )

                    if (bgImageVignetteEnabled) {
                        SettingsSliderItem(
                            label = "Vignette Intensity",
                            subtitle = "${(bgImageVignetteIntensity * 100).toInt()}% strength",
                            value = bgImageVignetteIntensity,
                            onValueChange = { AppearanceConfig.setBackgroundImageVignetteIntensity(it) },
                            valueRange = 0.1f..1.0f,
                            steps = 9,
                        )
                    }

                    SettingsToggleItem(
                        label = "Wallpaper Color Tint",
                        subtitle = "Blends accent color overlay onto wallpaper",
                        checked = bgImageTintEnabled,
                        onCheckedChange = { AppearanceConfig.setBackgroundImageTintEnabled(it) },
                    )

                    if (bgImageTintEnabled) {
                        SettingsSliderItem(
                            label = "Tint Opacity",
                            subtitle = "${(bgImageTintAlpha * 100).toInt()}% overlay",
                            value = bgImageTintAlpha,
                            onValueChange = { AppearanceConfig.setBackgroundImageTintAlpha(it) },
                            valueRange = 0.05f..0.80f,
                            steps = 15,
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                SettingsSliderItem(
                    label = "UI Container & Card Glass Opacity",
                    subtitle = "${(uiCardOpacity * 100).toInt()}% opacity",
                    value = uiCardOpacity,
                    onValueChange = { AppearanceConfig.setUiCardOpacity(it) },
                    valueRange = 0.15f..1.0f,
                    steps = 17,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 4. SUB-SCREEN: POSTERS & PROVIDER BRANDING
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPostersBadgesScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val cleanModeEnabled by AppearanceConfig.cleanModeEnabled.collectAsState()
    val hideProviderNames by AppearanceConfig.hideProviderNames.collectAsState()
    val hideDetailsSource by AppearanceConfig.hideDetailsSource.collectAsState()
    val hideStreamProviders by AppearanceConfig.hideStreamProviders.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val continueWatchingStyle by AppearanceConfig.continueWatchingStyle.collectAsState()
    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()
    val elementShadowsEnabled by AppearanceConfig.elementShadowsEnabled.collectAsState()
    val elementShadowMultiplier by AppearanceConfig.elementShadowMultiplier.collectAsState()
    val textDropShadowEnabled by AppearanceConfig.textDropShadowEnabled.collectAsState()
    val textDropShadowBlur by AppearanceConfig.textDropShadowBlur.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Master Clean Mode Hero Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (cleanModeEnabled) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                },
            ),
            border = if (cleanModeEnabled) {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            } else null,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    modifier = Modifier.size(46.dp),
                    shape = RoundedCornerShape(12.dp),
                    color = if (cleanModeEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.20f) else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = if (cleanModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Cinematic Clean Mode (Master)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (cleanModeEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
                        ) {
                            Text(
                                text = if (cleanModeEnabled) "Active" else "Custom",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (cleanModeEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "One-click master switch to strip all plugin/provider names, hide scraper tags, and auto-clean messy release strings across the entire app. Shortcut: Ctrl+Shift+C.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = cleanModeEnabled,
                    onCheckedChange = { AppearanceConfig.setCleanModeEnabled(it) },
                )
            }
        }

        SettingsGroupCard(title = "Customizable Clean Mode & Provider Branding") {
            SettingsToggleItem(
                label = "Hide Provider & Scraper Names Everywhere",
                subtitle = "Suppresses provider names in context menus, cards, and list subtitles",
                checked = hideProviderNames,
                onCheckedChange = { AppearanceConfig.setHideProviderNames(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Auto-Clean Messy Release Titles",
                subtitle = "Strips raw release tags (WEB-DL, Dual Audio, codecs) to show pure titles across the app",
                checked = autoCleanTitles,
                onCheckedChange = { CardMetadataConfig.setAutoCleanTitles(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Hide Source Spec on Details Page",
                subtitle = "Omits the 'Source' row from the movie and show technical specs sidebar",
                checked = hideDetailsSource,
                onCheckedChange = { AppearanceConfig.setHideDetailsSource(it) },
            )
        }

        SettingsGroupCard(title = "Depth & Shadow Enhancements") {
            SettingsToggleItem(
                label = "UI Element Drop Shadows",
                subtitle = "Adds visual depth and elevation shadows under cards and floating panels",
                checked = elementShadowsEnabled,
                onCheckedChange = { AppearanceConfig.setElementShadowsEnabled(it) },
            )

            if (elementShadowsEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Shadow Intensity Multiplier",
                    subtitle = "%.1fx strength".format(elementShadowMultiplier),
                    value = elementShadowMultiplier,
                    onValueChange = { AppearanceConfig.setElementShadowMultiplier(it) },
                    valueRange = 0.5f..2.5f,
                    steps = 8,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            SettingsToggleItem(
                label = "Text Legibility Shadows",
                subtitle = "Soft subtle drop shadows behind titles and badges for enhanced readability",
                checked = textDropShadowEnabled,
                onCheckedChange = { AppearanceConfig.setTextDropShadowEnabled(it) },
            )

            if (textDropShadowEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Text Shadow Blur Radius",
                    subtitle = "${textDropShadowBlur.toInt()} dp blur",
                    value = textDropShadowBlur,
                    onValueChange = { AppearanceConfig.setTextDropShadowBlur(it) },
                    valueRange = 2f..24f,
                    steps = 11,
                )
            }
        }

        SettingsGroupCard(title = "Visual Poster Studio") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
                    Text("Poster Visual Editor", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Text("Fine-tune poster card dimensions, corner rounding, and card spacing in real-time with live preview", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { onNavigateToSubScreen(SettingsSubScreen.POSTER_EDITOR) }, shape = RoundedCornerShape(8.dp)) {
                    Text("Open Studio ➔")
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 5. SUB-SCREEN: HOME FEED & CINEMA
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsHomeFeedScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit = {}) {
    val heroEnabled by AppearanceConfig.heroEnabled.collectAsState()
    val showContinueWatching by AppearanceConfig.showContinueWatching.collectAsState()
    val heroAutoSlideDelaySeconds by AppearanceConfig.heroAutoSlideDelaySeconds.collectAsState()
    val heroBannerStyle by AppearanceConfig.heroBannerStyle.collectAsState()

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        SettingsGroupCard(title = "Continue Watching Feed") {
            SettingsToggleItem(
                label = "Show Continue Watching",
                subtitle = "Display in-progress movies and series at the top of the Home feed",
                checked = showContinueWatching,
                onCheckedChange = { AppearanceConfig.setShowContinueWatching(it) },
            )
        }

        SettingsGroupCard(title = "Hero Spotlight Carousel") {
            SettingsToggleItem(
                label = "Enable Hero Slider",
                subtitle = "Display featured trending media spotlight banner at the top of Home",
                checked = heroEnabled,
                onCheckedChange = { AppearanceConfig.setHeroEnabled(it) },
            )

            if (heroEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Hero Banner Layout Style",
                    subtitle = "Choose between cinema peeking edges, fullscreen banner, and filmstrip",
                    options = listOf(
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.CINEMA_PEEKING to "Cinema (Peeking Rails)",
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.FULLSCREEN_IMMERSIVE to "Fullscreen Immersive",
                        com.lagradost.cloudstream3.desktop.ui.theme.HeroBannerStyle.THUMBNAIL_STRIP to "Thumbnail Filmstrip",
                    ),
                    currentValue = heroBannerStyle,
                    onSelectionChanged = { AppearanceConfig.setHeroBannerStyle(it) },
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsDropdownItem(
                    label = "Hero Auto-Slide Delay",
                    subtitle = "How long each spotlight item stays visible before transitioning",
                    options = listOf(
                        0 to "Off (Manual Only)",
                        4 to "4 seconds",
                        6 to "6 seconds",
                        8 to "8 seconds",
                        12 to "12 seconds",
                    ),
                    currentValue = heroAutoSlideDelaySeconds,
                    onSelectionChanged = { AppearanceConfig.setHeroAutoSlideDelaySeconds(it) },
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 6. SUB-SCREEN: DETAILS PAGE & MODULAR SECTIONS (With Drag Reorder)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsDetailsSectionsScreen() {
    val lockUnreleasedEpisodes by AppearanceConfig.lockUnreleasedEpisodes.collectAsState()
    val antiSpoilerEnabled by AppearanceConfig.antiSpoilerEnabled.collectAsState()
    val detailsShowCurrentTime by AppearanceConfig.detailsShowCurrentTime.collectAsState()
    val detailsShowEndTime by AppearanceConfig.detailsShowEndTime.collectAsState()
    val heroBackgroundBlurEnabled by AppearanceConfig.heroBackgroundBlurEnabled.collectAsState()
    val heroBackdropBlurRadius by AppearanceConfig.heroBackdropBlurRadius.collectAsState()
    val heroBackdropDarkening by AppearanceConfig.heroBackdropDarkening.collectAsState()
    val sectionOrder by AppearanceConfig.detailsSectionOrder.collectAsState()
    val disabledSections by AppearanceConfig.detailsDisabledSections.collectAsState()
    val scrollState = rememberScrollState()

    var draggingSectionKey by remember { mutableStateOf<DetailsSectionKey?>(null) }
    var dragAccumulatedY by remember { mutableStateOf(0f) }
    var dragInitialIndex by remember { mutableStateOf(0) }
    var slotHeightPx by remember { mutableStateOf(0f) }
    val fallbackSlotHeight = with(LocalDensity.current) { 64.dp.toPx() }
    val effectiveSlotHeight = if (slotHeightPx > 0f) slotHeightPx else fallbackSlotHeight

    val currentTargetIndex = if (draggingSectionKey != null && effectiveSlotHeight > 0f) {
        (dragInitialIndex + kotlin.math.round(dragAccumulatedY / effectiveSlotHeight).toInt())
            .coerceIn(0, sectionOrder.lastIndex)
    } else dragInitialIndex

    val currentSectionOrder by rememberUpdatedState(sectionOrder)
    val currentEffectiveSlotHeight by rememberUpdatedState(effectiveSlotHeight)
    val currentDragAccumulatedY by rememberUpdatedState(dragAccumulatedY)
    val currentDragInitialIndex by rememberUpdatedState(dragInitialIndex)

    val onDropSection by rememberUpdatedState {
        val fromIdx = currentDragInitialIndex
        val slotH = currentEffectiveSlotHeight
        val accY = currentDragAccumulatedY
        val toIdx = if (slotH > 0f) {
            (fromIdx + kotlin.math.round(accY / slotH).toInt())
                .coerceIn(0, currentSectionOrder.lastIndex)
        } else fromIdx
        draggingSectionKey = null
        dragAccumulatedY = 0f
        if (fromIdx != toIdx && fromIdx in currentSectionOrder.indices && toIdx in currentSectionOrder.indices) {
            AppearanceConfig.moveDetailsSection(fromIdx, toIdx)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(scrollState).padding(top = 8.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        SettingsGroupCard(title = "Modular Sections & Drag Reorder") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Arrange Details Page Sections",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "Drag the handles (⠿) to reorder sections, or toggle them on and off",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = { AppearanceConfig.resetDetailsSectionOrder() },
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Order")
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                sectionOrder.forEachIndexed { index, sectionKey ->
                    val isEnabled = sectionKey !in disabledSections
                    val isDraggingThis = draggingSectionKey == sectionKey

                    val targetShiftY = when {
                        isDraggingThis -> dragAccumulatedY
                        draggingSectionKey != null && dragInitialIndex < currentTargetIndex && index in (dragInitialIndex + 1)..currentTargetIndex -> -effectiveSlotHeight
                        draggingSectionKey != null && dragInitialIndex > currentTargetIndex && index in currentTargetIndex until dragInitialIndex -> effectiveSlotHeight
                        else -> 0f
                    }
                    val animatedShiftY by animateFloatAsState(
                        targetValue = targetShiftY,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    )

                    val elevation by animateDpAsState(if (isDraggingThis) 16.dp else 0.dp)
                    val scale by animateFloatAsState(if (isDraggingThis) 1.02f else 1.0f)

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDraggingThis) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        } else if (isEnabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.12f)
                        },
                        border = BorderStroke(
                            if (isDraggingThis) 1.5.dp else 0.5.dp,
                            if (isDraggingThis) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.08f),
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
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // Drag grip handle
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isDraggingThis) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.04f))
                                    .pointerInput(sectionKey) {
                                        detectDragGestures(
                                            onDragStart = {
                                                draggingSectionKey = sectionKey
                                                dragInitialIndex = currentSectionOrder.indexOf(sectionKey)
                                                dragAccumulatedY = 0f
                                            },
                                            onDragEnd = { onDropSection() },
                                            onDragCancel = {
                                                draggingSectionKey = null
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
                                    contentDescription = "Drag to reorder",
                                    tint = if (isDraggingThis) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            // Section info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = sectionKey.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                    fontWeight = FontWeight.Medium,
                                )
                                Text(
                                    text = sectionKey.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Switch(
                                checked = isEnabled,
                                onCheckedChange = { AppearanceConfig.toggleDetailsSection(sectionKey, it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                }
            }
        }

        SettingsGroupCard(title = "Episode Playback & Protection") {
            SettingsToggleItem(
                label = "Lock Unreleased Episodes",
                subtitle = "Prevent clicking and playing future/unreleased episodes and display countdown/air date badges",
                checked = lockUnreleasedEpisodes,
                onCheckedChange = { AppearanceConfig.setLockUnreleasedEpisodes(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsToggleItem(
                label = "Anti-Spoiler Mode",
                subtitle = "Hide episode thumbnails, titles, and descriptions until watched",
                checked = antiSpoilerEnabled,
                onCheckedChange = { AppearanceConfig.setAntiSpoilerEnabled(it) },
            )
        }

        SettingsGroupCard(title = "Time Badges & Metadata") {
            SettingsToggleItem(
                label = "Show Current Time",
                subtitle = "Display the current time on the details page",
                checked = detailsShowCurrentTime,
                onCheckedChange = { AppearanceConfig.setDetailsShowCurrentTime(it) },
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            SettingsToggleItem(
                label = "Show End Time",
                subtitle = "Display what time the movie/episode will end",
                checked = detailsShowEndTime,
                onCheckedChange = { AppearanceConfig.setDetailsShowEndTime(it) },
            )
        }

        SettingsGroupCard(title = "Backdrop Frosted Blur & Atmosphere") {
            SettingsToggleItem(
                label = "Dynamic Backdrop Blur",
                subtitle = "Apply atmospheric frosted blur to hero backdrops on movie details screens",
                checked = heroBackgroundBlurEnabled,
                onCheckedChange = { AppearanceConfig.setHeroBackgroundBlurEnabled(it) },
            )

            if (heroBackgroundBlurEnabled) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Backdrop Softness",
                    subtitle = "${heroBackdropBlurRadius.toInt()} dp blur",
                    value = heroBackdropBlurRadius,
                    onValueChange = { AppearanceConfig.setHeroBackdropBlurRadius(it) },
                    valueRange = 8f..64f,
                    steps = 7,
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                SettingsSliderItem(
                    label = "Backdrop Darkening",
                    subtitle = "${(heroBackdropDarkening * 100).toInt()}% overlay",
                    value = heroBackdropDarkening,
                    onValueChange = { AppearanceConfig.setHeroBackdropDarkening(it) },
                    valueRange = 0.1f..0.8f,
                    steps = 7,
                )
            }
        }
    }
}

// Backward compatibility forwards
@Composable
fun SettingsAppearanceThemeScreen() = SettingsThemeWallpaperScreen()

@Composable
fun SettingsAppearanceLayoutScreen(onNavigateToSubScreen: (SettingsSubScreen) -> Unit) = SettingsNavDockScreen()

@Composable
fun SettingsAppearanceEffectsScreen() = SettingsHomeFeedScreen()

fun colorToHsv(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    var h = 0f
    if (delta > 0f) {
        if (max == r) {
            h = 60f * (((g - b) / delta) % 6f)
        } else if (max == g) {
            h = 60f * (((b - r) / delta) + 2f)
        } else if (max == b) {
            h = 60f * (((r - g) / delta) + 4f)
        }
    }
    if (h < 0f) h += 360f

    val s = if (max == 0f) 0f else delta / max
    val v = max

    return floatArrayOf(h, s, v)
}

fun hsvToColor(h: Float, s: Float, v: Float): Color {
    val hNorm = h % 360f
    val c = v * s
    val x = c * (1f - kotlin.math.abs((hNorm / 60f) % 2f - 1f))
    val m = v - c

    var r = 0f
    var g = 0f
    var b = 0f

    when ((hNorm / 60f).toInt() % 6) {
        0 -> {
            r = c
            g = x
            b = 0f
        }
        1 -> {
            r = x
            g = c
            b = 0f
        }
        2 -> {
            r = 0f
            g = c
            b = x
        }
        3 -> {
            r = 0f
            g = x
            b = c
        }
        4 -> {
            r = x
            g = 0f
            b = c
        }
        5 -> {
            r = c
            g = 0f
            b = x
        }
    }

    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = 1f,
    )
}

@Composable
fun CustomColorStudioDialog(
    show: Boolean,
    title: String,
    initialHex: String,
    defaultHex: String = "#7C6BFF",
    curatedColors: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onColorConfirmed: (String) -> Unit,
) {
    if (!show) return

    var currentHexInput by remember(initialHex, show) { mutableStateOf(initialHex.uppercase()) }
    var parsedColor by remember(initialHex, show) {
        mutableStateOf(com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(initialHex, Color(0xFF7C6BFF)))
    }
    val hsv = remember(parsedColor) { colorToHsv(parsedColor) }
    var hue by remember(show) { mutableStateOf(hsv[0]) }
    var saturation by remember(show) { mutableStateOf(hsv[1]) }
    var value by remember(show) { mutableStateOf(hsv[2]) }

    fun syncFromHsv() {
        val c = hsvToColor(hue, saturation, value)
        val r = (c.red * 255).toInt()
        val g = (c.green * 255).toInt()
        val b = (c.blue * 255).toInt()
        val hex = String.format("#%02X%02X%02X", r, g, b)
        currentHexInput = hex
        parsedColor = c
    }

    fun syncFromHex(hex: String) {
        currentHexInput = hex.uppercase()
        val clean = hex.trim().removePrefix("#")
        if (clean.length == 6 || clean.length == 8) {
            val color = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(hex, parsedColor)
            parsedColor = color
            val newHsv = colorToHsv(color)
            hue = newHsv[0]
            saturation = newHsv[1]
            value = newHsv[2]
        }
    }

    CloudstreamCustomDialog(
        show = show,
        onDismissRequest = onDismiss,
        modifier = Modifier.widthIn(min = 460.dp, max = 520.dp).padding(16.dp),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
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
                        Icons.Default.Palette,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", modifier = Modifier.size(18.dp))
                }
            }

            // Hex Input & Preview Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = currentHexInput,
                    onValueChange = { syncFromHex(it) },
                    label = { Text("Hex Color Code (#RRGGBB)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f),
                )

                // Live Preview Block
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(parsedColor)
                        .border(1.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), RoundedCornerShape(12.dp)),
                )
            }

            // 2D Saturation / Value Canvas
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black),
            ) {
                val baseHueColor = hsvToColor(hue, 1f, 1f)
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.matchParentSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                saturation = (change.position.x / size.width).coerceIn(0f, 1f)
                                value = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                                syncFromHsv()
                            }
                        },
                ) {
                    drawRect(color = baseHueColor, size = size)
                    drawRect(
                        brush = Brush.horizontalGradient(listOf(Color.White, Color.Transparent)),
                        size = size,
                    )
                    drawRect(
                        brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                        size = size,
                    )
                    val thumbX = saturation * size.width
                    val thumbY = (1f - value) * size.height
                    drawCircle(
                        color = Color.White,
                        radius = 7.dp.toPx(),
                        center = Offset(thumbX, thumbY),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }

            // Hue Rainbow Slider
            val rainbowColors = listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp)),
            ) {
                androidx.compose.foundation.Canvas(
                    modifier = Modifier.matchParentSize()
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                hue = ((change.position.x / size.width) * 360f).coerceIn(0f, 360f)
                                syncFromHsv()
                            }
                        },
                ) {
                    drawRect(brush = Brush.horizontalGradient(rainbowColors), size = size)
                    val thumbX = (hue / 360f) * size.width
                    drawCircle(
                        color = Color.White,
                        radius = 8.dp.toPx(),
                        center = Offset(thumbX, size.height / 2f),
                        style = Stroke(width = 2.5.dp.toPx()),
                    )
                }
            }

            // Quick Select Curated Swatches
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Curated Palette",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    curatedColors.forEach { (name, hex) ->
                        val swatchColor = com.lagradost.cloudstream3.desktop.ui.theme.parseHexColor(hex, Color.Gray)
                        val isPicked = currentHexInput.equals(hex, ignoreCase = true)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(swatchColor)
                                .border(
                                    width = if (isPicked) 2.5.dp else 1.dp,
                                    color = if (isPicked) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(8.dp),
                                )
                                .clickable { syncFromHex(hex) },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isPicked) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { syncFromHex(defaultHex) },
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Reset to Default")
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Cancel")
                    }
                    Button(
                        onClick = {
                            val finalHex = if (currentHexInput.startsWith("#")) currentHexInput else "#$currentHexInput"
                            onColorConfirmed(finalHex)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text("Apply & Save")
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// 7. SUB-SCREEN: POSTER WORKSHOP STUDIO (Fullscreen Live Preview)
// ─────────────────────────────────────────────────────────────────────────────
@Composable
fun SettingsPosterEditorScreen(onBack: () -> Unit = {}) {
    val theme = com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme.current
    val posterWidthDp by AppearanceConfig.posterWidthDp.collectAsState()
    val homeSpacingDp by AppearanceConfig.homeSpacingDp.collectAsState()
    val homeVerticalSpacingDp by AppearanceConfig.homeVerticalSpacingDp.collectAsState()
    val posterRoundingDp by AppearanceConfig.posterRoundingDp.collectAsState()
    val posterTitlePosition by AppearanceConfig.posterTitlePosition.collectAsState()
    val continueWatchingStyle by AppearanceConfig.continueWatchingStyle.collectAsState()
    val providerBadgeDisplayMode by AppearanceConfig.providerBadgeDisplayMode.collectAsState()
    val posterHoverGlowEnabled by AppearanceConfig.posterHoverGlowEnabled.collectAsState()

    val autoCleanTitles by CardMetadataConfig.autoCleanTitles.collectAsState()
    val autoDetectSubDub by CardMetadataConfig.autoDetectSubDub.collectAsState()
    val autoDetectQuality by CardMetadataConfig.autoDetectQuality.collectAsState()
    val showRatingBadges by CardMetadataConfig.showRatingBadges.collectAsState()

    var isControlsExpanded by remember { mutableStateOf(true) }
    var activeControlTab by remember { mutableStateOf(0) }

    val mockApi = remember {
        object : com.lagradost.cloudstream3.MainAPI() {
            override var mainUrl = "mock"
            override var name = "Cinemeta"
            override val hasMainPage = true
        }
    }

    val mockHistory1 = remember {
        com.lagradost.common.storage.WatchHistory(
            parentId = "mock_history_1",
            showName = "House of the Dragon",
            showUrl = "dummy",
            apiName = "Cinemeta",
            posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg",
            episodeThumbnailUrl = null,
            screenshotUrl = null,
            episode = 1,
            season = 2,
            episodeId = "dummy_ep_1",
            position = 1800,
            duration = 3600,
            episodeName = "A Son for a Son",
            episodeDescription = "As grief and rage take hold across Westeros, Rhaenyra struggles to maintain control while Daemon plots revenge.",
        )
    }
    val mockHistory2 = remember {
        com.lagradost.common.storage.WatchHistory(
            parentId = "mock_history_2",
            showName = "Shōgun",
            showUrl = "dummy",
            apiName = "Cinemeta",
            posterUrl = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg",
            episodeThumbnailUrl = null,
            screenshotUrl = null,
            episode = 4,
            season = 1,
            episodeId = "dummy_ep_2",
            position = 2400,
            duration = 3600,
            episodeName = "The Eightfold Fence",
            episodeDescription = "Blackthorne and Mariko test their new alliance as they train Toranaga's gun regiment for impending conflict.",
        )
    }

    val fallbackMovies = remember {
        listOf(
            mockApi.newMovieSearchResponse("Dune: Part Two [4K] [IMAX.BluRay.DTS-HD]", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/1pdfLvkbY9ohJlCjQH2CZjjYVvJ.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(8.6)
            },
            mockApi.newMovieSearchResponse("The Batman [4K] [UHD.Remux.HDR10]", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/74xTEgt7R36Fpooo50r9T25onhq.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(7.9)
            },
            mockApi.newMovieSearchResponse("Oppenheimer [4K] [IMAX.x265]", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8Gxv8gSFCU0XGDykEGv7zR1n2ua.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(8.9)
            },
            mockApi.newMovieSearchResponse("Alien: Romulus [1080p.HEVC.Dual-Audio]", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/b33nnKl1vGa6kKK4a3GQU0IO4a.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
                score = com.lagradost.cloudstream3.Score.from10(7.3)
            },
            mockApi.newMovieSearchResponse("Deadpool & Wolverine [4K] [HDR]", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8cdWjvZQUExUUTzyp4t6EDMubfO.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(7.8)
            },
            mockApi.newMovieSearchResponse("Interstellar [4K] [Remastered.UHD]", "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/gEU2QniE6E77NI6lCU6MxlNBvIx.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(8.7)
            }
        )
    }

    val fallbackSeries = remember {
        listOf(
            mockApi.newMovieSearchResponse("House of the Dragon [4K] [HDR] [Dual-Audio.2160p]", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/1X4h40fcB4WWUmIBK0auT4zRBAV.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(8.9)
            },
            mockApi.newMovieSearchResponse("Arcane: Season 2 [SUB] [4K] [WEB-DL.x265]", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/fqldf2t8ztc9aiwn396nlv8g9qc.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(9.1)
            },
            mockApi.newMovieSearchResponse("Shōgun [DUB] [1080p.HEVC.Multi-Audio]", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/7O4iVfOMQmdCSxhOg1WnzG1AgYT.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
                score = com.lagradost.cloudstream3.Score.from10(8.8)
            },
            mockApi.newMovieSearchResponse("Severance [4K] [WEB-DL.Atmos]", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8t4fF2k9YvW1F7yW71c5Mv2M8k7.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(8.7)
            },
            mockApi.newMovieSearchResponse("Solo Leveling [SUB] [1080p.FHD.AAC]", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/8b8R8l88Qje9dn9OE8PY05Nx11H.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.HD
                score = com.lagradost.cloudstream3.Score.from10(8.4)
            },
            mockApi.newMovieSearchResponse("Breaking Bad [4K] [Remastered.UHD]", "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                posterUrl = "https://image.tmdb.org/t/p/w500/ggFHVNu6YYI5L9pCfOacjizRGt.jpg"
                quality = com.lagradost.cloudstream3.SearchQuality.UHD
                score = com.lagradost.cloudstream3.Score.from10(9.5)
            }
        )
    }

    var liveMovies by remember { mutableStateOf<List<com.lagradost.cloudstream3.SearchResponse>>(emptyList()) }
    var liveSeries by remember { mutableStateOf<List<com.lagradost.cloudstream3.SearchResponse>>(emptyList()) }

    LaunchedEffect(Unit) {
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            // 1. Fetch Live Trending Movies from Cinemeta
            try {
                val movieData = com.lagradost.cloudstream3.app.get("https://v3-cinemeta.strem.io/catalog/movie/top.json")
                    .parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                val movieMetas = movieData?.get("metas")
                if (movieMetas != null && movieMetas.isArray) {
                    val list = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                    for ((idx, node) in movieMetas.withIndex()) {
                        val rawName = node.get("name")?.asText() ?: continue
                        val posterUrl = node.get("poster")?.asText() ?: continue
                        val imdbRating = node.get("imdbRating")?.asDouble()
                        // Attach sample tokens to demonstrate real-time badge toggles on live titles
                        val enrichedTitle = when (idx % 4) {
                            0 -> "$rawName [4K] [HDR]"
                            1 -> "$rawName [1080p.WEB-DL]"
                            2 -> "$rawName [Dual-Audio.1080p]"
                            else -> "$rawName [4K] [IMAX.Remux]"
                        }
                        list.add(
                            mockApi.newMovieSearchResponse(enrichedTitle, "dummy", com.lagradost.cloudstream3.TvType.Movie, false) {
                                this.posterUrl = posterUrl
                                this.quality = if (idx % 2 == 0) com.lagradost.cloudstream3.SearchQuality.UHD else com.lagradost.cloudstream3.SearchQuality.HD
                                this.score = imdbRating?.let { com.lagradost.cloudstream3.Score.from10(it) }
                            }
                        )
                        if (list.size >= 16) break
                    }
                    if (list.isNotEmpty()) liveMovies = list
                }
            } catch (_: Exception) {}

            // 2. Fetch Live Popular Series from Cinemeta
            try {
                val seriesData = com.lagradost.cloudstream3.app.get("https://v3-cinemeta.strem.io/catalog/series/top.json")
                    .parsedSafe<com.fasterxml.jackson.databind.JsonNode>()
                val seriesMetas = seriesData?.get("metas")
                if (seriesMetas != null && seriesMetas.isArray) {
                    val list = mutableListOf<com.lagradost.cloudstream3.SearchResponse>()
                    for ((idx, node) in seriesMetas.withIndex()) {
                        val rawName = node.get("name")?.asText() ?: continue
                        val posterUrl = node.get("poster")?.asText() ?: continue
                        val imdbRating = node.get("imdbRating")?.asDouble()
                        // Attach sample SUB/DUB/4K tokens on series
                        val enrichedTitle = when (idx % 4) {
                            0 -> "$rawName [4K] [HDR] [Dual-Audio]"
                            1 -> "$rawName [SUB] [4K] [WEB-DL]"
                            2 -> "$rawName [DUB] [1080p.HEVC]"
                            else -> "$rawName [SUB] [DUB] [1080p]"
                        }
                        list.add(
                            mockApi.newMovieSearchResponse(enrichedTitle, "dummy", com.lagradost.cloudstream3.TvType.TvSeries, false) {
                                this.posterUrl = posterUrl
                                this.quality = if (idx % 2 == 0) com.lagradost.cloudstream3.SearchQuality.UHD else com.lagradost.cloudstream3.SearchQuality.HD
                                this.score = imdbRating?.let { com.lagradost.cloudstream3.Score.from10(it) }
                            }
                        )
                        if (list.size >= 16) break
                    }
                    if (list.isNotEmpty()) liveSeries = list
                }
            } catch (_: Exception) {}
        }
    }

    val displayMovies = if (liveMovies.isNotEmpty()) liveMovies else fallbackMovies
    val displaySeries = if (liveSeries.isNotEmpty()) liveSeries else fallbackSeries

    val currentSpacing = homeSpacingDp.dp
    val currentWidth = posterWidthDp.dp
    val currentVerticalSpacing = homeVerticalSpacingDp.dp

    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val paddingStart = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.LEFT) 88.dp else 22.dp
    val paddingEnd = if (dockPosition == com.lagradost.cloudstream3.desktop.ui.DockPosition.RIGHT) 88.dp else 22.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val availableWidth = this.maxWidth
        val isCompact = availableWidth < 600.dp
        val effectivePaddingStart = if (isCompact) 8.dp else paddingStart
        val effectivePaddingEnd = if (isCompact) 8.dp else paddingEnd
        val spacingDp = if (isCompact) 8.dp else currentSpacing

        val optimalItemWidth = if (isCompact) {
            115.dp
        } else {
            val baseWidth = currentWidth
            val netWidth = availableWidth - effectivePaddingStart - effectivePaddingEnd - 20.dp
            val exactColumns = (netWidth + spacingDp) / (baseWidth + spacingDp)
            val columns = exactColumns.toInt().coerceAtLeast(1)
            ((netWidth + spacingDp) / columns) - spacingDp
        }

        // ── 1. Full-Width Scrollable Canvas (100% Real-Time & Identical to HomePage) ──
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 74.dp, bottom = 220.dp),
            verticalArrangement = Arrangement.spacedBy(currentVerticalSpacing),
        ) {
            // Row 1: Continue Watching Shelf
            com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader(
                title = "Continue Watching",
                itemCount = 2,
                rowContentPadding = PaddingValues(
                    start = effectivePaddingStart + 10.dp,
                    end = effectivePaddingEnd + 10.dp,
                    top = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                    bottom = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                ),
                headerPadding = PaddingValues(
                    start = effectivePaddingStart + 10.dp,
                    end = effectivePaddingEnd + 10.dp,
                    top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                    bottom = 4.dp,
                ),
                itemSpacing = spacingDp,
            ) {
                item {
                    when (continueWatchingStyle) {
                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM -> {
                            val cardWidth = if (isCompact) 280.dp else (posterWidthDp * 2.4f).coerceAtLeast(360f).dp
                            val cardHeight = if (isCompact) 130.dp else (posterWidthDp * 1.5f).dp
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardWide(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                history = mockHistory1,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.DETAILED -> {
                            val cardWidth = if (isCompact) 280.dp else (posterWidthDp * 2.5f).coerceAtLeast(420f).dp
                            val cardHeight = if (isCompact) 130.dp else 145.dp
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardDetailed(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                history = mockHistory1,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.THUMBNAIL -> {
                            val cardWidth = if (isCompact) 180.dp else (posterWidthDp * 1.8f).dp
                            val cardHeight = if (isCompact) 100.dp else (posterWidthDp * 1.8f * 9f / 16f).dp
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                history = mockHistory1,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                    }
                }
                item {
                    when (continueWatchingStyle) {
                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM -> {
                            val cardWidth = if (isCompact) 280.dp else (posterWidthDp * 2.4f).coerceAtLeast(360f).dp
                            val cardHeight = if (isCompact) 130.dp else (posterWidthDp * 1.5f).dp
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardWide(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                history = mockHistory2,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.DETAILED -> {
                            val cardWidth = if (isCompact) 280.dp else (posterWidthDp * 2.5f).coerceAtLeast(420f).dp
                            val cardHeight = if (isCompact) 130.dp else 145.dp
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCardDetailed(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                history = mockHistory2,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                        com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.THUMBNAIL -> {
                            val cardWidth = if (isCompact) 180.dp else (posterWidthDp * 1.8f).dp
                            val cardHeight = if (isCompact) 100.dp else (posterWidthDp * 1.8f * 9f / 16f).dp
                            com.lagradost.cloudstream3.desktop.ui.components.WatchHistoryCard(
                                modifier = Modifier.width(cardWidth).height(cardHeight),
                                history = mockHistory2,
                                provider = mockApi,
                                onRemove = {},
                                onClick = {},
                                onPlayClick = {}
                            )
                        }
                    }
                }
            }

            // Row 2: Trending Movies Shelf (with Exact Arrow Chevrons)
            com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "Trending Movies",
                itemCount = displayMovies.size,
                rowContentPadding = PaddingValues(
                    start = effectivePaddingStart + 10.dp,
                    end = effectivePaddingEnd + 10.dp,
                    top = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                    bottom = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                ),
                headerPadding = PaddingValues(
                    start = effectivePaddingStart + 10.dp,
                    end = effectivePaddingEnd + 10.dp,
                    top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                    bottom = 4.dp,
                ),
                itemSpacing = spacingDp,
            ) {
                items(displayMovies.size) { index ->
                    com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                        item = displayMovies[index],
                        provider = mockApi,
                        itemWidth = optimalItemWidth,
                        onClick = {},
                        onPlayClick = {}
                    )
                }
            }

            // Row 3: Popular Series Shelf (with Exact Arrow Chevrons)
            com.lagradost.cloudstream3.desktop.ui.components.CategoryRowWithHeader(
                modifier = Modifier.fillMaxWidth(),
                title = "Popular Series",
                itemCount = displaySeries.size,
                rowContentPadding = PaddingValues(
                    start = effectivePaddingStart + 10.dp,
                    end = effectivePaddingEnd + 10.dp,
                    top = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                    bottom = if (isCompact) 4.dp else (4.dp + (homeVerticalSpacingDp * 0.25f).dp),
                ),
                headerPadding = PaddingValues(
                    start = effectivePaddingStart + 10.dp,
                    end = effectivePaddingEnd + 10.dp,
                    top = (4.dp + (homeVerticalSpacingDp * 0.35f).dp),
                    bottom = 4.dp,
                ),
                itemSpacing = spacingDp,
            ) {
                items(displaySeries.size) { index ->
                    com.lagradost.cloudstream3.desktop.ui.components.PosterCard(
                        item = displaySeries[index],
                        provider = mockApi,
                        itemWidth = optimalItemWidth,
                        onClick = {},
                        onPlayClick = {}
                    )
                }
            }
        }

        // ── 2. Top Floating Glass Header ─────────────────────────────────────────
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(64.dp),
            color = theme.SurfaceElevated.copy(alpha = 0.88f),
            shadowElevation = 8.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Back Button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = theme.SurfaceCard.copy(alpha = 0.7f),
                    border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.5f)),
                    modifier = Modifier.clickable { onBack() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = theme.TextPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            "Back to Settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = theme.TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(20.dp))

                // Title & Subtitle
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Palette, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Text(
                            "Poster Workshop Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = theme.TextPrimary,
                        )
                    }
                    Text(
                        "Live full-screen canvas preview matching your actual display width and column-snapping",
                        style = MaterialTheme.typography.labelSmall,
                        color = theme.TextMuted,
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                // Reset Defaults Button
                TextButton(
                    onClick = {
                        AppearanceConfig.setPosterWidthDp(160)
                        AppearanceConfig.setHomeSpacingDp(12)
                        AppearanceConfig.setHomeVerticalSpacingDp(16)
                        AppearanceConfig.setPosterRoundingDp(12)
                        AppearanceConfig.setPosterTitlePosition(com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW)
                        AppearanceConfig.setContinueWatchingStyle(com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM)
                        AppearanceConfig.setProviderBadgeDisplayMode(com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode.HIDDEN)
                        AppearanceConfig.setPosterHoverGlowEnabled(true)
                        CardMetadataConfig.setAutoCleanTitles(true)
                        CardMetadataConfig.setAutoDetectSubDub(true)
                        CardMetadataConfig.setAutoDetectQuality(true)
                        CardMetadataConfig.setShowRatingBadges(true)
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Defaults")
                }
            }
        }

        // ── 3. Bottom Floating Control Dock (Responsive 3-Tab Grid) ───────────────
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 20.dp, vertical = 14.dp)
                .widthIn(max = 1040.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = theme.SurfaceElevated.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.6f)),
            shadowElevation = 16.dp,
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Dock Header with Category Tabs & Collapse Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = activeControlTab == 0,
                            onClick = { activeControlTab = 0; isControlsExpanded = true },
                            label = { Text("📐 Dimensions & Spacing", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                        )
                        FilterChip(
                            selected = activeControlTab == 1,
                            onClick = { activeControlTab = 1; isControlsExpanded = true },
                            label = { Text("✨ Style & Layout", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                        )
                        FilterChip(
                            selected = activeControlTab == 2,
                            onClick = { activeControlTab = 2; isControlsExpanded = true },
                            label = { Text("🏷️ Badges & Overlays", fontWeight = FontWeight.Medium) },
                            shape = RoundedCornerShape(10.dp),
                        )
                    }

                    TextButton(
                        onClick = { isControlsExpanded = !isControlsExpanded }
                    ) {
                        Icon(
                            if (isControlsExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isControlsExpanded) "Hide Studio Bar" else "Show Controls")
                    }
                }

                if (isControlsExpanded) {
                    Spacer(modifier = Modifier.height(16.dp))

                    when (activeControlTab) {
                        0 -> {
                            // ── Tab 0: Dimensions & Spacing Sliders (Spacious 2x2 Grid) ────
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                ) {
                                    // 1. Poster Width
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Poster Width", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("${posterWidthDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = posterWidthDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setPosterWidthDp(it.toInt()) },
                                            valueRange = 100f..250f,
                                            steps = 29,
                                        )
                                    }

                                    // 2. Card Spacing
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Card Spacing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("${homeSpacingDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = homeSpacingDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setHomeSpacingDp(it.toInt()) },
                                            valueRange = 0f..32f,
                                            steps = 15,
                                        )
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                ) {
                                    // 3. Row Spacing
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Row Spacing", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("${homeVerticalSpacingDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = homeVerticalSpacingDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setHomeVerticalSpacingDp(it.toInt()) },
                                            valueRange = 0f..64f,
                                            steps = 31,
                                        )
                                    }

                                    // 4. Corner Radius
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Corner Radius", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("${posterRoundingDp} dp", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        }
                                        Slider(
                                            value = posterRoundingDp.toFloat(),
                                            onValueChange = { AppearanceConfig.setPosterRoundingDp(it.toInt()) },
                                            valueRange = 0f..24f,
                                            steps = 23,
                                        )
                                    }
                                }
                            }
                        }
                        1 -> {
                            // ── Tab 1: Style & Layout Controls ─────────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    // 1. Poster Title Position
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Poster Title Position", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(
                                                com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.BELOW to "Below",
                                                com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.INSIDE to "Hover",
                                                com.lagradost.cloudstream3.desktop.ui.theme.PosterTitlePosition.HIDDEN to "Hidden",
                                            ).forEach { (pos, label) ->
                                                FilterChip(
                                                    selected = posterTitlePosition == pos,
                                                    onClick = { AppearanceConfig.setPosterTitlePosition(pos) },
                                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            }
                                        }
                                    }

                                    // 2. Continue Watching Style
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Continue Watching Style", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            listOf(
                                                com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.PREMIUM to "Wide Card",
                                                com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.DETAILED to "Detailed",
                                                com.lagradost.cloudstream3.desktop.ui.theme.ContinueWatchingStyle.THUMBNAIL to "Classic",
                                            ).forEach { (style, label) ->
                                                FilterChip(
                                                    selected = continueWatchingStyle == style,
                                                    onClick = { AppearanceConfig.setContinueWatchingStyle(style) },
                                                    label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                    shape = RoundedCornerShape(8.dp),
                                                )
                                            }
                                        }
                                    }
                                }

                                // 3. Hover Ambient Glow Switch Card
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = theme.SurfaceCard.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                    ) {
                                        Column {
                                            Text("Hover Ambient Glow", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                            Text("Dynamic backdrop illumination beneath hovered cards", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                        }
                                        Switch(
                                            checked = posterHoverGlowEnabled,
                                            onCheckedChange = { AppearanceConfig.setPosterHoverGlowEnabled(it) }
                                        )
                                    }
                                }
                            }
                        }
                        else -> {
                            // ── Tab 2: Badges & Overlays Controls ──────────────────────
                            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    // 1. Rating Badges (★ Gold Pill)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column {
                                                Text("Rating Badges (★)", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("Community score pill", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = showRatingBadges,
                                                onCheckedChange = { CardMetadataConfig.setShowRatingBadges(it) }
                                            )
                                        }
                                    }

                                    // 2. Quality Badges (4K / HD)
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column {
                                                Text("Quality Badges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("4K and HD resolution tags", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = autoDetectQuality,
                                                onCheckedChange = { CardMetadataConfig.setAutoDetectQuality(it) }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                                ) {
                                    // 3. SUB / DUB Badges
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column {
                                                Text("SUB / DUB Badges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("Audio & subtitle availability", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Switch(
                                                checked = autoDetectSubDub,
                                                onCheckedChange = { CardMetadataConfig.setAutoDetectSubDub(it) }
                                            )
                                        }
                                    }

                                    // 4. Provider Badges on Cards
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = theme.SurfaceCard.copy(alpha = 0.6f),
                                        border = BorderStroke(1.dp, theme.Divider.copy(alpha = 0.4f)),
                                        modifier = Modifier.weight(1.3f),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                        ) {
                                            Column {
                                                Text("Provider Badges", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, color = theme.TextPrimary)
                                                Text("Plugin badge style", style = MaterialTheme.typography.labelSmall, color = theme.TextMuted)
                                            }
                                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                                listOf(
                                                    com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode.HIDDEN to "Hidden",
                                                    com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode.ICON_ONLY to "Icon",
                                                    com.lagradost.cloudstream3.desktop.ui.theme.ProviderBadgeDisplayMode.FULL_BADGE to "Full",
                                                ).forEach { (mode, label) ->
                                                    FilterChip(
                                                        selected = providerBadgeDisplayMode == mode,
                                                        onClick = { AppearanceConfig.setProviderBadgeDisplayMode(mode) },
                                                        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                                                        shape = RoundedCornerShape(8.dp),
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
