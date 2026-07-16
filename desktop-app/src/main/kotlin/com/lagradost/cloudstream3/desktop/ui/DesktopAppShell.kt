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

object DesktopUiState {
    val forceShowSearchBar = MutableStateFlow(false)
    val searchFocusTrigger = MutableStateFlow(0)
    val forceProviderRefresh = MutableStateFlow(0)
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

    val hasUnreadUpdates by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.hasUnreadUpdates() }
        .collectAsState(initial = DesktopDataStore.hasUnreadUpdates())

    val updatesHistory by DesktopDataStore.pluginUpdatesFlow
        .map { DesktopDataStore.getUpdatesHistory() }
        .collectAsState(initial = DesktopDataStore.getUpdatesHistory())

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
            val isLightMode by AppearanceConfig.isLightMode.collectAsState()
            val primaryColor = MaterialTheme.colorScheme.primary

            // Main content Box
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (ambientGlowEnabled && !isLightMode) {
                            Modifier.drawBehind {
                                ambientGlowPositions.forEach { position ->
                                    val centerOffset = when (position) {
                                        "Top" -> Offset(size.width / 2f, 0f)
                                        "Bottom" -> Offset(size.width / 2f, size.height)
                                        "Left" -> Offset(0f, size.height / 2f)
                                        "Right" -> Offset(size.width, size.height / 2f)
                                        "Top Left" -> Offset(0f, 0f)
                                        "Top Right" -> Offset(size.width, 0f)
                                        "Bottom Left" -> Offset(0f, size.height)
                                        "Bottom Right" -> Offset(size.width, size.height)
                                        else -> Offset(size.width / 2f, size.height / 2f) // Center
                                    }
                                    drawRect(
                                        brush = Brush.radialGradient(
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
                        } else Modifier
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                val contentPadding = if (current is Screen.Home) {
                    PaddingValues(0.dp)
                } else {
                    PaddingValues(top = 66.dp, start = 88.dp, end = 20.dp, bottom = 12.dp)
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

                    TopBar(
                        showBack = showBack,
                        onBack = { navController.goBack() },
                        isHome = current is Screen.Home,
                    )
                }

                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
                )
            }
            
            // Navigation Dock (Floating on left)
            NavigationDock(
                modifier = Modifier.align(Alignment.CenterStart),
                current = current,
                isSyncing = isSyncing,
                hasUnreadUpdates = hasUnreadUpdates,
                updatesHistory = updatesHistory,
                onNavigate = { navController.navigate(it) },
                onSearchClick = {
                    if (current !is Screen.Home) {
                        navController.navigate(Screen.Home)
                    }
                    DesktopUiState.forceShowSearchBar.value = true
                    DesktopUiState.searchFocusTrigger.value += 1
                },
                onMarkUpdatesRead = { DesktopDataStore.setUnreadUpdates(false) },
            )
        }
    }
}

@Composable
private fun NavigationDock(
    modifier: Modifier = Modifier,
    current: Screen,
    isSyncing: Boolean,
    hasUnreadUpdates: Boolean,
    updatesHistory: List<com.lagradost.common.storage.PluginUpdateRecord>,
    onNavigate: (Screen) -> Unit,
    onSearchClick: () -> Unit,
    onMarkUpdatesRead: () -> Unit,
) {
    val savedRepos by DesktopRepositoryManager.savedRepositories.collectAsState()
    var isUpdatesDialogExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top spacer to push the main pill to the center
        Spacer(modifier = Modifier.weight(1f))

        Surface(
            modifier = Modifier
                .padding(start = 16.dp)
                .width(72.dp)
                .wrapContentHeight(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(36.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
            ),
        ) {
            Column(
                modifier = Modifier.padding(vertical = 16.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DockItem(
                    icon = PremiumIcons.Home,
                    label = "Home",
                    selected = current is Screen.Home && !com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value,
                    onClick = {
                        com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value = false
                        onNavigate(Screen.Home)
                    }
                )
                DockItem(
                    icon = PremiumIcons.Search,
                    label = "Search",
                    selected = current is Screen.Home && com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value,
                    onClick = onSearchClick
                )
                DockItem(icon = PremiumIcons.Library, label = "Library", selected = current is Screen.Library, onClick = { onNavigate(Screen.Library) })
                DockItem(
                    icon = PremiumIcons.Extensions,
                    label = "Extensions",
                    selected = current is Screen.Extensions,
                    badge = savedRepos.size.takeIf { it > 0 }?.toString(),
                    onClick = { onNavigate(Screen.Extensions) },
                )
                DockItem(icon = PremiumIcons.Settings, label = "Settings", selected = current is Screen.Settings, onClick = { onNavigate(Screen.Settings) })
            }
        }

        // Bottom spacer to push the main pill to the center, and the updates button to the bottom
        Spacer(modifier = Modifier.weight(1f))

        Box(modifier = Modifier.padding(start = 16.dp, bottom = 16.dp)) {
            Surface(
                modifier = Modifier
                    .width(48.dp)
                    .height(48.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                shape = androidx.compose.foundation.shape.CircleShape,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
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
                    alignment = Alignment.BottomEnd,
                    offset = androidx.compose.ui.unit.IntOffset(16, -16),
                    onDismissRequest = { isUpdatesDialogExpanded = false },
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = DesktopUi.SurfaceElevated.copy(alpha = 0.95f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)),
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
                                        val timeString = SimpleDateFormat("MMM dd, HH:mm").format(Date(update.timestamp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            AsyncImage(
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
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
    showLabel: Boolean = true,
    selected: Boolean,
    badge: String? = null,
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
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .hoverable(itemInteraction)
            .clickable(onClick = onClick)
    ) {
        // Active indicator pill
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + slideInVertically { it / 2 },
            exit = fadeOut() + slideOutVertically { it / 2 },
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
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
                    .size(24.dp)
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

            // Global Toggles
            val windowState = com.lagradost.cloudstream3.desktop.ui.LocalWindowState.current
            val fullscreenController = com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController.current
            val isFullscreen = fullscreenController?.isFullscreen?.value ?: (windowState?.placement == androidx.compose.ui.window.WindowPlacement.Fullscreen)

            val theme = LocalDesktopTheme.current

            IconButton(
                onClick = { DesktopUiState.forceProviderRefresh.value += 1 },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.TextPrimary, modifier = Modifier.size(20.dp))
            }

            IconButton(
                onClick = {
                    if (fullscreenController != null) {
                        fullscreenController.toggle()
                    } else {
                        windowState?.placement = if (isFullscreen) {
                            androidx.compose.ui.window.WindowPlacement.Floating
                        } else {
                            androidx.compose.ui.window.WindowPlacement.Fullscreen
                        }
                    }
                },
                modifier = Modifier.size(38.dp),
            ) {
                Icon(
                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    contentDescription = "Fullscreen",
                    tint = theme.TextPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (!isHome) {
            HorizontalDivider(color = LocalDesktopTheme.current.Divider, thickness = 0.5.dp)
        }
    }
}
