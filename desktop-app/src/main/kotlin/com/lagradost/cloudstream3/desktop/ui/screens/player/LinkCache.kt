package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import java.util.concurrent.ConcurrentHashMap

object LinkCache {
    data class CachedLinks(
        val links: List<ExtractorLink>,
        val subtitles: List<SubtitleFile>,
        val timestamp: Long,
    )

    private val cache = ConcurrentHashMap<String, CachedLinks>()
    private const val CACHE_DURATION_MS = 5 * 60 * 1000L // 5 minutes
    private const val MAX_ENTRIES = 20

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

    fun clearAll() {
        cache.clear()
    }

    fun set(episodeId: String, links: List<ExtractorLink>, subtitles: List<SubtitleFile>) {
        val now = System.currentTimeMillis()

        // 1. Prune all expired entries
        cache.entries.removeIf { now - it.value.timestamp > CACHE_DURATION_MS }

        // 2. Enforce maximum capacity bound (evict oldest)
        if (cache.size >= MAX_ENTRIES) {
            val oldestKey = cache.minByOrNull { it.value.timestamp }?.key
            if (oldestKey != null) {
                cache.remove(oldestKey)
            }
        }

        // 3. Store new entry
        cache[episodeId] = CachedLinks(
            links = links,
            subtitles = subtitles,
            timestamp = now,
        )
    }
}
