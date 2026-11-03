package com.lagradost.cloudstream3.desktop.init

import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore

/**
 * Initializes security-related subsystems:
 * - Conscrypt & BouncyCastle security providers
 * - Profile binding for DataStore
 * - Rhino JavaScript Security ClassShutter
 */
fun initSecurity() {
    // Conscrypt security provider (BoringSSL - matches Chrome JA3 TLS fingerprint)
    try {
        java.security.Security.addProvider(org.conscrypt.Conscrypt.newProvider())
        AppLogger.i("Registered Conscrypt Security Provider (BoringSSL)")
    } catch (e: Exception) {
        AppLogger.e("Failed to register Conscrypt: ${e.message}")
    }

    // BouncyCastle security provider
    try {
        java.security.Security.addProvider(org.bouncycastle.jce.provider.BouncyCastleProvider())
        AppLogger.i("Registered BouncyCastle Security Provider")
    } catch (e: Exception) {
        AppLogger.e("Failed to register BouncyCastle: ${e.message}")
    }

    // Bind active profile provider (storage and configs pre-initialized in Main.kt)
    DesktopDataStore.activeProfileProvider = { com.lagradost.cloudstream3.desktop.profile.ProfileManager.activeProfileId }

    // Rhino JavaScript Security ClassShutter (prevents plugins from reflecting/importing Java classes in JS)
    com.lagradost.runtime.security.RhinoSecurity.init()
}
