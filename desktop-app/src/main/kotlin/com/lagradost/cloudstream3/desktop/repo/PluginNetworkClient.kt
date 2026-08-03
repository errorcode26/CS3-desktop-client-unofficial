package com.lagradost.cloudstream3.desktop.repo

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Internal network utility for repository and plugin list fetching.
 * Not part of the public API — consumed only by [DesktopRepositoryManager].
 */
internal object PluginNetworkClient {

    /** OkHttp client that follows redirects. Used for all content fetches. */
    internal val redirectClient by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .followRedirects(true)
            .build()
    }

    /** OkHttp client that does NOT follow redirects. Used for short-link resolution. */
    private val noRedirectClient by lazy {
        com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .followRedirects(false)
            .build()
    }

    /** Shared Jackson mapper — lenient, ignores unknown properties. */
    internal val mapper: ObjectMapper = ObjectMapper()
        .registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    /**
     * Resolves a user-provided input (short codes, custom schemes, plain URLs) to a
     * canonical HTTPS URL. Returns null if the input cannot be resolved.
     */
    suspend fun parseRepoUrl(url: String): String? = withContext(Dispatchers.IO) {
        val fixedUrl = url.trim()
        if (fixedUrl.matches(Regex("^[a-zA-Z0-9!_-]+$"))) {
            val request = Request.Builder().url("https://cutt.ly/$fixedUrl").build()
            noRedirectClient.newCall(request).execute().use { response ->
                val loc = response.header("Location")
                if (loc != null && !loc.startsWith("https://cutt.ly/404")) {
                    return@withContext loc
                }
            }
            return@withContext null
        }
        if (fixedUrl.contains(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"))) {
            return@withContext fixedUrl
                .replace(Regex("^(cloudstreamrepo://)|(https://cs\\.repo/\\??)"), "")
                .let { if (!it.startsWith("http")) "https://$it" else it }
        }
        if (!fixedUrl.matches(Regex("^https?://.*"))) return@withContext null
        return@withContext fixedUrl
    }

    /** Fetches and parses a [Repository] manifest JSON from [url]. Returns null on failure. */
    suspend fun fetchRepository(url: String): Repository? = withContext(Dispatchers.IO) {
        val finalUrl = parseRepoUrl(url)
            ?: url.trim().takeIf { it.startsWith("http") }
            ?: return@withContext null
        val request = Request.Builder().url(finalUrl).build()
        try {
            redirectClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body.string()
                if (body.trimStart().startsWith("<")) {
                    AppLogger.i("Repo fetch from $url returned HTML — likely behind a WAF.")
                    return@withContext null
                }
                return@withContext mapper.readValue(body, Repository::class.java)
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to fetch repository $url: ${e.message}")
            return@withContext null
        }
    }

    /** Fetches and parses a list of [SitePlugin] entries from [pluginListUrl]. Returns empty on failure. */
    suspend fun fetchPlugins(pluginListUrl: String): List<SitePlugin> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(pluginListUrl).build()
            redirectClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body.string()
                if (body.trimStart().startsWith("<")) {
                    AppLogger.i("Plugin list from $pluginListUrl returned HTML — likely behind a WAF.")
                    return@withContext emptyList()
                }
                return@withContext mapper
                    .readValue(body, object : TypeReference<List<SitePlugin>>() {})
                    .filter { it.status != 0 }
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to fetch or parse plugins from $pluginListUrl: ${e.message}")
            emptyList()
        }
    }
}
