package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CinemetaAPI {
    
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CinemetaSearchResponse(
        @JsonProperty("metas") val metas: List<CinemetaMeta>?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CinemetaMetaResponse(
        @JsonProperty("meta") val meta: CinemetaMeta?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CinemetaVideo(
        @JsonProperty("season") val season: Int?,
        @JsonProperty("episode") val episode: Int?,
        @JsonProperty("title") val title: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("imdbRating") val imdbRating: String?
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class CinemetaMeta(
        @JsonProperty("id") val id: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("background") val background: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("imdbRating") val imdbRating: String?,
        @JsonProperty("releaseInfo") val releaseInfo: String?,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("videos") val videos: List<CinemetaVideo>?
    )

    suspend fun search(query: String, type: String = "movie"): CinemetaMeta? {
        return withContext(Dispatchers.IO) {
            try {
                val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
                val url = "https://v3-cinemeta.strem.io/catalog/$type/top/search=$encodedQuery.json"
                val response = app.get(url).parsedSafe<CinemetaSearchResponse>()
                response?.metas?.firstOrNull()
            } catch (e: Exception) {
                null
            }
        }
    }

    suspend fun getMeta(id: String, type: String = "movie"): CinemetaMeta? {
        return withContext(Dispatchers.IO) {
            try {
                val url = "https://v3-cinemeta.strem.io/meta/$type/$id.json"
                val response = app.get(url).parsedSafe<CinemetaMetaResponse>()
                response?.meta
            } catch (e: Exception) {
                null
            }
        }
    }
}
