package com.lagradost.cloudstream3.desktop.ui.screens.dev

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.ui.screens.player.LivePlayerDiagnostics
import com.lagradost.common.logging.LogEntry
import com.lagradost.common.logging.LogLevel
import com.lagradost.common.logging.LogSubsystem
import com.lagradost.common.net.NetworkRequestEntry
import com.lagradost.runtime.executor.PluginHealthStats

enum class DevStudioTab(val title: String) {
    LOGS("Live LogCat"),
    NETWORK("Network Inspector"),
    PLAYER("Player & Stream"),
    PROVIDERS("Circuits & Health"),
}

data class DevStudioUiState(
    val currentTab: DevStudioTab = DevStudioTab.LOGS,

    // LogCat State
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
    val exceptionsOnly: Boolean = false,
    val searchQuery: String = "",
    val isPaused: Boolean = false,
    val autoScrollEnabled: Boolean = true,
    val isRegexSearch: Boolean = false,
    val isCaseSensitiveSearch: Boolean = false,
    val selectedEntry: LogEntry? = null,
    val isInspectorOpen: Boolean = false,

    // Network Inspector State
    val networkRequests: List<NetworkRequestEntry> = emptyList(),
    val totalNetworkCount: Int = 0,
    val networkErrorCount: Int = 0,
    val selectedNetworkRequest: NetworkRequestEntry? = null,
    val isNetworkInspectorOpen: Boolean = false,
    val networkSearchQuery: String = "",
    val networkMethodFilter: String? = null,
    val networkErrorsOnly: Boolean = false,
    val isNetworkPaused: Boolean = false,

    // Live Player Diagnostics State
    val playerDiagnostics: LivePlayerDiagnostics = LivePlayerDiagnostics(),

    // Provider Health & Circuit Breakers State
    val pluginHealth: Map<String, PluginHealthStats> = emptyMap(),
) : UiState

sealed interface DevStudioUiEvent : UiEvent {
    data class SwitchTab(val tab: DevStudioTab) : DevStudioUiEvent

    // LogCat Events
    data class SelectLevel(val level: LogLevel) : DevStudioUiEvent
    data class SelectSubsystem(val subsystem: LogSubsystem) : DevStudioUiEvent
    data class SelectPlugin(val pluginName: String?) : DevStudioUiEvent
    data class UpdateSearchQuery(val query: String) : DevStudioUiEvent
    data object ToggleExceptionsOnly : DevStudioUiEvent
    data object TogglePause : DevStudioUiEvent
    data object ToggleAutoScroll : DevStudioUiEvent
    data object ToggleRegexSearch : DevStudioUiEvent
    data object ToggleCaseSensitiveSearch : DevStudioUiEvent
    data class SelectEntry(val entry: LogEntry?) : DevStudioUiEvent
    data object ClearLogs : DevStudioUiEvent
    data class CopyAiSnapshot(val entryId: Long? = null) : DevStudioUiEvent
    data object ExportLogs : DevStudioUiEvent
    data object CloseInspector : DevStudioUiEvent
    data class CopyLogLine(val entry: LogEntry) : DevStudioUiEvent

    // Network Inspector Events
    data class SelectNetworkRequest(val entry: NetworkRequestEntry?) : DevStudioUiEvent
    data object CloseNetworkInspector : DevStudioUiEvent
    data class UpdateNetworkSearchQuery(val query: String) : DevStudioUiEvent
    data class SelectNetworkMethodFilter(val method: String?) : DevStudioUiEvent
    data object ToggleNetworkErrorsOnly : DevStudioUiEvent
    data object ToggleNetworkPause : DevStudioUiEvent
    data object ClearNetworkLogs : DevStudioUiEvent
    data object ExportNetworkLogs : DevStudioUiEvent
    data class CopyCurlCommand(val entry: NetworkRequestEntry) : DevStudioUiEvent
    data class CopyTextPayload(val text: String, val label: String) : DevStudioUiEvent

    // Player Diagnostics Events
    data object RefreshPlayerDiagnostics : DevStudioUiEvent

    // Circuit Breaker Events
    data class ResetCircuit(val providerName: String) : DevStudioUiEvent
    data object ResetAllCircuits : DevStudioUiEvent
}

sealed interface DevStudioUiEffect : UiEffect {
    data class CopyToClipboard(val text: String, val label: String) : DevStudioUiEffect
    data class ShowToast(val message: String) : DevStudioUiEffect
}
