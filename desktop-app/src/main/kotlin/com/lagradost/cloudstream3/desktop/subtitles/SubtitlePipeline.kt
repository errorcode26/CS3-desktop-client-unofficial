package com.lagradost.cloudstream3.desktop.subtitles

import com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch
import com.lagradost.cloudstream3.syncproviders.AccountManager
import com.lagradost.common.logging.AppLogger
import com.lagradost.runtime.executor.SafePluginInvoker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext

object SubtitlePipeline {
    private const val TAG = "SubtitlePipeline"

    suspend fun searchSubtitles(
        query: String,
        lang: String?,
        season: Int?,
        episode: Int?,
        imdbId: String? = null,
    ): List<Map<String, Any?>> = withContext(Dispatchers.IO) {
        val search = SubtitleSearch(
            query = query,
            lang = lang?.takeIf { it.isNotBlank() && !it.equals("all", true) },
            imdbId = imdbId,
            seasonNumber = season,
            epNumber = episode,
        )

        AppLogger.i(TAG, "Starting concurrent subtitle search for '${query}' [lang=${lang ?: "all"}, s=$season, e=$episode, imdb=$imdbId]")

        val allResults = mutableListOf<Map<String, Any?>>()
        val deferredList = AccountManager.subtitleProviders.map { provider ->
            async {
                val auth = AccountManager.cachedAccounts[provider.idPrefix]?.firstOrNull()
                SafePluginInvoker.invokeOrNull(
                    tag = "SubSearch:${provider.name}",
                    providerName = provider.name,
                    timeoutMs = SafePluginInvoker.TIMEOUT_SEARCH_MS,
                ) {
                    provider.search(auth, search)
                }
            }
        }

        val providerResults = deferredList.awaitAll().filterNotNull()
        for (subList in providerResults) {
            for (sub in subList) {
                val norm = LanguageNormalizer.normalize(sub.lang)
                allResults.add(
                    mapOf(
                        "idPrefix" to sub.idPrefix,
                        "name" to sub.name,
                        "lang" to sub.lang,
                        "langName" to norm.displayName,
                        "langBadge" to norm.badge,
                        "data" to sub.data,
                        "source" to sub.source,
                        "seasonNumber" to sub.seasonNumber,
                        "epNumber" to sub.epNumber,
                    ),
                )
            }
        }

        AppLogger.i(TAG, "Completed: total ${allResults.size} subtitles aggregated")
        return@withContext allResults
    }
}
