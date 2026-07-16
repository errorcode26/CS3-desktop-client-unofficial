package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.desktop.DesktopErrorReporter
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore

/**
 * Initializes security-related subsystems:
 * - Uncaught exception handler
 * - BouncyCastle security provider (Android AES-GCM compat)
 * - DataStore pre-initialization (prevents SecurityManager issues)
 */
fun initSecurity() {
    // Uncaught exception handler
    Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
        DesktopErrorReporter.report("Unhandled exception in ${thread.name}", throwable)
    }

    // BouncyCastle security provider
    java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1)
    AppLogger.i("Registered BouncyCastle Security Provider")

    // Pre-initialize DataStore
    // Force initialization of DataStore BEFORE plugins are loaded.
    // This prevents plugins from triggering <clinit> which causes the SecurityManager to block File.mkdirs()
    DesktopDataStore.init()
}
