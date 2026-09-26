package com.lagradost.cloudstream3.desktop.explore.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbEnrichmentService
import com.lagradost.cloudstream3.desktop.ui.screens.details.TmdbRateLimiter
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

data class FranchiseEntry(
    val id: Int,
    val name: String,
    val categories: List<String>,
)

data class ExploreCollectionDetail(
    val id: Int,
    val name: String,
    val overview: String,
    val backdropUrl: String?,
    val posterUrl: String?,
    val parts: List<ExploreItem>,
)

object ExploreCollectionsHub {
    private const val TAG = "ExploreCollectionsHub"
    private val mapper = jacksonObjectMapper()
    private val collectionDetailCache = ConcurrentHashMap<Int, ExploreCollectionDetail>()

    val CATEGORIES = listOf(
        "Sagas",
        "Superheroes",
        "Action",
        "Sci-Fi",
        "Animation",
        "Horror",
        "Crime",
    )

    val FRANCHISES: List<FranchiseEntry> = listOf(
        // Sagas & Epics
        FranchiseEntry(10, "Star Wars", listOf("Sagas", "Sci-Fi")),
        FranchiseEntry(1241, "Harry Potter", listOf("Sagas", "Fantasy")),
        FranchiseEntry(119, "The Lord of the Rings", listOf("Sagas", "Fantasy")),
        FranchiseEntry(121938, "The Hobbit", listOf("Sagas", "Fantasy")),
        FranchiseEntry(87096, "Avatar", listOf("Sagas", "Sci-Fi")),
        FranchiseEntry(131635, "The Hunger Games", listOf("Sagas", "Sci-Fi")),
        FranchiseEntry(726871, "Dune", listOf("Sagas", "Sci-Fi")),
        FranchiseEntry(295, "Pirates of the Caribbean", listOf("Sagas", "Action")),
        FranchiseEntry(33514, "The Twilight Saga", listOf("Sagas")),

        // Superheroes
        FranchiseEntry(263, "The Dark Knight Trilogy", listOf("Superheroes", "Crime")),
        FranchiseEntry(86311, "The Avengers", listOf("Superheroes", "Action")),
        FranchiseEntry(556, "Spider-Man", listOf("Superheroes")),
        FranchiseEntry(284433, "Guardians of the Galaxy", listOf("Superheroes", "Sci-Fi")),
        FranchiseEntry(448150, "Deadpool", listOf("Superheroes", "Action")),
        FranchiseEntry(748, "X-Men", listOf("Superheroes", "Sci-Fi")),
        FranchiseEntry(131292, "Iron Man", listOf("Superheroes")),
        FranchiseEntry(131295, "Thor", listOf("Superheroes", "Fantasy")),
        FranchiseEntry(131296, "Captain America", listOf("Superheroes", "Action")),

        // Action & Thrillers
        FranchiseEntry(404609, "John Wick", listOf("Action")),
        FranchiseEntry(9485, "The Fast and the Furious", listOf("Action")),
        FranchiseEntry(87359, "Mission: Impossible", listOf("Action")),
        FranchiseEntry(645, "James Bond 007", listOf("Action")),
        FranchiseEntry(2344, "The Matrix", listOf("Action", "Sci-Fi")),
        FranchiseEntry(528, "The Terminator", listOf("Action", "Sci-Fi")),
        FranchiseEntry(84, "Indiana Jones", listOf("Action", "Adventure")),
        FranchiseEntry(8945, "Mad Max", listOf("Action", "Sci-Fi")),
        FranchiseEntry(1570, "Die Hard", listOf("Action")),
        FranchiseEntry(31562, "The Bourne Series", listOf("Action")),
        FranchiseEntry(126125, "The Expendables", listOf("Action")),
        FranchiseEntry(134004, "Taken", listOf("Action")),
        FranchiseEntry(9742, "Bad Boys", listOf("Action")),
        FranchiseEntry(9435, "Lethal Weapon", listOf("Action", "Crime")),
        FranchiseEntry(481879, "Creed", listOf("Action")),
        FranchiseEntry(1575, "Rocky", listOf("Action")),

        // Sci-Fi Universes
        FranchiseEntry(328, "Jurassic Park", listOf("Sci-Fi")),
        FranchiseEntry(8091, "Alien", listOf("Sci-Fi", "Horror")),
        FranchiseEntry(399, "Predator", listOf("Sci-Fi", "Action")),
        FranchiseEntry(264, "Back to the Future", listOf("Sci-Fi")),
        FranchiseEntry(173710, "Planet of the Apes", listOf("Sci-Fi")),
        FranchiseEntry(8650, "Transformers", listOf("Sci-Fi", "Action")),
        FranchiseEntry(86055, "Men in Black", listOf("Sci-Fi")),
        FranchiseEntry(2980, "Ghostbusters", listOf("Sci-Fi")),
        FranchiseEntry(535313, "Godzilla & Monsterverse", listOf("Sci-Fi", "Action")),

        // Animation & Family
        FranchiseEntry(10194, "Toy Story", listOf("Animation")),
        FranchiseEntry(2150, "Shrek", listOf("Animation")),
        FranchiseEntry(89137, "How to Train Your Dragon", listOf("Animation")),
        FranchiseEntry(77816, "Kung Fu Panda", listOf("Animation")),
        FranchiseEntry(86066, "Despicable Me & Minions", listOf("Animation")),
        FranchiseEntry(8354, "Ice Age", listOf("Animation")),
        FranchiseEntry(14890, "Madagascar", listOf("Animation")),
        FranchiseEntry(468222, "The Incredibles", listOf("Animation", "Superheroes")),
        FranchiseEntry(137697, "Finding Nemo", listOf("Animation")),
        FranchiseEntry(137696, "Monsters, Inc.", listOf("Animation")),
        FranchiseEntry(87118, "Cars", listOf("Animation")),
        FranchiseEntry(386382, "Frozen", listOf("Animation")),
        FranchiseEntry(720879, "Sonic the Hedgehog", listOf("Animation")),
        FranchiseEntry(146443, "Hotel Transylvania", listOf("Animation")),

        // Horror Universes
        FranchiseEntry(313086, "The Conjuring Universe", listOf("Horror")),
        FranchiseEntry(656, "Saw", listOf("Horror")),
        FranchiseEntry(2602, "Scream", listOf("Horror")),
        FranchiseEntry(91361, "Halloween", listOf("Horror")),
        FranchiseEntry(8581, "A Nightmare on Elm Street", listOf("Horror")),
        FranchiseEntry(17255, "Resident Evil", listOf("Horror", "Action")),
        FranchiseEntry(228446, "Insidious", listOf("Horror")),
        FranchiseEntry(258814, "The Purge", listOf("Horror")),
        FranchiseEntry(13264, "Final Destination", listOf("Horror")),
        FranchiseEntry(726872, "A Quiet Place", listOf("Horror", "Sci-Fi")),

        // Crime & Mob Classics
        FranchiseEntry(230, "The Godfather", listOf("Crime")),
        FranchiseEntry(304, "Ocean's", listOf("Crime")),
        FranchiseEntry(2883, "Kill Bill", listOf("Crime", "Action")),
        FranchiseEntry(138101, "Sherlock Holmes", listOf("Crime")),
        FranchiseEntry(687834, "Knives Out", listOf("Crime")),
        FranchiseEntry(391807, "Now You See Me", listOf("Crime")),
    )

    suspend fun fetchCollectionDetails(collectionId: Int): ExploreCollectionDetail? {
        val cached = collectionDetailCache[collectionId]
        if (cached != null) return cached

        return try {
            TmdbRateLimiter.acquire()
            val apiKey = TmdbEnrichmentService.TMDB_API_KEY
            val url = "https://api.themoviedb.org/3/collection/$collectionId?api_key=$apiKey&language=en-US"
            val response = app.get(url, timeout = 10_000L, cacheTime = 60 * 24)
            val root = mapper.readTree(response.text)
            val name = root["name"]?.asText() ?: return null
            val overview = root["overview"]?.asText().orEmpty()
            val posterPath = root["poster_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
            val backdropPath = root["backdrop_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
            val partsArray = root["parts"]

            val parts = mutableListOf<ExploreItem>()
            if (partsArray != null && partsArray.isArray) {
                for (partNode in partsArray) {
                    val pId = partNode["id"]?.asInt() ?: continue
                    val title = partNode["title"]?.asText()?.takeIf { it.isNotBlank() } ?: continue
                    val pPoster = partNode["poster_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                    val pBackdrop = partNode["backdrop_path"]?.asText()?.takeIf { it.isNotBlank() && it != "null" }
                    val releaseDate = partNode["release_date"]?.asText()?.takeIf { it.isNotBlank() }
                    val year = releaseDate?.take(4)
                    val rating = partNode["vote_average"]?.asDouble()
                    val pOverview = partNode["overview"]?.asText()

                    parts.add(
                        ExploreItem(
                            id = "tmdb:$pId",
                            type = "movie",
                            name = title,
                            posterUrl = pPoster?.let { "https://image.tmdb.org/t/p/w500$it" },
                            backgroundUrl = pBackdrop?.let { "https://image.tmdb.org/t/p/original$it" },
                            releaseYear = year,
                            description = pOverview,
                            rating = rating,
                        )
                    )
                }
            }

            val sortedParts = parts.sortedBy { it.releaseYear ?: "9999" }

            val detail = ExploreCollectionDetail(
                id = collectionId,
                name = name,
                overview = overview,
                backdropUrl = backdropPath?.let { "https://image.tmdb.org/t/p/original$it" },
                posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w500$it" },
                parts = sortedParts,
            )
            collectionDetailCache[collectionId] = detail
            detail
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed fetching collection $collectionId: ${e.message}")
            null
        }
    }

    suspend fun fetchCategoryShelf(category: String, page: Int = 1, pageSize: Int = 12): List<ExploreItem> {
        val matches = if (category.equals("All", ignoreCase = true)) {
            FRANCHISES
        } else {
            FRANCHISES.filter { it.categories.any { c -> c.equals(category, ignoreCase = true) } }
        }

        val startIndex = (page - 1) * pageSize
        if (startIndex >= matches.size) return emptyList()
        val pageItems = matches.drop(startIndex).take(pageSize)

        return coroutineScope {
            pageItems.map { franchise ->
                async {
                    val detail = fetchCollectionDetails(franchise.id)
                    val partsCount = detail?.parts?.size ?: 0
                    val countLabel = if (partsCount > 0) "$partsCount Movies" else "Franchise"
                    val yearRange = detail?.parts?.let { parts ->
                        val first = parts.firstOrNull()?.releaseYear
                        val last = parts.lastOrNull()?.releaseYear
                        if (first != null && last != null && first != last) "$first–$last • $countLabel" else countLabel
                    } ?: countLabel

                    ExploreItem(
                        id = "collection:${franchise.id}",
                        type = "collection",
                        name = franchise.name,
                        posterUrl = detail?.backdropUrl ?: detail?.posterUrl,
                        backgroundUrl = detail?.backdropUrl,
                        posterShape = "landscape",
                        releaseYear = yearRange,
                        description = detail?.overview,
                    )
                }
            }.awaitAll()
        }
    }
}
