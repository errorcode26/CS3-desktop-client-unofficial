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
     * Sets the WebView2 zoom factor to dynamically scale HTML UI elements.
     */
    external fun setZoomFactor(zoom: Double)

    /**
     * Synchronizes in-app UI scale with WebView2.
     */
    fun syncUiScale(scale: Float) {
        try {
            setZoomFactor(scale.toDouble().coerceIn(0.5, 3.0))
        } catch (t: Throwable) {
            AppLogger.w("Failed to sync zoom factor to WebView: ${t.message}")
        }
    }

    /**
     * Resolves physical pixel dimensions of an AWT component by applying display DPI scaling transform.
     */
    fun getPhysicalDimensions(component: java.awt.Component): Pair<Int, Int> {
        if (component.width <= 0 || component.height <= 0) return 0 to 0
        val transform = component.graphicsConfiguration?.defaultTransform
            ?: java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice?.defaultConfiguration?.defaultTransform
        val scaleX = transform?.scaleX ?: 1.0
        val scaleY = transform?.scaleY ?: 1.0
        val pWidth = Math.round(component.width * scaleX).toInt().coerceAtLeast(1)
        val pHeight = Math.round(component.height * scaleY).toInt().coerceAtLeast(1)
        return pWidth to pHeight
    }

    /**
     * Resizes the native child window using physical pixel dimensions derived from the component's DPI scale.
     */
    fun resizeWebViewForComponent(component: java.awt.Component) {
        val (pWidth, pHeight) = getPhysicalDimensions(component)
        if (pWidth > 0 && pHeight > 0) {
            resizeWebView(pWidth, pHeight)
        }
    }

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
    external fun warmupWebView2(controlsUrl: String? = null)

    /**
     * Shuts down the background warmup thread.
     */
    external fun shutdownWebView2Warmup()

    fun loadPlayerUiResource(path: String): String {
        val devFile = java.io.File("desktop-app/src/main/resources$path").takeIf { it.exists() } ?: java.io.File("src/main/resources$path").takeIf { it.exists() }
        if (devFile != null) {
            val content = runCatching { devFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!content.isNullOrEmpty()) return content
        }
        return NativePlayerBridge::class.java.getResourceAsStream(path)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: ""
    }

    /**
     * Asynchronously warms up the WebView2 environment if running on Windows.
     * Prevents the 2-second stutter and unrendered DOM flashes when opening the player.
     */
    fun preloadAsync() {
        // Disabled: Microsoft Edge WebView2 forbids multiple controllers on different threads
        // sharing the same user data folder. Background prewarming locks the session and causes
        // initWebView to fail with 0x8007139f (ERROR_INVALID_STATE) on playback start.
        runCatching { shutdownWebView2Warmup() }
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
