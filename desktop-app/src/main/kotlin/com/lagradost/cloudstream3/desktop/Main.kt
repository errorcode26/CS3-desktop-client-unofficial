@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class, androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.lagradost.cloudstream3.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.arkivanov.decompose.DefaultComponentContext
import com.arkivanov.essenty.lifecycle.LifecycleRegistry
import com.arkivanov.essenty.lifecycle.destroy
import com.arkivanov.essenty.lifecycle.resume
import com.lagradost.cloudstream3.desktop.init.initCoil
import com.lagradost.cloudstream3.desktop.init.initCrashHandler
import com.lagradost.cloudstream3.desktop.init.initNetwork
import com.lagradost.cloudstream3.desktop.init.initPlugins
import com.lagradost.cloudstream3.desktop.init.initProviders
import com.lagradost.cloudstream3.desktop.init.initProxy
import com.lagradost.cloudstream3.desktop.init.initSecurity
import com.lagradost.cloudstream3.desktop.init.initWindowsEnvironment
import com.lagradost.cloudstream3.desktop.init.launchAutoUpdater
import com.lagradost.cloudstream3.desktop.init.launchPeriodicPluginUpdater
import com.lagradost.cloudstream3.desktop.init.rememberFullscreenHelper
import com.lagradost.cloudstream3.desktop.init.setupWindowBackgroundAndListeners
import com.lagradost.cloudstream3.desktop.player.ShaderManager
import com.lagradost.cloudstream3.desktop.ui.CloudstreamApp
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.cloudstream3.desktop.ui.components.PreAlphaNoticeDialog
import com.lagradost.cloudstream3.desktop.ui.navigation.DefaultRootComponent
import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioState
import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioView
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.awt.Toolkit

/**
 * Single unified entry point for CloudStream Desktop Client.
 */
fun main(args: Array<String> = emptyArray()) {
    initCrashHandler()
    initWindowsEnvironment()

    val safeModeSentinel = java.io.File(PlatformPaths.appDataDir, ".safe_mode")
    val isSafeModeRequested = args.any { it.equals("--safe-mode", ignoreCase = true) || it.equals("--safe", ignoreCase = true) } ||
        System.getProperty("cloudstream.safe") != null ||
        safeModeSentinel.exists()

    if (isSafeModeRequested) {
        com.lagradost.cloudstream3.desktop.init.SafeModeState.enable()
        try {
            safeModeSentinel.delete()
        } catch (_: Throwable) {}
        AppLogger.w("Running in SAFE MODE: 3rd-party plugins and non-essential startup routines bypassed.")
    }

    val isDevMode = args.any { it.equals("--dev", ignoreCase = true) || it.equals("--dev-logger", ignoreCase = true) } ||
        System.getProperty("cloudstream.dev") != null

    AppLogger.i("Launching CloudStream Desktop Client...")
    AppLogger.i("Platform: ${PlatformPaths.currentOS}")
    AppLogger.i("App data directory: ${PlatformPaths.appDataDir.absolutePath}")

    if (isDevMode) {
        AppLogger.i("Dev Mode enabled via startup arguments.")
        DevStudioState.open(detached = true)
    }

    if (!isSafeModeRequested) {
        ShaderManager.extractBundledShaders()
        com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.extractBundledFonts()
    }
    com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.init()

    // Initialize SQLite Database, Profiles, AppearanceConfig & MetadataConfig synchronously before Compose starts
    com.lagradost.common.storage.DesktopDataStore.init()
    com.lagradost.cloudstream3.desktop.profile.ProfileManager.init()
    com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.reloadFromDataStore()
    com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.reloadFromDataStore()

    // Pre-warm theme presets, search indexes, and custom font cache on background thread
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getAvailableFonts()
        com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets.presets.size
        com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSearchIndex.searchIndex.size
    }

    Runtime.getRuntime().addShutdownHook(
        Thread {
            com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.shutdown()
        },
    )

    application {
        initCoil()
        if (!com.lagradost.cloudstream3.desktop.init.SafeModeState.isSafeMode.value) {
            launchPeriodicPluginUpdater()
        }

        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val windowWidth = (screenSize.width * 0.7).toInt().coerceAtLeast(1000).dp
        val windowHeight = (screenSize.height * 0.7).toInt().coerceAtLeast(700).dp
        val state = rememberWindowState(
            width = windowWidth,
            height = windowHeight,
            position = WindowPosition.Aligned(Alignment.Center),
            placement = WindowPlacement.Maximized,
        )

        val fullscreenHelper = rememberFullscreenHelper()
        val isDevOpen by DevStudioState.isOpen.collectAsState()
        val isDevDetached by DevStudioState.isDetachedWindow.collectAsState()

        val isPipMode by com.lagradost.cloudstream3.desktop.ui.PipState.isPipMode.collectAsState()

        Window(
            onCloseRequest = {
                com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.shutdown()
                exitApplication()
            },
            title = "CloudStream - Unofficial Desktop Client (Pre-Alpha)",
            state = state,
            icon = painterResource("app_icon_small.png"),
            onKeyEvent = fullscreenHelper.onKeyEvent,
        ) {
            LaunchedEffect(isPipMode) {
                com.lagradost.cloudstream3.desktop.init.setNativePipMode(window, isPipMode)
            }

            window.minimumSize = if (isPipMode) java.awt.Dimension(280, 180) else java.awt.Dimension(980, 640)
            fullscreenHelper.attachToWindow(window)
            setupWindowBackgroundAndListeners(fullscreenHelper.controller)
            CompositionLocalProvider(
                com.lagradost.cloudstream3.desktop.ui.LocalWindowState provides state,
                LocalFullscreenController provides fullscreenHelper.controller,
                com.lagradost.cloudstream3.desktop.ui.LocalComposeWindow provides window,
            ) {
                var isAppReady by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    val startupJob = launch(Dispatchers.IO) {
                        val proxyJob = async { initProxy() }

                        // Strict dependency: Security (DataStore, Conscrypt) must init first
                        initSecurity()

                        // Providers, Repository, and APIHolder initialize immediately
                        val providersJob = async { initProviders() }
                        val repoJob = async { com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.initialize() }
                        val apiJob = async { com.lagradost.cloudstream3.APIHolder.initAll() }

                        // Active plugin initializes concurrently in parallel with Network initialization
                        val networkJob = async { initNetwork() }
                        val pluginsJob = async {
                            providersJob.await()
                            repoJob.await()
                            apiJob.await()
                            initPlugins()
                        }

                        // Wait for both network and active plugin before unveiling the UI
                        networkJob.await()
                        pluginsJob.await()
                        proxyJob.await()

                        // App shell and UI render with active provider already mounted and ready
                        kotlinx.coroutines.withContext(Dispatchers.Main) {
                            isAppReady = true
                            com.lagradost.cloudstream3.desktop.init.AppReadyState.markReady()
                        }

                        // Background warmup omitted: plugins load on-demand when selected by the user.

                        // Pre-warm settings and appearance classes in background
                        try {
                            Class.forName("com.lagradost.cloudstream3.desktop.ui.screens.settings.SettingsSession")
                            Class.forName("com.lagradost.cloudstream3.desktop.ui.screens.settings.AppearanceConfig")
                        } catch (_: Throwable) {}
                    }

                    // If offline at boot, alert user that offline mode is active
                    val initiallyOnline = com.lagradost.cloudstream3.desktop.network.NetworkMonitor.isOnline.value
                    if (!initiallyOnline) {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Offline Mode: Local library and downloaded content available.")
                    }

                    // Background updater: wait until startupJob finishes completely and defer non-urgent checks
                    // so the user has immediate, smooth zero-I/O responsiveness during startup.
                    launch(Dispatchers.IO) {
                        startupJob.join()
                        kotlinx.coroutines.delay(45_000L)

                        if (initiallyOnline && !com.lagradost.cloudstream3.desktop.init.SafeModeState.isSafeMode.value) {
                            launchAutoUpdater()
                            com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.checkAllUpdates()
                            com.lagradost.cloudstream3.desktop.AppUpdater.checkForUpdates()
                        }

                        // Reactive connection listener: instantly sync as soon as internet is restored
                        var wasOffline = !initiallyOnline
                        com.lagradost.cloudstream3.desktop.network.NetworkMonitor.isOnline.collect { online ->
                            if (online) {
                                if (wasOffline) {
                                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("Internet connection restored. Synchronizing online content...")
                                    wasOffline = false
                                    launchAutoUpdater()
                                    com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.checkAllUpdates()
                                    com.lagradost.cloudstream3.desktop.AppUpdater.checkForUpdates()
                                }
                            } else {
                                wasOffline = true
                            }
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    Crossfade<Boolean>(
                        targetState = isAppReady,
                        animationSpec = tween(500),
                    ) { ready ->
                        if (ready) {
                            val (root, rootLifecycle) = remember {
                                val lifecycle = LifecycleRegistry()
                                lifecycle.resume() // Start it immediately
                                Pair(DefaultRootComponent(DefaultComponentContext(lifecycle)), lifecycle)
                            }

                            DisposableEffect(rootLifecycle) {
                                onDispose {
                                    rootLifecycle.destroy()
                                }
                            }

                            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                                CloudstreamApp(rootComponent = root)

                                val isSafeMode by com.lagradost.cloudstream3.desktop.init.SafeModeState.isSafeMode.collectAsState()
                                if (isSafeMode) {
                                    com.lagradost.cloudstream3.desktop.ui.components.SafeModeRecoveryBanner(
                                        onOpenExtensions = {
                                            root.bringToFront(com.lagradost.cloudstream3.desktop.ui.navigation.Config.Extensions(initialTab = 1))
                                        },
                                        onDismiss = {
                                            com.lagradost.cloudstream3.desktop.init.SafeModeState.disable()
                                        },
                                        modifier = Modifier.align(Alignment.TopCenter),
                                    )
                                }

                                PreAlphaNoticeDialog()
                                com.lagradost.cloudstream3.desktop.ui.dialogs.EmbeddedVerificationHost()
                                com.lagradost.cloudstream3.desktop.ui.screens.ShadowUiHost()

                                // In-app Docked Dev Studio Overlay
                                if (isDevOpen && !isDevDetached) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.50f)
                                            .align(Alignment.BottomCenter),
                                    ) {
                                        DevStudioView(isDetached = false)
                                    }
                                }
                            }
                        } else {
                            com.lagradost.cloudstream3.desktop.ui.components.AppStartupSplashScreen()
                        }
                    }
                }
            }
        }

        // Secondary Standalone Floating Window for Dev Studio
        if (isDevOpen && isDevDetached) {
            val devWindowState = rememberWindowState(
                width = 1100.dp,
                height = 700.dp,
                position = WindowPosition.Aligned(Alignment.Center),
            )
            Window(
                onCloseRequest = { DevStudioState.close() },
                title = "CloudStream Dev Studio & Live LogCat",
                state = devWindowState,
                icon = painterResource("app_icon_small.png"),
            ) {
                DevStudioView(
                    isDetached = true,
                    onClose = { DevStudioState.close() },
                )
            }
        }
    }
}
