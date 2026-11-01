package com.lagradost.cloudstream3.desktop.ui.components

import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.common.logging.LogBuffer
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.time.LocalDateTime

/**
 * Diagnostic log retrieval and bundle generation for bug reporting and crash recovery.
 */
fun openLogsFolder(): Boolean {
    return try {
        val logsDir = File(com.lagradost.common.platform.PlatformPaths.appDataDir, "logs").apply {
            if (!exists()) mkdirs()
        }
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Desktop.getDesktop().open(logsDir)
            true
        } else {
            ProcessBuilder("explorer.exe", logsDir.absolutePath).start()
            true
        }
    } catch (_: Throwable) {
        false
    }
}

fun copyDiagnosticLogsToClipboard(): Boolean {
    return try {
        val diagnosticInfo = buildString {
            appendLine("============================================================")
            appendLine("CLOUDSTREAM DESKTOP - FULL DIAGNOSTIC REPORT")
            appendLine("Timestamp: ${LocalDateTime.now()}")
            appendLine("App Version: ${AppConfig.APP_VERSION}")
            appendLine("OS: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
            appendLine("Java Runtime: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
            appendLine("============================================================")

            appendLine()
            appendLine("============================================================")
            appendLine("SECTION 1: NATIVE JVM & FATAL CRASH DUMP")
            appendLine("============================================================")

            var hasCrashSection = false
            val crashLog = File(com.lagradost.common.platform.PlatformPaths.appDataDir, "crash.log")
            if (crashLog.exists() && crashLog.length() > 0) {
                appendLine("[Fatal Unhandled Crash (crash.log)]:")
                appendLine(crashLog.readText().takeLast(4000).trim())
                appendLine()
                hasCrashSection = true
            }

            val searchDirs = listOf(
                com.lagradost.common.platform.PlatformPaths.sandboxDir,
                com.lagradost.common.platform.PlatformPaths.appDataDir,
            )
            val latestNativeCrash = searchDirs.flatMap { dir ->
                dir.listFiles { f -> f.name.startsWith("hs_err_pid") && f.name.endsWith(".log") }?.toList() ?: emptyList()
            }.filter { System.currentTimeMillis() - it.lastModified() < 48 * 3600 * 1000L }
                .maxByOrNull { it.lastModified() }

            if (latestNativeCrash != null) {
                appendLine("[Latest Native JVM Crash Dump (${latestNativeCrash.name})]:")
                appendLine(latestNativeCrash.readLines().take(120).joinToString("\n"))
                hasCrashSection = true
            }

            if (!hasCrashSection) {
                appendLine("(No native JVM or fatal unhandled crashes recorded)")
            }

            appendLine()
            appendLine("============================================================")
            appendLine("SECTION 2: STREAMING & APPLICATION LOGS (logs/app.log)")
            appendLine("============================================================")
            val diskLogFile = File(com.lagradost.common.platform.PlatformPaths.appDataDir, "logs/app.log")
            val diskLines = if (diskLogFile.exists() && diskLogFile.length() > 0) {
                try {
                    diskLogFile.readLines().takeLast(250)
                } catch (_: Throwable) {
                    emptyList()
                }
            } else {
                emptyList()
            }

            if (diskLines.isNotEmpty()) {
                diskLines.forEach { appendLine(it) }
            } else {
                val snapshot = LogBuffer.getSnapshot()
                if (snapshot.isNotEmpty()) {
                    appendLine(LogBuffer.exportLogsAsText(snapshot))
                } else {
                    appendLine("(No application logs recorded)")
                }
            }

            appendLine()
            appendLine("============================================================")
            appendLine("SECTION 3: RUNTIME ERROR REGISTRY (DesktopErrorReporter)")
            appendLine("============================================================")
            appendLine(DesktopErrorReporter.getSnapshot())
            appendLine("============================================================")
        }
        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
        clipboard.setContents(StringSelection(diagnosticInfo), null)
        true
    } catch (_: Throwable) {
        false
    }
}
