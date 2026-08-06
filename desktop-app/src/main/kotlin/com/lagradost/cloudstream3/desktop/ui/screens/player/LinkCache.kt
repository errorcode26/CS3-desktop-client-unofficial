package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.concurrent.ConcurrentHashMap

object LinkCache {
    data class CachedLinks(
        val links: List<ExtractorLink>,
        val subtitles: List<SubtitleFile>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CachedLinks>()
    private const val CACHE_DURATION_MS = 2 * 60 * 1000L // 2 minutes

    fun get(episodeId: String): CachedLinks? {
        val entry = cache[episodeId] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > CACHE_DURATION_MS) {
            cache.remove(episodeId)
            return null
        }
        return entry
    }

    fun remove(episodeId: String) {
        cache.remove(episodeId)
    }

    fun set(episodeId: String, links: List<ExtractorLink>, subtitles: List<SubtitleFile>) {
        cache[episodeId] = CachedLinks(
            links = links,
            subtitles = subtitles,
            timestamp = System.currentTimeMillis()
        )
    }
}
