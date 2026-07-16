package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.animation.animateColorAsState
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
        Row(Modifier.fillMaxSize()) {
            // Navigation Dock
            NavigationDock(
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

            val ambientGlowEnabled by AppearanceConfig.ambientGlowEnabled.collectAsState()
            val isLightMode by AppearanceConfig.isLightMode.collectAsState()
            val primaryColor = MaterialTheme.colorScheme.primary

            // Main content Box
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .then(
                        if (ambientGlowEnabled && !isLightMode) {
                            Modifier.drawBehind {
                                drawRect(
                                    brush = Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.0f to primaryColor.copy(alpha = 0.15f),
                                            0.3f to primaryColor.copy(alpha = 0.08f),
                                            0.6f to primaryColor.copy(alpha = 0.03f),
                                            1.0f to Color.Transparent
                                        ),
                                        center = Offset(size.width / 2f, size.height / 2f),
                                        radius = size.width.coerceAtLeast(size.height) * 0.8f
                                    )
                                )
                            }
                        } else Modifier
                    ),
                contentAlignment = Alignment.TopCenter,
            ) {
                val contentPadding = if (current is Screen.Home) {
                    PaddingValues(0.dp)
                } else {
                    PaddingValues(top = 66.dp, start = 20.dp, end = 20.dp, bottom = 12.dp)
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
        }
    }
}

@Composable
private fun NavigationDock(
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

    Surface(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
        shadowElevation = 0.dp,
        shape = RoundedCornerShape(0.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // App icon at the top
            Spacer(Modifier.height(20.dp))
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource("logo_ui.png"),
                contentDescription = "App Logo",
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )

            // Main nav items, centered vertically
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                DockItem(
                    icon = Icons.Default.Home,
                    label = "Home",
                    selected = current is Screen.Home && !com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value,
                    onClick = {
                        com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value = false
                        onNavigate(Screen.Home)
                    }
                )
                DockItem(
                    icon = Icons.Default.Search,
                    label = "Search",
                    selected = current is Screen.Home && com.lagradost.cloudstream3.desktop.ui.DesktopUiState.forceShowSearchBar.value,
                    onClick = onSearchClick
                )
                DockItem(icon = Icons.Default.FavoriteBorder, label = "Library", selected = current is Screen.Library, onClick = { onNavigate(Screen.Library) })
                DockItem(
                    icon = Icons.Default.Extension,
                    label = "Extensions",
                    selected = current is Screen.Extensions,
                    badge = savedRepos.size.takeIf { it > 0 }?.toString(),
                    onClick = { onNavigate(Screen.Extensions) },
                )
                DockItem(icon = Icons.Default.Settings, label = "Settings", selected = current is Screen.Settings, onClick = { onNavigate(Screen.Settings) })
            }

            // Bottom actions, pushed to bottom
            Spacer(Modifier.weight(1f))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(horizontal = 8.dp),
            ) {
                Box {
                    DockItem(
                        icon = Icons.Default.Notifications,
                        label = "Updates",
                        selected = false,
                        badge = if (hasUnreadUpdates) "!" else null,
                        onClick = {
                            isUpdatesDialogExpanded = true
                            if (hasUnreadUpdates) {
                                onMarkUpdatesRead()
                            }
                        },
                    )

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
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun DockItem(
    icon: ImageVector,
    label: String,
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
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
            isHovered -> theme.SurfaceElevated
            else -> Color.Transparent
        },
        label = "dockItemBg",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .hoverable(itemInteraction)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = label, tint = iconTint, modifier = Modifier.size(24.dp))

        if (isHovered) {
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

            if (isHome) {
                IconButton(
                    onClick = { DesktopUiState.forceProviderRefresh.value += 1 },
                    modifier = Modifier.size(38.dp),
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.TextPrimary, modifier = Modifier.size(20.dp))
                }
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
