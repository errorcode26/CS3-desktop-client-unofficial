package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.extensions.compose.stack.Children
import com.arkivanov.decompose.extensions.compose.stack.animation.fade
import com.arkivanov.decompose.extensions.compose.stack.animation.plus
import com.arkivanov.decompose.extensions.compose.stack.animation.scale
import com.arkivanov.decompose.extensions.compose.stack.animation.stackAnimation
import com.arkivanov.decompose.extensions.compose.subscribeAsState
import com.lagradost.cloudstream3.desktop.ui.navigation.RootComponent
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeDetailsScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeHomeScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeLibraryScreen
import com.lagradost.cloudstream3.desktop.ui.screens.extensions.ComposeExtensionScreen
import com.lagradost.common.storage.WatchHistory

data class VideoLaunchData(
    val links: List<com.lagradost.cloudstream3.utils.ExtractorLink> = emptyList(),
    val initialIndex: Int,
    val title: String?,
    val subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    val startPositionMs: Long,
    val history: WatchHistory,
    val loadResponse: com.lagradost.cloudstream3.LoadResponse? = null,
)

val LocalVideoPlayer = androidx.compose.runtime.staticCompositionLocalOf<(VideoLaunchData?) -> Unit> { { } }
val LocalVideoPlayerActive = androidx.compose.runtime.staticCompositionLocalOf<Boolean> { false }
val LocalWindowState = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.window.WindowState?> { null }
val LocalComposeWindow = androidx.compose.runtime.staticCompositionLocalOf<java.awt.Window?> { null }

@androidx.compose.runtime.Stable
class SearchUiState(
    isSearchForced: Boolean = false,
    searchFocusTrigger: Int = 0,
) {
    var isSearchForced by androidx.compose.runtime.mutableStateOf(isSearchForced)
    var searchFocusTrigger by androidx.compose.runtime.mutableStateOf(searchFocusTrigger)
}

/**
 * Provides real AWT exclusive fullscreen control across the entire Compose tree.
 * Uses GraphicsDevice.setFullScreenWindow() which is the only way to get true fullscreen on Windows
 * (WindowPlacement.Fullscreen is "fake" — the OS title bar and taskbar still render on top).
 */
@androidx.compose.runtime.Stable
class FullscreenController(
    isFullscreen: Boolean,
    var toggle: () -> Unit,
    popupKey: Int = 0,
    var mainFrame: java.awt.Window? = null,
    /**
     * Tracks the main window's content-pane size in physical pixels.
     * Updated from an AWT ComponentListener on the EDT, so it always reflects
     * the true post-resize dimensions — unlike BoxWithConstraints which can
     * report stale values during the fullscreen ↔ maximized transition.
     * Zero means "not yet measured; fall back to BoxWithConstraints."
     */
    contentAreaPx: Pair<Int, Int> = Pair(0, 0),
) {
    var isFullscreen by androidx.compose.runtime.mutableStateOf(isFullscreen)
    var popupKey by androidx.compose.runtime.mutableStateOf(popupKey)
    var contentAreaPx by androidx.compose.runtime.mutableStateOf(contentAreaPx)
}
val LocalFullscreenController = androidx.compose.runtime.staticCompositionLocalOf<FullscreenController?> { null }

@androidx.compose.ui.ExperimentalComposeUiApi
@Composable
fun CloudstreamApp(rootComponent: RootComponent) {
    var showErrorsDialog by remember { mutableStateOf(false) }
    var currentVideo by remember { mutableStateOf<VideoLaunchData?>(null) }
    val childStack by rootComponent.childStack.subscribeAsState()

    val isLightMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.isLightMode.collectAsState()
    val themeAccent by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.themeAccent.collectAsState()
    val appThemeBackground by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.appThemeBackground.collectAsState()
    val customThemeAccent by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.customThemeAccent.collectAsState()
    val customAppThemeBackground by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.customAppThemeBackground.collectAsState()

    val primaryColor = com.lagradost.cloudstream3.desktop.ui.theme.accentColorFromName(themeAccent, customThemeAccent)
    val desktopColors = com.lagradost.cloudstream3.desktop.ui.theme.buildDesktopColors(primaryColor, isLightMode, appThemeBackground, customAppThemeBackground)
    val selectedFont by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.selectedFont.collectAsState()
    val typography = androidx.compose.runtime.remember(selectedFont) {
        com.lagradost.cloudstream3.desktop.ui.theme.buildTypography(
            com.lagradost.cloudstream3.desktop.ui.theme.getFontFamily(selectedFont),
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalVideoPlayer provides { currentVideo = it },
        LocalVideoPlayerActive provides (currentVideo != null),
        com.lagradost.cloudstream3.desktop.ui.components.LocalDesktopTheme provides desktopColors,
    ) {
        val appColorScheme = com.lagradost.cloudstream3.desktop.ui.theme.buildColorScheme(primaryColor, desktopColors, isLightMode)

        androidx.compose.material3.MaterialTheme(colorScheme = appColorScheme, typography = typography) {
            androidx.compose.material3.Surface(
                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                color = androidx.compose.material3.MaterialTheme.colorScheme.background,
            ) {
                androidx.compose.foundation.layout.Box(
                    modifier = androidx.compose.ui.Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type == PointerEventType.Release) {
                                        // Ignore back/forward navigation if the video player is open
                                        if (currentVideo == null) {
                                            when (event.button) {
                                                PointerButton.Back -> {
                                                    rootComponent.pop()
                                                }
                                                PointerButton.Forward -> {
                                                    // Decompose doesn't natively have forward stack out of the box unless implemented.
                                                    // We can ignore forward for now or implement it later.
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    val blurRadius by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (com.lagradost.cloudstream3.desktop.ui.components.GlobalDialogState.isAnyDialogOpen ||
                            com.lagradost.cloudstream3.desktop.ui.components.GlobalContextMenuState.isActive
                        ) {
                            16.dp
                        } else {
                            0.dp
                        },
                    )

                    androidx.compose.foundation.layout.Box(
                        modifier = androidx.compose.ui.Modifier.fillMaxSize().blur(blurRadius),
                    ) {
                        val activeInstance = childStack.active.instance

                        val title = when (activeInstance) {
                            is RootComponent.Child.Home -> "Home"
                            is RootComponent.Child.History -> "Watch History"
                            is RootComponent.Child.Search -> "Search"
                            is RootComponent.Child.Extensions -> "Extensions"
                            is RootComponent.Child.Library -> "Library"
                            is RootComponent.Child.Settings -> "Settings"
                            is RootComponent.Child.CategoryGrid -> activeInstance.title
                            is RootComponent.Child.Details -> null
                        }
                        val applySafePadding = when (activeInstance) {
                            is RootComponent.Child.Details -> false // Details manually pads itself
                            is RootComponent.Child.Home -> false // Home needs full-bleed for Hero
                            else -> true
                        }
                        val showDock = when (activeInstance) {
                            is RootComponent.Child.Details -> false
                            else -> true
                        }
                        val showTopBar = when (activeInstance) {
                            is RootComponent.Child.Details -> false
                            else -> true
                        }

                        DesktopAppShell(
                            onNavigate = { config -> rootComponent.bringToFront(config) },
                            onBack = { rootComponent.pop() },
                            title = title,
                            homeUiState = (activeInstance as? RootComponent.Child.Home)?.component?.viewModel?.uiState?.collectAsState()?.value,
                            homeActionDispatcher = { ev -> (activeInstance as? RootComponent.Child.Home)?.component?.viewModel?.onEvent(ev) },
                            showDock = showDock,
                            showTopBar = showTopBar,
                            applySafePadding = applySafePadding,
                        ) {
                            Children(
                                stack = childStack,
                                modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                                animation = stackAnimation(fade() + scale()),
                            ) {
                                when (val child = it.instance) {
                                    is RootComponent.Child.Details -> {
                                        val api = child.component.api
                                        if (api != null) {
                                            ComposeDetailsScreen(
                                                onBack = { rootComponent.pop() },
                                                onNavigate = { config -> rootComponent.bringToFront(config) },
                                                viewModel = child.component.viewModel,
                                                autoPlay = child.component.config.autoPlay,
                                            )
                                        } else {
                                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                                androidx.compose.material3.Text("Plugin unloaded. Cannot load details.")
                                            }
                                        }
                                    }

                                    is RootComponent.Child.Home -> {
                                        ComposeHomeScreen(
                                            onNavigate = { config -> rootComponent.bringToFront(config) },
                                            viewModel = child.component.viewModel,
                                        )
                                    }
                                    is RootComponent.Child.History -> {
                                        com.lagradost.cloudstream3.desktop.ui.screens.ComposeHistoryScreen(onNavigate = { rootComponent.bringToFront(it) })
                                    }
                                    is RootComponent.Child.Search -> {
                                        com.lagradost.cloudstream3.desktop.ui.screens.search.ComposeSearchScreen(
                                            onNavigate = { config -> rootComponent.bringToFront(config) },
                                            viewModel = child.component.viewModel,
                                        )
                                    }
                                    is RootComponent.Child.Extensions -> {
                                        ComposeExtensionScreen(onNavigate = { rootComponent.bringToFront(it) }, child.initialTab)
                                    }
                                    is RootComponent.Child.Library -> {
                                        ComposeLibraryScreen(onNavigate = { rootComponent.bringToFront(it) })
                                    }
                                    is RootComponent.Child.Settings -> {
                                        com.lagradost.cloudstream3.desktop.ui.screens.settings.ComposeSettingsScreen(
                                            onNavigate = { config -> rootComponent.bringToFront(config) },
                                        )
                                    }
                                    is RootComponent.Child.CategoryGrid -> {
                                        val api = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(child.providerName)
                                        if (api != null) {
                                            val items = com.lagradost.cloudstream3.desktop.ui.screens.CategoryGridCache.get(child.providerName, child.title) ?: emptyList()
                                            com.lagradost.cloudstream3.desktop.ui.screens.ComposeCategoryGridScreen(onNavigate = { rootComponent.bringToFront(it) }, onBack = { rootComponent.pop() }, api, child.title, items)
                                        } else {
                                            androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                                                androidx.compose.material3.Text("Plugin unloaded. Cannot load category.")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Context Menu Overlay (unblurred)
                    com.lagradost.cloudstream3.desktop.ui.components.ContextMenuOverlay()

                    var showExitFade by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    // The Embedded Video Player Overlay
                    currentVideo?.let { launchData ->
                        com.lagradost.cloudstream3.desktop.ui.screens.player.EmbeddedVideoPlayer(
                            launchData = launchData,
                            isExiting = showExitFade,
                            onClose = {
                                showExitFade = true
                            },
                            onError = { err ->
                                com.lagradost.cloudstream3.desktop.DesktopErrorReporter.report("Player Error: $err")
                                showErrorsDialog = true
                            },
                        )
                    }

                    val exitFadeAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (showExitFade) 1f else 0f,
                        animationSpec = if (showExitFade) androidx.compose.animation.core.snap() else androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                        label = "exitFadeAlpha",
                    )

                    if (exitFadeAlpha > 0f) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = exitFadeAlpha)),
                        )
                    }

                    androidx.compose.runtime.LaunchedEffect(showExitFade) {
                        if (showExitFade) {
                            // Wait for the Compose UI to snap to black and the isExiting state to propagate
                            // down to the player components so they can hide the AWT canvases BEFORE destruction.
                            // We wait for 2 frames to guarantee the black box is physically painted on screen,
                            // regardless of any JIT compilation pauses that might occur the first time.
                            androidx.compose.runtime.withFrameNanos { }
                            androidx.compose.runtime.withFrameNanos { }

                            // Destroy the native player instantly (no white flash due to BLACK_BRUSH).
                            // The heavy C++ teardown runs on a daemon thread in BaseMpvPlayer.
                            currentVideo = null

                            // Wait another 50ms just to ensure the native window is completely gone from the OS compositor
                            kotlinx.coroutines.delay(50)

                            // Trigger the smooth fade-out of the black box to reveal the Compose UI!
                            showExitFade = false
                        }
                    }

                    com.lagradost.cloudstream3.desktop.ui.components.CloudstreamCustomDialog(
                        show = showErrorsDialog,
                        onDismissRequest = { showErrorsDialog = false },
                        modifier = androidx.compose.ui.Modifier.fillMaxWidth(0.8f).fillMaxHeight(0.8f),
                    ) {
                        androidx.compose.foundation.layout.Column(modifier = androidx.compose.ui.Modifier.padding(20.dp)) {
                            androidx.compose.material3.Text("Error Logs", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                            val errorSnapshot = com.lagradost.cloudstream3.desktop.DesktopErrorReporter.getSnapshot()
                            androidx.compose.material3.OutlinedTextField(
                                value = errorSnapshot,
                                onValueChange = {},
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth().weight(1f),
                            )

                            androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.height(16.dp))

                            androidx.compose.foundation.layout.Row(
                                modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.End,
                            ) {
                                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                androidx.compose.material3.TextButton(onClick = {
                                    clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(errorSnapshot))
                                }) {
                                    androidx.compose.material3.Text("Copy")
                                }
                                androidx.compose.foundation.layout.Spacer(modifier = androidx.compose.ui.Modifier.width(8.dp))
                                androidx.compose.material3.Button(onClick = {
                                    showErrorsDialog = false
                                }) {
                                    androidx.compose.material3.Text("Close")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
