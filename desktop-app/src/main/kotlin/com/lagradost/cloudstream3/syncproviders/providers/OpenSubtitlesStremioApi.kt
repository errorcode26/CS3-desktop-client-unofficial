package com.lagradost.cloudstream3.syncproviders.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.subtitles.LanguageNormalizer
import com.lagradost.cloudstream3.desktop.subtitles.SubtitleConfig
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity
import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AuthData
import com.lagradost.cloudstream3.syncproviders.SubtitleAPI
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class OpenSubtitlesStremioApi : SubtitleAPI() {
    override val name = "OpenSubtitles (Stremio)"
    override val idPrefix = "stremio_opensubtitles"

    override val icon = null
    override val hasInApp = false
    override val requiresLogin = false

    companion object {
        private const val TAG = "OpenSubtitlesStremio"
        private const val BASE_URL = "https://opensubtitles-v3.strem.io"
        private const val CINEMETA_URL = "https://v3-cinemeta.strem.io"
    }

    private data class StremioSubtitleResponse(
        @JsonProperty("subtitles") val subtitles: List<StremioSubtitleItem>?,
    )

    private data class StremioSubtitleItem(
        @JsonProperty("id") val id: String?,
        @JsonProperty("url") val url: String?,
        @JsonProperty("lang") val lang: String?,
        @JsonProperty("SubEncoding") val subEncoding: String?,
        @JsonProperty("m") val matchType: String?,
        @JsonProperty("g") val rating: String?,
    )

    private data class CinemetaCatalogResponse(
        @JsonProperty("metas") val metas: List<CinemetaMeta>?,
    )

    private data class CinemetaMeta(
        @JsonProperty("id") val id: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("year") val year: String?,
    )

    override suspend fun search(
        auth: AuthData?,
        query: SubtitleSearch,
    ): List<SubtitleEntity>? = withContext(Dispatchers.IO) {
        if (!SubtitleConfig.openSubtitlesEnabled.value) {
            AppLogger.d(TAG, "OpenSubtitles provider is disabled in settings, skipping.")
            return@withContext emptyList()
        }

        try {
            var imdbId = query.imdbId?.takeIf { it.startsWith("tt") }
            val isSeries = (query.seasonNumber != null && query.seasonNumber!! > 0) || (query.epNumber != null && query.epNumber!! > 0)

            // Resolve IMDb ID via Cinemeta if missing
            if (imdbId == null && query.query.isNotBlank()) {
                val cleanQuery = query.query.trim()
                val type = if (isSeries) "series" else "movie"
                val encodedQuery = URLEncoder.encode(cleanQuery, "UTF-8")
                val searchUrl = "$CINEMETA_URL/catalog/$type/top/search=$encodedQuery.json"

                val cinemetaJson = app.get(searchUrl, timeout = 5000L).text
                val catalog = tryParseJson<CinemetaCatalogResponse>(cinemetaJson)
                imdbId = catalog?.metas?.firstOrNull { it.id?.startsWith("tt") == true }?.id
                if (imdbId != null) {
                    AppLogger.d(TAG, "Resolved '$cleanQuery' -> $imdbId via Cinemeta")
                }
            }

            if (imdbId == null) {
                AppLogger.w(TAG, "Cannot search OpenSubtitles: No valid IMDb ID found for query '${query.query}'")
                return@withContext emptyList()
            }

            val requestUrl = if (isSeries) {
                val s = query.seasonNumber ?: 1
                val e = query.epNumber ?: 1
                "$BASE_URL/subtitles/series/$imdbId:$s:$e.json"
            } else {
                "$BASE_URL/subtitles/movie/$imdbId.json"
            }

            AppLogger.d(TAG, "Fetching subtitles from $requestUrl")
            val responseText = app.get(requestUrl, timeout = 6000L).text
            val parsed = tryParseJson<StremioSubtitleResponse>(responseText)
            val subList = parsed?.subtitles ?: return@withContext emptyList()

            val entities = mutableListOf<SubtitleEntity>()
            val langTrackCounters = mutableMapOf<String, Int>()

            for (item in subList) {
                val downloadUrl = item.url ?: continue
                val normalizedLang = LanguageNormalizer.normalize(item.lang)

                // If a specific language was requested in search, filter accordingly
                if (!query.lang.isNullOrBlank() && !LanguageNormalizer.isMatch(query.lang, item.lang)) {
                    continue
                }

                val currentCount = (langTrackCounters[normalizedLang.displayName] ?: 0) + 1
                langTrackCounters[normalizedLang.displayName] = currentCount

                val encodingInfo = item.subEncoding?.takeIf { it.isNotBlank() && !it.equals("UTF-8", true) }?.let { " [$it]" } ?: ""
                val displayName = "${normalizedLang.displayName} - Track #$currentCount$encodingInfo"

                entities.add(
                    SubtitleEntity(
                        idPrefix = idPrefix,
                        name = displayName,
                        lang = normalizedLang.code2,
                        data = downloadUrl,
                        source = name,
                        epNumber = query.epNumber,
                        seasonNumber = query.seasonNumber,
                        year = query.year,
                    ),
                )
            }

            AppLogger.i(TAG, "Found ${entities.size} subtitles for $imdbId")
            return@withContext entities
        } catch (e: Exception) {
            AppLogger.e(TAG, "OpenSubtitles search failed: ${e.message}", e)
            return@withContext emptyList()
        }
    }

    override suspend fun load(auth: AuthData?, subtitle: SubtitleEntity): String? {
        return subtitle.data.takeIf { it.isNotBlank() }
    }
}
