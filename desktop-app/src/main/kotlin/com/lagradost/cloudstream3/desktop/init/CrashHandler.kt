package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.desktop.AppConfig
import com.lagradost.common.logging.LogBuffer
import com.lagradost.common.platform.PlatformPaths
import java.awt.Color
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Font
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JLabel
import javax.swing.JOptionPane
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities

fun initCrashHandler() {
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        showFatalCrashDialog(thread, throwable)
    }
}

fun classifySubsystem(t: Throwable): String {
    return when (t) {
        is java.security.GeneralSecurityException,
        is java.security.InvalidParameterException,
        is javax.net.ssl.SSLException,
        is javax.net.ssl.SSLHandshakeException -> "Security & Cryptography / TLS Engine"
        is LinkageError,
        is ClassNotFoundException,
        is NoClassDefFoundError,
        is ExceptionInInitializerError -> "JVM Runtime & ClassLoading Engine"
        is kotlinx.coroutines.TimeoutCancellationException,
        is kotlinx.coroutines.CancellationException -> "Coroutine Concurrency & Lifecycles"
        is java.io.IOException,
        is java.nio.file.FileSystemException -> "Windows Disk & File I/O Engine"
        is java.sql.SQLException -> "Database & KeyValue Storage Engine"
        is OutOfMemoryError -> "Memory Exhaustion (OOM)"
        else -> "Application Core Runtime"
    }
}

private val isCrashDialogShowing = AtomicBoolean(false)

fun showFatalCrashDialog(thread: Thread, throwable: Throwable) {
    if (!isCrashDialogShowing.compareAndSet(false, true)) {
        try {
            val crashDir = PlatformPaths.appDataDir
            crashDir.mkdirs()
            val crashFile = File(crashDir, "crash.log")
            val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
            crashFile.appendText("\n\n[Secondary Fatal Error on ${thread.name}]:\n$stackTrace\n")
        } catch (e: Throwable) { System.err.println("CrashHandler internal note: ${e.message}") }
        return
    }

    if (!SwingUtilities.isEventDispatchThread()) {
        try {
            SwingUtilities.invokeAndWait {
                renderAndShowCrashDialog(thread, throwable)
            }
            return
        } catch (e: Throwable) { System.err.println("CrashHandler internal note: ${e.message}") }
    }
    renderAndShowCrashDialog(thread, throwable)
}

private fun renderAndShowCrashDialog(thread: Thread, throwable: Throwable) {
    try {
        val crashDir = PlatformPaths.appDataDir
        crashDir.mkdirs()
        val crashFile = File(crashDir, "crash.log")

        val stackTrace = StringWriter().also { throwable.printStackTrace(PrintWriter(it)) }.toString()
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())

        val rootCause = generateSequence(throwable) { it.cause }.last()
        val rootCauseClass = rootCause.javaClass.name
        val rootCauseMsg = rootCause.message ?: throwable.message ?: "No detailed error message available."
        val subsystem = classifySubsystem(rootCause)

        val failingElement = throwable.stackTrace.firstOrNull { it.className.startsWith("com.lagradost") }
            ?: throwable.stackTrace.firstOrNull()
        val failingLocation = failingElement?.let { "${it.fileName ?: "Unknown"}:${it.lineNumber} in ${it.methodName}" } ?: "Unknown code location"

        val crashReport = buildString {
            appendLine("========================================")
            appendLine("CloudStream Desktop - Fatal Crash Report")
            appendLine("Timestamp: $time")
            appendLine("Subsystem: $subsystem")
            appendLine("Thread: ${thread.name} (ID: ${thread.id})")
            appendLine("Error: $rootCauseClass")
            appendLine("Message: $rootCauseMsg")
            appendLine("Location: $failingLocation")
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
        } catch (e: Throwable) { System.err.println("CrashHandler internal note: ${e.message}") }

        // 2. Automatically copy full report to clipboard
        fun copyReportToClipboard() {
            try {
                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                clipboard.setContents(StringSelection(crashReport), null)
            } catch (e: Throwable) { System.err.println("CrashHandler internal note: ${e.message}") }
        }
        copyReportToClipboard()

        // 3. Construct rich Diagnostics Panel for display
        val panel = JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = BorderFactory.createEmptyBorder(10, 10, 10, 10)
        }

        val headerLabel = JLabel("<html><b><font size='5' color='#D32F2F'>Fatal Crash Detected</font></b></html>")
        val subsystemLabel = JLabel("<html><b>Subsystem:</b> <font color='#1976D2'>$subsystem</font></html>")
        val threadLabel = JLabel("<html><b>Thread:</b> ${thread.name}</html>")
        val errorLabel = JLabel("<html><b>Exception:</b> <code>$rootCauseClass</code></html>")
        val causeLabel = JLabel("<html><b>Reason:</b> <font color='#D32F2F'>$rootCauseMsg</font></html>")
        val locLabel = JLabel("<html><b>Location:</b> <code>$failingLocation</code></html>")

        panel.add(headerLabel)
        panel.add(Box.createVerticalStrut(8))
        panel.add(subsystemLabel)
        panel.add(Box.createVerticalStrut(4))
        panel.add(threadLabel)
        panel.add(Box.createVerticalStrut(4))
        panel.add(errorLabel)
        panel.add(Box.createVerticalStrut(4))
        panel.add(causeLabel)
        panel.add(Box.createVerticalStrut(4))
        panel.add(locLabel)
        panel.add(Box.createVerticalStrut(10))

        val textArea = JTextArea(crashReport).apply {
            isEditable = false
            font = Font("Consolas", Font.PLAIN, 11)
            background = Color(24, 24, 24)
            foreground = Color(220, 220, 220)
            caretPosition = 0
            border = BorderFactory.createEmptyBorder(6, 6, 6, 6)
        }
        val scrollPane = JScrollPane(textArea).apply {
            preferredSize = Dimension(720, 260)
        }
        panel.add(scrollPane)
        panel.add(Box.createVerticalStrut(6))

        val footerLabel = JLabel("<html><font size='2' color='gray'>Log written to: ${crashFile.absolutePath}</font></html>")
        panel.add(footerLabel)

        // 4. Interactive Dialog Loop
        val isDbCrash = subsystem == "Database & KeyValue Storage Engine" || rootCause is java.sql.SQLException
        val options = if (isDbCrash) {
            arrayOf("Repair Database", "Open Logs in Notepad", "Copy Diagnostics", "Restart in Safe Mode", "Close & Exit")
        } else {
            arrayOf("Open Logs in Notepad", "Copy Diagnostics", "Restart in Safe Mode", "Close & Exit")
        }
        var shouldExit = false
        while (!shouldExit) {
            val choice = JOptionPane.showOptionDialog(
                null,
                panel,
                "CloudStream Crash Reporter & Diagnostics",
                JOptionPane.DEFAULT_OPTION,
                JOptionPane.ERROR_MESSAGE,
                null,
                options,
                options[0],
            )

            val action = if (isDbCrash) {
                when (choice) {
                    0 -> "repair"
                    1 -> "notepad"
                    2 -> "copy"
                    3 -> "safe_mode"
                    else -> "exit"
                }
            } else {
                when (choice) {
                    0 -> "notepad"
                    1 -> "copy"
                    2 -> "safe_mode"
                    else -> "exit"
                }
            }

            when (action) {
                "repair" -> {
                    val ok = try {
                        com.lagradost.common.db.DatabaseFactory.repairDatabase()
                    } catch (e: Throwable) {
                        System.err.println("Database repair failure in crash handler: ${e.message}")
                        false
                    }
                    if (ok) {
                        footerLabel.text = "<html><font size='2' color='#388E3C'><b>✓ Corrupted database quarantined to .bak and rebuilt. Relaunch the app.</b></font></html>"
                    } else {
                        footerLabel.text = "<html><font size='2' color='#D32F2F'><b>Repair failed. Check logs for details.</b></font></html>"
                    }
                    panel.revalidate()
                    panel.repaint()
                }
                "notepad" -> {
                    // Open Logs in Notepad
                    try {
                        ProcessBuilder("notepad.exe", crashFile.absolutePath).start()
                    } catch (e: Throwable) {
                        System.err.println("CrashHandler open with ProcessBuilder fallback note: ${e.message}")
                        try {
                            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                                Desktop.getDesktop().open(crashFile)
                            }
                        } catch (e: Throwable) { System.err.println("CrashHandler internal note: ${e.message}") }
                    }
                }
                "copy" -> {
                    // Copy Diagnostics
                    copyReportToClipboard()
                    footerLabel.text = "<html><font size='2' color='#388E3C'><b>✓ Diagnostics successfully copied to clipboard!</b></font></html>"
                    panel.revalidate()
                    panel.repaint()
                }
                "safe_mode" -> {
                    // Restart in Safe Mode
                    try {
                        File(crashDir, ".safe_mode").createNewFile()
                        footerLabel.text = "<html><font size='2' color='#E65100'><b>Safe Mode armed (.safe_mode created). Relaunch app to enter Safe Mode.</b></font></html>"
                        panel.revalidate()
                        panel.repaint()
                    } catch (e: Throwable) { System.err.println("CrashHandler internal note: ${e.message}") }
                }
                else -> {
                    shouldExit = true
                }
            }
        }
    } catch (t: Throwable) {
        t.printStackTrace()
    }
    kotlin.system.exitProcess(1)
}
