package com.lagradost.cloudstream3.desktop.debrid.torbox

import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe in-memory cache for user Torbox cloud items across navigation cycles.
 * Prevents redundant network calls and screen resets when switching tabs or exiting the player.
 */
object TorboxLibraryCache {
    private val cache = ConcurrentHashMap<TorboxStorageType, List<TorboxTorrentData>>()
    private val lastFetchTimes = ConcurrentHashMap<TorboxStorageType, Long>()

    @Volatile
    var lastSelectedStorageType: TorboxStorageType = TorboxStorageType.TORRENTS

    fun get(storageType: TorboxStorageType): List<TorboxTorrentData>? = cache[storageType]

    fun set(storageType: TorboxStorageType, items: List<TorboxTorrentData>) {
        cache[storageType] = items
        lastFetchTimes[storageType] = System.currentTimeMillis()
    }

    fun isFresh(storageType: TorboxStorageType, ttlMs: Long = 120_000L): Boolean {
        val last = lastFetchTimes[storageType] ?: return false
        return System.currentTimeMillis() - last < ttlMs
    }

    fun findItem(itemId: Int): TorboxTorrentData? {
        for (list in cache.values) {
            val found = list.find { it.id == itemId }
            if (found != null) return found
        }
        return null
    }

    fun updateItem(item: TorboxTorrentData) {
        val current = cache[item.storageType] ?: return
        val updated = current.map { if (it.id == item.id) item else it }
        cache[item.storageType] = updated
    }

    fun removeItem(storageType: TorboxStorageType, itemId: Int) {
        val current = cache[storageType] ?: return
        cache[storageType] = current.filterNot { it.id == itemId }
    }

    fun clear() {
        cache.clear()
        lastFetchTimes.clear()
    }
}
