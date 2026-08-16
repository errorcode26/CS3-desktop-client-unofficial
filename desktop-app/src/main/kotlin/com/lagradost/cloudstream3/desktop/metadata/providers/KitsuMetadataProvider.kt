package com.lagradost.cloudstream3.desktop.metadata.providers

import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentCallbacks
import com.lagradost.cloudstream3.desktop.metadata.MetadataEnrichmentContext
import com.lagradost.cloudstream3.desktop.metadata.MetadataMatch
import com.lagradost.cloudstream3.desktop.metadata.MetadataProvider
import com.lagradost.cloudstream3.desktop.utils.StringUtils
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.common.logging.AppLogger
import com.fasterxml.jackson.annotation.JsonProperty
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object KitsuMetadataProvider : MetadataProvider {
    private const val TAG = "KitsuProvider"
    private const val KITSU_API_URL = "https://kitsu.io/api/edge"

    override val id: String = "kitsu"
    override val displayName: String = "Kitsu"
    override val priority: Int = 1
    override val supportedTypes: Set<TvType> = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA,
    )

    private data class KitsuResponse(
        @JsonProperty("data") val data: List<KitsuMedia>?,
    )

    private data class KitsuMedia(
        @JsonProperty("id") val id: String,
        @JsonProperty("attributes") val attributes: KitsuAttributes?,
    )

    private data class KitsuAttributes(
        @JsonProperty("canonicalTitle") val canonicalTitle: String?,
        @JsonProperty("titles") val titles: Map<String, String>?,
        @JsonProperty("synopsis") val synopsis: String?,
        @JsonProperty("averageRating") val averageRating: String?,
        @JsonProperty("startDate") val startDate: String?,
        @JsonProperty("posterImage") val posterImage: KitsuImage?,
        @JsonProperty("coverImage") val coverImage: KitsuImage?,
    )

    private data class KitsuImage(
        @JsonProperty("original") val original: String?,
        @JsonProperty("large") val large: String?,
    )

    override suspend fun resolve(
        title: String,
        year: Int?,
        type: TvType,
        rawUrl: String?,
    ): MetadataMatch? {
        val (cleanName, titleYear) = com.lagradost.cloudstream3.desktop.utils.TitleUtils.cleanProviderTitle(title)
        val targetYear = year ?: titleYear
        
        return try {
            val encodedName = URLEncoder.encode(cleanName, "UTF-8")
            val requestUrl = "${KITSU_API_URL}/anime?filter[text]=$encodedName&page[limit]=5"

            val response = withContext(Dispatchers.IO) {
                app.get(
                    url = requestUrl,
                    headers = mapOf("Accept" to "application/vnd.api+json"),
                    timeout = 6000L,
                ).text
            }

            val parsed = tryParseJson<KitsuResponse>(response)
            val mediaList = parsed?.data ?: return null

            var bestMatch: KitsuMedia? = null
            var bestScore = 0.0

            for (media in mediaList) {
                val attrs = media.attributes ?: continue
                
                val candidateTitles = mutableListOf<String>()
                attrs.canonicalTitle?.let { candidateTitles.add(it) }
                attrs.titles?.values?.forEach { candidateTitles.add(it) }

                for (candTitle in candidateTitles) {
                    val cleanCand = candTitle.lowercase().removePrefix("the ").trim()
                    val cleanQuery = cleanName.lowercase().removePrefix("the ").trim()

                    val strippedCand = cleanCand.replace(Regex("[^a-zA-Z0-9]"), "")
                    val strippedQuery = cleanQuery.replace(Regex("[^a-zA-Z0-9]"), "")

                    val isStrictMatch = strippedCand.equals(strippedQuery, ignoreCase = true)
                    var score = StringUtils.similarity(strippedQuery, strippedCand)
                    if (isStrictMatch) score = 1.0

                    // Year validation if present
                    if (targetYear != null && attrs.startDate != null) {
                        val releaseYear = attrs.startDate.substringBefore("-").toIntOrNull()
                        if (releaseYear != null && Math.abs(releaseYear - targetYear) > 1) {
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
            val matchAttrs = match.attributes ?: return null
            
            AppLogger.i(TAG, "✓ Kitsu Match: '${matchAttrs.canonicalTitle}' (ID: ${match.id}) score=$bestScore")

            val ratingValue = matchAttrs.averageRating?.toDoubleOrNull()?.let { it / 10.0 } // 82.5 -> 8.25

            MetadataMatch(
                providerId = id,
                matchedTitle = matchAttrs.canonicalTitle ?: cleanName,
                matchedYear = matchAttrs.startDate?.substringBefore("-")?.toIntOrNull() ?: targetYear,
                posterUrl = matchAttrs.posterImage?.original ?: matchAttrs.posterImage?.large,
                backdropUrl = matchAttrs.coverImage?.original ?: matchAttrs.coverImage?.large,
                description = matchAttrs.synopsis,
                rating = ratingValue,
                rawData = match,
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLogger.w(TAG, "Kitsu resolve failed for '$cleanName'", e)
            null
        }
    }

    override suspend fun enrich(
        loaded: LoadResponse,
        match: MetadataMatch?,
        context: MetadataEnrichmentContext,
        callbacks: MetadataEnrichmentCallbacks,
    ): Boolean {
        // We already got most of the useful stuff in resolve (poster, banner, description).
        // Let's just apply it to the LoadResponse directly if we matched Kitsu!
        
        // If we didn't match via Kitsu originally, try to resolve it now.
        val resolvedMatch = if (match?.providerId == id) match else resolve(loaded.name, loaded.year, loaded.type, context.rawUrl)
        if (resolvedMatch == null) return false

        withContext(Dispatchers.Main.immediate) {
            if (resolvedMatch.backdropUrl != null) {
                loaded.backgroundPosterUrl = resolvedMatch.backdropUrl
            }
            if (resolvedMatch.posterUrl != null) {
                loaded.posterUrl = resolvedMatch.posterUrl
            }
            if (loaded.plot.isNullOrBlank() && !resolvedMatch.description.isNullOrBlank()) {
                loaded.plot = resolvedMatch.description
            }
            if (resolvedMatch.rating != null && loaded.score == null) {
                loaded.score = Score.from10(resolvedMatch.rating)
            }
            
            AppLogger.i(TAG, "✓ Kitsu enrichment applied. Banner: ${resolvedMatch.backdropUrl}")
        }

        return true
    }
}
