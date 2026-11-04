package com.lagradost.cloudstream3.desktop.init

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import java.io.File

/**
 * Manages detection of abnormal terminations, native crashes, and unhandled exceptions
 * between application sessions with zero false positives on clean exits.
 */
object CrashRecoveryManager {
    private val sessionLockFile by lazy { File(PlatformPaths.appDataDir, ".session_active") }
    private val acknowledgedCrashFile by lazy { File(PlatformPaths.appDataDir, ".last_acknowledged_crash") }

    var hasCrashDetected by mutableStateOf(false)
        private set

    /**
     * Inspects previous session health during pre-UI startup.
     * Evaluates dirty exits and new fatal crash artifacts without false-positive durations.
     */
    fun checkPreviousSession() {
        try {
            val wasActive = sessionLockFile.exists()
            var isAbnormalExit = false

            if (wasActive) {
                val previousPid = try {
                    sessionLockFile.readText().trim().toLongOrNull()
                } catch (_: Throwable) {
                    null
                }

                // If previous PID is still running right now, it is a concurrent instance, not a crash
                val isProcessStillAlive = previousPid != null && try {
                    ProcessHandle.of(previousPid).map { it.isAlive }.orElse(false)
                } catch (_: Throwable) {
                    false
                }

                if (!isProcessStillAlive) {
                    isAbnormalExit = true
                }
            }

            // Only evaluate crash dumps if the previous session exited abnormally
            if (isAbnormalExit) {
                val lastAckTime = if (acknowledgedCrashFile.exists()) {
                    acknowledgedCrashFile.readText().trim().toLongOrNull() ?: 0L
                } else {
                    0L
                }

                val crashLog = File(PlatformPaths.appDataDir, "crash.log")
                val latestFatalTime = if (crashLog.exists()) crashLog.lastModified() else 0L

                val latestNativeCrashTime = listOf(PlatformPaths.appDataDir, PlatformPaths.sandboxDir).flatMap { dir ->
                    dir.listFiles { f -> f.name.startsWith("hs_err_pid") && f.name.endsWith(".log") }?.toList() ?: emptyList()
                }.maxOfOrNull { it.lastModified() } ?: 0L

                val latestCrashTime = maxOf(latestFatalTime, latestNativeCrashTime)

                AppLogger.w("CrashRecoveryManager: Previous session ended abnormally (latestCrashTime=$latestCrashTime, lastAckTime=$lastAckTime)")
                hasCrashDetected = true

                if (latestCrashTime > 0) {
                    acknowledgedCrashFile.writeText(latestCrashTime.toString())
                }
            } else {
                hasCrashDetected = false
            }

            // Write current running PID as active marker
            try {
                sessionLockFile.writeText(ProcessHandle.current().pid().toString())
            } catch (_: Throwable) {
                sessionLockFile.writeText(System.currentTimeMillis().toString())
            }
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
        } catch (e: Throwable) { AppLogger.d("CrashRecoveryManager", "Session marker operation note: ${e.message}") }
    }

    fun dismiss() {
        hasCrashDetected = false
        try {
            acknowledgedCrashFile.writeText(System.currentTimeMillis().toString())
        } catch (e: Throwable) { AppLogger.d("CrashRecoveryManager", "Session marker operation note: ${e.message}") }
    }
}
