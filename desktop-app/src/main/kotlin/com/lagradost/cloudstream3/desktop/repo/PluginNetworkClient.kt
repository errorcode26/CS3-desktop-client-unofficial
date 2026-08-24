package com.lagradost.cloudstream3.desktop.repo

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.desktop.network.AutoRetryInterceptor
import com.lagradost.cloudstream3.desktop.network.DevNetworkInterceptor
import com.lagradost.cloudstream3.desktop.network.RateLimitInterceptor
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
        val builder = com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .followRedirects(true)
            .connectTimeout(java.time.Duration.ofSeconds(4))
            .readTimeout(java.time.Duration.ofSeconds(6))
            .callTimeout(java.time.Duration.ofSeconds(15))
        // Strip scraper-only interceptors — repo fetches are static JSON, not scrapers.
        // RateLimitInterceptor queues 20+ concurrent requests to the same host behind a
        // 500ms/host lock, easily blowing the callTimeout before the request is even sent.
        builder.interceptors().removeAll { it is RateLimitInterceptor || it is AutoRetryInterceptor || it is DevNetworkInterceptor }
        builder.build()
    }

    /** OkHttp client that does NOT follow redirects. Used for short-link resolution. */
    private val noRedirectClient by lazy {
        val builder = com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .followRedirects(false)
            .connectTimeout(java.time.Duration.ofSeconds(3))
            .readTimeout(java.time.Duration.ofSeconds(4))
        builder.interceptors().removeAll { it is RateLimitInterceptor || it is AutoRetryInterceptor || it is DevNetworkInterceptor }
        builder.build()
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

    /**
     * Resolves a relative or absolute URL string against a base URL.
     * Guarantees a fully qualified HTTPS/HTTP URL.
     */
    fun resolveUrl(baseUrl: String, relativeOrAbsolute: String): String {
        val trimmed = relativeOrAbsolute.trim()
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed
        }
        return try {
            val baseUri = java.net.URI(baseUrl)
            baseUri.resolve(trimmed).toString()
        } catch (_: Exception) {
            val base = baseUrl.substringBeforeLast('/')
            "$base/${trimmed.removePrefix("./").removePrefix("/")}"
        }
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
                val trimmedBody = body.trim()
                if (trimmedBody.startsWith("[")) {
                    // Check if it's a direct plugins.json list
                    val sampleFirst = mapper.readTree(trimmedBody).firstOrNull()
                    if (sampleFirst != null && sampleFirst.has("internalName") && sampleFirst.has("url")) {
                        val repoName = finalUrl.substringBeforeLast('/').substringAfterLast('/').ifBlank { "Custom Repository" }
                        return@withContext Repository(
                            name = repoName,
                            description = "Imported plugin repository",
                            manifestVersion = 1,
                            pluginLists = listOf(finalUrl),
                            iconUrl = null,
                        )
                    }
                }

                val rawRepo = mapper.readValue(body, Repository::class.java)
                val rawLists = if (rawRepo.pluginLists.isNullOrEmpty()) {
                    listOf(
                        if (finalUrl.endsWith("repo.json", ignoreCase = true)) {
                            finalUrl.replace(Regex("repo\\.json$", RegexOption.IGNORE_CASE), "builds/plugins.json")
                        } else if (finalUrl.endsWith("plugins.json", ignoreCase = true)) {
                            finalUrl
                        } else {
                            "${finalUrl.trimEnd('/')}/builds/plugins.json"
                        }
                    )
                } else {
                    rawRepo.pluginLists
                }

                val resolvedLists = rawLists.map { listUrl ->
                    resolveUrl(finalUrl, listUrl)
                }
                val resolvedIcon = rawRepo.iconUrl?.takeIf { it.isNotBlank() }?.let { resolveUrl(finalUrl, it) }
                return@withContext rawRepo.copy(
                    iconUrl = resolvedIcon,
                    pluginLists = resolvedLists,
                )
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
                val rawPlugins = mapper.readValue(body, object : TypeReference<List<SitePlugin>>() {})
                return@withContext rawPlugins
                    .filter { it.status != 0 }
                    .map { plugin ->
                        val resolvedUrl = resolveUrl(pluginListUrl, plugin.url)
                        val resolvedJarUrl = plugin.jarUrl?.takeIf { it.isNotBlank() }?.let { resolveUrl(pluginListUrl, it) }
                        val resolvedIconUrl = plugin.iconUrl?.takeIf { it.isNotBlank() }?.let { resolveUrl(pluginListUrl, it) }
                        plugin.copy(
                            url = resolvedUrl,
                            jarUrl = resolvedJarUrl,
                            iconUrl = resolvedIconUrl,
                        )
                    }
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to fetch or parse plugins from $pluginListUrl: ${e.message}")
            emptyList()
        }
    }
}
