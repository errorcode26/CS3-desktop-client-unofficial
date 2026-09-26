package com.lagradost.cloudstream3.desktop.ui.screens.settings.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import okhttp3.Cookie

enum class StorageCacheType {
    IMAGE,
    NETWORK,
    LOGS,
    STREAM,
}

data class StorageMetrics(
    val imageCacheBytes: Long? = null,
    val networkCacheBytes: Long? = null,
    val databaseBytes: Long? = null,
    val logsBytes: Long? = null,
    val streamCacheBytes: Long? = null,
) {
    val totalBytes: Long
        get() = (imageCacheBytes ?: 0L) + (networkCacheBytes ?: 0L) + (databaseBytes ?: 0L) + (logsBytes ?: 0L) + (streamCacheBytes ?: 0L)
}

data class TorrServerEngineState(
    val isInstalled: Boolean = false,
    val fileSizeMB: Float = 0f,
    val isCheckingUpdates: Boolean = false,
    val updateFeedback: String? = null,
)

data class UpdateCheckState(
    val isChecking: Boolean = false,
    val showCheckedFeedback: Boolean = false,
)

data class ClearanceState(
    val cookiesMap: Map<String, List<Cookie>> = emptyMap(),
    val isLaunchingManualBypass: Boolean = false,
)

data class ProviderTestReportState(
    val isRunning: Boolean = false,
    val results: Map<String, com.lagradost.cloudstream3.utils.TestingUtils.TestResultProvider> = emptyMap(),
    val passed: Int = 0,
    val failed: Int = 0,
    val total: Int = 0,
)

data class DiagnosticsState(
    val isNetworkTesting: Boolean = false,
    val isMetaTesting: Boolean = false,
    val results: List<com.lagradost.cloudstream3.desktop.network.DiagnosticResult> = emptyList(),
    val currentTest: String = "",
    val lastRunTime: String = "",
)

sealed class SettingsUiEvent : UiEvent {
    data class OnUpdateString(val key: String, val value: String) : SettingsUiEvent()
    data class OnUpdateBoolean(val key: String, val value: Boolean) : SettingsUiEvent()
    data class OnUpdateInt(val key: String, val value: Int) : SettingsUiEvent()
    data class OnUpdateFloat(val key: String, val value: Float) : SettingsUiEvent()
    data object RefreshStorageMetrics : SettingsUiEvent()
    data class ClearCache(val cacheType: StorageCacheType) : SettingsUiEvent()
    data object VacuumDatabase : SettingsUiEvent()
    data object ResetSettingsToDefault : SettingsUiEvent()
    data object FactoryReset : SettingsUiEvent()

    data class CheckUpdates(val force: Boolean = true) : SettingsUiEvent()
    data object RefreshTorrServerStatus : SettingsUiEvent()
    data object CheckTorrServerUpdates : SettingsUiEvent()
    data object DeleteTorrServerBinary : SettingsUiEvent()
    data object RefreshClearanceCookies : SettingsUiEvent()
    data class ClearDomainCookies(val domain: String) : SettingsUiEvent()
    data object ClearAllClearanceCookies : SettingsUiEvent()
    data class LaunchManualClearance(val url: String) : SettingsUiEvent()

    data class UnlockDeveloperMode(val password: String) : SettingsUiEvent()
    data class SetDeveloperMode(val enabled: Boolean) : SettingsUiEvent()
    data object StartProviderTests : SettingsUiEvent()
    data object CancelProviderTests : SettingsUiEvent()
    data object RunNetworkDiagnostics : SettingsUiEvent()
    data object RunMetaDiagnostics : SettingsUiEvent()
    data class UpdateDownloadPath(val path: String) : SettingsUiEvent()
    data class UpdateScreenshotPath(val path: String) : SettingsUiEvent()

    // Stream Priorities
    data class SetQualityPriority(val quality: Int, val priority: Int) : SettingsUiEvent()
    data object ResetQualityDefaults : SettingsUiEvent()
    data object SetQualityPreset4K : SettingsUiEvent()
    data object SetQualityPreset1080p : SettingsUiEvent()
    data class SetAudioLanguagePreset(val preset: List<String>) : SettingsUiEvent()
    data object ResetAudioDefaults : SettingsUiEvent()
    data class MoveAudioLanguage(val fromIndex: Int, val toIndex: Int) : SettingsUiEvent()
    data class AddAudioLanguage(val code: String) : SettingsUiEvent()
    data class RemoveAudioLanguage(val code: String) : SettingsUiEvent()
    data class SetSubtitleLanguagePreset(val preset: List<String>) : SettingsUiEvent()
    data object ResetSubtitleDefaults : SettingsUiEvent()
    data class MoveSubtitleLanguage(val fromIndex: Int, val toIndex: Int) : SettingsUiEvent()
    data class AddSubtitleLanguage(val code: String) : SettingsUiEvent()
    data class RemoveSubtitleLanguage(val code: String) : SettingsUiEvent()

    // Cloned Sites
    data class AddClonedSite(val site: com.lagradost.cloudstream3.desktop.models.CustomSite) : SettingsUiEvent()
    data class RemoveClonedSite(val site: com.lagradost.cloudstream3.desktop.models.CustomSite) : SettingsUiEvent()
}

data class SettingsUiState(
    val stringSettings: Map<String, String> = emptyMap(),
    val booleanSettings: Map<String, Boolean> = emptyMap(),
    val intSettings: Map<String, Int> = emptyMap(),
    val floatSettings: Map<String, Float> = emptyMap(),
    val storageMetrics: StorageMetrics = StorageMetrics(),
    val isOptimizingDb: Boolean = false,
    val isRefreshingStorage: Boolean = false,
    val engineState: TorrServerEngineState = TorrServerEngineState(),
    val updateCheckState: UpdateCheckState = UpdateCheckState(),
    val clearanceState: ClearanceState = ClearanceState(),
    val isDevModeEnabled: Boolean = false,
    val devModeError: String? = null,
    val providerTestState: ProviderTestReportState = ProviderTestReportState(),
    val diagnosticsState: DiagnosticsState = DiagnosticsState(),
    val downloadPath: String = "",
    val screenshotPath: String = "",
) : UiState

sealed class SettingsUiEffect : UiEffect {
    data class ShowToast(val message: String, val isError: Boolean = false) : SettingsUiEffect()
}
