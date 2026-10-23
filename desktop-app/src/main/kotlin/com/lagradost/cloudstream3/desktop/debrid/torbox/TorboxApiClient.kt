package com.lagradost.cloudstream3.desktop.debrid.torbox

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxEnvelope<T>(
    @JsonProperty("success") val success: Boolean? = true,
    @JsonProperty("error") val error: String? = null,
    @JsonProperty("detail") val detail: String? = null,
    @JsonProperty("data") val data: T? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxDeviceAuthData(
    @JsonProperty("device_code") val deviceCode: String = "",
    @JsonProperty("user_code") val userCode: String = "",
    @JsonProperty("verification_url") val verificationUrl: String = "https://torbox.app/device",
    @JsonProperty("interval") val interval: Int = 5,
    @JsonProperty("expires_in") val expiresIn: Int = 600,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxDeviceTokenData(
    @JsonProperty("token") val token: String? = null,
    @JsonProperty("session_token") val sessionToken: String? = null,
)

sealed interface TorboxTokenResult {
    data class Authorized(val token: String) : TorboxTokenResult
    data object Pending : TorboxTokenResult
    data object Expired : TorboxTokenResult
    data class Failed(val message: String?) : TorboxTokenResult
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxUserData(
    @JsonProperty("id") val id: Int? = null,
    @JsonProperty("email") val email: String? = null,
    @JsonProperty("plan") val rawPlan: Int? = 0,
    @JsonProperty("expires_at") val expiresAt: String? = null,
    @JsonProperty("is_subscribed") val rawIsSubscribed: Boolean? = false,
    @JsonProperty("customer_id") val customerId: String? = null,
) {
    @get:JsonIgnore val plan: Int get() = rawPlan ?: 0

    @get:JsonIgnore val isSubscribed: Boolean get() = rawIsSubscribed ?: false

    val planName: String
        get() = when (plan) {
            1 -> "Essential"
            2 -> "Pro"
            3 -> "Standard"
            else -> if (isSubscribed) "Active Plan" else "Free"
        }
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxFile(
    @JsonProperty("id") val rawId: Int? = 0,
    @JsonProperty("name") val rawName: String? = "",
    @JsonProperty("size") val rawSize: Long? = 0L,
    @JsonProperty("short_name") val shortName: String? = null,
) {
    @get:JsonIgnore val id: Int get() = rawId ?: 0

    @get:JsonIgnore val name: String get() = rawName.orEmpty()

    @get:JsonIgnore val size: Long get() = rawSize ?: 0L
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxCachedItem(
    @JsonProperty("name") val name: String? = null,
    @JsonProperty("size") val size: Long? = null,
    @JsonProperty("files") val rawFiles: List<TorboxFile>? = null,
) {
    @get:JsonIgnore val files: List<TorboxFile> get() = rawFiles ?: emptyList()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxCreateTorrentData(
    @JsonProperty("torrent_id") val torrentId: Int? = null,
    @JsonProperty("hash") val hash: String? = null,
)

enum class TorboxStorageType(val label: String) {
    TORRENTS("Torrents"),
    WEB_DOWNLOADS("Web Downloads"),
    USENET("Usenet"),
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class TorboxTorrentData(
    @JsonProperty("id") val rawId: Int? = 0,
    @JsonProperty("name") val rawName: String? = null,
    @JsonProperty("size") val rawSize: Long? = 0L,
    @JsonProperty("hash") val hash: String? = null,
    @JsonProperty("download_state") val downloadState: String? = null,
    @JsonProperty("download_finished") val downloadFinished: Boolean? = null,
    @JsonProperty("progress") val progress: Double? = null,
    @JsonProperty("download_speed") val downloadSpeed: Long? = 0L,
    @JsonProperty("eta") val eta: Long? = 0L,
    @JsonProperty("seeds") val seeds: Int? = 0,
    @JsonProperty("peers") val peers: Int? = 0,
    @JsonProperty("created_at") val createdAt: String? = null,
    @JsonProperty("updated_at") val updatedAt: String? = null,
    @JsonProperty("files") val rawFiles: List<TorboxFile>? = null,
    @JsonProperty("original_url") val originalUrl: String? = null,
    val storageType: TorboxStorageType = TorboxStorageType.TORRENTS,
) {
    @get:JsonIgnore val id: Int get() = rawId ?: 0

    @get:JsonIgnore val name: String get() = rawName.orEmpty()

    @get:JsonIgnore val size: Long get() = rawSize ?: 0L

    @get:JsonIgnore val files: List<TorboxFile> get() = rawFiles ?: emptyList()

    val isCompleted: Boolean
        get() = downloadFinished == true || downloadState == "completed" || downloadState == "cached"

    val progressPercent: Int
        get() = when {
            isCompleted -> 100
            progress != null -> (progress * 100).toInt().coerceIn(0, 100)
            else -> 0
        }

    val downloadedBytes: Long
        get() = if (size > 0L && progress != null) {
            (size.toDouble() * progress.coerceIn(0.0, 1.0)).toLong()
        } else {
            0L
        }

    val formattedDownloadedSize: String
        get() {
            if (downloadedBytes <= 0L) return "0 MB"
            val gb = downloadedBytes.toDouble() / (1024 * 1024 * 1024)
            return if (gb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f GB", gb)
            } else {
                val mb = downloadedBytes.toDouble() / (1024 * 1024)
                String.format(java.util.Locale.US, "%.0f MB", mb)
            }
        }

    val formattedSpeed: String?
        get() {
            val s = downloadSpeed ?: return null
            if (s <= 0L) return null
            val mb = s.toDouble() / (1024 * 1024)
            return if (mb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f MB/s", mb)
            } else {
                val kb = s.toDouble() / 1024
                String.format(java.util.Locale.US, "%.0f KB/s", kb)
            }
        }

    val formattedEta: String?
        get() {
            val seconds = eta ?: return null
            if (seconds <= 0L) return null
            val m = seconds / 60
            val s = seconds % 60
            return if (m >= 60) {
                val h = m / 60
                val remM = m % 60
                "${h}h ${remM}m left"
            } else if (m > 0) {
                "${m}m ${s}s left"
            } else {
                "${s}s left"
            }
        }

    val formattedSize: String
        get() {
            if (size <= 0L) return ""
            val gb = size.toDouble() / (1024 * 1024 * 1024)
            return if (gb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f GB", gb)
            } else {
                val mb = size.toDouble() / (1024 * 1024)
                String.format(java.util.Locale.US, "%.0f MB", mb)
            }
        }
}

object TorboxApiClient {
    private const val BASE_URL = "https://api.torbox.app"

    private val mapper = jacksonObjectMapper().apply {
        configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        configure(DeserializationFeature.ACCEPT_FLOAT_AS_INT, true)
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun startDeviceAuthorization(appName: String = "CloudstreamDesktop"): TorboxDeviceAuthData? =
        withContext(Dispatchers.IO) {
            try {
                val url = "$BASE_URL/v1/api/user/auth/device/start?app=$appName"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<TorboxDeviceAuthData>>(bodyStr)
                    envelope.data
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to start device authorization", e)
                null
            }
        }

    suspend fun redeemDeviceAuthorization(deviceCode: String): TorboxTokenResult =
        withContext(Dispatchers.IO) {
            val normalized = deviceCode.trim()
            if (normalized.isBlank()) return@withContext TorboxTokenResult.Failed("Blank device code")

            try {
                val url = "$BASE_URL/v1/api/user/auth/device/token"
                val jsonPayload = mapper.writeValueAsString(mapOf("device_code" to normalized))
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(jsonPayload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    val status = response.code
                    val bodyStr = response.body.string()

                    if (status in 200..299) {
                        val envelope = runCatching {
                            mapper.readValue<TorboxEnvelope<TorboxDeviceTokenData>>(bodyStr)
                        }.getOrNull()
                        val token = envelope?.data?.token ?: envelope?.data?.sessionToken
                        if (!token.isNullOrBlank()) {
                            return@withContext TorboxTokenResult.Authorized(token)
                        }
                    }

                    val lowerMsg = bodyStr.lowercase()
                    when {
                        status == 404 || status == 409 || status == 425 ||
                            lowerMsg.contains("pending") ||
                            lowerMsg.contains("not authorized") ||
                            lowerMsg.contains("not been used") ||
                            lowerMsg.contains("not used yet") ||
                            lowerMsg.contains("scan the code") -> {
                            TorboxTokenResult.Pending
                        }
                        status == 410 || lowerMsg.contains("expired") -> {
                            TorboxTokenResult.Expired
                        }
                        else -> {
                            TorboxTokenResult.Failed("Status $status: $bodyStr")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Error redeeming device authorization", e)
                TorboxTokenResult.Failed(e.message)
            }
        }

    suspend fun getUser(apiKey: String): TorboxUserData? =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank()) return@withContext null

            try {
                val url = "$BASE_URL/v1/api/user/me"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<TorboxUserData>>(bodyStr)
                    envelope.data
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to fetch user info", e)
                null
            }
        }

    suspend fun checkCached(apiKey: String, hashes: List<String>): Map<String, TorboxCachedItem> =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            val normalizedHashes = hashes.map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct()
            if (key.isBlank() || normalizedHashes.isEmpty()) return@withContext emptyMap()

            try {
                val url = "$BASE_URL/v1/api/torrents/checkcached?format=object"
                val payload = mapper.writeValueAsString(mapOf("hashes" to normalizedHashes))
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyMap()
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<Map<String, TorboxCachedItem>>>(bodyStr)
                    envelope.data.orEmpty()
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to check cached torrents", e)
                emptyMap()
            }
        }

    suspend fun createTorrent(apiKey: String, magnet: String, addOnlyIfCached: Boolean = false): Int? =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank() || magnet.isBlank()) return@withContext null

            try {
                val url = "$BASE_URL/v1/api/torrents/createtorrent"
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("magnet", magnet)
                    .addFormDataPart("add_only_if_cached", if (addOnlyIfCached) "true" else "false")
                    .addFormDataPart("allow_zip", "false")
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .post(multipart)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<TorboxCreateTorrentData>>(bodyStr)
                    envelope.data?.torrentId
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to create torrent", e)
                null
            }
        }

    suspend fun createWebDownload(apiKey: String, link: String): Boolean =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank() || link.isBlank()) return@withContext false

            try {
                val url = "$BASE_URL/v1/api/webdl/createwebdownload"
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("link", link.trim())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .post(multipart)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to create web download", e)
                false
            }
        }

    suspend fun createUsenet(apiKey: String, link: String): Boolean =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank() || link.isBlank()) return@withContext false

            try {
                val url = "$BASE_URL/v1/api/usenet/createusenetdownload"
                val multipart = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("link", link.trim())
                    .build()

                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .post(multipart)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to create usenet download", e)
                false
            }
        }

    suspend fun getTorrent(apiKey: String, id: Int): TorboxTorrentData? =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank() || id <= 0) return@withContext null

            try {
                val url = "$BASE_URL/v1/api/torrents/mylist?id=$id&bypass_cache=true"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<TorboxTorrentData>>(bodyStr)
                    envelope.data
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to get torrent details", e)
                null
            }
        }

    suspend fun listTorrents(apiKey: String): List<TorboxTorrentData> =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = "$BASE_URL/v1/api/torrents/mylist?bypass_cache=true"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<List<TorboxTorrentData>>>(bodyStr)
                    envelope.data.orEmpty().map { it.copy(storageType = TorboxStorageType.TORRENTS) }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to list torrents", e)
                emptyList()
            }
        }

    suspend fun listWebDownloads(apiKey: String): List<TorboxTorrentData> =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = "$BASE_URL/v1/api/webdl/mylist?bypass_cache=true"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<List<TorboxTorrentData>>>(bodyStr)
                    envelope.data.orEmpty().map { it.copy(storageType = TorboxStorageType.WEB_DOWNLOADS) }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to list web downloads", e)
                emptyList()
            }
        }

    suspend fun listUsenet(apiKey: String): List<TorboxTorrentData> =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank()) return@withContext emptyList()

            try {
                val url = "$BASE_URL/v1/api/usenet/mylist?bypass_cache=true"
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val bodyStr = response.body.string()
                    val envelope = mapper.readValue<TorboxEnvelope<List<TorboxTorrentData>>>(bodyStr)
                    envelope.data.orEmpty().map { it.copy(storageType = TorboxStorageType.USENET) }
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to list usenet downloads", e)
                emptyList()
            }
        }

    suspend fun listItems(apiKey: String, type: TorboxStorageType): List<TorboxTorrentData> =
        when (type) {
            TorboxStorageType.TORRENTS -> listTorrents(apiKey)
            TorboxStorageType.WEB_DOWNLOADS -> listWebDownloads(apiKey)
            TorboxStorageType.USENET -> listUsenet(apiKey)
        }

    suspend fun deleteTorrent(apiKey: String, torrentId: Int): Boolean =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank() || torrentId <= 0) return@withContext false

            try {
                val url = "$BASE_URL/v1/api/torrents/controltorrent"
                val payload = mapper.writeValueAsString(
                    mapOf(
                        "operation" to "delete",
                        "torrent_id" to torrentId,
                    ),
                )
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to delete torrent $torrentId", e)
                false
            }
        }

    suspend fun deleteWebDownload(apiKey: String, webId: Int): Boolean =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank() || webId <= 0) return@withContext false

            try {
                val url = "$BASE_URL/v1/api/webdl/controlwebdownload"
                val payload = mapper.writeValueAsString(
                    mapOf(
                        "operation" to "delete",
                        "webdl_id" to webId,
                    ),
                )
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to delete web download $webId", e)
                false
            }
        }

    suspend fun deleteUsenet(apiKey: String, usenetId: Int): Boolean =
        withContext(Dispatchers.IO) {
            val key = apiKey.trim()
            if (key.isBlank() || usenetId <= 0) return@withContext false

            try {
                val url = "$BASE_URL/v1/api/usenet/controlusenet"
                val payload = mapper.writeValueAsString(
                    mapOf(
                        "operation" to "delete",
                        "usenet_id" to usenetId,
                    ),
                )
                val request = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $key")
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .post(payload.toRequestBody(jsonMediaType))
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                AppLogger.e("TorboxApiClient: Failed to delete usenet $usenetId", e)
                false
            }
        }

    suspend fun deleteItem(apiKey: String, item: TorboxTorrentData): Boolean =
        when (item.storageType) {
            TorboxStorageType.TORRENTS -> deleteTorrent(apiKey, item.id)
            TorboxStorageType.WEB_DOWNLOADS -> deleteWebDownload(apiKey, item.id)
            TorboxStorageType.USENET -> deleteUsenet(apiKey, item.id)
        }

    suspend fun requestDownloadLink(
        apiKey: String,
        torrentId: Int,
        fileId: Int? = null,
    ): String? = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank() || torrentId <= 0) return@withContext null

        try {
            val fileParam = if (fileId != null && fileId > 0) "&file_id=$fileId" else ""
            val url = "$BASE_URL/v1/api/torrents/requestdl?token=$key&torrent_id=$torrentId$fileParam&zip_link=false&redirect=false"
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body.string()
                val envelope = mapper.readValue<TorboxEnvelope<String>>(bodyStr)
                envelope.data?.takeIf { it.isNotBlank() && it.startsWith("http") }
            }
        } catch (e: Exception) {
            AppLogger.e("TorboxApiClient: Failed to request download link", e)
            null
        }
    }

    suspend fun requestDownloadLink(
        apiKey: String,
        item: TorboxTorrentData,
        fileId: Int? = null,
    ): String? = withContext(Dispatchers.IO) {
        val key = apiKey.trim()
        if (key.isBlank() || item.id <= 0) return@withContext null

        try {
            val fileParam = if (fileId != null && fileId > 0) "&file_id=$fileId" else ""
            val url = when (item.storageType) {
                TorboxStorageType.TORRENTS ->
                    "$BASE_URL/v1/api/torrents/requestdl?token=$key&torrent_id=${item.id}$fileParam&zip_link=false&redirect=false"
                TorboxStorageType.WEB_DOWNLOADS ->
                    "$BASE_URL/v1/api/webdl/requestdl?token=$key&web_id=${item.id}$fileParam&zip_link=false&redirect=false"
                TorboxStorageType.USENET ->
                    "$BASE_URL/v1/api/usenet/requestdl?token=$key&usenet_id=${item.id}$fileParam&zip_link=false&redirect=false"
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $key")
                .header("Accept", "application/json")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body.string()
                val envelope = mapper.readValue<TorboxEnvelope<String>>(bodyStr)
                envelope.data?.takeIf { it.isNotBlank() && it.startsWith("http") }
            }
        } catch (e: Exception) {
            AppLogger.e("TorboxApiClient: Failed to request download link for ${item.storageType}", e)
            null
        }
    }

    /**
     * Finds the best video file within a list of files for playback.
     */
    fun selectBestVideoFile(
        files: List<TorboxFile>,
        season: Int? = null,
        episode: Int? = null,
    ): TorboxFile? {
        if (files.isEmpty()) return null
        val videoExtensions = listOf(".mkv", ".mp4", ".avi", ".mov", ".webm", ".ts", ".m4v")
        val videoFiles = files.filter { file ->
            val lower = file.name.lowercase()
            videoExtensions.any { lower.endsWith(it) } &&
                !lower.contains("sample") &&
                !lower.contains("trailer")
        }

        if (videoFiles.isEmpty()) {
            return files.maxByOrNull { it.size }
        }

        if (season != null && episode != null) {
            val seasonPad = season.toString().padStart(2, '0')
            val epPad = episode.toString().padStart(2, '0')
            val sPatterns = listOf("s${seasonPad}e$epPad", "${season}x$epPad", "e$epPad", "ep$epPad")

            val match = videoFiles.firstOrNull { file ->
                val lower = file.name.lowercase()
                sPatterns.any { lower.contains(it) }
            }
            if (match != null) return match
        }

        return videoFiles.maxByOrNull { it.size }
    }
}
