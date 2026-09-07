package com.lagradost.cloudstream3.desktop.ui.screens.downloads.contract

import androidx.compose.runtime.Immutable
import com.lagradost.cloudstream3.desktop.downloader.DownloadStatus
import com.lagradost.cloudstream3.desktop.downloader.DownloadTask
import com.lagradost.cloudstream3.desktop.ui.base.UiEffect
import com.lagradost.cloudstream3.desktop.ui.base.UiEvent
import com.lagradost.cloudstream3.desktop.ui.base.UiState

enum class DownloadsTab(val title: String) {
    ALL("All Downloads"),
    SHOWS("Shows & Series"),
    MOVIES("Movies"),
    ACTIVE_QUEUE("Active Queue"),
}

@Immutable
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
) : UiState {
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

@Immutable
sealed interface DownloadsUiEvent : UiEvent {
    data class SelectTab(val tab: DownloadsTab) : DownloadsUiEvent
    data class UpdateSearchQuery(val query: String) : DownloadsUiEvent
    data class PauseTask(val taskId: String) : DownloadsUiEvent
    data class ResumeTask(val taskId: String) : DownloadsUiEvent
    data class CancelTask(val taskId: String) : DownloadsUiEvent
    data class DeleteTask(val task: DownloadTask, val deleteFile: Boolean = true) : DownloadsUiEvent
    data class DeleteShow(val showName: String, val deleteFiles: Boolean = true) : DownloadsUiEvent
    data object PauseAll : DownloadsUiEvent
    data object ResumeAll : DownloadsUiEvent
    data object CancelAll : DownloadsUiEvent
    data object CleanOrphanedJunk : DownloadsUiEvent
    data object DismissJunkMessage : DownloadsUiEvent
    data class ToggleSettingsDialog(val open: Boolean) : DownloadsUiEvent
    data class UpdateDownloadPath(val path: String) : DownloadsUiEvent
    data class UpdateDownloadThreads(val threads: Float) : DownloadsUiEvent
    data class UpdateMaxConcurrent(val max: Float) : DownloadsUiEvent
}

@Immutable
sealed interface DownloadsUiEffect : UiEffect {
    data class ShowToast(val message: String, val isError: Boolean = false) : DownloadsUiEffect
    data class OpenFolder(val path: String) : DownloadsUiEffect
}
