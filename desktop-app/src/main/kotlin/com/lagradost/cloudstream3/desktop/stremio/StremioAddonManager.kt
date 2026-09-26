package com.lagradost.cloudstream3.desktop.stremio

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.subtitles.LanguageNormalizer
import com.lagradost.cloudstream3.desktop.utils.appScope
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Universal Manager for External Stremio Addons in CloudStream Desktop.
 * Provides a manifest-driven, non-intrusive addon architecture for Subtitles & Metadata.
 */
object StremioAddonManager {
    private const val TAG = "StremioAddonManager"
    private const val PREF_INSTALLED_ADDONS = "cs_desktop_stremio_installed_addons"

    private val mapper = jacksonObjectMapper()
    private val STREAM_SIZE_REGEX = Regex("(?:💾\\s*)?([0-9.]+\\s*(?:GB|MB|GiB|MiB|KB|TB))", RegexOption.IGNORE_CASE)

    val DEFAULT_ADDONS: List<ManagedStremioAddon> = listOf(
        ManagedStremioAddon(
            manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
            name = "Cinemeta",
            description = "The official movie and TV show catalog for Stremio",
            version = "3.0.12",
            enabled = true,
            providesSubtitles = false,
            providesMetadata = true,
            providesStreams = false,
            providesCatalogs = true,
            types = listOf("movie", "series"),
            idPrefixes = listOf("tt"),
            catalogsSummary = listOf("Top Movies", "Top Series"),
        )
    )

    private val _addons = MutableStateFlow<List<ManagedStremioAddon>>(emptyList())
    val addons: StateFlow<List<ManagedStremioAddon>> = _addons.asStateFlow()

    init {
        loadAddons()
    }

    fun loadAddons() {
        try {
            val savedJson = DesktopDataStore.getKey<String>(PREF_INSTALLED_ADDONS)
            if (savedJson != null) {
                if (savedJson.isNotBlank() && savedJson.trim() != "[]") {
                    val list = mapper.readValue(
                        savedJson,
                        mapper.typeFactory.constructCollectionType(List::class.java, ManagedStremioAddon::class.java),
                    ) as? List<ManagedStremioAddon>
                    if (!list.isNullOrEmpty()) {
                        val hasCinemeta = list.any { it.manifestUrl.contains("cinemeta", ignoreCase = true) }
                        _addons.value = if (!hasCinemeta) list + DEFAULT_ADDONS else list
                        return
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load installed addons from storage", e)
        }

        _addons.value = DEFAULT_ADDONS
        saveAddons()
    }

    private fun saveAddons() {
        try {
            val json = mapper.writeValueAsString(_addons.value)
            DesktopDataStore.setKey(PREF_INSTALLED_ADDONS, json)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to persist installed addons", e)
        }
    }

    suspend fun addAddon(rawUrl: String): Result<ManagedStremioAddon> = withContext(Dispatchers.IO) {
        val normalizedUrl = StremioTransport.normalizeManifestUrl(rawUrl)

        // Check if already installed
        val existing = _addons.value.find { it.manifestUrl.equals(normalizedUrl, ignoreCase = true) }
        if (existing != null) {
            return@withContext Result.failure(IllegalArgumentException("Addon is already installed: ${existing.name}"))
        }

        try {
            AppLogger.i(TAG, "Fetching addon manifest from $normalizedUrl...")
            val responseText = app.get(normalizedUrl, timeout = 8000L).text
            val manifest = StremioManifestParser.parse(normalizedUrl, responseText)

            val newAddon = ManagedStremioAddon(
                manifestUrl = normalizedUrl,
                name = manifest.name.ifBlank { "Addon" },
                description = manifest.description,
                version = manifest.version,
                logoUrl = manifest.logoUrl,
                backgroundUrl = manifest.backgroundUrl,
                enabled = true,
                providesSubtitles = manifest.providesSubtitles,
                providesMetadata = manifest.providesMetadata,
                providesStreams = manifest.providesStreams,
                providesCatalogs = manifest.providesCatalogs,
                types = manifest.types,
                idPrefixes = manifest.idPrefixes,
                catalogsSummary = manifest.catalogs.map { it.name.ifBlank { it.id } },
                isP2P = manifest.behaviorHints.p2p,
                isConfigurable = manifest.behaviorHints.configurable,
            )

            val updated = _addons.value + newAddon
            _addons.value = updated
            saveAddons()

            AppLogger.i(TAG, "Successfully installed addon: ${newAddon.name} (v${newAddon.version})")
            Result.success(newAddon)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to fetch/parse manifest from $normalizedUrl: ${e.message}", e)
            Result.failure(e)
        }
    }

    fun removeAddon(manifestUrl: String) {
        val updated = _addons.value.filterNot { it.manifestUrl.equals(manifestUrl, ignoreCase = true) }
        _addons.value = updated
        saveAddons()
    }

    fun setAddonEnabled(manifestUrl: String, enabled: Boolean) {
        val updated = _addons.value.map { addon ->
            if (addon.manifestUrl.equals(manifestUrl, ignoreCase = true)) {
                addon.copy(enabled = enabled)
            } else {
                addon
            }
        }
        _addons.value = updated
        saveAddons()
    }

    fun moveAddon(fromIndex: Int, toIndex: Int) {
        if (fromIndex !in _addons.value.indices || toIndex !in _addons.value.indices || fromIndex == toIndex) return
        val list = _addons.value.toMutableList()
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _addons.value = list
        saveAddons()
    }

    fun refreshAddon(manifestUrl: String) {
        appScope.launch(Dispatchers.IO) {
            try {
                val normalizedUrl = StremioTransport.normalizeManifestUrl(manifestUrl)
                val responseText = app.get(normalizedUrl, timeout = 6000L).text
                val manifest = StremioManifestParser.parse(normalizedUrl, responseText)

                val updated = _addons.value.map { addon ->
                    if (addon.manifestUrl.equals(normalizedUrl, ignoreCase = true)) {
                        addon.copy(
                            name = manifest.name.ifBlank { addon.name },
                            description = manifest.description,
                            version = manifest.version,
                            logoUrl = manifest.logoUrl,
                            backgroundUrl = manifest.backgroundUrl,
                            providesSubtitles = manifest.providesSubtitles,
                            providesMetadata = manifest.providesMetadata,
                            providesStreams = manifest.providesStreams,
                            providesCatalogs = manifest.providesCatalogs,
                            types = manifest.types,
                            idPrefixes = manifest.idPrefixes,
                            catalogsSummary = manifest.catalogs.map { it.name.ifBlank { it.id } },
                            isP2P = manifest.behaviorHints.p2p,
                            isConfigurable = manifest.behaviorHints.configurable,
                            errorMessage = null,
                        )
                    } else {
                        addon
                    }
                }
                _addons.value = updated
                saveAddons()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to refresh addon $manifestUrl: ${e.message}", e)
            }
        }
    }

    fun getEnabledSubtitleAddons(): List<ManagedStremioAddon> {
        return _addons.value.filter { it.enabled && it.providesSubtitles }
    }

    fun getEnabledMetadataAddons(): List<ManagedStremioAddon> {
        return _addons.value.filter { it.enabled && it.providesMetadata }
    }

    fun getEnabledStreamAddons(): List<ManagedStremioAddon> {
        return _addons.value.filter { it.enabled && it.providesStreams }
    }

    suspend fun searchStreams(
        imdbId: String?,
        season: Int? = null,
        episode: Int? = null,
        title: String? = null,
        onLink: (ExtractorLink) -> Unit,
    ) = withContext(Dispatchers.IO) {
        var cleanImdb = imdbId?.trim()?.takeIf { it.startsWith("tt", ignoreCase = true) }
        if (cleanImdb == null && !title.isNullOrBlank()) {
            cleanImdb = resolveImdbId(title, isSeries = (season != null && season > 0))
        }
        if (cleanImdb == null) {
            AppLogger.d(TAG, "Skipping Stremio stream query: No verified IMDb ID (title='$title')")
            return@withContext
        }

        val activeAddons = getEnabledStreamAddons()
        if (activeAddons.isEmpty()) return@withContext

        val isSeries = (season != null && season > 0) || (episode != null && episode > 0)
        val requestType = if (isSeries) "series" else "movie"
        val requestVideoId = if (isSeries) {
            "$cleanImdb:${season ?: 1}:${episode ?: 1}"
        } else {
            cleanImdb
        }

        AppLogger.i(TAG, "Querying ${activeAddons.size} Stremio stream addons for $requestType:$requestVideoId")

        val deferred = activeAddons.map { addon ->
            async {
                withTimeoutOrNull(5000L) {
                    try {
                        val streamUrl = StremioTransport.buildStreamUrl(
                            manifestUrl = addon.manifestUrl,
                            type = requestType,
                            id = requestVideoId,
                        )
                        AppLogger.d(TAG, "Querying stream addon '${addon.name}': $streamUrl")
                        val responseText = app.get(streamUrl, timeout = 4500L).text
                        val parsed = mapper.readValue(responseText, StremioStreamResponse::class.java)
                        val streams = parsed.streams ?: return@withTimeoutOrNull

                        for (item in streams) {
                            val streamUrlStr = item.url?.trim() ?: continue
                            if (streamUrlStr.isBlank() || !streamUrlStr.startsWith("http", ignoreCase = true)) continue

                            val titleText = item.title ?: item.description ?: ""
                            val rawName = item.name?.replace("\r", "")?.trim() ?: ""
                            val addonName = addon.name.ifBlank { "Stremio" }
                            val parsedQuality = parseQualityFromText(titleText, item.name)

                            val headers = item.behaviorHints?.headers ?: emptyMap()
                            val isM3u8 = streamUrlStr.contains(".m3u8", ignoreCase = true) || streamUrlStr.contains("m3u8", ignoreCase = true)
                            val isDash = streamUrlStr.contains(".mpd", ignoreCase = true)

                            val linkType = when {
                                isM3u8 -> ExtractorLinkType.M3U8
                                isDash -> ExtractorLinkType.DASH
                                else -> ExtractorLinkType.VIDEO
                            }

                            // 1. Extract file size (e.g. from "💾 4.56 GB" or "4.56 GB")
                            val sizeMatch = STREAM_SIZE_REGEX.find(titleText)?.groupValues?.get(1)
                                ?: STREAM_SIZE_REGEX.find(rawName)?.groupValues?.get(1)
                            val cleanSizeTag = sizeMatch?.let {
                                val normalizedUnit = it.replace("GiB", "GB", ignoreCase = true)
                                    .replace("MiB", "MB", ignoreCase = true)
                                    .trim()
                                "[$normalizedUnit]"
                            }

                            // 2. Extract release title (filtering out pure stats/emoji lines)
                            val contentLines = titleText.lines()
                                .map { it.trim() }
                                .filter { it.isNotBlank() && !it.startsWith("💾") && !it.startsWith("👤") && !it.startsWith("⚙️") }

                            val releaseTitle = contentLines.firstOrNull()?.ifBlank { null }
                                ?: rawName.lines().lastOrNull()?.trim()?.ifBlank { null }
                                ?: "Stream"

                            // 3. Extract provider or cache tag from rawName if available (e.g. "[RD+]")
                            val providerTag = if (rawName.isNotBlank() && !rawName.equals(addonName, ignoreCase = true)) {
                                val bracketMatch = Regex("\\[(.*?)\\]").find(rawName)?.value
                                val firstToken = rawName.lines().firstOrNull()?.trim()
                                bracketMatch ?: if (!firstToken.isNullOrBlank() && firstToken != releaseTitle) "[$firstToken]" else "[$addonName]"
                            } else {
                                "[$addonName]"
                            }

                            val cleanLabel = buildString {
                                append("⚡ ")
                                if (providerTag.isNotBlank()) {
                                    append("$providerTag ")
                                }
                                append(releaseTitle)
                                if (cleanSizeTag != null && !releaseTitle.contains(cleanSizeTag.drop(1).dropLast(1), ignoreCase = true)) {
                                    append(" $cleanSizeTag")
                                }
                            }

                            val extractorLink = newExtractorLink(
                                source = addonName,
                                name = cleanLabel,
                                url = streamUrlStr,
                                type = linkType,
                            ) {
                                this.referer = headers["Referer"] ?: ""
                                this.quality = parsedQuality
                                this.headers = headers
                            }

                            onLink(extractorLink)
                        }
                    } catch (e: Exception) {
                        AppLogger.w(TAG, "Stream query failed for '${addon.name}': ${e.message}")
                    }
                }
            }
        }

        deferred.awaitAll()
    }

    private val RESOLUTION_REGEX = Regex("(?i)(?:^|[^0-9a-z])(2160p|4k|uhd|1440p|2k|qhd|1080p|fhd|720p|hd|480p|sd|360p|1080|720)(?:[^0-9a-z]|$)")

    private fun parseQualityFromText(title: String, name: String?): Int {
        val combined = "$title ${name ?: ""}"
        val match = RESOLUTION_REGEX.find(combined)
        if (match != null) {
            val token = match.groupValues[1].lowercase()
            return when {
                token.contains("2160") || token == "4k" || token == "uhd" -> Qualities.P2160.value
                token.contains("1440") || token == "2k" || token == "qhd" -> Qualities.P1440.value
                token.contains("1080") || token == "fhd" -> Qualities.P1080.value
                token.contains("720") || token == "hd" -> Qualities.P720.value
                token.contains("480") || token == "sd" -> Qualities.P480.value
                token.contains("360") -> Qualities.P360.value
                else -> Qualities.P1080.value
            }
        }
        val lower = combined.lowercase()
        return when {
            lower.contains("2160") || lower.contains("4k") || lower.contains("uhd") -> Qualities.P2160.value
            lower.contains("1440") || lower.contains("2k") -> Qualities.P1440.value
            lower.contains("1080") || lower.contains("fhd") -> Qualities.P1080.value
            lower.contains("720") -> Qualities.P720.value
            lower.contains("480") || lower.contains("sd") -> Qualities.P480.value
            lower.contains("360") -> Qualities.P360.value
            else -> Qualities.P1080.value
        }
    }

    suspend fun searchSubtitles(
        query: String,
        lang: String?,
        season: Int?,
        episode: Int?,
        imdbId: String? = null,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val activeAddons = getEnabledSubtitleAddons()
        if (activeAddons.isEmpty()) return@withContext emptyList()

        // Resolve IMDb ID via active metadata addons if needed
        var resolvedImdb = imdbId?.takeIf { it.startsWith("tt") }
        if (resolvedImdb == null && query.isNotBlank()) {
            resolvedImdb = resolveImdbId(query, isSeries = (season != null && season > 0))
        }

        if (resolvedImdb == null) {
            AppLogger.d(TAG, "Cannot query Stremio subtitle addons: No valid IMDb ID for '$query'")
            return@withContext emptyList()
        }

        val isSeries = (season != null && season > 0) || (episode != null && episode > 0)
        val requestType = if (isSeries) "series" else "movie"
        val requestVideoId = if (isSeries) {
            "$resolvedImdb:${season ?: 1}:${episode ?: 1}"
        } else {
            resolvedImdb
        }

        val allResults = mutableListOf<Map<String, Any?>>()

        val deferredList = activeAddons.map { addon ->
            async {
                try {
                    val subUrl = StremioTransport.buildSubtitleUrl(
                        manifestUrl = addon.manifestUrl,
                        type = requestType,
                        id = requestVideoId,
                    )
                    AppLogger.d(TAG, "Querying subtitle addon '${addon.name}': $subUrl")
                    val responseText = app.get(subUrl, timeout = 6000L).text
                    val parsed = mapper.readValue(responseText, StremioSubtitleResponse::class.java)
                    val items = parsed.subtitles ?: return@async emptyList()

                    val langCounters = mutableMapOf<String, Int>()
                    val addonEntities = mutableListOf<Map<String, Any?>>()

                    for (item in items) {
                        val downloadUrl = item.url ?: continue
                        val itemLang = item.lang ?: "eng"

                        // Filter by language if user requested specific language
                        if (!lang.isNullOrBlank() && !LanguageNormalizer.isMatch(lang, itemLang)) {
                            continue
                        }

                        val norm = LanguageNormalizer.normalize(itemLang)
                        val count = (langCounters[norm.displayName] ?: 0) + 1
                        langCounters[norm.displayName] = count

                        val scoreText = item.rating?.toIntOrNull()?.let { score ->
                            if (score > 10) " (Score: $score)" else ""
                        } ?: ""
                        val encodingInfo = item.subEncoding?.takeIf { it.isNotBlank() && !it.equals("UTF-8", true) }?.let { " [$it]" } ?: ""
                        val displayName = "${norm.displayName} - Track #$count$scoreText$encodingInfo"

                        addonEntities.add(
                            mapOf(
                                "idPrefix" to (item.id ?: "stremio_${addon.name}_$count"),
                                "name" to displayName,
                                "lang" to norm.code2,
                                "langName" to norm.displayName,
                                "langBadge" to norm.badge,
                                "data" to downloadUrl,
                                "source" to addon.name,
                                "seasonNumber" to season,
                                "epNumber" to episode,
                            ),
                        )
                    }
                    addonEntities
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Subtitle query failed for '${addon.name}': ${e.message}")
                    emptyList()
                }
            }
        }

        val results = deferredList.awaitAll().flatten()
        allResults.addAll(results)
        AppLogger.i(TAG, "Aggregated ${allResults.size} subtitles across ${activeAddons.size} Stremio addons")
        return@withContext allResults
    }

    private suspend fun resolveImdbId(query: String, isSeries: Boolean): String? {
        val metaAddons = getEnabledMetadataAddons()
        for (addon in metaAddons) {
            try {
                val clean = query.trim()
                val type = if (isSeries) "series" else "movie"
                val encoded = java.net.URLEncoder.encode(clean, "UTF-8")
                val baseUrl = StremioTransport.getBaseUrl(addon.manifestUrl)
                val searchUrl = "$baseUrl/catalog/$type/top/search=$encoded.json"

                val jsonText = app.get(searchUrl, timeout = 5000L).text
                val root = mapper.readTree(jsonText)
                val metas = root["metas"]
                val id = metas?.firstOrNull { it["id"]?.asText()?.startsWith("tt") == true }?.get("id")?.asText()
                if (id != null) {
                    AppLogger.d(TAG, "Resolved '$query' -> $id via ${addon.name}")
                    return id
                }
            } catch (e: Exception) {
                // Try next metadata addon
            }
        }
        return null
    }
}
