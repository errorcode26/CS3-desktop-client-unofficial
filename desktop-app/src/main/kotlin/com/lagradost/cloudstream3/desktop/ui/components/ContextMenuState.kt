package com.lagradost.cloudstream3.desktop.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.desktop.downloader.DownloadTask
import com.lagradost.common.storage.DesktopBookmark
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.DesktopWatchType
import com.lagradost.common.storage.WatchHistory

enum class ContextMenuType {
    POSTER,
    WATCH_HISTORY,
    EPISODE,
    BOOKMARK,
    DOWNLOAD,
}

object GlobalContextMenuState {
    var isActive by mutableStateOf(false)
    var bounds by mutableStateOf(Rect.Zero)
    var menuType by mutableStateOf(ContextMenuType.POSTER)

    var searchResponse: SearchResponse? by mutableStateOf(null)
    var watchHistory: WatchHistory? by mutableStateOf(null)
    var bookmark: DesktopBookmark? by mutableStateOf(null)
    var provider: MainAPI? by mutableStateOf(null)
    var downloadTask: DownloadTask? by mutableStateOf(null)
    var showTaskCount: Int by mutableStateOf(0)
    var showTotalBytes: Long by mutableStateOf(0L)

    var episode: Episode? by mutableStateOf(null)
    var loadResponse: LoadResponse? by mutableStateOf(null)
    var isAntiSpoiler: Boolean by mutableStateOf(false)
    var enableDownloadButtons: Boolean by mutableStateOf(true)

    var onRemove: (() -> Unit)? by mutableStateOf(null)
    var onDetailsClick: (() -> Unit)? by mutableStateOf(null)
    var onPlayClick: (() -> Unit)? by mutableStateOf(null)
    var onChangeCategory: ((DesktopWatchType) -> Unit)? by mutableStateOf(null)
    var onReLink: (() -> Unit)? by mutableStateOf(null)
    var onSearchOtherProviders: (() -> Unit)? by mutableStateOf(null)
    var onOpenInExplorer: (() -> Unit)? by mutableStateOf(null)
    var onDeleteShow: (() -> Unit)? by mutableStateOf(null)

    var onPlayEpisode: ((Episode) -> Unit)? by mutableStateOf(null)
    var onDownloadEpisode: ((Episode) -> Unit)? by mutableStateOf(null)
    var onToggleWatched: ((Episode, Boolean) -> Unit)? by mutableStateOf(null)
    var onRemoveEpisodeWatched: ((Episode) -> Unit)? by mutableStateOf(null)
    var onMarkPreviousWatched: ((Episode) -> Unit)? by mutableStateOf(null)

    fun dismiss() {
        isActive = false
    }

    fun clear() {
        searchResponse = null
        watchHistory = null
        bookmark = null
        provider = null
        downloadTask = null
        showTaskCount = 0
        showTotalBytes = 0L
        episode = null
        loadResponse = null
        isAntiSpoiler = false
        enableDownloadButtons = false
        onRemove = null
        onDetailsClick = null
        onPlayClick = null
        onChangeCategory = null
        onReLink = null
        onSearchOtherProviders = null
        onOpenInExplorer = null
        onDeleteShow = null
        onPlayEpisode = null
        onDownloadEpisode = null
        onToggleWatched = null
        onRemoveEpisodeWatched = null
        onMarkPreviousWatched = null
    }

    fun showForPoster(
        bounds: Rect,
        item: SearchResponse,
        provider: MainAPI?,
        onClick: (() -> Unit)?,
        onPlayClick: (() -> Unit)? = null,
    ) {
        this.bounds = bounds
        this.searchResponse = item
        this.provider = provider
        this.onDetailsClick = onClick
        this.onPlayClick = onPlayClick
        this.menuType = ContextMenuType.POSTER
        this.isActive = true
    }

    fun showForBookmark(
        bounds: Rect,
        bookmark: DesktopBookmark,
        provider: MainAPI?,
        onClick: (() -> Unit)?,
        onPlayClick: (() -> Unit)? = null,
        onRemove: () -> Unit,
        onChangeCategory: (DesktopWatchType) -> Unit,
        onReLink: () -> Unit,
        onSearchOtherProviders: () -> Unit,
    ) {
        this.bounds = bounds
        this.bookmark = bookmark
        this.provider = provider
        this.onDetailsClick = onClick
        this.onPlayClick = onPlayClick
        this.onRemove = onRemove
        this.onChangeCategory = onChangeCategory
        this.onReLink = onReLink
        this.onSearchOtherProviders = onSearchOtherProviders
        this.menuType = ContextMenuType.BOOKMARK
        this.isActive = true
    }

    fun showForWatchHistory(
        bounds: Rect,
        history: WatchHistory,
        provider: MainAPI?,
        onRemove: () -> Unit,
        onClick: (() -> Unit)?,
        onPlayClick: (() -> Unit)? = null,
    ) {
        this.bounds = bounds
        this.watchHistory = history
        this.provider = provider
        this.onRemove = onRemove
        this.onDetailsClick = onClick
        this.onPlayClick = onPlayClick
        this.menuType = ContextMenuType.WATCH_HISTORY
        this.isActive = true
    }

    fun showForEpisode(
        episode: Episode,
        loadResponse: LoadResponse,
        history: WatchHistory?,
        provider: MainAPI?,
        isAntiSpoiler: Boolean = false,
        enableDownloadButtons: Boolean = true,
        onPlay: (Episode) -> Unit,
        onDownload: ((Episode) -> Unit)? = null,
        onToggleWatched: (Episode, Boolean) -> Unit,
        onRemoveEpisodeWatched: (Episode) -> Unit,
        onMarkPreviousWatched: ((Episode) -> Unit)? = null,
    ) {
        this.episode = episode
        this.loadResponse = loadResponse
        val resolvedHistory = history ?: run {
            val pName = provider?.name ?: loadResponse.apiName
            val parentId = DesktopDataStore.watchHistoryId(pName, loadResponse.url)
            DesktopDataStore.getEpisodeWatched(parentId, episode.data)
        }
        this.watchHistory = resolvedHistory
        this.provider = provider
        this.isAntiSpoiler = isAntiSpoiler
        this.enableDownloadButtons = enableDownloadButtons
        this.onPlayEpisode = onPlay
        this.onDownloadEpisode = onDownload
        this.onToggleWatched = onToggleWatched
        this.onRemoveEpisodeWatched = onRemoveEpisodeWatched
        this.onMarkPreviousWatched = onMarkPreviousWatched
        this.menuType = ContextMenuType.EPISODE
        this.isActive = true
    }

    fun showForDownload(
        bounds: Rect,
        task: DownloadTask,
        allShowTasks: List<DownloadTask>,
        onPlay: () -> Unit,
        onDelete: () -> Unit,
        onDeleteShow: (() -> Unit)? = null,
        onOpenInExplorer: (() -> Unit)? = null,
    ) {
        this.bounds = bounds
        this.downloadTask = task
        this.showTaskCount = allShowTasks.size
        this.showTotalBytes = allShowTasks.sumOf { it.downloadedBytes }
        this.onPlayClick = onPlay
        this.onRemove = onDelete
        this.onDeleteShow = onDeleteShow
        this.onOpenInExplorer = onOpenInExplorer
        this.menuType = ContextMenuType.DOWNLOAD
        this.isActive = true
    }
}
