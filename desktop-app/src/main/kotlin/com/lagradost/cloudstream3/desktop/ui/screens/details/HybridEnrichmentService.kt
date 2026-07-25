package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.lagradost.cloudstream3.LoadResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

object HybridEnrichmentService {

    suspend fun enrich(
        loaded: LoadResponse,
        url: String,
        fetchCast: Boolean = true,
        onScreenshotsLoaded: (List<String>) -> Unit,
        onActorsLoaded: (List<com.lagradost.cloudstream3.ActorData>) -> Unit = {},
        onMetadataLoaded: (
            tagline: String?,
            status: String?,
            studios: List<String>,
            collectionName: String?,
            collectionBg: String?,
            seasonsCount: Int?,
            episodesCount: Int?,
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
        ) -> Unit = { _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _, _ -> },
        onEnrichmentComplete: () -> Unit = {},
    ) {
        withContext(Dispatchers.IO) {
            val type = if (loaded.type == com.lagradost.cloudstream3.TvType.Movie) "movie" else "series"

            // 1. Ask Cinemeta to resolve "shitty" provider titles first.
            // This search is extremely fast and provides a highly accurate, clean title.
            val cleanName = loaded.name
                .replace(Regex("""\s*\(\d{4}\).*"""), "")
                .replace(Regex("""\s*[\(\[\{]?(?i)(dual audio|multi audio|hindi dubbed|full movie|720p|1080p|480p|2160p|webrip|web-dl|hdtv|bluray).*"""), "")
                .replace(Regex("""\s*[\[\{].*"""), "")
                .replace(Regex("""\s*\|.*"""), "")
                .replace(Regex("""(?i)(-\s*)?\b(season|series|episodes|episode)\b\s*\d+.*"""), "")
                .replace(Regex("""(?i)\bs\d{1,2}(e\d{1,2})?\b.*"""), "")
                .trim()

            var searchResult = CinemetaAPI.search(cleanName, type)
            val originalName = loaded.name

            if (searchResult?.name != null) {
                val searchResultName = searchResult.name
                val strippedResultName = searchResultName.replace(Regex("[^a-zA-Z0-9]"), "")
                val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")

                val resultWords = searchResultName.lowercase().replace(Regex("[^a-z0-9 ]"), "").split(" ").filter { it.isNotBlank() }
                val cleanWords = cleanName.lowercase().replace(Regex("[^a-z0-9 ]"), "").split(" ").filter { it.isNotBlank() }
                val isStrictMatch = strippedResultName.equals(strippedCleanName, ignoreCase = true)
                val isSubsetMatch = resultWords.isNotEmpty() && cleanWords.size > 1 && (cleanWords.containsAll(resultWords) || resultWords.containsAll(cleanWords))

                var score = com.lagradost.cloudstream3.desktop.utils.StringUtils.similarity(strippedCleanName.lowercase(), strippedResultName.lowercase())
                if (isStrictMatch) {
                    score = 1.0
                } else if (isSubsetMatch && score < 0.85) {
                    score = 0.85
                }

                if (score >= 0.80) {
                    loaded.name = searchResultName
                } else {
                    searchResult = null // Reject completely invalid match
                }
            }

            // 2. Kick off full Cinemeta metadata fetch concurrently
            val cinemetaDeferred = async {
                if (searchResult?.id != null) {
                    CinemetaAPI.getMeta(searchResult.id, type)
                } else {
                    null
                }
            }

            // 3. Let TMDB do its full enrichment block using the cleaned title.
            // We intercept onMetadataLoaded to inject Cinemeta's IMDb rating.
            var tmdbTagline: String? = null
            var tmdbStatus: String? = null
            var tmdbStudios: List<String> = emptyList()
            var tmdbCollectionName: String? = null
            var tmdbCollectionBg: String? = null
            var tmdbSeasonsCount: Int? = null
            var tmdbEpisodesCount: Int? = null
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

            TmdbEnrichmentService.enrich(
                loaded = loaded,
                url = url,
                fetchCast = fetchCast,
                onScreenshotsLoaded = onScreenshotsLoaded,
                onActorsLoaded = onActorsLoaded,
                onMetadataLoaded = { tagline, status, studios, collectionName, collectionBg, seasonsCount, episodesCount, originalLang, releaseDate, country, collectionItems, budget, revenue, networks, year, duration, tags, actors ->
                    tmdbTagline = tagline
                    tmdbStatus = status
                    tmdbStudios = studios
                    tmdbCollectionName = collectionName
                    tmdbCollectionBg = collectionBg
                    tmdbSeasonsCount = seasonsCount
                    tmdbEpisodesCount = episodesCount
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
                onEnrichmentComplete = {}, // We will call the real one at the very end
            )

            // 3. Await Cinemeta results
            val cinemetaData = cinemetaDeferred.await()

            // 4. Merge best of both worlds into `loaded`
            if (cinemetaData != null) {
                if (loaded.posterUrl.isNullOrBlank() && cinemetaData.poster != null) {
                    loaded.posterUrl = cinemetaData.poster
                }
                if (loaded.backgroundPosterUrl.isNullOrBlank() && cinemetaData.background != null) {
                    loaded.backgroundPosterUrl = cinemetaData.background
                }
                if (loaded.plot.isNullOrBlank() && cinemetaData.description != null) {
                    loaded.plot = cinemetaData.description
                }
                if (cinemetaData.imdbRating != null) {
                    val ratingDouble = cinemetaData.imdbRating.toDoubleOrNull()
                    if (ratingDouble != null) {
                        loaded.score = com.lagradost.cloudstream3.Score.from10(ratingDouble)
                    }
                }

                // Merge episode descriptions and ratings
                if (!cinemetaData.videos.isNullOrEmpty()) {
                    val allEpisodes = if (loaded is com.lagradost.cloudstream3.TvSeriesLoadResponse) {
                        loaded.episodes
                    } else if (loaded is com.lagradost.cloudstream3.AnimeLoadResponse) {
                        loaded.episodes.values.flatten()
                    } else {
                        emptyList()
                    }

                    allEpisodes.forEach { ep ->
                        val seasonToUse = ep.season ?: 1
                        val cinemetaEp = cinemetaData.videos.find { it.season == seasonToUse && it.episode == ep.episode }
                        if (cinemetaEp != null) {
                            if (ep.description.isNullOrBlank() && !cinemetaEp.description.isNullOrBlank()) {
                                ep.description = cinemetaEp.description
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
            }

            // 5. Fire the merged metadata back to the UI
            onMetadataLoaded(
                tmdbTagline,
                tmdbStatus,
                tmdbStudios,
                tmdbCollectionName,
                tmdbCollectionBg,
                tmdbSeasonsCount,
                tmdbEpisodesCount,
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

            // 6. Complete
            onEnrichmentComplete()
        }
    }
}
