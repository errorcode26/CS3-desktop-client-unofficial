package com.lagradost.cloudstream3.desktop.network.webview

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

data class ActiveVerificationSession(
    val session: WindowsWebView2Session,
    val targetUrl: String,
    val hostName: String,
    val isDialogVisible: Boolean = false,
    val isSuccess: Boolean = false,
    val latch: CountDownLatch = CountDownLatch(1),
    val isDismissed: AtomicBoolean = AtomicBoolean(false),
)

object EmbeddedVerificationState {
    private val _activeSession = MutableStateFlow<ActiveVerificationSession?>(null)
    val activeSession: StateFlow<ActiveVerificationSession?> = _activeSession.asStateFlow()

    private val activeSessionsByHost = ConcurrentHashMap<String, WindowsWebView2Session>()

    fun getSessionForHost(host: String): WindowsWebView2Session? {
        val cleanHost = host.lowercase().trim()
        if (cleanHost.isBlank()) return null
        activeSessionsByHost[cleanHost]?.let { return it }

        val apex = com.lagradost.cloudstream3.network.CloudflareKiller.getApexDomain(cleanHost)
        if (apex.isNotBlank()) {
            activeSessionsByHost[apex]?.let { return it }
        }

        return activeSessionsByHost.entries.firstOrNull { (k, _) ->
            cleanHost == k || cleanHost.endsWith(".$k") || k.endsWith(".$cleanHost") ||
                (apex.isNotBlank() && (k == apex || apex.endsWith(".$k") || k.endsWith(".$apex")))
        }?.value
    }

    fun closeSession(host: String) {
        val cleanHost = host.lowercase().trim()
        activeSessionsByHost.remove(cleanHost)?.close()
        val apex = com.lagradost.cloudstream3.network.CloudflareKiller.getApexDomain(cleanHost)
        if (apex.isNotBlank()) {
            activeSessionsByHost.remove(apex)?.close()
        }
    }

    fun closeAllSessions() {
        activeSessionsByHost.values.forEach { runCatching { it.close() } }
        activeSessionsByHost.clear()
    }

    fun startSession(
        session: WindowsWebView2Session,
        targetUrl: String,
        hostName: String,
    ): ActiveVerificationSession {
        val cleanHost = hostName.lowercase().trim()
        // Close any pre-existing session in progress
        _activeSession.value?.let { prev ->
            prev.isDismissed.set(true)
            prev.latch.countDown()
            prev.session.close()
        }
        closeSession(cleanHost)

        val newSession = ActiveVerificationSession(
            session = session,
            targetUrl = targetUrl,
            hostName = hostName,
            isDialogVisible = false,
            isSuccess = false,
        )
        _activeSession.value = newSession
        return newSession
    }

    /** Surfacing the embedded Compose modal when an interactive user check is required */
    fun requestDialogVisibility() {
        if (com.lagradost.cloudstream3.desktop.ui.GlobalPlayerState.isPlaybackActive.value) {
            com.lagradost.common.logging.AppLogger.w("EmbeddedVerificationState: Suppressing verification dialog because video playback is currently active.")
            return
        }
        val current = _activeSession.value ?: return
        if (!current.isDialogVisible) {
            _activeSession.value = current.copy(isDialogVisible = true)
        }
    }

    fun reloadCurrentSession() {
        _activeSession.value?.session?.reload()
    }

    fun dismiss() {
        val current = _activeSession.value ?: return
        current.isDismissed.set(true)
        current.latch.countDown()
        // Do NOT call current.session.close() here!
        // The background polling thread might be in the middle of evaluateJs() which would cause a JVM crash.
        // It will be safely dispatched back to the UI thread by the caller.
        _activeSession.value = null
    }

    fun closeSessionSafely(session: com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Session) {
        javax.swing.SwingUtilities.invokeLater {
            session.close()
        }
    }

    fun finishSuccess() {
        val current = _activeSession.value ?: return
        current.latch.countDown()
        _activeSession.value = current.copy(isSuccess = true)
        try {
            Thread.sleep(700)
        } catch (_: InterruptedException) {}
        val cleanHost = current.hostName.lowercase().trim()
        activeSessionsByHost[cleanHost] = current.session
        val apex = com.lagradost.cloudstream3.network.CloudflareKiller.getApexDomain(cleanHost)
        if (apex.isNotBlank() && apex != cleanHost) {
            activeSessionsByHost[apex] = current.session
        }
        _activeSession.value = null
    }
}
