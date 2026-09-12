package com.lagradost.cloudstream3.desktop.ui.screens.settings

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.download.TaskStatus
import com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager
import com.lagradost.cloudstream3.desktop.models.CustomSite
import com.lagradost.cloudstream3.desktop.network.DiagnosticsRunner
import com.lagradost.cloudstream3.desktop.network.NetworkConfig
import com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass
import com.lagradost.cloudstream3.desktop.player.LanguagePriorityHelper
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.*
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import com.lagradost.cloudstream3.desktop.utils.DeveloperModeManager
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.TestingUtils
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel : BaseMviViewModel<SettingsUiState, SettingsUiEvent, SettingsUiEffect>(
    initialState = SettingsUiState(),
) {
    private val imageCacheDir = File(PlatformPaths.appDataDir, "image_cache")
    private val networkCacheDir = PlatformPaths.cacheDir
    private val dbFile = File(PlatformPaths.dataDir, "cloudstream.db")
    private val logsDir = PlatformPaths.logsDir

    private var providerTestJob: Job? = null
    private var networkDiagJob: Job? = null
    private var metaDiagJob: Job? = null

    init {
        updateState {
            copy(
                isDevModeEnabled = DeveloperModeManager.isEnabled,
                downloadPath = DesktopDownloadManager.downloadsDir.absolutePath,
                screenshotPath = PlatformPaths.screenshotsDir.absolutePath,
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val initialDownloadPath = DesktopDataStore.getKey<String>(DesktopDataStore.PREF_DOWNLOAD_PATH)
                ?: DesktopDownloadManager.downloadsDir.absolutePath
            val initialScreenshotPath = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SCREENSHOT_DIR)
                ?: PlatformPaths.screenshotsDir.absolutePath
            val cfEnabled = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_ALLOW_CF_BYPASS) ?: false
            val p2pEnabled = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_P2P_ENABLED) ?: false
            val audioNorm = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUDIO_NORMALIZATION) ?: false
            val audioDelay = DesktopDataStore.getKey<Float>(PlayerConfig.PREF_AUDIO_DELAY) ?: 0f
            val downloadThreads = DesktopDataStore.getKey<Float>(DesktopDataStore.PREF_DOWNLOAD_THREADS) ?: 8f
            val maxConcurrent = DesktopDataStore.getKey<Float>(DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT) ?: 2f
            val subBold = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BOLD) ?: "no"
            val subItalic = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_ITALIC) ?: "no"
            updateState {
                copy(
                    downloadPath = initialDownloadPath,
                    screenshotPath = initialScreenshotPath,
                    booleanSettings = booleanSettings + mapOf(
                        DesktopDataStore.PREF_ALLOW_CF_BYPASS to cfEnabled,
                        DesktopDataStore.PREF_P2P_ENABLED to p2pEnabled,
                        PlayerConfig.PREF_AUDIO_NORMALIZATION to audioNorm,
                    ),
                    floatSettings = floatSettings + mapOf(
                        PlayerConfig.PREF_AUDIO_DELAY to audioDelay,
                        DesktopDataStore.PREF_DOWNLOAD_THREADS to downloadThreads,
                        DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT to maxConcurrent,
                    ),
                    stringSettings = stringSettings + mapOf(
                        PlayerConfig.PREF_SUB_BOLD to subBold,
                        PlayerConfig.PREF_SUB_ITALIC to subItalic,
                    ),
                )
            }
        }
        handleEvent(SettingsUiEvent.RefreshStorageMetrics)
        handleEvent(SettingsUiEvent.RefreshTorrServerStatus)
        handleEvent(SettingsUiEvent.RefreshClearanceCookies)
        observeTorrServerDownloadTask()
    }

    override fun handleEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OnUpdateString -> {
                updateState { copy(stringSettings = stringSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)
                }
            }
            is SettingsUiEvent.OnUpdateBoolean -> {
                updateState { copy(booleanSettings = booleanSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)

                    if (event.key.startsWith("DISCORD_RPC")) {
                        try {
                            DiscordRpcManager.onSettingsChanged()
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("Discord RPC Error", e)
                        }
                    }
                }
            }
            is SettingsUiEvent.OnUpdateInt -> {
                updateState { copy(intSettings = intSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)

                    if (event.key == NetworkConfig.PREF_DOH_PROVIDER) {
                        try {
                            NetworkConfig.updateGlobalNetworkClients()
                            // Status message could be handled via UiEffect if needed
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("Network Reload Error", e)
                        }
                    }
                }
            }
            is SettingsUiEvent.OnUpdateFloat -> {
                updateState { copy(floatSettings = floatSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)
                }
            }
            is SettingsUiEvent.RefreshStorageMetrics -> {
                refreshStorageMetrics()
            }
            is SettingsUiEvent.ClearCache -> {
                clearCache(event.cacheType)
            }
            is SettingsUiEvent.VacuumDatabase -> {
                vacuumDatabase()
            }
            is SettingsUiEvent.ResetSettingsToDefault -> {
                resetSettingsToDefault()
            }
            is SettingsUiEvent.FactoryReset -> {
                factoryReset()
            }
            is SettingsUiEvent.CheckUpdates -> {
                checkUpdates(event.force)
            }
            is SettingsUiEvent.RefreshTorrServerStatus -> {
                refreshTorrServerStatus()
            }
            is SettingsUiEvent.CheckTorrServerUpdates -> {
                checkTorrServerUpdates()
            }
            is SettingsUiEvent.DeleteTorrServerBinary -> {
                deleteTorrServerBinary()
            }
            is SettingsUiEvent.RefreshClearanceCookies -> {
                refreshClearanceCookies()
            }
            is SettingsUiEvent.ClearDomainCookies -> {
                clearDomainCookies(event.domain)
            }
            is SettingsUiEvent.ClearAllClearanceCookies -> {
                clearAllClearanceCookies()
            }
            is SettingsUiEvent.LaunchManualClearance -> {
                launchManualClearance(event.url)
            }
            is SettingsUiEvent.UnlockDeveloperMode -> {
                unlockDeveloperMode(event.password)
            }
            is SettingsUiEvent.SetDeveloperMode -> {
                setDeveloperMode(event.enabled)
            }
            is SettingsUiEvent.StartProviderTests -> {
                startProviderTests()
            }
            is SettingsUiEvent.CancelProviderTests -> {
                cancelProviderTests()
            }
            is SettingsUiEvent.RunNetworkDiagnostics -> {
                runNetworkDiagnostics()
            }
            is SettingsUiEvent.RunMetaDiagnostics -> {
                runMetaDiagnostics()
            }
            is SettingsUiEvent.UpdateDownloadPath -> {
                updateDownloadPath(event.path)
            }
            is SettingsUiEvent.UpdateScreenshotPath -> {
                updateScreenshotPath(event.path)
            }
            is SettingsUiEvent.SetQualityPriority -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        QualityDataHelper.setQualityPriority(event.quality, event.priority)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to set quality priority", e)
                    }
                }
            }
            is SettingsUiEvent.ResetQualityDefaults -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        QualityDataHelper.resetToDefaults()
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to reset quality defaults", e)
                    }
                }
            }
            is SettingsUiEvent.SetQualityPreset4K -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        QualityDataHelper.setQualityPriority(com.lagradost.cloudstream3.utils.Qualities.P2160.value, 10)
                        QualityDataHelper.setQualityPriority(com.lagradost.cloudstream3.utils.Qualities.P1080.value, 8)
                        QualityDataHelper.setQualityPriority(com.lagradost.cloudstream3.utils.Qualities.P720.value, 5)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to set 4K preset", e)
                    }
                }
            }
            is SettingsUiEvent.SetQualityPreset1080p -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        QualityDataHelper.setQualityPriority(com.lagradost.cloudstream3.utils.Qualities.P1080.value, 10)
                        QualityDataHelper.setQualityPriority(com.lagradost.cloudstream3.utils.Qualities.P720.value, 8)
                        QualityDataHelper.setQualityPriority(com.lagradost.cloudstream3.utils.Qualities.P2160.value, 4)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to set 1080p preset", e)
                    }
                }
            }
            is SettingsUiEvent.SetAudioLanguagePreset -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.setAudioPreset(event.preset)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to set audio preset", e)
                    }
                }
            }
            is SettingsUiEvent.ResetAudioDefaults -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.resetAudioDefaults()
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to reset audio defaults", e)
                    }
                }
            }
            is SettingsUiEvent.MoveAudioLanguage -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.moveAudio(event.fromIndex, event.toIndex)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to move audio language", e)
                    }
                }
            }
            is SettingsUiEvent.AddAudioLanguage -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.addAudioToStack(event.code)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to add audio language", e)
                    }
                }
            }
            is SettingsUiEvent.RemoveAudioLanguage -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.removeAudioFromStack(event.code)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to remove audio language", e)
                    }
                }
            }
            is SettingsUiEvent.SetSubtitleLanguagePreset -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.setSubtitlePreset(event.preset)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to set subtitle preset", e)
                    }
                }
            }
            is SettingsUiEvent.ResetSubtitleDefaults -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.resetSubtitleDefaults()
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to reset subtitle defaults", e)
                    }
                }
            }
            is SettingsUiEvent.MoveSubtitleLanguage -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.moveSubtitle(event.fromIndex, event.toIndex)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to move subtitle language", e)
                    }
                }
            }
            is SettingsUiEvent.AddSubtitleLanguage -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.addSubtitleToStack(event.code)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to add subtitle language", e)
                    }
                }
            }
            is SettingsUiEvent.RemoveSubtitleLanguage -> {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        LanguagePriorityHelper.removeSubtitleFromStack(event.code)
                    } catch (e: Exception) {
                        AppLogger.e("SettingsViewModel", "Failed to remove subtitle language", e)
                    }
                }
            }
            is SettingsUiEvent.AddClonedSite -> {
                addClonedSite(event.site)
            }
            is SettingsUiEvent.RemoveClonedSite -> {
                removeClonedSite(event.site)
            }
        }
    }

    private fun getDirectorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } catch (_: Exception) {
            0L
        }
    }

    private fun refreshStorageMetrics() {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isRefreshingStorage = true) }
            val img = getDirectorySizeBytes(imageCacheDir)
            val net = getDirectorySizeBytes(networkCacheDir)
            val db = if (dbFile.exists()) dbFile.length() else 0L
            val logs = getDirectorySizeBytes(logsDir)
            updateState {
                copy(
                    storageMetrics = StorageMetrics(
                        imageCacheBytes = img,
                        networkCacheBytes = net,
                        databaseBytes = db,
                        logsBytes = logs,
                    ),
                    isRefreshingStorage = false,
                )
            }
        }
    }

    private fun clearCache(type: StorageCacheType) {
        viewModelScope.launch(Dispatchers.IO) {
            val (dir, label) = when (type) {
                StorageCacheType.IMAGE -> imageCacheDir to "Image cache"
                StorageCacheType.NETWORK -> networkCacheDir to "Network cache"
                StorageCacheType.LOGS -> logsDir to "Logs"
            }
            try {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { it.deleteRecursively() }
                }
                sendEffect(SettingsUiEffect.ShowToast("$label cleared"))
            } catch (e: Exception) {
                sendEffect(SettingsUiEffect.ShowToast("Failed to clear $label: ${e.message}", isError = true))
            }
            refreshStorageMetrics()
        }
    }

    private fun vacuumDatabase() {
        if (uiState.value.isOptimizingDb) return
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(isOptimizingDb = true) }
            try {
                if (dbFile.exists()) {
                    java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            stmt.execute("VACUUM;")
                        }
                    }
                }
                sendEffect(SettingsUiEffect.ShowToast("Database defragmented and compacted"))
            } catch (e: Exception) {
                sendEffect(SettingsUiEffect.ShowToast("Optimization failed: ${e.message}", isError = true))
            } finally {
                updateState { copy(isOptimizingDb = false) }
                refreshStorageMetrics()
            }
        }
    }

    private fun resetSettingsToDefault() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (dbFile.exists()) {
                    java.sql.DriverManager.getConnection("jdbc:sqlite:${dbFile.absolutePath}").use { conn ->
                        conn.createStatement().use { stmt ->
                            stmt.execute("DELETE FROM KeyValueStore;")
                        }
                    }
                }
                DesktopDataStore.rawKeyCache.clear()
                com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.reloadFromDataStore()
                updateState {
                    copy(
                        stringSettings = emptyMap(),
                        booleanSettings = emptyMap(),
                        intSettings = emptyMap(),
                        floatSettings = emptyMap(),
                    )
                }
                sendEffect(SettingsUiEffect.ShowToast("Settings restored to defaults"))
            } catch (e: Exception) {
                sendEffect(SettingsUiEffect.ShowToast("Failed to reset settings: ${e.message}", isError = true))
            }
            refreshStorageMetrics()
        }
    }

    private fun factoryReset() {
        viewModelScope.launch(Dispatchers.IO) {
            val target = PlatformPaths.appDataDir
            try {
                com.lagradost.runtime.loader.ExtensionLoader.unloadAllPlugins()
            } catch (_: Throwable) {}

            if (target.exists()) {
                try {
                    target.deleteRecursively()
                } catch (_: Throwable) {}
            }

            try {
                val isWindows = PlatformPaths.currentOS == PlatformPaths.OS.WINDOWS
                if (isWindows) {
                    ProcessBuilder(
                        "cmd.exe", "/c", "timeout /t 1 /nobreak > nul & rmdir /s /q \"${target.absolutePath}\""
                    ).start()
                } else {
                    ProcessBuilder(
                        "sh", "-c", "sleep 1 && rm -rf \"${target.absolutePath}\""
                    ).start()
                }
            } catch (_: Throwable) {}

            kotlin.system.exitProcess(0)
        }
    }

    private fun checkUpdates(force: Boolean) {
        if (uiState.value.updateCheckState.isChecking) return
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(updateCheckState = updateCheckState.copy(isChecking = true, showCheckedFeedback = false)) }
            try {
                UnifiedUpdateManager.checkAllUpdates(force = force)
                val hasNoUpdates = UnifiedUpdateManager.availableUpdates.value.isEmpty()
                updateState {
                    copy(
                        updateCheckState = updateCheckState.copy(
                            isChecking = false,
                            showCheckedFeedback = hasNoUpdates,
                        )
                    )
                }
            } catch (e: Exception) {
                updateState { copy(updateCheckState = updateCheckState.copy(isChecking = false)) }
                sendEffect(SettingsUiEffect.ShowToast("Update check failed: ${e.message}", isError = true))
            }
        }
    }

    private fun refreshTorrServerStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val installed = DesktopTorrentEngine.binary.isInstalled()
            val size = DesktopTorrentEngine.binary.getFileSizeMB()
            updateState {
                copy(
                    engineState = engineState.copy(
                        isInstalled = installed,
                        fileSizeMB = size,
                    )
                )
            }
        }
    }

    private fun checkTorrServerUpdates() {
        if (uiState.value.engineState.isCheckingUpdates) return
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(engineState = engineState.copy(isCheckingUpdates = true, updateFeedback = null)) }
            try {
                val update = UnifiedUpdateManager.checkTorrServerUpdate(force = true)
                if (update != null) {
                    UnifiedUpdateManager.showDialogForUpdate(update)
                    updateState { copy(engineState = engineState.copy(isCheckingUpdates = false)) }
                } else {
                    val version = UnifiedUpdateManager.getTorrServerInstalledVersion()
                    updateState {
                        copy(
                            engineState = engineState.copy(
                                isCheckingUpdates = false,
                                updateFeedback = "✓ TorrServer is up to date ($version)",
                            )
                        )
                    }
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        engineState = engineState.copy(
                            isCheckingUpdates = false,
                            updateFeedback = "Failed to check update: ${e.message}",
                        )
                    )
                }
            }
        }
    }

    private fun deleteTorrServerBinary() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DesktopTorrentEngine.binary.deleteBinary()
                refreshTorrServerStatus()
                sendEffect(SettingsUiEffect.ShowToast("Torrent engine uninstalled"))
            } catch (e: Exception) {
                sendEffect(SettingsUiEffect.ShowToast("Failed to delete binary: ${e.message}", isError = true))
            }
        }
    }

    private fun refreshClearanceCookies() {
        viewModelScope.launch(Dispatchers.IO) {
            val cookies = CloudflareKiller.getAllStoredCookies()
            updateState { copy(clearanceState = clearanceState.copy(cookiesMap = cookies)) }
        }
    }

    private fun clearDomainCookies(domain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            CloudflareKiller.clearClearanceForDomain(domain)
            refreshClearanceCookies()
            sendEffect(SettingsUiEffect.ShowToast("Cookies cleared for $domain"))
        }
    }

    private fun clearAllClearanceCookies() {
        viewModelScope.launch(Dispatchers.IO) {
            CloudflareKiller.clearAllClearance()
            refreshClearanceCookies()
            sendEffect(SettingsUiEffect.ShowToast("All cookies and clearance tokens cleared"))
        }
    }

    private fun launchManualClearance(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            updateState { copy(clearanceState = clearanceState.copy(isLaunchingManualBypass = true)) }
            sendEffect(SettingsUiEffect.ShowToast("Opening browser to solve clearance..."))
            val success = SystemBrowserCdpBypass.launchManualClearance(url)
            updateState { copy(clearanceState = clearanceState.copy(isLaunchingManualBypass = false)) }
            if (success) {
                sendEffect(SettingsUiEffect.ShowToast("Clearance acquired and saved!"))
                refreshClearanceCookies()
            } else {
                sendEffect(SettingsUiEffect.ShowToast("Manual clearance window closed without clearance.", isError = true))
            }
        }
    }

    private fun observeTorrServerDownloadTask() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                AppDownloadManager.tasks.collect { tasks ->
                    val torrTask = tasks.firstOrNull { it.id == "torrserver" }
                    if (torrTask?.status == TaskStatus.COMPLETED || torrTask == null) {
                        refreshTorrServerStatus()
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Failed to observe TorrServer download task", e)
            }
        }
    }

    private fun unlockDeveloperMode(password: String) {
        if (password.trim().equals("banana", ignoreCase = true)) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    DeveloperModeManager.setEnabled(true)
                    updateState { copy(isDevModeEnabled = true, devModeError = null) }
                } catch (e: Exception) {
                    AppLogger.e("SettingsViewModel", "Failed to enable developer mode", e)
                }
            }
        } else {
            updateState { copy(devModeError = "Incorrect password. Try again.") }
        }
    }

    private fun setDeveloperMode(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DeveloperModeManager.setEnabled(enabled)
                updateState { copy(isDevModeEnabled = enabled, devModeError = null) }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Failed to set developer mode", e)
            }
        }
    }

    private fun startProviderTests() {
        cancelProviderTests()
        val providers = APIHolder.allProviders.distinctBy { it::class.java.simpleName }.sortedBy { it.name }
        updateState {
            copy(
                providerTestState = ProviderTestReportState(
                    isRunning = true,
                    results = emptyMap(),
                    passed = 0,
                    failed = 0,
                    total = providers.size,
                )
            )
        }
        providerTestJob = viewModelScope.launch(Dispatchers.IO) {
            TestingUtils.getDeferredProviderTests(this, providers.toTypedArray()) { api, result ->
                updateState {
                    val newResults = providerTestState.results + (api.name to result)
                    val passed = if (result.success) providerTestState.passed + 1 else providerTestState.passed
                    val failed = if (!result.success) providerTestState.failed + 1 else providerTestState.failed
                    val isRunning = newResults.size < providers.size
                    copy(
                        providerTestState = providerTestState.copy(
                            results = newResults,
                            passed = passed,
                            failed = failed,
                            isRunning = isRunning,
                        )
                    )
                }
            }
        }
    }

    private fun cancelProviderTests() {
        providerTestJob?.cancel()
        providerTestJob = null
        updateState { copy(providerTestState = providerTestState.copy(isRunning = false)) }
    }

    private fun runNetworkDiagnostics() {
        networkDiagJob?.cancel()
        updateState {
            copy(
                diagnosticsState = diagnosticsState.copy(
                    isNetworkTesting = true,
                    results = emptyList(),
                    currentTest = "Starting...",
                )
            )
        }
        networkDiagJob = viewModelScope.launch(Dispatchers.IO) {
            DiagnosticsRunner.runAll { result ->
                updateState {
                    copy(
                        diagnosticsState = diagnosticsState.copy(
                            results = diagnosticsState.results + result,
                            currentTest = result.name,
                        )
                    )
                }
            }
            val formattedTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            updateState {
                copy(
                    diagnosticsState = diagnosticsState.copy(
                        isNetworkTesting = false,
                        currentTest = "",
                        lastRunTime = formattedTime,
                    )
                )
            }
        }
    }

    private fun runMetaDiagnostics() {
        metaDiagJob?.cancel()
        updateState {
            copy(
                diagnosticsState = diagnosticsState.copy(
                    isMetaTesting = true,
                    results = diagnosticsState.results.filter { !it.name.startsWith("Provider:") },
                    currentTest = "Starting metadata tests...",
                )
            )
        }
        metaDiagJob = viewModelScope.launch(Dispatchers.IO) {
            DiagnosticsRunner.runMetaProviders { result ->
                updateState {
                    copy(
                        diagnosticsState = diagnosticsState.copy(
                            results = diagnosticsState.results + result,
                            currentTest = result.name,
                        )
                    )
                }
            }
            val formattedTime = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            updateState {
                copy(
                    diagnosticsState = diagnosticsState.copy(
                        isMetaTesting = false,
                        currentTest = "",
                        lastRunTime = formattedTime,
                    )
                )
            }
        }
    }

    private fun updateDownloadPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(path).mkdirs()
                DesktopDataStore.setKey(DesktopDataStore.PREF_DOWNLOAD_PATH, path)
                updateState { copy(downloadPath = path) }
                sendEffect(SettingsUiEffect.ShowToast("Download directory updated"))
            } catch (e: Exception) {
                AppLogger.e("DownloadSettings", "Failed to update download directory", e)
                sendEffect(SettingsUiEffect.ShowToast("Failed to set directory: ${e.message}", isError = true))
            }
        }
    }

    private fun updateScreenshotPath(path: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                File(path).mkdirs()
                DesktopDataStore.setKey(PlayerConfig.PREF_SCREENSHOT_DIR, path)
                updateState { copy(screenshotPath = path) }
                sendEffect(SettingsUiEffect.ShowToast("Screenshot directory updated"))
            } catch (e: Exception) {
                AppLogger.e("ScreenshotSettings", "Failed to update screenshot directory", e)
                sendEffect(SettingsUiEffect.ShowToast("Failed to set directory: ${e.message}", isError = true))
            }
        }
    }

    private fun addClonedSite(site: CustomSite) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mapper = jacksonObjectMapper()
                val existing = try {
                    val json = DesktopDataStore.getKey<String>(PreferenceKeys.USER_PROVIDER_API)
                    if (json != null) mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<CustomSite>>() {}) else emptyList()
                } catch (_: Exception) { emptyList() }
                val newList = existing + site
                DesktopDataStore.setKey(PreferenceKeys.USER_PROVIDER_API, mapper.writeValueAsString(newList))
                updateState { copy(stringSettings = stringSettings + (PreferenceKeys.USER_PROVIDER_API to mapper.writeValueAsString(newList))) }
                try {
                    val baseProvider = APIHolder.allProviders.firstOrNull { it.javaClass.simpleName == site.parentJavaClass }
                    if (baseProvider != null) {
                        val clone = baseProvider.javaClass.getDeclaredConstructor().newInstance()
                        clone.name = site.name
                        clone.lang = site.lang
                        clone.mainUrl = site.url
                        clone.canBeOverridden = false
                        APIHolder.allProviders.add(clone)
                        APIHolder.addPluginMapping(clone)
                    }
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.e("SettingsViewModel", "Failed to register cloned provider", e)
                }
            } catch (e: Exception) {
                sendEffect(SettingsUiEffect.ShowToast("Failed to add cloned site: ${e.message}", isError = true))
            }
        }
    }

    private fun removeClonedSite(site: CustomSite) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mapper = jacksonObjectMapper()
                val existing = try {
                    val json = DesktopDataStore.getKey<String>(PreferenceKeys.USER_PROVIDER_API)
                    if (json != null) mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<CustomSite>>() {}) else emptyList()
                } catch (_: Exception) { emptyList() }
                val newList = existing.filter { it != site }
                DesktopDataStore.setKey(PreferenceKeys.USER_PROVIDER_API, mapper.writeValueAsString(newList))
                updateState { copy(stringSettings = stringSettings + (PreferenceKeys.USER_PROVIDER_API to mapper.writeValueAsString(newList))) }
            } catch (e: Exception) {
                sendEffect(SettingsUiEffect.ShowToast("Failed to remove cloned site: ${e.message}", isError = true))
            }
        }
    }
}
