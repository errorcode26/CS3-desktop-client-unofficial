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
    enum class Flavor { PORTABLE, INNO_SETUP, MSI }

    val currentOS: OS by lazy {
        val osName = System.getProperty("os.name").lowercase()
        when {
            osName.contains("win") -> OS.WINDOWS
            osName.contains("mac") -> OS.MACOS
            osName.contains("nix") || osName.contains("nux") || osName.contains("aix") -> OS.LINUX
            else -> OS.UNKNOWN
        }
    }

    /** True if running as a self-contained portable application. */
    val isPortable: Boolean by lazy {
        val customDirProp = System.getProperty("cloudstream.data.dir")
        if (!customDirProp.isNullOrBlank()) return@lazy false

        val appRoot = try {
            val jvmHome = System.getProperty("java.home")
            if (!jvmHome.isNullOrBlank()) {
                val jvmDir = File(jvmHome)
                if (jvmDir.name.equals("runtime", ignoreCase = true)) {
                    jvmDir.parentFile
                } else null
            } else null
        } catch (_: Throwable) { null }

        val candidateDirs = listOfNotNull(
            appRoot,
            File(System.getProperty("user.dir")),
        )

        candidateDirs.any { File(it, "portable.txt").exists() || File(it, ".portable").exists() }
    }

    /** Detects whether the current runtime was installed via Inno Setup (EXE), MSI, or is Portable. */
    val installationFlavor: Flavor by lazy {
        if (isPortable) return@lazy Flavor.PORTABLE

        val appRoot = try {
            val jvmHome = System.getProperty("java.home")
            if (!jvmHome.isNullOrBlank()) {
                val jvmDir = File(jvmHome)
                if (jvmDir.name.equals("runtime", ignoreCase = true)) {
                    jvmDir.parentFile
                } else null
            } else null
        } catch (_: Throwable) { null }

        val candidateDirs = listOfNotNull(
            appRoot,
            File(System.getProperty("user.dir")),
        )

        val hasInnoUninstaller = candidateDirs.any {
            File(it, "unins000.exe").exists() || File(it, "unins001.exe").exists()
        }

        if (hasInnoUninstaller) {
            Flavor.INNO_SETUP
        } else if (appRoot != null) {
            Flavor.MSI
        } else {
            Flavor.INNO_SETUP
        }
    }

    /** The base application data directory, OS-aware. */
    val appDataDir: File by lazy {
        val customDirProp = System.getProperty("cloudstream.data.dir")
        if (!customDirProp.isNullOrBlank()) {
            return@lazy File(customDirProp).also { it.mkdirs() }
        }

        if (isPortable) {
            val appRoot = try {
                val jvmHome = System.getProperty("java.home")
                if (!jvmHome.isNullOrBlank()) {
                    val jvmDir = File(jvmHome)
                    if (jvmDir.name.equals("runtime", ignoreCase = true)) {
                        jvmDir.parentFile
                    } else null
                } else null
            } catch (_: Throwable) { null }

            val candidateDirs = listOfNotNull(
                appRoot,
                File(System.getProperty("user.dir")),
            )

            for (dir in candidateDirs) {
                if (File(dir, "portable.txt").exists() || File(dir, ".portable").exists()) {
                    return@lazy File(dir, "CloudStreamData").also { it.mkdirs() }
                }
            }
        }

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

    /** Directory for isolated AppContainer sandbox runtime, temp files, and worker data. */
    val sandboxDir: File by lazy {
        File(appDataDir, "Sandbox").also { it.mkdirs() }
    }

    /** Directory for installed extensions/plugins. */
    val extensionsDir: File by lazy {
        File(appDataDir, "Extensions").also { it.mkdirs() }
    }

    /** Directory for cache files. */
    val cacheDir: File by lazy {
        File(appDataDir, "cache").also { it.mkdirs() }
    }

    /** Directory for transpiled plugin JVM bytecode, security-patched jars, and translation markers. */
    val pluginCacheDir: File by lazy {
        File(cacheDir, "plugins").also { it.mkdirs() }
    }

    /** Directory for log files. */
    val logsDir: File by lazy {
        File(appDataDir, "logs").also { it.mkdirs() }
    }

    /** Directory for extracted MPV shaders. */
    val shadersDir: File by lazy {
        File(appDataDir, "shaders").also { it.mkdirs() }
    }

    /** Directory for user-provided custom fonts. */
    val fontsDir: File by lazy {
        File(appDataDir, "fonts").also { it.mkdirs() }
    }

    /** Directory for video player screenshots. */
    val screenshotsDir: File
        get() {
            val customPath = com.lagradost.common.storage.DesktopDataStore.getKey<String>("player_screenshot_dir")
            if (!customPath.isNullOrBlank()) {
                val f = File(customPath)
                if (f.exists() || f.mkdirs()) return f
            }
            val userHome = System.getProperty("user.home") ?: ""
            val picturesDir = File(userHome, "Pictures")
            val targetDir = if (picturesDir.exists() && picturesDir.isDirectory) {
                File(picturesDir, "CloudStream")
            } else {
                File(appDataDir, "screenshots")
            }
            return targetDir.also { it.mkdirs() }
        }
}
