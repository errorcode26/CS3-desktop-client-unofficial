package com.lagradost.cloudstream3.desktop.explore.search

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.client.ExploreCatalogDiscoverer
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbRateLimiter
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.Locale

data class AddonSearchResultGroup(
    val addonName: String,
    val items: List<ExploreItem>,
)

data class ExploreSearchResults(
    val query: String,
    val topMatch: ExploreItem? = null,
    val movies: List<ExploreItem> = emptyList(),
    val series: List<ExploreItem> = emptyList(),
    val anime: List<ExploreItem> = emptyList(),
    val addonGroups: List<AddonSearchResultGroup> = emptyList(),
)

object ExploreSearchEngine {
    private const val TAG = "ExploreSearchEngine"
    private val mapper = jacksonObjectMapper()

    private val TMDB_GENRES = mapOf(
        28 to "Action",
        12 to "Adventure",
        16 to "Animation",
        35 to "Comedy",
        80 to "Crime",
        99 to "Documentary",
        18 to "Drama",
        10751 to "Family",
        14 to "Fantasy",
        36 to "History",
        27 to "Horror",
        10402 to "Music",
        9648 to "Mystery",
        10749 to "Romance",
        878 to "Sci-Fi",
        10770 to "TV Movie",
        53 to "Thriller",
        10752 to "War",
        37 to "Western",
        10759 to "Action & Adventure",
        10762 to "Kids",
        10765 to "Sci-Fi & Fantasy",
    )

    fun searchFlow(
        query: String,
        enabledAddons: List<ManagedStremioAddon>,
        limitPerCategory: Int = 24,
    ): Flow<ExploreSearchResults> = flow {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            emit(ExploreSearchResults(query = trimmed))
            return@flow
        }

        // Phase 1: Fast edge-CDN queries (Cinemeta ~80ms)
        val (cinemetaMovies, cinemetaSeries) = coroutineScope {
            val moviesDeferred = async { searchDirectCinemeta("movie", trimmed, limitPerCategory) }
            val seriesDeferred = async { searchDirectCinemeta("series", trimmed, limitPerCategory) }
            moviesDeferred.await() to seriesDeferred.await()
        }

        val initialAll = (cinemetaMovies + cinemetaSeries)
        val initialExact = initialAll.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
        val initialTop = initialExact ?: initialAll.maxByOrNull { it.rating ?: 0.0 } ?: initialAll.firstOrNull()

        if (cinemetaMovies.isNotEmpty() || cinemetaSeries.isNotEmpty()) {
            emit(
                ExploreSearchResults(
                    query = trimmed,
                    topMatch = initialTop,
                    movies = cinemetaMovies,
                    series = cinemetaSeries,
                    anime = emptyList(),
                    addonGroups = emptyList(),
                )
            )
        }

        // Phase 2: Query non-Cinemeta addons and AniList concurrently
        coroutineScope {
            val nonCinemetaAddons = enabledAddons.filter { !it.manifestUrl.contains("cinemeta", ignoreCase = true) }
            val addonsDeferred = async { searchAddons(trimmed, nonCinemetaAddons, limitPerCategory) }
            val anilistDeferred = async { searchAnilist(trimmed, limitPerCategory) }

            // Only query TMDB as a fallback if Cinemeta returned ZERO movies and series
            val tmdbDeferred = if (cinemetaMovies.isEmpty() && cinemetaSeries.isEmpty()) {
                async { searchTmdb(trimmed) }
            } else {
                null
            }

            val addonGroups = addonsDeferred.await()
            val anilistResults = anilistDeferred.await()
            val tmdbResults = tmdbDeferred?.await() ?: emptyList()

            val addonMovies = addonGroups.flatMap { it.items }.filter { it.type.equals("movie", ignoreCase = true) }
            val tmdbMovies = tmdbResults.filter { it.type.equals("movie", ignoreCase = true) }
            val allMovies = (cinemetaMovies + addonMovies + tmdbMovies)
                .distinctBy { "${it.name.lowercase(Locale.US)}_${it.releaseYear}" }
                .take(limitPerCategory)

            val addonSeries = addonGroups.flatMap { it.items }.filter { it.type.equals("series", ignoreCase = true) || it.type.equals("tv", ignoreCase = true) }
            val tmdbSeries = tmdbResults.filter { it.type.equals("series", ignoreCase = true) || it.type.equals("tv", ignoreCase = true) }
            val allSeries = (cinemetaSeries + addonSeries + tmdbSeries)
                .distinctBy { "${it.name.lowercase(Locale.US)}_${it.releaseYear}" }
                .take(limitPerCategory)

            val addonAnime = addonGroups.flatMap { it.items }.filter { it.type.equals("anime", ignoreCase = true) }
            val allAnime = (anilistResults + addonAnime)
                .distinctBy { "${it.name.lowercase(Locale.US)}_${it.releaseYear}" }
                .take(limitPerCategory)

            val finalAddonGroups = addonGroups.filter { !it.addonName.equals("Cinemeta", ignoreCase = true) && it.items.isNotEmpty() }

            val allCandidates = (allMovies + allSeries + allAnime).filter { it.posterUrl != null }
            val exactMatch = allCandidates.firstOrNull { it.name.equals(trimmed, ignoreCase = true) }
            val topMatch = exactMatch ?: allCandidates.maxByOrNull { (it.rating ?: 0.0) } ?: allCandidates.firstOrNull()

            emit(
                ExploreSearchResults(
                    query = trimmed,
                    topMatch = topMatch,
                    movies = allMovies,
                    series = allSeries,
                    anime = allAnime,
                    addonGroups = finalAddonGroups,
                )
            )
        }
    }

    suspend fun search(
        query: String,
        enabledAddons: List<ManagedStremioAddon>,
        limitPerCategory: Int = 24,
    ): ExploreSearchResults = withContext(Dispatchers.IO) {
        var last: ExploreSearchResults = ExploreSearchResults(query = query.trim())
        searchFlow(query, enabledAddons, limitPerCategory).collect { last = it }
        last
    }

    private suspend fun searchTmdb(query: String): List<ExploreItem> {
        return try {
            TmdbRateLimiter.acquire()
            val apiKey = TmdbEnrichmentService.TMDB_API_KEY
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://api.themoviedb.org/3/search/multi?api_key=$apiKey&query=$encodedQuery&include_adult=false&language=en-US"

            val response = app.get(url, timeout = 8000L)
            val root = mapper.readTree(response.text)
            val results = root["results"] ?: return emptyList()
            if (!results.isArray) return emptyList()

            val items = mutableListOf<ExploreItem>()
            for (node in results) {
                val mediaType = node["media_type"]?.asText() ?: continue
                if (mediaType != "movie" && mediaType != "tv") continue

                val tmdbId = node["id"]?.asInt() ?: continue
                val title = (node["title"]?.asText() ?: node["name"]?.asText())?.takeIf { it.isNotBlank() } ?: continue
                val posterPath = node["poster_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val backdropPath = node["backdrop_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                val releaseDate = (node["release_date"]?.asText() ?: node["first_air_date"]?.asText())?.takeIf { it.isNotBlank() }
                val year = releaseDate?.take(4)
                val rating = node["vote_average"]?.asDouble()
                val overview = node["overview"]?.asText()?.takeIf { it.isNotBlank() }

                val genreIds = node["genre_ids"]?.mapNotNull { it.asInt() } ?: emptyList()
                val genreNames = genreIds.mapNotNull { TMDB_GENRES[it] }

                val posterUrl = TmdbEnrichmentService.tmdbImageUrl(posterPath, "w500")
                val backdropUrl = TmdbEnrichmentService.tmdbImageUrl(backdropPath, "original")

                items.add(
                    ExploreItem(
                        id = "tmdb:$tmdbId",
                        type = if (mediaType == "tv") "series" else "movie",
                        name = title,
                        posterUrl = posterUrl,
                        backgroundUrl = backdropUrl,
                        releaseYear = year,
                        description = overview,
                        rating = rating?.takeIf { it > 0.0 },
                        genres = genreNames,
                    )
                )
            }
            items
        } catch (e: Exception) {
            AppLogger.w(TAG, "searchTmdb failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchAnilist(query: String, limit: Int): List<ExploreItem> {
        val graphql = """
            query (${'$'}search: String, ${'$'}perPage: Int) {
                Page (page: 1, perPage: ${'$'}perPage) {
                    media (search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                        id
                        idMal
                        title {
                            userPreferred
                            english
                            romaji
                        }
                        coverImage {
                            extraLarge
                            large
                        }
                        bannerImage
                        averageScore
                        seasonYear
                        genres
                        description(asHtml: false)
                        format
                    }
                }
            }
        """.trimIndent()

        val jsonMap = mapOf(
            "query" to graphql,
            "variables" to mapOf(
                "search" to query,
                "perPage" to limit,
            )
        )
        val payload = mapper.writeValueAsString(jsonMap).toRequestBody("application/json".toMediaTypeOrNull())

        return try {
            val response = app.post(
                "https://graphql.anilist.co",
                requestBody = payload,
                timeout = 8000L,
            )
            val root = mapper.readTree(response.text)
            val mediaArray = root["data"]?.get("Page")?.get("media") ?: return emptyList()
            if (!mediaArray.isArray) return emptyList()

            val items = mutableListOf<ExploreItem>()
            for (node in mediaArray) {
                val anilistId = node["id"]?.asInt() ?: continue
                val idMal = node["idMal"]?.asInt()
                val titleNode = node["title"]
                val title = (titleNode?.get("userPreferred")?.asText()
                    ?: titleNode?.get("english")?.asText()
                    ?: titleNode?.get("romaji")?.asText())?.takeIf { it.isNotBlank() } ?: continue

                val coverNode = node["coverImage"]
                val posterUrl = (coverNode?.get("extraLarge")?.asText() ?: coverNode?.get("large")?.asText())?.takeIf { it.isNotBlank() }
                val bannerUrl = node["bannerImage"]?.asText()?.takeIf { it.isNotBlank() }
                val score = node["averageScore"]?.asDouble()?.let { it / 10.0 }
                val year = node["seasonYear"]?.asText()?.takeIf { it.isNotBlank() }
                val desc = node["description"]?.asText()?.takeIf { it.isNotBlank() }
                val genres = node["genres"]?.mapNotNull { it.asText() } ?: emptyList()
                val format = node["format"]?.asText()?.lowercase() ?: "anime"

                items.add(
                    ExploreItem(
                        id = if (idMal != null) "mal:$idMal" else "anilist:$anilistId",
                        type = if (format == "movie") "movie" else "series",
                        name = title,
                        posterUrl = posterUrl,
                        backgroundUrl = bannerUrl,
                        releaseYear = year,
                        description = desc,
                        rating = score,
                        genres = genres,
                    )
                )
            }
            items
        } catch (e: Exception) {
            AppLogger.w(TAG, "searchAnilist failed: ${e.message}")
            emptyList()
        }
    }

    private suspend fun searchAddons(
        query: String,
        enabledAddons: List<ManagedStremioAddon>,
        limitPerCatalog: Int,
    ): List<AddonSearchResultGroup> = coroutineScope {
        val effectiveAddons = if (enabledAddons.none { it.manifestUrl.contains("cinemeta", ignoreCase = true) }) {
            listOf(
                ManagedStremioAddon(
                    manifestUrl = "https://v3-cinemeta.strem.io/manifest.json",
                    name = "Cinemeta",
                    description = "The official movie and TV show catalog for Stremio",
                    version = "3.0.12",
                    enabled = true,
                )
            ) + enabledAddons
        } else {
            enabledAddons
        }

        val groups = effectiveAddons.map { addon ->
            async {
                try {
                    val catalogs = ExploreCatalogDiscoverer.getCatalogsForAddon(addon)
                    val searchCatalogs = catalogs.filter { it.supportsSearch }
                    if (searchCatalogs.isEmpty()) return@async null

                    val catalogItems = searchCatalogs.map { cat ->
                        async {
                            searchSingleAddonCatalog(cat, query, limitPerCatalog)
                        }
                    }.awaitAll().flatten().distinctBy { it.id }

                    if (catalogItems.isNotEmpty()) {
                        AddonSearchResultGroup(
                            addonName = addon.name,
                            items = catalogItems,
                        )
                    } else null
                } catch (e: Exception) {
                    AppLogger.w(TAG, "Addon search failed for ${addon.name}: ${e.message}")
                    null
                }
            }
        }.awaitAll().filterNotNull()

        groups
    }

    private suspend fun searchSingleAddonCatalog(
        catalog: ManifestCatalogDescriptor,
        query: String,
        limit: Int,
    ): List<ExploreItem> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val cleanBase = catalog.addonBaseUrl.trimEnd('/')
            val url = "$cleanBase/catalog/${catalog.type}/${catalog.id}/search=$encodedQuery.json"

            val response = app.get(url, timeout = 5000L)
            val root = mapper.readTree(response.text)
            val metas = root["metas"] ?: return emptyList()
            if (!metas.isArray) return emptyList()

            val items = mutableListOf<ExploreItem>()
            for (node in metas) {
                val id = node["id"]?.asText() ?: continue
                val itemType = node["type"]?.asText() ?: catalog.type
                val name = node["name"]?.asText() ?: continue
                val poster = node["poster"]?.asText()?.takeIf { it.isNotBlank() }
                val background = node["background"]?.asText()?.takeIf { it.isNotBlank() }
                val logo = node["logo"]?.asText()?.takeIf { it.isNotBlank() }
                val releaseInfo = (node["releaseInfo"]?.asText() ?: node["year"]?.asText())?.takeIf { it.isNotBlank() }
                val description = node["description"]?.asText()?.takeIf { it.isNotBlank() }
                val scoreStr = node["imdbRating"]?.asText()
                val rating = scoreStr?.toDoubleOrNull() ?: node["imdbRating"]?.asDouble()
                val posterShape = node["posterShape"]?.asText()?.takeIf { it.isNotBlank() }

                val genresList = mutableListOf<String>()
                val genreNode = node["genres"] ?: node["genre"]
                if (genreNode != null && genreNode.isArray) {
                    for (g in genreNode) genresList.add(g.asText())
                }

                items.add(
                    ExploreItem(
                        id = id,
                        type = itemType,
                        name = name,
                        posterUrl = poster,
                        backgroundUrl = background,
                        logoUrl = logo,
                        releaseYear = releaseInfo,
                        description = description,
                        rating = rating,
                        genres = genresList,
                        posterShape = posterShape,
                    )
                )
                if (items.size >= limit) break
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchDirectCinemeta(
        type: String,
        query: String,
        limit: Int,
    ): List<ExploreItem> {
        return try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8").replace("+", "%20")
            val url = "https://v3-cinemeta.strem.io/catalog/$type/top/search=$encodedQuery.json"
            val response = app.get(url, timeout = 4500L)
            val root = mapper.readTree(response.text)
            val metas = root["metas"] ?: return emptyList()
            if (!metas.isArray) return emptyList()

            val items = mutableListOf<ExploreItem>()
            for (node in metas) {
                val id = node["id"]?.asText() ?: continue
                val itemType = node["type"]?.asText() ?: type
                val name = node["name"]?.asText()?.takeIf { it.isNotBlank() } ?: continue
                val poster = node["poster"]?.asText()?.takeIf { it.isNotBlank() }
                val background = node["background"]?.asText()?.takeIf { it.isNotBlank() }
                val logo = node["logo"]?.asText()?.takeIf { it.isNotBlank() }
                val releaseInfo = (node["releaseInfo"]?.asText() ?: node["year"]?.asText())?.takeIf { it.isNotBlank() }
                val description = node["description"]?.asText()?.takeIf { it.isNotBlank() }
                val scoreStr = node["imdbRating"]?.asText()
                val rating = scoreStr?.toDoubleOrNull() ?: node["imdbRating"]?.asDouble()
                val posterShape = node["posterShape"]?.asText()?.takeIf { it.isNotBlank() }

                val genresList = mutableListOf<String>()
                val genreNode = node["genres"] ?: node["genre"]
                if (genreNode != null && genreNode.isArray) {
                    for (g in genreNode) genresList.add(g.asText())
                }

                items.add(
                    ExploreItem(
                        id = id,
                        type = itemType,
                        name = name,
                        posterUrl = poster,
                        backgroundUrl = background,
                        logoUrl = logo,
                        releaseYear = releaseInfo,
                        description = description,
                        rating = rating,
                        genres = genresList,
                        posterShape = posterShape,
                    )
                )
                if (items.size >= limit) break
            }
            items
        } catch (_: Exception) {
            emptyList()
        }
    }
}
