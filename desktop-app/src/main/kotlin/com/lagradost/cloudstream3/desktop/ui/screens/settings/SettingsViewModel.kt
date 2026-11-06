package com.lagradost.cloudstream3.desktop.ui.screens.settings

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager
import com.lagradost.cloudstream3.desktop.download.AppDownloadManager
import com.lagradost.cloudstream3.desktop.download.TaskStatus
import com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager
import com.lagradost.cloudstream3.desktop.models.CustomSite
import com.lagradost.cloudstream3.desktop.network.DiagnosticsRunner
import com.lagradost.cloudstream3.desktop.network.DohProvider
import com.lagradost.cloudstream3.desktop.network.NetworkConfig

import com.lagradost.cloudstream3.desktop.player.LanguagePriorityHelper
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.player.QualityDataHelper
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.ui.screens.settings.contract.*
import com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager
import com.lagradost.cloudstream3.desktop.utils.DeveloperModeManager
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.cloudstream3.utils.TestingUtils
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel : BaseMviViewModel<SettingsUiState, SettingsUiEvent, SettingsUiEffect>(
    initialState = SettingsUiState(),
) {
    private val imageCacheDir = File(PlatformPaths.appDataDir, "image_cache")
    private val networkCacheDir = File(PlatformPaths.cacheDir, "network")
    private val dbFile = File(PlatformPaths.dataDir, "cloudstream.db")
    private val logsDir = PlatformPaths.logsDir
    private val streamCacheDir = File(PlatformPaths.cacheDir, "stream_cache")
    private val updatesDir = UnifiedUpdateManager.getUpdatesDirectory()

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
            val subColor = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_COLOR) ?: "#FFFFFF"
            val subSize = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SIZE) ?: "45"
            val subBg = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BG) ?: "#00000000"
            val subFont = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_FONT) ?: "Inter"
            val subBorderColor = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BORDER_COLOR) ?: "#000000"
            val subBorderSize = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BORDER_SIZE) ?: "3"
            val subShadowColor = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SHADOW_COLOR) ?: "#00000000"
            val subShadowOffset = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_SHADOW_OFFSET) ?: "0"
            val subBlur = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BLUR) ?: "0"
            val subPos = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_POS) ?: "100"

            val subOverride = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_ENABLE_SUB_OVERRIDE) ?: true
            val subCaptions = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_SUB_REMOVE_CAPTIONS) ?: false
            val subBloat = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_SUB_REMOVE_BLOAT) ?: true
            val subUppercase = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_SUB_UPPERCASE) ?: false
            val subEnabled = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_SUB_ENABLED) ?: true

            val dohProvider = DesktopDataStore.getKey<Int>(NetworkConfig.PREF_DOH_PROVIDER) ?: 0
            val torboxApiKey = DesktopDataStore.getKey<String>(DesktopDataStore.PREF_TORBOX_API_KEY) ?: ""
            val torboxUser = if (torboxApiKey.isNotBlank()) {
                try {
                    TorboxApiClient.getUser(torboxApiKey)
                } catch (e: Exception) {
                    null
                }
            } else null

            val ignoredDomains = DesktopDataStore.getIgnoredCfDomains().toList().sorted()

            val clonedSitesList = try {
                val json = DesktopDataStore.getKey<String>(PreferenceKeys.USER_PROVIDER_API)
                if (json != null) {
                    jacksonObjectMapper().readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<CustomSite>>() {})
                } else emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            updateState {
                copy(
                    downloadPath = initialDownloadPath,
                    screenshotPath = initialScreenshotPath,
                    torboxAccountState = torboxAccountState.copy(user = torboxUser),
                    ignoredCfDomains = ignoredDomains,
                    clonedSites = clonedSitesList,
                    booleanSettings = booleanSettings + mapOf(
                        DesktopDataStore.PREF_ALLOW_CF_BYPASS to cfEnabled,
                        DesktopDataStore.PREF_P2P_ENABLED to p2pEnabled,
                        PlayerConfig.PREF_AUDIO_NORMALIZATION to audioNorm,
                        PlayerConfig.PREF_ENABLE_SUB_OVERRIDE to subOverride,
                        PlayerConfig.PREF_SUB_REMOVE_CAPTIONS to subCaptions,
                        PlayerConfig.PREF_SUB_REMOVE_BLOAT to subBloat,
                        PlayerConfig.PREF_SUB_UPPERCASE to subUppercase,
                        PlayerConfig.PREF_SUB_ENABLED to subEnabled,
                    ),
                    intSettings = intSettings + mapOf(
                        NetworkConfig.PREF_DOH_PROVIDER to dohProvider,
                    ),
                    floatSettings = floatSettings + mapOf(
                        PlayerConfig.PREF_AUDIO_DELAY to audioDelay,
                        DesktopDataStore.PREF_DOWNLOAD_THREADS to downloadThreads,
                        DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT to maxConcurrent,
                    ),
                    stringSettings = stringSettings + mapOf(
                        PlayerConfig.PREF_SUB_BOLD to subBold,
                        PlayerConfig.PREF_SUB_ITALIC to subItalic,
                        PlayerConfig.PREF_SUB_COLOR to subColor,
                        PlayerConfig.PREF_SUB_SIZE to subSize,
                        PlayerConfig.PREF_SUB_BG to subBg,
                        PlayerConfig.PREF_SUB_FONT to subFont,
                        PlayerConfig.PREF_SUB_BORDER_COLOR to subBorderColor,
                        PlayerConfig.PREF_SUB_BORDER_SIZE to subBorderSize,
                        PlayerConfig.PREF_SUB_SHADOW_COLOR to subShadowColor,
                        PlayerConfig.PREF_SUB_SHADOW_OFFSET to subShadowOffset,
                        PlayerConfig.PREF_SUB_BLUR to subBlur,
                        PlayerConfig.PREF_SUB_POS to subPos,
                        NetworkConfig.PREF_CUSTOM_DOH_URL to (DesktopDataStore.getKey<String>(NetworkConfig.PREF_CUSTOM_DOH_URL) ?: ""),
                    ),
                )
            }
        }
        handleEvent(SettingsUiEvent.RefreshStorageMetrics)
        handleEvent(SettingsUiEvent.RefreshTorrServerStatus)
        handleEvent(SettingsUiEvent.RefreshClearanceCookies)
        observeTorrServerDownloadTask()
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.keyUpdates.collect { key ->
                if (key.startsWith("player_sub_") || key == PlayerConfig.PREF_ENABLE_SUB_OVERRIDE) {
                    val booleanKeys = setOf(
                        PlayerConfig.PREF_SUB_ENABLED,
                        PlayerConfig.PREF_ENABLE_SUB_OVERRIDE,
                        PlayerConfig.PREF_SUB_REMOVE_CAPTIONS,
                        PlayerConfig.PREF_SUB_REMOVE_BLOAT,
                        PlayerConfig.PREF_SUB_UPPERCASE,
                        PlayerConfig.PREF_SUBSOURCE_ENABLED,
                    )
                    if (key in booleanKeys) {
                        val boolVal = DesktopDataStore.getKey<Boolean>(key)
                        updateState {
                            copy(
                                booleanSettings = if (boolVal != null) booleanSettings + (key to boolVal) else booleanSettings - key,
                            )
                        }
                    } else {
                        val strVal = DesktopDataStore.getKey<String>(key)
                        updateState {
                            copy(
                                stringSettings = if (strVal != null) stringSettings + (key to strVal) else stringSettings - key,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun handleEvent(event: SettingsUiEvent) {
        when (event) {
            is SettingsUiEvent.OnUpdateString -> {
                updateState {
                    copy(
                        stringSettings = stringSettings + (event.key to event.value),
                        dnsTestState = if (event.key == NetworkConfig.PREF_CUSTOM_DOH_URL) DnsTestState() else dnsTestState,
                    )
                }
                if (event.key == NetworkConfig.PREF_CUSTOM_DOH_URL) {
                    try {
                        NetworkConfig.updateGlobalNetworkClients()
                    } catch (e: Exception) {
                        com.lagradost.common.logging.AppLogger.e("Network Reload Error", e)
                    }
                }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)
                }
            }
            is SettingsUiEvent.OnUpdateBoolean -> {
                updateState { copy(booleanSettings = booleanSettings + (event.key to event.value)) }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)

                    if (event.key == DesktopDataStore.PREF_ALLOW_CF_BYPASS && event.value) {
                        CloudflareKiller.unmarkAllFailed()
                        val lastUrl = CloudflareKiller.lastChallengedUrl
                        val lastTime = CloudflareKiller.lastChallengeTimestamp
                        if (!lastUrl.isNullOrBlank() && (System.currentTimeMillis() - lastTime) < 300_000L) {
                            launchManualClearance(lastUrl)
                        }
                    }

                    if (event.key == NetworkConfig.PREF_DOH_AUTO_FALLBACK) {
                        try {
                            NetworkConfig.updateGlobalNetworkClients()
                        } catch (e: Exception) {
                            com.lagradost.common.logging.AppLogger.e("Network Reload Error", e)
                        }
                    }

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
                updateState {
                    copy(
                        intSettings = intSettings + (event.key to event.value),
                        dnsTestState = if (event.key == NetworkConfig.PREF_DOH_PROVIDER) DnsTestState() else dnsTestState,
                    )
                }
                viewModelScope.launch(Dispatchers.IO) {
                    DesktopDataStore.setKey(event.key, event.value)

                    if (event.key == NetworkConfig.PREF_DOH_PROVIDER) {
                        try {
                            NetworkConfig.updateGlobalNetworkClients()
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
            is SettingsUiEvent.StartSingleProviderTest -> {
                testSingleProvider(event.apiName)
            }
            is SettingsUiEvent.SetProviderTestFilter -> {
                updateState { copy(providerTestState = providerTestState.copy(filter = event.filter)) }
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
            is SettingsUiEvent.TestDns -> {
                testDns(event.provider, event.customEndpoint)
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
            is SettingsUiEvent.ValidateTorboxKey -> {
                validateTorboxKey(event.apiKey)
            }
            is SettingsUiEvent.StartTorboxAuth -> {
                startTorboxAuth()
            }
            is SettingsUiEvent.CompleteTorboxAuth -> {
                completeTorboxAuth(event.token)
            }
            is SettingsUiEvent.SetTorboxAuthError -> {
                setTorboxAuthError(event.error)
            }
            is SettingsUiEvent.CancelTorboxAuth -> {
                cancelTorboxAuth()
            }
            is SettingsUiEvent.DisconnectTorbox -> {
                disconnectTorbox()
            }
            is SettingsUiEvent.LoadIgnoredCfDomains -> {
                loadIgnoredCfDomains()
            }
            is SettingsUiEvent.RemoveIgnoredCfDomain -> {
                removeIgnoredCfDomain(event.domain)
            }
            is SettingsUiEvent.ClearAllIgnoredCfDomains -> {
                clearAllIgnoredCfDomains()
            }
            is SettingsUiEvent.TestAccountApiKey -> {
                testAccountApiKey(event.idPrefix, event.apiKey)
            }
            is SettingsUiEvent.ResetAccountApiTestStatus -> {
                updateState { copy(testAccountApiStatus = null) }
            }
        }
    }

    private fun getDirectorySizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return try {
            dir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
        } catch (e: Exception) {
            AppLogger.d("Settings: Directory size calculation failed for ${dir.name}: ${e.message}")
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
            val stream = getDirectorySizeBytes(streamCacheDir)
            val upd = getDirectorySizeBytes(updatesDir)
            updateState {
                copy(
                    storageMetrics = StorageMetrics(
                        imageCacheBytes = img,
                        networkCacheBytes = net,
                        databaseBytes = db,
                        logsBytes = logs,
                        streamCacheBytes = stream,
                        updatesBytes = upd,
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
                StorageCacheType.STREAM -> streamCacheDir to "Stream & temp cache"
                StorageCacheType.UPDATES -> updatesDir to "Update installers"
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
                com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager.close()
            } catch (e: Throwable) {
                AppLogger.d("Settings: Teardown step notice during reset: ${e.message}")
            }
            try {
                DesktopTorrentEngine.binary.stop()
            } catch (e: Throwable) {
                AppLogger.d("Settings: Teardown step notice during reset: ${e.message}")
            }
            try {
                com.lagradost.runtime.loader.ExtensionLoader.unloadAllPlugins()
            } catch (e: Throwable) {
                AppLogger.d("Settings: Teardown step notice during reset: ${e.message}")
            }

            @Suppress("ExplicitGarbageCollectionCall")
            System.gc()
            @Suppress("deprecation")
            System.runFinalization()

            if (target.exists()) {
                try {
                    target.deleteRecursively()
                } catch (e: Throwable) {
                    AppLogger.w("Settings: Direct appDataDir delete notice during reset: ${e.message}")
                }
            }

            try {
                val isWindows = PlatformPaths.currentOS == PlatformPaths.OS.WINDOWS
                val tempDir = File(System.getProperty("java.io.tmpdir"))
                if (isWindows) {
                    val escapedPath = target.absolutePath.replace("'", "''")
                    ProcessBuilder(
                        "powershell.exe",
                        "-WindowStyle", "Hidden",
                        "-NoProfile",
                        "-NonInteractive",
                        "-Command",
                        "Start-Sleep -Seconds 2; Stop-Process -Name torrserver -Force -ErrorAction SilentlyContinue; if (Test-Path -LiteralPath '$escapedPath') { Remove-Item -LiteralPath '$escapedPath' -Recurse -Force -ErrorAction SilentlyContinue }"
                    ).directory(tempDir).start()
                } else {
                    val escapedPath = target.absolutePath.replace("\"", "\\\"")
                    ProcessBuilder(
                        "sh",
                        "-c",
                        "sleep 2; if [ -d \"$escapedPath\" ]; then rm -rf \"$escapedPath\" 2>/dev/null; fi"
                    ).directory(tempDir).start()
                }
            } catch (e: Throwable) {
                AppLogger.e("Settings: Background delayed wipe launcher failed", e)
            }

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
                        ),
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
                    ),
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
                            ),
                        )
                    }
                }
            } catch (e: Exception) {
                updateState {
                    copy(
                        engineState = engineState.copy(
                            isCheckingUpdates = false,
                            updateFeedback = "Failed to check update: ${e.message}",
                        ),
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
            val host = try {
                java.net.URI(url).host.orEmpty()
            } catch (e: Exception) {
                AppLogger.w("Settings: Invalid URL for manual clearance '$url': ${e.message}")
                ""
            }
            val success = if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable && host.isNotBlank()) {
                CloudflareKiller.solveWithEmbeddedWebView(url, host)
            } else {
                false
            }
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
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DeveloperModeManager.setEnabled(true)
                updateState { copy(isDevModeEnabled = true, devModeError = null) }
            } catch (e: Exception) {
                AppLogger.e("SettingsViewModel", "Failed to enable developer mode", e)
            }
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
        val providers = APIHolder.allProviders
            .filter { it.providerType != com.lagradost.cloudstream3.ProviderType.MetaProvider && it.name != "NONE" && it.name.isNotBlank() }
            .distinctBy { it.name }
            .sortedBy { it.name }
        updateState {
            copy(
                providerTestState = ProviderTestReportState(
                    isRunning = true,
                    results = emptyMap(),
                    passed = 0,
                    failed = 0,
                    total = providers.size,
                    filter = providerTestState.filter,
                ),
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
                            currentlyTestingProvider = if (isRunning) api.name else null,
                        ),
                    )
                }
            }
        }
    }

    private fun cancelProviderTests() {
        providerTestJob?.cancel()
        providerTestJob = null
        updateState { copy(providerTestState = providerTestState.copy(isRunning = false, currentlyTestingProvider = null)) }
    }

    private fun testSingleProvider(apiName: String) {
        val target = APIHolder.allProviders.firstOrNull { it.name.equals(apiName, ignoreCase = true) } ?: return
        updateState {
            copy(
                providerTestState = providerTestState.copy(
                    currentlyTestingProvider = target.name,
                ),
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                TestingUtils.getDeferredProviderTests(this, arrayOf(target)) { api, result ->
                    updateState {
                        val currentResults = providerTestState.results.toMutableMap()
                        currentResults[api.name] = result
                        val passedCount = currentResults.values.count { it.success }
                        val failedCount = currentResults.values.count { !it.success }
                        copy(
                            providerTestState = providerTestState.copy(
                                results = currentResults,
                                passed = passedCount,
                                failed = failedCount,
                                currentlyTestingProvider = if (providerTestState.currentlyTestingProvider == api.name) null else providerTestState.currentlyTestingProvider,
                            ),
                        )
                    }
                }
            } catch (e: Throwable) {
                AppLogger.e("SettingsViewModel", "Single provider test failed for $apiName", e)
            } finally {
                updateState {
                    copy(
                        providerTestState = providerTestState.copy(
                            currentlyTestingProvider = if (providerTestState.currentlyTestingProvider == target.name) null else providerTestState.currentlyTestingProvider,
                        ),
                    )
                }
            }
        }
    }

    private fun runNetworkDiagnostics() {
        networkDiagJob?.cancel()
        updateState {
            copy(
                diagnosticsState = diagnosticsState.copy(
                    isNetworkTesting = true,
                    results = emptyList(),
                    currentTest = "Starting...",
                ),
            )
        }
        networkDiagJob = viewModelScope.launch(Dispatchers.IO) {
            DiagnosticsRunner.runAll { result ->
                updateState {
                    copy(
                        diagnosticsState = diagnosticsState.copy(
                            results = diagnosticsState.results + result,
                            currentTest = result.name,
                        ),
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
                    ),
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
                ),
            )
        }
        metaDiagJob = viewModelScope.launch(Dispatchers.IO) {
            DiagnosticsRunner.runMetaProviders { result ->
                updateState {
                    copy(
                        diagnosticsState = diagnosticsState.copy(
                            results = diagnosticsState.results + result,
                            currentTest = result.name,
                        ),
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
                    ),
                )
            }
        }
    }

    private fun testDns(provider: DohProvider?, customEndpoint: String?) {
        updateState { copy(dnsTestState = DnsTestState(isTesting = true, result = null)) }
        viewModelScope.launch(Dispatchers.IO) {
            val currentProvider = provider
                ?: uiState.value.intSettings[NetworkConfig.PREF_DOH_PROVIDER]?.let { idx -> DohProvider.entries.getOrNull(idx) }
                ?: DesktopDataStore.getKey<Int>(NetworkConfig.PREF_DOH_PROVIDER)?.let { idx -> DohProvider.entries.getOrNull(idx) }
                ?: DohProvider.NONE

            val endpoint = customEndpoint
                ?: uiState.value.stringSettings[NetworkConfig.PREF_CUSTOM_DOH_URL]
                ?: DesktopDataStore.getKey<String>(NetworkConfig.PREF_CUSTOM_DOH_URL)
            val result = NetworkConfig.testDnsConnection(currentProvider, endpoint)
            updateState { copy(dnsTestState = DnsTestState(isTesting = false, result = result)) }
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
                } catch (e: Exception) {
                    AppLogger.w("Settings: Failed parsing cloned sites JSON", e)
                    emptyList()
                }
                val newList = existing + site
                DesktopDataStore.setKey(PreferenceKeys.USER_PROVIDER_API, mapper.writeValueAsString(newList))
                updateState { copy(stringSettings = stringSettings + (PreferenceKeys.USER_PROVIDER_API to mapper.writeValueAsString(newList)), clonedSites = newList) }
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
                } catch (e: Exception) {
                    AppLogger.w("Settings: Failed parsing cloned sites JSON", e)
                    emptyList()
                }
                val newList = existing.filter { it != site }
                DesktopDataStore.setKey(PreferenceKeys.USER_PROVIDER_API, mapper.writeValueAsString(newList))
                updateState { copy(stringSettings = stringSettings + (PreferenceKeys.USER_PROVIDER_API to mapper.writeValueAsString(newList)), clonedSites = newList) }
            } catch (e: Exception) {
                sendEffect(SettingsUiEffect.ShowToast("Failed to remove cloned site: ${e.message}", isError = true))
            }
        }
    }

    private fun validateTorboxKey(apiKey: String) {
        val clean = apiKey.trim()
        if (clean.isBlank()) {
            updateState {
                copy(
                    torboxAccountState = torboxAccountState.copy(
                        feedbackMessage = "API Key cannot be blank",
                        feedbackIsError = true,
                    )
                )
            }
            return
        }
        updateState {
            copy(
                torboxAccountState = torboxAccountState.copy(
                    isValidatingKey = true,
                    feedbackMessage = null,
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val user = TorboxApiClient.getUser(clean)
            if (user != null) {
                DesktopDataStore.setKey(DesktopDataStore.PREF_TORBOX_API_KEY, clean)
                DesktopDataStore.setKey(DesktopDataStore.PREF_TORBOX_ENABLED, true)
                updateState {
                    copy(
                        stringSettings = stringSettings + (DesktopDataStore.PREF_TORBOX_API_KEY to clean),
                        booleanSettings = booleanSettings + (DesktopDataStore.PREF_TORBOX_ENABLED to true),
                        torboxAccountState = torboxAccountState.copy(
                            user = user,
                            isValidatingKey = false,
                            feedbackMessage = "Connected successfully as ${user.email ?: "user"} (${user.planName})",
                            feedbackIsError = false,
                        ),
                    )
                }
            } else {
                updateState {
                    copy(
                        torboxAccountState = torboxAccountState.copy(
                            isValidatingKey = false,
                            feedbackMessage = "Invalid API Key or server unreachable",
                            feedbackIsError = true,
                        ),
                    )
                }
            }
        }
    }

    private fun startTorboxAuth() {
        updateState {
            copy(
                torboxAccountState = torboxAccountState.copy(
                    isStartingAuth = true,
                    feedbackMessage = null,
                )
            )
        }
        viewModelScope.launch(Dispatchers.IO) {
            val auth = TorboxApiClient.startDeviceAuthorization()
            if (auth != null) {
                updateState {
                    copy(
                        torboxAccountState = torboxAccountState.copy(
                            isStartingAuth = false,
                            activeAuth = auth,
                        )
                    )
                }
            } else {
                updateState {
                    copy(
                        torboxAccountState = torboxAccountState.copy(
                            isStartingAuth = false,
                            feedbackMessage = "Failed to start Torbox authorization. Check internet connection.",
                            feedbackIsError = true,
                        )
                    )
                }
            }
        }
    }

    private fun completeTorboxAuth(token: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(DesktopDataStore.PREF_TORBOX_API_KEY, token)
            DesktopDataStore.setKey(DesktopDataStore.PREF_TORBOX_ENABLED, true)
            val user = TorboxApiClient.getUser(token)
            updateState {
                copy(
                    stringSettings = stringSettings + (DesktopDataStore.PREF_TORBOX_API_KEY to token),
                    booleanSettings = booleanSettings + (DesktopDataStore.PREF_TORBOX_ENABLED to true),
                    torboxAccountState = torboxAccountState.copy(
                        user = user,
                        activeAuth = null,
                        feedbackMessage = "Successfully connected to Torbox via WebView!",
                        feedbackIsError = false,
                    )
                )
            }
        }
    }

    private fun setTorboxAuthError(error: String) {
        updateState {
            copy(
                torboxAccountState = torboxAccountState.copy(
                    activeAuth = null,
                    feedbackMessage = error,
                    feedbackIsError = true,
                )
            )
        }
    }

    private fun cancelTorboxAuth() {
        updateState {
            copy(
                torboxAccountState = torboxAccountState.copy(
                    activeAuth = null,
                )
            )
        }
    }

    private fun disconnectTorbox() {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.removeKey(DesktopDataStore.PREF_TORBOX_API_KEY)
            updateState {
                copy(
                    stringSettings = stringSettings + (DesktopDataStore.PREF_TORBOX_API_KEY to ""),
                    torboxAccountState = torboxAccountState.copy(
                        user = null,
                        activeAuth = null,
                        feedbackMessage = "Disconnected from Torbox",
                        feedbackIsError = false,
                    )
                )
            }
        }
    }

    private fun loadIgnoredCfDomains() {
        viewModelScope.launch(Dispatchers.IO) {
            val updated = DesktopDataStore.getIgnoredCfDomains().toList().sorted()
            updateState { copy(ignoredCfDomains = updated) }
        }
    }

    private fun removeIgnoredCfDomain(domain: String) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setDomainCfIgnored(domain, false)
            val updated = DesktopDataStore.getIgnoredCfDomains().toList().sorted()
            updateState { copy(ignoredCfDomains = updated) }
        }
    }

    private fun clearAllIgnoredCfDomains() {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.getIgnoredCfDomains().let { current ->
                current.forEach { DesktopDataStore.setDomainCfIgnored(it, false) }
                updateState { copy(ignoredCfDomains = emptyList()) }
            }
        }
    }

    private fun testAccountApiKey(idPrefix: String, apiKey: String) {
        val cleanKey = apiKey.trim()
        if (cleanKey.isBlank()) {
            updateState { copy(testAccountApiStatus = false to "API Key cannot be empty") }
            return
        }
        updateState { copy(isTestingAccountApi = true, testAccountApiStatus = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                when (idPrefix) {
                    "subdl" -> {
                        val r = com.lagradost.cloudstream3.app.get(
                            "https://api.subdl.com/api/v1/subtitles?api_key=$cleanKey&film_name=matrix",
                            timeout = 8000L
                        ).okhttpResponse
                        when (r.code) {
                            200 -> true to "Valid API Key (Connected successfully)"
                            400, 401, 403 -> false to "Invalid API Key (HTTP ${r.code})"
                            else -> false to "HTTP ${r.code}: Unexpected response"
                        }
                    }
                    "subsource" -> {
                        com.lagradost.cloudstream3.desktop.subtitles.SubsourceSubtitleProvider.testApiKey(cleanKey)
                    }
                    else -> {
                        true to "API Key format verified"
                    }
                }
            }.getOrElse { false to "Connection error: ${it.message}" }.let { res -> updateState { copy(isTestingAccountApi = false, testAccountApiStatus = res) }

            }
        }
    }
}
