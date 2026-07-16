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
import com.lagradost.cloudstream3.desktop.ui.components.DockItem
import com.lagradost.cloudstream3.desktop.ui.components.UpdatesNotificationBell
import com.lagradost.cloudstream3.desktop.ui.components.TopBar

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
            label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.HOME,
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
            label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.SEARCH,
            selected = current is Screen.Home && isSearchForced,
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = onSearchClick
        )
        DockItem(icon = PremiumIcons.Library, label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.LIBRARY, selected = current is Screen.Library, isHorizontal = isHorizontal, indicatorAtTop = isTop, onClick = { onNavigate(Screen.Library) })
        DockItem(
            icon = PremiumIcons.Extensions,
            label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.EXTENSIONS,
            selected = current is Screen.Extensions,
            isHorizontal = isHorizontal,
            indicatorAtTop = isTop,
            onClick = { onNavigate(Screen.Extensions) },
        )
        DockItem(icon = PremiumIcons.Settings, label = com.lagradost.cloudstream3.desktop.utils.DesktopStrings.SETTINGS, selected = current is Screen.Settings, isHorizontal = isHorizontal, indicatorAtTop = isTop, onClick = { onNavigate(Screen.Settings) })
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

