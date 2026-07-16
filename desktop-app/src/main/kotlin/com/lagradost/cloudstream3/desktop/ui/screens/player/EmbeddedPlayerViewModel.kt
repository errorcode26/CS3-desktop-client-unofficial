package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class EmbeddedPlayerViewModel(private val coroutineScope: CoroutineScope) {

    private val _launchData = MutableStateFlow<VideoLaunchData?>(null)
    val launchData: StateFlow<VideoLaunchData?> = _launchData.asStateFlow()

    private val _isLoadingNextEpisode = MutableStateFlow(false)
    val isLoadingNextEpisode: StateFlow<Boolean> = _isLoadingNextEpisode.asStateFlow()

    private val _nextEpisodeError = MutableStateFlow<String?>(null)
    val nextEpisodeError: StateFlow<String?> = _nextEpisodeError.asStateFlow()

    private val _nextEpisodeLinks = MutableStateFlow<List<ExtractorLink>>(emptyList())
    val nextEpisodeLinks: StateFlow<List<ExtractorLink>> = _nextEpisodeLinks.asStateFlow()

    private val _nextEpisodeSubtitles = MutableStateFlow<List<SubtitleFile>>(emptyList())
    val nextEpisodeSubtitles: StateFlow<List<SubtitleFile>> = _nextEpisodeSubtitles.asStateFlow()

    private val _isScrapingLinks = MutableStateFlow(false)
    val isScrapingLinks: StateFlow<Boolean> = _isScrapingLinks.asStateFlow()

    private val _targetEpisodeData = MutableStateFlow<Episode?>(null)
    val targetEpisodeData: StateFlow<Episode?> = _targetEpisodeData.asStateFlow()

    private var loadLinksJob: Job? = null

    fun init(initialData: VideoLaunchData) {
        if (_launchData.value == null) {
            val isFinished = initialData.history.duration > 0 && initialData.history.position >= initialData.history.duration - 15
            val adjustedData = if (isFinished) {
                initialData.copy(
                    startPositionMs = 0L,
                    history = initialData.history.copy(position = 0L),
                )
            } else {
                initialData
            }
            _launchData.value = adjustedData

            // Auto-scrape initial episode if links are empty
            if (adjustedData.links.isEmpty() && adjustedData.history.episodeId != null) {
                val apiName = adjustedData.loadResponse?.apiName
                val provider = APIHolder.getApiFromNameNull(apiName ?: "")
                if (provider != null) {
                    _isLoadingNextEpisode.value = true
                    _isScrapingLinks.value = true
                    _targetEpisodeData.value = provider.newEpisode(adjustedData.history.episodeId!!) {
                        this.name = adjustedData.history.showName
                        this.season = adjustedData.history.season
                        this.episode = adjustedData.history.episode
                    }
                    _nextEpisodeLinks.value = emptyList()
                    _nextEpisodeSubtitles.value = adjustedData.subtitles
                    
                    var hasStartedPlaying = false

                    loadLinksJob = coroutineScope.launch(Dispatchers.IO) {
                        try {
                            provider.loadLinks(
                                data = adjustedData.history.episodeId!!,
                                isCasting = false,
                                subtitleCallback = { sub ->
                                    if (!hasStartedPlaying) {
                                        _nextEpisodeSubtitles.value = _nextEpisodeSubtitles.value + sub
                                    } else {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            val current = _launchData.value
                                            if (current != null && current.history.episodeId == adjustedData.history.episodeId) {
                                                _launchData.value = current.copy(subtitles = current.subtitles + sub)
                                            }
                                        }
                                    }
                                },
                                callback = { link ->
                                    if (!hasStartedPlaying) {
                                        _nextEpisodeLinks.value = _nextEpisodeLinks.value + link
                                        hasStartedPlaying = true
                                        coroutineScope.launch(Dispatchers.Main) {
                                            val newLaunchData = adjustedData.copy(
                                                links = _nextEpisodeLinks.value.toList(),
                                                subtitles = _nextEpisodeSubtitles.value.toList(),
                                                initialIndex = 0,
                                            )
                                            _launchData.value = newLaunchData
                                            _isLoadingNextEpisode.value = false
                                        }
                                    } else {
                                        coroutineScope.launch(Dispatchers.Main) {
                                            val current = _launchData.value
                                            if (current != null && current.history.episodeId == adjustedData.history.episodeId) {
                                                _launchData.value = current.copy(links = current.links + link)
                                            }
                                        }
                                    }
                                },
                            )

                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                _isScrapingLinks.value = false
                                if (!hasStartedPlaying) {
                                    _isLoadingNextEpisode.value = false
                                }
                            }
                        } catch (e: kotlinx.coroutines.CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            e.printStackTrace()
                            kotlinx.coroutines.withContext(Dispatchers.Main) {
                                _isScrapingLinks.value = false
                                _isLoadingNextEpisode.value = false
                            }
                        }
                    }
                }
            }
        }
    }

    fun loadEpisode(episode: Episode) {
        val currentData = _launchData.value ?: return

        loadLinksJob?.cancel()
        _isLoadingNextEpisode.value = true
        _isScrapingLinks.value = true
        _nextEpisodeError.value = null
        _nextEpisodeLinks.value = emptyList()
        _nextEpisodeSubtitles.value = emptyList()
        _targetEpisodeData.value = episode

        val apiName = currentData.loadResponse?.apiName
        val provider = APIHolder.getApiFromNameNull(apiName ?: "")
        
        var hasStartedPlaying = false

        if (provider != null && episode.data.isNotBlank()) {
            loadLinksJob = coroutineScope.launch(Dispatchers.IO) {
                try {
                    provider.loadLinks(
                        data = episode.data,
                        isCasting = false,
                        subtitleCallback = { sub ->
                            if (!hasStartedPlaying) {
                                _nextEpisodeSubtitles.value = _nextEpisodeSubtitles.value + sub
                            } else {
                                coroutineScope.launch(Dispatchers.Main) {
                                    val current = _launchData.value
                                    if (current != null && current.history.episodeId == episode.data) {
                                        _launchData.value = current.copy(subtitles = current.subtitles + sub)
                                    }
                                }
                            }
                        },
                        callback = { link ->
                            if (!hasStartedPlaying) {
                                _nextEpisodeLinks.value = _nextEpisodeLinks.value + link
                                hasStartedPlaying = true
                                coroutineScope.launch(Dispatchers.Main) {
                                    playLoadedEpisode()
                                    _isLoadingNextEpisode.value = false
                                }
                            } else {
                                coroutineScope.launch(Dispatchers.Main) {
                                    val current = _launchData.value
                                    if (current != null && current.history.episodeId == episode.data) {
                                        _launchData.value = current.copy(links = current.links + link)
                                    }
                                }
                            }
                        },
                    )

                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        _isScrapingLinks.value = false
                        if (!hasStartedPlaying) {
                            _isLoadingNextEpisode.value = false
                            _nextEpisodeError.value = "No links found for this episode."
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e.printStackTrace()
                    kotlinx.coroutines.withContext(Dispatchers.Main) {
                        _isScrapingLinks.value = false
                        if (!hasStartedPlaying) {
                            _isLoadingNextEpisode.value = false
                            _nextEpisodeError.value = "Failed to load links: ${e.message}"
                        }
                    }
                }
            }
        } else {
            _isLoadingNextEpisode.value = false
            _isScrapingLinks.value = false
        }
    }

    fun cancelLoading() {
        loadLinksJob?.cancel()
        _isLoadingNextEpisode.value = false
        _nextEpisodeError.value = null
    }

    fun playLoadedEpisode() {
        val currentData = _launchData.value ?: return
        val epData = _targetEpisodeData.value ?: return
        if (_nextEpisodeLinks.value.isEmpty()) return

        val pastHistory = com.lagradost.common.storage.DesktopDataStore.getEpisodeWatched(
            parentId = currentData.history.parentId,
            episodeId = epData.data
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
            links = _nextEpisodeLinks.value.toList(),
            subtitles = _nextEpisodeSubtitles.value.toList(),
            history = newHistory,
            initialIndex = 0,
            startPositionMs = startPos, // Resume exactly where they left off
            title = buildString {
                append(newHistory.showName)
                if (newHistory.season != null && newHistory.episode != null) {
                    append(" - S${newHistory.season}E${newHistory.episode}")
                } else if (newHistory.episode != null) {
                    append(" - E${newHistory.episode}")
                }
            },
        )

        _isLoadingNextEpisode.value = false
        _nextEpisodeError.value = null
        _targetEpisodeData.value = null
        _nextEpisodeLinks.value = emptyList()
        _nextEpisodeSubtitles.value = emptyList()
        _launchData.value = newLaunchData
    }

    fun getEpisodesList(): List<Episode> {
        val currentData = _launchData.value ?: return emptyList()
        return when (val resp = currentData.loadResponse) {
            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> resp.episodes
            is com.lagradost.cloudstream3.AnimeLoadResponse -> {
                val dub = resp.episodes.entries.firstOrNull { entry -> entry.value.any { it.data == currentData.history.episodeId } }?.key
                resp.episodes[dub] ?: emptyList()
            }
            else -> emptyList()
        }
    }

    fun loadNextEpisode() {
        val episodes = getEpisodesList()
        val currentData = _launchData.value ?: return
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        val nextEpisode = if (currentIndex != -1 && currentIndex + 1 < episodes.size) episodes[currentIndex + 1] else null

        if (nextEpisode != null) {
            loadEpisode(nextEpisode)
        }
    }

    fun loadPrevEpisode() {
        val episodes = getEpisodesList()
        val currentData = _launchData.value ?: return
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        val prevEpisode = if (currentIndex > 0) episodes[currentIndex - 1] else null

        if (prevEpisode != null) {
            loadEpisode(prevEpisode)
        }
    }

    fun hasNextEpisode(): Boolean {
        val episodes = getEpisodesList()
        val currentData = _launchData.value ?: return false
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        return currentIndex != -1 && currentIndex + 1 < episodes.size
    }

    fun getNextEpisode(): com.lagradost.cloudstream3.Episode? {
        val episodes = getEpisodesList()
        val currentData = _launchData.value ?: return null
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        return if (currentIndex != -1 && currentIndex + 1 < episodes.size) episodes[currentIndex + 1] else null
    }

    fun hasPrevEpisode(): Boolean {
        val episodes = getEpisodesList()
        val currentData = _launchData.value ?: return false
        val currentIndex = episodes.indexOfFirst { it.data == currentData.history.episodeId }
        return currentIndex > 0
    }

    fun cancelScraping() {
        loadLinksJob?.cancel()
        _isScrapingLinks.value = false
        _isLoadingNextEpisode.value = false
    }
}
