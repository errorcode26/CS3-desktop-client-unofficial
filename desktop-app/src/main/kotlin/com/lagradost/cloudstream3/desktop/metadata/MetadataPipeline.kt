package com.lagradost.cloudstream3.desktop.metadata

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.desktop.metadata.providers.AniListMetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.providers.CinemetaMetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.providers.KitsuMetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.providers.TmdbMetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.providers.TvMazeMetadataProvider
import com.lagradost.cloudstream3.desktop.repo.HeroCache
import com.lagradost.cloudstream3.desktop.repo.HeroMeta
import com.lagradost.cloudstream3.desktop.utils.TitleCleaner
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

object MetadataPipeline {
    private const val TAG = "MetadataPipeline"

    private val identityCache = ConcurrentHashMap<String, MetadataMatch>()
    private val inFlightResolutions = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<MetadataMatch?>>()

    private val providers = mutableListOf<MetadataProvider>(
        TmdbMetadataProvider,
        TvMazeMetadataProvider,
        AniListMetadataProvider,
        KitsuMetadataProvider,
        CinemetaMetadataProvider,
    )

    fun registerProvider(provider: MetadataProvider) {
        synchronized(providers) {
            if (providers.none { it.id == provider.id }) providers.add(provider)
        }
    }

    fun unregisterProvider(id: String) {
        synchronized(providers) { providers.removeAll { it.id == id } }
    }

    fun clearCache(title: String? = null) {
        if (title.isNullOrBlank()) {
            identityCache.clear()
            inFlightResolutions.clear()
            AppLogger.d(TAG, "Cleared entire metadata identity cache")
        } else {
            val keyPrefix = TitleCleaner.clean(title).lowercase().trim()
            if (keyPrefix.isBlank()) {
                AppLogger.d(TAG, "clearCache: '$title' cleaned to blank, skipping")
                return
            }
            var count = 0
            val it = identityCache.keys.iterator()
            while (it.hasNext()) {
                val key = it.next()
                if (key.startsWith(keyPrefix)) {
                    it.remove()
                    inFlightResolutions.remove(key)
                    count++
                }
            }
            AppLogger.d(TAG, "Evicted $count cache entries for '$title' (prefix='$keyPrefix')")
        }
    }

    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        fetchCast: Boolean = true,
        callbacks: MetadataEnrichmentCallbacks = MetadataEnrichmentCallbacks(),
    ) {
        withContext(Dispatchers.IO) {
            val isDummy = url.startsWith("dummy_")
            val urlClean = url.removePrefix("dummy_")

            val rawTitle = loaded.name

            // 1a. Aggressive title cleaning.
            val cleanName = TitleCleaner.clean(rawTitle)
            val titleYear = TitleCleaner.extractYear(rawTitle)
            val titleSeason = TitleCleaner.extractSeason(rawTitle)

            if (cleanName.isNotBlank() && cleanName != rawTitle) {
                loaded.name = cleanName
            }
            if (loaded.year == null && titleYear != null) {
                loaded.year = titleYear
            }

            // 1b. Season adjustment
            val allEpisodes = when (loaded) {
                is com.lagradost.cloudstream3.TvSeriesLoadResponse -> loaded.episodes
                is com.lagradost.cloudstream3.AnimeLoadResponse -> loaded.episodes.values.flatten()
                else -> emptyList()
            }
            val hasMultipleSeasons = allEpisodes.mapNotNull { it.season }.distinct().size > 1
            if (titleSeason != null && titleSeason > 1 && !hasMultipleSeasons) {
                allEpisodes.forEach { ep -> ep.season = titleSeason }
            }

            // 2. Year from URL slug
            if (loaded.year == null) {
                try {
                    val pathSegment = urlClean.substringBefore("?").split("/").lastOrNull { it.isNotBlank() }
                    if (pathSegment != null) {
                        val yearMatches = Regex("""\b(19\d{2}|20\d{2})\b""")
                            .findAll(pathSegment)
                            .map { it.range.first to it.groupValues[1].toInt() }
                            .toList()
                        val parsedYear = yearMatches.firstOrNull { (pos, _) ->
                            !(pos <= 2 && Regex("""^\d{4}\b""").containsMatchIn(cleanName))
                        }?.second
                        if (parsedYear != null) loaded.year = parsedYear
                    }
                } catch (_: Exception) {}
            }

            // 3. Type disambiguation
            val hasSeriesPattern = Regex("""(?i)\b(?:season|series|s\d{1,2}|episodes?|complete|all-episodes|web-series|tv-series)\b""").containsMatchIn(rawTitle)
                || Regex("""(?i)\b(?:season|series|s\d{1,2}|episodes?|all-episodes|web-series|tv-series)\b""").containsMatchIn(urlClean)
            val isMovie = loaded.type == com.lagradost.cloudstream3.TvType.Movie || loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie
            if (isMovie && (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse || allEpisodes.size > 1 || titleSeason != null || hasSeriesPattern)) {
                loaded.type = if (loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie) com.lagradost.cloudstream3.TvType.Anime else com.lagradost.cloudstream3.TvType.TvSeries
            }

            // 4. Direct ID extraction
            val directImdbId = loaded.syncData["imdb"]?.takeIf { it.startsWith("tt") && it != "tt0000000" }
                ?: loaded.syncData.values.firstNotNullOfOrNull { raw ->
                    Regex("""\b(tt\d{6,10})\b""").find(raw)?.groupValues?.get(1)?.takeIf { it != "tt0000000" }
                }
                ?: Regex("""\b(tt\d{6,10})\b""").find(urlClean)?.groupValues?.get(1)?.takeIf { it != "tt0000000" }
                ?: Regex("""\b(tt\d{6,10})\b""").find(loaded.url)?.groupValues?.get(1)?.takeIf { it != "tt0000000" }

            val directTmdbId = loaded.syncData["tmdb"]?.toIntOrNull()?.takeIf { it > 0 }
                ?: Regex("""themoviedb\.org/(?:movie|tv)/(\d+)""").find(urlClean)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }
                ?: Regex("""themoviedb\.org/(?:movie|tv)/(\d+)""").find(loaded.url)?.groupValues?.get(1)?.toIntOrNull()?.takeIf { it > 0 }

            // Multi-variant search list, computed once.
            val searchVariants = TitleCleaner.searchVariants(cleanName, loaded.year)

            AppLogger.i(
                TAG,
                "▶ START pipeline | raw='$rawTitle' | clean='$cleanName' | year=${loaded.year} | type=${loaded.type} " +
                    "| directImdb=$directImdbId | directTmdb=$directTmdbId | variants=$searchVariants"
            )

            val isAnime = loaded is com.lagradost.cloudstream3.AnimeLoadResponse ||
                loaded.type == com.lagradost.cloudstream3.TvType.Anime ||
                loaded.type == com.lagradost.cloudstream3.TvType.AnimeMovie ||
                loaded.type == com.lagradost.cloudstream3.TvType.OVA ||
                loaded.tags?.any { it.contains("anime", ignoreCase = true) || it.contains("animation", ignoreCase = true) } == true

            val currentProviders = synchronized(providers) { providers.toList() }
            val supportedProviders = currentProviders.filter { provider ->
                if (!MetadataConfig.isProviderEnabled(provider.id)) return@filter false
                provider.supportedTypes.contains(loaded.type) || (isAnime && (provider.id == "anilist" || provider.id == "kitsu"))
            }
            val sortedResolvers = supportedProviders.sortedWith(
                compareBy(
                    {
                        if (isAnime) {
                            val primary = MetadataConfig.animePrimaryProvider.value
                            if (it.id == primary) 0 else if (it.id == "anilist" || it.id == "kitsu") 1 else 2
                        } else {
                            if (it.id == "cinemeta") 0 else 1
                        }
                    },
                    { it.priority },
                )
            )

            // 5. Resolve Media Identity
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
                        val resolved = coroutineScope {
                            // Each resolver iterates the variant list in order and
                            // returns the first non-null match. All resolvers run
                            // concurrently.
                            val asyncList = sortedResolvers.map { resolver ->
                                resolver to async(Dispatchers.IO) {
                                    try {
                                        for (variant in searchVariants) {
                                            val match = try {
                                                resolver.resolve(variant, loaded.year, loaded.type, urlClean)
                                            } catch (e: CancellationException) {
                                                throw e
                                            } catch (e: Exception) {
                                                AppLogger.w(
                                                    TAG,
                                                    "Resolver ${resolver.id} errored for '$variant'",
                                                    e,
                                                )
                                                null
                                            }
                                            if (match != null) {
                                                if (variant != cleanName) {
                                                    AppLogger.i(
                                                        TAG,
                                                        "✓ ${resolver.id} matched on variant '$variant'"
                                                    )
                                                }
                                                return@async match
                                            }
                                        }
                                        null
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        AppLogger.w(TAG, "Resolver ${resolver.id} failed for '$cleanName'", e)
                                        null
                                    }
                                }
                            }

                            var bestMatch: MetadataMatch? = null
                            for ((resolver, asyncJob) in asyncList) {
                                val match = asyncJob.await()
                                if (match != null && bestMatch == null) {
                                    bestMatch = match
                                    AppLogger.i(
                                        TAG,
                                        "✓ Stage 1 Identity Resolved by ${resolver.id} -> '${match.matchedTitle}' (${match.matchedYear})"
                                    )
                                }
                            }
                            bestMatch
                        }

                        if (resolved != null) {
                            identityCache[identityKey] = resolved
                        }
                        deferred.complete(resolved)
                    } catch (e: Throwable) {
                        deferred.completeExceptionally(e)
                        throw e
                    } finally {
                        inFlightResolutions.remove(identityKey)
                    }
                }

                activeMatch = try {
                    deferred.await()
                } catch (_: Exception) {
                    null
                }
            } else {
                AppLogger.i(TAG, "✓ Reusing cached match for '$cleanName' (IMDb: ${activeMatch.imdbId}, TMDB: ${activeMatch.tmdbId})")
            }

            // 6. Progressive Enrichment
            val context = MetadataEnrichmentContext(
                rawUrl = if (isDummy) "dummy_$urlClean" else urlClean,
                isDummy = isDummy,
                fetchCast = fetchCast,
                directImdbId = activeMatch?.imdbId ?: directImdbId,
                directTmdbId = activeMatch?.tmdbId ?: directTmdbId,
            )

            val sortedEnrichers = supportedProviders.sortedWith(
                compareBy(
                    {
                        if (isAnime) {
                            val primary = MetadataConfig.animePrimaryProvider.value
                            if (it.id == primary) 0 else if (it.id == "anilist" || it.id == "kitsu") 1 else 2
                        } else {
                            if (it.id == "tmdb") 0 else 1
                        }
                    },
                    { it.priority },
                )
            )

            coroutineScope {
                val enrichJobs = sortedEnrichers.map { enricher ->
                    async(Dispatchers.IO) {
                        try {
                            enricher.enrich(loaded, activeMatch, context, callbacks)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            AppLogger.e(TAG, "Enricher ${enricher.id} failed", e)
                        }
                    }
                }
                enrichJobs.forEach { it.await() }
            }

            // 7. Hero cache
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

    fun getCachedImdbId(showName: String?): String? {
        if (showName.isNullOrBlank()) return null
        val cleanName = TitleCleaner.clean(showName)
        if (cleanName.isBlank()) return null
        val cleanLower = cleanName.lowercase().trim()
        return identityCache.values.firstOrNull { match ->
            match.imdbId?.startsWith("tt", ignoreCase = true) == true &&
                (match.matchedTitle.equals(cleanName, ignoreCase = true) || match.matchedTitle.lowercase().trim() == cleanLower)
        }?.imdbId
    }
}