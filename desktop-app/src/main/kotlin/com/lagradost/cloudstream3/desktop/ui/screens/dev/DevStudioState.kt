package com.lagradost.cloudstream3.desktop.ui.screens.dev

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global coordinator for Dev Studio & Live LogCat visibility across windows.
 */
object DevStudioState {
    private val _isOpen = MutableStateFlow(false)
    val isOpen = _isOpen.asStateFlow()

    private val _isDetachedWindow = MutableStateFlow(false)
    val isDetachedWindow = _isDetachedWindow.asStateFlow()

    fun toggle() {
        if (_isOpen.value) {
            close()
        } else {
            open(detached = _isDetachedWindow.value)
        }
    }

    fun open(detached: Boolean = false) {
        _isDetachedWindow.value = detached
        _isOpen.value = true
    }

    fun close() {
        _isOpen.value = false
    }

    fun detachToWindow() {
        _isDetachedWindow.value = true
        _isOpen.value = true
    }

    fun dockToMain() {
        _isDetachedWindow.value = false
        _isOpen.value = true
    }
}
