package com.lagradost.cloudstream3.desktop.utils

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object NativeBrowserManager {

    private val _systemBrowser = MutableStateFlow<String?>(null)
    val systemBrowser = _systemBrowser.asStateFlow()

    private val _systemBrowserExecutable = MutableStateFlow<File?>(null)
    val systemBrowserExecutable = _systemBrowserExecutable.asStateFlow()

    init {
        checkInstalled()
    }

    private fun checkInstalled() {
        val osName = System.getProperty("os.name").lowercase()
        val isWindows = osName.contains("win")
        val isMac = osName.contains("mac")

        val edgePaths = mutableListOf<File>()
        val chromePaths = mutableListOf<File>()

        if (isWindows) {
            val progFiles = System.getenv("ProgramFiles") ?: "C:\\Program Files"
            val progFiles86 = System.getenv("ProgramFiles(x86)") ?: "C:\\Program Files (x86)"
            val localAppData = System.getenv("LOCALAPPDATA") ?: "C:\\Users\\Default\\AppData\\Local"

            edgePaths.add(File("$progFiles86\\Microsoft\\Edge\\Application\\msedge.exe"))
            edgePaths.add(File("$progFiles\\Microsoft\\Edge\\Application\\msedge.exe"))

            chromePaths.add(File("$progFiles\\Google\\Chrome\\Application\\chrome.exe"))
            chromePaths.add(File("$progFiles86\\Google\\Chrome\\Application\\chrome.exe"))
            chromePaths.add(File("$localAppData\\Google\\Chrome\\Application\\chrome.exe"))
        } else if (isMac) {
            edgePaths.add(File("/Applications/Microsoft Edge.app/Contents/MacOS/Microsoft Edge"))
            chromePaths.add(File("/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"))
        } else {
            // Linux
            edgePaths.add(File("/usr/bin/microsoft-edge-stable"))
            edgePaths.add(File("/usr/bin/microsoft-edge"))
            chromePaths.add(File("/usr/bin/google-chrome-stable"))
            chromePaths.add(File("/usr/bin/google-chrome"))
        }

        when {
            edgePaths.any { it.exists() } -> {
                _systemBrowser.value = "msedge"
                _systemBrowserExecutable.value = edgePaths.first { it.exists() }
            }
            chromePaths.any { it.exists() } -> {
                _systemBrowser.value = "chrome"
                _systemBrowserExecutable.value = chromePaths.first { it.exists() }
            }
            else -> {
                _systemBrowser.value = null
                _systemBrowserExecutable.value = null
            }
        }
    }
}
