package com.lagradost.cloudstream3.desktop.init

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

data class FailedPluginReport(
    val pluginName: String,
    val file: File,
    val errorMessage: String,
)

object PluginFailureState {
    private val _activeFailure = MutableStateFlow<FailedPluginReport?>(null)
    val activeFailure: StateFlow<FailedPluginReport?> = _activeFailure.asStateFlow()

    fun reportFailure(pluginName: String, file: File, error: String) {
        _activeFailure.value = FailedPluginReport(pluginName, file, error)
    }

    fun dismiss() {
        _activeFailure.value = null
    }
}
