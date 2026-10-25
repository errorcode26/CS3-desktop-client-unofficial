package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.common.logging.LogBuffer
import com.lagradost.common.platform.PlatformPaths
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.JOptionPane

private const val DISCORD_INVITE_URL = "https://discord.gg/cU6Fr7Wx4"

fun initCrashHandler() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        try {
            val crashDir = PlatformPaths.appDataDir
            crashDir.mkdirs()
            val crashFile = File(crashDir, "crash.log")

            val stackTrace = StringWriter().also { e.printStackTrace(PrintWriter(it)) }.toString()
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())

            val crashReport = buildString {
                appendLine("========================================")
                appendLine("CloudStream Desktop - Fatal Crash Report")
                appendLine("Timestamp: $time")
                appendLine("Version: ${AppConfig.APP_VERSION}")
                appendLine("OS: ${System.getProperty("os.name")} (${System.getProperty("os.arch")})")
                appendLine("Java Runtime: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})")
                appendLine("========================================")
                appendLine()
                appendLine("### EXCEPTION STACK TRACE:")
                appendLine(stackTrace)
                appendLine()
                appendLine("### RECENT APPLICATION LOG STREAM:")
                try {
                    val recentLogs = LogBuffer.getSnapshot()
                    if (recentLogs.isNotEmpty()) {
                        appendLine(LogBuffer.exportLogsAsText(recentLogs))
                    } else {
                        appendLine("(No logs recorded prior to crash)")
                    }
                } catch (t: Throwable) {
                    appendLine("(Failed to dump log buffer: ${t.message})")
                }
            }

            // 1. Write crash report to disk
            try {
                crashFile.appendText("\n\n$crashReport\n")
            } catch (_: Throwable) {}

            // 2. Arm safe mode sentinel for next boot
            try {
                File(crashDir, ".safe_mode").createNewFile()
            } catch (_: Throwable) {}

            // 3. Automatically inject crash report into system clipboard
            fun copyReportToClipboard() {
                try {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(crashReport), null)
                } catch (_: Throwable) {}
            }
            copyReportToClipboard()

            // 4. Interactive Crash Dialog (no File Explorer spam)
            val options = arrayOf("Open Discord to Report", "Copy Again", "Close")
            var shouldExit = false
            while (!shouldExit) {
                val choice = JOptionPane.showOptionDialog(
                    null,
                    "CloudStream encountered an unexpected fatal error and had to close.\n\n" +
                        "✓ The complete crash report and stack trace have been automatically copied to your clipboard!\n\n" +
                        "You can paste (Ctrl+V) directly into our Discord community to report this issue.\n" +
                        "A backup copy of the crash log was also saved to:\n${crashFile.absolutePath}",
                    "CloudStream Crash Reporter",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.ERROR_MESSAGE,
                    null,
                    options,
                    options[0],
                )

                when (choice) {
                    0 -> {
                        // Open Discord to Report
                        try {
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                                Desktop.getDesktop().browse(URI(DISCORD_INVITE_URL))
                            }
                        } catch (_: Throwable) {}
                        shouldExit = true
                    }
                    1 -> {
                        // Copy Again
                        copyReportToClipboard()
                    }
                    else -> {
                        // Close or dialog closed
                        shouldExit = true
                    }
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        kotlin.system.exitProcess(1)
    }
}
