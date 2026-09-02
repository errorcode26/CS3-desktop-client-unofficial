package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MovieLoadResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerError
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiState
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.lagradost.cloudstream3.desktop.domain.player.interactor.SavePlaybackProgress
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedPlayerViewModel(
    private val savePlaybackProgress: SavePlaybackProgress = SavePlaybackProgress(),
) : BaseMviViewModel<PlayerUiState, PlayerUiEvent, PlayerUiEffect>(
    initialState = PlayerUiState(),
) {
    val playerState = PlayerState()
    private var loadLinksJob: Job? = null
    private var saveJob: Job? = null
    private var timeoutJob: Job? = null
    private var countdownJob: Job? = null
    private var preScrapeJob: Job? = null
    private var lastPreScrapedEpisodeId: String? = null

    private val linkRetries = mutableMapOf<String, Int>()
    companion object {
        private const val MAX_RETRIES = 2
    }

    init {
        PlayerDiagnosticsHolder.register(playerState)

        viewModelScope.launch(Dispatchers.IO) {
            val autoPlay = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true
            updateState { copy(autoPlayEnabled = autoPlay) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            var lastSavedPositionSec = 0L

            playerState.positionMs.collect { posMs ->
                val currentPosSec = posMs / 1000L
                val durSec = playerState.durationMs.value / 1000L
                val isPaused = playerState.isPaused.value
                val currentData = uiState.value.launchData

                if (kotlin.math.abs(currentPosSec - lastSavedPositionSec) >= 5) {
                    lastSavedPositionSec = currentPosSec
                    if (currentData != null) {
                        val updatedHistory = currentData.history.copy(
                            position = currentPosSec,
                            duration = durSec,
                            updateTime = System.currentTimeMillis(),
                        )
                        savePosition(updatedHistory)
                    }
                }

                val percentage = if (durSec > 0) currentPosSec.toFloat() / durSec.toFloat() else 0f
                if (percentage >= 0.88f && uiState.value.hasNextEpisode && uiState.value.autoPlayEnabled) {
                    triggerBackgroundPreScrape()
                } else if (percentage < 0.85f && preScrapeJob?.isActive == true) {
                    preScrapeJob?.cancel()
                }

                if (currentData != null) {
                    val title = currentData.title ?: currentData.history.showName
                    val season = currentData.history.season
                    val episode = currentData.history.episode
                    val episodeInfo = when {
                        season != null && episode != null -> "S$season • E$episode"
                        episode != null -> "Episode $episode"
                        else -> null
                    }
                    com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updatePlaying(
                        title = title,
                        episodeInfo = episodeInfo,
                        positionSeconds = currentPosSec,
                        durationSeconds = durSec,
                        isPaused = isPaused,
                        posterUrl = currentData.history.posterUrl,
                    )
                }
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            playerState.isPaused.collect { isPaused ->
                val currentData = uiState.value.launchData ?: return@collect
                val currentPosSec = playerState.positionMs.value / 1000L
                val durSec = playerState.durationMs.value / 1000L
                val title = currentData.title ?: currentData.history.showName
                val season = currentData.history.season
                val episode = currentData.history.episode
                val episodeInfo = when {
                    season != null && episode != null -> "S$season • E$episode"
                    episode != null -> "Episode $episode"
                    else -> null
                }
                com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.updatePlaying(
                    title = title,
                    episodeInfo = episodeInfo,
                    positionSeconds = currentPosSec,
                    durationSeconds = durSec,
                    isPaused = isPaused,
                    posterUrl = currentData.history.posterUrl,
                )
            }
        }
    }

    override fun dispose() {
        preScrapeJob?.cancel()
        countdownJob?.cancel()
        timeoutJob?.cancel()
        com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.onPlayerStopped()
        PlayerDiagnosticsHolder.unregister(playerState)
        val currentData = uiState.value.launchData
        val currentDurSec = playerState.durationMs.value / 1000L
        val currentPosSec = playerState.positionMs.value / 1000L
        if (currentData != null && currentDurSec > 0 && currentPosSec > 0) {
            val screenshotPath = "${com.lagradost.common.platform.PlatformPaths.appDataDir.absolutePath}/screenshots/history_${currentData.history.parentId}.jpg"
            java.io.File(screenshotPath).parentFile.mkdirs()
            playerState.takeScreenshot(screenshotPath)

            val updatedHistory = currentData.history.copy(
                position = currentPosSec,
                duration = currentDurSec,
                screenshotUrl = "file:///$screenshotPath",
                updateTime = System.currentTimeMillis(),
            )
            DesktopDataStore.setLastWatched(updatedHistory)

            val percentage = currentPosSec.toFloat() / currentDurSec.toFloat()
            if (percentage >= 0.90f && uiState.value.hasNextEpisode) {
                val nextEp = uiState.value.nextEpisodeData
                if (nextEp != null) {
                    val existingNext = DesktopDataStore.getEpisodeWatched(
                        parentId = updatedHistory.parentId,
                        episodeId = nextEp.data,
                    )
                    if (existingNext == null) {
                        val nextEpHistory = WatchHistory(
                            parentId = updatedHistory.parentId,
                            showName = updatedHistory.showName,
                            showUrl = updatedHistory.showUrl,
                            apiName = updatedHistory.apiName,
                            posterUrl = updatedHistory.posterUrl,
                            episodeThumbnailUrl = nextEp.posterUrl ?: updatedHistory.posterUrl,
                            screenshotUrl = null,
                            episode = nextEp.episode,
                            season = nextEp.season,
                            episodeId = nextEp.data,
                            position = 0,
                            duration = 0,
                            updateTime = System.currentTimeMillis() + 1000,
                            episodeName = nextEp.name,
                            episodeDescription = nextEp.description,
                        )
                        DesktopDataStore.setLastWatched(nextEpHistory)
                    } else {
                        DesktopDataStore.setLastWatched(
                            existingNext.copy(
                                updateTime = System.currentTimeMillis() + 1000,
                                episodeThumbnailUrl = existingNext.episodeThumbnailUrl ?: nextEp.posterUrl ?: updatedHistory.posterUrl,
                            ),
                        )
                    }
                }
            }
        }
        playerState.detachMpv()

        super.dispose()
        loadLinksJob?.cancel()
        saveJob?.cancel()
        countdownJob?.cancel()
        timeoutJob?.cancel()
        preScrapeJob?.cancel()
        updateState { copy(launchData = null, phase = PlayerPhase.Idle, failedLinks = emptyMap()) }
    }

    override fun handleEvent(event: PlayerUiEvent) {
        when (event) {
            is PlayerUiEvent.OnInit -> init(event.launchData)
            is PlayerUiEvent.OnLoadEpisode -> loadEpisode(event.episode)
            is PlayerUiEvent.OnLoadNextEpisode -> loadNextEpisode()
            is PlayerUiEvent.OnLoadPrevEpisode -> loadPrevEpisode()
            is PlayerUiEvent.OnPlayLoadedEpisode -> playLoadedEpisode()
            is PlayerUiEvent.OnCancelLoading -> cancelLoading()
            is PlayerUiEvent.OnCancelScraping -> cancelScraping()
            is PlayerUiEvent.OnSavePosition -> savePosition(event.history)
            is PlayerUiEvent.OnSelectShader -> selectShader(event.shaderName)
            is PlayerUiEvent.OnPlaybackError -> handlePlaybackError(event.failedUrl, event.reason)
            is PlayerUiEvent.OnLinkChange -> handleLinkChange(event.url)
            is PlayerUiEvent.OnPlaybackReady -> handlePlaybackReady()
            is PlayerUiEvent.OnPlaybackFinished -> handlePlaybackFinished()
            is PlayerUiEvent.OnCancelCountdown -> cancelCountdown()
        }
    }

    // Picks the best available link from the current launchData that hasn't failed.
    // Uses Android-parity QualityDataHelper score engine.
    private fun pickBestActiveLink(
        links: List<ExtractorLink>,
        failed: Set<String>,
        startPositionMs: Long = 0L,
    ): ExtractorLink? {
        val available = links.filter { it.url !in failed }
        if (available.isEmpty()) return null
        return sortLinks(available, startPositionMs).firstOrNull()
    }

    private fun updatePhase(phase: PlayerPhase, newFailedLinks: Map<String, String>? = null) {
        countdownJob?.cancel()

        // Timeout job is ONLY scheduled when we enter Probing phase.
        if (phase is PlayerPhase.Probing) {
            val isNewProbing = uiState.value.phase !is PlayerPhase.Probing ||
                (uiState.value.phase as? PlayerPhase.Probing)?.link?.url != phase.link.url ||
                phase.isRetry
            if (isNewProbing) {
                timeoutJob?.cancel()
                val timedOutUrl = phase.link.url
                val timeoutStr = DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "8000"
                val baseTimeoutMs = timeoutStr.toLongOrNull() ?: 8_000L
                val isP2p = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isTorrentLink(phase.link) || phase.link.url.contains("127.0.0.1:8091")
                val timeoutMs = if (isP2p) 35_000L else baseTimeoutMs
                timeoutJob = viewModelScope.launch {
                    delay(timeoutMs)
                    if (uiState.value.phase is PlayerPhase.Probing && (uiState.value.phase as? PlayerPhase.Probing)?.link?.url == timedOutUrl) {
                        AppLogger.w("EmbeddedPlayerViewModel", "Stream connection timed out after ${timeoutMs}ms, advancing to next candidate: ${timedOutUrl.take(80)}")
                        handlePlaybackError(timedOutUrl, "Connection Timed Out (${timeoutMs / 1000}s)")
                    }
                }
            }
        } else {
            timeoutJob?.cancel()
        }

        updateState {
            copy(
                phase = phase,
                countdownToNextEpisode = null,
                failedLinks = newFailedLinks ?: failedLinks,
            )
        }
    }

    private fun handlePlaybackReady() {
        timeoutJob?.cancel()
        val currentPhase = uiState.value.phase
        if (currentPhase is PlayerPhase.Probing && !currentPhase.isInitial) {
            val qualStr = if (currentPhase.link.quality > 0) " (${com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(currentPhase.link.quality)})" else ""
            sendEffect(PlayerUiEffect.ShowToast("Now playing: ${currentPhase.link.name}$qualStr"))
        }
        updateState {
            if (currentPhase is PlayerPhase.Probing) {
                copy(phase = PlayerPhase.Playing(currentPhase.link, currentPhase.stillScraping))
            } else {
                this
            }
        }
    }

    private fun handlePlaybackFinished() {
        timeoutJob?.cancel()
        // Re-read the pref fresh so a mid-session toggle takes effect immediately.
        val autoPlay = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true
        val state = uiState.value
        val hasNext = state.hasNextEpisode
        // When loadResponse is null (history / deep-link launch), episodes is empty so
        // hasNextEpisode is always false. Fall back to the episode number as a heuristic —
        // if the current entry has an episode number it is a series episode and there may
        // be a next one. loadNextEpisode will surface a toast if nothing is found.
        val episodesUnknown = state.episodes.isEmpty() && state.launchData?.history?.episode != null
        if ((hasNext || episodesUnknown) && autoPlay) {
            startCountdown()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        preScrapeJob?.cancel()
        updateState { copy(countdownToNextEpisode = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        triggerBackgroundPreScrape()
        countdownJob = viewModelScope.launch {
            val timeoutStr = DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "8000"
            var ticks = (timeoutStr.toLongOrNull() ?: 8000L) / 1000L

            while (ticks > 0) {
                updateState { copy(countdownToNextEpisode = ticks.toInt()) }
                delay(1000)
                ticks--
            }
            updateState { copy(countdownToNextEpisode = null) }
            handleEvent(PlayerUiEvent.OnLoadNextEpisode)
        }
    }

    private fun triggerBackgroundPreScrape() {
        val state = uiState.value
        if (!state.autoPlayEnabled || !state.hasNextEpisode) return
        val nextEp = state.nextEpisodeData ?: return
        val nextEpId = nextEp.data

        if (preScrapeJob?.isActive == true || lastPreScrapedEpisodeId == nextEpId) return
        if (LinkCache.get(nextEpId) != null) return

        val currentData = state.launchData ?: return
        val apiName = currentData.history.apiName.takeIf { it.isNotBlank() } ?: currentData.loadResponse?.apiName
        val provider = apiName?.let { APIHolder.getApiFromNameNull(it) } ?: return

        lastPreScrapedEpisodeId = nextEpId
        preScrapeJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                AppLogger.i("EmbeddedPlayerViewModel", "Starting background pre-scrape for next episode: ${nextEp.name ?: nextEp.data}")
                val collectedLinks = mutableListOf<ExtractorLink>()
                val collectedSubs = mutableListOf<SubtitleFile>()

                provider.loadLinks(
                    data = nextEpId,
                    isCasting = false,
                    subtitleCallback = { sub ->
                        collectedSubs.add(sub)
                    },
                    callback = { link ->
                        collectedLinks.add(link)
                        com.lagradost.cloudstream3.desktop.player.QualityDataHelper.registerDiscoveredSource(link.source)
                    },
                )

                if (collectedLinks.isNotEmpty()) {
                    val sorted = sortLinks(collectedLinks)
                    LinkCache.set(nextEpId, sorted, collectedSubs)
                    AppLogger.i("EmbeddedPlayerViewModel", "Background pre-scrape complete: cached ${sorted.size} streams for ${nextEp.name ?: nextEp.data}")
                }
            } catch (e: Throwable) {
                AppLogger.w("EmbeddedPlayerViewModel", "Background pre-scrape error: ${e.message}")
            }
        }
    }

    private fun handlePlaybackError(failedUrl: String, reason: String = "Connection failed") {
        val currentState = uiState.value
        val isTimeout = reason.contains("Timeout", ignoreCase = true) || reason.contains("Timed Out", ignoreCase = true)
        val isPermanentError = isTimeout || reason.contains("403") || reason.contains("404") ||
            reason.contains("401") || reason.contains("Forbidden") ||
            reason.contains("Not Found") || reason.contains("Unsupported")

        val retries = linkRetries.getOrDefault(failedUrl, 0)

        if (!isPermanentError && retries < MAX_RETRIES) {
            linkRetries[failedUrl] = retries + 1
            AppLogger.w("EmbeddedPlayerViewModel", "Stream error ($reason). Retrying same link (${retries + 1}/$MAX_RETRIES): $failedUrl")

            val currentLink = currentState.launchData?.links?.find { it.url == failedUrl }
            if (currentLink != null) {
                sendEffect(PlayerUiEffect.ShowToast("Stream error ($reason). Reconnecting (${retries + 1}/$MAX_RETRIES)..."))
                val currentPos = playerState.positionMs.value
                val startPos = if (currentPos > 0L) currentPos else currentState.launchData.startPositionMs
                updateState {
                    copy(launchData = launchData?.copy(startPositionMs = startPos))
                }
                updatePhase(PlayerPhase.Probing(currentLink, currentState.isScrapingLinks, isInitial = false, isRetry = true), currentState.failedLinks)
                return
            }
        }

        AppLogger.e("EmbeddedPlayerViewModel", "Playback error on $failedUrl: $reason")
        val newFailed = currentState.failedLinks + (failedUrl to reason)
        val links = currentState.nextEpisodeLinks.ifEmpty { currentState.launchData?.links ?: emptyList() }
        val currentPos = playerState.positionMs.value
        val startPos = if (currentPos > 0L) currentPos else (currentState.launchData?.startPositionMs ?: 0L)
        val next = pickBestActiveLink(links, newFailed.keys, startPos)

        if (next != null) {
            val failedLinkName = currentState.launchData?.links?.find { it.url == failedUrl }?.name ?: "Source"
            val nextQual = if (next.quality > 0) " (${com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(next.quality)})" else ""
            sendEffect(PlayerUiEffect.ShowToast("$failedLinkName failed ($reason). Falling back to ${next.name}$nextQual..."))
            updateState {
                copy(launchData = launchData?.copy(startPositionMs = startPos))
            }
            viewModelScope.launch {
                // Graceful 500ms breather so the UI can highlight the failed item in red with its reason
                delay(500)
                updatePhase(PlayerPhase.Probing(next, uiState.value.isScrapingLinks, isInitial = false), newFailed)
            }
        } else if (currentState.isScrapingLinks) {
            // Still scraping, wait for scrapers to produce more links.
            updatePhase(PlayerPhase.Scraping, newFailed)
        } else {
            // All links exhausted and scraping is done — surface the error
            AppLogger.e("EmbeddedPlayerViewModel", "All sources exhausted ($reason).")
            updatePhase(PlayerPhase.Idle, newFailed)
            sendEffect(PlayerUiEffect.ShowError("All sources failed ($reason)"))
            sendEffect(PlayerUiEffect.ClosePlayer)
        }
    }

    private fun handleLinkChange(url: String) {
        linkRetries.clear()
        val currentState = uiState.value
        val link = currentState.launchData?.links?.find { it.url == url }
        if (link != null) {
            val qualStr = if (link.quality > 0) " (${com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(link.quality)})" else ""
            sendEffect(PlayerUiEffect.ShowToast("Switching to ${link.name}$qualStr..."))
            val currentPos = playerState.positionMs.value
            val startPos = if (currentPos > 0L) currentPos else currentState.launchData.startPositionMs
            updateState {
                copy(launchData = launchData?.copy(startPositionMs = startPos))
            }
            updatePhase(PlayerPhase.Probing(link, currentState.isScrapingLinks, isInitial = false), emptyMap())
        }
    }

    private fun selectShader(shaderName: String) {
        DesktopDataStore.setKey(
            PlayerConfig.PREF_ACTIVE_SHADER,
            shaderName,
        )
        // Note: The shader will be applied on the NEXT player initialization.
        // Hot-swapping requires MPV property commands, which can be added via PlayerUiEffect if needed.
    }

    private fun savePosition(history: WatchHistory) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            delay(2000)
            val currentDurSec = history.duration
            val currentPosSec = history.position
            val percentage = if (currentDurSec > 0) currentPosSec.toFloat() / currentDurSec else 0f
            if (percentage >= 0.90f) {
                val hasNext = uiState.value.hasNextEpisode
                if (hasNext) {
                    val nextEp = uiState.value.nextEpisodeData
                    if (nextEp != null) {
                        DesktopDataStore.setLastWatched(history)
                        // Only create a "queued" placeholder for the next episode if it has never
                        // been touched — avoids wiping real progress if user already started it.
                        val existingNext = DesktopDataStore.getEpisodeWatched(
                            parentId = history.parentId,
                            episodeId = nextEp.data,
                        )
                        if (existingNext == null) {
                            val nextEpHistory = WatchHistory(
                                parentId = history.parentId,
                                showName = history.showName,
                                showUrl = history.showUrl,
                                apiName = history.apiName,
                                posterUrl = history.posterUrl,
                                episodeThumbnailUrl = nextEp.posterUrl ?: history.posterUrl,
                                screenshotUrl = null,
                                episode = nextEp.episode,
                                season = nextEp.season,
                                episodeId = nextEp.data,
                                position = 0,
                                duration = 0,
                                updateTime = System.currentTimeMillis() + 1000,
                                episodeName = nextEp.name,
                                episodeDescription = nextEp.description,
                            )
                            DesktopDataStore.setLastWatched(nextEpHistory)
                        } else {
                            DesktopDataStore.setLastWatched(
                                existingNext.copy(
                                    updateTime = System.currentTimeMillis() + 1000,
                                    episodeThumbnailUrl = existingNext.episodeThumbnailUrl ?: nextEp.posterUrl ?: history.posterUrl,
                                ),
                            )
                        }
                        return@launch
                    }
                }
            }
            DesktopDataStore.setLastWatched(history)
        }
    }

    private fun init(initialData: VideoLaunchData) {
        linkRetries.clear()
        loadLinksJob?.cancel()
        countdownJob?.cancel()
        timeoutJob?.cancel()
        preScrapeJob?.cancel()

        val isFinished = initialData.history.duration > 0 && initialData.history.position >= initialData.history.duration - 15
        val adjustedData = if (isFinished) {
            initialData.copy(
                startPositionMs = 0L,
                history = initialData.history.copy(position = 0L),
            )
        } else {
            initialData
        }
        updateState { copy(launchData = adjustedData, phase = PlayerPhase.Idle, failedLinks = emptyMap()) }

        // If launched from history without full metadata, fetch it in the background
        // This is required to populate the episode list so "Auto Next" and the Episodes panel work!
        if (adjustedData.loadResponse == null) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val apiName = adjustedData.history.apiName
                    val showUrl = adjustedData.history.showUrl
                    val provider = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull {
                        it.name == apiName && it.mainUrl.isNotBlank() && showUrl.startsWith(it.mainUrl)
                    } ?: com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(apiName)
                    if (provider != null) {
                        val res = SafePluginInvoker.invokeOrNull(
                            tag = "HistoryLaunch:${provider.name}",
                            timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                        ) {
                            provider.load(adjustedData.history.showUrl)
                        }
                        if (res is com.lagradost.cloudstream3.LoadResponse) {
                            updateState {
                                val currentLaunch = launchData
                                if (currentLaunch != null) {
                                    copy(launchData = currentLaunch.copy(loadResponse = res))
                                } else {
                                    this
                                }
                            }
                        }
                    }
                } catch (e: Throwable) {
                    com.lagradost.common.logging.AppLogger.w("EmbeddedPlayerViewModel", "Failed to fetch metadata for history launch: ${e.message}")
                }
            }
        }

        // Auto-scrape initial episode if links are empty
        if (adjustedData.links.isEmpty() && adjustedData.history.episodeId != null) {
            val apiName = adjustedData.loadResponse?.apiName ?: adjustedData.history.apiName
            val showUrl = adjustedData.loadResponse?.url ?: adjustedData.history.showUrl
            val provider = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull {
                it.name == apiName && it.mainUrl.isNotBlank() && showUrl.startsWith(it.mainUrl)
            } ?: com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(apiName)
            if (provider != null) {
                val targetEp = provider.newEpisode(adjustedData.history.episodeId!!) {
                    this.name = adjustedData.history.showName
                    this.season = adjustedData.history.season
                    this.episode = adjustedData.history.episode
                }
                updateState {
                    copy(
                        phase = PlayerPhase.Scraping,
                        targetEpisodeData = targetEp,
                        nextEpisodeLinks = emptyList(),
                        nextEpisodeSubtitles = adjustedData.subtitles,
                    )
                }

                loadLinksJob = viewModelScope.launch(Dispatchers.IO) {
                    scrapeAndPlay(provider, adjustedData.history.episodeId!!, adjustedData, targetEp)
                }
            } else {
                // Plugin not installed or apiName unknown — can't scrape, can't play.
                // Surface an error immediately rather than leaving the player on a blank screen.
                AppLogger.e("EmbeddedPlayerViewModel", "Provider not found for apiName='$apiName'. Cannot scrape links.")
                sendEffect(PlayerUiEffect.ShowError("Plugin not found — cannot load video."))
                sendEffect(PlayerUiEffect.ClosePlayer)
            }
        } else if (adjustedData.links.isNotEmpty()) {
            // Links already provided at launch (e.g. direct open) — pick immediately
            val best = pickBestActiveLink(adjustedData.links, emptySet(), adjustedData.startPositionMs)
            if (best != null) {
                updatePhase(PlayerPhase.Probing(best, false))
            } else {
                updatePhase(PlayerPhase.Idle)
            }
        }
    }

    private fun loadEpisode(episode: Episode) {
        linkRetries.clear()
        val currentData = uiState.value.launchData ?: return

        val lockUnreleased = com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.value
        if (lockUnreleased) {
            val status = com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(episode)
            if (status.isUnreleased) {
                val toast = status.statusBadgeText ?: "This episode is unreleased."
                sendEffect(PlayerUiEffect.ShowToast(toast))
                return
            }
        }

        countdownJob?.cancel()
        loadLinksJob?.cancel()
        updateState {
            copy(
                phase = PlayerPhase.Scraping,
                nextEpisodeError = null,
                nextEpisodeLinks = emptyList(),
                nextEpisodeSubtitles = emptyList(),
                targetEpisodeData = episode,
                failedLinks = emptyMap(),
            )
        }

        val apiName = currentData.loadResponse?.apiName
        val provider = APIHolder.getApiFromNameNull(apiName ?: "")

        if (provider != null) {
            loadLinksJob = viewModelScope.launch(Dispatchers.IO) {
                scrapeAndPlay(provider, episode.data, currentData, episode)
            }
        } else {
            updateState {
                copy(
                    targetEpisodeData = null,
                    phase = PlayerPhase.Idle,
                )
            }
        }
    }

    private fun cancelLoading() {
        loadLinksJob?.cancel()
        updateState {
            copy(
                targetEpisodeData = null,
                phase = PlayerPhase.Idle,
                nextEpisodeError = null,
            )
        }
    }

    private fun playLoadedEpisode() {
        linkRetries.clear()
        val currentData = uiState.value.launchData ?: return
        val epData = uiState.value.targetEpisodeData ?: return
        val currentLinks = uiState.value.nextEpisodeLinks
        if (currentLinks.isEmpty()) return

        val pastHistory = DesktopDataStore.getEpisodeWatched(
            parentId = currentData.history.parentId,
            episodeId = epData.data,
        )

        val startPos = if (pastHistory != null && pastHistory.duration > 0 && pastHistory.position < pastHistory.duration - 15) {
            pastHistory.position * 1000L
        } else {
            0L
        }

        val newHistory = currentData.history.copy(
            episodeId = epData.data,
            episode = epData.episode,
            season = epData.season,
            position = startPos / 1000L,
            duration = pastHistory?.duration ?: 0L,
        )

        val newLaunchData = currentData.copy(
            links = currentLinks,
            subtitles = uiState.value.nextEpisodeSubtitles,
            history = newHistory,
            initialIndex = 0,
            startPositionMs = startPos,
            title = buildString {
                append(newHistory.showName)
                if (newHistory.season != null && newHistory.episode != null) {
                    append(" - S${newHistory.season}E${newHistory.episode}")
                } else if (newHistory.episode != null) {
                    append(" - E${newHistory.episode}")
                }
            },
        )

        val best = pickBestActiveLink(currentLinks, uiState.value.failedLinks.keys, startPos)

        updateState {
            copy(
                nextEpisodeError = null,
                targetEpisodeData = null,
                nextEpisodeLinks = emptyList(),
                nextEpisodeSubtitles = emptyList(),
                launchData = newLaunchData,
            )
        }
        if (best != null) {
            updatePhase(PlayerPhase.Probing(best, false))
        } else {
            updatePhase(PlayerPhase.Idle)
        }
    }

    private fun loadNextEpisode() {
        linkRetries.clear()
        val episodes = uiState.value.episodes
        val currentData = uiState.value.launchData ?: return
        val currentEpId = currentData.history.episodeId
        var currentIndex = episodes.indexOfFirst { it.data == currentEpId }

        // Fallback by episode number if data/URL ID matching didn't resolve index
        if (currentIndex == -1) {
            val currentEpNum = currentData.history.episode
            val currentSeasonNum = currentData.history.season
            if (currentEpNum != null) {
                currentIndex = episodes.indexOfFirst { it.episode == currentEpNum && (currentSeasonNum == null || it.season == currentSeasonNum) }
            }
        }

        val nextEpisode = if (currentIndex != -1 && currentIndex + 1 < episodes.size) {
            episodes[currentIndex + 1]
        } else if (currentIndex == -1 && episodes.isNotEmpty()) {
            val currentEpNum = currentData.history.episode
            val currentSeasonNum = currentData.history.season
            if (currentEpNum != null) {
                episodes.find { it.episode == currentEpNum + 1 && (currentSeasonNum == null || it.season == currentSeasonNum) }
                    ?: episodes.find { it.season == (currentSeasonNum ?: 1) + 1 && (it.episode == 1 || it.episode == 0) }
            } else {
                null
            }
        } else {
            null
        }

        if (nextEpisode != null) {
            val lockUnreleased = com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.value
            if (lockUnreleased) {
                val status = com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(nextEpisode)
                if (status.isUnreleased) {
                    updateState {
                        copy(
                            phase = PlayerPhase.Idle,
                            countdownToNextEpisode = null,
                        )
                    }
                    sendEffect(PlayerUiEffect.ShowToast("Next episode is unreleased (${status.statusBadgeText ?: "Upcoming"})"))
                    return
                }
            }
            loadEpisode(nextEpisode)
        } else {
            updateState {
                copy(
                    phase = PlayerPhase.Idle,
                    countdownToNextEpisode = null,
                )
            }
            sendEffect(PlayerUiEffect.ShowToast("No next episode found"))
        }
    }

    private fun loadPrevEpisode() {
        linkRetries.clear()
        val episodes = uiState.value.episodes
        val currentData = uiState.value.launchData ?: return
        val currentEpId = currentData.history.episodeId
        var currentIndex = episodes.indexOfFirst { it.data == currentEpId }

        if (currentIndex == -1) {
            val currentEpNum = currentData.history.episode
            val currentSeasonNum = currentData.history.season
            if (currentEpNum != null) {
                currentIndex = episodes.indexOfFirst { it.episode == currentEpNum && (currentSeasonNum == null || it.season == currentSeasonNum) }
            }
        }

        val prevEpisode = if (currentIndex > 0) episodes[currentIndex - 1] else null

        if (prevEpisode != null) {
            loadEpisode(prevEpisode)
        } else {
            updateState {
                copy(
                    phase = PlayerPhase.Idle,
                    countdownToNextEpisode = null,
                )
            }
            sendEffect(PlayerUiEffect.ShowToast("No previous episode found"))
        }
    }

    private fun cancelScraping() {
        loadLinksJob?.cancel()

        val currentLinks = uiState.value.nextEpisodeLinks.ifEmpty { uiState.value.launchData?.links ?: emptyList() }
        val current = uiState.value.launchData
        val startPos = current?.startPositionMs ?: 0L
        val sortedLinks = sortLinks(currentLinks, startPos)
        val best = pickBestActiveLink(sortedLinks, uiState.value.failedLinks.keys, startPos)

        if (best != null && current != null) {
            val epData = uiState.value.targetEpisodeData
            val pastHistory = if (epData != null) {
                DesktopDataStore.getEpisodeWatched(
                    parentId = current.history.parentId,
                    episodeId = epData.data,
                )
            } else {
                null
            }
            val resumeStartPos = if (pastHistory != null && pastHistory.duration > 0 && pastHistory.position < pastHistory.duration - 15) {
                pastHistory.position * 1000L
            } else {
                startPos
            }

            val newHistory = if (epData != null) {
                current.history.copy(
                    episodeId = epData.data,
                    episode = epData.episode,
                    season = epData.season,
                    position = resumeStartPos / 1000L,
                    duration = pastHistory?.duration ?: 0L,
                )
            } else {
                current.history
            }

            val newLaunchData = current.copy(
                links = sortedLinks,
                subtitles = uiState.value.nextEpisodeSubtitles.ifEmpty { current.subtitles },
                history = newHistory,
                initialIndex = sortedLinks.indexOfFirst { it.url == best.url }.coerceAtLeast(0),
                startPositionMs = resumeStartPos,
                title = if (epData != null) {
                    buildString {
                        append(newHistory.showName)
                        if (newHistory.season != null && newHistory.episode != null) {
                            append(" - S${newHistory.season}E${newHistory.episode}")
                        } else if (newHistory.episode != null) {
                            append(" - E${newHistory.episode}")
                        }
                    }
                } else current.title,
            )

            updateState {
                copy(
                    launchData = newLaunchData,
                    nextEpisodeLinks = sortedLinks,
                    targetEpisodeData = null,
                    nextEpisodeError = null,
                )
            }
            updatePhase(PlayerPhase.Probing(best, stillScraping = false))
        } else {
            updateState {
                val newPhase = when (val p = phase) {
                    is PlayerPhase.Scraping -> PlayerPhase.Idle
                    is PlayerPhase.Probing -> p.copy(stillScraping = false)
                    is PlayerPhase.Playing -> p.copy(stillScraping = false)
                    else -> p
                }
                copy(phase = newPhase)
            }
        }
    }

    private fun sortLinks(
        links: List<ExtractorLink>,
        startPositionMs: Long = 0L,
    ): List<ExtractorLink> {
        return com.lagradost.cloudstream3.desktop.player.QualityDataHelper.sortLinks(links)
    }

    private suspend fun scrapeAndPlay(
        provider: MainAPI,
        targetEpisodeId: String,
        baseLaunchData: VideoLaunchData,
        targetEpisodeData: Episode? = null,
    ) {
        val hasStartedPlaying = AtomicBoolean(false)

        // Fetch DB data outside of the callbacks and StateFlow CAS loops!
        val current = uiState.value.launchData ?: baseLaunchData
        val pastHistory = if (targetEpisodeData != null) {
            DesktopDataStore.getEpisodeWatched(
                parentId = current.history.parentId,
                episodeId = targetEpisodeData.data,
            )
        } else {
            null
        }

        val startPos = if (pastHistory != null && pastHistory.duration > 0 && pastHistory.position < pastHistory.duration - 15) {
            pastHistory.position * 1000L
        } else {
            0L
        }

        val newHistory = if (targetEpisodeData != null) {
            current.history.copy(
                episodeId = targetEpisodeData.data,
                episode = targetEpisodeData.episode,
                season = targetEpisodeData.season,
            )
        } else {
            null
        }

        val autoPlay = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true

        val cached = LinkCache.get(targetEpisodeId)
        if (cached != null && cached.links.isNotEmpty()) {
            AppLogger.i("EmbeddedPlayerViewModel:${provider.name}", "Using cached links for episode: $targetEpisodeId")
            val sortedLinks = sortLinks(cached.links, startPos)

            val newLaunchData = if (targetEpisodeData != null && newHistory != null) {
                current.copy(
                    links = sortedLinks,
                    subtitles = cached.subtitles,
                    history = newHistory.copy(position = startPos / 1000L, duration = pastHistory?.duration ?: 0L),
                    initialIndex = 0,
                    startPositionMs = startPos,
                    title = buildString {
                        append(newHistory.showName)
                        if (newHistory.season != null && newHistory.episode != null) {
                            append(" - S${newHistory.season}E${newHistory.episode}")
                        } else if (newHistory.episode != null) {
                            append(" - E${newHistory.episode}")
                        }
                    },
                )
            } else {
                current.copy(
                    links = sortedLinks,
                    subtitles = cached.subtitles,
                    initialIndex = 0,
                )
            }

            val bestLink = pickBestActiveLink(sortedLinks, emptySet(), startPos)
            AppLogger.i("EmbeddedPlayerViewModel:${provider.name}", "Cache hit — picked link: ${bestLink?.url?.take(60)}")
            updateState {
                copy(
                    nextEpisodeLinks = sortedLinks,
                    nextEpisodeSubtitles = cached.subtitles,
                    launchData = newLaunchData,
                    targetEpisodeData = null,
                    nextEpisodeError = null,
                    failedLinks = emptyMap(),
                )
            }
            if (bestLink != null) {
                updatePhase(PlayerPhase.Probing(bestLink, false))
            } else {
                updatePhase(PlayerPhase.Idle)
            }
            return
        }

        val prefQuality = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"

        val sharedSubtitleCallback = SafePluginInvoker.wrapCallback("SubtitleCallback") { sub: SubtitleFile ->
            val cleanUrl = sub.url.trim()
            if (cleanUrl.isNotBlank()) {
                val cleanSub = sub.copy(url = cleanUrl, lang = sub.lang.trim())
                AppLogger.i("Plugin:${provider.name}", "Extracted subtitle: [${cleanSub.lang}] ${cleanSub.url}")
                updateState {
                    val newSubs = (nextEpisodeSubtitles + cleanSub).distinctBy { it.url.trim().lowercase() }
                    if (!hasStartedPlaying.get()) {
                        copy(nextEpisodeSubtitles = newSubs)
                    } else {
                        val cur = launchData
                        val updatedLaunch = if (cur != null && cur.history.episodeId == targetEpisodeId) {
                            cur.copy(subtitles = (cur.subtitles + cleanSub).distinctBy { it.url.trim().lowercase() })
                        } else {
                            cur
                        }
                        copy(nextEpisodeSubtitles = newSubs, launchData = updatedLaunch)
                    }
                }
            }
        }

        val sharedLinkCallback = SafePluginInvoker.wrapCallback("LinkCallback") { link: ExtractorLink ->
            AppLogger.i("Plugin:${provider.name}", "Extracted link: ${link.name} (quality=${link.quality}) -> ${link.url}")
            com.lagradost.cloudstream3.desktop.player.QualityDataHelper.registerDiscoveredSource(link.source)

            // Probe range seekability for non-HLS/DASH streams in background
            if (!link.isM3u8 && !link.isDash && link.type != com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 && link.type != com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH) {
                viewModelScope.launch(Dispatchers.IO) {
                    val isSeekable = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.probeRangeSeekability(link)
                    if (isSeekable) {
                        updateState {
                            if (!isScrapingLinks) return@updateState this
                            val reSorted = sortLinks(nextEpisodeLinks, startPos)
                            val curLaunch = launchData
                            val updated = if (curLaunch != null && curLaunch.history.episodeId == targetEpisodeId) {
                                curLaunch.copy(links = reSorted)
                            } else curLaunch
                            copy(nextEpisodeLinks = reSorted, launchData = updated)
                        }
                    }
                }
            }

            updateState {
                if (!isScrapingLinks) {
                    return@updateState this
                }
                val newLinks = sortLinks(nextEpisodeLinks + link, startPos)
                val currentLaunch = launchData
                val updatedLaunch = if (currentLaunch != null && currentLaunch.history.episodeId == targetEpisodeId) {
                    currentLaunch.copy(links = newLinks)
                } else {
                    currentLaunch
                }

                copy(
                    nextEpisodeLinks = newLinks,
                    launchData = updatedLaunch,
                )
            }
        }

        // Concurrently query installed Stremio stream addons
        val epHistoryId = current.history.episodeId
        val loadResp = current.loadResponse
        val resolvedImdbId = when {
            targetEpisodeId.startsWith("tt", ignoreCase = true) -> targetEpisodeId.substringBefore(":")
            epHistoryId?.startsWith("tt", ignoreCase = true) == true -> epHistoryId.substringBefore(":")
            loadResp?.syncData?.get("imdb")?.startsWith("tt", ignoreCase = true) == true -> loadResp.syncData["imdb"]
            loadResp?.url?.startsWith("tt", ignoreCase = true) == true -> loadResp.url.substringBefore(":")
            else -> com.lagradost.cloudstream3.desktop.metadata.MetadataPipeline.getCachedImdbId(current.history.showName)
        }

        val epNumber = current.history.episode ?: targetEpisodeData?.episode
        val seasonNumber = current.history.season ?: targetEpisodeData?.season
        viewModelScope.launch(Dispatchers.IO) {
            StremioAddonManager.searchStreams(
                imdbId = resolvedImdbId,
                season = seasonNumber,
                episode = epNumber,
                title = current.history.showName,
                onLink = { link -> sharedLinkCallback(link) },
            )
        }

        val result = SafePluginInvoker.invoke(
            tag = "EmbeddedPlayerViewModel:${provider.name}",
            providerName = provider.name,
            timeoutMs = SafePluginInvoker.TIMEOUT_SCRAPE_MS,
            // Timeout on scraping is expected — links stream via callback and may already
            // be in UI. A slow/dead extractor should not trip the circuit breaker.
            penalizeOnTimeout = false,
        ) {
            AppLogger.i("Plugin:${provider.name}", "Scraping streams for episode: $targetEpisodeId (autoPlay=$autoPlay)")
            provider.loadLinks(
                data = targetEpisodeId,
                isCasting = false,
                subtitleCallback = sharedSubtitleCallback,
                callback = sharedLinkCallback,
            )
        }

        if (result.isSuccess) {
            var bestLinkToProbe: ExtractorLink? = null
            updateState {
                val sortedLinks = sortLinks(nextEpisodeLinks, startPos)

                if (!hasStartedPlaying.get()) {
                    if (nextEpisodeLinks.isNotEmpty()) {
                        hasStartedPlaying.set(true)
                        val best = pickBestActiveLink(sortedLinks, emptySet(), startPos)
                        if (best != null) {
                            bestLinkToProbe = best
                            val launch = if (targetEpisodeData != null && newHistory != null) {
                                current.copy(
                                    links = sortedLinks,
                                    subtitles = nextEpisodeSubtitles,
                                    history = newHistory,
                                    initialIndex = 0,
                                    startPositionMs = startPos,
                                    title = buildString {
                                        append(newHistory.showName)
                                        if (newHistory.season != null && newHistory.episode != null) {
                                            append(" - S${newHistory.season}E${newHistory.episode}")
                                        } else if (newHistory.episode != null) {
                                            append(" - E${newHistory.episode}")
                                        }
                                    },
                                )
                            } else {
                                current.copy(
                                    links = sortedLinks,
                                    subtitles = nextEpisodeSubtitles,
                                    initialIndex = 0,
                                )
                            }
                            LinkCache.set(targetEpisodeId, sortedLinks, nextEpisodeSubtitles)
                            copy(
                                nextEpisodeLinks = sortedLinks,
                                launchData = launch,
                                targetEpisodeData = null,
                                nextEpisodeError = null,
                            )
                        } else {
                            copy(
                                phase = PlayerPhase.Idle,
                                targetEpisodeData = null,
                                nextEpisodeError = PlayerError.ExtractorError(
                                    pluginName = provider.name,
                                    message = "No playable links found.",
                                ),
                            )
                        }
                    } else {
                        copy(
                            phase = PlayerPhase.Idle,
                            targetEpisodeData = null,
                            nextEpisodeError = PlayerError.ExtractorError(
                                pluginName = provider.name,
                                message = "No streams discovered.",
                            ),
                        )
                    }
                } else {
                    this
                }
            }

            val linkToProbe = bestLinkToProbe
            if (linkToProbe != null) {
                updatePhase(PlayerPhase.Probing(linkToProbe, false))
            }
        } else {
            val ex = result.exceptionOrNull()
            LinkCache.remove(targetEpisodeId)

            val isJsonParseError = ex is com.fasterxml.jackson.core.JsonParseException ||
                ex?.message?.contains("Unrecognized token") == true ||
                ex?.cause is com.fasterxml.jackson.core.JsonParseException

            val showUrl = current.loadResponse?.url ?: current.history.showUrl
            if (isJsonParseError && targetEpisodeId.startsWith("http")) {
                AppLogger.w("Plugin:${provider.name}", "Detected invalid episode data payload ($targetEpisodeId). Performing automatic self-healing re-fetch from $showUrl...")
                val parentId = DesktopDataStore.watchHistoryId(provider.name, showUrl)
                DesktopDataStore.removeEpisodeWatched(parentId, targetEpisodeId)
                try {
                    val freshResp = SafePluginInvoker.invokeOrNull(
                        tag = "SelfHealing:${provider.name}",
                        timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                    ) {
                        provider.load(showUrl)
                    }
                    if (freshResp is MovieLoadResponse && freshResp.dataUrl.isNotBlank() && freshResp.dataUrl != targetEpisodeId) {
                        val freshDataUrl = freshResp.dataUrl
                        AppLogger.i("Plugin:${provider.name}", "Self-healing resolved valid movie data payload. Retrying scraping...")
                        val newTargetEp = provider.newEpisode(freshDataUrl) {
                            this.name = freshResp.name
                            this.posterUrl = freshResp.posterUrl
                        }
                        val updatedLaunch = current.copy(
                            loadResponse = freshResp,
                            history = current.history.copy(episodeId = freshDataUrl),
                        )
                        scrapeAndPlay(provider, freshDataUrl, updatedLaunch, newTargetEp)
                        return
                    }
                } catch (healEx: Throwable) {
                    AppLogger.e("Plugin:${provider.name}", "Self-healing re-fetch failed: ${healEx.message}")
                }
            }

            var bestFallbackToProbe: ExtractorLink? = null
            updateState {
                if (nextEpisodeLinks.isNotEmpty()) {
                    if (!hasStartedPlaying.get()) {
                        hasStartedPlaying.set(true)
                        val sortedLinks = sortLinks(nextEpisodeLinks, startPos)
                        val best = pickBestActiveLink(sortedLinks, emptySet(), startPos)
                        bestFallbackToProbe = best
                        val launch = if (targetEpisodeData != null && newHistory != null) {
                            current.copy(
                                links = sortedLinks,
                                subtitles = nextEpisodeSubtitles,
                                history = newHistory,
                                initialIndex = 0,
                                startPositionMs = startPos,
                                title = buildString {
                                    append(newHistory.showName)
                                    if (newHistory.season != null && newHistory.episode != null) {
                                        append(" - S${newHistory.season}E${newHistory.episode}")
                                    } else if (newHistory.episode != null) {
                                        append(" - E${newHistory.episode}")
                                    }
                                },
                            )
                        } else {
                            current.copy(
                                links = sortedLinks,
                                subtitles = nextEpisodeSubtitles,
                                initialIndex = 0,
                            )
                        }
                        LinkCache.set(targetEpisodeId, sortedLinks, nextEpisodeSubtitles)
                        copy(
                            nextEpisodeLinks = sortedLinks,
                            launchData = launch,
                            targetEpisodeData = null,
                            nextEpisodeError = null,
                        )
                    } else {
                        copy(
                            phase = PlayerPhase.Idle,
                            targetEpisodeData = null,
                            nextEpisodeError = PlayerError.ExtractorError(
                                pluginName = provider.name,
                                message = "Failed to load links: ${ex?.message ?: "Unknown error"}",
                                cause = ex,
                            ),
                        )
                    }
                } else if (hasStartedPlaying.get()) {
                    AppLogger.d("Plugin:${provider.name}", "Scrape timed out but playback already started — suppressing error")
                    val sortedLinks = sortLinks(nextEpisodeLinks, startPos)
                    val newLaunchData = launchData?.copy(links = sortedLinks)
                    val newPhase = when (val p = phase) {
                        is PlayerPhase.Scraping -> PlayerPhase.Idle
                        is PlayerPhase.Probing -> p.copy(stillScraping = false)
                        is PlayerPhase.Playing -> p.copy(stillScraping = false)
                        else -> p
                    }
                    copy(
                        phase = newPhase,
                        nextEpisodeLinks = sortedLinks,
                        launchData = newLaunchData,
                    )
                } else {
                    AppLogger.e("Plugin:${provider.name}", "Failed to load links: ${ex?.message}", ex)
                    copy(
                        phase = PlayerPhase.Idle,
                        targetEpisodeData = null,
                        nextEpisodeError = PlayerError.ExtractorError(
                            pluginName = provider.name,
                            message = "Failed to load links: ${ex?.message ?: "Unknown error"}",
                            cause = ex,
                        ),
                    )
                }
            }

            val fallbackToProbe = bestFallbackToProbe
            if (fallbackToProbe != null) {
                updatePhase(PlayerPhase.Probing(fallbackToProbe, false))
            }
        }
    }
}
