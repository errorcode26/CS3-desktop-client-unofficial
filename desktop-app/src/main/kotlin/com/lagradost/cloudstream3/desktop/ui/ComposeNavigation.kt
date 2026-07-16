package com.lagradost.cloudstream3.desktop.ui

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.lagradost.cloudstream3.desktop.ui.navigation.NavController
import com.lagradost.cloudstream3.desktop.ui.navigation.Screen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeDetailsScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeExtensionScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeHomeScreen
import com.lagradost.cloudstream3.desktop.ui.screens.ComposeLibraryScreen
import com.lagradost.common.storage.WatchHistory

data class VideoLaunchData(
    val links: List<com.lagradost.cloudstream3.utils.ExtractorLink> = emptyList(),
    val initialIndex: Int,
    val title: String?,
    val subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    val startPositionMs: Long,
    val history: WatchHistory,
    val loadResponse: com.lagradost.cloudstream3.LoadResponse? = null,
    val onError: ((String) -> Unit)? = null,
    val onClosed: (() -> Unit)? = null,
)

val LocalVideoPlayer = androidx.compose.runtime.staticCompositionLocalOf<(VideoLaunchData?) -> Unit> { { } }
val LocalWindowState = androidx.compose.runtime.staticCompositionLocalOf<androidx.compose.ui.window.WindowState?> { null }
val LocalComposeWindow = androidx.compose.runtime.staticCompositionLocalOf<java.awt.Window?> { null }

/**
 * Provides real AWT exclusive fullscreen control across the entire Compose tree.
 * Uses GraphicsDevice.setFullScreenWindow() which is the only way to get true fullscreen on Windows
 * (WindowPlacement.Fullscreen is "fake" — the OS title bar and taskbar still render on top).
 */
data class FullscreenController(
    val isFullscreen: androidx.compose.runtime.MutableState<Boolean>,
    val toggle: () -> Unit,
    val popupKey: androidx.compose.runtime.MutableState<Int> = androidx.compose.runtime.mutableStateOf(0),
    val mainFrame: java.awt.Window? = null,
    /**
     * Tracks the main window's content-pane size in physical pixels.
     * Updated from an AWT ComponentListener on the EDT, so it always reflects
     * the true post-resize dimensions — unlike BoxWithConstraints which can
     * report stale values during the fullscreen ↔ maximized transition.
     * Zero means "not yet measured; fall back to BoxWithConstraints."
     */
    val contentAreaPx: androidx.compose.runtime.MutableState<Pair<Int, Int>> =
        androidx.compose.runtime.mutableStateOf(Pair(0, 0)),
)
val LocalFullscreenController = androidx.compose.runtime.staticCompositionLocalOf<FullscreenController?> { null }

@androidx.compose.ui.ExperimentalComposeUiApi
@Composable
fun CloudstreamApp() {
    val navController = remember { NavController() }
    var showErrorsDialog by remember { mutableStateOf(false) }
    var currentVideo by remember { mutableStateOf<VideoLaunchData?>(null) }
    val screen = navController.currentScreen

    val isLightMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.isLightMode.collectAsState()
    val themeAccent by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.themeAccent.collectAsState()
    val amoledMode by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.amoledMode.collectAsState()
    val primaryColor = com.lagradost.cloudstream3.desktop.ui.theme.accentColorFromName(themeAccent)
    val desktopColors = com.lagradost.cloudstream3.desktop.ui.theme.buildDesktopColors(primaryColor, isLightMode, amoledMode)
    val selectedFont by com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.selectedFont.collectAsState()
    val typography = androidx.compose.runtime.remember(selectedFont) {
        com.lagradost.cloudstream3.desktop.ui.theme.buildTypography(
            com.lagradost.cloudstream3.desktop.ui.theme.getFontFamily(selectedFont),
        )
    }

    androidx.compose.runtime.CompositionLocalProvider(
        LocalVideoPlayer provides { currentVideo = it },
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
                                                    if (navController.canGoBack()) navController.goBack()
                                                }
                                                PointerButton.Forward -> {
                                                    if (navController.canGoForward()) navController.goForward()
                                                }
                                                else -> {}
                                            }
                                        }
                                    }
                                }
                            }
                        },
                ) {
                    val saveableStateHolder = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

                    androidx.compose.animation.AnimatedContent(
                        targetState = screen,
                        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.TopStart,
                        transitionSpec = {
                            val isTopLevelTarget = targetState is Screen.Home || targetState is Screen.Library || targetState is Screen.Extensions || targetState is Screen.Settings
                            val isTopLevelInitial = initialState is Screen.Home || initialState is Screen.Library || initialState is Screen.Extensions || initialState is Screen.Settings

                            val isTabSwitch = isTopLevelInitial && isTopLevelTarget
                            val isPush = navController.lastAction == com.lagradost.cloudstream3.desktop.ui.navigation.NavController.NavAction.Push && !isTabSwitch
                            val isPop = navController.lastAction == com.lagradost.cloudstream3.desktop.ui.navigation.NavController.NavAction.Pop && !isTabSwitch

                            val isToDetails = targetState is Screen.Details
                            val isFromDetails = initialState is Screen.Details

                            if (isPush) {
                                if (isToDetails) {
                                    // Smooth fade and slight scale-in for Details, without scaling the Home screen
                                    (
                                        androidx.compose.animation.fadeIn(
                                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        ) + androidx.compose.animation.scaleIn(
                                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            initialScale = 0.95f,
                                        )
                                        ).togetherWith(
                                        androidx.compose.animation.fadeOut(
                                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        ),
                                    ).apply { targetContentZIndex = 1f }
                                } else {
                                    // Shared Axis Z — push: new screen scales up from 92%, old screen zooms away to 108%
                                    (
                                        androidx.compose.animation.fadeIn(
                                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        ) + androidx.compose.animation.scaleIn(
                                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            initialScale = 0.92f,
                                        )
                                        ).togetherWith(
                                        androidx.compose.animation.fadeOut(
                                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        ) + androidx.compose.animation.scaleOut(
                                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            targetScale = 1.08f,
                                        ),
                                    ).apply { targetContentZIndex = 1f }
                                }
                            } else if (isPop) {
                                if (isFromDetails) {
                                    // Smooth fade out and scale down for Details, without scaling the Home screen
                                    (
                                        androidx.compose.animation.fadeIn(
                                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        )
                                        ).togetherWith(
                                        androidx.compose.animation.fadeOut(
                                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        ) + androidx.compose.animation.scaleOut(
                                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            targetScale = 0.95f,
                                        ),
                                    ).apply { targetContentZIndex = -1f }
                                } else {
                                    // Shared Axis Z — pop: old screen shrinks back to 92%, previous screen zooms in from 108%
                                    (
                                        androidx.compose.animation.fadeIn(
                                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        ) + androidx.compose.animation.scaleIn(
                                            animationSpec = androidx.compose.animation.core.tween(300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            initialScale = 1.08f,
                                        )
                                        ).togetherWith(
                                        androidx.compose.animation.fadeOut(
                                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                        ) + androidx.compose.animation.scaleOut(
                                            animationSpec = androidx.compose.animation.core.tween(220, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                            targetScale = 0.92f,
                                        ),
                                    ).apply { targetContentZIndex = -1f }
                                }
                            } else {
                                // Tab switch: simple crossfade — no depth needed for same-level navigation
                                androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) togetherWith
                                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300))
                            }
                        },
                        label = "screen_transition",
                    ) { targetScreen ->
                        saveableStateHolder.SaveableStateProvider(targetScreen) {
                            when (targetScreen) {
                                is Screen.Details -> ComposeDetailsScreen(navController, targetScreen.provider, targetScreen.url, targetScreen.preloadedName, targetScreen.preloadedPoster, targetScreen.preloadedBg, targetScreen.autoPlay)

                                is Screen.Home -> DesktopAppShell(
                                    navController = navController,
                                    title = "Home",
                                    onErrorLogs = { showErrorsDialog = true },
                                ) {
                                    ComposeHomeScreen(
                                        navController = navController,
                                    )
                                }
                                is Screen.Extensions -> DesktopAppShell(
                                    navController = navController,
                                    title = "Extensions",
                                    onErrorLogs = { showErrorsDialog = true },
                                ) {
                                    ComposeExtensionScreen(navController)
                                }
                                is Screen.Library -> DesktopAppShell(
                                    navController = navController,
                                    title = "Library",
                                    onErrorLogs = { showErrorsDialog = true },
                                ) {
                                    ComposeLibraryScreen(navController)
                                }
                                is Screen.Settings -> DesktopAppShell(
                                    navController = navController,
                                    title = "Settings",
                                ) {
                                    com.lagradost.cloudstream3.desktop.ui.screens.settings.ComposeSettingsScreen(
                                        navController = navController,
                                        onErrorLogs = { showErrorsDialog = true },
                                    )
                                }
                                is Screen.CategoryGrid -> DesktopAppShell(
                                    navController = navController,
                                    title = targetScreen.title,
                                    showBack = true,
                                    onErrorLogs = { showErrorsDialog = true },
                                ) {
                                    com.lagradost.cloudstream3.desktop.ui.screens.ComposeCategoryGridScreen(navController, targetScreen.provider, targetScreen.title, targetScreen.items)
                                }
                            }
                        }
                    }

                    var showExitFade by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                    // The Embedded Video Player Overlay
                    if (currentVideo != null) {
                        currentVideo?.let { launchData ->
                            com.lagradost.cloudstream3.desktop.ui.screens.player.EmbeddedVideoPlayer(
                                launchData = launchData,
                                isExiting = false,
                                onClose = {
                                    showExitFade = true
                                },
                            )
                        }
                    }

                    val exitFadeAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (showExitFade) 1f else 0f,
                        animationSpec = if (showExitFade) androidx.compose.animation.core.tween(400) else androidx.compose.animation.core.tween(800, easing = androidx.compose.animation.core.LinearEasing),
                        label = "exitFadeAlpha",
                    )

                    if (exitFadeAlpha > 0f) {
                        androidx.compose.foundation.layout.Box(
                            modifier = androidx.compose.ui.Modifier
                                .fillMaxSize()
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = exitFadeAlpha)),
                        )
                    }

                    val fsController = LocalFullscreenController.current
                    androidx.compose.runtime.LaunchedEffect(showExitFade) {
                        if (showExitFade) {
                            // Raise the native DWM shield to block the WebView2 destruction flash
                            com.lagradost.cloudstream3.desktop.player.DesktopPlayerShield.showForActiveWindow()

                            // Smoothly fade to black over 400ms before destroying the player
                            kotlinx.coroutines.delay(450)

                            // Destroy the native player. The screen stays black because our shield is completely opaque!
                            currentVideo = null

                            // Wait 50ms more to ensure the native window is truly gone from Windows DWM
                            kotlinx.coroutines.delay(50)

                            // Drop the shield and let Compose's exit fade take over!
                            com.lagradost.cloudstream3.desktop.player.DesktopPlayerShield.hideAfter(100)

                            // Do NOT exit fullscreen here, as the user wants to stay in fullscreen
                            // if they were already in it before opening the player!

                            // Trigger the smooth fade-out!
                            showExitFade = false
                        }
                    }

                    if (showErrorsDialog) {
                        androidx.compose.material3.AlertDialog(
                            onDismissRequest = { showErrorsDialog = false },
                            title = { androidx.compose.material3.Text("Error Logs") },
                            text = {
                                val errorSnapshot = com.lagradost.cloudstream3.desktop.DesktopErrorReporter.getSnapshot()
                                androidx.compose.material3.OutlinedTextField(
                                    value = errorSnapshot,
                                    onValueChange = {},
                                    modifier = androidx.compose.ui.Modifier.fillMaxWidth().height(400.dp),
                                )
                            },
                            confirmButton = {
                                val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
                                val errorSnapshot = com.lagradost.cloudstream3.desktop.DesktopErrorReporter.getSnapshot()
                                androidx.compose.foundation.layout.Row {
                                    androidx.compose.material3.Button(onClick = {
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
                            },
                        )
                    }
                }
            }
        }
    }
}
