package com.lagradost.cloudstream3.desktop.metadata

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.desktop.metadata.providers.AniListMetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.providers.CinemetaMetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.providers.KitsuMetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.providers.TmdbMetadataProvider
import com.lagradost.cloudstream3.desktop.repo.HeroCache
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Orchestrator that coordinates metadata resolution, progressive enrichment,
 * and background caching across all registered [MetadataProvider] instances.
 */
object MetadataPipeline {
    private const val TAG = "MetadataPipeline"

    private val identityCache = ConcurrentHashMap<String, MetadataMatch>()
    private val inFlightResolutions = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<MetadataMatch?>>()

    private val providers = mutableListOf<MetadataProvider>(
        TmdbMetadataProvider,
        AniListMetadataProvider,
        KitsuMetadataProvider,
        CinemetaMetadataProvider,
    )

    /**
     * Registers an additional metadata provider into the pipeline.
     */
    fun registerProvider(provider: MetadataProvider) {
        synchronized(providers) {
            if (providers.none { it.id == provider.id }) {
                providers.add(provider)
            }
        }
    }

    /**
     * Unregisters a metadata provider by ID.
     */
    fun unregisterProvider(id: String) {
        synchronized(providers) {
            providers.removeAll { it.id == id }
        }
    }

    /**
     * Executes the full metadata enrichment lifecycle for the given [loaded] response.
     */
    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        fetchCast: Boolean = true,
        callbacks: MetadataEnrichmentCallbacks = MetadataEnrichmentCallbacks(),
    ) {
        withContext(Dispatchers.IO) {
            val isDummy = url.startsWith("dummy_")
            val urlClean = url.removePrefix("dummy_")

            // 1. Season adjustment from title if all episodes default to null or 1
            val titleSeason = Regex("""(?i)\b(?:season|series)\b\s*(\d+)""").find(loaded.name)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(?i)\bs(\d{1,2})\b""").find(loaded.name)?.groupValues?.get(1)?.toIntOrNull()
                ?: Regex("""(?i)\bs(\d{1,2})e\d+""").find(loaded.name)?.groupValues?.get(1)?.toIntOrNull()

            val allEpisodes = when (loaded) {
                is com.lagradost.cloudstream3.TvSeriesLoadResponse -> loaded.episodes
                is com.lagradost.cloudstream3.AnimeLoadResponse -> loaded.episodes.values.flatten()
                else -> emptyList()
            }
            val hasMultipleSeasons = allEpisodes.mapNotNull { it.season }.distinct().size > 1
            if (titleSeason != null && titleSeason > 1 && !hasMultipleSeasons) {
                allEpisodes.forEach { ep -> ep.season = titleSeason }
            }

            // 2. Extract year from URL slug if available
            var parsedYear: Int? = null
            try {
                val pathSegment = urlClean.substringBefore("?").split("/").lastOrNull { it.isNotBlank() }
                if (pathSegment != null) {
                    val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(pathSegment)
                    parsedYear = yearMatch?.groupValues?.get(1)?.toIntOrNull()
                }
            } catch (_: Exception) {}

            if (loaded.year == null && parsedYear != null) {
                loaded.year = parsedYear
            }

            val (cleanName, titleYear) = TitleUtils.cleanProviderTitle(loaded.name)
            if (loaded.year == null && titleYear != null) {
                loaded.year = titleYear
            }

            AppLogger.i(TAG, "▶ START pipeline | raw='${loaded.name}' | clean='$cleanName' | year=${loaded.year} | type=${loaded.type}")

            val currentProviders = synchronized(providers) { providers.toList() }
            val supportedProviders = currentProviders.filter {
                MetadataConfig.isProviderEnabled(it.id) && it.supportedTypes.contains(loaded.type)
            }

            val isAnime = loaded.type == com.lagradost.cloudstream3.TvType.Anime ||
                loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie ||
                loaded.type == com.lagradost.cloudstream3.TvType.OVA

            val sortedResolvers = supportedProviders.sortedWith(
                compareBy(
                    { if (isAnime) (if (it.id == "kitsu") 0 else if (it.id == "anilist") 1 else 2) else (if (it.id == "cinemeta") 0 else 1) },
                    { it.priority },
                )
            )

            // 3. Resolve Media Identity (Stage 1 Resolvers with In-Flight Deduplication)
            val identityKey = "${cleanName.lowercase().trim()}_${loaded.year}_${loaded.type}"
            var activeMatch: MetadataMatch? = identityCache[identityKey]

            if (activeMatch == null) {
                var isInitiator = false
                val deferred = inFlightResolutions.computeIfAbsent(identityKey) {
                    isInitiator = true
                    kotlinx.coroutines.CompletableDeferred()
                }

                if (isInitiator) {
                    try {
                        var resolved: MetadataMatch? = null
                        for (resolver in sortedResolvers) {
                            try {
                                val match = resolver.resolve(loaded.name, loaded.year, loaded.type, urlClean)
                                if (match != null) {
                                    AppLogger.i(TAG, "✓ Resolved match via ${resolver.id}: '${match.matchedTitle}' (IMDb: ${match.imdbId}, TMDB: ${match.tmdbId}, AniList: ${match.anilistId})")
                                    resolved = match
                                    break
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                AppLogger.w(TAG, "Resolver ${resolver.id} threw an exception", e)
                            }
                        }
                        deferred.complete(resolved)
                    } catch (t: Throwable) {
                        deferred.completeExceptionally(t)
                    } finally {
                        inFlightResolutions.remove(identityKey)
                    }
                }

                activeMatch = deferred.await()
                if (activeMatch != null) {
                    identityCache[identityKey] = activeMatch
                }
            } else {
                AppLogger.i(TAG, "✓ Reusing cached match for '$cleanName' (IMDb: ${activeMatch.imdbId}, TMDB: ${activeMatch.tmdbId})")
            }

            // 4. Progressive Enrichment (Stage 1 -> Stage 2+)
            val context = MetadataEnrichmentContext(
                rawUrl = if (isDummy) "dummy_$urlClean" else urlClean,
                isDummy = isDummy,
                fetchCast = fetchCast,
                directImdbId = activeMatch?.imdbId,
                directTmdbId = activeMatch?.tmdbId,
            )

            val sortedEnrichers = supportedProviders.sortedWith(
                compareBy(
                    { if (isAnime) (if (it.id == "kitsu") 0 else if (it.id == "anilist") 1 else 2) else (if (it.id == "tmdb") 0 else 1) },
                    { it.priority },
                )
            )

            for (enricher in sortedEnrichers) {
                try {
                    enricher.enrich(loaded, activeMatch, context, callbacks)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Enricher ${enricher.id} failed", e)
                }
            }

            // 5. Store high-res metadata into Hero cache
            val heroTitle = loaded.name.takeIf { it.isNotBlank() }
            val heroBackdrop = loaded.backgroundPosterUrl?.takeIf { it.isNotBlank() }
            val heroLogo = loaded.logoUrl?.takeIf { it.isNotBlank() }
            val heroTags = loaded.tags?.take(4) ?: emptyList()
            val heroPlot = loaded.plot?.take(200)
            val heroScore = loaded.score?.toString()

            val heroMeta = HeroMeta(
                title = heroTitle,
                backdropUrl = heroBackdrop,
                logoUrl = heroLogo,
                tags = heroTags,
                plot = heroPlot,
                score = heroScore,
                year = loaded.year,
                type = loaded.type,
                contentRating = loaded.contentRating,
                duration = loaded.duration,
            )
            val cacheKey = "${loaded.apiName}_$urlClean"
            DesktopDataStore.setKey("herometa_$cacheKey", heroMeta)
            HeroCache.put(cacheKey, heroMeta)

            callbacks.onEnrichmentComplete()
            AppLogger.i(TAG, "✓ Pipeline completed successfully for '${loaded.name}'")
        }
    }

    /**
     * Attempts to retrieve a verified IMDb ID from the in-memory identity cache.
     */
    fun getCachedImdbId(showName: String?): String? {
        if (showName.isNullOrBlank()) return null
        val (cleanName, _) = TitleUtils.cleanProviderTitle(showName)
        val cleanLower = cleanName.lowercase().trim()
        return identityCache.values.firstOrNull { match ->
            match.imdbId?.startsWith("tt", ignoreCase = true) == true &&
                (match.matchedTitle.equals(cleanName, ignoreCase = true) || match.matchedTitle.lowercase().trim() == cleanLower)
        }?.imdbId
    }
}
