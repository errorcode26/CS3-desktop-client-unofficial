package com.lagradost.cloudstream3.desktop.ui.screens.downloads

import com.arkivanov.essenty.instancekeeper.InstanceKeeper
import com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager
import com.lagradost.cloudstream3.desktop.downloader.DownloadStatus
import com.lagradost.cloudstream3.desktop.downloader.DownloadTask
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class DownloadsTab(val title: String) {
    ALL("All Downloads"),
    SHOWS("Shows & Series"),
    MOVIES("Movies"),
    ACTIVE_QUEUE("Active Queue"),
}

data class DownloadsUiState(
    val activeTab: DownloadsTab = DownloadsTab.ALL,
    val searchQuery: String = "",
    val tasks: List<DownloadTask> = emptyList(),
    val totalActiveSpeed: Long = 0L,
    val isSettingsOpen: Boolean = false,
    val reclaimedBytesMessage: String? = null,
    val downloadPath: String = "",
    val downloadThreads: Float = 8f,
    val maxConcurrent: Float = 2f,
) {
    val activeTasks: List<DownloadTask>
        get() = tasks.filter { it.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED) }

    val completedTasks: List<DownloadTask>
        get() = tasks
            .filter { it.status == DownloadStatus.COMPLETED && it.existsOnDisk }
            .distinctBy { it.filePath }

    val filteredCompletedTasks: List<DownloadTask>
        get() {
            var list = completedTasks
            if (searchQuery.isNotBlank()) {
                list = list.filter {
                    it.showName.contains(searchQuery, ignoreCase = true) ||
                    (it.episodeTitle?.contains(searchQuery, ignoreCase = true) == true)
                }
            }
            return when (activeTab) {
                DownloadsTab.ALL -> list
                DownloadsTab.SHOWS -> list.filter { !it.isMovie }
                DownloadsTab.MOVIES -> list.filter { it.isMovie }
                DownloadsTab.ACTIVE_QUEUE -> list
            }
        }
}

class DownloadsViewModel : InstanceKeeper.Instance {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(
        DownloadsUiState(
            downloadPath = DesktopDownloadManager.downloadsDir.absolutePath,
            downloadThreads = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS) ?: 8f,
            maxConcurrent = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT) ?: 2f,
        )
    )
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        scope.launch {
            DesktopDownloadManager.tasks.collect { taskList ->
                _uiState.value = _uiState.value.copy(tasks = taskList)
            }
        }
        scope.launch {
            DesktopDownloadManager.activeSpeed.collect { speed ->
                _uiState.value = _uiState.value.copy(totalActiveSpeed = speed)
            }
        }
    }

    fun setTab(tab: DownloadsTab) {
        _uiState.value = _uiState.value.copy(activeTab = tab)
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun openSettings() {
        val currentPath = DesktopDownloadManager.downloadsDir.absolutePath
        val threads = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS) ?: 8f
        val maxConcurrent = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT) ?: 2f
        _uiState.value = _uiState.value.copy(
            isSettingsOpen = true,
            downloadPath = currentPath,
            downloadThreads = threads,
            maxConcurrent = maxConcurrent,
        )
    }

    fun closeSettings() {
        _uiState.value = _uiState.value.copy(isSettingsOpen = false)
    }

    fun dismissJunkMessage() {
        _uiState.value = _uiState.value.copy(reclaimedBytesMessage = null)
    }

    fun updateDownloadPath(newPath: String) {
        _uiState.value = _uiState.value.copy(downloadPath = newPath)
        scope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_PATH, newPath)
        }
    }

    fun updateDownloadThreads(threads: Float) {
        _uiState.value = _uiState.value.copy(downloadThreads = threads)
        scope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS, threads)
        }
    }

    fun updateMaxConcurrent(max: Float) {
        _uiState.value = _uiState.value.copy(maxConcurrent = max)
        scope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT, max)
            DesktopDownloadManager.dispatchNextTasks()
        }
    }

    fun pause(taskId: String) {
        DesktopDownloadManager.pause(taskId)
    }

    fun resume(taskId: String) {
        DesktopDownloadManager.resume(taskId)
    }

    fun cancel(taskId: String) {
        DesktopDownloadManager.cancel(taskId)
    }

    fun pauseAll() {
        DesktopDownloadManager.pauseAll()
    }

    fun resumeAll() {
        DesktopDownloadManager.resumeAll()
    }

    fun cancelAll() {
        DesktopDownloadManager.cancelAll()
    }

    fun cleanOrphanedJunk() {
        scope.launch(Dispatchers.IO) {
            val reclaimed = DesktopDownloadManager.cleanOrphanedTempFiles()
            val msg = if (reclaimed > 0) {
                "Successfully purged ${formatSize(reclaimed)} of orphaned temp chunks and dangling files."
            } else {
                "No orphaned temporary files found. Download storage is 100% clean."
            }
            withContext(Dispatchers.Main) {
                _uiState.value = _uiState.value.copy(reclaimedBytesMessage = msg)
            }
        }
    }

    fun delete(task: DownloadTask, deleteFile: Boolean = true) {
        val ok = DesktopDownloadManager.delete(task.id, deleteFile)
        if (ok) {
            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Deleted '${task.displayTitle}'")
        } else {
            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showWarning("Removed '${task.displayTitle}' from downloads. File is currently locked by a player.")
        }
    }

    fun deleteShow(showName: String, deleteFiles: Boolean = true) {
        val showTasks = _uiState.value.tasks.filter { it.showName.equals(showName, ignoreCase = true) }
        var deletedCount = 0
        var totalBytesReclaimed = 0L
        for (t in showTasks) {
            totalBytesReclaimed += t.downloadedBytes
            val ok = DesktopDownloadManager.delete(t.id, deleteFiles)
            if (ok) deletedCount++
        }
        if (deletedCount > 0) {
            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("Deleted $deletedCount episodes of '$showName' (${formatSize(totalBytesReclaimed)})")
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return "%.1f KB".format(kb)
        val mb = kb / 1024.0
        if (mb < 1024) return "%.1f MB".format(mb)
        val gb = mb / 1024.0
        return "%.2f GB".format(gb)
    }

    override fun onDestroy() {
        scope.cancel()
    }
}
