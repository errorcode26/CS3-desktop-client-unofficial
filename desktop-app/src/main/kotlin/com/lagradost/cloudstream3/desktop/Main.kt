@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class, androidx.compose.animation.ExperimentalAnimationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.lagradost.cloudstream3.desktop

// TODO: Yeah I know this is a big ball of mud, but let's refactor this later.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import coil3.compose.setSingletonImageLoaderFactory
import coil3.request.crossfade
import com.lagradost.cloudstream3.desktop.init.initNetwork
import com.lagradost.cloudstream3.desktop.init.initPlugins
import com.lagradost.cloudstream3.desktop.init.initProviders
import com.lagradost.cloudstream3.desktop.init.initProxy
import com.lagradost.cloudstream3.desktop.init.initSecurity
import com.lagradost.cloudstream3.desktop.init.launchAutoUpdater
import com.lagradost.cloudstream3.desktop.ui.CloudstreamApp
import com.lagradost.cloudstream3.desktop.ui.FullscreenController
import com.lagradost.cloudstream3.desktop.ui.LocalFullscreenController
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import okio.Path.Companion.toOkioPath
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private interface Kernel32 : com.sun.jna.Library {
    fun SetEnvironmentVariableW(name: com.sun.jna.WString, value: com.sun.jna.WString): Boolean
    companion object {
        val INSTANCE = com.sun.jna.Native.load("kernel32", Kernel32::class.java) as Kernel32
    }
}

/**
 * Single unified entry point for CloudStream Desktop Client.
 */
fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        try {
            val crashDir = PlatformPaths.appDataDir
            crashDir.mkdirs()
            val crashFile = File(crashDir, "crash.log")
            
            val stackTrace = java.io.StringWriter().also { e.printStackTrace(java.io.PrintWriter(it)) }.toString()
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())
            
            crashFile.appendText("\n\n--- CRASH LOG: $time ---\n")
            crashFile.appendText(stackTrace)
            
            try {
                java.awt.Desktop.getDesktop().open(crashDir)
            } catch (t: Throwable) {
                // Ignore if opening folder fails
            }
            
            javax.swing.JOptionPane.showMessageDialog(
                null,
                "CloudStream encountered a fatal error and crashed.\n\nA crash log has been saved to:\n${crashFile.absolutePath}\n\nPlease share this file with the developers.",
                "CloudStream Crash Reporter",
                javax.swing.JOptionPane.ERROR_MESSAGE
            )
        } catch (t: Throwable) {
            // Failsafe, don't crash the crash handler itself
            t.printStackTrace()
        }
        kotlin.system.exitProcess(1)
    }

    if (System.getProperty("os.name").lowercase().contains("win")) {
        try {
            Kernel32.INSTANCE.SetEnvironmentVariableW(
                com.sun.jna.WString("WEBVIEW2_DEFAULT_BACKGROUND_COLOR"),
                com.sun.jna.WString("00000000")
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Removed the default black background. Setting it globally forces popups and tooltips 
    // to render as black boxes if they fail to paint or get stuck.
    // val black = java.awt.Color.BLACK
    // javax.swing.UIManager.put("Window.background", black)
    // javax.swing.UIManager.put("Canvas.background", black)

    AppLogger.i("Launching CloudStream Desktop Client...")
    AppLogger.i("Platform: ${PlatformPaths.currentOS}")
    AppLogger.i("App data directory: ${PlatformPaths.appDataDir.absolutePath}")

    application {
        setSingletonImageLoaderFactory { context ->
            coil3.ImageLoader.Builder(context)
                .memoryCache {
                    coil3.memory.MemoryCache.Builder()
                        .maxSizePercent(context, 0.25)
                        .build()
                }
                .diskCache {
                    coil3.disk.DiskCache.Builder()
                        .directory(File(PlatformPaths.appDataDir, "image_cache").also { it.mkdirs() }.toOkioPath())
                        .maxSizeBytes(512L * 1024 * 1024)
                        .build()
                }
                .components {
                    add(
                        coil3.network.okhttp.OkHttpNetworkFetcherFactory(
                            callFactory = { request ->
                                com.lagradost.cloudstream3.app.baseClient.newCall(request)
                            },
                        ),
                    )
                }
                .crossfade(true)
                .build()
        }

        val screenSize = java.awt.Toolkit.getDefaultToolkit().screenSize
        val windowWidth = (screenSize.width * 0.7).toInt().coerceAtLeast(1000).dp
        val windowHeight = (screenSize.height * 0.7).toInt().coerceAtLeast(700).dp
        val state = androidx.compose.ui.window.rememberWindowState(
            width = windowWidth,
            height = windowHeight,
            position = androidx.compose.ui.window.WindowPosition.Aligned(androidx.compose.ui.Alignment.Center),
            placement = androidx.compose.ui.window.WindowPlacement.Maximized
        )

        val isFullscreenState = androidx.compose.runtime.mutableStateOf(false)
        val popupKeyState = androidx.compose.runtime.mutableStateOf(0)
        val contentAreaPxState = androidx.compose.runtime.mutableStateOf(Pair(0, 0))
        // Content-pane size captured just before entering fullscreen.
        // Used to pre-seed contentAreaPxState when exiting, so the FIRST recomposition
        // triggered by isFullscreenState=false already has the correct overlay dimensions.
        var savedContentPxBeforeFullscreen: Pair<Int, Int>? = null
        val windowRef = AtomicReference<java.awt.Window?>(null)
        val toggleScope = kotlinx.coroutines.MainScope()

        fun toggleFullscreen() {
            val w = windowRef.get() as? javax.swing.JFrame ?: return
            if (isFullscreenState.value) {
                // Pre-seed contentAreaPxState with the saved pre-fullscreen content
                // pane dimensions BEFORE isFullscreenState.value = false fires.
                // This guarantees the very first recomposition (triggered by the
                // state change below) already sees the correct overlay size, so no
                // frame ever renders with stale fullscreen dimensions.
                savedContentPxBeforeFullscreen?.let { saved ->
                    contentAreaPxState.value = saved
                }
                exitWindowsFullscreen(w)
                isFullscreenState.value = false
            } else {
                // Snapshot the drawable content area before hiding the title bar.
                // contentPane is the JPanel that fills the client area; its size
                // excludes the title bar and window borders — exactly what we need.
                val pane = w.contentPane
                savedContentPxBeforeFullscreen = Pair(pane.width, pane.height)
                enterWindowsFullscreen(w)
                isFullscreenState.value = true
            }
        }

        Window(
            onCloseRequest = ::exitApplication,
            title = "CloudStream - Unofficial Desktop Client (Pre-Alpha)",
            state = state,
            icon = androidx.compose.ui.res.painterResource("logo_ui.png"),
            onKeyEvent = { keyEvent ->
                if (keyEvent.key == Key.F11 && keyEvent.type == KeyEventType.KeyDown) {
                    toggleFullscreen()
                    true
                } else if (keyEvent.key == Key.Escape && keyEvent.type == KeyEventType.KeyDown && isFullscreenState.value) {
                    toggleFullscreen()
                    true
                } else {
                    false
                }
            }
        ) {
            windowRef.set(window)
            window.minimumSize = java.awt.Dimension(1000, 700)
            
            androidx.compose.runtime.SideEffect {
                val black = java.awt.Color.BLACK
                window.background = black
                window.rootPane.background = black
                window.contentPane.background = black
                (window.contentPane as? javax.swing.JComponent)?.isOpaque = true

                // Deep-dive fix: Compose 1.7+ internal SkiaLayers often default to white.
                // This absolute nightmare causes awful white flashes during rendering.
                // We recursively traverse the entire Swing component tree and force everything black.
                fun forceBlackBackground(container: java.awt.Container) {
                    for (c in container.components) {
                        c.background = black
                        if (c is javax.swing.JComponent) {
                            c.isOpaque = true
                        }
                        if (c is java.awt.Container) {
                            forceBlackBackground(c)
                        }
                    }
                }
                forceBlackBackground(window)
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                setWindowsDarkMode(window)
            }

            // Exit fullscreen cleanly when window closes
            androidx.compose.runtime.DisposableEffect(Unit) {
                onDispose {
                    val w = window as? javax.swing.JFrame
                    if (w != null && isFullscreenState.value) {
                        exitWindowsFullscreen(w)
                    }
                }
            }

            // Track content-pane size so EmbeddedVideoPlayer can size its overlay
            // correctly even when BoxWithConstraints has stale fullscreen dimensions.
            // (Yes, BoxWithConstraints frequently fails to update its size correctly here, which is incredibly annoying).
            // The contentPane ComponentListener fires on the AWT EDT AFTER the window
            // resize has settled (e.g. after ShowWindow SW_MAXIMIZE completes), making
            // it the ground truth for the drawable area in physical pixels.
            androidx.compose.runtime.DisposableEffect(Unit) {
                val contentPane = (window as? javax.swing.JFrame)?.contentPane
                val listener = object : java.awt.event.ComponentAdapter() {
                    override fun componentResized(e: java.awt.event.ComponentEvent) {
                        contentAreaPxState.value = Pair(e.component.width, e.component.height)
                    }
                }
                contentPane?.addComponentListener(listener)
                // Seed with the current size so the first frame is correct.
                if (contentPane != null) {
                    contentAreaPxState.value = Pair(contentPane.width, contentPane.height)
                }
                onDispose { contentPane?.removeComponentListener(listener) }
            }

            val fullscreenController = FullscreenController(
                isFullscreen = isFullscreenState,
                toggle = ::toggleFullscreen,
                popupKey = popupKeyState,
                mainFrame = window,
                contentAreaPx = contentAreaPxState,
            )

            androidx.compose.runtime.CompositionLocalProvider(
                com.lagradost.cloudstream3.desktop.ui.LocalWindowState provides state,
                LocalFullscreenController provides fullscreenController,
            ) {
                var isAppReady by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    val initJob = launch(kotlinx.coroutines.Dispatchers.IO) {
                        initProxy()
                        initSecurity()
                        initNetwork()
                        initProviders()
                        initPlugins()
                        com.lagradost.cloudstream3.APIHolder.initAll()
                        launchAutoUpdater()
                    }
                    val delayJob = launch {
                        // Artificial 5-second delay for the banana loading bar
                        delay(5000)
                    }
                    initJob.join()
                    delayJob.join()
                    isAppReady = true
                }

                androidx.compose.foundation.layout.Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black)
                ) {
                    androidx.compose.animation.Crossfade<Boolean>(
                        targetState = isAppReady,
                        animationSpec = androidx.compose.animation.core.tween(500)
                    ) { ready ->
                        if (ready) {
                            CloudstreamApp()
                        } else {
                            val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition()
                            val scale by infiniteTransition.animateFloat(
                                initialValue = 0.90f,
                                targetValue = 1.05f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                )
                            )
                            val alpha by infiniteTransition.animateFloat(
                                initialValue = 0.6f,
                                targetValue = 1.0f,
                                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                                    animation = androidx.compose.animation.core.tween(1500, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                                )
                            )

                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = androidx.compose.ui.Alignment.Center
                            ) {
                                androidx.compose.foundation.layout.Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource("logo_ui.png"),
                                        contentDescription = "CloudStream Logo",
                                        modifier = Modifier
                                            .size(200.dp)
                                            .scale(scale)
                                            .alpha(alpha)
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))
                                    androidx.compose.material3.Text(
                                        text = "LOADING",
                                        color = Color.White.copy(alpha = alpha), // Matches the logo breathing
                                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 12.sp
                                    )
                                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(24.dp))
                                    
                                    var bananaProgress by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(0f) }
                                    androidx.compose.runtime.LaunchedEffect(Unit) {
                                        androidx.compose.animation.core.animate(
                                            initialValue = 0f,
                                            targetValue = 1f,
                                            animationSpec = androidx.compose.animation.core.tween(5000, easing = androidx.compose.animation.core.LinearEasing)
                                        ) { value, _ -> bananaProgress = value }
                                    }

                                    val totalBananas = 5
                                    val currentBananas = (bananaProgress * totalBananas).toInt().coerceIn(0, totalBananas)
                                    
                                    androidx.compose.foundation.layout.Row(
                                        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp),
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                        modifier = Modifier.height(32.dp) // Keep height consistent
                                    ) {
                                        for (i in 0 until currentBananas) {
                                            androidx.compose.material3.Text(
                                                text = "🍌",
                                                fontSize = 28.sp
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

// Windows Borderless Fullscreen via C++ JNI bridge (NativePlayerBridge)
private fun enterWindowsFullscreen(frame: javax.swing.JFrame) {
    if (!System.getProperty("os.name", "").lowercase().contains("win")) {
        // Non-Windows fallback: use AWT exclusive fullscreen
        val gd = java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        gd.fullScreenWindow = frame
        return
    }
    try {
        val hwnd = com.sun.jna.Native.getComponentID(frame)
        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.setFullscreen(
            hwnd = hwnd,
            fullscreen = true,
            x = 0, y = 0, width = 0, height = 0,
        )
        AppLogger.i("Entered borderless fullscreen via C++ bridge (hwnd=0x${hwnd.toString(16)})")
    } catch (e: Exception) {
        AppLogger.e("enterWindowsFullscreen failed: ${e.message}")
        e.printStackTrace()
        runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.fullScreenWindow = frame
        }
    }
}

private fun exitWindowsFullscreen(frame: javax.swing.JFrame) {
    if (!System.getProperty("os.name", "").lowercase().contains("win")) {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.fullScreenWindow = null
        return
    }
    try {
        val hwnd = com.sun.jna.Native.getComponentID(frame)
        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.setFullscreen(
            hwnd = hwnd,
            fullscreen = false,
            x = 0, y = 0, width = 0, height = 0,
        )
        AppLogger.i("Exited borderless fullscreen via C++ bridge (hwnd=0x${hwnd.toString(16)})")
    } catch (e: Exception) {
        AppLogger.e("exitWindowsFullscreen failed: ${e.message}")
        e.printStackTrace()
        runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.fullScreenWindow = null
        }
    }
}

// DWM Dark mode title bar + caption colour (via C++ bridge)
private const val WindowBackgroundRgb = 0x0D0D0D
private const val WindowTextRgb = 0xF5F7F8

private fun setWindowsDarkMode(window: java.awt.Window) {
    if (!System.getProperty("os.name").lowercase().contains("win")) return
    try {
        val hwnd = com.sun.jna.Native.getComponentID(window)
        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.applyWindowChrome(
            hwnd = hwnd,
            darkMode = true,
            captionColorRgb = WindowBackgroundRgb,
            borderColorRgb = WindowBackgroundRgb,
            textColorRgb = WindowTextRgb,
        )
    } catch (e: Throwable) {
        e.printStackTrace()
    }
}
