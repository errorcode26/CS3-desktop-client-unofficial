package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lagradost.cloudstream3.desktop.ui.components.WindowControlsPill
import com.lagradost.cloudstream3.desktop.ui.screens.home.AnimatedSearchOverlay
import coil3.compose.AsyncImage
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.ui.components.DesktopUi
import com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester

object DesktopUiState {
    val forceShowSearchBar = MutableStateFlow(false)
    val searchFocusTrigger = MutableStateFlow(0)
    val forceProviderRefresh = MutableStateFlow(0)
    
    // Global provider selection states (fed by HomeViewModel)
    val homeProviders = MutableStateFlow<List<com.lagradost.cloudstream3.MainAPI>>(emptyList())
    val selectedProviderName = MutableStateFlow<String?>(null)
    val mergedPluginIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    val isProviderDropdownExpanded = MutableStateFlow(false)
}

@Composable
fun DesktopAppShell(
    navController: NavController,
    title: String,
    showBack: Boolean = false,
    onErrorLogs: () -> Unit = {},
    content: @Composable () -> Unit,
) {
    val current = navController.currentScreen
    var isSyncing by remember { mutableStateOf(false) }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(current) {
        if (current !is Screen.Home) {
            com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value = false
            com.lagradost.cloudstream3.desktop.ui.DesktopUiState.searchFocusTrigger.value = 0
        }
    }

    val hasUnreadUpdates by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.hasUnreadUpdates() }
        .collectAsState(initial = DesktopDataStore.hasUnreadUpdates())

    val updatesHistory by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.getUpdatesHistory() }
        .collectAsState(initial = DesktopDataStore.getUpdatesHistory())

    val dockPosition by AppearanceConfig.dockPosition.collectAsState()
    val isSearchForced by com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.collectAsState()

    LaunchedEffect(Unit) {
        while (true) {
            delay(30 * 60 * 1000L) // 30 minutes
            DesktopRepositoryManager.autoUpdatePlugins()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
            val ambientGlowIntensity by AppearanceConfig.ambientGlowIntensity.collectAsState()
            val ambientGlowPositions by AppearanceConfig.ambientGlowPositions.collectAsState()
            val dynamicColorEnabled by AppearanceConfig.heroDynamicColorEnabled.collectAsState()
            val isLightMode by AppearanceConfig.isLightMode.collectAsState()
            val primaryColor = MaterialTheme.colorScheme.primary

            val surfaceColor = MaterialTheme.colorScheme.surface
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (ambientGlowEnabled && !isLightMode) {
                            Modifier.drawBehind {
                                drawRect(color = surfaceColor)
                                ambientGlowPositions.forEach { position ->
                                    val yOffset = 0f
                                    val centerOffset = when (position) {
                                        "Top" -> Offset(size.width / 2f, yOffset)
                                        "Bottom" -> Offset(size.width / 2f, size.height)
                                        "Left" -> Offset(0f, size.height / 2f)
                                        "Right" -> Offset(size.width, size.height / 2f)
                                        "Top Left" -> Offset(0f, yOffset)
                                        "Top Right" -> Offset(size.width, yOffset)
                                        "Bottom Left" -> Offset(0f, size.height)
                                        "Bottom Right" -> Offset(size.width, size.height)
                                        else -> Offset(size.width / 2f, size.height / 2f)
                                    }
                                    drawRect(
                                        brush = androidx.compose.ui.graphics.Brush.radialGradient(
                                            colorStops = arrayOf(
                                                0.0f to primaryColor.copy(alpha = ambientGlowIntensity),
                                                0.3f to primaryColor.copy(alpha = ambientGlowIntensity * 0.53f),
                                                0.6f to primaryColor.copy(alpha = ambientGlowIntensity * 0.2f),
                                                1.0f to Color.Transparent
                                            ),
                                            center = centerOffset,
                                            radius = size.width.coerceAtLeast(size.height) * 0.8f
                                        )
                                    )
                                }
                            }
                        } else {
                            Modifier.background(surfaceColor)
                        }
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                val contentPadding = if (current is Screen.Home) {
                    PaddingValues(0.dp)
                } else {
                    val hPadding = if (dockPosition == "Right" || dockPosition == "Left") 88.dp else 20.dp
                    when (dockPosition) {
                        "Right" -> PaddingValues(top = 66.dp, start = hPadding, end = hPadding, bottom = 12.dp)
                        "Bottom" -> PaddingValues(top = 66.dp, start = 20.dp, end = 20.dp, bottom = 88.dp)
                        "Top" -> PaddingValues(top = 88.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)
                        else -> PaddingValues(top = 66.dp, start = hPadding, end = hPadding, bottom = 12.dp)
                    }
                }

                val layoutWidthSetting by AppearanceConfig.layoutWidth.collectAsState()
                val maxWidthConstraint = when (layoutWidthSetting) {
                    "Compact" -> 1000.dp
                    "Modern" -> 1400.dp
                    else -> androidx.compose.ui.unit.Dp.Unspecified
                }

                val applyMaxWidth = current !is Screen.Home

                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .run { if (applyMaxWidth) this.widthIn(max = maxWidthConstraint) else this },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                    ) {
                        content()
                    }
                }

                // Global TopBar (Back button + Window Controls)
                // Positioned outside the width-constrained box so it always anchors to the absolute edges of the window
                TopBar(
                    showBack = showBack,
                    onBack = { navController.goBack() },
                    isHome = current is Screen.Home,
                )

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }
            
            // Navigation Dock
            val dockAlignment = when (dockPosition) {
                "Right" -> Alignment.CenterEnd
                "Bottom" -> Alignment.BottomCenter
                "Top" -> Alignment.TopCenter
                else -> Alignment.CenterStart
            }
            NavigationDock(
                modifier = Modifier.align(dockAlignment),
                current = current,
                dockPosition = dockPosition,
                isSyncing = isSyncing,
                onNavigate = { navController.navigateRoot(it) },
                onSearchClick = {
                    if (current !is Screen.Home) {
                        navController.navigateRoot(Screen.Home)
                    }
                    com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value = true
                    com.lagradost.cloudstream3.desktop.ui.DesktopUiState.searchFocusTrigger.value += 1
                },
            )

            // Updates Notification Bell (Always bottom left)
            UpdatesNotificationBell(
                modifier = Modifier.align(Alignment.BottomStart),
                hasUnreadUpdates = hasUnreadUpdates,
                updatesHistory = updatesHistory,
                onMarkUpdatesRead = { DesktopDataStore.setUnreadUpdates(false) },
            )

        }
    }
}

@Composable
private fun NavigationDock(
    modifier: Modifier = Modifier,
    current: Screen,
    dockPosition: String,
    isSyncing: Boolean,
    onNavigate: (Screen) -> Unit,
    onSearchClick: () -> Unit,
) {
    val savedRepos by DesktopRepositoryManager.savedRepositories.collectAsState()

    val isBottom = dockPosition == "Bottom"
    val isRight = dockPosition == "Right"
    val isTop = dockPosition == "Top"
    val isHorizontal = isBottom || isTop

    val isSearchForced by com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.collectAsState()

    val dockItems = @Composable {
        DockItem(
            icon = PremiumIcons.Home,
            label = "Home",
            selected = current is Screen.Home && !isSearchForced,
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = {
                com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value = false
                onNavigate(Screen.Home)
            }
        )
        DockItem(
            icon = PremiumIcons.Search,
            label = "Search",
            selected = current is Screen.Home && isSearchForced,
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = onSearchClick
        )
        DockItem(icon = PremiumIcons.Library, label = "Library", selected = current is Screen.Library, isHorizontal = isHorizontal, indicatorAtTop = isTop, onClick = { onNavigate(Screen.Library) })
        DockItem(
            icon = PremiumIcons.Extensions,
            label = "Extensions",
            selected = current is Screen.Extensions,
            badge = savedRepos.size.takeIf { it > 0 }?.toString(),
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = { onNavigate(Screen.Extensions) },
        )
        DockItem(icon = PremiumIcons.Settings, label = "Settings", selected = current is Screen.Settings, isHorizontal = isHorizontal, indicatorAtTop = isTop, onClick = { onNavigate(Screen.Settings) })
    }

    val surfaceModifier = when {
        isBottom -> Modifier.padding(bottom = 14.dp).height(54.dp).wrapContentWidth()
        isTop -> Modifier.padding(top = 14.dp).height(54.dp).wrapContentWidth()
        isRight -> Modifier.padding(end = 14.dp).width(54.dp).wrapContentHeight()
        else -> Modifier.padding(start = 14.dp).width(54.dp).wrapContentHeight()
    }

    val paddingInsideSurface = if (isHorizontal) {
        Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
    } else {
        Modifier.padding(vertical = 14.dp, horizontal = 6.dp)
    }

    val updatesBoxPadding = when {
        isBottom -> Modifier.padding(bottom = 16.dp, end = 16.dp)
        isTop -> Modifier.padding(top = 16.dp, end = 16.dp)
        isRight -> Modifier.padding(end = 16.dp, bottom = 16.dp)
        else -> Modifier.padding(start = 16.dp, bottom = 16.dp)
    }

    val mainDockSurface = @Composable {
        Surface(
            modifier = surfaceModifier,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f), // Stronger frosted glass opacity
            shadowElevation = 4.dp, // Softer shadow for glass effect
            shape = RoundedCornerShape(16.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f) // Subtle light-catching border
            ),
        ) {
            if (isHorizontal) {
                Row(
                    modifier = paddingInsideSurface,
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dockItems()
                }
            } else {
                Column(
                    modifier = paddingInsideSurface,
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    dockItems()
                }
            }
        }
    }

    Box(modifier = modifier) {
        mainDockSurface()
    }
}

@Composable
private fun UpdatesNotificationBell(
    modifier: Modifier = Modifier,
    hasUnreadUpdates: Boolean,
    updatesHistory: List<com.lagradost.common.storage.PluginUpdateRecord>,
    onMarkUpdatesRead: () -> Unit,
) {
    var isUpdatesDialogExpanded by remember { mutableStateOf(false) }

    Box(modifier = modifier.padding(start = 16.dp, bottom = 16.dp)) {
        Surface(
            modifier = Modifier
                .width(48.dp)
                .height(48.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
            shadowElevation = 4.dp,
            shape = androidx.compose.foundation.shape.CircleShape,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
            ),
        ) {
            Box(contentAlignment = Alignment.Center) {
                DockItem(
                    icon = PremiumIcons.Updates,
                    label = "Updates",
                    showLabel = false,
                    selected = false,
                    badge = if (hasUnreadUpdates) "!" else null,
                    onClick = {
                        isUpdatesDialogExpanded = true
                        if (hasUnreadUpdates) {
                            onMarkUpdatesRead()
                        }
                    },
                )
            }
        }

        if (isUpdatesDialogExpanded) {
            androidx.compose.ui.window.Popup(
                alignment = Alignment.BottomStart,
                offset = androidx.compose.ui.unit.IntOffset(16, -16),
                onDismissRequest = { isUpdatesDialogExpanded = false },
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = DesktopUi.SurfaceElevated.copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
                    modifier = Modifier.width(360.dp).heightIn(max = 500.dp),
                    shadowElevation = 8.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(20.dp)) {
                        Text(
                            "Plugin Updates",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (updatesHistory.isEmpty()) {
                            Text(
                                "No plugin updates recorded recently.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp,
                            )
                        } else {
                            androidx.compose.foundation.lazy.LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                items(updatesHistory.size) { i ->
                                    val update = updatesHistory[i]
                                    val timeString = java.text.SimpleDateFormat("MMM dd, HH:mm").format(java.util.Date(update.timestamp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        coil3.compose.AsyncImage(
                                            model = update.iconUrl,
                                            contentDescription = null,
                                            modifier = Modifier.size(32.dp).clip(RoundedCornerShape(8.dp)).background(Color.White),
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                update.pluginName,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 14.sp,
                                            )
                                            Text(
                                                "Updated to v${update.version} • $timeString",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontSize = 12.sp,
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

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    showLabel: Boolean = false,
    selected: Boolean,
    badge: String? = null,
    isHorizontal: Boolean = false,
    indicatorAtTop: Boolean = false,
    onClick: () -> Unit,
) {
    val itemInteraction = remember { MutableInteractionSource() }
    val isHovered by itemInteraction.collectIsHoveredAsState()

    val theme = LocalDesktopTheme.current
    val iconTint = when {
        selected -> MaterialTheme.colorScheme.primary
        isHovered -> theme.TextPrimary
        else -> theme.TextMuted
    }
    val bgColor by animateColorAsState(
        targetValue = when {
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            isHovered -> theme.SurfaceElevated
            else -> Color.Transparent
        },
        label = "dockItemBg",
    )
    val scale by animateFloatAsState(
        targetValue = if (isHovered) 1.15f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "dockItemScale"
    )

    Box(
        modifier = Modifier
            .then(
                if (isHorizontal) Modifier.fillMaxHeight().width(56.dp)
                else Modifier.fillMaxWidth().height(56.dp)
            )
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .hoverable(itemInteraction)
            .clickable(onClick = onClick)
    ) {
        // Active indicator pill
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + when {
                indicatorAtTop -> slideInVertically { -it / 2 }
                isHorizontal -> slideInHorizontally { it / 2 }
                else -> slideInVertically { it / 2 }
            },
            exit = fadeOut() + when {
                indicatorAtTop -> slideOutVertically { -it / 2 }
                isHorizontal -> slideOutHorizontally { it / 2 }
                else -> slideOutVertically { it / 2 }
            },
            modifier = Modifier.align(when {
                indicatorAtTop -> Alignment.TopCenter
                isHorizontal -> Alignment.BottomCenter
                else -> Alignment.CenterStart
            })
        ) {
            Box(
                modifier = Modifier
                    .run {
                        when {
                            indicatorAtTop -> width(20.dp).height(3.dp)
                                .clip(RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                            isHorizontal -> width(20.dp).height(3.dp)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                            else -> width(3.dp).height(20.dp)
                                .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp))
                        }
                    }
                    .background(MaterialTheme.colorScheme.primary)
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier
                    .size(22.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
            )

            if (showLabel) {
                AnimatedVisibility(
                    visible = isHovered,
                    enter = fadeIn() + expandVertically() + slideInVertically { it / 2 },
                    exit = fadeOut() + shrinkVertically() + slideOutVertically { it / 2 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = if (selected) theme.TextPrimary else theme.TextMuted,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                        )
                    }
                }
            }
        }
    }
}

// Top Navigation Bar
@Composable
private fun TopBar(
    showBack: Boolean,
    onBack: () -> Unit,
    isHome: Boolean,
) {
    val bg = Color.Transparent
    Column(modifier = Modifier.fillMaxWidth().background(bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showBack) {
                val theme = LocalDesktopTheme.current
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(theme.SurfaceElevated.copy(alpha = 0.5f))
                        .clickable { onBack() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = theme.TextPrimary, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.weight(1f))

            // Global Toggles & Window Controls
            val windowState = com.lagradost.cloudstream3.desktop.ui.LocalWindowState.current
            val fullscreenController = com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController.current
            val isFullscreen = fullscreenController?.isFullscreen?.value ?: (windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)

            val theme = LocalDesktopTheme.current
            
        WindowControlsPill(isHome = isHome)
        }
        if (!isHome) {
            HorizontalDivider(color = LocalDesktopTheme.current.Divider, thickness = 0.5.dp)
        }
    }
}
