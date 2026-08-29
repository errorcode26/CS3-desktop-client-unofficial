package com.lagradost.cloudstream3.desktop.torrent

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorrServerFileStat(
    @JsonProperty("id") val id: Int = 1,
    @JsonProperty("path") val path: String = "",
    @JsonProperty("length") val length: Long = 0L,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorrServerStats(
    @JsonProperty("hash") val hash: String = "",
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("download_speed") val downloadSpeed: Double = 0.0,
    @JsonProperty("upload_speed") val uploadSpeed: Double = 0.0,
    @JsonProperty("active_peers") val activePeers: Int = 0,
    @JsonProperty("connected_seeders") val connectedSeeders: Int = 0,
    @JsonProperty("total_peers") val totalPeers: Int = 0,
    @JsonProperty("preloaded_bytes") val preloadedBytes: Long = 0L,
    @JsonProperty("preload_size") val preloadSize: Long = 0L,
    @JsonProperty("loaded_size") val loadedSize: Long = 0L,
    @JsonProperty("torrent_size") val torrentSize: Long = 0L,
    @JsonProperty("stat") val stat: Int = 0,
    @JsonProperty("stat_string") val statString: String? = null,
    @JsonProperty("file_stats") val fileStats: List<TorrServerFileStat> = emptyList(),
) {
    val downloadSpeedLong: Long get() = downloadSpeed.toLong()
    val uploadSpeedLong: Long get() = uploadSpeed.toLong()
}

class DesktopTorrServerApi(
    private val binary: DesktopTorrServerBinary,
) {
    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    suspend fun addTorrent(magnetLink: String, title: String? = null): String? = withContext(Dispatchers.IO) {
        val payload = mutableMapOf<String, Any>(
            "action" to "add",
            "link" to magnetLink,
            "save_to_db" to false,
        )
        if (title != null) payload["title"] = title

        try {
            val jsonStr = mapper.writeValueAsString(payload)
            val req = Request.Builder()
                .url("${binary.baseUrl}/torrents")
                .post(jsonStr.toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    AppLogger.e("TorrServer addTorrent failed: HTTP ${res.code}")
                    return@withContext null
                }
                val body = res.body?.string() ?: return@withContext null
                val root = mapper.readTree(body)
                val hash = root.get("hash")?.asText()?.takeIf { it.isNotBlank() }
                AppLogger.d("TorrServer added torrent: $hash")
                hash
            }
        } catch (e: Exception) {
            AppLogger.e("TorrServer addTorrent error: ${e.message}")
            null
        }
    }

    suspend fun getTorrentStats(hash: String): TorrServerStats? = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "action" to "get",
            "hash" to hash,
        )
        try {
            val jsonStr = mapper.writeValueAsString(payload)
            val req = Request.Builder()
                .url("${binary.baseUrl}/torrents")
                .post(jsonStr.toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return@withContext null
                val body = res.body?.string() ?: return@withContext null
                mapper.readValue<TorrServerStats>(body)
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun dropTorrent(hash: String): Boolean = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "action" to "drop",
            "hash" to hash,
        )
        try {
            val jsonStr = mapper.writeValueAsString(payload)
            val req = Request.Builder()
                .url("${binary.baseUrl}/torrents")
                .post(jsonStr.toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(req).execute().use { res ->
                res.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }

    suspend fun preloadTorrent(hash: String, fileIdx: Int): Boolean = withContext(Dispatchers.IO) {
        val payload = mapOf(
            "action" to "preload",
            "hash" to hash,
            "index" to fileIdx,
        )
        try {
            val jsonStr = mapper.writeValueAsString(payload)
            val req = Request.Builder()
                .url("${binary.baseUrl}/torrents")
                .post(jsonStr.toRequestBody(jsonMedia))
                .build()

            httpClient.newCall(req).execute().use { res ->
                res.isSuccessful
            }
        } catch (e: Exception) {
            false
        }
    }
}
