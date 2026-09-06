package com.lagradost.cloudstream3.desktop.metadata.providers

import com.lagradost.cloudstream3.AnimeLoadResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.ActorData
import com.lagradost.cloudstream3.ActorRole
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentCallbacks
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentContext
import com.lagradost.cloudstream3.desktop.metadata.MetadataMatch
import com.lagradost.cloudstream3.desktop.metadata.MetadataProvider
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.TrailerData
import com.lagradost.cloudstream3.desktop.utils.StringUtils
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.common.logging.AppLogger
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AniListMetadataProvider : MetadataProvider {
    private const val TAG = "AniListProvider"
    private const val ANILIST_GRAPHQL_URL = "https://graphql.anilist.co"

    private val dummyApi = object : com.lagradost.cloudstream3.MainAPI() {
        override var name = "AniList"
        override var mainUrl = "https://anilist.co"
    }

    override val id: String = "anilist"
    override val displayName: String = "AniList"
    override val priority: Int = 1
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )

    private data class GraphQlRequest(
        @JsonProperty("query") val query: String,
        @JsonProperty("variables") val variables: Map<String, Any?>? = null,
    )

    private data class SearchResponse(
        @JsonProperty("data") val data: SearchData?,
    )

    private data class SearchData(
        @JsonProperty("Page") val page: SearchPage?,
    )

    private data class SearchPage(
        @JsonProperty("media") val media: List<AniListMedia>?,
    )

    private data class MediaDetailsResponse(
        @JsonProperty("data") val data: MediaDetailsData?,
    )

    private data class MediaDetailsData(
        @JsonProperty("Media") val media: AniListMedia?,
    )

    private data class AniListMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("title") val title: AniListTitle?,
        @JsonProperty("bannerImage") val bannerImage: String?,
        @JsonProperty("coverImage") val coverImage: AniListCoverImage?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("averageScore") val averageScore: Int?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("seasonYear") val seasonYear: Int?,
        @JsonProperty("format") val format: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("studios") val studios: AniListStudios?,
        @JsonProperty("trailer") val trailer: AniListTrailer?,
        @JsonProperty("characters") val characters: AniListCharacters?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: AniListNextAiringEpisode?,
    )

    private data class AniListNextAiringEpisode(
        @JsonProperty("airingAt") val airingAt: Long?,
        @JsonProperty("timeUntilAiring") val timeUntilAiring: Long?,
        @JsonProperty("episode") val episode: Int?,
    )

    private data class AniListTitle(
        @JsonProperty("romaji") val romaji: String?,
        @JsonProperty("english") val english: String?,
        @JsonProperty("native") val native: String?,
        @JsonProperty("userPreferred") val userPreferred: String?,
    )

    private data class AniListCoverImage(
        @JsonProperty("extraLarge") val extraLarge: String?,
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    private data class AniListStudios(
        @JsonProperty("nodes") val nodes: List<AniListStudioNode>?,
    )

    private data class AniListStudioNode(
        @JsonProperty("name") val name: String?,
        @JsonProperty("isAnimationStudio") val isAnimationStudio: Boolean?,
    )

    private data class AniListTrailer(
        @JsonProperty("id") val id: String?,
        @JsonProperty("site") val site: String?,
        @JsonProperty("thumbnail") val thumbnail: String?,
    )

    private data class AniListCharacters(
        @JsonProperty("edges") val edges: List<AniListCharacterEdge>?,
    )

    private data class AniListCharacterEdge(
        @JsonProperty("role") val role: String?,
        @JsonProperty("node") val node: AniListCharacterNode?,
        @JsonProperty("voiceActors") val voiceActors: List<AniListVoiceActorNode>?,
    )

    private data class AniListCharacterNode(
        @JsonProperty("name") val name: AniListName?,
        @JsonProperty("image") val image: AniListImage?,
    )

    private data class AniListVoiceActorNode(
        @JsonProperty("name") val name: AniListName?,
        @JsonProperty("image") val image: AniListImage?,
        @JsonProperty("languageV2") val languageV2: String?,
    )

    private data class AniListName(
        @JsonProperty("full") val full: String?,
        @JsonProperty("userPreferred") val userPreferred: String?,
        @JsonProperty("native") val native: String?,
    )

    private data class AniListImage(
        @JsonProperty("large") val large: String?,
        @JsonProperty("medium") val medium: String?,
    )

    override suspend fun resolve(
        title: String,
        year: Int?,
        type: TvType,
        rawUrl: String?,
    ): MetadataMatch? {
        val (cleanName, titleYear) = TitleUtils.cleanProviderTitle(title)
        val targetYear = year ?: titleYear

        val query = """
            query (${'$'}search: String) {
                Page(page: 1, perPage: 10) {
                    media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                        id
                        idMal
                        title {
                            romaji
                            english
                            native
                            userPreferred
                        }
                        bannerImage
                        coverImage {
                            extraLarge
                            large
                            medium
                        }
                        description(asHtml: false)
                        averageScore
                        seasonYear
                        genres
                    }
                }
            }
        """.trimIndent()

        return try {
            val jsonMap = mapOf(
                "query" to query,
                "variables" to mapOf("search" to cleanName),
            )
            val jsonPayload = jsonMap.toJson()

            val response = withContext(Dispatchers.IO) {
                app.post(
                    url = ANILIST_GRAPHQL_URL,
                    requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Accept" to "application/json",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    ),
                    timeout = 8000L,
                ).text
            }

            val parsed = tryParseJson<SearchResponse>(response)
            val mediaList = parsed?.data?.page?.media ?: return null

            var bestMatch: AniListMedia? = null
            var bestScore = 0.0

            for (media in mediaList) {
                val candidateTitles = listOfNotNull(
                    media.title?.english,
                    media.title?.romaji,
                    media.title?.userPreferred,
                )

                for (candTitle in candidateTitles) {
                    val cleanCand = candTitle.lowercase().removePrefix("the ").trim()
                    val cleanQuery = cleanName.lowercase().removePrefix("the ").trim()

                    val strippedCand = cleanCand.replace(Regex("[^a-zA-Z0-9]"), "")
                    val strippedQuery = cleanQuery.replace(Regex("[^a-zA-Z0-9]"), "")

                    val isStrictMatch = strippedCand.equals(strippedQuery, ignoreCase = true)
                    var score = StringUtils.similarity(strippedQuery, strippedCand)
                    if (isStrictMatch) score = 1.0

                    // Year validation if present
                    if (targetYear != null && media.seasonYear != null) {
                        if (Math.abs(media.seasonYear - targetYear) > 1) {
                            continue
                        }
                    }

                    if (score > bestScore && score >= 0.75) {
                        bestScore = score
                        bestMatch = media
                        if (isStrictMatch) break
                    }
                }
                if (bestScore >= 0.95) break
            }

            val match = bestMatch ?: return null
            AppLogger.i(TAG, "✓ AniList Match: '${match.title?.userPreferred}' (ID: ${match.id}) score=$bestScore")

            MetadataMatch(
                providerId = id,
                matchedTitle = match.title?.english ?: match.title?.romaji ?: match.title?.userPreferred ?: cleanName,
                matchedYear = match.seasonYear ?: targetYear,
                anilistId = match.id,
                posterUrl = match.coverImage?.extraLarge ?: match.coverImage?.large,
                backdropUrl = match.bannerImage,
                description = match.description,
                genres = match.genres,
                rating = match.averageScore?.let { it / 10.0 },
                rawData = match,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "AniList resolve failed for '$cleanName'", e)
            null
        }
    }

    override suspend fun enrich(
        loaded: LoadResponse,
        match: MetadataMatch?,
        context: MetadataEnrichmentContext,
        callbacks: MetadataEnrichmentCallbacks,
    ): Boolean {
        val anilistId = match?.anilistId
            ?: resolve(loaded.name, loaded.year, loaded.type, context.rawUrl)?.anilistId
            ?: return false

        val query = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id, type: ANIME) {
                    id
                    idMal
                    title {
                        romaji
                        english
                        native
                        userPreferred
                    }
                    bannerImage
                    coverImage {
                        extraLarge
                        large
                    }
                    description(asHtml: false)
                    averageScore
                    season
                    seasonYear
                    status
                    genres
                    studios(isMain: true) {
                        nodes {
                            name
                            isAnimationStudio
                        }
                    }
                    nextAiringEpisode {
                        airingAt
                        timeUntilAiring
                        episode
                    }
                    trailer {
                        id
                        site
                        thumbnail
                    }
                    characters(sort: ROLE, page: 1, perPage: 25) {
                        edges {
                            role
                            node {
                                name {
                                    userPreferred
                                    full
                                    native
                                }
                                image {
                                    large
                                }
                            }
                            voiceActors(language: JAPANESE) {
                                name {
                                    userPreferred
                                    full
                                    native
                                }
                                image {
                                    large
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        return try {
            val detailsJsonMap = mapOf(
                "query" to query,
                "variables" to mapOf("id" to anilistId),
            )
            val detailsJsonPayload = detailsJsonMap.toJson()

            val response = withContext(Dispatchers.IO) {
                app.post(
                    url = ANILIST_GRAPHQL_URL,
                    requestBody = detailsJsonPayload.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull()),
                    headers = mapOf(
                        "Content-Type" to "application/json",
                        "Accept" to "application/json",
                        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36",
                    ),
                    timeout = 8000L,
                ).text
            }

            val parsed = tryParseJson<MediaDetailsResponse>(response)
            val media = parsed?.data?.media ?: return false

            withContext(Dispatchers.Main.immediate) {
                // Apply preferred anime title
                val preferredTitle = when (com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.animeTitleLanguage.value) {
                    "english" -> media.title?.english ?: media.title?.userPreferred ?: media.title?.romaji
                    "native" -> media.title?.native ?: media.title?.romaji ?: media.title?.english
                    else -> media.title?.romaji ?: media.title?.userPreferred ?: media.title?.english
                }
                if (!preferredTitle.isNullOrBlank()) {
                    loaded.name = preferredTitle
                }

                // Apply high-res banner if available
                if (media.bannerImage != null && (loaded.backgroundPosterUrl.isNullOrBlank() || context.overwrite)) {
                    loaded.backgroundPosterUrl = media.bannerImage
                }
                if (loaded.posterUrl.isNullOrBlank() && media.coverImage?.extraLarge != null) {
                    loaded.posterUrl = media.coverImage.extraLarge
                }
                if (loaded.plot.isNullOrBlank() && !media.description.isNullOrBlank()) {
                    loaded.plot = TitleUtils.cleanHtml(media.description)
                }
                if (media.averageScore != null) {
                    if (loaded.score == null) {
                        loaded.score = Score.from10(media.averageScore / 10.0)
                    }
                    callbacks.onRatingsLoaded(null, null, media.averageScore / 10.0)
                }

                // Map Voice Actors with character roles (if enabled)
                val actorsList = mutableListOf<ActorData>()
                if (com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.animeVoiceCast.value) {
                    media.characters?.edges?.forEach { edge ->
                        val charName = edge.node?.name?.userPreferred ?: edge.node?.name?.full ?: ""
                        val charImage = edge.node?.image?.large

                        val vaNode = edge.voiceActors?.firstOrNull()
                        val vaName = vaNode?.name?.userPreferred ?: vaNode?.name?.full
                        val vaImage = vaNode?.image?.large

                        val parsedRole = when (edge.role?.uppercase()) {
                            "MAIN" -> ActorRole.Main
                            "SUPPORTING" -> ActorRole.Supporting
                            "BACKGROUND" -> ActorRole.Background
                            else -> null
                        }

                        if (charName.isNotBlank()) {
                            actorsList.add(
                                ActorData(
                                    actor = com.lagradost.cloudstream3.Actor(
                                        name = charName,
                                        image = charImage,
                                    ),
                                    role = parsedRole,
                                    voiceActor = if (vaName != null) {
                                        com.lagradost.cloudstream3.Actor(
                                            name = vaName,
                                            image = vaImage,
                                        )
                                    } else null,
                                )
                            )
                        }
                    }

                    if (actorsList.isNotEmpty()) {
                        loaded.actors = actorsList
                        callbacks.onActorsLoaded(actorsList)
                    }
                }

                // Map YouTube trailers
                val trailersList = mutableListOf<TrailerData>()
                val trailerId = media.trailer?.id
                if (media.trailer?.site.equals("youtube", ignoreCase = true) && !trailerId.isNullOrBlank()) {
                    trailersList.add(
                        TrailerData(
                            id = trailerId,
                            name = "${media.title?.userPreferred ?: loaded.name} Official Trailer",
                            url = "https://www.youtube.com/watch?v=$trailerId",
                            rawKey = trailerId,
                            thumbnailUrl = media.trailer.thumbnail ?: "https://img.youtube.com/vi/$trailerId/maxresdefault.jpg",
                            site = "YouTube",
                        )
                    )
                    callbacks.onTrailersLoaded(trailersList)
                }

                // Synthesize upcoming simulcast episode if nextAiringEpisode is available (if enabled)
                val nextEp = media.nextAiringEpisode
                if (com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.animeSimulcast.value && nextEp != null && nextEp.episode != null && nextEp.airingAt != null) {
                    val airEpochMs = nextEp.airingAt * 1000L
                    val dateIso = java.time.Instant.ofEpochMilli(airEpochMs).toString().substringBefore("T")
                    if (loaded is AnimeLoadResponse) {
                        val allEps = loaded.episodes.values.flatten()
                        val exists = allEps.any { it.episode == nextEp.episode }
                        if (!exists) {
                            val targetSeason = allEps.mapNotNull { it.season }.maxOrNull() ?: 1
                            val synthetic = dummyApi.newEpisode("unreleased_anime_ep${nextEp.episode}") {
                                this.name = "Episode ${nextEp.episode}"
                                this.episode = nextEp.episode
                                this.season = targetSeason
                                this.posterUrl = media.bannerImage ?: media.coverImage?.extraLarge
                                this.description = "||DATE:$dateIso||Upcoming anime simulcast episode."
                            }
                            val mutableMap = loaded.episodes.toMutableMap()
                            if (mutableMap.isEmpty()) {
                                mutableMap[DubStatus.Subbed] = listOf(synthetic)
                            } else {
                                mutableMap.keys.forEach { k ->
                                    val cleanList = mutableMap[k].orEmpty().filter { it.episode != nextEp.episode }
                                    mutableMap[k] = (cleanList + synthetic)
                                        .distinctBy { Pair(it.season ?: targetSeason, it.episode ?: 0) }
                                        .sortedWith(compareBy({ it.season ?: targetSeason }, { it.episode ?: 0 }))
                                }
                            }
                            loaded.episodes = mutableMap
                        }
                    }
                }

                // Studios (if enabled)
                val studiosList = if (com.lagradost.cloudstream3.desktop.metadata.MetadataConfig.animeStudios.value) {
                    media.studios?.nodes?.mapNotNull { it.name } ?: emptyList()
                } else emptyList()
                val aniListCompanies = studiosList.map { sName ->
                    com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany(
                        name = sName,
                        originCountry = "JP",
                    )
                }

                callbacks.onMetadataLoaded(
                    null, // tagline
                    media.status,
                    studiosList,
                    null, // collectionName
                    null, // collectionBg
                    null, // seasonsCount
                    null, // episodesCount
                    null, // seasonsMetadata
                    "ja", // originalLang
                    null, // releaseDate
                    "JP", // country
                    emptyList(), // collectionItems
                    null, // budget
                    null, // revenue
                    null, // networks
                    media.seasonYear ?: loaded.year,
                    loaded.duration,
                    media.genres,
                    actorsList,
                    aniListCompanies, // productionCompanies
                    null, // networkCompanies
                )
            }

            AppLogger.i(TAG, "✓ AniList enrichment completed for '${loaded.name}' (Cast: ${loaded.actors?.size ?: 0})")
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "AniList enrichment failed for ID=$anilistId", e)
            false
        }
    }
}
