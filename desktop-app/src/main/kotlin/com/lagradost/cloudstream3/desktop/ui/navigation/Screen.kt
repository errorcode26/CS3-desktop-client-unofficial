package com.lagradost.cloudstream3.desktop.ui.navigation

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.SearchResponse

sealed class Screen {
    object Home : Screen()
    object Extensions : Screen()
    object Library : Screen()
    object Settings : Screen()
    data class Details(val provider: MainAPI, val url: String, val preloadedName: String? = null, val preloadedPoster: String? = null, val preloadedBg: String? = null) : Screen()
    data class CategoryGrid(val provider: MainAPI, val title: String, val items: List<SearchResponse>) : Screen()
}
