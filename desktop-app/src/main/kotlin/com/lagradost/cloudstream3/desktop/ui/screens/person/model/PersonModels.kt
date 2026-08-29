package com.lagradost.cloudstream3.desktop.ui.screens.person.model

import com.lagradost.cloudstream3.TvType

enum class FilmographyCategory {
    ALL,
    MOVIES,
    TV_SHOWS,
}

data class PersonMediaCredit(
    val tmdbId: Int,
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val releaseYear: String?,
    val characterOrJob: String?,
    val mediaType: TvType,
    val voteAverage: Double?,
    val popularity: Double,
    val overview: String? = null,
)

data class PersonDetail(
    val tmdbId: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    val placeOfBirth: String?,
    val profileUrl: String?,
    val knownForDepartment: String?,
    val movieCredits: List<PersonMediaCredit>,
    val tvCredits: List<PersonMediaCredit>,
) {
    val allCredits: List<PersonMediaCredit> by lazy {
        (movieCredits + tvCredits).sortedByDescending { it.popularity }
    }
}
