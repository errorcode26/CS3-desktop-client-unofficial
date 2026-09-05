package com.lagradost.cloudstream3.desktop.ui.screens.details

import com.fasterxml.jackson.databind.JsonNode
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.utils.StringUtils
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object TmdbMatchResolver {

    data class TmdbMatch(val id: Int, val isMovie: Boolean)

    suspend fun resolve(
        loaded: LoadResponse,
        cleanName: String,
        isAnime: Boolean,
        isTv: Boolean,
        tempYear: Int?,
        directTmdbId: Int?,
        directImdbId: String?,
        apiKey: String,
    ): TmdbMatch? = withContext(Dispatchers.IO) {

        val isExplicitMovie = loaded.type == TvType.Movie || loaded.type == TvType.AnimeMovie
        val isExplicitTv = loaded.type == TvType.TvSeries || loaded.type == TvType.AsianDrama || loaded.type == TvType.Cartoon
        val strippedCleanName = cleanName.replace(Regex("[^a-zA-Z0-9]"), "")

        var resolvedMatchId: Int? = null
        var resolvedIsMovie = isExplicitMovie

        // Fast path 1: IMDb ID → use /find/ with strict type alignment
        if (directImdbId != null) {
            TmdbRateLimiter.acquire()
            val findUrl = "https://api.themoviedb.org/3/find/$directImdbId?api_key=$apiKey&external_source=imdb_id"
            val findData = app.get(findUrl).parsedSafe<JsonNode>()
            val movieRes = findData?.get("movie_results")
            val tvRes = findData?.get("tv_results")

            when {
                isExplicitTv -> {
                    if (tvRes?.isArray == true && tvRes.size() > 0) {
                        resolvedMatchId = tvRes[0].get("id")?.asInt()
                        resolvedIsMovie = false
                        com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → tv id=$resolvedMatchId")
                    }
                }
                isExplicitMovie -> {
                    if (movieRes?.isArray == true && movieRes.size() > 0) {
                        resolvedMatchId = movieRes[0].get("id")?.asInt()
                        resolvedIsMovie = true
                        com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → movie id=$resolvedMatchId")
                    }
                }
                else -> {
                    if (tvRes?.isArray == true && tvRes.size() > 0) {
                        resolvedMatchId = tvRes[0].get("id")?.asInt()
                        resolvedIsMovie = false
                        com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → tv id=$resolvedMatchId")
                    } else if (movieRes?.isArray == true && movieRes.size() > 0) {
                        resolvedMatchId = movieRes[0].get("id")?.asInt()
                        resolvedIsMovie = true
                        com.lagradost.common.logging.AppLogger.i("Enrichment", "  TMDB: IMDb find → movie id=$resolvedMatchId")
                    }
                }
            }
        }

        // Fast path 2: Direct TMDB ID passthrough
        if (resolvedMatchId == null && directTmdbId != null) {
            resolvedMatchId = directTmdbId
            com.lagradost.common.logging.AppLogger.i("Enrichment", "  ✓ TMDB: direct TMDB ID → ${if (resolvedIsMovie) "movie" else "tv"} id=$resolvedMatchId")
        }

        // Text search across progressive root title candidates
        if (resolvedMatchId == null) {
            val searchCandidates = TitleUtils.extractRootTitleCandidates(loaded.name)
            val endpoint = if (isExplicitTv) "tv" else if (isExplicitMovie) "movie" else "multi"

            for (cand in searchCandidates) {
                val q = cand.first
                val searchUrl = "https://api.themoviedb.org/3/search/$endpoint?api_key=$apiKey&query=${java.net.URLEncoder.encode(q, "UTF-8")}&page=1&language=en-US"
                val searchData = app.get(searchUrl).parsedSafe<JsonNode>()
                var matchNode = findMatch(searchData?.get("results"), q, loaded.type, isAnime, isTv, tempYear, strippedCleanName)

                // Pass 2: no language filter for non-English titles
                if (matchNode == null) {
                    TmdbRateLimiter.acquire()
                    val fallbackUrl = "https://api.themoviedb.org/3/search/$endpoint?api_key=$apiKey&query=${java.net.URLEncoder.encode(q, "UTF-8")}&page=1"
                    val fallbackData = app.get(fallbackUrl).parsedSafe<JsonNode>()
                    matchNode = findMatch(fallbackData?.get("results"), q, loaded.type, isAnime, isTv, tempYear, strippedCleanName)
                }

                if (matchNode != null) {
                    resolvedMatchId = matchNode.get("id")?.asInt()
                    val mType = matchNode.get("media_type")?.asText()
                    resolvedIsMovie = if (mType != null) mType == "movie" else !isExplicitTv
                    com.lagradost.common.logging.AppLogger.i("Enrichment", "  ✓ TMDB: text search ('$q') → ${if (resolvedIsMovie) "movie" else "tv"} id=$resolvedMatchId")
                    break
                }
            }

            if (resolvedMatchId == null) {
                com.lagradost.common.logging.AppLogger.w("Enrichment", "  TMDB: text search for '${loaded.name}' found no high-confidence match")
            }
        }

        if (resolvedMatchId != null) TmdbMatch(resolvedMatchId, resolvedIsMovie) else null
    }

    private fun findMatch(
        resultsNode: JsonNode?,
        queryName: String,
        loadedType: TvType,
        isAnime: Boolean,
        isTv: Boolean,
        tempYear: Int?,
        strippedCleanNameOuter: String,
    ): JsonNode? {
        if (resultsNode == null || !resultsNode.isArray) return null

        val isExplicitMovie = loadedType == TvType.Movie || loadedType == TvType.AnimeMovie
        val isExplicitTv = loadedType == TvType.TvSeries || loadedType == TvType.AsianDrama || loadedType == TvType.Cartoon

        val possible = mutableListOf<Pair<JsonNode, Double>>()

        for (result in resultsNode) {
            val mediaType = result.get("media_type")?.asText()
            if (mediaType != null && mediaType != "movie" && mediaType != "tv") continue
            if (isExplicitMovie && mediaType == "tv") continue
            if (isExplicitTv && mediaType == "movie") continue

            val resultName = result.get("name")?.asText()
                ?: result.get("title")?.asText()
                ?: result.get("original_name")?.asText() ?: ""
            val cleanCompare = queryName.lowercase().removePrefix("the ").trim()
            val resultCompare = resultName.lowercase().removePrefix("the ").trim()

            val strippedResultName = resultCompare.replace(Regex("[^a-zA-Z0-9]"), "")
            val strippedCleanName = cleanCompare.replace(Regex("[^a-zA-Z0-9]"), "")

            // Prevent sequel number mismatches (e.g. Iron Man vs Iron Man 2)
            val numbers1 = Regex("""\b\d+\b""").findAll(cleanCompare).map { it.value }.toSet()
            val numbers2 = Regex("""\b\d+\b""").findAll(resultCompare).map { it.value }.toSet()
            val romanRegex = Regex("""\b(ii|iii|iv|v|vi|vii|viii|ix|x)\b""")
            val romans1 = romanRegex.findAll(cleanCompare).map { it.value }.toSet()
            val romans2 = romanRegex.findAll(resultCompare).map { it.value }.toSet()
            if (numbers1 != numbers2 || romans1 != romans2) continue

            // Reject unrelated titles sharing only stop words
            if (!StringUtils.hasContentWordMatch(queryName, resultName, minOverlapRatio = 0.75)) continue

            val isStrictMatch = strippedResultName.equals(strippedCleanName, ignoreCase = true)

            val genreArray = result.get("genre_ids")
            val isAnimation = genreArray?.isArray == true && genreArray.any { it.asInt() == 16 }
            val originArray = result.get("origin_country")
            val isEastAsian = originArray?.isArray == true && originArray.any { it.asText() in setOf("JP", "CN", "KR") }

            // Hard domain guard: never let live-action hijack anime
            if (isAnime && !isAnimation && !isEastAsian) continue

            val releaseDate = result.get("release_date")?.asText() ?: result.get("first_air_date")?.asText()
            val resultYear = releaseDate?.split("-")?.firstOrNull()?.toIntOrNull()

            if (resultYear != null && tempYear != null) {
                if (isTv) {
                    if (resultYear > tempYear + 1) continue
                } else {
                    if (Math.abs(resultYear - tempYear) > 1) continue
                }
            }

            var similarity = StringUtils.similarity(strippedCleanName, strippedResultName)
            if (isStrictMatch) similarity = 1.0
            if (similarity < 0.80) continue

            var score = similarity * 10.0
            if (isAnime && isAnimation) score += 20.0
            if (resultYear != null && tempYear != null) {
                if (resultYear == tempYear) score += 5.0
                else if (isTv && resultYear <= tempYear) score += 2.0
            }

            val popularity = result.get("popularity")?.asDouble() ?: 0.0
            score += Math.min(8.0, popularity / 15.0)

            val voteCount = result.get("vote_count")?.asInt() ?: 0
            score += when {
                voteCount > 500 -> 6.0
                voteCount > 50 -> 3.0
                voteCount < 3 && !isAnime -> -12.0
                else -> 0.0
            }

            if (score >= 9.0) possible.add(Pair(result, score))
        }

        return if (possible.isEmpty()) null else possible.sortedByDescending { it.second }.first().first
    }
}
