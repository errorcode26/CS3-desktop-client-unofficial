package com.lagradost.cloudstream3.desktop.ui.screens.studio.model

import com.lagradost.cloudstream3.TvType

enum class StudioCategory {
    ALL,
    MOVIES,
    TV_SHOWS,
}

data class StudioMediaItem(
    val tmdbId: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val mediaType: TvType,
    val voteAverage: Double?,
    val popularity: Double,
    val overview: String? = null,
)

data class StudioDetail(
    val id: Int,
    val name: String,
    val description: String?,
    val headquarters: String?,
    val originCountry: String?,
    val homepage: String?,
    val logoUrl: String?,
    val movieTitles: List<StudioMediaItem>,
    val tvTitles: List<StudioMediaItem>,
) {
    val allTitles: List<StudioMediaItem> by lazy {
        (movieTitles + tvTitles).sortedByDescending { it.popularity }
    }
}
