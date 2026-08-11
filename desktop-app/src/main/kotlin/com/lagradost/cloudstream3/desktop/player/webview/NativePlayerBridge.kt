package com.lagradost.cloudstream3.desktop.player.webview

import com.lagradost.common.logging.AppLogger
import java.util.concurrent.atomic.AtomicBoolean

object NativePlayerBridge {
    private val preloadStarted = AtomicBoolean(false)

    init {
        try {
            val resourcesDir = System.getProperty("compose.application.resources.dir")

            // Try absolute paths first (Release mode / AppImage)
            val webviewDll = if (resourcesDir != null) java.io.File(resourcesDir, "jni/WebView2Loader.dll") else null
            val playerDll = if (resourcesDir != null) java.io.File(resourcesDir, "jni/player_bridge.dll") else null

            if (webviewDll?.exists() == true && playerDll?.exists() == true) {
                System.load(webviewDll.absolutePath)
                System.load(playerDll.absolutePath)
            } else {
                // Fallback to java.library.path (Dev mode)
                System.loadLibrary("WebView2Loader")
                System.loadLibrary("player_bridge")
            }
            AppLogger.i("Successfully loaded player_bridge native library")
        } catch (e: Throwable) {
            AppLogger.e("Failed to load player_bridge native library: ${e.message}")
        }
    }

    /**
     * Initializes the native child window.
     * @param hostHwnd The HWND of the AWT Canvas.
     * @return The HWND of the new child window, or 0 if failed.
     */
    external fun initWebView(hostHwnd: Long, width: Int, height: Int): Long

    /**
     * Resizes the native child window.
     */
    external fun resizeWebView(width: Int, height: Int)

    /**
     * Enables or disables true borderless fullscreen on the native window.
     * Uses per-window state tracking (thread-safe).
     */
    @JvmStatic
    external fun setFullscreen(hwnd: Long, fullscreen: Boolean, x: Int, y: Int, width: Int, height: Int)

    /**
     * Installs/removes the PiP-only top-level window subclass that blocks
     * WM_DPICHANGED to prevent AWT from resizing the PiP window on monitor change.
     */
    @JvmStatic
    external fun setPipSubclass(hwnd: Long, enable: Boolean)

    /**
     * Applies DWM window chrome: dark mode title bar and optional caption/border/text colours.
     * No-op on Windows versions that don't support these DWM attributes.
     */
    @JvmStatic
    external fun applyWindowChrome(hwnd: Long, darkMode: Boolean, captionColorRgb: Int, borderColorRgb: Int, textColorRgb: Int)

    /**
     * Destroys the native child window.
     */
    external fun destroyWebView()

    /**
     * Forces OS focus onto the WebView container so keyboard events route properly.
     */
    external fun focusWebView()

    /**
     * Sends a JSON state string to the WebView.
     */
    external fun executeScript(script: String)

    /**
     * Posts a JSON message directly to the WebView2 control using postWebMessageAsJson.
     */
    external fun postMessage(json: String)

    /**
     * Posts a JSON message directly to the WebView2 control using postWebMessageAsJson.
     */
    external fun notifyThemeChange(isDarkMode: Boolean)

    /**
     * Initializes an invisible WebView2 instance in the background to warm up Chromium.
     */
    external fun warmupWebView2()

    /**
     * Shuts down the background warmup thread.
     */
    external fun shutdownWebView2Warmup()

    /**
     * Asynchronously warms up the WebView2 environment if running on Windows.
     * Prevents the 2-second stutter when opening the player.
     */
    fun preloadAsync() {
        if (!preloadStarted.compareAndSet(false, true)) return

        Thread {
            runCatching {
                AppLogger.i("Starting NativePlayerBridge warmup...")
                warmupWebView2()
            }.onFailure {
                AppLogger.e("Failed to warmup NativePlayerBridge: ${it.message}")
            }
        }.apply {
            name = "cloudstream-native-player-preload"
            isDaemon = true
            start()
        }

        Runtime.getRuntime().addShutdownHook(
            Thread {
                runCatching { shutdownWebView2Warmup() }
            }.apply {
                name = "cloudstream-webview2-warmup-shutdown"
            },
        )
    }

    /**
     * Navigates the WebView to a specific URL (like file:///...)
     */
    external fun loadUrl(url: String)

    /**
     * Starts a direct C++ sync timer for mpv properties (bypassing Kotlin loop overhead).
     */
    external fun startMpvSync(mpvHandle: Long)

    /**
     * Stops the direct C++ sync timer before destroying the mpv handle to prevent dangling pointer crashes.
     */
    external fun stopMpvSync()

    /**
     * Opens the WebView devtools.
     */
    external fun openDevTools()

    /**
     * Registers a listener to receive events from the WebView JS bridge.
     */
    external fun setEventListener(listener: NativePlayerEventListener?)

    interface NativePlayerEventListener {
        fun onPlayerEvent(type: String, value: String)
    }
}
