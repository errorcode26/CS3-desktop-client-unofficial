package com.lagradost.cloudstream3.desktop.init

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Tracks application startup completion to gate transient background activities.
 */
object AppReadyState {
    private val _isReady = AtomicBoolean(false)
    val isReady: Boolean get() = _isReady.get()

    fun markReady() {
        _isReady.set(true)
    }
}
