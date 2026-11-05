package com.lagradost.cloudstream3.desktop.ui.screens.settings.contract

import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState
import com.lagradost.cloudstream3.desktop.network.DohProvider
import okhttp3.Cookie

enum class StorageCacheType {
    IMAGE,
    NETWORK,
    LOGS,
    STREAM,
    UPDATES,
}

data class StorageMetrics(
    val imageCacheBytes: Long? = null,
    val networkCacheBytes: Long? = null,
    val databaseBytes: Long? = null,
    val logsBytes: Long? = null,
    val streamCacheBytes: Long? = null,
    val updatesBytes: Long? = null,
) {
    val totalBytes: Long
        get() = (imageCacheBytes ?: 0L) + (networkCacheBytes ?: 0L) + (databaseBytes ?: 0L) + (logsBytes ?: 0L) + (streamCacheBytes ?: 0L) + (updatesBytes ?: 0L)
}

data class TorboxAccountState(
    val user: com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxUserData? = null,
    val isValidatingKey: Boolean = false,
    val isStartingAuth: Boolean = false,
    val activeAuth: com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxDeviceAuthData? = null,
    val feedbackMessage: String? = null,
    val feedbackIsError: Boolean = false,
)

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

enum class ProviderTestFilter {
    ALL,
    PASSED,
    FAILED,
}

data class ProviderTestReportState(
    val currentlyTestingProvider: String? = null,
    val filter: ProviderTestFilter = ProviderTestFilter.ALL,
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

data class DnsTestResult(
    val isSuccess: Boolean,
    val latencyMs: Long = 0L,
    val serverDisplay: String = "",
    val resolvedIp: String? = null,
    val errorMessage: String? = null,
)

data class DnsTestState(
    val isTesting: Boolean = false,
    val result: DnsTestResult? = null,
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
    data class StartSingleProviderTest(val apiName: String) : SettingsUiEvent()
    data class SetProviderTestFilter(val filter: ProviderTestFilter) : SettingsUiEvent()
    data object CancelProviderTests : SettingsUiEvent()
    data object RunNetworkDiagnostics : SettingsUiEvent()
    data object RunMetaDiagnostics : SettingsUiEvent()
    data class TestDns(val provider: DohProvider? = null, val customEndpoint: String? = null) : SettingsUiEvent()
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

    // Torbox Debrid
    data class ValidateTorboxKey(val apiKey: String) : SettingsUiEvent()
    data object StartTorboxAuth : SettingsUiEvent()
    data class CompleteTorboxAuth(val token: String) : SettingsUiEvent()
    data class SetTorboxAuthError(val error: String) : SettingsUiEvent()
    data object CancelTorboxAuth : SettingsUiEvent()
    data object DisconnectTorbox : SettingsUiEvent()

    // Ignored CF Domains
    data object LoadIgnoredCfDomains : SettingsUiEvent()
    data class RemoveIgnoredCfDomain(val domain: String) : SettingsUiEvent()
    data object ClearAllIgnoredCfDomains : SettingsUiEvent()
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
    val isDevModeEnabled: Boolean = true,
    val devModeError: String? = null,
    val providerTestState: ProviderTestReportState = ProviderTestReportState(),
    val diagnosticsState: DiagnosticsState = DiagnosticsState(),
    val downloadPath: String = "",
    val screenshotPath: String = "",
    val dnsTestState: DnsTestState = DnsTestState(),
    val torboxAccountState: TorboxAccountState = TorboxAccountState(),
    val ignoredCfDomains: List<String> = emptyList(),
    val clonedSites: List<com.lagradost.cloudstream3.desktop.models.CustomSite> = emptyList(),
) : UiState

sealed class SettingsUiEffect : UiEffect {
    data class ShowToast(val message: String, val isError: Boolean = false) : SettingsUiEffect()
}
