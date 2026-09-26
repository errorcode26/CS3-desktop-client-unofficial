package com.lagradost.cloudstream3.desktop.shadowui

import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * Manages the desktop shadow UI dialog stack and user event dispatching.
 */
object ShadowUi {
    val dialogs = MutableStateFlow<List<ShadowDialog>>(emptyList())
    val version = MutableStateFlow(0)
    val sessionPluginId = MutableStateFlow<String?>(null)
    val sessionProducedDialogs = MutableStateFlow(false)
    val sessionState = MutableStateFlow<SessionState>(SessionState.Idle)
    val toast = MutableStateFlow<ToastEvent?>(null)

    val executor: ExecutorService = Executors.newSingleThreadExecutor { r ->
        Thread(r, "shadow-ui").apply { isDaemon = true }
    }

    private val dialogIds = AtomicLong(0)

    enum class SessionState { Idle, Running, Finished }
    data class ToastEvent(val text: String, val at: Long)

    fun beginSession(pluginId: String) {
        sessionPluginId.value = pluginId
        sessionProducedDialogs.value = false
        sessionState.value = SessionState.Running
        synchronized(dialogs) { dialogs.value = emptyList() }
    }

    fun finishSessionDelayed(delayMillis: Long = 900) {
        executor.execute {
            try {
                Thread.sleep(delayMillis)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
            if (sessionState.value == SessionState.Running) {
                sessionState.value = SessionState.Finished
            }
        }
    }

    fun endSession() {
        sessionState.value = SessionState.Idle
        sessionPluginId.value = null
        sessionProducedDialogs.value = false
        synchronized(dialogs) { dialogs.value = emptyList() }
        bump()
    }

    fun push(dialog: ShadowDialog) {
        synchronized(dialogs) {
            if (dialogs.value.any { it.platform === dialog.platform }) return
            dialogs.value = dialogs.value + dialog
        }
        sessionProducedDialogs.value = true
        if (sessionState.value == SessionState.Idle) {
            sessionState.value = SessionState.Finished
        }
        bump()
    }

    fun pop(platform: Any?) {
        if (platform == null) return
        synchronized(dialogs) {
            dialogs.value = dialogs.value.filterNot { it.platform === platform }
        }
        bump()
    }

    fun isShowing(platform: Any?): Boolean =
        dialogs.value.any { it.platform === platform }

    fun stackEmptyAndFinished(): Boolean =
        sessionState.value == SessionState.Finished && dialogs.value.isEmpty()

    fun bump() {
        version.value = version.value + 1
    }

    fun dispatch(tag: String, block: () -> Unit) {
        executor.execute {
            try {
                block()
            } catch (t: Throwable) {
                System.err.println("[$tag] ${t::class.simpleName}: ${t.message}")
            } finally {
                bump()
            }
        }
    }

    fun nextDialogId(): Long = dialogIds.incrementAndGet()

    fun toast(text: String) {
        toast.value = ToastEvent(text, System.currentTimeMillis())
    }
}

class ShadowDialog(
    @JvmField val platform: Any,
    @JvmField val view: View?,
    @JvmField val fromFragment: Boolean = false,
    @JvmField val fragmentTag: String? = null,
    @JvmField val id: Long = ShadowUi.nextDialogId(),
) {
    fun sameAs(other: Any?): Boolean = other is ShadowDialog && other.platform === platform
}
