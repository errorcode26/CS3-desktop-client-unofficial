package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.downloader.DesktopDownloadManager
import com.lagradost.cloudstream3.desktop.downloader.DownloadStatus
import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import java.io.File

object UniversalCrossProviderAggregator {
    data class MediaRequest(
        val title: String,
        val year: Int? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val imdbId: String? = null,
        val tmdbId: Int? = null,
        val tvType: TvType = TvType.TvSeries,
        val primaryApiName: String? = null,
        val primaryUrl: String? = null,
    )

    fun aggregateStreams(request: MediaRequest): Flow<ExtractorLink> = channelFlow {
        // 1. Instant check: Is it already downloaded locally?
        val localTask = DesktopDownloadManager.tasks.value.find { task ->
            task.status == DownloadStatus.COMPLETED &&
                    task.existsOnDisk &&
                    (task.canonicalKey.equals(request.imdbId, ignoreCase = true) ||
                     task.canonicalKey.equals(request.tmdbId?.toString(), ignoreCase = true) ||
                     (task.showName.equals(request.title, ignoreCase = true) && task.season == request.season && task.episode == request.episode))
        }

        if (localTask != null) {
            val localFile = File(localTask.filePath)
            if (localFile.exists() && localFile.length() > 0) {
                AppLogger.i("UniversalCrossProviderAggregator: Found local offline download -> ${localFile.absolutePath}")
                send(
                    newExtractorLink(
                        source = "Downloaded (Offline)",
                        name = "Local Storage • ${localTask.quality}p",
                        url = localFile.absolutePath,
                        type = ExtractorLinkType.VIDEO,
                    ) {
                        this.quality = localTask.quality
                    }
                )
            }
        }

        coroutineScope {
            // 2. Query Stremio Addons in parallel (if IMDb ID available)
            if (!request.imdbId.isNullOrBlank() || request.title.isNotBlank()) {
                launch(Dispatchers.IO) {
                    try {
                        AppLogger.d("UniversalCrossProviderAggregator: Querying Stremio stream addons for '${request.title}' (imdb=${request.imdbId})")
                        StremioAddonManager.searchStreams(
                            imdbId = request.imdbId,
                            season = request.season,
                            episode = request.episode,
                            title = request.title,
                            onLink = { link ->
                                launch { send(link) }
                            },
                        )
                    } catch (e: Exception) {
                        AppLogger.w("Stremio addon aggregation failed: ${e.message}")
                    }
                }
            }
        }
    }
}
