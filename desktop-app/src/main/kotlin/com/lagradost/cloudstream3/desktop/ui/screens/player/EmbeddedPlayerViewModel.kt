package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerError
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiState
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedPlayerViewModel : BaseMviViewModel<PlayerUiState, PlayerUiEvent, PlayerUiEffect>(
    initialState = PlayerUiState(),
) {
    val playerState = PlayerState()
    private var loadLinksJob: Job? = null
    private var saveJob: Job? = null
    private var scrapeJob: Job? = null
    private var timeoutJob: Job? = null
    private var countdownJob: Job? = null

    private val linkRetries = mutableMapOf<String, Int>()
    private val MAX_RETRIES = 2

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
                if (kotlin.math.abs(currentPosSec - lastSavedPositionSec) >= 5) {
                    lastSavedPositionSec = currentPosSec
                    val currentData = uiState.value.launchData ?: return@collect
                    val updatedHistory = currentData.history.copy(
                        position = currentPosSec,
                        duration = playerState.durationMs.value / 1000L,
                        updateTime = System.currentTimeMillis()
                    )
                    savePosition(updatedHistory)
                }
            }
        }
    }

    override fun dispose() {
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
                updateTime = System.currentTimeMillis()
            )
            DesktopDataStore.setLastWatched(updatedHistory)
        }
        playerState.detachMpv()
        
        super.dispose()
        loadLinksJob?.cancel()
        saveJob?.cancel()
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
            is PlayerUiEvent.OnPlaybackError -> handlePlaybackError(event.failedUrl)
            is PlayerUiEvent.OnLinkChange -> handleLinkChange(event.url)
            is PlayerUiEvent.OnPlaybackReady -> handlePlaybackReady()
            is PlayerUiEvent.OnPlaybackFinished -> handlePlaybackFinished()
            is PlayerUiEvent.OnCancelCountdown -> cancelCountdown()
        }
    }

    // Picks the best available link from the current launchData that hasn't failed.
    // This is the single decision point — replaces all the inline logic that was in the UI.
    private fun pickBestActiveLink(
        links: List<ExtractorLink>,
        failed: Set<String>,
        startPositionMs: Long,
    ): ExtractorLink? {
        val prefQuality = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
        val targetQualityInt: Int? = when (prefQuality) {
            "2160p (4K)" -> com.lagradost.cloudstream3.utils.Qualities.P2160.value
            "1080p" -> com.lagradost.cloudstream3.utils.Qualities.P1080.value
            "720p" -> com.lagradost.cloudstream3.utils.Qualities.P720.value
            "480p", "480p / SD" -> com.lagradost.cloudstream3.utils.Qualities.P480.value
            else -> null
        }
        val isResuming = startPositionMs > 0
        val available = links.filter { it.url !in failed }
        return available.minByOrNull { link ->
            val qualityDelta = if (targetQualityInt != null) {
                kotlin.math.abs(link.quality - targetQualityInt)
            } else {
                -link.quality
            }
            val seekPenalty = if (isResuming) {
                when {
                    link.isM3u8 || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 -> 1
                    link.isDash || link.type == com.lagradost.cloudstream3.utils.ExtractorLinkType.DASH -> 2
                    else -> 0
                }
            } else { 0 }
            seekPenalty * 10_000 + qualityDelta
        }
    }

    private fun updatePhase(phase: PlayerPhase, newFailedLinks: Set<String>? = null) {
        countdownJob?.cancel()
        
        // Timeout job is ONLY scheduled when we enter Probing phase.
        if (phase is PlayerPhase.Probing) {
            val isNewProbing = uiState.value.phase !is PlayerPhase.Probing || 
                               (uiState.value.phase as? PlayerPhase.Probing)?.link?.url != phase.link.url ||
                               phase.isRetry
            if (isNewProbing) {
                timeoutJob?.cancel()
                val timedOutUrl = phase.link.url
                timeoutJob = viewModelScope.launch {
                    delay(20_000)
                    if (uiState.value.phase is PlayerPhase.Probing) {
                        AppLogger.w("EmbeddedPlayerViewModel", "Link timed out after 20s, advancing: ${timedOutUrl.take(80)}")
                        handleEvent(PlayerUiEvent.OnPlaybackError(timedOutUrl))
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
                failedLinks = newFailedLinks ?: failedLinks
            )
        }
    }

    private fun handlePlaybackReady() {
        timeoutJob?.cancel()
        updateState {
            val currentPhase = phase
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
        updateState { copy(countdownToNextEpisode = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            val timeoutStr = DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "15000"
            var ticks = (timeoutStr.toLongOrNull() ?: 15000L) / 1000L
            
            while (ticks > 0) {
                updateState { copy(countdownToNextEpisode = ticks.toInt()) }
                delay(1000)
                ticks--
            }
            updateState { copy(countdownToNextEpisode = null) }
            handleEvent(PlayerUiEvent.OnLoadNextEpisode)
        }
    }

    private fun handlePlaybackError(failedUrl: String) {
        val currentState = uiState.value
        val retries = linkRetries.getOrDefault(failedUrl, 0)
        
        if (retries < MAX_RETRIES) {
            linkRetries[failedUrl] = retries + 1
            AppLogger.w("EmbeddedPlayerViewModel", "Stream error. Retrying same link (${retries + 1}/$MAX_RETRIES): $failedUrl")
            
            val currentLink = currentState.launchData?.links?.find { it.url == failedUrl }
            if (currentLink != null) {
                val currentPos = playerState.positionMs.value
                val startPos = if (currentPos > 0L) currentPos else (currentState.launchData?.startPositionMs ?: 0L)
                updateState {
                    copy(launchData = launchData?.copy(startPositionMs = startPos))
                }
                updatePhase(PlayerPhase.Probing(currentLink, currentState.isScrapingLinks, isInitial = false, isRetry = true), currentState.failedLinks)
                return
            }
        }

        AppLogger.e("EmbeddedPlayerViewModel", "Playback error exhausted retries on: $failedUrl")
        val newFailed = currentState.failedLinks + failedUrl
        val links = currentState.nextEpisodeLinks.ifEmpty { currentState.launchData?.links ?: emptyList() }
        val currentPos = playerState.positionMs.value
        val startPos = if (currentPos > 0L) currentPos else (currentState.launchData?.startPositionMs ?: 0L)
        val next = pickBestActiveLink(links, newFailed, startPos)
        
        if (next != null) {
            updateState {
                copy(launchData = launchData?.copy(startPositionMs = startPos))
            }
            updatePhase(PlayerPhase.Probing(next, currentState.isScrapingLinks, isInitial = false), newFailed)
        } else if (currentState.isScrapingLinks) {
            // Still scraping, just wait.
            updatePhase(PlayerPhase.Scraping, newFailed)
        } else {
            // All links exhausted and scraping is done — surface the error
            AppLogger.e("EmbeddedPlayerViewModel", "All sources exhausted.")
            updatePhase(PlayerPhase.Idle, newFailed)
            sendEffect(PlayerUiEffect.ShowError("All sources failed."))
            sendEffect(PlayerUiEffect.ClosePlayer)
        }
    }

    private fun handleLinkChange(url: String) {
        linkRetries.clear()
        val currentState = uiState.value
        val link = currentState.launchData?.links?.find { it.url == url }
        if (link != null) {
            val currentPos = playerState.positionMs.value
            val startPos = if (currentPos > 0L) currentPos else (currentState.launchData?.startPositionMs ?: 0L)
            updateState {
                copy(launchData = launchData?.copy(startPositionMs = startPos))
            }
            updatePhase(PlayerPhase.Probing(link, currentState.isScrapingLinks, isInitial = false), emptySet())
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
                            )
                            DesktopDataStore.setLastWatched(nextEpHistory)
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
        if (uiState.value.launchData == null) {
            val isFinished = initialData.history.duration > 0 && initialData.history.position >= initialData.history.duration - 15
            val adjustedData = if (isFinished) {
                initialData.copy(
                    startPositionMs = 0L,
                    history = initialData.history.copy(position = 0L),
                )
            } else {
                initialData
            }
            updateState { copy(launchData = adjustedData, phase = PlayerPhase.Idle, failedLinks = emptySet()) }

            // If launched from history without full metadata, fetch it in the background
            // This is required to populate the episode list so "Auto Next" and the Episodes panel work!
            if (adjustedData.loadResponse == null) {
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val apiName = adjustedData.history.apiName
                        val provider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(apiName ?: "")
                        if (provider != null) {
                            val res = provider.load(adjustedData.history.showUrl)
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
                    } catch (e: Exception) {
                        com.lagradost.common.logging.AppLogger.w("EmbeddedPlayerViewModel", "Failed to fetch metadata for history launch: ${e.message}")
                    }
                }
            }

            // Auto-scrape initial episode if links are empty
            if (adjustedData.links.isEmpty() && adjustedData.history.episodeId != null) {
                val apiName = adjustedData.loadResponse?.apiName ?: adjustedData.history.apiName
                val provider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(apiName ?: "")
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
    }

    private fun loadEpisode(episode: Episode) {
        linkRetries.clear()
        val currentData = uiState.value.launchData ?: return

        countdownJob?.cancel()
        loadLinksJob?.cancel()
        updateState {
            copy(
                phase = PlayerPhase.Scraping,
                nextEpisodeError = null,
                nextEpisodeLinks = emptyList(),
                nextEpisodeSubtitles = emptyList(),
                targetEpisodeData = episode,
                failedLinks = emptySet(),
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

        val best = pickBestActiveLink(currentLinks, uiState.value.failedLinks, startPos)

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
            } else null
        } else null

        if (nextEpisode != null) {
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
        // Safe to cancel on Desktop: Duktape JNI handles thread interrupts safely here.
        // Uncommented to aggressively halt background network traffic.
        loadLinksJob?.cancel()
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

    private fun sortLinks(links: List<ExtractorLink>, preferredQuality: String): List<ExtractorLink> {
        if (preferredQuality == "Auto" || preferredQuality == "Auto / Highest" || preferredQuality == "Highest Available") {
            return links.sortedByDescending { it.quality }
        }

        val targetQuality = when (preferredQuality) {
            "2160p (4K)" -> Qualities.P2160.value
            "1080p" -> Qualities.P1080.value
            "720p" -> Qualities.P720.value
            "480p", "480p / SD" -> Qualities.P480.value
            else -> Qualities.Unknown.value
        }

        return links.sortedWith(
            compareByDescending<ExtractorLink> {
                it.quality == targetQuality
            }.thenByDescending {
                it.quality
            },
        )
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
                position = startPos / 1000L,
                duration = pastHistory?.duration ?: 0L,
            )
        } else {
            null
        }

        val cached = LinkCache.get(targetEpisodeId)
        if (cached != null) {
            AppLogger.i("EmbeddedPlayerViewModel:${provider.name}", "Using cached links for episode: $targetEpisodeId")

            val prefQuality = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
            val sortedLinks = sortLinks(cached.links, prefQuality)

            val newLaunchData = if (targetEpisodeData != null && newHistory != null) {
                current.copy(
                    links = sortedLinks,
                    subtitles = cached.subtitles,
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
                    failedLinks = emptySet(),
                )
            }
            if (bestLink != null) {
                updatePhase(PlayerPhase.Probing(bestLink, false))
            } else {
                updatePhase(PlayerPhase.Idle)
            }
            return
        }

        val result = SafePluginInvoker.invoke(
            tag = "EmbeddedPlayerViewModel:${provider.name}",
            providerName = provider.name,
            timeoutMs = SafePluginInvoker.TIMEOUT_SCRAPE_MS,
            // Timeout on scraping is expected — links stream via callback and may already
            // be in UI. A slow/dead extractor should not trip the circuit breaker.
            penalizeOnTimeout = false,
        ) {
            AppLogger.i("Plugin:${provider.name}", "Scraping streams for episode: $targetEpisodeId")
            provider.loadLinks(
                data = targetEpisodeId,
                isCasting = false,
                subtitleCallback = SafePluginInvoker.wrapCallback("SubtitleCallback") { sub ->
                    AppLogger.i("Plugin:${provider.name}", "Extracted subtitle: [${sub.lang}] ${sub.url}")
                    updateState {
                        val newSubs = nextEpisodeSubtitles + sub
                        if (!hasStartedPlaying.get()) {
                            copy(nextEpisodeSubtitles = newSubs)
                        } else {
                            val current = launchData
                            val updatedLaunch = if (current != null && current.history.episodeId == targetEpisodeId) {
                                current.copy(subtitles = current.subtitles + sub)
                            } else {
                                current
                            }
                            copy(nextEpisodeSubtitles = newSubs, launchData = updatedLaunch)
                        }
                    }
                },
                callback = SafePluginInvoker.wrapCallback("LinkCallback") { link ->
                    AppLogger.i("Plugin:${provider.name}", "Extracted link: ${link.name} (quality=${link.quality}) -> ${link.url}")
                    var bestLinkFound: ExtractorLink? = null
                    var shouldUpdatePhase = false

                    updateState {
                        if (!isScrapingLinks) {
                            return@updateState this
                        }
                        val newLinks = nextEpisodeLinks + link
                        if (hasStartedPlaying.compareAndSet(false, true)) {
                            val newLaunchData = if (targetEpisodeData != null && newHistory != null) {
                                current.copy(
                                    links = newLinks,
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
                                    links = newLinks,
                                    subtitles = nextEpisodeSubtitles,
                                    initialIndex = 0,
                                )
                            }
                            
                            shouldUpdatePhase = true
                            bestLinkFound = pickBestActiveLink(newLinks, failedLinks, startPos)

                            copy(
                                nextEpisodeLinks = newLinks,
                                launchData = newLaunchData,
                                targetEpisodeData = null,
                                nextEpisodeError = null,
                            )
                        } else {
                            val currentLaunch = launchData
                            val updatedLaunch = if (currentLaunch != null && currentLaunch.history.episodeId == targetEpisodeId) {
                                currentLaunch.copy(links = currentLaunch.links + link)
                            } else {
                                currentLaunch
                            }
                            
                            if (activeLink == null) {
                                shouldUpdatePhase = true
                                bestLinkFound = pickBestActiveLink(newLinks, failedLinks, startPos)
                            }
                            
                            copy(
                                nextEpisodeLinks = newLinks,
                                launchData = updatedLaunch,
                            )
                        }
                    }
                    
                    if (shouldUpdatePhase && bestLinkFound != null) {
                        updatePhase(PlayerPhase.Probing(bestLinkFound!!, true))
                    }
                },
            )
        }

        if (result.isSuccess) {
            updateState {
                val prefQuality = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
                val sortedLinks = sortLinks(nextEpisodeLinks, prefQuality)
                val newLaunchData = launchData?.copy(links = sortedLinks)

                if (!hasStartedPlaying.get()) {
                    copy(
                        phase = PlayerPhase.Idle,
                        targetEpisodeData = null,
                        nextEpisodeError = PlayerError.ExtractorError(
                            pluginName = provider.name,
                            message = "No links found for this episode.",
                        ),
                    )
                } else {
                    LinkCache.set(targetEpisodeId, sortedLinks, nextEpisodeSubtitles)
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
                }
            }
        } else {
            val ex = result.exceptionOrNull()
            if (ex is CancellationException) {
                throw ex
            }
            // If links were already delivered via callback and playback has started,
            // the timeout fired after we already got what we needed — not an error.
            if (hasStartedPlaying.get()) {
                AppLogger.d("Plugin:${provider.name}", "Scrape timed out but playback already started — suppressing error")
                updateState {
                    val prefQuality = DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
                    val sortedLinks = sortLinks(nextEpisodeLinks, prefQuality)
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
                }
            } else {
                AppLogger.e("Plugin:${provider.name}", "Failed to load links: ${ex?.message}", ex)
                updateState {
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
        }
    }
}
