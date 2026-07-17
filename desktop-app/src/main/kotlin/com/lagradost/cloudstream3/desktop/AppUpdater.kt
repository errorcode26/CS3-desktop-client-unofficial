package com.lagradost.cloudstream3.desktop

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

@JsonIgnoreProperties(ignoreUnknown = true)
data class GitHubRelease(
    val tag_name: String,
    val name: String,
    val body: String?,
    val html_url: String,
    val published_at: String
)

object AppUpdater {
    private val client = OkHttpClient()
    private val mapper = jacksonObjectMapper()

    private val _latestRelease = MutableStateFlow<GitHubRelease?>(null)
    val latestRelease: StateFlow<GitHubRelease?> = _latestRelease.asStateFlow()

    private var hasChecked = false

    suspend fun checkForUpdates(force: Boolean = false) {
        if (hasChecked && !force) return
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/${AppConfig.GITHUB_REPO}/releases/latest"
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github.v3+json")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.string()?.let { bodyString ->
                            val release = mapper.readValue<GitHubRelease>(bodyString)
                            val remoteVersion = release.tag_name.removePrefix("v")
                            
                            if (compareVersions(remoteVersion, AppConfig.APP_VERSION) > 0) {
                                _latestRelease.value = release
                            }
                        }
                    }
                }
                hasChecked = true
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("Update check failed", e)
            }
        }
    }

    internal fun compareVersions(v1: String, v2: String): Int {
        val parts1 = v1.split(".").map { it.toIntOrNull() ?: 0 }
        val parts2 = v2.split(".").map { it.toIntOrNull() ?: 0 }
        val length = maxOf(parts1.size, parts2.size)
        for (i in 0 until length) {
            val p1 = parts1.getOrElse(i) { 0 }
            val p2 = parts2.getOrElse(i) { 0 }
            if (p1 != p2) return p1.compareTo(p2)
        }
        return 0
    }
}
