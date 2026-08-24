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

    const val KEY_STREMIO_ADDON_ENABLED = "stremio_metadata_addon_enabled"
    const val KEY_STREMIO_ADDON_URL = "stremio_metadata_addon_url"

    const val KEY_ENABLE_SKIP_INTERVALS = "pref_enable_skip_intervals"
    const val KEY_AUTO_SKIP_INTRO = "pref_auto_skip_intro"
    const val KEY_AUTO_SKIP_OUTRO = "pref_auto_skip_outro"

    private val _tmdbEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_TMDB_ENABLED) ?: true)
    val tmdbEnabled: StateFlow<Boolean> = _tmdbEnabled.asStateFlow()

    private val _customTmdbApiKey = MutableStateFlow(DesktopDataStore.getKey<String>(KEY_TMDB_API_KEY) ?: "")
    val customTmdbApiKey: StateFlow<String> = _customTmdbApiKey.asStateFlow()

    private val _anilistEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_ANILIST_ENABLED) ?: true)
    val anilistEnabled: StateFlow<Boolean> = _anilistEnabled.asStateFlow()

    private val _kitsuEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(KEY_KITSU_ENABLED) ?: true)
    val kitsuEnabled: StateFlow<Boolean> = _kitsuEnabled.asStateFlow()

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

    fun isProviderEnabled(providerId: String): Boolean {
        return when (providerId) {
            "tmdb" -> _tmdbEnabled.value
            "anilist" -> _anilistEnabled.value
            "kitsu" -> _kitsuEnabled.value
            "stremio", "cinemeta" -> _stremioAddonEnabled.value && _stremioAddonUrl.value.isNotBlank()
            else -> true
        }
    }

    fun reloadFromDataStore() {
        _tmdbEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_TMDB_ENABLED) ?: true
        _customTmdbApiKey.value = DesktopDataStore.getKey<String>(KEY_TMDB_API_KEY) ?: ""
        _anilistEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_ANILIST_ENABLED) ?: true
        _kitsuEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_KITSU_ENABLED) ?: true
        _stremioAddonEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_STREMIO_ADDON_ENABLED) ?: false
        _stremioAddonUrl.value = DesktopDataStore.getKey<String>(KEY_STREMIO_ADDON_URL) ?: ""
        _skipIntervalsEnabled.value = DesktopDataStore.getKey<Boolean>(KEY_ENABLE_SKIP_INTERVALS) ?: true
        _autoSkipIntro.value = DesktopDataStore.getKey<Boolean>(KEY_AUTO_SKIP_INTRO) ?: false
        _autoSkipOutro.value = DesktopDataStore.getKey<Boolean>(KEY_AUTO_SKIP_OUTRO) ?: false
    }
}
