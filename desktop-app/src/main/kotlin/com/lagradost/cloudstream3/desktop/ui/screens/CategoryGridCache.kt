package com.lagradost.cloudstream3.desktop.ui.screens

import com.lagradost.cloudstream3.SearchResponse

object CategoryGridCache {
    private val cache = mutableMapOf<String, List<SearchResponse>>()

    fun put(providerName: String, title: String, items: List<SearchResponse>) {
        cache["$providerName-$title"] = items
    }

    fun get(providerName: String, title: String): List<SearchResponse>? {
        return cache["$providerName-$title"]
    }

    fun remove(providerName: String, title: String) {
        cache.remove("$providerName-$title")
    }
}
