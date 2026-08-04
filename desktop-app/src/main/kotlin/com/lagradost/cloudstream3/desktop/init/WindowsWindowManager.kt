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

fun initWindowsEnvironment() {
    if (System.getProperty("os.name").lowercase().contains("win")) {
        try {
            Kernel32.INSTANCE.SetEnvironmentVariableW(
                com.sun.jna.WString("WEBVIEW2_DEFAULT_BACKGROUND_COLOR"),
                com.sun.jna.WString("00000000"),
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
