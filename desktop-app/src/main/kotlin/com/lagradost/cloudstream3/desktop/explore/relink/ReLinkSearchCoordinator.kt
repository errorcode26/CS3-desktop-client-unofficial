package com.lagradost.cloudstream3.desktop.explore.relink

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.explore.models.ExploreItem
import com.lagradost.cloudstream3.desktop.explore.models.ProviderMatch
import com.lagradost.cloudstream3.desktop.metadata.stremio.StremioAddonClient
import com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.ui.badges.CardTitleSanitizer
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.CopyOnWriteArrayList

object ReLinkSearchCoordinator {
    private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9]")
    private val WHITESPACE_REGEX = Regex("\\s+")
    private val STOP_WORDS = setOf("the", "a", "an", "and", "of", "in", "to", "for", "with", "on", "at", "by", "from", "season", "episode")

    fun isTitleRelevant(targetTitle: String, candidateTitle: String): Boolean {
        val targetNorm = NON_ALPHANUMERIC_REGEX.replace(
            CardTitleSanitizer.sanitize(targetTitle).displayTitle.lowercase(Locale.US),
            " ",
        ).trim()
        val candidateNorm = NON_ALPHANUMERIC_REGEX.replace(
            CardTitleSanitizer.sanitize(candidateTitle).displayTitle.lowercase(Locale.US),
            " ",
        ).trim()

        if (targetNorm.isBlank() || candidateNorm.isBlank()) return false
        if (targetNorm == candidateNorm) return true
        if (candidateNorm.contains(targetNorm) || targetNorm.contains(candidateNorm)) return true

        val targetWords = targetNorm.split(WHITESPACE_REGEX).filter { it.length > 1 && it !in STOP_WORDS }

        if (targetWords.isNotEmpty() && targetWords.all { candidateNorm.contains(it) }) {
            return true
        }

        return false
    }

    suspend fun resolveExploreItem(
        rawTitle: String,
        posterUrl: String?,
        isSeriesHint: Boolean?,
    ): ExploreItem = withContext(Dispatchers.IO) {
        val cleanTitle = CardTitleSanitizer.sanitize(rawTitle, autoClean = true).displayTitle.ifBlank { rawTitle }
        val targetType = when (isSeriesHint) {
            true -> "series"
            false -> "movie"
            null -> "series"
        }

        val initialItem = ExploreItem(
            id = cleanTitle,
            type = targetType,
            name = cleanTitle,
            posterUrl = posterUrl,
        )

        try {
            val results = StremioAddonClient.search(cleanTitle, targetType)
                ?: (if (isSeriesHint == null) StremioAddonClient.search(cleanTitle, "movie") else null)
            val match = results?.firstOrNull {
                it.name?.equals(cleanTitle, ignoreCase = true) == true
            } ?: results?.firstOrNull()

            if (match != null) {
                initialItem.copy(
                    id = match.id ?: cleanTitle,
                    type = match.type ?: targetType,
                    name = match.name ?: cleanTitle,
                    posterUrl = match.poster ?: posterUrl,
                    backgroundUrl = match.background?.replace("t/p/original//", "t/p/original/"),
                    logoUrl = match.logo,
                    releaseYear = match.releaseInfo,
                    description = match.description,
                    rating = match.imdbRating?.toDoubleOrNull(),
                    genres = match.genres ?: emptyList(),
                )
            } else {
                initialItem
            }
        } catch (_: Exception) {
            initialItem
        }
    }

    suspend fun searchRealProvidersProgressive(
        searchTitle: String,
        onUpdate: (List<ProviderMatch>) -> Unit,
    ) = coroutineScope {
        val cleanTitle = CardTitleSanitizer.sanitize(searchTitle, autoClean = true).displayTitle.ifBlank { searchTitle }
        val activeProviders: List<MainAPI> = ActiveProviderRepository.allRealProviders.value
            .ifEmpty { APIHolder.allProviders.filter { ActiveProviderRepository.isRealContentProvider(it) } }

        if (activeProviders.isEmpty()) return@coroutineScope

        val aggregatedMatches = CopyOnWriteArrayList<ProviderMatch>()

        val jobs = activeProviders.map { provider ->
            launch(Dispatchers.IO) {
                try {
                    val res = SafePluginInvoker.invokeOrNull(
                        tag = "ReLink:Search:${provider.name}",
                        timeoutMs = 4000L,
                    ) {
                        provider.search(cleanTitle, 1)
                    }

                    val items = res?.items
                    if (!items.isNullOrEmpty()) {
                        val valid = items.filter { isTitleRelevant(cleanTitle, it.name) }.map { resp ->
                            val meta = CardTitleSanitizer.sanitize(resp.name)
                            ProviderMatch(
                                providerName = provider.name,
                                searchResponse = resp,
                                displayTitle = meta.displayTitle,
                                qualityText = meta.qualityText,
                                hasSub = meta.hasSub,
                                hasDub = meta.hasDub,
                            )
                        }
                        if (valid.isNotEmpty()) {
                            aggregatedMatches.addAll(valid)
                            onUpdate(aggregatedMatches.toList())
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        jobs.forEach { it.join() }
    }
}
