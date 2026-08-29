package com.lagradost.cloudstream3.desktop.metadata.providers

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentCallbacks
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentContext
import com.lagradost.cloudstream3.desktop.metadata.MetadataMatch
import com.lagradost.cloudstream3.desktop.metadata.MetadataProvider
import com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient
import com.lagradost.cloudstream3.desktop.utils.StringUtils
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CinemetaMetadataProvider : MetadataProvider {
    private const val TAG = "StremioMetaProvider"

    override val id: String = "cinemeta"
    override val displayName: String = "Custom Stremio Addon"
    override val priority: Int = 10
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Cartoon,
        TvType.Documentary,
        TvType.AsianDrama,
    )

    override suspend fun resolve(
        title: String,
        year: Int?,
        type: TvType,
        rawUrl: String?,
    ): MetadataMatch? {
        val stringType = if (type == TvType.Movie) "movie" else "series"
        val altType = if (stringType == "movie") "series" else "movie"

        val titleCandidates = TitleUtils.extractRootTitleCandidates(title)
        var searchResult: StremioAddonClient.StremioMetaItem? = null
        var resolvedType = stringType
        var bestScore = 0.0

        for (candidate in titleCandidates) {
            val cleanName = candidate.first
            val targetYear = year ?: candidate.second

            for (searchType in listOf(stringType, altType)) {
                val searchResults = StremioAddonClient.search(cleanName, searchType)
                AppLogger.i(TAG, "Search '$cleanName' ($searchType) → ${searchResults?.size ?: 0} results")

                if (!searchResults.isNullOrEmpty()) {
                    for (result in searchResults) {
                        val searchResultName = result.name ?: continue
                        val cleanCompare = cleanName.lowercase().removePrefix("the ").trim()
                        val resultCompare = searchResultName.lowercase().removePrefix("the ").trim()

                        val strippedResultName = resultCompare.replace(Regex("[^a-zA-Z0-9]"), "")
                        val strippedCleanName = cleanCompare.replace(Regex("[^a-zA-Z0-9]"), "")

                        // Check strict digit/roman number match to prevent Iron Man -> Iron Man 2 mismatches
                        val numbers1 = Regex("""\b\d+\b""").findAll(cleanCompare).map { it.value }.toSet()
                        val numbers2 = Regex("""\b\d+\b""").findAll(resultCompare).map { it.value }.toSet()
                        val romanRegex = Regex("""\b(ii|iii|iv|v|vi|vii|viii|ix|x)\b""")
                        val romans1 = romanRegex.findAll(cleanCompare).map { it.value }.toSet()
                        val romans2 = romanRegex.findAll(resultCompare).map { it.value }.toSet()
                        val hasNumberMismatch = numbers1 != numbers2 || romans1 != romans2
                        if (hasNumberMismatch) continue

                        val isStrictMatch = strippedResultName.equals(strippedCleanName, ignoreCase = true)
                        val isTv = type == TvType.TvSeries || type == TvType.Anime || type == TvType.AsianDrama || type == TvType.Cartoon
                        val resultYear = result.releaseInfo?.take(4)?.toIntOrNull()

                        // Year validation: for TV series, start year can precede season year
                        if (resultYear != null && targetYear != null) {
                            if (isTv) {
                                if (resultYear > targetYear + 1) continue
                            } else {
                                if (Math.abs(resultYear - targetYear) > 1) continue
                            }
                        }

                        var nameSimilarity = StringUtils.similarity(strippedCleanName, strippedResultName)
                        if (isStrictMatch) nameSimilarity = 1.0

                        if (nameSimilarity < 0.65) continue

                        // Calculate weighted composite score:
                        val compositeScore = nameSimilarity * 10.0 + when {
                            resultYear == targetYear -> 5.0
                            isTv && resultYear != null && targetYear != null && resultYear <= targetYear -> 2.0
                            else -> 0.0
                        }

                        if (compositeScore > bestScore) {
                            bestScore = compositeScore
                            searchResult = result
                            resolvedType = searchType
                            AppLogger.i(TAG, "✓ Candidate match: '$searchResultName' (${result.id}) compositeScore=$compositeScore year=${result.releaseInfo} type=$searchType")
                            if (isStrictMatch && resultYear == targetYear) break
                        }
                    }
                }
                if (bestScore >= 15.0) break
            }
            if (bestScore >= 15.0) break
            if (searchResult != null && bestScore >= 8.0) break
        }

        val matchId = searchResult?.id ?: return null
        val fullMeta = try {
            StremioAddonClient.getMeta(matchId, resolvedType)
        } catch (e: Exception) {
            AppLogger.e(TAG, "getMeta failed for id=$matchId", e)
            null
        }

        val imdbId = matchId.takeIf { it.startsWith("tt") }
        val tmdbId = fullMeta?.moviedbId

        return MetadataMatch(
            providerId = id,
            matchedTitle = searchResult.name ?: (titleCandidates.firstOrNull()?.first ?: title),
            matchedYear = searchResult.releaseInfo?.take(4)?.toIntOrNull() ?: year ?: titleCandidates.firstOrNull()?.second,
            imdbId = imdbId,
            tmdbId = tmdbId,
            posterUrl = fullMeta?.poster ?: searchResult.poster,
            backdropUrl = fullMeta?.background?.replace("t/p/original//", "t/p/original/"),
            logoUrl = fullMeta?.logo ?: imdbId?.let { "https://images.metahub.space/logo/medium/$it/img" },
            description = fullMeta?.description,
            genres = fullMeta?.genres,
            rating = fullMeta?.imdbRating?.toDoubleOrNull(),
            rawData = fullMeta,
        )
    }

    override suspend fun enrich(
        loaded: LoadResponse,
        match: MetadataMatch?,
        context: MetadataEnrichmentContext,
        callbacks: MetadataEnrichmentCallbacks,
    ): Boolean {
        if (match == null) return false
        val cinemetaData = match.rawData as? StremioAddonClient.StremioMetaItem

        withContext(Dispatchers.Main.immediate) {
            if (loaded.name.isBlank()) {
                loaded.name = match.matchedTitle
            }
            if (match.posterUrl != null) {
                loaded.posterUrl = match.posterUrl
            }
            if (match.backdropUrl != null) {
                loaded.backgroundPosterUrl = match.backdropUrl
            }
            if (loaded.plot.isNullOrBlank() && match.description != null) {
                loaded.plot = match.description
            }
            if (match.logoUrl != null) {
                when (loaded) {
                    is com.lagradost.cloudstream3.MovieLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = match.logoUrl
                    is com.lagradost.cloudstream3.TvSeriesLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = match.logoUrl
                    is com.lagradost.cloudstream3.AnimeLoadResponse -> if (loaded.logoUrl.isNullOrBlank()) loaded.logoUrl = match.logoUrl
                    else -> {}
                }
            }
            if (match.rating != null) {
                if (loaded.score == null) {
                    loaded.score = com.lagradost.cloudstream3.Score.from10(match.rating)
                }
                callbacks.onRatingsLoaded(match.rating, null, null)
            }

            // Episode descriptions, thumbnails, and ratings
            val allEpisodes = when (loaded) {
                is com.lagradost.cloudstream3.TvSeriesLoadResponse -> loaded.episodes
                is com.lagradost.cloudstream3.AnimeLoadResponse -> loaded.episodes.values.flatten()
                else -> emptyList()
            }

            if (!cinemetaData?.videos.isNullOrEmpty()) {
                allEpisodes.forEach { ep ->
                    val seasonToUse = ep.season ?: 1
                    val cinemetaEp = cinemetaData.videos.find { it.season == seasonToUse && it.episode == ep.episode }
                    if (cinemetaEp != null) {
                        if (ep.description.isNullOrBlank() && !cinemetaEp.description.isNullOrBlank()) {
                            ep.description = cinemetaEp.description
                        }
                        if (cinemetaEp.released != null) {
                            val releaseDateIso = cinemetaEp.released.take(10)
                            val cleanDesc = (ep.description ?: "").replace(Regex("\\|\\|DATE:.*?\\|\\|"), "")
                            ep.description = "||DATE:$releaseDateIso||" + cleanDesc
                        }
                        val isMissingOrBadUrl = ep.posterUrl.isNullOrBlank() || ep.posterUrl?.contains("imgbb") == true
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
                callbacks.onEpisodeThumbnailsEnriched()
            }
        }

        // Fire Stage 1 instant metadata to UI
        callbacks.onMetadataLoaded(
            null, // tagline
            null, // status
            emptyList(), // studios
            null, // collectionName
            null, // collectionBg
            null, // seasonsCount
            null, // episodesCount
            null, // seasonsMetadata
            null, // originalLang
            cinemetaData?.releaseInfo, // releaseDate
            null, // country
            emptyList(), // collectionItems
            null, // budget
            null, // revenue
            null, // networks
            loaded.year ?: match.matchedYear,
            loaded.duration,
            match.genres,
            loaded.actors,
            null,
            null,
        )

        return true
    }
}
