package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxLibraryCache
import com.lagradost.cloudstream3.desktop.domain.player.interactor.SavePlaybackProgress
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerPhase
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiState
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
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
    private var scrapeGraceJob: Job? = null
    private val scraper = PlayerStreamScraper(viewModelScope)

    private val linkRetries = mutableMapOf<String, Int>()
    private val hasShownNowPlayingToast = AtomicBoolean(false)
    private var lastSavedPositionSec = 0L
    companion object {
        private const val MAX_PROBE_RETRIES = 0
        private const val MAX_PLAYING_RETRIES = 2
    }

    init {
        PlayerDiagnosticsHolder.register(playerState)

        viewModelScope.launch(Dispatchers.IO) {
            val autoPlay = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true
            updateState { copy(autoPlayEnabled = autoPlay) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            playerState.positionMs.collect { posMs ->
                val currentPosSec = posMs / 1000L
                val durSec = playerState.durationMs.value / 1000L
                val isPaused = playerState.isPaused.value
                val currentData = uiState.value.launchData

                if (currentPosSec <= 0L && lastSavedPositionSec > 0L) {
                    // Ignore position reset ticks during episode switches or seek-to-zero so they do not overwrite saved progress with 0
                    return@collect
                }

                if (kotlin.math.abs(currentPosSec - lastSavedPositionSec) >= 5) {
                    lastSavedPositionSec = currentPosSec
                    if (currentData != null && durSec > 0L) {
                        val pct = currentPosSec.toFloat() / durSec.toFloat()
                        val updatedHistory = currentData.history.copy(
                            position = if (pct >= 0.85f) durSec else currentPosSec,
                            duration = durSec,
                            updateTime = System.currentTimeMillis(),
                        )
                        savePosition(updatedHistory, forceNotify = pct >= 0.85f)
                    }
                }

                val percentage = if (durSec > 0) currentPosSec.toFloat() / durSec.toFloat() else 0f
                if (percentage >= 0.88f && uiState.value.hasNextEpisode && uiState.value.autoPlayEnabled) {
                    triggerBackgroundPreScrape()
                } else if (percentage < 0.85f && scraper.isPreScrapeActive) {
                    scraper.cancelPreScrape()
                }

                DiscordRpcCoordinator.updatePlaying(
                    launchData = currentData,
                    positionSeconds = currentPosSec,
                    durationSeconds = durSec,
                    isPaused = isPaused,
                )
            }
        }

        DiscordRpcCoordinator.attachPauseObserver(
            scope = viewModelScope,
            playerState = playerState,
            getLaunchData = { uiState.value.launchData },
        )

        viewModelScope.launch(Dispatchers.IO) {
            playerState.isPaused.collect { paused ->
                if (paused) {
                    val currentData = uiState.value.launchData ?: return@collect
                    val currentPosSec = playerState.positionMs.value / 1000L
                    val durSec = playerState.durationMs.value / 1000L
                    if (currentPosSec > 0 && durSec > 0) {
                        val pct = currentPosSec.toFloat() / durSec.toFloat()
                        savePosition(
                            currentData.history.copy(
                                position = if (pct >= 0.85f) durSec else currentPosSec,
                                duration = durSec,
                                updateTime = System.currentTimeMillis(),
                            ),
                            forceNotify = true,
                        )
                    }
                }
            }
        }
    }

    override fun dispose() {
        scraper.cancelPreScrape()
        countdownJob?.cancel()
        timeoutJob?.cancel()
        com.lagradost.cloudstream3.desktop.discord.DiscordRpcManager.onPlayerStopped()
        PlayerDiagnosticsHolder.unregister(playerState)
        val currentData = uiState.value.launchData
        val currentDurSec = playerState.durationMs.value / 1000L
        val currentPosSec = playerState.positionMs.value / 1000L
        val showName = currentData?.history?.showName?.trim().orEmpty()
        val isValidName = showName.isNotBlank() && !showName.equals("Unknown Channel", ignoreCase = true) && !showName.equals("Unknown", ignoreCase = true)
        if (currentData != null && isValidName && currentDurSec > 0 && currentPosSec > 0) {
            val screenshotPath = "${com.lagradost.common.platform.PlatformPaths.appDataDir.absolutePath}/screenshots/history_${currentData.history.parentId}.jpg"
            val hasNextEpisode = uiState.value.hasNextEpisode
            val nextEpisodeData = uiState.value.nextEpisodeData
            val updatedHistory = currentData.history.copy(
                position = currentPosSec,
                duration = currentDurSec,
                screenshotUrl = "file:///$screenshotPath",
                updateTime = System.currentTimeMillis(),
            )
            com.lagradost.cloudstream3.desktop.utils.appScope.launch(Dispatchers.IO) {
                try {
                    java.io.File(screenshotPath).parentFile?.mkdirs()
                    playerState.takeScreenshot(screenshotPath)
                    WatchHistoryCoordinator.saveWithNextEpisodeQueue(
                        history = updatedHistory,
                        hasNextEpisode = hasNextEpisode,
                        nextEpisode = nextEpisodeData,
                        saveProgress = savePlaybackProgress,
                        forceNotify = true,
                    )
                } catch (e: Exception) {
                    AppLogger.e("EmbeddedPlayerViewModel", "Failed to save history or screenshot on dispose", e)
                }
            }
        }

        val currentLinksToCache = (uiState.value.nextEpisodeLinks.ifEmpty { currentData?.links.orEmpty() }).distinctBy { it.url }
        val currentSubsToCache = (uiState.value.nextEpisodeSubtitles.ifEmpty { currentData?.subtitles.orEmpty() }).distinctBy { it.url }
        if (currentData != null && currentLinksToCache.isNotEmpty()) {
            val epId = currentData.history.episodeId
            val showUrl = currentData.loadResponse?.url ?: currentData.history.showUrl
            val keys = buildList {
                epId?.takeIf { it.isNotBlank() }?.let { add(it) }
                if (isEmpty()) {
                    showUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                    currentData.history.parentId?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }.distinct()
            LinkCache.addLinks(keys, currentLinksToCache, currentSubsToCache)
        }

        playerState.detachMpv()

        super.dispose()
        loadLinksJob?.cancel()
        saveJob?.cancel()
        countdownJob?.cancel()
        timeoutJob?.cancel()
        scraper.cancelPreScrape()
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
            is PlayerUiEvent.OnRetryPlayback -> retryPlayback()
        }
    }

    private fun retryPlayback() {
        linkRetries.clear()
        val currentData = uiState.value.launchData ?: return
        val epId = currentData.history.episodeId
        if (epId != null) {
            LinkCache.remove(epId)
        }
        updateState {
            copy(
                failedLinks = emptyMap(),
                nextEpisodeError = null,
            )
        }
        val apiName = currentData.loadResponse?.apiName ?: currentData.history.apiName
        val provider = com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(apiName)
        val targetEp = currentData.episodes.find { it.data == epId }
        if (provider != null && epId != null) {
            updatePhase(PlayerPhase.Scraping, emptyMap())
            loadLinksJob?.cancel()
            loadLinksJob = viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                scrapeAndPlay(provider, epId, currentData, targetEp)
            }
        } else if (currentData.links.isNotEmpty()) {
            val best = pickBestActiveLink(currentData.links, emptySet(), currentData.startPositionMs)
            if (best != null) {
                updatePhase(PlayerPhase.Probing(best, false), emptyMap())
            } else {
                updatePhase(PlayerPhase.Idle, emptyMap())
            }
        }
    }

    // Picks the best available link from the current launchData that hasn't failed.
    // Uses Android-parity QualityDataHelper score engine.
    private fun pickBestActiveLink(
        links: List<ExtractorLink>,
        failed: Set<String>,
        startPositionMs: Long = 0L,
    ): ExtractorLink? {
        val p2pReady = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isP2pReady
        val available = links.filter { link ->
            link.url !in failed && (p2pReady || !com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isTorrentLink(link))
        }
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
                hasShownNowPlayingToast.set(false)
                timeoutJob?.cancel()
                val timedOutUrl = phase.link.url
                val timeoutStr = DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT) ?: "20000"
                val baseTimeoutMs = timeoutStr.toLongOrNull() ?: 20_000L
                val isP2p = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isTorrentLink(phase.link) || phase.link.url.contains("127.0.0.1:8091")
                val isLocalLoopback = (phase.link.url.contains("127.0.0.1") || phase.link.url.contains("localhost")) &&
                    !phase.link.url.contains("/proxy") && !phase.link.url.contains("/dash")
                val isProxied = phase.link.url.contains("/proxy") || phase.link.url.contains("/dash")
                val isTimeoutDisabled = baseTimeoutMs <= 0L && !phase.isRetry && !isP2p
                val timeoutMs = when {
                    phase.isRetry -> 3_500L
                    isP2p || isLocalLoopback -> 45_000L
                    isProxied -> if (baseTimeoutMs > 0L) maxOf(20_000L, baseTimeoutMs) else 0L
                    else -> baseTimeoutMs
                }
                if (!isTimeoutDisabled) timeoutJob = viewModelScope.launch {
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
            val links = launchData?.links.orEmpty()
            val newLaunch = if (phase is PlayerPhase.Probing) {
                launchData?.copy(initialIndex = links.indexOf(phase.link).coerceAtLeast(0))
            } else {
                launchData
            }
            copy(
                phase = phase,
                launchData = newLaunch,
                countdownToNextEpisode = null,
                failedLinks = newFailedLinks ?: failedLinks,
            )
        }
    }

    private fun handlePlaybackReady() {
        timeoutJob?.cancel()
        val currentPhase = uiState.value.phase
        if (currentPhase is PlayerPhase.Probing && !currentPhase.isInitial) {
            if (hasShownNowPlayingToast.compareAndSet(false, true)) {
                val qualStr = if (currentPhase.link.quality > 0) " (${com.lagradost.cloudstream3.desktop.player.QualityDataHelper.formatQuality(currentPhase.link.quality)})" else ""
                sendEffect(PlayerUiEffect.ShowToast("Now playing: ${currentPhase.link.name}$qualStr"))
            }
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
        val state = uiState.value
        val durSec = playerState.durationMs.value / 1000L
        val posSec = playerState.positionMs.value / 1000L
        val currentUrl = when (val p = state.phase) {
            is PlayerPhase.Playing -> p.link.url
            is PlayerPhase.Probing -> p.link.url
            else -> null
        }

        // Detect premature stream terminations (e.g. ad redirects or corrupt streams reaching EOF before playing)
        if (durSec <= 0L || (posSec < 5L && (durSec <= 0L || durSec > 60L))) {
            if (currentUrl != null) {
                val candidateLinks = state.nextEpisodeLinks.ifEmpty { state.launchData?.links ?: emptyList() }
                val remainingCandidates = candidateLinks.filter { it.url != currentUrl && it.url !in state.failedLinks }
                if (remainingCandidates.isNotEmpty()) {
                    AppLogger.w("EmbeddedPlayerViewModel", "Premature stream termination at ${posSec}s (dur=${durSec}s). Failing over to remaining candidate streams...")
                    handlePlaybackError(currentUrl, "Stream ended prematurely")
                    return
                }
            }
        }

        flushCurrentEpisodeProgress(markCompletedIfNearEnd = true)
        // Re-read the pref fresh so a mid-session toggle takes effect immediately.
        val autoPlay = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true
        val hasNext = state.hasNextEpisode
        val isOffline = state.launchData?.history?.apiName in listOf("Offline", "Local")
        // When loadResponse is null (history / deep-link launch), episodes is empty so
        // hasNextEpisode is always false. Fall back to the episode number as a heuristic —
        // if the current entry has an episode number it is a series episode and there may
        // be a next one. loadNextEpisode will surface a toast if nothing is found.
        val episodesUnknown = !isOffline && state.episodes.isEmpty() && state.launchData?.history?.episode != null
        if ((hasNext || episodesUnknown) && autoPlay) {
            startCountdown()
        }
    }

    private fun cancelCountdown() {
        countdownJob?.cancel()
        scraper.cancelPreScrape()
        updateState { copy(countdownToNextEpisode = null) }
    }

    private fun startCountdown() {
        countdownJob?.cancel()
        triggerBackgroundPreScrape()
        countdownJob = viewModelScope.launch {
            var ticks = 5L

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
        scraper.preScrapeNextEpisode(nextEp, state.launchData)
    }

    private fun isFatalPlaybackError(reason: String): Boolean {
        val r = reason.lowercase(java.util.Locale.US)
        return r.contains("unsupported stream format") ||
            r.contains("format unsupported") ||
            r.contains("no audio/video") ||
            r.contains("torrent stream error") ||
            r.contains("p2p torrent streaming is disabled") ||
            r.contains("torrserver engine is not installed") ||
            r.contains("400") ||
            r.contains("401") ||
            r.contains("403") ||
            r.contains("404") ||
            r.contains("405") ||
            r.contains("410") ||
            r.contains("428") ||
            r.contains("500") ||
            r.contains("502") ||
            r.contains("503") ||
            r.contains("504") ||
            r.contains("server error") ||
            r.contains("bad gateway") ||
            r.contains("service unavailable") ||
            r.contains("empty / eof") ||
            r.contains("connection refused") ||
            r.contains("resolution failed") ||
            r.contains("timed out") ||
            r.contains("code -13") ||
            r.contains("error=-13") ||
            r.contains("demuxer") ||
            r.contains("immediate exit requested")
    }

    private fun handlePlaybackError(failedUrl: String, reason: String) {
        val currentState = uiState.value
        val currentPhase = currentState.phase

        // If the error arrived for a link that isn't the currently probing/playing link, ignore.
        val currentLink = when (currentPhase) {
            is PlayerPhase.Probing -> currentPhase.link
            is PlayerPhase.Playing -> currentPhase.link
            else -> null
        }
        if (currentLink != null && currentLink.url != failedUrl) {
            AppLogger.d("EmbeddedPlayerViewModel", "Ignoring error for stale link: $failedUrl (current is ${currentLink.url})")
            return
        }

        val isFatal = isFatalPlaybackError(reason)
        val isProbing = currentPhase is PlayerPhase.Probing
        val maxRetries = if (isProbing) MAX_PROBE_RETRIES else MAX_PLAYING_RETRIES

        // Check retries for this specific link (1 fast retry for transient probe errors; up to 2 for active playback)
        val retries = linkRetries.getOrDefault(failedUrl, 0)
        if (!isFatal && retries < maxRetries) {
            linkRetries[failedUrl] = retries + 1
            AppLogger.w("EmbeddedPlayerViewModel", "Retrying link ($retries/$maxRetries): $failedUrl")
            if (currentLink != null) {
                sendEffect(PlayerUiEffect.ShowToast("Stream error ($reason). Reconnecting (${retries + 1}/$maxRetries)..."))
                val startPos = playerState.positionMs.value.takeIf { it > 0L }
                    ?: currentState.launchData?.startPositionMs ?: 0L
                updateState {
                    copy(
                        launchData = launchData?.copy(startPositionMs = startPos),
                        reloadNonce = reloadNonce + 1,
                    )
                }
                updatePhase(PlayerPhase.Probing(currentLink, currentState.isScrapingLinks, isInitial = false, isRetry = true), currentState.failedLinks)
                return
            }
        }

        AppLogger.e("EmbeddedPlayerViewModel", "Playback error on $failedUrl: $reason (fatal=$isFatal)")
        val newFailed = currentState.failedLinks + (failedUrl to reason)
        val links = currentState.nextEpisodeLinks.ifEmpty { currentState.launchData?.links ?: emptyList() }
        val currentPos = playerState.positionMs.value
        val startPos = if (currentPos > 0L) currentPos else (currentState.launchData?.startPositionMs ?: 0L)
        val next = pickBestActiveLink(links, newFailed.keys, startPos)

        if (next != null) {
            val failedLinkObj = links.find { it.url == failedUrl }
            val cleanReason = when {
                reason.contains("403", ignoreCase = true) -> "403 Forbidden"
                reason.contains("404", ignoreCase = true) -> "404 Not Found"
                reason.contains("timed out", ignoreCase = true) || reason.contains("timeout", ignoreCase = true) -> "Connection Timed Out"
                reason.contains("refused", ignoreCase = true) -> "Connection Refused"
                reason.isNotBlank() -> reason.take(30)
                else -> "Playback Error"
            }
            val rawName = failedLinkObj?.name?.trim() ?: "Source"
            val cleanName = if (rawName.length > 36) rawName.take(34) + "…" else rawName
            val nextIndex = links.indexOf(next) + 1
            val totalLinks = links.size
            sendEffect(PlayerUiEffect.ShowToast("$cleanName failed ($cleanReason). Trying mirror candidate $nextIndex of $totalLinks..."))

            updateState {
                copy(
                    launchData = launchData?.copy(startPositionMs = startPos),
                    failedLinks = newFailed,
                )
            }
            viewModelScope.launch {
                delay(1000L)
                updatePhase(PlayerPhase.Probing(next, uiState.value.isScrapingLinks, isInitial = false), newFailed)
            }
        } else if (currentState.isScrapingLinks) {
            // Still scraping, wait for scrapers to produce more links.
            updatePhase(PlayerPhase.Scraping, newFailed)
        } else {
            // All links exhausted and scraping is done — enter in-player failure & diagnostics state
            AppLogger.e("EmbeddedPlayerViewModel", "All sources exhausted ($reason). Entering in-player diagnostic failure state.")
            val showName = currentState.launchData?.history?.showName ?: "Media"
            val epNum = currentState.launchData?.history?.episode?.let { "E$it" } ?: ""
            val providerName = currentState.launchData?.loadResponse?.apiName ?: currentState.launchData?.history?.apiName ?: "Provider"
            val totalCandidates = links.size
            val diagReport = buildString {
                appendLine("=== Playback Diagnostics Report ===")
                appendLine("Title: $showName $epNum".trim())
                appendLine("Provider: $providerName")
                appendLine("Total Sources Discovered: $totalCandidates")
                appendLine("Terminal Reason: $reason")
                appendLine("Failed Candidates Breakdown:")
                links.forEachIndexed { i, link ->
                    val failure = newFailed[link.url] ?: "Skipped"
                    val host = try {
                        java.net.URI(link.url).host ?: "unknown-host"
                    } catch (_: Throwable) {
                        "link-$i"
                    }
                    val q = if (link.quality > 0) "${link.quality}p" else "unknown"
                    appendLine("  [${i + 1}] ${link.name} ($q, host: $host) -> $failure")
                }
                appendLine("Generated At: ${java.time.Instant.now()}")
            }
            updatePhase(
                PlayerPhase.Exhausted(
                    reason = "All $totalCandidates sources failed ($reason)",
                    failedLinks = newFailed,
                    diagnostics = diagReport,
                ),
                newFailed,
            )
            if (playerState.positionMs.value <= 0L) {
                saveJob?.cancel()
                val parentId = currentState.launchData?.history?.parentId
                if (!parentId.isNullOrBlank()) {
                    viewModelScope.launch(Dispatchers.IO) {
                        DesktopDataStore.removeWatchHistory(parentId)
                    }
                }
            }
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
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(
                PlayerConfig.PREF_ACTIVE_SHADER,
                shaderName,
            )
        }
        // Note: The shader will be applied on the NEXT player initialization.
        // Hot-swapping requires MPV property commands, which can be added via PlayerUiEffect if needed.
    }

    private fun savePosition(history: WatchHistory, forceNotify: Boolean = false) {
        val name = history.showName.trim()
        if (name.isBlank() || name.equals("Unknown Channel", ignoreCase = true) || name.equals("Unknown", ignoreCase = true)) {
            return
        }
        if (history.duration <= 0 && history.position <= 0) {
            return
        }
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            WatchHistoryCoordinator.saveWithNextEpisodeQueue(
                history = history,
                hasNextEpisode = uiState.value.hasNextEpisode,
                nextEpisode = uiState.value.nextEpisodeData,
                saveProgress = savePlaybackProgress,
                forceNotify = forceNotify,
            )
        }
    }

    private fun flushCurrentEpisodeProgress(markCompletedIfNearEnd: Boolean = false) {
        val currentData = uiState.value.launchData ?: return
        val currentDurSec = playerState.durationMs.value / 1000L
        val currentPosSec = playerState.positionMs.value / 1000L
        if (currentDurSec <= 0 && currentData.history.duration <= 0) return

        val durSec = if (currentDurSec > 0) currentDurSec else currentData.history.duration
        val posSec = if (currentPosSec > 0) currentPosSec else currentData.history.position

        val pct = if (durSec > 0) posSec.toFloat() / durSec.toFloat() else 0f
        val shouldMarkCompleted = (markCompletedIfNearEnd && pct >= 0.80f) || pct >= 0.85f
        val finalPosSec = if (shouldMarkCompleted && durSec > 0) durSec else posSec

        val updatedHistory = currentData.history.copy(
            position = finalPosSec,
            duration = durSec,
            updateTime = System.currentTimeMillis(),
        )

        // Launch in NonCancellable so subsequent playerState.pause() or playerState.reset()
        // cannot cancel database persistence or history notification in flight.
        viewModelScope.launch(Dispatchers.IO + NonCancellable) {
            WatchHistoryCoordinator.saveWithNextEpisodeQueue(
                history = updatedHistory,
                hasNextEpisode = uiState.value.hasNextEpisode,
                nextEpisode = uiState.value.nextEpisodeData,
                saveProgress = savePlaybackProgress,
                forceNotify = true,
            )
        }
    }

    private fun init(initialData: VideoLaunchData) {
        linkRetries.clear()
        lastSavedPositionSec = initialData.startPositionMs / 1000L
        loadLinksJob?.cancel()
        countdownJob?.cancel()
        timeoutJob?.cancel()
        scraper.cancelPreScrape()

        val isFinished = initialData.history.duration > 0 && initialData.history.position >= initialData.history.duration - 15
        val adjustedData = if (isFinished) {
            initialData.copy(
                startPositionMs = 0L,
                history = initialData.history.copy(position = 0L),
            )
        } else {
            initialData
        }
        // Fast-path: Check EnrichedDetailsCache for immediately available logo, backdrop, or actors
        val showUrl = adjustedData.loadResponse?.url ?: adjustedData.history.showUrl
        val cachedState = if (showUrl.isNotBlank()) com.lagradost.cloudstream3.desktop.ui.screens.details.EnrichedDetailsCache.get(showUrl) else null
        val hydratedData = if (cachedState != null) {
            adjustedData.copy(
                enrichedLogoUrl = adjustedData.enrichedLogoUrl?.takeIf { it.isNotBlank() } ?: cachedState.enrichedLogoUrl,
                enrichedBackdropUrl = adjustedData.enrichedBackdropUrl?.takeIf { it.isNotBlank() } ?: cachedState.enrichedBackdropUrl,
                enrichedActors = adjustedData.enrichedActors?.takeIf { it.isNotEmpty() } ?: cachedState.enrichedActors,
                loadResponse = adjustedData.loadResponse ?: cachedState.response,
            )
        } else {
            adjustedData
        }

        updateState { copy(launchData = hydratedData, phase = PlayerPhase.Idle, failedLinks = emptyMap()) }

        // Background metadata hydration pipeline:
        // Handles history launches (loadResponse == null) and quick-play launches (missing logo/cast)
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentLaunch = uiState.value.launchData ?: hydratedData
                val currentShowUrl = currentLaunch.loadResponse?.url ?: currentLaunch.history.showUrl
                if (currentShowUrl.isBlank()) return@launch

                // 1. Ensure loadResponse is loaded
                var effectiveResp = currentLaunch.loadResponse
                if (effectiveResp == null) {
                    val apiName = currentLaunch.history.apiName
                    val provider = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull {
                        it.name == apiName && it.mainUrl.isNotBlank() && currentShowUrl.startsWith(it.mainUrl)
                    } ?: com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(apiName)

                    if (provider != null) {
                        val res = SafePluginInvoker.invokeOrNull(
                            tag = "HistoryLaunch:${provider.name}",
                            timeoutMs = SafePluginInvoker.TIMEOUT_LOAD_MS,
                        ) {
                            provider.load(currentShowUrl)
                        }
                        if (res is com.lagradost.cloudstream3.LoadResponse) {
                            effectiveResp = res
                            updateState {
                                val curr = launchData
                                if (curr != null) {
                                    copy(launchData = curr.copy(loadResponse = res))
                                } else {
                                    this
                                }
                            }
                        }
                    }
                }

                // 2. If logo or cast are still missing, trigger background metadata enrichment
                if (effectiveResp != null) {
                    val activeLaunch = uiState.value.launchData ?: hydratedData
                    val missingLogo = activeLaunch.enrichedLogoUrl.isNullOrBlank() && effectiveResp.logoUrl.isNullOrBlank()
                    val missingCast = activeLaunch.enrichedActors.isNullOrEmpty() && effectiveResp.actors.isNullOrEmpty()
                    val missingBackdrop = activeLaunch.enrichedBackdropUrl.isNullOrBlank() && effectiveResp.backgroundPosterUrl.isNullOrBlank()

                    if (missingLogo || missingCast || missingBackdrop) {
                        com.lagradost.cloudstream3.desktop.metadata.MetadataPipeline.enrich(
                            loaded = effectiveResp,
                            url = currentShowUrl,
                            fetchCast = true,
                            callbacks = com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentCallbacks(
                                onLogoLoaded = { logo ->
                                    updateState {
                                        val curr = launchData
                                        if (curr != null && (curr.enrichedLogoUrl.isNullOrBlank() || curr.enrichedLogoUrl != logo)) {
                                            copy(launchData = curr.copy(enrichedLogoUrl = logo))
                                        } else {
                                            this
                                        }
                                    }
                                },
                                onBackdropLoaded = { backdrop ->
                                    updateState {
                                        val curr = launchData
                                        if (curr != null && (curr.enrichedBackdropUrl.isNullOrBlank() || curr.enrichedBackdropUrl != backdrop)) {
                                            copy(launchData = curr.copy(enrichedBackdropUrl = backdrop))
                                        } else {
                                            this
                                        }
                                    }
                                },
                                onActorsLoaded = { actors ->
                                    updateState {
                                        val curr = launchData
                                        if (curr != null && (curr.enrichedActors.isNullOrEmpty() || curr.enrichedActors != actors)) {
                                            copy(launchData = curr.copy(enrichedActors = actors))
                                        } else {
                                            this
                                        }
                                    }
                                },
                                onMetadataLoaded = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, actors, _, _ ->
                                    if (!actors.isNullOrEmpty()) {
                                        updateState {
                                            val curr = launchData
                                            if (curr != null && curr.enrichedActors.isNullOrEmpty()) {
                                                copy(launchData = curr.copy(enrichedActors = actors))
                                            } else {
                                                this
                                            }
                                        }
                                    }
                                },
                            ),
                        )
                    }
                }
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.w("EmbeddedPlayerViewModel", "Background metadata hydration failed: ${e.message}")
            }
        }

        // Auto-scrape initial episode if links are empty
        if (hydratedData.links.isEmpty() && hydratedData.history.episodeId != null) {
            val apiName = hydratedData.loadResponse?.apiName ?: hydratedData.history.apiName
            val showUrl = hydratedData.loadResponse?.url ?: hydratedData.history.showUrl
            val provider = com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull {
                it.name == apiName && it.mainUrl.isNotBlank() && showUrl.startsWith(it.mainUrl)
            } ?: com.lagradost.cloudstream3.APIHolder.getApiFromNameNull(apiName)
            if (provider != null) {
                val targetEp = provider.newEpisode(hydratedData.history.episodeId!!) {
                    this.name = hydratedData.history.episodeName ?: hydratedData.history.showName
                    this.posterUrl = hydratedData.history.episodeThumbnailUrl ?: hydratedData.history.posterUrl
                    this.description = hydratedData.history.episodeDescription
                    this.season = hydratedData.history.season
                    this.episode = hydratedData.history.episode
                }
                updateState {
                    copy(
                        phase = PlayerPhase.Scraping,
                        targetEpisodeData = targetEp,
                        nextEpisodeLinks = emptyList(),
                        nextEpisodeSubtitles = hydratedData.subtitles,
                    )
                }

                loadLinksJob = viewModelScope.launch(Dispatchers.IO) {
                    scrapeAndPlay(provider, hydratedData.history.episodeId!!, hydratedData, targetEp)
                }
            } else {
                // Plugin not installed or apiName unknown — can't scrape, can't play.
                // Surface an error immediately rather than leaving the player on a blank screen.
                AppLogger.e("EmbeddedPlayerViewModel", "Provider not found for apiName='$apiName'. Cannot scrape links.")
                sendEffect(PlayerUiEffect.ShowError("Plugin not found — cannot load video."))
                sendEffect(PlayerUiEffect.ClosePlayer)
            }
        } else if (hydratedData.links.isNotEmpty()) {
            // Links already provided at launch — respect user's explicitly selected stream if specified
            val selected = hydratedData.links.getOrNull(hydratedData.initialIndex)
                ?: pickBestActiveLink(hydratedData.links, emptySet(), hydratedData.startPositionMs)
            if (selected != null) {
                updatePhase(PlayerPhase.Probing(selected, false))
            } else {
                updatePhase(PlayerPhase.Idle)
            }
        }
    }

    private fun loadEpisode(episode: Episode) {
        linkRetries.clear()
        val currentData = uiState.value.launchData ?: return

        val lockUnreleased = com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig.lockUnreleasedEpisodes.value
        val hasPlayableData = episode.data.isNotBlank() &&
            !episode.data.startsWith("unreleased_") &&
            !episode.data.startsWith("synthetic_") &&
            episode.data != "null" && episode.data != "{}" && episode.data != "[]"
        val hasCachedLinks = LinkCache.get(episode.data) != null
        val resolvedApiName = currentData.loadResponse?.apiName ?: currentData.history.apiName
        run {
            val status = com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(episode, resolvedApiName)
            val isEpisodeLocked = !hasCachedLinks && (
                (status.isUnreleased && lockUnreleased) || status.isMissingFromProvider || !hasPlayableData
            )
            if (isEpisodeLocked) {
                val toast = status.statusBadgeText ?: (if (status.isMissingFromProvider) "Episode is not available on $resolvedApiName." else "This episode is unreleased.")
                sendEffect(PlayerUiEffect.ShowToast(toast))
                return
            }
        }

        flushCurrentEpisodeProgress(markCompletedIfNearEnd = true)
        lastSavedPositionSec = 0L

        countdownJob?.cancel()
        scrapeGraceJob?.cancel()
        scrapeGraceJob = null
        loadLinksJob?.cancel()
        playerState.pause()
        playerState.reset()

        val isOffline = currentData.history.apiName in listOf("Offline", "Local") || java.io.File(episode.data).exists()
        if (isOffline) {
            val file = java.io.File(episode.data)
            if (file.exists()) {
                viewModelScope.launch(Dispatchers.IO) {
                    val offlineLink = com.lagradost.cloudstream3.utils.newExtractorLink(
                        source = "Downloaded (Offline)",
                        name = episode.name ?: file.name,
                        url = file.absolutePath,
                        type = if (file.name.contains(".m3u8", ignoreCase = true)) com.lagradost.cloudstream3.utils.ExtractorLinkType.M3U8 else com.lagradost.cloudstream3.utils.ExtractorLinkType.VIDEO,
                    ) {
                        this.referer = ""
                        this.quality = com.lagradost.cloudstream3.utils.Qualities.Unknown.value
                    }
                    val newLaunchData = currentData.copy(
                        links = listOf(offlineLink),
                        initialIndex = 0,
                        title = episode.name ?: file.name,
                        startPositionMs = 0L,
                        history = currentData.history.copy(
                            showUrl = file.absolutePath,
                            episodeId = file.absolutePath,
                            episode = episode.episode,
                            season = episode.season,
                            position = 0L,
                            duration = 0L,
                            updateTime = System.currentTimeMillis(),
                            episodeName = episode.name,
                        ),
                    )
                    updateState {
                        copy(
                            launchData = newLaunchData,
                            phase = PlayerPhase.Probing(offlineLink, stillScraping = false),
                            targetEpisodeData = null,
                            nextEpisodeLinks = listOf(offlineLink),
                            nextEpisodeError = null,
                        )
                    }
                }
                return
            }
        }

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

        val isTorbox = currentData.history.apiName.equals("Torbox", ignoreCase = true)
        if (isTorbox) {
            loadLinksJob = viewModelScope.launch(Dispatchers.IO) {
                loadTorboxEpisode(episode, currentData)
            }
            return
        }

        val apiName = currentData.loadResponse?.apiName ?: currentData.history.apiName
        val provider = APIHolder.getApiFromNameNull(apiName)

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

    private suspend fun loadTorboxEpisode(episode: Episode, currentData: VideoLaunchData) {
        val apiKey = DesktopDataStore.getKey<String>(DesktopDataStore.PREF_TORBOX_API_KEY)?.trim().orEmpty()
        if (apiKey.isBlank()) {
            AppLogger.e("EmbeddedPlayerViewModel", "Torbox API key not configured")
            sendEffect(PlayerUiEffect.ShowToast("Torbox API key not configured"))
            updateState { copy(targetEpisodeData = null, phase = PlayerPhase.Idle) }
            return
        }

        val fileId = episode.data.toIntOrNull()
        val parentId = currentData.history.parentId.orEmpty()
        val parentParts = parentId.split("_")
        val itemId = parentParts.getOrNull(2)?.toIntOrNull()
        val item = if (itemId != null) TorboxLibraryCache.findItem(itemId) else null

        val cachedLinks = LinkCache.get(episode.data)
        val activeLink = if (cachedLinks != null && cachedLinks.links.isNotEmpty()) {
            cachedLinks.links.first()
        } else {
            val streamUrl = if (item != null) {
                TorboxApiClient.requestDownloadLink(apiKey, item, fileId)
            } else if (itemId != null) {
                TorboxApiClient.requestDownloadLink(apiKey, itemId, fileId)
            } else {
                null
            }

            if (!streamUrl.isNullOrBlank()) {
                val file = item?.files?.find { it.id == fileId }
                val videoTitle = file?.name?.takeIf { it.isNotBlank() } ?: episode.name ?: "Episode ${episode.episode ?: 1}"
                val effectiveBytes = file?.size ?: 0L
                val sizeStr = if (effectiveBytes > 0L) {
                    val gb = effectiveBytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
                    if (gb >= 1.0) {
                        String.format(java.util.Locale.US, "%.1f GB", gb)
                    } else {
                        String.format(java.util.Locale.US, "%.0f MB", effectiveBytes.toDouble() / (1024.0 * 1024.0))
                    }
                } else {
                    null
                }
                val sizeTag = if (sizeStr != null) " [$sizeStr]" else ""
                val extLink = newExtractorLink(
                    source = "Torbox ${item?.storageType?.label ?: "Cloud"}",
                    name = videoTitle + sizeTag,
                    url = streamUrl,
                    type = if (streamUrl.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                ) {
                    this.referer = ""
                }
                LinkCache.set(episode.data, listOf(extLink), emptyList())
                extLink
            } else {
                null
            }
        }

        if (activeLink != null) {
            val file = item?.files?.find { it.id == fileId }
            val videoTitle = file?.name?.takeIf { it.isNotBlank() } ?: episode.name ?: "Episode ${episode.episode ?: 1}"
            val pastHistory = DesktopDataStore.getEpisodeWatched(
                parentId = currentData.history.parentId,
                episodeId = episode.data,
            )
            val startPos = if (pastHistory != null && pastHistory.duration > 0 && pastHistory.position < pastHistory.duration - 15) {
                pastHistory.position * 1000L
            } else {
                0L
            }
            val newHistory = currentData.history.copy(
                showUrl = activeLink.url,
                episodeId = episode.data,
                episode = episode.episode,
                season = episode.season,
                position = startPos / 1000L,
                duration = pastHistory?.duration ?: 0L,
                updateTime = System.currentTimeMillis(),
                episodeName = videoTitle,
            )
            val newLaunchData = currentData.copy(
                links = listOf(activeLink),
                initialIndex = 0,
                title = videoTitle,
                startPositionMs = startPos,
                history = newHistory,
            )
            updateState {
                copy(
                    launchData = newLaunchData,
                    phase = PlayerPhase.Probing(activeLink, stillScraping = false),
                    targetEpisodeData = null,
                    nextEpisodeLinks = listOf(activeLink),
                    nextEpisodeError = null,
                    failedLinks = emptyMap(),
                )
            }
        } else {
            AppLogger.e("EmbeddedPlayerViewModel", "Failed to resolve Torbox stream for episode ${episode.data}")
            sendEffect(PlayerUiEffect.ShowToast("Failed to resolve stream for ${episode.name ?: "episode"}"))
            updateState {
                copy(
                    targetEpisodeData = null,
                    phase = PlayerPhase.Idle,
                )
            }
        }
    }

    private fun cancelLoading() {
        scrapeGraceJob?.cancel()
        scrapeGraceJob = null
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
        viewModelScope.launch(Dispatchers.IO) {
            val currentData = uiState.value.launchData ?: return@launch
            val epData = uiState.value.targetEpisodeData ?: return@launch
            val currentLinks = uiState.value.nextEpisodeLinks
            if (currentLinks.isEmpty()) return@launch

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
                episodeName = epData.name,
                episodeThumbnailUrl = epData.posterUrl ?: currentData.history.posterUrl,
                episodeDescription = epData.description ?: currentData.history.episodeDescription,
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
            val hasPlayableData = nextEpisode.data.isNotBlank() &&
                !nextEpisode.data.startsWith("unreleased_") &&
                !nextEpisode.data.startsWith("synthetic_") &&
                nextEpisode.data != "null" && nextEpisode.data != "{}" && nextEpisode.data != "[]"
            val apiName = currentData.loadResponse?.apiName ?: currentData.history.apiName
            val hasCachedLinks = LinkCache.get(nextEpisode.data) != null
            run {
                val status = com.lagradost.cloudstream3.desktop.ui.screens.details.parseEpisodeReleaseStatus(nextEpisode, apiName)
                val isEpisodeLocked = !hasCachedLinks && (
                (status.isUnreleased && lockUnreleased) || status.isMissingFromProvider || !hasPlayableData
            )
            if (isEpisodeLocked) {
                    updateState {
                        copy(
                            phase = PlayerPhase.Idle,
                            countdownToNextEpisode = null,
                        )
                    }
                    val toast = status.statusBadgeText ?: (if (status.isMissingFromProvider) "Next episode is not available on $apiName." else "Next episode is unreleased.")
                    sendEffect(PlayerUiEffect.ShowToast(toast))
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
        viewModelScope.launch(Dispatchers.IO) {
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
                    } else {
                        current.title
                    },
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
    }

    private fun stopBackgroundScraping() {
        scrapeGraceJob?.cancel()
        scrapeGraceJob = null
        loadLinksJob?.cancel()
        loadLinksJob = null
        viewModelScope.launch(Dispatchers.IO) {
            val current = uiState.value.launchData
            val startPos = current?.startPositionMs ?: 0L
            val sortedLinks = sortLinks(uiState.value.nextEpisodeLinks, startPos)
            if (sortedLinks.isNotEmpty()) {
                val candidateKeys = listOfNotNull(
                    current?.history?.episodeId,
                    uiState.value.targetEpisodeData?.data,
                ).distinct()
                LinkCache.set(candidateKeys, sortedLinks, uiState.value.nextEpisodeSubtitles)
            }
            updateState {
                val currentLaunch = launchData
                val updatedLaunch = if (currentLaunch != null) currentLaunch.copy(links = sortedLinks) else null
                val newPhase = when (val p = phase) {
                    is PlayerPhase.Probing -> p.copy(stillScraping = false)
                    is PlayerPhase.Playing -> p.copy(stillScraping = false)
                    is PlayerPhase.Scraping -> PlayerPhase.Idle
                    else -> p
                }
                copy(
                    nextEpisodeLinks = sortedLinks,
                    launchData = updatedLaunch ?: launchData,
                    phase = newPhase,
                )
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
                position = startPos / 1000L,
                duration = pastHistory?.duration ?: 0L,
                episodeName = targetEpisodeData.name,
                episodeThumbnailUrl = targetEpisodeData.posterUrl ?: current.history.posterUrl,
                episodeDescription = targetEpisodeData.description ?: current.history.episodeDescription,
            )
        } else {
            null
        }

        val autoPlay = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_AUTO_PLAY) ?: true

        val tmdbId = current.loadResponse?.syncData?.get("tmdb")?.toIntOrNull()
        val targetSeason = targetEpisodeData?.season
        val hasDualCast = current.enrichedActors?.any { it.voiceActor != null } == true
        val newSeasonActors = if (!hasDualCast && tmdbId != null && targetSeason != null && targetSeason > 0 && targetSeason != current.history.season) {
            try {
                val fetched = com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService.fetchSeasonCredits(tmdbId, targetSeason)
                if (fetched.isNotEmpty()) fetched else current.enrichedActors
            } catch (_: Throwable) {
                current.enrichedActors
            }
        } else {
            current.enrichedActors
        }

        val showUrl = current.loadResponse?.url ?: current.history.showUrl
        val parentId = current.history.parentId
        val episodeData = targetEpisodeData?.data
        val candidateKeys = buildList {
            targetEpisodeId?.takeIf { it.isNotBlank() }?.let { add(it) }
            episodeData?.takeIf { it.isNotBlank() }?.let { add(it) }
            if (isEmpty()) {
                showUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                parentId?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }.distinct()

        val cached = candidateKeys.firstNotNullOfOrNull { LinkCache.get(it) }
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
                    enrichedActors = newSeasonActors,
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
                updatePhase(PlayerPhase.Scraping)
                viewModelScope.launch {
                    delay(750L)
                    updatePhase(PlayerPhase.Probing(bestLink, false))
                }
            } else {
                updatePhase(PlayerPhase.Idle)
            }
            return
        }

        var fastPlayJob: Job? = null

        val triggerFastPlayback: () -> Unit = {
            val isScrapingPhase = uiState.value.phase is PlayerPhase.Scraping
            if (hasStartedPlaying.compareAndSet(false, true) || isScrapingPhase) {
                hasStartedPlaying.set(true)
                var bestLinkToProbe: ExtractorLink? = null
                updateState {
                    val currentLinks = nextEpisodeLinks
                    val sorted = sortLinks(currentLinks, startPos)
                    val best = pickBestActiveLink(sorted, failedLinks.keys, startPos)
                    if (best != null) {
                        bestLinkToProbe = best
                        val launch = if (targetEpisodeData != null && newHistory != null) {
                            current.copy(
                                links = sorted,
                                subtitles = nextEpisodeSubtitles,
                                history = newHistory.copy(position = startPos / 1000L, duration = pastHistory?.duration ?: 0L),
                                initialIndex = 0,
                                startPositionMs = startPos,
                                enrichedActors = newSeasonActors,
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
                                links = sorted,
                                subtitles = nextEpisodeSubtitles,
                                initialIndex = 0,
                                enrichedActors = newSeasonActors,
                            )
                        }
                        copy(
                            nextEpisodeLinks = sorted,
                            launchData = launch,
                            targetEpisodeData = null,
                            nextEpisodeError = null,
                        )
                    } else {
                        this
                    }
                }
                val link = bestLinkToProbe
                if (link != null) {
                    AppLogger.i("EmbeddedPlayerViewModel:${provider.name}", "Fast-play triggered: probing ${link.name} (quality=${link.quality}p)")
                    updatePhase(PlayerPhase.Probing(link, stillScraping = true))

                    val flowControl = DesktopDataStore.getKey<String>(PlayerConfig.PREF_SCRAPE_FLOW_CONTROL)
                    if (flowControl == "immediate") {
                        AppLogger.i("EmbeddedPlayerViewModel", "Scrape Flow Control: Immediate mode active — stopping background scrape.")
                        stopBackgroundScraping()
                    }
                }
            }
        }

        val pendingLinks = ConcurrentLinkedQueue<ExtractorLink>()
        val pendingSubs = ConcurrentLinkedQueue<SubtitleFile>()

        suspend fun flushBatch() = kotlinx.coroutines.withContext(Dispatchers.Default) {
            if (pendingLinks.isEmpty() && pendingSubs.isEmpty()) return@withContext

            val drainedLinks = mutableListOf<ExtractorLink>()
            while (true) {
                val link = pendingLinks.poll() ?: break
                drainedLinks.add(link)
            }

            val drainedSubs = mutableListOf<SubtitleFile>()
            while (true) {
                val sub = pendingSubs.poll() ?: break
                drainedSubs.add(sub)
            }

            if (drainedLinks.isEmpty() && drainedSubs.isEmpty()) return@withContext

            if (drainedLinks.isNotEmpty()) {
                LinkCache.addLinks(candidateKeys, drainedLinks)
            }
            if (drainedSubs.isNotEmpty()) {
                LinkCache.addLinks(candidateKeys, emptyList(), drainedSubs)
            }

            val isPlaying = hasStartedPlaying.get()
            val currentLinks = uiState.value.nextEpisodeLinks
            val combinedLinks = if (drainedLinks.isNotEmpty()) {
                val merged = (currentLinks + drainedLinks).distinctBy { it.url }
                if (isPlaying) merged else sortLinks(merged, startPos)
            } else {
                currentLinks
            }

            val currentSubs = uiState.value.nextEpisodeSubtitles
            val combinedSubs = if (drainedSubs.isNotEmpty()) {
                (currentSubs + drainedSubs).distinctBy { it.url.trim().lowercase() }
            } else {
                currentSubs
            }

            updateState {
                if (!isScrapingLinks && !isPlaying) return@updateState this
                val currentLaunch = launchData
                val updatedLaunch = if (currentLaunch != null && currentLaunch.history.episodeId == targetEpisodeId) {
                    currentLaunch.copy(links = combinedLinks, subtitles = combinedSubs)
                } else {
                    currentLaunch
                }

                copy(
                    nextEpisodeLinks = combinedLinks,
                    nextEpisodeSubtitles = combinedSubs,
                    launchData = updatedLaunch,
                )
            }
        }

        val flusherJob = viewModelScope.launch(Dispatchers.Default) {
            while (isActive) {
                delay(150L)
                flushBatch()
            }
        }

        val result = try {
            scraper.executeScrape(
                provider = provider,
                targetEpisodeId = targetEpisodeId,
                autoPlay = autoPlay,
                currentLaunchData = current,
                targetEpisodeData = targetEpisodeData,
                onSubtitle = { cleanSub ->
                    pendingSubs.add(cleanSub)
                },
                onLink = { link ->
                    pendingLinks.add(link)
                    val isPlaying = hasStartedPlaying.get()
                    if (!isPlaying && autoPlay && uiState.value.phase is PlayerPhase.Scraping) {
                        val score = com.lagradost.cloudstream3.desktop.player.QualityDataHelper.getLinkScore(link)
                        if (score >= com.lagradost.cloudstream3.desktop.player.QualityDataHelper.FAST_PLAY_SCORE_THRESHOLD) {
                            fastPlayJob?.cancel()
                            fastPlayJob = viewModelScope.launch(Dispatchers.IO) {
                                delay(900)
                                triggerFastPlayback()
                            }
                        } else if (fastPlayJob == null) {
                            fastPlayJob = viewModelScope.launch(Dispatchers.IO) {
                                delay(2000)
                                triggerFastPlayback()
                            }
                        }
                    }
                },
            )
        } finally {
            flusherJob.cancel()
            flushBatch()
            fastPlayJob?.cancel()
            scrapeGraceJob?.cancel()
        }

        if (result.isSuccess) {
            val sortedLinks = sortLinks(uiState.value.nextEpisodeLinks, startPos)
            if (sortedLinks.isNotEmpty()) {
                LinkCache.set(candidateKeys, sortedLinks, uiState.value.nextEpisodeSubtitles)
            }

            if (hasStartedPlaying.get()) {
                updateState {
                    val newPhase = when (val p = phase) {
                        is PlayerPhase.Probing -> p.copy(stillScraping = false)
                        is PlayerPhase.Playing -> p.copy(stillScraping = false)
                        else -> p
                    }
                    val currentLaunch = launchData
                    val updatedLaunch = if (currentLaunch != null && currentLaunch.history.episodeId == targetEpisodeId) {
                        currentLaunch.copy(links = sortedLinks)
                    } else {
                        currentLaunch
                    }
                    copy(
                        phase = newPhase,
                        nextEpisodeLinks = sortedLinks,
                        launchData = updatedLaunch ?: launchData,
                    )
                }
            } else {
                var bestLinkToProbe: ExtractorLink? = null
                updateState {
                    if (nextEpisodeLinks.isNotEmpty()) {
                        hasStartedPlaying.set(true)
                        val best = pickBestActiveLink(sortedLinks, emptySet(), startPos)
                        if (best != null) {
                            bestLinkToProbe = best
                            val launch = if (targetEpisodeData != null && newHistory != null) {
                                current.copy(
                                    links = sortedLinks,
                                    subtitles = nextEpisodeSubtitles,
                                    history = newHistory.copy(position = startPos / 1000L, duration = pastHistory?.duration ?: 0L),
                                    initialIndex = 0,
                                    startPositionMs = startPos,
                                    enrichedActors = newSeasonActors,
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
                                    enrichedActors = newSeasonActors,
                                )
                            }
                            copy(
                                nextEpisodeLinks = sortedLinks,
                                launchData = launch,
                                targetEpisodeData = null,
                                nextEpisodeError = null,
                            )
                        } else {
                            copy(
                                phase = PlayerPhase.Exhausted(
                                    reason = "No playable sources found.",
                                    failedLinks = emptyMap(),
                                    diagnostics = "Provider '${provider.name}' returned candidate links, but none met playable criteria.",
                                ),
                                targetEpisodeData = null,
                                nextEpisodeError = null,
                            )
                        }
                    } else {
                        copy(
                            phase = PlayerPhase.Exhausted(
                                reason = "No streams discovered.",
                                failedLinks = emptyMap(),
                                diagnostics = "Provider '${provider.name}' returned 0 streams for this title/episode.",
                            ),
                            targetEpisodeData = null,
                            nextEpisodeError = null,
                        )
                    }
                }

                val linkToProbe = bestLinkToProbe
                if (linkToProbe != null) {
                    updatePhase(PlayerPhase.Probing(linkToProbe, false))
                }
            }
        } else {
            val ex = result.exceptionOrNull()
            if (!hasStartedPlaying.get()) {
                candidateKeys.forEach { LinkCache.remove(it) }
            }

            val isJsonParseError = ex is com.fasterxml.jackson.core.JsonParseException ||
                ex?.message?.contains("Unrecognized token") == true ||
                ex?.cause is com.fasterxml.jackson.core.JsonParseException

            val showUrl = current.loadResponse?.url ?: current.history.showUrl
            if (!hasStartedPlaying.get() && isJsonParseError && targetEpisodeId.startsWith("http")) {
                AppLogger.w("Plugin:${provider.name}", "Detected invalid episode data payload ($targetEpisodeId). Performing automatic self-healing re-fetch from $showUrl...")
                val healed = scraper.attemptSelfHealing(provider, targetEpisodeId, showUrl)
                if (healed != null) {
                    val (freshDataUrl, freshResp) = healed
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
            }

            if (hasStartedPlaying.get()) {
                val sortedLinks = sortLinks(uiState.value.nextEpisodeLinks, startPos)
                if (sortedLinks.isNotEmpty()) {
                    LinkCache.set(targetEpisodeId, sortedLinks, uiState.value.nextEpisodeSubtitles)
                }
                updateState {
                    val newPhase = when (val p = phase) {
                        is PlayerPhase.Probing -> p.copy(stillScraping = false)
                        is PlayerPhase.Playing -> p.copy(stillScraping = false)
                        else -> p
                    }
                    copy(phase = newPhase)
                }
            } else {
                var bestFallbackToProbe: ExtractorLink? = null
                updateState {
                    if (nextEpisodeLinks.isNotEmpty()) {
                        hasStartedPlaying.set(true)
                        val sortedLinks = sortLinks(nextEpisodeLinks, startPos)
                        val best = pickBestActiveLink(sortedLinks, emptySet(), startPos)
                        if (best != null) {
                            bestFallbackToProbe = best
                            val launch = if (targetEpisodeData != null && newHistory != null) {
                                current.copy(
                                    links = sortedLinks,
                                    subtitles = nextEpisodeSubtitles,
                                    history = newHistory.copy(position = startPos / 1000L, duration = pastHistory?.duration ?: 0L),
                                    initialIndex = 0,
                                    startPositionMs = startPos,
                                    enrichedActors = newSeasonActors,
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
                                    enrichedActors = newSeasonActors,
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
                                phase = PlayerPhase.Exhausted(
                                    reason = "No playable sources found.",
                                    failedLinks = emptyMap(),
                                    diagnostics = "Provider '${provider.name}' returned candidate links, but none met playable criteria.",
                                ),
                                targetEpisodeData = null,
                                nextEpisodeError = null,
                            )
                        }
                    } else {
                        AppLogger.e("Plugin:${provider.name}", "Failed to load links: ${ex?.message}", ex)
                        copy(
                            phase = PlayerPhase.Exhausted(
                                reason = "Failed to load links: ${ex?.message ?: "Unknown error"}",
                                failedLinks = emptyMap(),
                                diagnostics = buildString {
                                    appendLine("=== Scrape Exception ===")
                                    appendLine("Provider: ${provider.name}")
                                    appendLine("Message: ${ex?.message ?: "Unknown error"}")
                                    if (ex != null) appendLine("Type: ${ex.javaClass.simpleName}")
                                    appendLine("Timestamp: ${java.time.Instant.now()}")
                                },
                            ),
                            targetEpisodeData = null,
                            nextEpisodeError = null,
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
}
