package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiState
import com.lagradost.cloudstream3.newEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedPlayerViewModel : BaseMviViewModel<PlayerUiState, PlayerUiEvent, PlayerUiEffect>(
    initialState = PlayerUiState(),
) {
    private var loadLinksJob: Job? = null
    private var saveJob: Job? = null
    private var scrapeJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val autoPlay = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
            updateState { copy(autoPlayEnabled = autoPlay) }
        }
    }

    override fun dispose() {
        super.dispose()
        // See cancelScraping() - Do NOT cancel loadLinksJob on exit
        // loadLinksJob?.cancel()
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
        }
    }

    private fun selectShader(shaderName: String) {
        com.lagradost.common.storage.DesktopDataStore.setKey(
            com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ACTIVE_SHADER,
            shaderName,
        )
        // Note: The shader will be applied on the NEXT player initialization.
        // Hot-swapping requires MPV property commands, which can be added via PlayerUiEffect if needed.
    }

    private fun savePosition(history: com.lagradost.common.storage.WatchHistory) {
        saveJob?.cancel()
        saveJob = viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
            val currentDurSec = history.duration
            val currentPosSec = history.position
            val percentage = if (currentDurSec > 0) currentPosSec.toFloat() / currentDurSec else 0f
            if (percentage >= 0.90f) {
                val hasNext = uiState.value.hasNextEpisode
                if (hasNext) {
                    val nextEp = uiState.value.nextEpisodeData
                    if (nextEp != null) {
                        com.lagradost.common.storage.DesktopDataStore.setLastWatched(history)
                        val nextEpHistory = com.lagradost.common.storage.WatchHistory(
                            parentId = history.parentId,
                            showName = history.showName,
                            showUrl = history.showUrl,
                            apiName = history.apiName,
                            posterUrl = history.posterUrl,
                            episodeThumbnailUrl = nextEp.posterUrl,
                            screenshotUrl = null,
                            episode = nextEp.episode,
                            season = nextEp.season,
                            episodeId = nextEp.data,
                            position = 0,
                            duration = 0,
                            updateTime = System.currentTimeMillis() + 1000,
                        )
                        com.lagradost.common.storage.DesktopDataStore.setLastWatched(nextEpHistory)
                        return@launch
                    }
                }
            }
            com.lagradost.common.storage.DesktopDataStore.setLastWatched(history)
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
            updateState { copy(launchData = adjustedData) }

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
                        scrapeAndPlay(provider, adjustedData.history.episodeId!!, adjustedData)
                    }
                }
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

        val pastHistory = com.lagradost.common.storage.DesktopDataStore.getEpisodeWatched(
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
        // Do not cancel loadLinksJob to prevent plugin recursion crashes.
        // loadLinksJob?.cancel()
        updateState {
            copy(
                isScrapingLinks = false,
                isLoadingNextEpisode = false,
            )
        }
    }

    private fun sortLinks(links: List<com.lagradost.cloudstream3.utils.ExtractorLink>, preferredQuality: String): List<com.lagradost.cloudstream3.utils.ExtractorLink> {
        if (preferredQuality == "Auto" || preferredQuality == "Auto / Highest" || preferredQuality == "Highest Available") {
            return links.sortedByDescending { it.quality }
        }

        val targetQuality = when (preferredQuality) {
            "2160p (4K)" -> com.lagradost.cloudstream3.utils.Qualities.P2160.value
            "1080p" -> com.lagradost.cloudstream3.utils.Qualities.P1080.value
            "720p" -> com.lagradost.cloudstream3.utils.Qualities.P720.value
            "480p", "480p / SD" -> com.lagradost.cloudstream3.utils.Qualities.P480.value
            else -> com.lagradost.cloudstream3.utils.Qualities.Unknown.value
        }

        return links.sortedWith(
            compareByDescending<com.lagradost.cloudstream3.utils.ExtractorLink> {
                it.quality == targetQuality
            }.thenByDescending {
                it.quality
            },
        )
    }

    private suspend fun scrapeAndPlay(
        provider: com.lagradost.cloudstream3.MainAPI,
        targetEpisodeId: String,
        baseLaunchData: VideoLaunchData,
        targetEpisodeData: Episode? = null,
    ) {
        val hasStartedPlaying = AtomicBoolean(false)

        // Fetch DB data outside of the callbacks and StateFlow CAS loops!
        val current = uiState.value.launchData ?: baseLaunchData
        val pastHistory = if (targetEpisodeData != null) {
            com.lagradost.common.storage.DesktopDataStore.getEpisodeWatched(
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

        try {
            provider.loadLinks(
                data = targetEpisodeId,
                isCasting = false,
                subtitleCallback = { sub ->
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
                callback = { link ->
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

                            copy(
                                nextEpisodeLinks = newLinks,
                                launchData = newLaunchData,
                                targetEpisodeData = null,
                                isLoadingNextEpisode = false,
                                nextEpisodeError = null,
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

            updateState {
                val prefQuality = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_PREFERRED_QUALITY) ?: "Auto"
                val sortedLinks = sortLinks(nextEpisodeLinks, prefQuality)
                val newLaunchData = launchData?.copy(links = sortedLinks)

                if (!hasStartedPlaying.get()) {
                    copy(
                        isScrapingLinks = false,
                        targetEpisodeData = null,
                        isLoadingNextEpisode = false,
                        nextEpisodeError = "No links found for this episode.",
                    )
                } else {
                    copy(
                        isScrapingLinks = false,
                        nextEpisodeLinks = sortedLinks,
                        launchData = newLaunchData,
                    )
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.e("Failed to load links", e)
            updateState {
                copy(
                    isScrapingLinks = false,
                    targetEpisodeData = null,
                    isLoadingNextEpisode = false,
                    nextEpisodeError = "Failed to load links: ${e.message}",
                )
            }
        }
    }
}
