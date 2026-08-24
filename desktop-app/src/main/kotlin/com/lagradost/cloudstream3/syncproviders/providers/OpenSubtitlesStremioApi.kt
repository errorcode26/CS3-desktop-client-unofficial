package com.lagradost.cloudstream3.syncproviders.providers

import com.lagradost.cloudstream3.desktop.stremio.StremioAddonManager
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class OpenSubtitlesStremioApi : SubtitleAPI() {
    override val name = "External Stremio Addons"
    override val idPrefix = "stremio_external_addons"

    override val icon = null
    override val hasInApp = false
    override val requiresLogin = false

    companion object {
        private const val TAG = "StremioSubtitleBridge"
    }

    override suspend fun search(
        auth: AuthData?,
        query: SubtitleSearch,
    ): List<SubtitleEntity>? = withContext(Dispatchers.IO) {
        try {
            val rawResults = StremioAddonManager.searchSubtitles(
                query = query.query,
                lang = query.lang,
                season = query.seasonNumber,
                episode = query.epNumber,
                imdbId = query.imdbId,
            )

            return@withContext rawResults.map { map ->
                SubtitleEntity(
                    idPrefix = (map["idPrefix"] as? String) ?: idPrefix,
                    name = (map["name"] as? String) ?: "Subtitle",
                    lang = (map["lang"] as? String) ?: "en",
                    data = (map["data"] as? String) ?: "",
                    source = (map["source"] as? String) ?: name,
                    epNumber = map["epNumber"] as? Int ?: query.epNumber,
                    seasonNumber = map["seasonNumber"] as? Int ?: query.seasonNumber,
                    year = query.year,
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to query Stremio subtitle addons: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    override suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? {
        return subtitle.data.takeIf { it.isNotBlank() }
    }
}
