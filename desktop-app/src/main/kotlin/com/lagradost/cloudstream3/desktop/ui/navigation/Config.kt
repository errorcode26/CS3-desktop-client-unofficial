package com.lagradost.cloudstream3.desktop.ui.navigation

import com.lagradost.cloudstream3.ActorData

sealed class Config {
    data object Home : Config()
    data object Explore : Config()
    data object History : Config()
    data object Search : Config()
    data class Extensions(val initialTab: Int = 0) : Config()
    data object Library : Config()
    data object Downloads : Config()
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
    data class Person(
        val name: String,
        val image: String? = null,
        val tmdbId: Int? = null,
    ) : Config()
    data class Studio(
        val name: String,
        val companyId: Int? = null,
        val logoUrl: String? = null,
        val originCountry: String? = null,
    ) : Config()
    data class FullCast(
        val mediaTitle: String,
        val providerName: String? = null,
        val cast: List<ActorData> = emptyList(),
        val directors: List<ActorData> = emptyList(),
        val writers: List<ActorData> = emptyList(),
        val producers: List<ActorData> = emptyList(),
        val tmdbId: Int? = null,
        val availableSeasons: List<Int> = emptyList(),
        val initialSeason: Int? = null,
    ) : Config()
}
