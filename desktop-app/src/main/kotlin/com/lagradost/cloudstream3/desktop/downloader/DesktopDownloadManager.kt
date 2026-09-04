package com.lagradost.cloudstream3.desktop.downloader

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.common.db.DatabaseFactory
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object DesktopDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mapper = jacksonObjectMapper()

    private fun getTurboDownloader(): TurboChunkDownloader {
        val threads = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_THREADS)?.toInt() ?: 8
        return TurboChunkDownloader(maxWorkers = threads.coerceIn(1, 16))
    }

    private val hlsDownloader = HlsDownloader()

    fun sanitizeFileName(name: String): String {
        return name.replace(Regex("[\\\\/:*?\"<>|]"), "-")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.', ' ')
    }

    val downloadsDir: File
        get() {
            val custom = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_PATH)
            val dir = if (!custom.isNullOrBlank()) {
                File(custom)
            } else {
                File(PlatformPaths.appDataDir, "downloads")
            }
            dir.mkdirs()
            return dir
        }

    val stagingBaseDir: File
        get() = File(downloadsDir, ".temp").apply { mkdirs() }

    fun getTaskStagingDir(taskId: String): File {
        return File(stagingBaseDir, taskId)
    }

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val cancelledTasks = ConcurrentHashMap.newKeySet<String>()

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    private val _activeSpeed = MutableStateFlow(0L)
    val activeSpeed: StateFlow<Long> = _activeSpeed.asStateFlow()

    init {
        loadTasksFromDatabase()
    }

    fun loadTasksFromDatabase() {
        scope.launch {
            try {
                val dbTasks = DatabaseFactory.database.cloudstreamDBQueries.selectAllDownloads().executeAsList()
                val list = dbTasks.map { row ->
                    val headers: Map<String, String> = row.headersJson?.let {
                        try { mapper.readValue(it) } catch (_: Exception) { emptyMap() }
                    } ?: emptyMap()

                    var initialStatus = try { DownloadStatus.valueOf(row.status) } catch (_: Exception) { DownloadStatus.PAUSED }
                    // Reconcile interrupted active downloads on startup
                    if (initialStatus == DownloadStatus.DOWNLOADING) {
                        initialStatus = DownloadStatus.PAUSED
                        try {
                            DatabaseFactory.database.cloudstreamDBQueries.updateDownloadStatus(
                                status = DownloadStatus.PAUSED.name,
                                dateCompleted = null,
                                id = row.id,
                            )
                        } catch (_: Exception) {}
                    }

                    DownloadTask(
                        id = row.id,
                        canonicalKey = row.canonicalKey,
                        showName = row.showName,
                        showUrl = row.showUrl,
                        episodeTitle = row.episodeTitle,
                        posterUrl = row.posterUrl,
                        backdropUrl = row.backdropUrl,
                        season = row.season?.toInt(),
                        episode = row.episode?.toInt(),
                        filePath = row.filePath,
                        streamUrl = row.streamUrl,
                        totalBytes = row.totalBytes,
                        downloadedBytes = row.downloadedBytes,
                        status = initialStatus,
                        quality = row.quality.toInt(),
                        apiName = row.apiName,
                        headers = headers,
                        dateAdded = row.dateAdded,
                        dateCompleted = row.dateCompleted,
                    )
                }
                _tasks.value = list

                // Startup orphan cleanup
                cleanOrphanedTempFiles()
            } catch (e: Exception) {
                AppLogger.e("Failed to load downloads from SQLite: ${e.message}")
            }
        }
    }

    fun getMaxConcurrent(): Int {
        val max = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.common.storage.DesktopDataStore.PREF_DOWNLOAD_MAX_CONCURRENT)?.toInt() ?: 2
        return max.coerceIn(1, 5)
    }

    @Synchronized
    fun dispatchNextTasks() {
        val max = getMaxConcurrent()
        val activeCount = _tasks.value.count { it.status == DownloadStatus.DOWNLOADING }
        val slotsAvailable = (max - activeCount).coerceAtLeast(0)
        if (slotsAvailable <= 0) return

        val queued = _tasks.value.filter { it.status == DownloadStatus.QUEUED }.take(slotsAvailable)
        for (task in queued) {
            startDownloadInternal(task.id)
        }
    }

    fun enqueue(
        canonicalKey: String,
        showName: String,
        showUrl: String,
        episodeTitle: String? = null,
        posterUrl: String? = null,
        backdropUrl: String? = null,
        season: Int? = null,
        episode: Int? = null,
        link: ExtractorLink,
        apiName: String = "",
    ): String {
        // Check for existing identical download
        val existingTask = _tasks.value.find {
            (it.canonicalKey == canonicalKey || it.showName.equals(showName, ignoreCase = true)) &&
            it.season == season && it.episode == episode
        }
        if (existingTask != null) {
            val dest = File(existingTask.filePath)
            if (existingTask.status == DownloadStatus.COMPLETED && dest.exists() && dest.length() > 0) {
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("'$showName' is already downloaded.")
                return existingTask.id
            } else if (existingTask.status == DownloadStatus.DOWNLOADING || existingTask.status == DownloadStatus.QUEUED) {
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo("'$showName' is already in download queue.")
                return existingTask.id
            } else {
                // Stale or failed record - purge old DB entry
                scope.launch {
                    try {
                        DatabaseFactory.database.cloudstreamDBQueries.deleteDownloadTask(existingTask.id)
                    } catch (_: Exception) {}
                }
                _tasks.value = _tasks.value.filterNot { it.id == existingTask.id }
            }
        }

        val id = UUID.randomUUID().toString()
        val isMovie = season == null && episode == null
        val cleanShow = sanitizeFileName(showName)
        val subDir = if (isMovie) {
            File(downloadsDir, "Movies/$cleanShow")
        } else {
            val seasonFolder = if (season != null && season > 0) "Season %02d".format(season) else "Season 01"
            File(downloadsDir, "Shows/$cleanShow/$seasonFolder")
        }.apply { mkdirs() }

        val sanitizedEp = com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer.sanitizeEpisodeTitle(episodeTitle)
        val baseName = if (isMovie) {
            "$cleanShow (${link.quality}p)"
        } else {
            val sNum = season ?: 1
            val eNum = episode ?: 1
            val cleanEpTitle = sanitizedEp?.let { sanitizeFileName(it) }?.takeIf { it.isNotBlank() }
            if (cleanEpTitle != null) {
                "$cleanShow - S%02dE%02d - $cleanEpTitle (${link.quality}p)".format(sNum, eNum)
            } else {
                "$cleanShow - S%02dE%02d (${link.quality}p)".format(sNum, eNum)
            }
        }

        val ext = if (link.isM3u8 || link.type == ExtractorLinkType.M3U8 || link.url.contains(".m3u8", ignoreCase = true)) "mp4" else "mkv"
        val destinationFile = File(subDir, "$baseName.$ext")

        val task = DownloadTask(
            id = id,
            canonicalKey = canonicalKey,
            showName = showName,
            showUrl = showUrl,
            episodeTitle = sanitizedEp,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            season = season,
            episode = episode,
            filePath = destinationFile.absolutePath,
            streamUrl = link.url,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadStatus.QUEUED,
            quality = link.quality,
            apiName = apiName,
            headers = link.headers,
            dateAdded = System.currentTimeMillis(),
        )

        scope.launch {
            try {
                val headersJson = mapper.writeValueAsString(link.headers)
                DatabaseFactory.database.cloudstreamDBQueries.insertDownloadTask(
                    id = task.id,
                    canonicalKey = task.canonicalKey,
                    showName = task.showName,
                    showUrl = task.showUrl,
                    episodeTitle = task.episodeTitle,
                    posterUrl = task.posterUrl,
                    backdropUrl = task.backdropUrl,
                    season = task.season?.toLong(),
                    episode = task.episode?.toLong(),
                    filePath = task.filePath,
                    streamUrl = task.streamUrl,
                    totalBytes = 0L,
                    downloadedBytes = 0L,
                    status = DownloadStatus.QUEUED.name,
                    quality = task.quality.toLong(),
                    apiName = task.apiName,
                    headersJson = headersJson,
                    dateAdded = task.dateAdded,
                    dateCompleted = null,
                )
                _tasks.value = _tasks.value + task
                com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("Added '${task.displayTitle}' (${link.quality}p) to download queue")
                dispatchNextTasks()
            } catch (e: Exception) {
                AppLogger.e("Failed to enqueue download task: ${e.message}", e)
            }
        }

        return id
    }

    fun startDownload(taskId: String) {
        resume(taskId)
    }

    private fun startDownloadInternal(taskId: String) {
        val currentTask = _tasks.value.find { it.id == taskId } ?: return
        if (activeJobs.containsKey(taskId)) return

        cancelledTasks.remove(taskId)
        updateTaskStatus(taskId, DownloadStatus.DOWNLOADING)

        val job = scope.launch {
            val destination = File(currentTask.filePath)
            val stagingDir = getTaskStagingDir(taskId).apply { mkdirs() }
            val tempPart = File(stagingDir, "stream.part")
            val streamUrl = currentTask.streamUrl
            val isM3u8 = streamUrl.contains(".m3u8", ignoreCase = true)
            val isTorrent = DesktopTorrentEngine.isTorrentLink(
                com.lagradost.cloudstream3.utils.newExtractorLink(
                    source = currentTask.apiName,
                    name = currentTask.showName,
                    url = streamUrl,
                    type = ExtractorLinkType.VIDEO,
                )
            )

            AppLogger.i("DesktopDownloadManager starting sandbox download for task $taskId: ${currentTask.displayTitle}")

            try {
                val success = when {
                    isTorrent -> {
                        val resolved = DesktopTorrentEngine.transformLink(
                            com.lagradost.cloudstream3.utils.newExtractorLink(
                                source = currentTask.apiName,
                                name = currentTask.showName,
                                url = streamUrl,
                                type = ExtractorLinkType.TORRENT,
                            )
                        )
                        getTurboDownloader().download(
                            url = resolved.url,
                            headers = currentTask.headers,
                            destinationFile = destination,
                            tempPartFile = tempPart,
                            totalBytesEstimated = currentTask.totalBytes,
                            onProgress = { dl, total, speed ->
                                updateProgress(taskId, dl, total, speed)
                            },
                            isCancelled = { cancelledTasks.contains(taskId) },
                        )
                    }
                    isM3u8 -> {
                        hlsDownloader.download(
                            playlistUrl = streamUrl,
                            headers = currentTask.headers,
                            destinationFile = destination,
                            tempPartFile = tempPart,
                            onProgress = { dl, total, speed ->
                                updateProgress(taskId, dl, total, speed)
                            },
                            isCancelled = { cancelledTasks.contains(taskId) },
                        )
                    }
                    else -> {
                        getTurboDownloader().download(
                            url = streamUrl,
                            headers = currentTask.headers,
                            destinationFile = destination,
                            tempPartFile = tempPart,
                            totalBytesEstimated = currentTask.totalBytes,
                            onProgress = { dl, total, speed ->
                                updateProgress(taskId, dl, total, speed)
                            },
                            isCancelled = { cancelledTasks.contains(taskId) },
                        )
                    }
                }

                if (success && !cancelledTasks.contains(taskId)) {
                    if (tempPart.exists() && tempPart.length() > 0L) {
                        destination.parentFile?.mkdirs()
                        if (destination.exists()) {
                            destination.delete()
                        }

                        val moved = if (tempPart.renameTo(destination)) {
                            true
                        } else {
                            try {
                                tempPart.inputStream().use { input ->
                                    destination.outputStream().use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                tempPart.delete()
                                true
                            } catch (e: Exception) {
                                AppLogger.e("Failed to copy completed download to destination: ${e.message}", e)
                                false
                            }
                        }

                        if (moved && destination.exists() && destination.length() > 0L) {
                            stagingDir.deleteRecursively()
                            val finalSize = destination.length()
                            updateTaskCompleted(taskId, finalSize)
                            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess("Finished download: ${currentTask.displayTitle}")
                            AppLogger.i("DesktopDownloadManager task completed: ${currentTask.displayTitle} ($finalSize bytes)")
                        } else {
                            updateTaskStatus(taskId, DownloadStatus.FAILED, "Failed to save file to destination folder")
                            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError("Failed to save '${currentTask.displayTitle}' to disk.")
                        }
                    } else {
                        updateTaskStatus(taskId, DownloadStatus.FAILED, "Empty downloaded file")
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError("Download failed: empty stream for '${currentTask.displayTitle}'")
                    }
                } else if (cancelledTasks.contains(taskId)) {
                    updateTaskStatus(taskId, DownloadStatus.PAUSED)
                } else {
                    updateTaskStatus(taskId, DownloadStatus.FAILED, "Download failed")
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showError("Download failed for '${currentTask.displayTitle}'")
                }
            } catch (e: Exception) {
                AppLogger.e("Download error on task $taskId: ${e.message}", e)
                if (!cancelledTasks.contains(taskId)) {
                    updateTaskStatus(taskId, DownloadStatus.FAILED, e.message)
                }
            } finally {
                activeJobs.remove(taskId)
                recalculateTotalSpeed()
                dispatchNextTasks()
            }
        }

        activeJobs[taskId] = job
    }

    fun pause(taskId: String) {
        cancelledTasks.add(taskId)
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTaskStatus(taskId, DownloadStatus.PAUSED)
        recalculateTotalSpeed()
        dispatchNextTasks()
    }

    fun resume(taskId: String) {
        cancelledTasks.remove(taskId)
        updateTaskStatus(taskId, DownloadStatus.QUEUED)
        dispatchNextTasks()
    }

    fun cancel(taskId: String) {
        cancelledTasks.add(taskId)
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        updateTaskStatus(taskId, DownloadStatus.CANCELLED)
        getTaskStagingDir(taskId).deleteRecursively()
        val task = _tasks.value.find { it.id == taskId }
        if (task != null) {
            val dest = File(task.filePath)
            if (dest.exists() && dest.length() == 0L) dest.delete()
            File("${task.filePath}.part").delete()
            File("${task.filePath}.part.segments").deleteRecursively()
        }
        recalculateTotalSpeed()
        dispatchNextTasks()
    }

    fun delete(taskId: String, deleteFile: Boolean = true): Boolean {
        cancel(taskId)
        val task = _tasks.value.find { it.id == taskId }
        var fileDeleted = true
        if (task != null && deleteFile) {
            val file = File(task.filePath)
            val legacyTemp = File("${task.filePath}.part")
            val legacySegments = File("${task.filePath}.part.segments")
            try {
                if (file.exists()) {
                    val ok = file.delete()
                    if (!ok && file.exists()) {
                        fileDeleted = false
                        AppLogger.w("DesktopDownloadManager: Could not delete ${file.absolutePath}, file may be locked by a video player.")
                    } else {
                        cleanEmptyParentDirectories(file.parentFile)
                    }
                }
                if (legacyTemp.exists()) legacyTemp.delete()
                if (legacySegments.exists()) legacySegments.deleteRecursively()
            } catch (e: Exception) {
                fileDeleted = false
                AppLogger.w("DesktopDownloadManager: Error deleting file: ${e.message}")
            }
        }
        getTaskStagingDir(taskId).deleteRecursively()

        scope.launch {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.deleteDownloadTask(taskId)
                _tasks.value = _tasks.value.filterNot { it.id == taskId }
                dispatchNextTasks()
            } catch (e: Exception) {
                AppLogger.e("Failed to delete download task from DB: ${e.message}")
            }
        }
        return fileDeleted
    }

    private fun cleanEmptyParentDirectories(dir: File?) {
        try {
            var current = dir
            val baseDownloads = downloadsDir.canonicalFile
            while (current != null && current.canonicalFile != baseDownloads && current.canonicalFile.parentFile != null) {
                val isRootCategory = current.name.equals("Shows", ignoreCase = true) || current.name.equals("Movies", ignoreCase = true)
                if (isRootCategory) break
                val contents = current.listFiles()
                if (contents.isNullOrEmpty()) {
                    val deleted = current.delete()
                    if (!deleted) break
                    current = current.parentFile
                } else {
                    break
                }
            }
        } catch (_: Exception) {}
    }

    fun pauseAll() {
        val targets = _tasks.value.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
        for (t in targets) {
            cancelledTasks.add(t.id)
            activeJobs[t.id]?.cancel()
            activeJobs.remove(t.id)
            updateTaskStatus(t.id, DownloadStatus.PAUSED)
        }
        recalculateTotalSpeed()
    }

    fun resumeAll() {
        val paused = _tasks.value.filter { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
        for (t in paused) {
            cancelledTasks.remove(t.id)
            updateTaskStatus(t.id, DownloadStatus.QUEUED)
        }
        dispatchNextTasks()
    }

    fun cancelAll() {
        val activeOrQueued = _tasks.value.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED }
        for (t in activeOrQueued) {
            cancel(t.id)
        }
    }

    fun cleanOrphanedTempFiles(): Long {
        var reclaimedBytes = 0L
        try {
            val liveTaskIds = _tasks.value.filter {
                it.status in listOf(DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED, DownloadStatus.PAUSED, DownloadStatus.FAILED)
            }.map { it.id }.toSet()

            // 1. Scan .temp directory
            val tempDir = stagingBaseDir
            if (tempDir.exists() && tempDir.isDirectory) {
                tempDir.listFiles()?.forEach { file ->
                    if (!liveTaskIds.contains(file.name)) {
                        reclaimedBytes += file.walkTopDown().sumOf { it.length() }
                        file.deleteRecursively()
                    }
                }
            }

            // 2. Scan legacy dangling .part files and .segments in main downloads directory
            downloadsDir.walkTopDown().forEach { file ->
                if (file.isFile && file.name.endsWith(".part", ignoreCase = true)) {
                    reclaimedBytes += file.length()
                    file.delete()
                } else if (file.isDirectory && file.name.endsWith(".segments", ignoreCase = true)) {
                    reclaimedBytes += file.walkTopDown().sumOf { it.length() }
                    file.deleteRecursively()
                }
            }

            // 3. Scan and prune empty show/movie folders
            listOf(File(downloadsDir, "Shows"), File(downloadsDir, "Movies")).forEach { root ->
                if (root.exists() && root.isDirectory) {
                    root.listFiles()?.forEach { showDir ->
                        if (showDir.isDirectory) {
                            showDir.listFiles()?.forEach { seasonDir ->
                                if (seasonDir.isDirectory && seasonDir.listFiles().isNullOrEmpty()) {
                                    seasonDir.delete()
                                }
                            }
                            if (showDir.listFiles().isNullOrEmpty()) {
                                showDir.delete()
                            }
                        }
                    }
                }
            }

            AppLogger.i("DesktopDownloadManager: Cleaned orphaned temp files ($reclaimedBytes bytes reclaimed)")
        } catch (e: Exception) {
            AppLogger.w("Failed to clean orphaned temp files: ${e.message}")
        }
        return reclaimedBytes
    }

    private fun updateProgress(taskId: String, downloaded: Long, total: Long, speed: Long) {
        _tasks.value = _tasks.value.map { t ->
            if (t.id == taskId) {
                val remaining = (total - downloaded).coerceAtLeast(0L)
                val eta = if (speed > 0L) remaining / speed else 0L
                t.copy(
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speedBytesSec = speed,
                    etaSeconds = eta,
                )
            } else t
        }
        recalculateTotalSpeed()
    }

    private fun updateTaskStatus(taskId: String, status: DownloadStatus, error: String? = null) {
        _tasks.value = _tasks.value.map { t ->
            if (t.id == taskId) t.copy(status = status, speedBytesSec = 0L, errorMessage = error)
            else t
        }
        scope.launch {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.updateDownloadStatus(
                    status = status.name,
                    dateCompleted = if (status == DownloadStatus.COMPLETED) System.currentTimeMillis() else null,
                    id = taskId,
                )
            } catch (e: Exception) {
                AppLogger.e("Failed to update download status: ${e.message}")
            }
        }
    }

    private fun updateTaskCompleted(taskId: String, finalSize: Long) {
        _tasks.value = _tasks.value.map { t ->
            if (t.id == taskId) t.copy(
                status = DownloadStatus.COMPLETED,
                downloadedBytes = finalSize,
                totalBytes = finalSize,
                speedBytesSec = 0L,
                dateCompleted = System.currentTimeMillis(),
            ) else t
        }
        scope.launch {
            try {
                DatabaseFactory.database.cloudstreamDBQueries.updateDownloadProgress(
                    downloadedBytes = finalSize,
                    totalBytes = finalSize,
                    status = DownloadStatus.COMPLETED.name,
                    id = taskId,
                )
            } catch (e: Exception) {
                AppLogger.e("Failed to mark download completed in DB: ${e.message}")
            }
        }
    }

    private fun recalculateTotalSpeed() {
        val total = _tasks.value.filter { it.status == DownloadStatus.DOWNLOADING }.sumOf { it.speedBytesSec }
        _activeSpeed.value = total
    }
}
