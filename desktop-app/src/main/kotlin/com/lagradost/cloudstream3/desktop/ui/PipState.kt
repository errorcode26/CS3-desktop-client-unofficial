package com.lagradost.cloudstream3.desktop.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object PipState {
    private val _isPipMode = MutableStateFlow(false)
    val isPipMode: StateFlow<Boolean> = _isPipMode.asStateFlow()

    fun setPipMode(enabled: Boolean) {
        _isPipMode.value = enabled
    }
}
