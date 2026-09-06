package com.lagradost.cloudstream3.desktop.init

import com.lagradost.common.logging.AppLogger
import java.awt.Window
import javax.swing.JFrame

interface Kernel32 : com.sun.jna.Library {
    fun SetEnvironmentVariableW(name: com.sun.jna.WString, value: com.sun.jna.WString): Boolean
    companion object {
        val INSTANCE: Kernel32 by lazy {
            com.sun.jna.Native.load("kernel32", Kernel32::class.java) as Kernel32
        }
    }
}

interface ExtUser32 : com.sun.jna.Library {
    fun ReleaseCapture(): Boolean
    companion object {
        val INSTANCE: ExtUser32 by lazy {
            com.sun.jna.Native.load("user32", ExtUser32::class.java) as ExtUser32
        }
    }
}

fun initWindowsEnvironment() {
    // Disable AWT background erasing globally to prevent white flashes when Canvas components mount
    System.setProperty("sun.awt.noerasebackground", "true")

    if (System.getProperty("os.name").lowercase().contains("win")) {
        try {
            Kernel32.INSTANCE.SetEnvironmentVariableW(
                com.sun.jna.WString("WEBVIEW2_ADDITIONAL_BROWSER_ARGUMENTS"),
                com.sun.jna.WString("--allow-file-access-from-files --disable-web-security --allow-running-insecure-content --default-background-color=00000000 --disk-cache-size=1 --disable-application-cache --aggressive-cache-discard"),
            )
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("WindowsWindowManager", "initWindowsEnvironment failed", e)
        }
    }
}

// Windows Borderless Fullscreen via C++ JNI bridge (NativePlayerBridge)
fun enterWindowsFullscreen(frame: javax.swing.JFrame) {
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
            x = 0,
            y = 0,
            width = 0,
            height = 0,
        )
        AppLogger.i("Entered borderless fullscreen via C++ bridge (hwnd=0x${hwnd.toString(16)})")
    } catch (e: Exception) {
        AppLogger.e("enterWindowsFullscreen failed: ${e.message}")
        AppLogger.e("WindowsWindowManager", "enterWindowsFullscreen failed", e)
        runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.fullScreenWindow = frame
        }
    }
}

fun exitWindowsFullscreen(frame: javax.swing.JFrame) {
    if (!System.getProperty("os.name", "").lowercase().contains("win")) {
        java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.fullScreenWindow = null
        return
    }
    try {
        val hwnd = com.sun.jna.Native.getComponentID(frame)
        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.setFullscreen(
            hwnd = hwnd,
            fullscreen = false,
            x = 0,
            y = 0,
            width = 0,
            height = 0,
        )
        AppLogger.i("Exited borderless fullscreen via C++ bridge (hwnd=0x${hwnd.toString(16)})")
    } catch (e: Exception) {
        AppLogger.e("exitWindowsFullscreen failed: ${e.message}")
        AppLogger.e("WindowsWindowManager", "exitWindowsFullscreen failed", e)
        runCatching {
            java.awt.GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.fullScreenWindow = null
        }
    }
}

// DWM Dark mode title bar + caption colour (via C++ bridge)
private const val WINDOW_BACKGROUND_RGB = 0x0D0D0D
private const val WINDOW_TEXT_RGB = 0xF5F7F8

fun setWindowsDarkMode(window: java.awt.Window) {
    if (!System.getProperty("os.name").lowercase().contains("win")) return
    if (!window.isDisplayable) return
    try {
        val hwnd = com.sun.jna.Native.getComponentID(window)
        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.applyWindowChrome(
            hwnd = hwnd,
            darkMode = true,
            captionColorRgb = WINDOW_BACKGROUND_RGB,
            borderColorRgb = WINDOW_BACKGROUND_RGB,
            textColorRgb = WINDOW_TEXT_RGB,
        )
    } catch (e: Throwable) {
        com.lagradost.common.logging.AppLogger.e("WindowsWindowManager", "setWindowsDarkMode failed", e)
    }
}

private var prePipBounds: java.awt.Rectangle? = null

fun setNativePipMode(window: java.awt.Window, enable: Boolean) {
    if (!System.getProperty("os.name").lowercase().contains("win")) return
    if (!window.isDisplayable) return
    try {
        val hwnd = com.sun.jna.Native.getComponentID(window)
        if (enable) {
            prePipBounds = window.bounds
            val bounds = window.graphicsConfiguration.bounds
            val scaleX = window.graphicsConfiguration.defaultTransform.scaleX
            val scaleY = window.graphicsConfiguration.defaultTransform.scaleY

            val w = (400 * scaleX).toInt()
            val h_size = (225 * scaleY).toInt()
            val x = bounds.x + bounds.width - w - (20 * scaleX).toInt()
            val y = bounds.y + bounds.height - h_size - (40 * scaleY).toInt()

            // Safely strip borders using C++ bridge
            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.setFullscreen(
                hwnd = hwnd,
                fullscreen = true,
                x = 0,
                y = 0,
                width = 0,
                height = 0,
            )
            // Install PiP-only subclass to block WM_DPICHANGED on monitor drag
            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.setPipSubclass(hwnd, true)

            val hWin = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(hwnd))
            val hwndTopMost = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(-1L))

            // SWP_NOZORDER is 0x0004, SWP_SHOWWINDOW is 0x0040
            com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(
                hWin,
                hwndTopMost,
                x,
                y,
                w,
                h_size,
                0x0040,
            )
        } else {
            // Remove PiP subclass before restoring fullscreen state
            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.setPipSubclass(hwnd, false)
            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.setFullscreen(
                hwnd = hwnd,
                fullscreen = false,
                x = 0,
                y = 0,
                width = 0,
                height = 0,
            )
            val hWin = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(hwnd))
            val hwndNoTopMost = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(-2L))
            val prev = prePipBounds
            if (prev != null) {
                com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(
                    hWin,
                    hwndNoTopMost,
                    prev.x,
                    prev.y,
                    prev.width,
                    prev.height,
                    0x0040,
                )
                prePipBounds = null
            } else {
                com.sun.jna.platform.win32.User32.INSTANCE.SetWindowPos(
                    hWin,
                    hwndNoTopMost,
                    0,
                    0,
                    0,
                    0,
                    0x0003,
                )
            }
        }
    } catch (e: Throwable) {
        com.lagradost.common.logging.AppLogger.e("WindowsWindowManager", "setNativePipMode failed", e)
    }
}
