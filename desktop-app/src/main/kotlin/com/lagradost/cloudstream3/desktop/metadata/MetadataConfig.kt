package com.lagradost.cloudstream3.desktop.metadata

import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * User configuration and persistent preferences for Metadata, Addons, and Skip Integrations.
 */
object MetadataConfig {
    const val KEY_TMDB_ENABLED = "meta_tmdb_enabled"
    const val KEY_TMDB_API_KEY = "tmdb_api_key"
    const val KEY_ANILIST_ENABLED = "meta_anilist_enabled"
    const val KEY_KITSU_ENABLED = "meta_kitsu_enabled"

    const val KEY_TMDB_LANGUAGE = "pref_tmdb_language"
    const val KEY_TMDB_INCLUDE_IMAGE_LANG = "pref_tmdb_image_language"
    const val KEY_DETAILS_SHOW_FINANCIALS = "pref_details_show_financials"
    const val KEY_DETAILS_SEPARATE_NETWORKS = "pref_details_separate_networks"
    const val KEY_DETAILS_MAX_TRAILERS = "pref_details_max_trailers"

    const val KEY_STREMIO_ADDON_ENABLED = "stremio_metadata_addon_enabled"
    const val KEY_STREMIO_ADDON_URL = "stremio_metadata_addon_url"

    const val KEY_ENABLE_SKIP_INTERVALS = "pref_enable_skip_intervals"
    const val KEY_AUTO_SKIP_INTRO = "pref_auto_skip_intro"
    const val KEY_AUTO_SKIP_OUTRO = "pref_auto_skip_outro"
    const val KEY_ANIME_TITLE_LANGUAGE = "pref_anime_title_language"
    const val KEY_ANIME_VOICE_CAST = "pref_anime_voice_cast"
    const val KEY_ANIME_SIMULCAST = "pref_anime_simulcast_schedules"
    const val KEY_ANIME_STUDIOS = "pref_anime_studio_badges"
    const val KEY_ANIME_PRIMARY_PROVIDER = "pref_anime_primary_provider"
    const val KEY_TMDB_INCLUDE_ADULT = "pref_tmdb_include_adult"
    const val KEY_TVMAZE_ENABLED = "pref_tvmaze_enabled"

    private val _tmdbEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_TMDB_ENABLED) ?: true)
    val tmdbEnabled: StateFlow<Boolean> = _tmdbEnabled.asStateFlow()

    private val _customTmdbApiKey = MutableStateFlow(DesktopDataStore.getKey<String>(KEY_TMDB_API_KEY) ?: "")
    val customTmdbApiKey: StateFlow<String> = _customTmdbApiKey.asStateFlow()

    private val _tmdbLanguage = MutableStateFlow(DesktopDataStore.getKey<String>(KEY_TMDB_LANGUAGE) ?: "en-US")
    val tmdbLanguage: StateFlow<String> = _tmdbLanguage.asStateFlow()

    private val _tmdbImageLanguage = MutableStateFlow(DesktopDataStore.getKey<String>(KEY_TMDB_INCLUDE_IMAGE_LANG) ?: "en,en-US,null")
    val tmdbImageLanguage: StateFlow<String> = _tmdbImageLanguage.asStateFlow()

    private val _tmdbIncludeAdult = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_TMDB_INCLUDE_ADULT) ?: false)
    val tmdbIncludeAdult: StateFlow<Boolean> = _tmdbIncludeAdult.asStateFlow()

    private val _showFinancials = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_DETAILS_SHOW_FINANCIALS) ?: true)
    val showFinancials: StateFlow<Boolean> = _showFinancials.asStateFlow()

    private val _separateNetworks = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_DETAILS_SEPARATE_NETWORKS) ?: true)
    val separateNetworks: StateFlow<Boolean> = _separateNetworks.asStateFlow()

    private val _maxTrailers = MutableStateFlow(DesktopDataStore.getKey<Int>(KEY_DETAILS_MAX_TRAILERS) ?: 10)
    val maxTrailers: StateFlow<Int> = _maxTrailers.asStateFlow()

    private val _anilistEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_ANILIST_ENABLED) ?: true)
    val anilistEnabled: StateFlow<Boolean> = _anilistEnabled.asStateFlow()

    private val _kitsuEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_KITSU_ENABLED) ?: true)
    val kitsuEnabled: StateFlow<Boolean> = _kitsuEnabled.asStateFlow()

    private val _tvmazeEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_TVMAZE_ENABLED) ?: true)
    val tvmazeEnabled: StateFlow<Boolean> = _tvmazeEnabled.asStateFlow()

    private val _animeTitleLanguage = MutableStateFlow(DesktopDataStore.getKey<String>(KEY_ANIME_TITLE_LANGUAGE) ?: "romaji")
    val animeTitleLanguage: StateFlow<String> = _animeTitleLanguage.asStateFlow()

    private val _animeVoiceCast = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_ANIME_VOICE_CAST) ?: true)
    val animeVoiceCast: StateFlow<Boolean> = _animeVoiceCast.asStateFlow()

    private val _animeSimulcast = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_ANIME_SIMULCAST) ?: true)
    val animeSimulcast: StateFlow<Boolean> = _animeSimulcast.asStateFlow()

    private val _animeStudios = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_ANIME_STUDIOS) ?: true)
    val animeStudios: StateFlow<Boolean> = _animeStudios.asStateFlow()

    private val _animePrimaryProvider = MutableStateFlow(DesktopDataStore.getKey<String>(KEY_ANIME_PRIMARY_PROVIDER) ?: "anilist")
    val animePrimaryProvider: StateFlow<String> = _animePrimaryProvider.asStateFlow()

    private val _stremioAddonEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_STREMIO_ADDON_ENABLED) ?: false)
    val stremioAddonEnabled: StateFlow<Boolean> = _stremioAddonEnabled.asStateFlow()

    private val _stremioAddonUrl = MutableStateFlow(DesktopDataStore.getKey<String>(KEY_STREMIO_ADDON_URL) ?: "")
    val stremioAddonUrl: StateFlow<String> = _stremioAddonUrl.asStateFlow()

    private val _skipIntervalsEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_ENABLE_SKIP_INTERVALS) ?: true)
    val skipIntervalsEnabled: StateFlow<Boolean> = _skipIntervalsEnabled.asStateFlow()

    private val _autoSkipIntro = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_AUTO_SKIP_INTRO) ?: false)
    val autoSkipIntro: StateFlow<Boolean> = _autoSkipIntro.asStateFlow()

    private val _autoSkipOutro = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_AUTO_SKIP_OUTRO) ?: false)
    val autoSkipOutro: StateFlow<Boolean> = _autoSkipOutro.asStateFlow()

    fun setTmdbEnabled(enabled: Boolean) {
        _tmdbEnabled.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_TMDB_ENABLED, enabled)
        }
    }

    fun setCustomTmdbApiKey(key: String) {
        val trimmed = key.trim()
        _customTmdbApiKey.value = trimmed
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_TMDB_API_KEY, trimmed)
        }
    }

    fun setAniListEnabled(enabled: Boolean) {
        _anilistEnabled.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_ANILIST_ENABLED, enabled)
        }
    }

    fun setKitsuEnabled(enabled: Boolean) {
        _kitsuEnabled.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_KITSU_ENABLED, enabled)
        }
    }

    fun setStremioAddonEnabled(enabled: Boolean) {
        _stremioAddonEnabled.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_STREMIO_ADDON_ENABLED, enabled)
        }
    }

    fun setStremioAddonUrl(url: String) {
        val trimmed = url.trim()
        _stremioAddonUrl.value = trimmed
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_STREMIO_ADDON_URL, trimmed)
        }
    }

    fun setSkipIntervalsEnabled(enabled: Boolean) {
        _skipIntervalsEnabled.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_ENABLE_SKIP_INTERVALS, enabled)
        }
    }

    fun setAutoSkipIntro(enabled: Boolean) {
        _autoSkipIntro.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_AUTO_SKIP_INTRO, enabled)
        }
    }

    fun setAutoSkipOutro(enabled: Boolean) {
        _autoSkipOutro.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_AUTO_SKIP_OUTRO, enabled)
        }
    }

    fun setTmdbLanguage(lang: String) {
        val trimmed = lang.trim().ifBlank { "en-US" }
        _tmdbLanguage.value = trimmed
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_TMDB_LANGUAGE, trimmed)
        }
    }

    fun setTmdbImageLanguage(imageLang: String) {
        val trimmed = imageLang.trim().ifBlank { "en,en-US,null" }
        _tmdbImageLanguage.value = trimmed
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_TMDB_INCLUDE_IMAGE_LANG, trimmed)
        }
    }

    fun setShowFinancials(enabled: Boolean) {
        _showFinancials.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_DETAILS_SHOW_FINANCIALS, enabled)
        }
    }

    fun setSeparateNetworks(enabled: Boolean) {
        _separateNetworks.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_DETAILS_SEPARATE_NETWORKS, enabled)
        }
    }

    fun setMaxTrailers(max: Int) {
        val coerced = max.coerceIn(3, 50)
        _maxTrailers.value = coerced
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_DETAILS_MAX_TRAILERS, coerced)
        }
    }

    fun setTmdbIncludeAdult(enabled: Boolean) {
        _tmdbIncludeAdult.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_TMDB_INCLUDE_ADULT, enabled)
        }
    }

    fun setTvmazeEnabled(enabled: Boolean) {
        _tvmazeEnabled.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_TVMAZE_ENABLED, enabled)
        }
    }

    fun setAnimeTitleLanguage(lang: String) {
        val normalized = lang.trim().lowercase().ifBlank { "romaji" }
        _animeTitleLanguage.value = normalized
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_ANIME_TITLE_LANGUAGE, normalized)
        }
    }

    fun setAnimeVoiceCast(enabled: Boolean) {
        _animeVoiceCast.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_ANIME_VOICE_CAST, enabled)
        }
    }

    fun setAnimeSimulcast(enabled: Boolean) {
        _animeSimulcast.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_ANIME_SIMULCAST, enabled)
        }
    }

    fun setAnimeStudios(enabled: Boolean) {
        _animeStudios.value = enabled
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_ANIME_STUDIOS, enabled)
        }
    }

    fun setAnimePrimaryProvider(provider: String) {
        val normalized = provider.trim().lowercase().ifBlank { "anilist" }
        _animePrimaryProvider.value = normalized
        com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_ANIME_PRIMARY_PROVIDER, normalized)
        }
    }

    fun isProviderEnabled(providerId: String): Boolean {
        return when (providerId) {
            "tmdb" -> _tmdbEnabled.value
            "anilist" -> _anilistEnabled.value
            "kitsu" -> _kitsuEnabled.value
            "tvmaze" -> _tvmazeEnabled.value
            "stremio", "cinemeta" -> _stremioAddonEnabled.value && _stremioAddonUrl.value.isNotBlank()
            else -> true
        }
    }

    fun reloadFromDataStore() {
        _tmdbEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_TMDB_ENABLED) ?: true
        _customTmdbApiKey.value = DesktopDataStore.getKey<String>(KEY_TMDB_API_KEY) ?: ""
        _tmdbLanguage.value = DesktopDataStore.getKey<String>(KEY_TMDB_LANGUAGE) ?: "en-US"
        _tmdbImageLanguage.value = DesktopDataStore.getKey<String>(KEY_TMDB_INCLUDE_IMAGE_LANG) ?: "en,en-US,null"
        _tmdbIncludeAdult.value = DesktopDataStore.getKey<Boolean>(KEY_TMDB_INCLUDE_ADULT) ?: false
        _showFinancials.value = DesktopDataStore.getKey<Boolean>(KEY_DETAILS_SHOW_FINANCIALS) ?: true
        _separateNetworks.value = DesktopDataStore.getKey<Boolean>(KEY_DETAILS_SEPARATE_NETWORKS) ?: true
        _maxTrailers.value = DesktopDataStore.getKey<Int>(KEY_DETAILS_MAX_TRAILERS) ?: 10
        _anilistEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_ANILIST_ENABLED) ?: true
        _kitsuEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_KITSU_ENABLED) ?: true
        _tvmazeEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_TVMAZE_ENABLED) ?: true
        _animeTitleLanguage.value = DesktopDataStore.getKey<String>(KEY_ANIME_TITLE_LANGUAGE) ?: "romaji"
        _animeVoiceCast.value = DesktopDataStore.getKey<Boolean>(KEY_ANIME_VOICE_CAST) ?: true
        _animeSimulcast.value = DesktopDataStore.getKey<Boolean>(KEY_ANIME_SIMULCAST) ?: true
        _animeStudios.value = DesktopDataStore.getKey<Boolean>(KEY_ANIME_STUDIOS) ?: true
        _animePrimaryProvider.value = DesktopDataStore.getKey<String>(KEY_ANIME_PRIMARY_PROVIDER) ?: "anilist"
        _stremioAddonEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_STREMIO_ADDON_ENABLED) ?: false
        _stremioAddonUrl.value = DesktopDataStore.getKey<String>(KEY_STREMIO_ADDON_URL) ?: ""
        _skipIntervalsEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_ENABLE_SKIP_INTERVALS) ?: true
        _autoSkipIntro.value = DesktopDataStore.getKey<Boolean>(KEY_AUTO_SKIP_INTRO) ?: false
        _autoSkipOutro.value = DesktopDataStore.getKey<Boolean>(KEY_AUTO_SKIP_OUTRO) ?: false
    }
}
