package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ARCHITECTURE NOTE: This is the enrichment orchestrator. It calls Stage 1 (Cinemeta)
// and Stage 2 (TMDB) sequentially, merging their results into the LoadResponse.
//
// Each enrichment source (Cinemeta, TMDB) is a self-contained service behind a
// try/catch boundary. If any source goes down, the pipeline degrades gracefully
// to whatever data the remaining sources (and the raw provider) can supply.
//
// DO NOT couple downstream consumers (DetailsViewModel, UI screens) directly to
// any specific enrichment source. All enrichment data flows through the generic
// callback parameters (onMetadataLoaded, onScreenshotsLoaded, etc.).
//
// To replace TMDB: create a new service with the same enrich() signature and
// swap the call on the Stage 2 line below. No other files need to change.
object HybridEnrichmentService {
    private const val TAG = "Enrichment"

    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        fetchCast: Boolean = true,
        onScreenshotsLoaded: (List<String>) -> Unit,
        onActorsLoaded: (List<com.lagradost.cloudstream3.ActorData>) -> Unit = {},
        onTrailersLoaded: (List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData>) -> Unit = {},
        onReviewsLoaded: (List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ReviewData>) -> Unit = {},
        onMetadataLoaded: (
            tagline: String?,
            status: String?,
            studios: List<String>,
            collectionName: String?,
            collectionBg: String?,
            seasonsCount: Int?,
            episodesCount: Int?,
            seasons: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata>?,
            originalLang: String?,
            releaseDate: String?,
            country: String?,
            collectionItems: List<com.lagradost.cloudstream3.SearchResponse>,
            budget: Long?,
            revenue: Long?,
            networks: List<String>?,
            year: Int?,
            duration: Int?,
            tags: List<String>?,
            actors: List<com.lagradost.cloudstream3.ActorData>?,
        ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
        onEnrichmentComplete: () -> Unit = {},
        onEpisodeThumbnailsEnriched: () -> Unit = {},
    ) {
        withContext(Dispatchers.IO) {
            val type = if (loaded.type == com.lagradost.cloudstream3.TvType.Movie) "movie" else "series"

            // Parse season number from title if all episodes default to null or 1
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
                allEpisodes.forEach { ep ->
                    ep.season = titleSeason
                }
            }

            val urlClean = url.removePrefix("dummy_")
            var parsedYear: Int? = null
            var parsedUrlName: String? = null
            try {
                // Split URL by '/' and take the last segment that is not blank, ignoring query params if present
                val pathSegment = urlClean.substringBefore("?").split("/").lastOrNull { it.isNotBlank() }
                if (pathSegment != null) {
                    val yearMatch = Regex("""\b(19\d{2}|20\d{2})\b""").find(pathSegment)
                    parsedYear = yearMatch?.groupValues?.get(1)?.toIntOrNull()

                    val rawParsed = pathSegment
                        .replace(Regex("""^(?i)download-"""), "")
                        .replace(Regex("""-\d{4}$"""), "")
                        .replace(Regex("""\b(19\d{2}|20\d{2})\b"""), "")
                        .replace(Regex("""(?i)-(?:dual-audio|hindi-dubbed|english-dubbed|multi-audio|full-movie|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray|season|series|episode|episodes|s\d{1,2}|e\d{1,2}|part-\d+).*"""), "")
                        .replace("-", " ")
                        .trim()
                    if (rawParsed.isNotBlank()) {
                        parsedUrlName = rawParsed
                    }
                }
            } catch (e: Exception) {
                // Ignore parsing errors
            }

            if (loaded.year == null && parsedYear != null) {
                loaded.year = parsedYear
            }

            // 1. Strip provider junk from the title before any lookup.
            AppLogger.i(TAG, "▶ START enrich | raw='${loaded.name}' | url='$url' | type=$type | year=${loaded.year}")
            val (cleanName, titleYear) = TitleUtils.cleanProviderTitle(loaded.name)
            if (loaded.year == null && titleYear != null) loaded.year = titleYear

            AppLogger.i(TAG, "  cleanName='$cleanName' | urlParsed='$parsedUrlName' | parsedYear=$parsedYear")

            // 2. Cinemeta text search — fast, ~100ms.
            // Many providers misreport the type (e.g. tag series as "movie"),
            // so we try both types if the first search yields no match.
            val altType = if (type == "movie") "series" else "movie"
            var searchResult: CinemetaAPI.CinemetaMeta? = null

            for (searchType in listOf(type, altType)) {
                val searchResults = CinemetaAPI.search(cleanName, searchType)
                AppLogger.i(TAG, "  Cinemeta search '$cleanName' ($searchType) → ${searchResults?.size ?: 0} results")

                if (!searchResults.isNullOrEmpty()) {
                    for (result in searchResults) {
                        val searchResultName = result.name ?: continue
                        val cleanCompare = cleanName.lowercase().removePrefix("the ").trim()
                        val resultCompare = searchResultName.lowercase().removePrefix("the ").trim()

                        val strippedResultName = resultCompare.replace(Regex("[^a-zA-Z0-9]"), "")
                        val strippedCleanName = cleanCompare.replace(Regex("[^a-zA-Z0-9]"), "")

                        val cleanWords = cleanCompare.replace(Regex("[^a-z0-9 ]"), "").split(" ").filter { it.isNotBlank() }
                        val resultWords = resultCompare.replace(Regex("[^a-z0-9 ]"), "").split(" ").filter { it.isNotBlank() }

                        // Check strict digit/roman number match to prevent Iron Man -> Iron Man 2 mismatches
                        val numbers1 = Regex("""\b\d+\b""").findAll(cleanCompare).map { it.value }.toSet()
                        val numbers2 = Regex("""\b\d+\b""").findAll(resultCompare).map { it.value }.toSet()
                        val romanRegex = Regex("""\b(ii|iii|iv|v|vi|vii|viii|ix|x)\b""")
                        val romans1 = romanRegex.findAll(cleanCompare).map { it.value }.toSet()
                        val romans2 = romanRegex.findAll(resultCompare).map { it.value }.toSet()
                        val hasNumberMismatch = numbers1 != numbers2 || romans1 != romans2

                        val isStrictMatch = strippedResultName.equals(strippedCleanName, ignoreCase = true)

                        // Strictly reject if years don't match (allowing a 1-year tolerance for release date weirdness)
                        val loadedYear = loaded.year
                        val resultYear = result.releaseInfo?.take(4)?.toIntOrNull()
                        val yearMismatch = resultYear != null && loadedYear != null && Math.abs(resultYear - loadedYear) > 1
                        if (yearMismatch) continue

                        var score = com.lagradost.cloudstream3.desktop.utils.StringUtils.similarity(strippedCleanName, strippedResultName)
                        if (isStrictMatch) {
                            score = 1.0
                        }

                        if (hasNumberMismatch) {
                            score = 0.0
                        }

                        if (score >= 0.80) {
                            AppLogger.i(TAG, "  ✓ Cinemeta MATCH: '$searchResultName' (${result.id}) score=$score year=${result.releaseInfo} type=$searchType")
                            loaded.name = searchResultName
                            searchResult = result
                            break
                        }
                    }
                }
                // If we found a match, stop; otherwise try the fallback type.
                if (searchResult != null) break
            }
            if (searchResult == null) {
                AppLogger.w(TAG, "  ✗ Cinemeta: no match for '$cleanName' (year=${loaded.year}) [tried $type + $altType]")
            }

            // 3. If Cinemeta matched, fetch full meta to get moviedb_id and logo.
            val cinemetaData: CinemetaAPI.CinemetaMeta? = if (searchResult?.id != null) {
                try {
                    CinemetaAPI.getMeta(searchResult.id, type)
                } catch (e: Exception) {
                    AppLogger.e(TAG, "  ✗ Cinemeta getMeta CRASHED for id=${searchResult.id}", e)
                    null
                }
            } else {
                null
            }
            AppLogger.i(TAG, "  Cinemeta meta: bg=${cinemetaData?.background != null} | logo=${cinemetaData?.logo != null} | tmdbId=${cinemetaData?.moviedbId} | imdbId=${searchResult?.id}")

            // Extract IDs for TMDB direct lookup
            val directTmdbId: Int? = cinemetaData?.moviedbId
            val directImdbId: String? = searchResult?.id?.takeIf { it.startsWith("tt") }

            // 3.5. STAGE 1: Populate Cinemeta metadata instantly to update the UI
            if (cinemetaData != null) {
                withContext(Dispatchers.Main.immediate) {
                    if (loaded.posterUrl.isNullOrBlank() && cinemetaData.poster != null) {
                        loaded.posterUrl = cinemetaData.poster
                    }
                    val cleanCinemetaBg = cinemetaData.background?.replace("t/p/original//", "t/p/original/")
                    if (loaded.backgroundPosterUrl.isNullOrBlank() && cleanCinemetaBg != null) {
                        loaded.backgroundPosterUrl = cleanCinemetaBg
                        AppLogger.i(TAG, "  ✓ Stage1: set backdrop from Cinemeta")
                    }
                    if (loaded.plot.isNullOrBlank() && cinemetaData.description != null) {
                        loaded.plot = cinemetaData.description
                    }
                    // MetaHub logo as fallback
                    val cinemetaLogoUrl = cinemetaData.logo
                        ?: searchResult?.id?.let { imdbId -> "https://images.metahub.space/logo/medium/$imdbId/img" }
                    if (cinemetaLogoUrl != null) {
                        when (loaded) {
                            is com.lagradost.cloudstream3.MovieLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = cinemetaLogoUrl
                            is com.lagradost.cloudstream3.TvSeriesLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = cinemetaLogoUrl
                            is com.lagradost.cloudstream3.AnimeLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = cinemetaLogoUrl
                            else -> {}
                        }
                    }
                    if (cinemetaData.imdbRating != null) {
                        val ratingDouble = cinemetaData.imdbRating.toDoubleOrNull()
                        if (ratingDouble != null) {
                            loaded.score = com.lagradost.cloudstream3.Score.from10(ratingDouble)
                        }
                    }

                    // Cinemeta episode descriptions + per-episode IMDb scores + thumbnails
                    if (!cinemetaData.videos.isNullOrEmpty()) {
                        allEpisodes.forEach { ep ->
                            val seasonToUse = ep.season ?: 1
                            val cinemetaEp = cinemetaData.videos.find { it.season == seasonToUse && it.episode == ep.episode }
                            if (cinemetaEp != null) {
                                if (ep.description.isNullOrBlank() && !cinemetaEp.description.isNullOrBlank()) {
                                    ep.description = cinemetaEp.description
                                }
                                if (cinemetaEp.released != null) {
                                    val releaseDateIso = cinemetaEp.released.take(10) // Format: "YYYY-MM-DD"
                                    val cleanDesc = (ep.description ?: "").replace(Regex("\\|\\|DATE:.*?\\|\\|"), "")
                                    ep.description = "||DATE:$releaseDateIso||" + cleanDesc
                                }
                                // Cinemeta fills gaps. A blank URL or a known CF-protected
                                // provider URL (e.g. imgbb.zip) both count as "missing".
                                val isMissingOrBadUrl = ep.posterUrl.isNullOrBlank() ||
                                    ep.posterUrl?.contains("imgbb") == true
                                if (isMissingOrBadUrl && !cinemetaEp.thumbnail.isNullOrBlank()) {
                                    ep.posterUrl = cinemetaEp.thumbnail
                                }
                                if (cinemetaEp.imdbRating != null && ep.score == null) {
                                    val epRatingDouble = cinemetaEp.imdbRating.toDoubleOrNull()
                                    if (epRatingDouble != null) {
                                        ep.score = com.lagradost.cloudstream3.Score.from10(epRatingDouble)
                                    }
                                }
                            }
                        }
                    }
                    // Signal that ep.posterUrl fields were mutated — triggers Compose recompose
                    onEpisodeThumbnailsEnriched()
                }

                // Fire Stage 1 metadata to the UI for instant loading
                onMetadataLoaded(
                    null, // tagline
                    null, // status
                    emptyList(), // studios
                    null, // collectionName
                    null, // collectionBg
                    null, // seasonsCount
                    null, // episodesCount
                    null, // seasonsMetadata
                    null, // originalLang
                    null, // releaseDate
                    null, // country
                    emptyList(), // collectionItems
                    null, // budget
                    null, // revenue
                    null, // networks
                    loaded.year ?: cinemetaData.releaseInfo?.take(4)?.toIntOrNull(),
                    loaded.duration,
                    cinemetaData.genres,
                    loaded.actors,
                )
            }

            // 4. STAGE 2: Kick off TMDB lookup in the background
            AppLogger.i(TAG, "  Stage2: TMDB lookup | directTmdbId=$directTmdbId | directImdbId=$directImdbId")
            var tmdbTagline: String? = null
            var tmdbStatus: String? = null
            var tmdbStudios: List<String> = emptyList()
            var tmdbCollectionName: String? = null
            var tmdbCollectionBg: String? = null
            var tmdbSeasonsCount: Int? = null
            var tmdbEpisodesCount: Int? = null
            var tmdbSeasonsMetadata: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.SeasonMetadata>? = null
            var tmdbOriginalLang: String? = null
            var tmdbReleaseDate: String? = null
            var tmdbCountry: String? = null
            var tmdbCollectionItems: List<com.lagradost.cloudstream3.SearchResponse> = emptyList()
            var tmdbBudget: Long? = null
            var tmdbRevenue: Long? = null
            var tmdbNetworks: List<String>? = null
            var tmdbYear: Int? = null
            var tmdbDuration: Int? = null
            var tmdbTags: List<String>? = null
            var tmdbActors: List<com.lagradost.cloudstream3.ActorData>? = null

            try {
                TmdbEnrichmentService.enrich(
                    loaded = loaded,
                    url = url,
                    fetchCast = fetchCast,
                    onScreenshotsLoaded = onScreenshotsLoaded,
                    onActorsLoaded = onActorsLoaded,
                    onTrailersLoaded = onTrailersLoaded,
                    onReviewsLoaded = onReviewsLoaded,
                    onMetadataLoaded = { tagline, status, studios, collectionName, collectionBg, seasonsCount, episodesCount, seasons, originalLang, releaseDate, country, collectionItems, budget, revenue, networks, year, duration, tags, actors ->
                        tmdbTagline = tagline
                        tmdbStatus = status
                        tmdbStudios = studios
                        tmdbCollectionName = collectionName
                        tmdbCollectionBg = collectionBg
                        tmdbSeasonsCount = seasonsCount
                        tmdbEpisodesCount = episodesCount
                        tmdbSeasonsMetadata = seasons
                        tmdbOriginalLang = originalLang
                        tmdbReleaseDate = releaseDate
                        tmdbCountry = country
                        tmdbCollectionItems = collectionItems
                        tmdbBudget = budget
                        tmdbRevenue = revenue
                        tmdbNetworks = networks
                        tmdbYear = year
                        tmdbDuration = duration
                        tmdbTags = tags
                        tmdbActors = actors
                    },
                    onEnrichmentComplete = {},
                    directTmdbId = directTmdbId,
                    directImdbId = directImdbId,
                    overwrite = false,
                    onEpisodeThumbnailsEnriched = {
                        onEpisodeThumbnailsEnriched()
                    },
                )
                AppLogger.i(TAG, "  ✓ Stage2: TMDB done | bg=${loaded.backgroundPosterUrl != null} | logo=${loaded.logoUrl != null}")
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLogger.e(TAG, "  ✗ Stage2: TMDB CRASHED (Cinemeta data preserved) — ${e::class.simpleName}: ${e.message}")
            }

            // 5. Merge final metadata: fill any gaps TMDB left
            if (cinemetaData != null) {
                if (loaded.posterUrl.isNullOrBlank() && cinemetaData.poster != null) {
                    loaded.posterUrl = cinemetaData.poster
                }
                val cleanCinemetaBg = cinemetaData.background?.replace("t/p/original//", "t/p/original/")
                if (loaded.backgroundPosterUrl.isNullOrBlank() && cleanCinemetaBg != null) {
                    loaded.backgroundPosterUrl = cleanCinemetaBg
                }
                if (loaded.plot.isNullOrBlank() && cinemetaData.description != null) {
                    loaded.plot = cinemetaData.description
                }
                val cinemetaLogoUrl = cinemetaData.logo
                    ?: searchResult?.id?.let { imdbId -> "https://images.metahub.space/logo/medium/$imdbId/img" }
                if (cinemetaLogoUrl != null) {
                    when (loaded) {
                        is com.lagradost.cloudstream3.MovieLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = cinemetaLogoUrl
                        is com.lagradost.cloudstream3.TvSeriesLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = cinemetaLogoUrl
                        is com.lagradost.cloudstream3.AnimeLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = cinemetaLogoUrl
                        else -> {}
                    }
                }
                if (cinemetaData.imdbRating != null && loaded.score == null) {
                    val ratingDouble = cinemetaData.imdbRating.toDoubleOrNull()
                    if (ratingDouble != null) {
                        loaded.score = com.lagradost.cloudstream3.Score.from10(ratingDouble)
                    }
                }
            }

            // 6. Fire Stage 2 metadata to update the UI with premium details
            onMetadataLoaded(
                tmdbTagline,
                tmdbStatus,
                tmdbStudios,
                tmdbCollectionName,
                tmdbCollectionBg,
                tmdbSeasonsCount,
                tmdbEpisodesCount,
                tmdbSeasonsMetadata,
                tmdbOriginalLang,
                tmdbReleaseDate,
                tmdbCountry,
                tmdbCollectionItems,
                tmdbBudget,
                tmdbRevenue,
                tmdbNetworks,
                tmdbYear,
                tmdbDuration,
                tmdbTags ?: cinemetaData?.genres,
                tmdbActors,
            )

            // 7. Cache the perfect enriched metadata for the Hero slider to use later
            val heroTitle = loaded.name.takeIf { it.isNotBlank() }
            val heroBackdrop = loaded.backgroundPosterUrl?.takeIf { it.isNotBlank() }
            val heroLogo = loaded.logoUrl?.takeIf { it.isNotBlank() }
            val heroTags = loaded.tags?.take(4) ?: emptyList()
            val heroPlot = loaded.plot?.take(200)
            val heroScore = loaded.score?.toString()
            val heroMeta = com.lagradost.cloudstream3.desktop.repo.HeroMeta(
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
            val cacheKey = "${loaded.apiName}_$url" // Matches HeroRepository cacheKey format
            com.lagradost.common.storage.DesktopDataStore.setKey("herometa_$cacheKey", heroMeta)
            com.lagradost.cloudstream3.desktop.repo.HeroCache.put(cacheKey, heroMeta)

            onEnrichmentComplete()
        }
    }
}
