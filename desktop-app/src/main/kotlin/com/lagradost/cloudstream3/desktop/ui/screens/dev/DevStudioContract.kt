package com.lagradost.cloudstream3.desktop.ui.screens.dev

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.common.logging.LogEntry
import com.lagradost.common.logging.LogLevel
import com.lagradost.common.logging.LogSubsystem
import com.lagradost.runtime.executor.PluginHealthStats

data class DevStudioUiState(
    val logs: List<LogEntry> = emptyList(),
    val totalCount: Int = 0,
    val errorCount: Int = 0,
    val warnCount: Int = 0,
    val infoCount: Int = 0,
    val debugCount: Int = 0,
    val selectedLevel: LogLevel = LogLevel.VERBOSE,
    val selectedSubsystem: LogSubsystem = LogSubsystem.ALL,
    val selectedPlugin: String? = null,
    val availablePlugins: List<String> = emptyList(),
    val pluginHealth: Map<String, PluginHealthStats> = emptyMap(),
    val exceptionsOnly: Boolean = false,
    val searchQuery: String = "",
    val isPaused: Boolean = false,
    val selectedEntry: LogEntry? = null,
    val isInspectorOpen: Boolean = false,
) : UiState

sealed interface DevStudioUiEvent : UiEvent {
    data class SelectLevel(val level: LogLevel) : DevStudioUiEvent
    data class SelectSubsystem(val subsystem: LogSubsystem) : DevStudioUiEvent
    data class SelectPlugin(val pluginName: String?) : DevStudioUiEvent
    data class UpdateSearchQuery(val query: String) : DevStudioUiEvent
    data object ToggleExceptionsOnly : DevStudioUiEvent
    data object TogglePause : DevStudioUiEvent
    data class SelectEntry(val entry: LogEntry?) : DevStudioUiEvent
    data object ClearLogs : DevStudioUiEvent
    data class CopyAiSnapshot(val entryId: Long? = null) : DevStudioUiEvent
    data object ExportLogs : DevStudioUiEvent
    data object CloseInspector : DevStudioUiEvent
    data class ResetCircuit(val providerName: String) : DevStudioUiEvent
    data object ResetAllCircuits : DevStudioUiEvent
}

sealed interface DevStudioUiEffect : UiEffect {
    data class CopyToClipboard(val text: String, val label: String) : DevStudioUiEffect
    data class ShowToast(val message: String) : DevStudioUiEffect
}
