package com.lagradost.cloudstream3.desktop.init

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import java.io.File

/**
 * Manages detection of abnormal terminations, native JVM crashes, and unhandled exceptions
 * between application sessions.
 */
object CrashRecoveryManager {
    private val sessionLockFile by lazy { File(PlatformPaths.appDataDir, ".session_active") }

    var hasCrashDetected by mutableStateOf(false)
        private set

    /**
     * Inspects previous session health during pre-UI startup.
     * Checks for uncleaned session markers, recent fatal crash logs, or native hs_err dumps.
     */
    fun checkPreviousSession() {
        try {
            val wasActive = sessionLockFile.exists()
            val crashLog = File(PlatformPaths.appDataDir, "crash.log")
            val hasRecentFatalLog = crashLog.exists() && (System.currentTimeMillis() - crashLog.lastModified() < 24 * 3600 * 1000L)

            val recentNativeCrash = listOf(PlatformPaths.sandboxDir, PlatformPaths.appDataDir).flatMap { dir ->
                dir.listFiles { f -> f.name.startsWith("hs_err_pid") && f.name.endsWith(".log") }?.toList() ?: emptyList()
            }.any { System.currentTimeMillis() - it.lastModified() < 24 * 3600 * 1000L }

            if (wasActive || hasRecentFatalLog || recentNativeCrash) {
                AppLogger.w("CrashRecoveryManager: Previous session ended abnormally (active=$wasActive, fatalLog=$hasRecentFatalLog, nativeCrash=$recentNativeCrash)")
                hasCrashDetected = true
            }

            // Write active marker for the current running session
            sessionLockFile.writeText(System.currentTimeMillis().toString())
        } catch (t: Throwable) {
            AppLogger.d("CrashRecoveryManager: Session check warning: ${t.message}")
        }
    }

    /**
     * Deletes the active session marker on normal, clean application shutdown.
     */
    fun markCleanShutdown() {
        try {
            if (sessionLockFile.exists()) {
                sessionLockFile.delete()
            }
        } catch (_: Throwable) {}
    }

    fun dismiss() {
        hasCrashDetected = false
    }
}
