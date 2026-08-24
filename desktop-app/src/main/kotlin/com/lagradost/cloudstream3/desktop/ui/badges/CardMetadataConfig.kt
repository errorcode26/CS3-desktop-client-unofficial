package com.lagradost.cloudstream3.desktop.ui.badges

import com.lagradost.cloudstream3.desktop.ui.theme.AppearanceConfig
import com.lagradost.cloudstream3.desktop.utils.appScope
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * User preferences and policies for Poster Card metadata, title cleaning, and rating sources.
 * 100% decoupled, synchronized with AppearanceConfig.
 */
enum class RatingSourcePolicy {
    VERIFIED_ADDON, // Fetch verified rating from Cinemeta / AniList / TMDB
    SCRAPER_NATIVE, // Use raw scraper rating
    SMART_HYBRID,    // Use scraper rating if valid; otherwise fallback to Verified Addon
}

object CardMetadataConfig {
    const val KEY_AUTO_CLEAN_TITLES = "pref_card_auto_clean_titles"
    const val KEY_AUTO_DETECT_SUB_DUB = "pref_show_poster_language"
    const val KEY_AUTO_DETECT_QUALITY = "pref_show_poster_quality"
    const val KEY_SHOW_RATING_BADGES = "pref_show_poster_rating"
    const val KEY_RATING_POLICY = "pref_card_rating_source_policy"

    private val _autoCleanTitles = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(KEY_AUTO_CLEAN_TITLES) ?: true
    )
    val autoCleanTitles: StateFlow<Boolean> = _autoCleanTitles.asStateFlow()

    private val _autoDetectSubDub = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(KEY_AUTO_DETECT_SUB_DUB) ?: true
    )
    val autoDetectSubDub: StateFlow<Boolean> = _autoDetectSubDub.asStateFlow()

    private val _autoDetectQuality = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(KEY_AUTO_DETECT_QUALITY) ?: true
    )
    val autoDetectQuality: StateFlow<Boolean> = _autoDetectQuality.asStateFlow()

    private val _showRatingBadges = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(KEY_SHOW_RATING_BADGES) ?: true
    )
    val showRatingBadges: StateFlow<Boolean> = _showRatingBadges.asStateFlow()

    private val _ratingPolicy = MutableStateFlow(
        DesktopDataStore.getKey<String>(KEY_RATING_POLICY)?.let {
            try { RatingSourcePolicy.valueOf(it) } catch (_: Exception) { null }
        } ?: RatingSourcePolicy.SMART_HYBRID
    )
    val ratingPolicy: StateFlow<RatingSourcePolicy> = _ratingPolicy.asStateFlow()

    fun setAutoCleanTitles(enabled: Boolean) {
        _autoCleanTitles.value = enabled
        appScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_AUTO_CLEAN_TITLES, enabled)
        }
    }

    fun setAutoDetectSubDub(enabled: Boolean) {
        _autoDetectSubDub.value = enabled
        AppearanceConfig.setShowPosterLanguage(enabled)
        appScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_AUTO_DETECT_SUB_DUB, enabled)
        }
    }

    fun setAutoDetectQuality(enabled: Boolean) {
        _autoDetectQuality.value = enabled
        AppearanceConfig.setShowPosterQuality(enabled)
        appScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_AUTO_DETECT_QUALITY, enabled)
        }
    }

    fun setShowRatingBadges(enabled: Boolean) {
        _showRatingBadges.value = enabled
        AppearanceConfig.setShowPosterRating(enabled)
        appScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_SHOW_RATING_BADGES, enabled)
        }
    }

    fun setRatingPolicy(policy: RatingSourcePolicy) {
        _ratingPolicy.value = policy
        appScope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(KEY_RATING_POLICY, policy.name)
        }
    }
}
