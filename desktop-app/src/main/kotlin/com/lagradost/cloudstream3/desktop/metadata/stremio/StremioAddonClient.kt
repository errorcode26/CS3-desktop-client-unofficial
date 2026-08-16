package com.lagradost.cloudstream3.desktop.metadata.stremio

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.metadata.MetadataConfig
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object StremioAddonClient {
    private const val TAG = "StremioAddonClient"

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StremioManifest(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("version") val version: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("resources") val resources: List<Any>? = null,
        @JsonProperty("types") val types: List<String>? = null,
        @JsonProperty("idPrefixes") val idPrefixes: List<String>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StremioCatalogResponse(
        @JsonProperty("metas") val metas: List<StremioMetaItem>? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StremioMetaResponse(
        @JsonProperty("meta") val meta: StremioMetaItem? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StremioVideo(
        @JsonProperty("season") val season: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null,
        @JsonProperty("released") val released: String? = null,
        @JsonProperty("imdbRating") val imdbRating: String? = null,
        @JsonProperty("rating") val rating: String? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class StremioMetaItem(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("type") val type: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("poster") val poster: String? = null,
        @JsonProperty("background") val background: String? = null,
        @JsonProperty("logo") val logo: String? = null,
        @JsonProperty("description") val description: String? = null,
        @JsonProperty("imdbRating") val imdbRating: String? = null,
        @JsonProperty("releaseInfo") val releaseInfo: String? = null,
        @JsonProperty("genres") val genres: List<String>? = null,
        @JsonProperty("videos") val videos: List<StremioVideo>? = null,
        @JsonProperty("moviedb_id") val moviedbId: Int? = null,
    )

    fun normalizeManifestUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (trimmed.isBlank()) return ""

        val normalizedScheme = when {
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("stremio://", ignoreCase = true) -> "https://${trimmed.substring(10)}"
            else -> "https://$trimmed"
        }

        val withoutFragment = normalizedScheme.substringBefore("#")
        val query = withoutFragment.substringAfter("?", "")
        val path = withoutFragment.substringBefore("?").trimEnd('/')
        val manifestPath = if (path.endsWith("/manifest.json", ignoreCase = true)) {
            path
        } else {
            "$path/manifest.json"
        }

        return if (query.isEmpty()) manifestPath else "$manifestPath?$query"
    }

    fun getTransportBaseUrl(manifestUrl: String): String {
        return manifestUrl.substringBefore("?").removeSuffix("/manifest.json").trimEnd('/')
    }

    suspend fun testManifest(rawUrl: String): Result<StremioManifest> = withContext(Dispatchers.IO) {
        val normalized = normalizeManifestUrl(rawUrl)
        if (normalized.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("URL is empty"))
        }

        try {
            val response = app.get(
                url = normalized,
                headers = mapOf("Accept" to "application/json", "User-Agent" to "CloudStream-Desktop/1.0"),
                timeout = 5000L,
            )
            if (response.code != 200) {
                return@withContext Result.failure(Exception("HTTP ${response.code} received from manifest"))
            }

            val manifest = response.parsedSafe<StremioManifest>()
                ?: return@withContext Result.failure(Exception("Invalid manifest JSON format"))

            if (manifest.id.isNullOrBlank() && manifest.name.isNullOrBlank()) {
                return@withContext Result.failure(Exception("Manifest missing required 'id' or 'name'"))
            }

            Result.success(manifest)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun search(query: String, type: String = "series"): List<StremioMetaItem>? = withContext(Dispatchers.IO) {
        val manifestUrl = MetadataConfig.stremioAddonUrl.value.trim()
        val enabled = MetadataConfig.stremioAddonEnabled.value
        if (!enabled || manifestUrl.isBlank()) return@withContext null

        val baseUrl = getTransportBaseUrl(manifestUrl)
        if (baseUrl.isBlank()) return@withContext null

        val queryParam = manifestUrl.substringAfter("?", "").let { if (it.isBlank()) "" else "?$it" }

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "$baseUrl/catalog/$type/top/search=$encodedQuery.json$queryParam"
            val response = app.get(
                url = url,
                headers = mapOf("Accept" to "application/json", "User-Agent" to "CloudStream-Desktop/1.0"),
                timeout = 4000L,
            )
            val parsed = response.parsedSafe<StremioCatalogResponse>()
            parsed?.metas
        } catch (e: Exception) {
            AppLogger.d(TAG, "Search failed for '$query': ${e.message}")
            null
        }
    }

    suspend fun getMeta(id: String, type: String = "series"): StremioMetaItem? = withContext(Dispatchers.IO) {
        val manifestUrl = MetadataConfig.stremioAddonUrl.value.trim()
        val enabled = MetadataConfig.stremioAddonEnabled.value
        if (!enabled || manifestUrl.isBlank()) return@withContext null

        val baseUrl = getTransportBaseUrl(manifestUrl)
        if (baseUrl.isBlank()) return@withContext null

        val queryParam = manifestUrl.substringAfter("?", "").let { if (it.isBlank()) "" else "?$it" }

        try {
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val url = "$baseUrl/meta/$type/$encodedId.json$queryParam"
            val response = app.get(
                url = url,
                headers = mapOf("Accept" to "application/json", "User-Agent" to "CloudStream-Desktop/1.0"),
                timeout = 4000L,
            )
            val parsed = response.parsedSafe<StremioMetaResponse>()
            parsed?.meta
        } catch (e: Exception) {
            AppLogger.d(TAG, "getMeta failed for id='$id': ${e.message}")
            null
        }
    }
}
