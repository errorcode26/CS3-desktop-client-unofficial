package com.lagradost.cloudstream3.desktop.ui.screens.dev

import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.common.logging.LogBuffer
import com.lagradost.common.logging.LogEntry
import com.lagradost.common.logging.LogLevel
import com.lagradost.common.logging.LogSubsystem
import com.lagradost.runtime.executor.PluginCircuitBreaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class DevStudioViewModel : BaseMviViewModel<DevStudioUiState, DevStudioUiEvent, DevStudioUiEffect>(
    initialState = DevStudioUiState(),
) {
    init {
        refreshLogs()
        listenToLiveLogs()
        listenToPluginHealth()
    }

    private fun listenToLiveLogs() {
        viewModelScope.launch {
            LogBuffer.logFlow
                .collect { _ ->
                    if (!uiState.value.isPaused) {
                        refreshLogs()
                    }
                }
        }
    }

    private fun listenToPluginHealth() {
        viewModelScope.launch {
            PluginCircuitBreaker.healthStatsFlow.collect { stats ->
                updateState { copy(pluginHealth = stats) }
            }
        }
    }

    private fun refreshLogs() {
        viewModelScope.launch(Dispatchers.Default) {
            val allSnapshot = LogBuffer.getSnapshot()
            val state = uiState.value

            val errorCount = allSnapshot.count { it.level == LogLevel.ERROR }
            val warnCount = allSnapshot.count { it.level == LogLevel.WARN }
            val infoCount = allSnapshot.count { it.level == LogLevel.INFO }
            val debugCount = allSnapshot.count { it.level == LogLevel.DEBUG }

            val plugins = (allSnapshot.mapNotNull { it.pluginName } + state.pluginHealth.keys).distinct().sorted()

            val filtered = LogBuffer.filter(
                snapshot = allSnapshot,
                minLevel = state.selectedLevel,
                subsystem = state.selectedSubsystem,
                pluginFilter = state.selectedPlugin,
                query = state.searchQuery,
            )

            updateState {
                copy(
                    logs = filtered,
                    totalCount = allSnapshot.size,
                    errorCount = errorCount,
                    warnCount = warnCount,
                    infoCount = infoCount,
                    debugCount = debugCount,
                    availablePlugins = plugins,
                )
            }
        }
    }

    override fun handleEvent(event: DevStudioUiEvent) {
        when (event) {
            is DevStudioUiEvent.SelectLevel -> {
                updateState { copy(selectedLevel = event.level) }
                refreshLogs()
            }
            is DevStudioUiEvent.SelectSubsystem -> {
                updateState { copy(selectedSubsystem = event.subsystem) }
                refreshLogs()
            }
            is DevStudioUiEvent.SelectPlugin -> {
                updateState { copy(selectedPlugin = event.pluginName) }
                refreshLogs()
            }
            is DevStudioUiEvent.UpdateSearchQuery -> {
                updateState { copy(searchQuery = event.query) }
                refreshLogs()
            }
            is DevStudioUiEvent.TogglePause -> {
                val newPaused = !uiState.value.isPaused
                updateState { copy(isPaused = newPaused) }
                if (!newPaused) {
                    refreshLogs()
                }
            }
            is DevStudioUiEvent.SelectEntry -> {
                updateState {
                    copy(
                        selectedEntry = event.entry,
                        isInspectorOpen = event.entry != null,
                    )
                }
            }
            is DevStudioUiEvent.CloseInspector -> {
                updateState {
                    copy(
                        selectedEntry = null,
                        isInspectorOpen = false,
                    )
                }
            }
            is DevStudioUiEvent.ClearLogs -> {
                LogBuffer.clear()
                refreshLogs()
                sendEffect(DevStudioUiEffect.ShowToast("Dev Studio logs cleared"))
            }
            is DevStudioUiEvent.CopyAiSnapshot -> {
                val targetId = event.entryId ?: uiState.value.selectedEntry?.id
                val snapshot = LogBuffer.buildAiDebugSnapshot(targetId, precedingCount = 25)
                sendEffect(DevStudioUiEffect.CopyToClipboard(snapshot, "AI Debug Snapshot"))
                sendEffect(DevStudioUiEffect.ShowToast("AI Debug Context copied to clipboard!"))
            }
            is DevStudioUiEvent.ExportLogs -> {
                val currentLogs = uiState.value.logs
                val exportText = LogBuffer.exportLogsAsText(currentLogs)
                sendEffect(DevStudioUiEffect.CopyToClipboard(exportText, "Full Log Export"))
                sendEffect(DevStudioUiEffect.ShowToast("${currentLogs.size} logs exported to clipboard!"))
            }
            is DevStudioUiEvent.ResetCircuit -> {
                PluginCircuitBreaker.resetProvider(event.providerName)
                sendEffect(DevStudioUiEffect.ShowToast("Reset circuit breaker for '${event.providerName}'"))
            }
            is DevStudioUiEvent.ResetAllCircuits -> {
                PluginCircuitBreaker.resetAll()
                sendEffect(DevStudioUiEffect.ShowToast("All plugin circuit breakers reset!"))
            }
        }
    }
}
