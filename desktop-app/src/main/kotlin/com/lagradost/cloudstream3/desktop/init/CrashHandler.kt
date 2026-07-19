package com.lagradost.cloudstream3.desktop.init

import com.lagradost.common.platform.PlatformPaths
import java.io.File

fun initCrashHandler() {
    Thread.setDefaultUncaughtExceptionHandler { _, e ->
        try {
            val crashDir = PlatformPaths.appDataDir
            crashDir.mkdirs()
            val crashFile = File(crashDir, "crash.log")

            val stackTrace = java.io.StringWriter().also { e.printStackTrace(java.io.PrintWriter(it)) }.toString()
            val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(java.util.Date())

            crashFile.appendText("\n\n--- CRASH LOG: $time ---\n")
            crashFile.appendText(stackTrace)

            try {
                java.awt.Desktop.getDesktop().open(crashDir)
            } catch (t: Throwable) {
                // Ignore if opening folder fails
            }

            javax.swing.JOptionPane.showMessageDialog(
                null,
                "CloudStream encountered a fatal error and crashed.\n\nA crash log has been saved to:\n${crashFile.absolutePath}\n\nPlease share this file with the developers.",
                "CloudStream Crash Reporter",
                javax.swing.JOptionPane.ERROR_MESSAGE,
            )
        } catch (t: Throwable) {
            // Failsafe, don't crash the crash handler itself
            t.printStackTrace()
        }
        kotlin.system.exitProcess(1)
    }
}
