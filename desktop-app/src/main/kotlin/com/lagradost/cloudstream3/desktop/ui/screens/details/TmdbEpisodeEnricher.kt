package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvSeriesLoadResponse
import com.lagradost.cloudstream3.newEpisode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object TmdbEpisodeEnricher {

    private val dummyApi = object : MainAPI() {
        override var name = "TMDB"
        override var mainUrl = "https://www.themoviedb.org"
    }

    /**
     * Enriches episode metadata (thumbnails, descriptions, air dates, runtimes, scores)
     * and synthesizes unreleased future episodes from the TMDB season data.
     * Mutates [loaded] on the Main dispatcher.
     */
    suspend fun enrich(
        loaded: LoadResponse,
        tmdbData: JsonNode,
        isMovie: Boolean,
        overwrite: Boolean,
        onEpisodeThumbnailsEnriched: () -> Unit,
    ) {
        if (isMovie) {
            onEpisodeThumbnailsEnriched()
            return
        }

        val allEpisodes = when (loaded) {
            is TvSeriesLoadResponse -> loaded.episodes
            is AnimeLoadResponse -> loaded.episodes.values.flatten()
            else -> emptyList()
        }

        val realEpisodes = allEpisodes.filter { !it.data.startsWith("unreleased_") }
        val newEpisodesToAdd = mutableListOf<com.lagradost.cloudstream3.Episode>()
        val allSeasonNumbers = (realEpisodes.mapNotNull { it.season } + listOf(1)).distinct()

        allSeasonNumbers.forEach { seasonNum ->
            val seasonNode = tmdbData.get("season/$seasonNum")
            if (seasonNode == null || !seasonNode.isObject) return@forEach

            val episodesNode = seasonNode.get("episodes")
            if (episodesNode == null || !episodesNode.isArray) return@forEach

            val existingEpNumbersInSeason = realEpisodes
                .filter { (it.season ?: 1) == seasonNum }
                .mapNotNull { it.episode }
                .toSet()

            episodesNode.forEach { epNode ->
                val epNum = epNode.get("episode_number")?.asInt() ?: return@forEach
                val epPosterPath = epNode.get("still_path")?.asText()
                val epPoster = TmdbEnrichmentService.tmdbImageUrl(epPosterPath, "original")
                val epOverview = epNode.get("overview")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val epReleaseDate = epNode.get("air_date")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val epName = epNode.get("name")?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val epRuntime = epNode.get("runtime")?.asInt()?.takeIf { it > 0 }
                val epVote = epNode.get("vote_average")?.asDouble()?.takeIf { it > 0 }

                if (existingEpNumbersInSeason.contains(epNum)) {
                    val existingEp = realEpisodes.find { (it.season ?: 1) == seasonNum && it.episode == epNum }
                    if (existingEp != null) {
                        // TMDB high-res still is authoritative over provider scraper thumbnails
                        if (epPoster != null) existingEp.posterUrl = epPoster
                        if ((existingEp.description.isNullOrBlank() || overwrite) && epOverview != null) {
                            existingEp.description = epOverview
                        }
                        if (epReleaseDate != null) {
                            val cleanDesc = (existingEp.description ?: "").replace(Regex("\\|\\|DATE:.*?\\|\\|"), "")
                            existingEp.description = "||DATE:$epReleaseDate||$cleanDesc"
                        }
                        if (epName != null) {
                            val currentName = existingEp.name?.trim() ?: ""
                            val isGenericTitle = currentName.isBlank() || currentName.matches(Regex("""^(?i)Episode[\s]*\d+$"""))
                            if (isGenericTitle || overwrite || !epName.matches(Regex("""^(?i)Episode[\s]*\d+$"""))) {
                                existingEp.name = epName
                            }
                        }
                        if (epRuntime != null) existingEp.runTime = epRuntime
                        if (epVote != null && existingEp.score == null) existingEp.score = Score.from10(epVote)
                    }
                } else {
                    // Only synthesize if the episode is strictly in the future
                    val isFuture = epReleaseDate?.let { dateStr ->
                        try {
                            val parsed = java.time.LocalDate.parse(dateStr.take(10))
                            val now = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                            parsed.isAfter(now)
                        } catch (_: Exception) { false }
                    } ?: false

                    if (isFuture) {
                        val descWithDate = "||DATE:$epReleaseDate||${epOverview ?: ""}"
                        val synthetic = dummyApi.newEpisode("unreleased_s${seasonNum}_e${epNum}") {
                            this.name = epName ?: "Episode $epNum"
                            this.season = seasonNum
                            this.episode = epNum
                            this.posterUrl = epPoster
                            this.description = descWithDate
                            this.runTime = epRuntime
                            this.score = epVote?.let { Score.from10(it) }
                        }
                        newEpisodesToAdd.add(synthetic)
                    }
                }
            }
        }

        val distinctNewEpisodes = newEpisodesToAdd.distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }

        if (distinctNewEpisodes.isNotEmpty()) {
            withContext(Dispatchers.Main.immediate) {
                when (loaded) {
                    is TvSeriesLoadResponse -> {
                        val base = loaded.episodes.filter { !it.data.startsWith("unreleased_") }
                        loaded.episodes = (base + distinctNewEpisodes)
                            .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                            .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
                            .toMutableList()
                    }
                    is AnimeLoadResponse -> {
                        val mutableMap = loaded.episodes.toMutableMap()
                        if (mutableMap.isEmpty()) {
                            mutableMap[DubStatus.Subbed] = distinctNewEpisodes
                        } else {
                            mutableMap.keys.forEach { dubKey ->
                                val current = mutableMap[dubKey].orEmpty().filter { !it.data.startsWith("unreleased_") }
                                mutableMap[dubKey] = (current + distinctNewEpisodes)
                                    .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                                    .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))
                            }
                        }
                        loaded.episodes = mutableMap
                    }
                    else -> {}
                }
            }
        }

        onEpisodeThumbnailsEnriched()
    }
}
