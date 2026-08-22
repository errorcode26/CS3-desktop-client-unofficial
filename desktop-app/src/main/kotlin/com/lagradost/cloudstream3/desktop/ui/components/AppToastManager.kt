package com.lagradost.cloudstream3.desktop.ui.components

import com.lagradost.cloudstream3.desktop.utils.appScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

enum class ToastType {
    INFO,
    SUCCESS,
    WARNING,
    ERROR,
}

data class ToastMessage(
    val id: Long,
    val text: String,
    val type: ToastType = ToastType.INFO,
    val durationMs: Long = 3500L,
    val timestamp: Long = System.currentTimeMillis(),
)

object AppToastManager {
    private val nextId = AtomicLong(1L)
    private val _toasts = MutableStateFlow<List<ToastMessage>>(emptyList())
    val toasts: StateFlow<List<ToastMessage>> = _toasts.asStateFlow()

    private val lastEmittedMessages = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private const val DEBOUNCE_WINDOW_MS = 2500L

    fun showToast(
        text: String,
        type: ToastType = ToastType.INFO,
        durationMs: Long = 3500L,
    ) {
        if (text.isBlank()) return
        val now = System.currentTimeMillis()
        val lastTime = lastEmittedMessages[text] ?: 0L
        if (now - lastTime < DEBOUNCE_WINDOW_MS) {
            return // Suppress spam of identical messages within window
        }
        lastEmittedMessages[text] = now

        val id = nextId.getAndIncrement()
        val message = ToastMessage(id = id, text = text, type = type, durationMs = durationMs)

        _toasts.update { current ->
            // Keep at most 4 toasts visible at once
            val trimmed = if (current.size >= 4) current.drop(1) else current
            trimmed + message
        }

        appScope.launch(Dispatchers.Main) {
            delay(durationMs)
            dismiss(id)
        }
    }

    fun showInfo(text: String, durationMs: Long = 3500L) = showToast(text, ToastType.INFO, durationMs)
    fun showSuccess(text: String, durationMs: Long = 3500L) = showToast(text, ToastType.SUCCESS, durationMs)
    fun showWarning(text: String, durationMs: Long = 4000L) = showToast(text, ToastType.WARNING, durationMs)
    fun showError(text: String, durationMs: Long = 5000L) = showToast(text, ToastType.ERROR, durationMs)

    fun showPluginError(pluginName: String, action: String, error: String?) {
        val cleanError = error?.takeIf { it.isNotBlank() } ?: "Unknown error"
        showWarning("⚠️ [$pluginName] $action failed: $cleanError", durationMs = 4500L)
    }

    fun showPluginQuarantined(pluginName: String, reason: String?) {
        val cleanReason = reason?.takeIf { it.isNotBlank() } ?: "Incompatible or corrupt bytecode"
        showError("❌ Plugin '$pluginName' failed to load and was disabled: $cleanReason", durationMs = 6000L)
    }

    fun dismiss(id: Long) {
        _toasts.update { current -> current.filterNot { it.id == id } }
    }
}
