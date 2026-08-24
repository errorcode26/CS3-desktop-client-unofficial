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
        if (thread.name.contains("Plugin", ignoreCase = true) || thread.name.contains("Worker", ignoreCase = true)) {
            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showWarning("⚠️ Background task failed: ${throwable.message ?: throwable.javaClass.simpleName}")
        }
    }

    // Conscrypt security provider (BoringSSL - matches Chrome JA3 TLS fingerprint)
    try {
        java.security.Security.insertProviderAt(org.conscrypt.Conscrypt.newProvider(), 1)
        AppLogger.i("Registered Conscrypt Security Provider (BoringSSL)")
    } catch (e: Exception) {
        AppLogger.e("Failed to register Conscrypt: ${e.message}")
    }

    // BouncyCastle security provider
    java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 2)
    AppLogger.i("Registered BouncyCastle Security Provider")

    // Pre-initialize DataStore & ProfileManager
    DesktopDataStore.init()
    com.lagradost.cloudstream3.desktop.profile.ProfileManager.init()
    DesktopDataStore.activeProfileProvider = { com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfileId }
    com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.reloadFromDataStore()
    com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.reloadFromDataStore()

    // Rhino JavaScript Security ClassShutter (prevents plugins from reflecting/importing Java classes in JS)
    com.lagradost.runtime.security.RhinoSecurity.init()
}
