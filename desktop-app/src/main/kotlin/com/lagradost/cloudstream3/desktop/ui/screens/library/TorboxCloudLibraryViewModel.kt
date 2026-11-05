package com.lagradost.cloudstream3.desktop.ui.screens.library

import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxFile
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxLibraryCache
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxStorageType
import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxTorrentData
import com.lagradost.cloudstream3.desktop.ui.VideoLaunchData
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.screens.library.components.createTorboxEpisode
import com.lagradost.cloudstream3.desktop.ui.screens.library.components.isVideoFile
import com.lagradost.cloudstream3.desktop.ui.screens.library.components.naturalNameComparator
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.TorboxLibraryUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.TorboxLibraryUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.library.contract.TorboxLibraryUiState
import com.lagradost.cloudstream3.desktop.ui.screens.player.LinkCache
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.WatchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TorboxCloudLibraryViewModel : BaseMviViewModel<TorboxLibraryUiState, TorboxLibraryUiEvent, TorboxLibraryUiEffect>(
    initialState = TorboxLibraryUiState(),
) {
    init {
        loadApiKeyAndInitialize()
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.historyUpdates.collect {
                loadRecentHistory()
            }
        }
    }

    override fun handleEvent(event: TorboxLibraryUiEvent) {
        when (event) {
            is TorboxLibraryUiEvent.OnLoadApiKey -> loadApiKeyAndInitialize()
            is TorboxLibraryUiEvent.OnRefresh -> refreshItems(force = event.force)
            is TorboxLibraryUiEvent.OnSelectStorageType -> selectStorageType(event.type)
            is TorboxLibraryUiEvent.OnSearchQueryChange -> updateState { copy(searchQuery = event.query) }
            is TorboxLibraryUiEvent.OnFilterChange -> updateState { copy(selectedFilter = event.filter) }
            is TorboxLibraryUiEvent.OnAddItem -> addItem(event.link, event.onSuccess)
            is TorboxLibraryUiEvent.OnDeleteItem -> deleteItem(event.item, event.onSuccess)
            is TorboxLibraryUiEvent.OnPlayItem -> playItemFile(event.item, event.file, event.onLaunchPlayer)
            is TorboxLibraryUiEvent.OnResumeFromHistory -> resumeFromHistory(event.history, event.onLaunchPlayer)
            is TorboxLibraryUiEvent.OnRemoveHistory -> removeHistory(event.history)
            is TorboxLibraryUiEvent.OnDismissError -> updateState { copy(errorMessage = null) }
            is TorboxLibraryUiEvent.OnDismissAddItemError -> updateState { copy(addItemError = null) }
        }
    }

    private fun loadApiKeyAndInitialize() {
        val apiKey = DesktopDataStore.getKey<String>(DesktopDataStore.PREF_TORBOX_API_KEY)?.trim().orEmpty()
        val storageType = TorboxLibraryCache.lastSelectedStorageType
        val cached = TorboxLibraryCache.get(storageType)
        updateState {
            copy(
                apiKey = apiKey,
                selectedStorageType = storageType,
                itemsList = cached.orEmpty(),
                isLoading = cached == null && apiKey.isNotBlank(),
            )
        }
        loadRecentHistory()
        if (apiKey.isNotBlank()) {
            if (cached != null) {
                if (!TorboxLibraryCache.isFresh(storageType)) {
                    refreshItems(force = true)
                }
            } else {
                refreshItems(force = true)
            }
        }
    }

    private fun selectStorageType(type: TorboxStorageType) {
        if (uiState.value.selectedStorageType == type) return
        TorboxLibraryCache.lastSelectedStorageType = type
        val cached = TorboxLibraryCache.get(type)
        updateState {
            copy(
                selectedStorageType = type,
                searchQuery = "",
                itemsList = cached.orEmpty(),
                isLoading = cached == null && apiKey.isNotBlank(),
                errorMessage = null,
            )
        }
        loadRecentHistory()
        if (cached != null) {
            if (!TorboxLibraryCache.isFresh(type)) {
                refreshItems(force = true)
            }
        } else if (uiState.value.apiKey.isNotBlank()) {
            refreshItems(force = true)
        }
    }

    private fun refreshItems(force: Boolean = false) {
        val apiKey = uiState.value.apiKey
        val storageType = uiState.value.selectedStorageType
        if (apiKey.isBlank()) return

        val cached = TorboxLibraryCache.get(storageType)
        if (!force && cached != null && TorboxLibraryCache.isFresh(storageType)) {
            updateState { copy(itemsList = cached, isLoading = false) }
            return
        }

        if (cached == null) {
            updateState { copy(isLoading = true, errorMessage = null) }
        } else {
            updateState { copy(errorMessage = null) }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val list = TorboxApiClient.listItems(apiKey, storageType)
                TorboxLibraryCache.set(storageType, list)
                updateState {
                    copy(
                        itemsList = list,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
                loadRecentHistory()
            } catch (e: Exception) {
                AppLogger.e("TorboxCloudLibraryViewModel: Failed to load ${storageType.label}", e)
                updateState {
                    copy(
                        errorMessage = e.message ?: "Failed to load ${storageType.label}",
                        isLoading = false,
                    )
                }
            }
        }
    }

    private fun addItem(link: String, onSuccess: () -> Unit) {
        val apiKey = uiState.value.apiKey
        val storageType = uiState.value.selectedStorageType
        val input = link.trim()
        if (input.isBlank() || apiKey.isBlank()) {
            updateState { copy(addItemError = "Please enter a valid link") }
            return
        }

        updateState { copy(isAddingItem = true, addItemError = null) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = when (storageType) {
                    TorboxStorageType.TORRENTS -> TorboxApiClient.createTorrent(apiKey, input, addOnlyIfCached = false) != null
                    TorboxStorageType.WEB_DOWNLOADS -> TorboxApiClient.createWebDownload(apiKey, input)
                    TorboxStorageType.USENET -> TorboxApiClient.createUsenet(apiKey, input)
                }
                if (success) {
                    updateState { copy(isAddingItem = false, addItemError = null) }
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                    refreshItems(force = true)
                } else {
                    updateState {
                        copy(
                            isAddingItem = false,
                            addItemError = "Failed to add to Torbox cloud storage",
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxCloudLibraryViewModel: Error adding item", e)
                updateState {
                    copy(
                        isAddingItem = false,
                        addItemError = e.message ?: "Failed to add to Torbox cloud storage",
                    )
                }
            }
        }
    }

    private fun deleteItem(item: TorboxTorrentData, onSuccess: () -> Unit) {
        val apiKey = uiState.value.apiKey
        if (apiKey.isBlank()) return

        updateState { copy(isDeleting = true) }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val success = TorboxApiClient.deleteItem(apiKey, item)
                if (success) {
                    TorboxLibraryCache.removeItem(item.storageType, item.id)
                    updateState {
                        copy(
                            isDeleting = false,
                            itemsList = itemsList.filterNot { it.id == item.id },
                        )
                    }
                    withContext(Dispatchers.Main) {
                        onSuccess()
                    }
                    refreshItems(force = true)
                } else {
                    updateState { copy(isDeleting = false) }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxCloudLibraryViewModel: Error deleting item", e)
                updateState { copy(isDeleting = false) }
            }
        }
    }

    private fun playItemFile(
        item: TorboxTorrentData,
        file: TorboxFile?,
        onLaunchPlayer: (VideoLaunchData) -> Unit,
    ) {
        val apiKey = uiState.value.apiKey
        if (apiKey.isBlank()) return

        val videoFiles = item.files.filter { isVideoFile(it.name) }.sortedWith(naturalNameComparator)
        val targetFile = file ?: videoFiles.firstOrNull() ?: TorboxApiClient.selectBestVideoFile(item.files)
        val activeFileId = targetFile?.id ?: item.id
        val currentEpisodeId = activeFileId.toString()
        val parentId = "torbox_${item.storageType.name.lowercase()}_${item.id}"

        updateState {
            copy(
                resolvingItemId = item.id,
                resolvingFileId = targetFile?.id,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cached = LinkCache.get(currentEpisodeId)
                val existingLink = cached?.links?.firstOrNull()
                val streamUrl = existingLink?.url
                    ?: TorboxApiClient.requestDownloadLink(apiKey, item, activeFileId)

                if (!streamUrl.isNullOrBlank()) {
                    val videoTitle = targetFile?.name ?: item.name
                    val extLink = existingLink ?: newExtractorLink(
                        source = "Torbox [Cloud]",
                        name = videoTitle,
                        url = streamUrl,
                        type = if (streamUrl.contains(".m3u8", ignoreCase = true)) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO,
                    ) {
                        this.referer = ""
                    }

                    val siblingEpisodes = if (videoFiles.size > 1) {
                        videoFiles.mapIndexed { idx, f -> createTorboxEpisode(f, idx) }
                    } else {
                        emptyList()
                    }

                    val activeEpisodeIndex = if (siblingEpisodes.isNotEmpty()) {
                        val idx = videoFiles.indexOfFirst { it.id == activeFileId }
                        if (idx >= 0) idx + 1 else 1
                    } else {
                        null
                    }

                    TorboxLibraryCache.updateItem(item)
                    if (existingLink == null) {
                        LinkCache.set(currentEpisodeId, listOf(extLink), emptyList())
                    }

                    val pastHistory = DesktopDataStore.getEpisodeWatched(parentId, currentEpisodeId)
                    val startPositionMs = if (pastHistory != null && pastHistory.duration > 0 && pastHistory.position < pastHistory.duration - 15) {
                        pastHistory.position * 1000L
                    } else {
                        0L
                    }

                    val launchData = VideoLaunchData(
                        links = listOf(extLink),
                        initialIndex = 0,
                        title = videoTitle,
                        subtitles = emptyList(),
                        startPositionMs = startPositionMs,
                        history = WatchHistory(
                            parentId = parentId,
                            showName = item.name,
                            showUrl = streamUrl,
                            apiName = "Torbox",
                            posterUrl = null,
                            episodeThumbnailUrl = null,
                            screenshotUrl = null,
                            episode = activeEpisodeIndex,
                            season = if (activeEpisodeIndex != null) 1 else null,
                            episodeId = currentEpisodeId,
                            position = startPositionMs / 1000L,
                            duration = pastHistory?.duration ?: 0L,
                            updateTime = System.currentTimeMillis(),
                            episodeName = videoTitle,
                        ),
                        episodes = siblingEpisodes,
                        enrichedBackdropUrl = null,
                    )

                    updateState {
                        copy(
                            resolvingItemId = null,
                            resolvingFileId = null,
                        )
                    }

                    withContext(Dispatchers.Main) {
                        onLaunchPlayer(launchData)
                    }
                } else {
                    updateState {
                        copy(
                            resolvingItemId = null,
                            resolvingFileId = null,
                            errorMessage = "Failed to resolve stream link from Torbox",
                        )
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxCloudLibraryViewModel: Error resolving stream", e)
                updateState {
                    copy(
                        resolvingItemId = null,
                        resolvingFileId = null,
                        errorMessage = e.message ?: "Failed to generate stream link",
                    )
                }
            }
        }
    }

    private fun resumeFromHistory(
        history: WatchHistory,
        onLaunchPlayer: (VideoLaunchData) -> Unit,
    ) {
        val selectedStorageType = uiState.value.selectedStorageType
        val prefix = "torbox_${selectedStorageType.name.lowercase()}_"
        val itemId = history.parentId.substringAfter(prefix).toIntOrNull() ?: return
        val item = uiState.value.itemsList.find { it.id == itemId } ?: return
        val file = item.files.find { it.id.toString() == history.episodeId }
        playItemFile(item, file, onLaunchPlayer)
    }

    private fun removeHistory(history: WatchHistory) {
        viewModelScope.launch(Dispatchers.IO) {
            DesktopDataStore.removeWatchHistory(history.parentId)
        }
    }

    private fun loadRecentHistory() {
        val selectedStorageType = uiState.value.selectedStorageType
        val itemsList = uiState.value.itemsList
        val prefix = "torbox_${selectedStorageType.name.lowercase()}_"
        val allHistory = DesktopDataStore.getAllWatchHistory()
        val matching = allHistory.filter { it.parentId.startsWith(prefix) }
            .filter { hist ->
                val itemId = hist.parentId.substringAfter(prefix).toIntOrNull()
                itemsList.any { it.id == itemId }
            }
        val active = matching.groupBy { it.parentId }.values.mapNotNull { histories ->
            histories.maxByOrNull { it.updateTime }
        }.sortedByDescending { it.updateTime }
        updateState { copy(recentHistory = active) }
    }
}
