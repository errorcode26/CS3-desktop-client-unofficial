package com.lagradost.cloudstream3.desktop.downloader

import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import java.io.File

enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED,
}

data class DownloadTask(
    val id: String,
    val canonicalKey: String,
    val showName: String,
    val showUrl: String,
    val episodeTitle: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val filePath: String,
    val streamUrl: String,
    val totalBytes: Long = 0L,
    val downloadedBytes: Long = 0L,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val quality: Int = 1080,
    val speedBytesSec: Long = 0L,
    val etaSeconds: Long = 0L,
    val apiName: String = "",
    val headers: Map<String, String> = emptyMap(),
    val dateAdded: Long = System.currentTimeMillis(),
    val dateCompleted: Long? = null,
    val errorMessage: String? = null,
) {
    val progressPercent: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val isMovie: Boolean
        get() = season == null && episode == null

    val cleanEpisodeTitle: String?
        get() = CardTitleSanitizer.sanitizeEpisodeTitle(episodeTitle)

    val displayTitle: String
        get() = if (isMovie) {
            showName
        } else {
            val ep = cleanEpisodeTitle
            "$showName • S${season ?: 1} E${episode ?: 1}${if (!ep.isNullOrBlank()) " - $ep" else ""}"
        }

    val file: File
        get() = File(filePath)

    val existsOnDisk: Boolean
        get() = file.exists() && file.length() > 0L
}

data class ChunkProgress(
    val chunkIndex: Int,
    val startByte: Long,
    val endByte: Long,
    val downloadedBytes: Long,
    val isFinished: Boolean,
)
