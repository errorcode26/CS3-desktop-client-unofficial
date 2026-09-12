package com.lagradost.cloudstream3.desktop.ui.screens

import com.lagradost.cloudstream3.SearchResponse

object CategoryGridCache {
    private const val MAX_ENTRIES = 30
    private val lock = Any()
    private val cache = object : java.util.LinkedHashMap<String, List<SearchResponse>>(MAX_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SearchResponse>>?): Boolean {
            return size > MAX_ENTRIES
        }
    }

    fun put(providerName: String, title: String, items: List<SearchResponse>) {
        synchronized(lock) {
            cache["$providerName-$title"] = items
        }
    }

    fun get(providerName: String, title: String): List<SearchResponse>? {
        return synchronized(lock) {
            cache["$providerName-$title"]
        }
    }

    fun remove(providerName: String, title: String) {
        synchronized(lock) {
            cache.remove("$providerName-$title")
        }
    }
}
