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
import com.lagradost.cloudstream3.desktop.ui.CloudstreamApp
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.cloudstream3.desktop.player.ShaderManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Toolkit

/**
 * Single unified entry point for CloudStream Desktop Client.
 */
fun main() {
    initCrashHandler()
    initWindowsEnvironment()

    AppLogger.i("Launching CloudStream Desktop Client...")
    AppLogger.i("Platform: ${PlatformPaths.currentOS}")
    AppLogger.i("App data directory: ${PlatformPaths.appDataDir.absolutePath}")

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
                        launchAutoUpdater()
                        AppUpdater.checkForUpdates()
                    }.join()
                    isAppReady = true
                }

                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    Crossfade<Boolean>(
                        targetState = isAppReady,
                        animationSpec = tween(500),
                    ) { ready ->
                        if (ready) {
                            CloudstreamApp()
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
    }
}
