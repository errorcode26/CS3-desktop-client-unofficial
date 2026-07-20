package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.player.contract.PlayerUiState
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class EmbeddedPlayerViewModel : BaseMviViewModel<PlayerUiState, PlayerUiEvent, PlayerUiEffect>(
    initialState = PlayerUiState()
) {
    private var loadLinksJob: Job? = null

    override fun dispose() {
        super.dispose()
        loadLinksJob?.cancel()
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

                    val hasStartedPlaying = AtomicBoolean(false)

                    loadLinksJob = viewModelScope.launch(Dispatchers.IO) {
                        try {
                            provider.loadLinks(
                                data = adjustedData.history.episodeId!!,
                                isCasting = false,
                                subtitleCallback = { sub ->
                                    updateState {
                                        val newSubs = nextEpisodeSubtitles + sub
                                        if (!hasStartedPlaying.get()) {
                                            copy(nextEpisodeSubtitles = newSubs)
                                        } else {
                                            val current = launchData
                                            val updatedLaunch = if (current != null && current.history.episodeId == adjustedData.history.episodeId) {
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
                                        val newLinks = nextEpisodeLinks + link
                                        if (hasStartedPlaying.compareAndSet(false, true)) {
                                            val newLaunchData = adjustedData.copy(
                                                links = newLinks,
                                                subtitles = nextEpisodeSubtitles,
                                                initialIndex = 0,
                                            )
                                            copy(
                                                nextEpisodeLinks = newLinks,
                                                launchData = newLaunchData,
                                                targetEpisodeData = null,
                                                isLoadingNextEpisode = false,
                                            )
                                        } else {
                                            val current = launchData
                                            val updatedLaunch = if (current != null && current.history.episodeId == adjustedData.history.episodeId) {
                                                current.copy(links = current.links + link)
                                            } else {
                                                current
                                            }
                                            copy(nextEpisodeLinks = newLinks, launchData = updatedLaunch)
                                        }
                                    }
                                },
                            )

                            updateState {
                                if (!hasStartedPlaying.get()) {
                                    copy(
                                        isScrapingLinks = false,
                                        targetEpisodeData = null,
                                        isLoadingNextEpisode = false,
                                    )
                                } else {
                                    copy(isScrapingLinks = false)
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            updateState {
                                copy(
                                    isScrapingLinks = false,
                                    targetEpisodeData = null,
                                    isLoadingNextEpisode = false,
                                )
                            }
                        }
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

        val hasStartedPlaying = AtomicBoolean(false)

        if (provider != null && episode.data.isNotBlank()) {
            loadLinksJob = viewModelScope.launch(Dispatchers.IO) {
                try {
                    provider.loadLinks(
                        data = episode.data,
                        isCasting = false,
                        subtitleCallback = { sub ->
                            updateState {
                                val newSubs = nextEpisodeSubtitles + sub
                                if (!hasStartedPlaying.get()) {
                                    copy(nextEpisodeSubtitles = newSubs)
                                } else {
                                    val current = launchData
                                    val updatedLaunch = if (current != null && current.history.episodeId == episode.data) {
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
                                val newLinks = nextEpisodeLinks + link
                                if (hasStartedPlaying.compareAndSet(false, true)) {
                                    val current = launchData
                                    val epData = targetEpisodeData
                                    if (current != null && epData != null && newLinks.isNotEmpty()) {
                                        val pastHistory = com.lagradost.common.storage.DesktopDataStore.getEpisodeWatched(
                                            parentId = current.history.parentId,
                                            episodeId = epData.data,
                                        )

                                        val startPos = if (pastHistory != null && pastHistory.duration > 0 && pastHistory.position < pastHistory.duration - 15) {
                                            pastHistory.position * 1000L
                                        } else {
                                            0L
                                        }

                                        val newHistory = current.history.copy(
                                            episodeId = epData.data,
                                            episode = epData.episode,
                                            season = epData.season,
                                            position = startPos / 1000L,
                                            duration = pastHistory?.duration ?: 0L,
                                        )

                                        val newLaunchData = current.copy(
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

                                        copy(
                                            nextEpisodeLinks = newLinks,
                                            isLoadingNextEpisode = false,
                                            nextEpisodeError = null,
                                            targetEpisodeData = null,
                                            launchData = newLaunchData,
                                        )
                                    } else {
                                        copy(
                                            nextEpisodeLinks = newLinks,
                                            isLoadingNextEpisode = false,
                                        )
                                    }
                                } else {
                                    val current = launchData
                                    val updatedLaunch = if (current != null && current.history.episodeId == episode.data) {
                                        current.copy(links = current.links + link)
                                    } else {
                                        current
                                    }
                                    copy(nextEpisodeLinks = newLinks, launchData = updatedLaunch)
                                }
                            }
                        },
                    )

                    updateState {
                        if (!hasStartedPlaying.get()) {
                            copy(
                                isScrapingLinks = false,
                                targetEpisodeData = null,
                                isLoadingNextEpisode = false,
                                nextEpisodeError = "No links found for this episode.",
                            )
                        } else {
                            copy(isScrapingLinks = false)
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateState {
                        if (!hasStartedPlaying.get()) {
                            copy(
                                isScrapingLinks = false,
                                targetEpisodeData = null,
                                isLoadingNextEpisode = false,
                                nextEpisodeError = "Failed to load links: ${e.message}",
                            )
                        } else {
                            copy(isScrapingLinks = false)
                        }
                    }
                }
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

    fun getEpisodesList(): List<Episode> {
        val currentData = uiState.value.launchData ?: return emptyList()
        return when (val resp = currentData.loadResponse) {
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> resp.episodes
            is com.lagradost.cloudstream3.AnimeLoadResponse -> {
                val dub = resp.episodes.entries.firstOrNull { entry -> entry.value.any { it.data == currentData.history.episodeId } }?.key
                resp.episodes[dub] ?: emptyList()
            }
            else -> emptyList()
        }
    }

    private fun loadNextEpisode() {
        val episodes = getEpisodesList()
        val currentData = uiState.value.launchData ?: return
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        val nextEpisode = if (currentIndex != -1 && currentIndex + 1 < episodes.size) episodes[currentIndex + 1] else null

        if (nextEpisode != null) {
            loadEpisode(nextEpisode)
        }
    }

    private fun loadPrevEpisode() {
        val episodes = getEpisodesList()
        val currentData = uiState.value.launchData ?: return
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        val prevEpisode = if (currentIndex > 0) episodes[currentIndex - 1] else null

        if (prevEpisode != null) {
            loadEpisode(prevEpisode)
        }
    }

    fun hasNextEpisode(): Boolean {
        val episodes = getEpisodesList()
        val currentData = uiState.value.launchData ?: return false
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        return currentIndex != -1 && currentIndex + 1 < episodes.size
    }

    fun getNextEpisode(): com.lagradost.cloudstream3.Episode? {
        val episodes = getEpisodesList()
        val currentData = uiState.value.launchData ?: return null
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        return if (currentIndex != -1 && currentIndex + 1 < episodes.size) episodes[currentIndex + 1] else null
    }

    fun hasPrevEpisode(): Boolean {
        val episodes = getEpisodesList()
        val currentData = uiState.value.launchData ?: return false
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        return currentIndex > 0
    }

    private fun cancelScraping() {
        loadLinksJob?.cancel()
        updateState {
            copy(
                isScrapingLinks = false,
                isLoadingNextEpisode = false,
            )
        }
    }
}
