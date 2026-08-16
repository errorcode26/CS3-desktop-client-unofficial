package com.lagradost.cloudstream3.desktop.player.skip

import com.lagradost.cloudstream3.desktop.player.PlayerConfig
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object SkipManager {

    private val skipCache = ConcurrentHashMap<String, List<SkipInterval>>()

    private val providers: List<ISkipProvider> = listOf(
        AniSkipProvider,
        IntroDbProvider,
        ChapterSkipProvider
    )

    suspend fun resolveSkipIntervals(
        query: SkipQuery,
        chapters: List<PlayerState.Chapter> = emptyList(),
        totalDurationMs: Long = 0L
    ): List<SkipInterval> = withContext(Dispatchers.IO) {
        val enabled = DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_ENABLE_SKIP_INTERVALS) ?: true
        if (!enabled) return@withContext emptyList()

        val cacheKey = "${query.title.lowercase().trim()}_S${query.season}E${query.episode}"
        skipCache[cacheKey]?.let { cached ->
            if (cached.isNotEmpty()) {
                com.lagradost.common.logging.AppLogger.i("SkipManager", "Returning ${cached.size} cached skip intervals for '$cacheKey'")
                return@withContext cached
            }
        }

        val intervals = mutableListOf<SkipInterval>()
        com.lagradost.common.logging.AppLogger.i("SkipManager", "Resolving skip intervals for '${query.title}' (S${query.season}E${query.episode})...")

        // 1. Try AniSkip (Anime API)
        try {
            val aniSkipResults = AniSkipProvider.getSkipIntervals(query)
            if (aniSkipResults.isNotEmpty()) {
                intervals.addAll(aniSkipResults)
                skipCache[cacheKey] = intervals
                com.lagradost.common.logging.AppLogger.i("SkipManager", "Loaded ${aniSkipResults.size} intervals from AniSkip")
                return@withContext intervals
            }
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.i("SkipManager", "AniSkip lookup error: ${e.message}")
        }

        // 2. Try IntroDB (TV Shows)
        try {
            val introDbResults = IntroDbProvider.getSkipIntervals(query)
            if (introDbResults.isNotEmpty()) {
                intervals.addAll(introDbResults)
                skipCache[cacheKey] = intervals
                com.lagradost.common.logging.AppLogger.i("SkipManager", "Loaded ${introDbResults.size} intervals from IntroDB")
                return@withContext intervals
            }
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.i("SkipManager", "IntroDB lookup error: ${e.message}")
        }

        // 3. Fallback: Embedded Chapters from video stream
        if (chapters.isNotEmpty()) {
            val chapterIntervals = ChapterSkipProvider.parseChapters(chapters, totalDurationMs)
            if (chapterIntervals.isNotEmpty()) {
                intervals.addAll(chapterIntervals)
                skipCache[cacheKey] = intervals
                com.lagradost.common.logging.AppLogger.i("SkipManager", "Loaded ${chapterIntervals.size} intervals from embedded chapters")
                return@withContext intervals
            }
        }

        intervals
    }
}
