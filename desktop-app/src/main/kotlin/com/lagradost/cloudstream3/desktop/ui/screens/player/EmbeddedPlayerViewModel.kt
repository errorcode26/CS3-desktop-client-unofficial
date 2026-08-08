package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerError
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

    init {
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
            val qualityDelta = if (targetQualityInt != null) kotlin.math.abs(link.quality - targetQualityInt) else 0
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

    private fun handlePlaybackError(failedUrl: String) {
        AppLogger.e("EmbeddedPlayerViewModel", "Playback error on: $failedUrl")
        updateState {
            val newFailed = failedLinks + failedUrl
            val links = launchData?.links ?: emptyList()
            val startPos = launchData?.startPositionMs ?: 0L
            val next = pickBestActiveLink(links, newFailed, startPos)
            if (next == null && !isScrapingLinks) {
                // All links exhausted and scraping is done — surface the error
                AppLogger.e("EmbeddedPlayerViewModel", "All sources exhausted.")
            }
            copy(
                failedLinks = newFailed,
                activeLink = next,
            )
        }
    }

    private fun handleLinkChange(url: String) {
        updateState {
            val link = launchData?.links?.find { it.url == url }
            copy(
                failedLinks = emptySet(),
                activeLink = link,
            )
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
            updateState { copy(launchData = adjustedData, activeLink = null, failedLinks = emptySet()) }

            // Auto-scrape initial episode if links are empty
            if (adjustedData.links.isEmpty() && adjustedData.history.episodeId != null) {
                val apiName = adjustedData.loadResponse?.apiName
                val provider = APIHolder.getApiFromNameNull(apiName ?: "")
                if (provider != null) {
                    val targetEp = provider.newEpisode(adjustedData.history.episodeId!!) {
                        this.name = adjustedData.history.showName
                        this.season = adjustedData.history.season
                        this.episode = adjustedData.history.episode
                    }
                    updateState {
                        copy(
                            isLoadingNextEpisode = true,
                            isScrapingLinks = true,
                            targetEpisodeData = targetEp,
                            nextEpisodeLinks = emptyList(),
                            nextEpisodeSubtitles = adjustedData.subtitles,
                        )
                    }

                    loadLinksJob = viewModelScope.launch(Dispatchers.IO) {
                        scrapeAndPlay(provider, adjustedData.history.episodeId!!, adjustedData, targetEp)
                    }
                }
            } else if (adjustedData.links.isNotEmpty()) {
                // Links already provided at launch (e.g. direct open) — pick immediately
                val best = pickBestActiveLink(adjustedData.links, emptySet(), adjustedData.startPositionMs)
                updateState { copy(activeLink = best) }
            }
        }
    }

    private fun loadEpisode(episode: Episode) {
        val currentData = uiState.value.launchData ?: return

        loadLinksJob?.cancel()
        updateState {
            copy(
                isLoadingNextEpisode = true,
                isScrapingLinks = true,
                nextEpisodeError = null,
                nextEpisodeLinks = emptyList(),
                nextEpisodeSubtitles = emptyList(),
                targetEpisodeData = episode,
                activeLink = null,
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
                    isLoadingNextEpisode = false,
                    isScrapingLinks = false,
                )
            }
        }
    }

    private fun cancelLoading() {
        loadLinksJob?.cancel()
        updateState {
            copy(
                targetEpisodeData = null,
                isLoadingNextEpisode = false,
                nextEpisodeError = null,
            )
        }
    }

    private fun playLoadedEpisode() {
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

        updateState {
            copy(
                isLoadingNextEpisode = false,
                nextEpisodeError = null,
                targetEpisodeData = null,
                nextEpisodeLinks = emptyList(),
                nextEpisodeSubtitles = emptyList(),
                launchData = newLaunchData,
            )
        }
    }

    private fun loadNextEpisode() {
        val episodes = uiState.value.episodes
        val currentData = uiState.value.launchData ?: return
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        val nextEpisode = if (currentIndex != -1 && currentIndex + 1 < episodes.size) episodes[currentIndex + 1] else null

        if (nextEpisode != null) {
            loadEpisode(nextEpisode)
        }
    }

    private fun loadPrevEpisode() {
        val episodes = uiState.value.episodes
        val currentData = uiState.value.launchData ?: return
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        val prevEpisode = if (currentIndex > 0) episodes[currentIndex - 1] else null

        if (prevEpisode != null) {
            loadEpisode(prevEpisode)
        }
    }

    private fun cancelScraping() {
        // Safe to cancel on Desktop: Duktape JNI handles thread interrupts safely here.
        // Uncommented to aggressively halt background network traffic.
        loadLinksJob?.cancel()
        updateState {
            copy(
                isScrapingLinks = false,
                isLoadingNextEpisode = false,
            )
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
                    isScrapingLinks = false,
                    nextEpisodeLinks = sortedLinks,
                    nextEpisodeSubtitles = cached.subtitles,
                    launchData = newLaunchData,
                    targetEpisodeData = null,
                    isLoadingNextEpisode = false,
                    nextEpisodeError = null,
                    activeLink = bestLink,
                    failedLinks = emptySet(),
                )
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
                            val bestLink = pickBestActiveLink(newLinks, failedLinks, startPos)

                            copy(
                                nextEpisodeLinks = newLinks,
                                launchData = newLaunchData,
                                targetEpisodeData = null,
                                isLoadingNextEpisode = false,
                                nextEpisodeError = null,
                                activeLink = bestLink,
                            )
                        } else {
                            val currentLaunch = launchData
                            val updatedLaunch = if (currentLaunch != null && currentLaunch.history.episodeId == targetEpisodeId) {
                                currentLaunch.copy(links = currentLaunch.links + link)
                            } else {
                                currentLaunch
                            }
                            copy(nextEpisodeLinks = newLinks, launchData = updatedLaunch)
                        }
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
                        isScrapingLinks = false,
                        targetEpisodeData = null,
                        isLoadingNextEpisode = false,
                        nextEpisodeError = PlayerError.ExtractorError(
                            pluginName = provider.name,
                            message = "No links found for this episode.",
                        ),
                    )
                } else {
                    LinkCache.set(targetEpisodeId, sortedLinks, nextEpisodeSubtitles)
                    copy(
                        isScrapingLinks = false,
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
                    copy(
                        isScrapingLinks = false,
                        nextEpisodeLinks = sortedLinks,
                        launchData = newLaunchData,
                    )
                }
            } else {
                AppLogger.e("Plugin:${provider.name}", "Failed to load links: ${ex?.message}", ex)
                updateState {
                    copy(
                        isScrapingLinks = false,
                        targetEpisodeData = null,
                        isLoadingNextEpisode = false,
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
