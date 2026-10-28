package com.lagradost.cloudstream3.desktop.init

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks application startup completion and progress to gate background activities
 * and render a smooth startup indicator.
 */
object AppReadyState {
    private val _isReady = AtomicBoolean(false)
    val isReady: Boolean get() = _isReady.get()

    private val _progress = MutableStateFlow(0.10f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _statusText = MutableStateFlow("Initializing runtime...")
    val statusText: StateFlow<String> = _statusText.asStateFlow()

    fun updateProgress(value: Float, status: String? = null) {
        _progress.value = value.coerceIn(0f, 1f)
        if (status != null) {
            _statusText.value = status
        }
    }

    fun markReady() {
        _progress.value = 1.0f
        _statusText.value = "Ready"
        _isReady.set(true)
    }
}
