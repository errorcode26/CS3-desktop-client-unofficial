@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class, androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.lagradost.cloudstream3.desktop

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.lagradost.cloudstream3.desktop.init.AppUpdateDialog
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
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Toolkit

import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioState
import com.lagradost.cloudstream3.desktop.ui.screens.dev.DevStudioView
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight

/**
 * Single unified entry point for CloudStream Desktop Client.
 */
fun main(args: Array<String> = emptyArray()) {
    System.setProperty("sun.awt.noerasebackground", "true")
    javax.swing.UIManager.put("Panel.background", java.awt.Color.BLACK)
    javax.swing.UIManager.put("Window.background", java.awt.Color.BLACK)
    javax.swing.UIManager.put("Frame.background", java.awt.Color.BLACK)
    javax.swing.UIManager.put("RootPane.background", java.awt.Color.BLACK)
    javax.swing.UIManager.put("Control.background", java.awt.Color.BLACK)
    javax.swing.UIManager.put("control", java.awt.Color.BLACK)
    javax.swing.UIManager.put("window", java.awt.Color.BLACK)
    javax.swing.UIManager.put("Canvas.background", java.awt.Color.BLACK)
    
    initCrashHandler()
    initWindowsEnvironment()

    val isDevMode = args.any { it.equals("--dev", ignoreCase = true) || it.equals("--dev-logger", ignoreCase = true) } ||
            System.getProperty("cloudstream.dev") != null

    AppLogger.i("Launching CloudStream Desktop Client...")
    AppLogger.i("Platform: ${PlatformPaths.currentOS}")
    AppLogger.i("App data directory: ${PlatformPaths.appDataDir.absolutePath}")

    if (isDevMode) {
        AppLogger.i("Dev Mode enabled via startup arguments.")
        DevStudioState.open(detached = true)
    }

    ShaderManager.extractBundledShaders()

    application {
        initCoil()
        launchPeriodicPluginUpdater()

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

        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream - Unofficial Desktop Client (Pre-Alpha)",
            state = state,
            icon = painterResource("app_icon_small.png"),
            onKeyEvent = fullscreenHelper.onKeyEvent,
        ) {
            fullscreenHelper.attachToWindow(window)
            setupWindowBackgroundAndListeners(fullscreenHelper.controller)

            CompositionLocalProvider(
                com.lagradost.cloudstream3.desktop.ui.LocalWindowState provides state,
                LocalFullscreenController provides fullscreenHelper.controller,
            ) {
                var isAppReady by remember { mutableStateOf(false) }

                LaunchedEffect(Unit) {
                    launch(Dispatchers.IO) {
                        initProxy()
                        initSecurity()
                        initNetwork()
                        initProviders()
                        initPlugins()
                        com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager.initialize()
                        com.lagradost.cloudstream3.APIHolder.initAll()
                    }.join()
                    
                    isAppReady = true

                    // Run updates in the background so they don't block the UI if the network is down or slow
                    launch(Dispatchers.IO) {
                        launchAutoUpdater()
                        AppUpdater.checkForUpdates()
                    }
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    Crossfade<Boolean>(
                        targetState = isAppReady,
                        animationSpec = tween(500),
                    ) { ready ->
                        if (ready) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                CloudstreamApp()

                                // In-app Docked Dev Studio Overlay
                                if (isDevOpen && !isDevDetached) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .fillMaxHeight(0.50f)
                                            .align(Alignment.BottomCenter)
                                    ) {
                                        DevStudioView(isDetached = false)
                                    }
                                }
                            }
                            AppUpdateDialog()
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = Color.White.copy(alpha = 0.7f),
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
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
