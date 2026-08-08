package com.lagradost.cloudstream3.desktop.ui.screens.dev

import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerDiagnosticsHolder
import com.lagradost.common.logging.LogBuffer
import com.lagradost.common.logging.LogLevel
import com.lagradost.common.net.NetworkTrafficBuffer
import com.lagradost.runtime.executor.PluginCircuitBreaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.regex.Pattern

class DevStudioViewModel : BaseMviViewModel<DevStudioUiState, DevStudioUiEvent, DevStudioUiEffect>(
    initialState = DevStudioUiState(),
) {
    private var pendingLogRefreshJob: Job? = null
    private var pendingNetworkRefreshJob: Job? = null
    private var playerPollJob: Job? = null

    init {
        refreshLogsDirect()
        refreshNetworkDirect()
        listenToLiveLogs()
        listenToNetworkTraffic()
        listenToPluginHealth()
        startPlayerDiagnosticsPolling()
    }

    private fun listenToLiveLogs() {
        viewModelScope.launch {
            LogBuffer.logFlow.collect { _ ->
                if (!uiState.value.isPaused) {
                    scheduleLogRefresh()
                }
            }
        }
    }

    private fun listenToNetworkTraffic() {
        viewModelScope.launch {
            NetworkTrafficBuffer.networkFlow.collect { _ ->
                if (!uiState.value.isNetworkPaused) {
                    scheduleNetworkRefresh()
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

    private fun startPlayerDiagnosticsPolling() {
        playerPollJob?.cancel()
        playerPollJob = viewModelScope.launch {
            while (isActive) {
                if (uiState.value.currentTab == DevStudioTab.PLAYER) {
                    val snapshot = PlayerDiagnosticsHolder.getSnapshot()
                    updateState { copy(playerDiagnostics = snapshot) }
                }
                delay(300)
            }
        }
    }

    private fun scheduleLogRefresh() {
        if (pendingLogRefreshJob?.isActive == true) return
        pendingLogRefreshJob = viewModelScope.launch(Dispatchers.Default) {
            delay(40) // 40ms debounce batching
            refreshLogsDirect()
        }
    }

    private fun scheduleNetworkRefresh() {
        if (pendingNetworkRefreshJob?.isActive == true) return
        pendingNetworkRefreshJob = viewModelScope.launch(Dispatchers.Default) {
            delay(40) // 40ms debounce batching
            refreshNetworkDirect()
        }
    }

    private fun refreshLogsDirect() {
        viewModelScope.launch(Dispatchers.Default) {
            val allSnapshot = LogBuffer.getSnapshot()
            val state = uiState.value

            val errorCount = allSnapshot.count { it.level == LogLevel.ERROR }
            val warnCount = allSnapshot.count { it.level == LogLevel.WARN }
            val infoCount = allSnapshot.count { it.level == LogLevel.INFO }
            val debugCount = allSnapshot.count { it.level == LogLevel.DEBUG }

            val plugins = (allSnapshot.mapNotNull { it.pluginName } + state.pluginHealth.keys).distinct().sorted()

            val query = state.searchQuery.trim()
            val isRegex = state.isRegexSearch
            val isCase = state.isCaseSensitiveSearch

            val regexPattern = if (isRegex && query.isNotEmpty()) {
                try {
                    val flags = if (isCase) 0 else Pattern.CASE_INSENSITIVE
                    Pattern.compile(query, flags)
                } catch (e: Throwable) {
                    null
                }
            } else {
                null
            }

            val filtered = allSnapshot.filter { entry ->
                if (state.exceptionsOnly && entry.throwable == null && entry.level != LogLevel.ERROR) return@filter false
                if (!entry.level.isAtLeast(state.selectedLevel)) return@filter false
                if (state.selectedSubsystem != com.lagradost.common.logging.LogSubsystem.ALL && entry.subsystem != state.selectedSubsystem) return@filter false
                if (!state.selectedPlugin.isNullOrBlank() && entry.pluginName != state.selectedPlugin) return@filter false

                if (query.isNotEmpty()) {
                    if (regexPattern != null) {
                        val matches = regexPattern.matcher(entry.tag).find() ||
                            regexPattern.matcher(entry.message).find() ||
                            regexPattern.matcher(entry.threadName).find() ||
                            (entry.throwable?.message?.let { regexPattern.matcher(it).find() } == true)
                        if (!matches) return@filter false
                    } else {
                        val q = if (isCase) query else query.lowercase()
                        val tag = if (isCase) entry.tag else entry.tag.lowercase()
                        val msg = if (isCase) entry.message else entry.message.lowercase()
                        val th = if (isCase) entry.threadName else entry.threadName.lowercase()
                        val err = entry.throwable?.message?.let { if (isCase) it else it.lowercase() }

                        val matches = tag.contains(q) || msg.contains(q) || th.contains(q) || (err?.contains(q) == true)
                        if (!matches) return@filter false
                    }
                }
                true
            }

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

    private fun refreshNetworkDirect() {
        viewModelScope.launch(Dispatchers.Default) {
            val allSnapshot = NetworkTrafficBuffer.getSnapshot()
            val state = uiState.value

            val errorCount = allSnapshot.count { it.isError }
            val filtered = NetworkTrafficBuffer.filter(
                snapshot = allSnapshot,
                query = state.networkSearchQuery,
                methodFilter = state.networkMethodFilter,
                errorsOnly = state.networkErrorsOnly,
            )

            updateState {
                copy(
                    networkRequests = filtered,
                    totalNetworkCount = allSnapshot.size,
                    networkErrorCount = errorCount,
                )
            }
        }
    }

    override fun handleEvent(event: DevStudioUiEvent) {
        when (event) {
            is DevStudioUiEvent.SwitchTab -> {
                updateState { copy(currentTab = event.tab) }
                if (event.tab == DevStudioTab.PLAYER) {
                    val snapshot = PlayerDiagnosticsHolder.getSnapshot()
                    updateState { copy(playerDiagnostics = snapshot) }
                }
            }

            // LogCat Handlers
            is DevStudioUiEvent.SelectLevel -> {
                updateState { copy(selectedLevel = event.level) }
                refreshLogsDirect()
            }
            is DevStudioUiEvent.SelectSubsystem -> {
                updateState { copy(selectedSubsystem = event.subsystem) }
                refreshLogsDirect()
            }
            is DevStudioUiEvent.SelectPlugin -> {
                updateState { copy(selectedPlugin = event.pluginName) }
                refreshLogsDirect()
            }
            is DevStudioUiEvent.UpdateSearchQuery -> {
                updateState { copy(searchQuery = event.query) }
                refreshLogsDirect()
            }
            is DevStudioUiEvent.ToggleExceptionsOnly -> {
                updateState { copy(exceptionsOnly = !exceptionsOnly) }
                refreshLogsDirect()
            }
            is DevStudioUiEvent.TogglePause -> {
                val newPaused = !uiState.value.isPaused
                updateState { copy(isPaused = newPaused) }
                if (!newPaused) {
                    refreshLogsDirect()
                }
            }
            is DevStudioUiEvent.ToggleAutoScroll -> {
                updateState { copy(autoScrollEnabled = !autoScrollEnabled) }
            }
            is DevStudioUiEvent.ToggleRegexSearch -> {
                updateState { copy(isRegexSearch = !isRegexSearch) }
                refreshLogsDirect()
            }
            is DevStudioUiEvent.ToggleCaseSensitiveSearch -> {
                updateState { copy(isCaseSensitiveSearch = !isCaseSensitiveSearch) }
                refreshLogsDirect()
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
                refreshLogsDirect()
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
            is DevStudioUiEvent.CopyLogLine -> {
                val line = "[${event.entry.formattedTime}] [${event.entry.level.shortLabel}] [${event.entry.tag}] ${event.entry.message}"
                sendEffect(DevStudioUiEffect.CopyToClipboard(line, "Log Line"))
                sendEffect(DevStudioUiEffect.ShowToast("Log line copied!"))
            }

            // Network Inspector Handlers
            is DevStudioUiEvent.SelectNetworkRequest -> {
                updateState {
                    copy(
                        selectedNetworkRequest = event.entry,
                        isNetworkInspectorOpen = event.entry != null,
                    )
                }
            }
            is DevStudioUiEvent.CloseNetworkInspector -> {
                updateState {
                    copy(
                        selectedNetworkRequest = null,
                        isNetworkInspectorOpen = false,
                    )
                }
            }
            is DevStudioUiEvent.UpdateNetworkSearchQuery -> {
                updateState { copy(networkSearchQuery = event.query) }
                refreshNetworkDirect()
            }
            is DevStudioUiEvent.SelectNetworkMethodFilter -> {
                updateState { copy(networkMethodFilter = event.method) }
                refreshNetworkDirect()
            }
            is DevStudioUiEvent.ToggleNetworkErrorsOnly -> {
                updateState { copy(networkErrorsOnly = !networkErrorsOnly) }
                refreshNetworkDirect()
            }
            is DevStudioUiEvent.ToggleNetworkPause -> {
                val newPaused = !uiState.value.isNetworkPaused
                updateState { copy(isNetworkPaused = newPaused) }
                if (!newPaused) {
                    refreshNetworkDirect()
                }
            }
            is DevStudioUiEvent.ClearNetworkLogs -> {
                NetworkTrafficBuffer.clear()
                refreshNetworkDirect()
                sendEffect(DevStudioUiEffect.ShowToast("Network traffic cleared"))
            }
            is DevStudioUiEvent.ExportNetworkLogs -> {
                val currentReqs = uiState.value.networkRequests
                val exportText = NetworkTrafficBuffer.exportAsText(currentReqs)
                sendEffect(DevStudioUiEffect.CopyToClipboard(exportText, "Network Traffic Export"))
                sendEffect(DevStudioUiEffect.ShowToast("${currentReqs.size} requests exported to clipboard!"))
            }
            is DevStudioUiEvent.CopyCurlCommand -> {
                val curl = event.entry.toCurlCommand()
                sendEffect(DevStudioUiEffect.CopyToClipboard(curl, "cURL Command"))
                sendEffect(DevStudioUiEffect.ShowToast("cURL command copied!"))
            }
            is DevStudioUiEvent.CopyTextPayload -> {
                sendEffect(DevStudioUiEffect.CopyToClipboard(event.text, event.label))
                sendEffect(DevStudioUiEffect.ShowToast("${event.label} copied to clipboard!"))
            }

            // Player Diagnostics Handlers
            is DevStudioUiEvent.RefreshPlayerDiagnostics -> {
                val snapshot = PlayerDiagnosticsHolder.getSnapshot()
                updateState { copy(playerDiagnostics = snapshot) }
            }

            // Provider Health Handlers
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
