package com.lagradost.cloudstream3.desktop.ui.navigation

import com.lagradost.cloudstream3.SearchResponse

sealed class Config {
    data object Home : Config()
    data object History : Config()
    data object Search : Config()
    data class Extensions(val initialTab: Int = 0) : Config()
    data object Library : Config()
    data object Settings : Config()
    data class Details(
        val providerName: String,
        val url: String,
        val preloadedName: String? = null,
        val preloadedPoster: String? = null,
        val preloadedBg: String? = null,
        val autoPlay: Boolean = false,
    ) : Config()
    data class CategoryGrid(
        val providerName: String,
        val title: String,
    ) : Config()
}
