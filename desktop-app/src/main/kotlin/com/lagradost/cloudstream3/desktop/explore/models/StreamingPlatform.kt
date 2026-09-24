package com.lagradost.cloudstream3.desktop.explore.models

import androidx.compose.ui.graphics.Color

enum class StreamingPlatform(
    val id: String,
    val tmdbProviderId: Int,
    val displayName: String,
    val brandColor: Color,
    val gradientColors: List<Color>,
    val logoText: String,
    val subtitle: String,
    val logoImageUrl: String,
) {
    NETFLIX(
        id = "netflix",
        tmdbProviderId = 8,
        displayName = "Netflix",
        brandColor = Color(0xFFE50914),
        gradientColors = listOf(Color(0xFF2C0609), Color(0xFF120C12)),
        logoText = "NETFLIX",
        subtitle = "Originals & Trending",
        logoImageUrl = "https://image.tmdb.org/t/p/original/wwemzKWzjKYJFfCeiB57q3r4Bcm.png",
    ),
    DISNEY_PLUS(
        id = "disney",
        tmdbProviderId = 337,
        displayName = "Disney+",
        brandColor = Color(0xFF0063E5),
        gradientColors = listOf(Color(0xFF051D4A), Color(0xFF070D1C)),
        logoText = "Disney+",
        subtitle = "Marvel, Star Wars & Pixar",
        logoImageUrl = "https://image.tmdb.org/t/p/original/gJ8VX6JSu3ciXHuC2dDGAo2lvwM.png",
    ),
    PRIME_VIDEO(
        id = "prime",
        tmdbProviderId = 9,
        displayName = "Prime Video",
        brandColor = Color(0xFF00A8E1),
        gradientColors = listOf(Color(0xFF002448), Color(0xFF080F1C)),
        logoText = "prime video",
        subtitle = "Amazon Originals",
        logoImageUrl = "https://image.tmdb.org/t/p/original/ifhbNuuVnlwYy5oXA5VIb2YR8AZ.png",
    ),
    APPLE_TV(
        id = "apple",
        tmdbProviderId = 350,
        displayName = "Apple TV+",
        brandColor = Color(0xFFF5F5F7),
        gradientColors = listOf(Color(0xFF323238), Color(0xFF131316)),
        logoText = "tv+",
        subtitle = "Apple Original Films",
        logoImageUrl = "https://image.tmdb.org/t/p/original/4KAy34EHvRM25Ih8wb82AuGU7zJ.png",
    ),
    HULU(
        id = "hulu",
        tmdbProviderId = 15,
        displayName = "Hulu",
        brandColor = Color(0xFF1CE783),
        gradientColors = listOf(Color(0xFF042A18), Color(0xFF08140E)),
        logoText = "hulu",
        subtitle = "Stream TV & Exclusives",
        logoImageUrl = "https://image.tmdb.org/t/p/original/pqUTCleNUiTLAVlelGxUgWn1ELh.png",
    ),
    MAX(
        id = "max",
        tmdbProviderId = 1899,
        displayName = "Max",
        brandColor = Color(0xFF9B6CFF),
        gradientColors = listOf(Color(0xFF220C48), Color(0xFF0C0718)),
        logoText = "MAX",
        subtitle = "HBO & Warner Bros.",
        logoImageUrl = "https://image.tmdb.org/t/p/original/nmU0UMDJB3dRRQSTUqawzF2Od1a.png",
    ),
    PARAMOUNT_PLUS(
        id = "paramount",
        tmdbProviderId = 531,
        displayName = "Paramount+",
        brandColor = Color(0xFF0064FF),
        gradientColors = listOf(Color(0xFF031D4D), Color(0xFF070C1A)),
        logoText = "Paramount+",
        subtitle = "Live Sports & Movies",
        logoImageUrl = "https://image.tmdb.org/t/p/original/fi83B1oztoS47xxcemFdPMhIzK.png",
    ),
    CRUNCHYROLL(
        id = "crunchyroll",
        tmdbProviderId = 283,
        displayName = "Crunchyroll",
        brandColor = Color(0xFFF47521),
        gradientColors = listOf(Color(0xFF331302), Color(0xFF110803)),
        logoText = "crunchyroll",
        subtitle = "The Global Anime Library",
        logoImageUrl = "https://image.tmdb.org/t/p/original/qqyXcZlJQKlRmAD1TCKV7mGLQlt.png",
    );

    companion object {
        fun fromIdOrNull(id: String): StreamingPlatform? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
    }
}
