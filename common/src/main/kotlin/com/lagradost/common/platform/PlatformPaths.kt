package com.lagradost.common.platform

import java.io.File

/**
 * Cross-platform path resolution for the CloudStream Desktop client.
 *
 * Replaces all direct `System.getenv("APPDATA")` calls with proper
 * OS-aware paths that work on Windows, macOS, and Linux.
 *
 * Directory layout per OS:
 *   Windows: %APPDATA%/CloudStreamDesktop/
 *   macOS:   ~/Library/Application Support/CloudStreamDesktop/
 *   Linux:   ~/.local/share/CloudStreamDesktop/
 */
object PlatformPaths {
    enum class OS { WINDOWS, MACOS, LINUX, UNKNOWN }

    val currentOS: OS by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("win") -> OS.WINDOWS
            osName.contains("mac") -> OS.MACOS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> OS.LINUX
            else -> OS.UNKNOWN
        }
    }

    /** The base application data directory, OS-aware. */
    val appDataDir: File by lazy {
        val basePath =
            when (currentOS) {
                OS.WINDOWS -> {
                    val appData = System.getenv("APPDATA")
                    if (!appData.isNullOrEmpty()) appData else System.getProperty("user.home")
                }
                OS.MACOS -> System.getProperty("user.home") + "/Library/Application Support"
                OS.LINUX -> System.getProperty("user.home") + "/.local/share"
                OS.UNKNOWN -> System.getProperty("user.home")
            }
        File(basePath, "CloudStreamDesktop").also { it.mkdirs() }
    }

    /** Directory for persistent data store (bookmarks, history, preferences). */
    val dataDir: File by lazy {
        File(appDataDir, "data").also { it.mkdirs() }
    }

    /** Directory for SharedPreferences JSON files. */
    val sharedPrefsDir: File by lazy {
        File(appDataDir, "shared_prefs").also { it.mkdirs() }
    }

    /** Directory for installed extensions/plugins. */
    val extensionsDir: File by lazy {
        File(appDataDir, "Extensions").also { it.mkdirs() }
    }

    /** Directory for cache files. */
    val cacheDir: File by lazy {
        File(appDataDir, "cache").also { it.mkdirs() }
    }

    /** Directory for log files. */
    val logsDir: File by lazy {
        File(appDataDir, "logs").also { it.mkdirs() }
    }
}
