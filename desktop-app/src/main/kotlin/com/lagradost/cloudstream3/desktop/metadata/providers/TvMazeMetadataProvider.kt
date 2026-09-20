package com.lagradost.cloudstream3.desktop.metadata.providers

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.desktop.metadata.*
import com.lagradost.cloudstream3.desktop.ui.screens.details.contract.ProductionCompany
import com.lagradost.cloudstream3.desktop.utils.StringUtils
import com.lagradost.cloudstream3.desktop.utils.TitleUtils
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

/**
 * Universal TVmaze Metadata Provider.
 * Delivers zero-key, high-speed enrichment for Western TV series, cartoons, and documentaries.
 * Provides exact broadcast air dates, streaming channels, episode stills, and cast portfolios.
 */
object TvMazeMetadataProvider : MetadataProvider {
    private const val TAG = "TvMazeProvider"
    private const val TVMAZE_API_URL = "https://api.tvmaze.com"

    private val dummyApi = object : MainAPI() {
        override var name = "TVmaze"
        override var mainUrl = "https://tvmaze.com"
    }

    override val id: String = "tvmaze"
    override val displayName: String = "TVmaze"
    override val priority: Int = 2
    override val supportedTypes: Set<TvType> = setOf(
        TvType.TvSeries,
        TvType.Cartoon,
        TvType.Documentary,
    )

    private data class TvMazeSearchResult(
        @JsonProperty("score") val score: Double?,
        @JsonProperty("show") val show: TvMazeShow?,
    )

    private data class TvMazeShow(
        @JsonProperty("id") val id: Int,
        @JsonProperty("name") val name: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("language") val language: String?,
        @JsonProperty("genres") val genres: List<String>?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("runtime") val runtime: Int?,
        @JsonProperty("averageRuntime") val averageRuntime: Int?,
        @JsonProperty("premiered") val premiered: String?,
        @JsonProperty("ended") val ended: String?,
        @JsonProperty("officialSite") val officialSite: String?,
        @JsonProperty("rating") val rating: TvMazeRating?,
        @JsonProperty("network") val network: TvMazeNetwork?,
        @JsonProperty("webChannel") val webChannel: TvMazeNetwork?,
        @JsonProperty("image") val image: TvMazeImage?,
        @JsonProperty("summary") val summary: String?,
        @JsonProperty("externals") val externals: TvMazeExternals?,
        @JsonProperty("_embedded") val embedded: TvMazeEmbedded?,
    )

    private data class TvMazeRating(
        @JsonProperty("average") val average: Double?,
    )

    private data class TvMazeNetwork(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("country") val country: TvMazeCountry?,
        @JsonProperty("officialSite") val officialSite: String?,
    )

    private data class TvMazeCountry(
        @JsonProperty("name") val name: String?,
        @JsonProperty("code") val code: String?,
        @JsonProperty("timezone") val timezone: String?,
    )

    private data class TvMazeImage(
        @JsonProperty("medium") val medium: String?,
        @JsonProperty("original") val original: String?,
    )

    private data class TvMazeExternals(
        @JsonProperty("imdb") val imdb: String?,
        @JsonProperty("thetvdb") val thetvdb: Int?,
    )

    private data class TvMazeEmbedded(
        @JsonProperty("cast") val cast: List<TvMazeCastMember>?,
        @JsonProperty("episodes") val episodes: List<TvMazeEpisode>?,
        @JsonProperty("nextepisode") val nextepisode: TvMazeEpisode?,
    )

    private data class TvMazeCastMember(
        @JsonProperty("person") val person: TvMazePerson?,
        @JsonProperty("character") val character: TvMazeCharacter?,
    )

    private data class TvMazePerson(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("image") val image: TvMazeImage?,
    )

    private data class TvMazeCharacter(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("image") val image: TvMazeImage?,
    )

    private data class TvMazeEpisode(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("name") val name: String?,
        @JsonProperty("season") val season: Int?,
        @JsonProperty("number") val number: Int?,
        @JsonProperty("airdate") val airdate: String?,
        @JsonProperty("airtime") val airtime: String?,
        @JsonProperty("airstamp") val airstamp: String?,
        @JsonProperty("runtime") val runtime: Int?,
        @JsonProperty("rating") val rating: TvMazeRating?,
        @JsonProperty("image") val image: TvMazeImage?,
        @JsonProperty("summary") val summary: String?,
    )

    private data class TvMazeImageItem(
        @JsonProperty("id") val id: Int,
        @JsonProperty("type") val type: String?,
        @JsonProperty("main") val main: Boolean?,
        @JsonProperty("resolutions") val resolutions: TvMazeResolutions?,
    )

    private data class TvMazeResolutions(
        @JsonProperty("original") val original: TvMazeResolutionUrl?,
        @JsonProperty("medium") val medium: TvMazeResolutionUrl?,
    )

    private data class TvMazeResolutionUrl(
        @JsonProperty("url") val url: String?,
        @JsonProperty("width") val width: Int?,
        @JsonProperty("height") val height: Int?,
    )

    override suspend fun resolve(
        title: String,
        year: Int?,
        type: TvType,
        rawUrl: String?,
    ): MetadataMatch? {
        val (cleanName, titleYear) = TitleUtils.cleanProviderTitle(title)
        val targetYear = year ?: titleYear

        return try {
            withContext(Dispatchers.IO) {
                // 1. Single search with full embeds (most efficient query)
                val encodedQuery = URLEncoder.encode(cleanName, "UTF-8")
                val singleSearchUrl = "$TVMAZE_API_URL/singlesearch/shows?q=$encodedQuery&embed[]=episodes&embed[]=cast&embed[]=nextepisode"

                var show: TvMazeShow? = null
                try {
                    val res = app.get(singleSearchUrl, timeout = 6000L).text
                    val cand = tryParseJson<TvMazeShow>(res)
                    if (cand != null) {
                        val candName = cand.name ?: ""
                        val candYear = cand.premiered?.substringBefore("-")?.toIntOrNull()
                        if (StringUtils.isTitleMatch(cleanName, candName, targetYear, candYear, isTv = true)) {
                            show = cand
                        } else {
                            show = null
                        }
                    }
                } catch (_: Exception) {}

                // 2. Multi-search fallback if single search missed or rejected
                if (show == null) {
                    val multiSearchUrl = "$TVMAZE_API_URL/search/shows?q=$encodedQuery"
                    val res = app.get(multiSearchUrl, timeout = 6000L).text
                    val results = tryParseJson<List<TvMazeSearchResult>>(res)

                    if (!results.isNullOrEmpty()) {
                        var bestCandidate: TvMazeShow? = null
                        var bestScore = 0.0

                        for (cand in results) {
                            val candShow = cand.show ?: continue
                            val candName = candShow.name ?: continue
                            val candYear = candShow.premiered?.substringBefore("-")?.toIntOrNull()

                            if (!StringUtils.isTitleMatch(cleanName, candName, targetYear, candYear, isTv = true)) {
                                continue
                            }

                            val strippedClean = cleanName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                            val strippedCand = candName.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

                            val sim = StringUtils.similarity(strippedClean, strippedCand)
                            if (sim < 0.65) continue

                            var finalScore = sim
                            if (targetYear != null && candYear != null) {
                                val yearDiff = Math.abs(targetYear - candYear)
                                if (yearDiff <= 1) finalScore += 0.2
                                else if (yearDiff <= 2) finalScore += 0.05
                                else finalScore -= 0.7
                            }

                            if (finalScore > bestScore && finalScore >= 0.85) {
                                bestScore = finalScore
                                bestCandidate = candShow
                            }
                        }

                        if (bestCandidate != null) {
                            // Fetch full embeds for the best candidate
                            val fullUrl = "$TVMAZE_API_URL/shows/${bestCandidate.id}?embed[]=episodes&embed[]=cast&embed[]=nextepisode"
                            val fullRes = app.get(fullUrl, timeout = 6000L).text
                            show = tryParseJson<TvMazeShow>(fullRes) ?: bestCandidate
                        }
                    }
                }

                if (show == null) return@withContext null

                val showYear = show.premiered?.substringBefore("-")?.toIntOrNull() ?: targetYear
                val poster = show.image?.original ?: show.image?.medium
                val ratingScore = show.rating?.average

                AppLogger.i(TAG, "✓ TVmaze resolved: '${show.name}' ($showYear) | id=${show.id}")

                MetadataMatch(
                    providerId = id,
                    matchedTitle = show.name ?: cleanName,
                    matchedYear = showYear,
                    imdbId = show.externals?.imdb,
                    tmdbId = null,
                    posterUrl = poster,
                    description = cleanHtmlSummary(show.summary),
                    rating = ratingScore,
                    rawData = show,
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "TVmaze resolution failed for '$cleanName'", e)
            null
        }
    }

    override suspend fun enrich(
        loaded: LoadResponse,
        match: MetadataMatch?,
        context: MetadataEnrichmentContext,
        callbacks: MetadataEnrichmentCallbacks,
    ): Boolean {
        return try {
            withContext(Dispatchers.IO) {
                var showData = match?.rawData as? TvMazeShow

                val imdb = context.directImdbId ?: match?.imdbId
                if (!imdb.isNullOrBlank()) {
                    if (showData == null || showData.embedded == null) {
                        try {
                            val lookupUrl = "$TVMAZE_API_URL/lookup/shows?imdb=$imdb"
                            val res = app.get(lookupUrl, timeout = 4000L).text
                            val partial = tryParseJson<TvMazeShow>(res)
                            if (partial != null) {
                                val fullUrl = "$TVMAZE_API_URL/shows/${partial.id}?embed[]=episodes&embed[]=cast&embed[]=nextepisode"
                                val fullRes = app.get(fullUrl, timeout = 4000L).text
                                showData = tryParseJson<TvMazeShow>(fullRes) ?: partial
                            }
                        } catch (_: Exception) {}
                    }
                } else if (showData == null) {
                    val resolved = resolve(loaded.name, loaded.year, loaded.type, context.rawUrl)
                    showData = resolved?.rawData as? TvMazeShow
                }

                if (showData == null) return@withContext false

                val show = showData

                // 1. Fetch 4K Background Wallpaper & Transparent Logo from TVmaze Images endpoint if missing
                var tvmazeBackdrop: String? = null
                var tvmazeLogo: String? = null
                if (loaded.backgroundPosterUrl.isNullOrBlank() || loaded.logoUrl.isNullOrBlank()) {
                    try {
                        val imagesUrl = "$TVMAZE_API_URL/shows/${show.id}/images"
                        val imgRes = app.get(imagesUrl, timeout = 4000L).text
                        val imgList = tryParseJson<List<TvMazeImageItem>>(imgRes)
                        if (!imgList.isNullOrEmpty()) {
                            tvmazeBackdrop = imgList.firstOrNull { it.type.equals("background", ignoreCase = true) }?.resolutions?.original?.url
                            tvmazeLogo = imgList.firstOrNull { it.type.equals("typography", ignoreCase = true) }?.resolutions?.original?.url
                        }
                    } catch (_: Exception) {}
                }

                withContext(Dispatchers.Main.immediate) {
                    // Hole-filling visual artwork
                    if (loaded.backgroundPosterUrl.isNullOrBlank() && tvmazeBackdrop != null) {
                        loaded.backgroundPosterUrl = tvmazeBackdrop
                    }
                    if (loaded.posterUrl.isNullOrBlank() && show.image?.original != null) {
                        loaded.posterUrl = show.image.original
                    }
                    if (loaded.logoUrl.isNullOrBlank() && tvmazeLogo != null) {
                        loaded.logoUrl = tvmazeLogo
                    }

                    // Hole-filling plot summary
                    val cleanSummary = cleanHtmlSummary(show.summary)
                    if (loaded.plot.isNullOrBlank() && !cleanSummary.isNullOrBlank()) {
                        loaded.plot = cleanSummary
                    }

                    // Hole-filling ratings & duration
                    if (loaded.score == null && show.rating?.average != null) {
                        loaded.score = Score.from10(show.rating.average)
                        callbacks.onRatingsLoaded(null, null, show.rating.average)
                    }
                    if (loaded.duration == null && (show.averageRuntime ?: show.runtime) != null) {
                        loaded.duration = show.averageRuntime ?: show.runtime
                    }

                    // Broadcaster Network & Streaming Web Channels
                    val networkName = show.webChannel?.name ?: show.network?.name
                    val networkCompanies = mutableListOf<ProductionCompany>()
                    if (!networkName.isNullOrBlank()) {
                        networkCompanies.add(
                            ProductionCompany(
                                name = networkName,
                                originCountry = show.network?.country?.code ?: show.webChannel?.country?.code ?: "US",
                            )
                        )
                    }

                    // Cast & Character Fusion (Deduplicated with photo backfill)
                    val tvmazeCast = show.embedded?.cast.orEmpty()
                    if (tvmazeCast.isNotEmpty()) {
                        val currentActors = loaded.actors.orEmpty().toMutableList()
                        val existingActorNames = currentActors.map { it.actor.name.lowercase().trim() }.toSet()

                        tvmazeCast.forEach { member ->
                            val actorName = member.person?.name?.trim() ?: return@forEach
                            val actorImage = member.person?.image?.original ?: member.person?.image?.medium

                            if (!existingActorNames.contains(actorName.lowercase())) {
                                currentActors.add(
                                    ActorData(
                                        actor = Actor(name = actorName, image = actorImage),
                                        role = ActorRole.Main,
                                        voiceActor = null,
                                    )
                                )
                            } else {
                                // Backfill missing image on existing actor
                                val existingIndex = currentActors.indexOfFirst { it.actor.name.equals(actorName, ignoreCase = true) }
                                if (existingIndex != -1) {
                                    val existing = currentActors[existingIndex]
                                    if (existing.actor.image.isNullOrBlank() && actorImage != null) {
                                        currentActors[existingIndex] = existing.copy(actor = existing.actor.copy(image = actorImage))
                                    }
                                }
                            }
                        }

                        if (currentActors.isNotEmpty()) {
                            loaded.actors = currentActors
                            callbacks.onActorsLoaded(currentActors)
                        }
                    }

                    // 8. Enrich Episode Stills & Descriptions (non-destructive)
                    val tvmazeEpisodes = show.embedded?.episodes.orEmpty()
                    if (tvmazeEpisodes.isNotEmpty()) {
                        var anyThumbnailsEnriched = false

                        val enrichEpisodeItem: (Episode) -> Unit = { existingEp ->
                            val epSeason = existingEp.season ?: 1
                            val epNum = existingEp.episode ?: 0
                            val tvmEp = tvmazeEpisodes.find { (it.season ?: 1) == epSeason && it.number == epNum }
                            if (tvmEp != null) {
                                val epOverview = tvmEp.summary?.let { cleanHtmlSummary(it) }
                                val epStill = tvmEp.image?.original ?: tvmEp.image?.medium
                                val epAirDate = tvmEp.airdate ?: tvmEp.airstamp?.substringBefore("T")

                                val isMissingStill = existingEp.posterUrl.isNullOrBlank() ||
                                    existingEp.posterUrl?.contains("imgbb") == true ||
                                    existingEp.posterUrl == loaded.posterUrl
                                if (isMissingStill && epStill != null) {
                                    existingEp.posterUrl = epStill
                                    anyThumbnailsEnriched = true
                                }
                                if (existingEp.description.isNullOrBlank() && epOverview != null) {
                                    existingEp.description = epOverview
                                }
                                if (epAirDate != null && (existingEp.description == null || !existingEp.description!!.contains("||DATE:"))) {
                                    val clean = (existingEp.description ?: "").replace(Regex("\\|\\|DATE:.*?\\|\\|"), "")
                                    existingEp.description = "||DATE:$epAirDate||$clean"
                                }
                                if (existingEp.name.isNullOrBlank() && tvmEp.name != null) {
                                    existingEp.name = tvmEp.name
                                }
                                if (existingEp.runTime == null && tvmEp.runtime != null) {
                                    existingEp.runTime = tvmEp.runtime
                                }
                            }
                        }

                        if (loaded is TvSeriesLoadResponse) {
                            val existingEpisodes = loaded.episodes.toMutableList()
                            existingEpisodes.forEach(enrichEpisodeItem)

                            val newUnreleased = mutableListOf<Episode>()
                            tvmazeEpisodes.forEach { ep ->
                                val epSeason = ep.season ?: 1
                                val epNum = ep.number ?: return@forEach
                                val exists = existingEpisodes.any { (it.season ?: 1) == epSeason && it.episode == epNum }
                                if (!exists) {
                                    val epAirDate = ep.airdate ?: ep.airstamp?.substringBefore("T")
                                    val isFuture = epAirDate?.let { dateStr ->
                                        try {
                                            val parsed = java.time.LocalDate.parse(dateStr.take(10))
                                            val now = java.time.LocalDate.now(java.time.ZoneOffset.UTC)
                                            parsed.isAfter(now)
                                        } catch (_: Exception) {
                                            false
                                        }
                                    } ?: false

                                    if (isFuture) {
                                        val epOverview = ep.summary?.let { cleanHtmlSummary(it) }
                                        val epStill = ep.image?.original ?: ep.image?.medium
                                        val descWithDate = "||DATE:$epAirDate||${epOverview ?: ""}"
                                        val synthetic = dummyApi.newEpisode("unreleased_s${epSeason}_e${epNum}") {
                                            this.name = ep.name ?: "Episode $epNum"
                                            this.season = epSeason
                                            this.episode = epNum
                                            this.posterUrl = epStill
                                            this.description = descWithDate
                                            this.runTime = ep.runtime
                                            this.score = ep.rating?.average?.let { Score.from10(it) }
                                        }
                                        newUnreleased.add(synthetic)
                                    }
                                }
                            }

                            val distinctNew = newUnreleased.distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                            val updated = (existingEpisodes + distinctNew)
                                .distinctBy { Pair(it.season ?: 1, it.episode ?: 0) }
                                .sortedWith(compareBy({ it.season ?: 1 }, { it.episode ?: 0 }))

                            loaded.episodes = updated.toMutableList()
                        } else if (loaded is AnimeLoadResponse) {
                            loaded.episodes.values.forEach { epList ->
                                epList.forEach(enrichEpisodeItem)
                            }
                        }

                        if (anyThumbnailsEnriched) {
                            callbacks.onEpisodeThumbnailsEnriched()
                        }
                    }

                    callbacks.onMetadataLoaded(
                        null,
                        show.status,
                        if (networkName != null) listOf(networkName) else emptyList(),
                        null,
                        null,
                        null,
                        null,
                        null,
                        show.language ?: "en",
                        show.premiered,
                        show.network?.country?.code ?: "US",
                        emptyList(),
                        null,
                        null,
                        if (networkName != null) listOf(networkName) else emptyList(),
                        show.premiered?.substringBefore("-")?.toIntOrNull() ?: loaded.year,
                        show.averageRuntime ?: show.runtime ?: loaded.duration,
                        show.genres,
                        loaded.actors,
                        null,
                        networkCompanies.takeIf { it.isNotEmpty() },
                    )
                }

                val finalNetName = show.webChannel?.name ?: show.network?.name
                AppLogger.i(TAG, "✓ TVmaze enrichment applied for '${loaded.name}' (Network: $finalNetName, Cast: ${loaded.actors?.size ?: 0})")
                true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.e(TAG, "TVmaze enrichment failed for '${loaded.name}'", e)
            false
        }
    }

    private fun cleanHtmlSummary(html: String?): String? {
        if (html.isNullOrBlank()) return null
        return html
            .replace(Regex("<.*?>"), "")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .trim()
            .takeIf { it.isNotBlank() }
    }
}
