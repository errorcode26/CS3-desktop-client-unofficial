package com.lagradost.cloudstream3.desktop.ui.screens.downloads

import com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager
import com.lagradost.cloudstream3.desktop.downloader.DownloadTask
import com.lagradost.cloudstream3.desktop.ui.base.BaseMviViewModel
import com.lagradost.cloudstream3.desktop.ui.components.AppToastManager
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsTab
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsUiEffect
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsUiEvent
import com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

typealias DownloadsTab = com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsTab
typealias DownloadsUiState = com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract.DownloadsUiState

class DownloadsViewModel : BaseMviViewModel<DownloadsUiState, DownloadsUiEvent, DownloadsUiEffect>(
    initialState = DownloadsUiState(
        downloadPath = DesktopDownloadManager.downloadsDir.absolutePath,
        downloadThreads = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS) ?: 8f,
        maxConcurrent = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT) ?: 2f,
    ),
) {
    init {
        viewModelScope.launch {
            DesktopDownloadManager.tasks.collect { taskList ->
                updateState { copy(tasks = taskList) }
            }
        }
        viewModelScope.launch {
            DesktopDownloadManager.activeSpeed.collect { speed ->
                updateState { copy(totalActiveSpeed = speed) }
            }
        }
    }

    override fun handleEvent(event: DownloadsUiEvent) {
        when (event) {
            is DownloadsUiEvent.SelectTab -> updateState { copy(activeTab = event.tab) }
            is DownloadsUiEvent.UpdateSearchQuery -> updateState { copy(searchQuery = event.query) }
            is DownloadsUiEvent.PauseTask -> DesktopDownloadManager.pause(event.taskId)
            is DownloadsUiEvent.ResumeTask -> DesktopDownloadManager.resume(event.taskId)
            is DownloadsUiEvent.CancelTask -> DesktopDownloadManager.cancel(event.taskId)
            is DownloadsUiEvent.DeleteTask -> deleteTaskInternal(event.task, event.deleteFile)
            is DownloadsUiEvent.DeleteShow -> deleteShowInternal(event.showName, event.deleteFiles)
            is DownloadsUiEvent.PauseAll -> DesktopDownloadManager.pauseAll()
            is DownloadsUiEvent.ResumeAll -> DesktopDownloadManager.resumeAll()
            is DownloadsUiEvent.CancelAll -> DesktopDownloadManager.cancelAll()
            is DownloadsUiEvent.CleanOrphanedJunk -> cleanOrphanedJunkInternal()
            is DownloadsUiEvent.DismissJunkMessage -> updateState { copy(reclaimedBytesMessage = null) }
            is DownloadsUiEvent.ToggleSettingsDialog -> toggleSettings(event.open)
            is DownloadsUiEvent.UpdateDownloadPath -> updateDownloadPathInternal(event.path)
            is DownloadsUiEvent.UpdateDownloadThreads -> updateDownloadThreadsInternal(event.threads)
            is DownloadsUiEvent.UpdateMaxConcurrent -> updateMaxConcurrentInternal(event.max)
        }
    }

    private fun deleteTaskInternal(task: DownloadTask, deleteFile: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = DesktopDownloadManager.delete(task.id, deleteFile)
            val msg = if (ok) "Deleted '${task.displayTitle}'" else "Removed '${task.displayTitle}' from downloads. File is currently locked by a player."
            sendEffect(DownloadsUiEffect.ShowToast(msg, isError = !ok))
            withContext(Dispatchers.Main) {
                if (ok) {
                    AppToastManager.showInfo(msg)
                } else {
                    AppToastManager.showWarning(msg)
                }
            }
        }
    }

    private fun deleteShowInternal(showName: String, deleteFiles: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val showTasks = uiState.value.tasks.filter { it.showName.equals(showName, ignoreCase = true) }
            var deletedCount = 0
            var totalBytesReclaimed = 0L
            for (t in showTasks) {
                totalBytesReclaimed += t.downloadedBytes
                val ok = DesktopDownloadManager.delete(t.id, deleteFiles)
                if (ok) deletedCount++
            }
            if (deletedCount > 0) {
                val msg = "Deleted $deletedCount episodes of '$showName' (${formatSize(totalBytesReclaimed)})"
                sendEffect(DownloadsUiEffect.ShowToast(msg))
                withContext(Dispatchers.Main) {
                    AppToastManager.showInfo(msg)
                }
            }
        }
    }

    private fun cleanOrphanedJunkInternal() {
        viewModelScope.launch(Dispatchers.IO) {
            val reclaimed = DesktopDownloadManager.cleanOrphanedTempFiles()
            val msg = if (reclaimed > 0) {
                "Successfully purged ${formatSize(reclaimed)} of orphaned temp chunks and dangling files."
            } else {
                "No orphaned temporary files found. Download storage is 100% clean."
            }
            updateState { copy(reclaimedBytesMessage = msg) }
        }
    }

    private fun toggleSettings(open: Boolean) {
        if (open) {
            val currentPath = DesktopDownloadManager.downloadsDir.absolutePath
            val threads = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS) ?: 8f
            val maxConcurrent = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT) ?: 2f
            updateState {
                copy(
                    isSettingsOpen = true,
                    downloadPath = currentPath,
                    downloadThreads = threads,
                    maxConcurrent = maxConcurrent,
                )
            }
        } else {
            updateState { copy(isSettingsOpen = false) }
        }
    }

    private fun updateDownloadPathInternal(newPath: String) {
        updateState { copy(downloadPath = newPath) }
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_PATH, newPath)
        }
    }

    private fun updateDownloadThreadsInternal(threads: Float) {
        updateState { copy(downloadThreads = threads) }
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS, threads)
        }
    }

    private fun updateMaxConcurrentInternal(max: Float) {
        updateState { copy(maxConcurrent = max) }
        viewModelScope.launch(Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT, max)
            DesktopDownloadManager.dispatchNextTasks()
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
}
