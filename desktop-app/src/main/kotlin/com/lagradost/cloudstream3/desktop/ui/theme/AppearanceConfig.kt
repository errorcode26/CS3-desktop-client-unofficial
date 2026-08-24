package com.lagradost.cloudstream3.desktop.ui.theme

import com.lagradost.cloudstream3.desktop.ui.DockPosition
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PosterTitlePosition {
    INSIDE,
    BELOW,
    HIDDEN,
    ;

    companion object {
        fun fromString(value: String?): PosterTitlePosition {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: INSIDE
        }
    }
}

enum class ContinueWatchingStyle {
    THUMBNAIL,
    PREMIUM,
    ;

    companion object {
        fun fromString(value: String?): ContinueWatchingStyle {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: THUMBNAIL
        }
    }
}

enum class ClockDisplayMode {
    HIDDEN,
    TIME_ONLY,
    DATE_ONLY,
    BOTH,
    ;

    companion object {
        fun fromString(v: String?) = entries.find { it.name.equals(v, ignoreCase = true) } ?: HIDDEN
    }
}

enum class DockItemKey(
    val id: String,
    val displayName: String,
    val description: String,
    val isRequired: Boolean = false,
) {
    HOME("home", "Home", "Main landing page with hero banner & catalogs", isRequired = true),
    SEARCH("search", "Search", "Global search & provider explorer"),
    LIBRARY("library", "Library", "Bookmarked shows, movies, and custom lists"),
    SETTINGS("settings", "Settings", "Preferences, appearance, player, and plugins", isRequired = true),
    HISTORY("history", "Watch History", "Recently watched episodes & resume points"),
    EXTENSIONS("extensions", "Extensions", "Installed plugins, repos, and updates"),
    ;

    companion object {
        val DEFAULT_ORDER = listOf(HOME, SEARCH, LIBRARY, SETTINGS, HISTORY, EXTENSIONS)
        val DEFAULT_DISABLED = setOf(HISTORY, EXTENSIONS)

        fun parseOrder(raw: String?): List<DockItemKey> {
            if (raw.isNullOrBlank()) return DEFAULT_ORDER
            val parsed = raw.split(",").mapNotNull { id -> entries.find { it.id.equals(id.trim(), ignoreCase = true) } }
            val missing = entries.filter { it !in parsed }
            return parsed + missing
        }

        fun parseDisabled(raw: String?): Set<DockItemKey> {
            if (raw == null) return DEFAULT_DISABLED
            if (raw.isBlank() || raw.equals("NONE", ignoreCase = true)) return emptySet()
            return raw.split(",")
                .mapNotNull { id -> entries.find { it.id.equals(id.trim(), ignoreCase = true) } }
                .filter { !it.isRequired }
                .toSet()
        }

        fun serialize(items: Iterable<DockItemKey>): String {
            val list = items.filter { !it.isRequired }
            return if (list.isEmpty()) "NONE" else list.joinToString(",") { it.id }
        }
    }
}

enum class NavigationStyle(val label: String) {
    FLOATING_DOCK("Floating Dock"),
    SEAMLESS_BAR("Navigation Bar"),
    ;

    companion object {
        fun fromString(v: String?) = entries.find { it.name.equals(v, ignoreCase = true) || it.label.equals(v, ignoreCase = true) } ?: FLOATING_DOCK
    }
}

object AppearanceConfig {
    private const val PREF_GLOBAL_UI_SCALE = "pref_global_ui_scale"
    private const val PREF_NAVIGATION_STYLE = "pref_navigation_style"
    private const val PREF_THEME_ACCENT = "pref_theme_accent"
    private const val PREF_AMOLED_MODE = "pref_amoled_mode"
    private const val PREF_LIGHT_MODE = "pref_light_mode"
    private const val PREF_GRID_SCALE = "pref_grid_scale"
    private const val PREF_AMBIENT_GLOW = "pref_ambient_glow"
    private const val PREF_AMBIENT_GLOW_INTENSITY = "pref_ambient_glow_intensity"
    private const val PREF_AMBIENT_GLOW_POSITION = "pref_ambient_glow_position"
    private const val PREF_HERO_BACKGROUND_BLUR = "pref_hero_background_blur"
    private const val PREF_HERO_BACKDROP_BLUR_RADIUS = "pref_hero_backdrop_blur_radius"
    private const val PREF_HERO_BACKDROP_DARKENING = "pref_hero_backdrop_darkening"
    private const val PREF_DOCK_POSITION = "pref_dock_position"
    private const val PREF_FONT = "pref_font"
    private const val PREF_SCREENSAVER_ENABLED = "pref_screensaver_enabled"
    private const val PREF_HERO_AUTO_SLIDE_DELAY = "pref_hero_auto_slide_delay"
    private const val PREF_CONTINUE_WATCHING_STYLE = "pref_continue_watching_style"
    private const val PREF_POSTER_HOVER_GLOW_ENABLED = "pref_poster_hover_glow_enabled"
    private const val PREF_POSTER_TITLE_POSITION = "pref_poster_title_position"
    private const val PREF_HOME_SPACING_DP = "pref_home_spacing_dp"
    private const val PREF_HOME_VERTICAL_SPACING_DP = "pref_home_vertical_spacing_dp"
    private const val PREF_POSTER_WIDTH = "pref_poster_width"
    private const val PREF_POSTER_ROUNDING = "pref_poster_rounding"
    private const val PREF_CUSTOM_THEME_ACCENT = "pref_custom_theme_accent"
    private const val PREF_APP_THEME_BACKGROUND = "pref_app_theme_background"
    private const val PREF_CUSTOM_APP_THEME_BACKGROUND = "pref_custom_app_theme_background"
    private const val PREF_HERO_ENABLED = "pref_hero_enabled"
    private const val PREF_SHOW_POSTER_RATING = "pref_show_poster_rating"
    private const val PREF_SHOW_POSTER_QUALITY = "pref_show_poster_quality"
    private const val PREF_SHOW_POSTER_LANGUAGE = "pref_show_poster_language"
    private const val PREF_TEXT_DROP_SHADOW_ENABLED = "pref_text_drop_shadow_enabled"
    private const val PREF_TEXT_DROP_SHADOW_BLUR = "pref_text_drop_shadow_blur"
    private const val PREF_ELEMENT_SHADOWS_ENABLED = "pref_element_shadows_enabled"
    private const val PREF_ELEMENT_SHADOW_MULTIPLIER = "pref_element_shadow_multiplier"
    private const val PREF_APP_PRESET_THEME = "pref_app_preset_theme"
    private const val PREF_BACKGROUND_GRADIENT_ENABLED = "pref_background_gradient_enabled"
    private const val PREF_BACKGROUND_GRADIENT_TYPE = "pref_background_gradient_type"
    private const val PREF_BACKGROUND_GRADIENT_INTENSITY = "pref_background_gradient_intensity"
    private const val PREF_CUSTOM_PRESETS = "pref_custom_presets"
    private const val PREF_CLOCK_MODE = "pref_clock_mode"
    private const val PREF_CLOCK_TIME_FORMAT = "pref_clock_time_format"
    private const val PREF_CLOCK_DATE_FORMAT = "pref_clock_date_format"
    private const val PREF_BG_IMAGE_PATH = "pref_bg_image_path"
    private const val PREF_BG_IMAGE_BLUR = "pref_bg_image_blur"
    private const val PREF_BG_IMAGE_BRIGHTNESS = "pref_bg_image_brightness"
    private const val PREF_BG_IMAGE_OPACITY = "pref_bg_image_opacity"
    private const val PREF_BG_IMAGE_SATURATION = "pref_bg_image_saturation"
    private const val PREF_BG_IMAGE_VIGNETTE = "pref_bg_image_vignette"
    private const val PREF_BG_IMAGE_VIGNETTE_INTENSITY = "pref_bg_image_vignette_intensity"
    private const val PREF_BG_IMAGE_TINT_ENABLED = "pref_bg_image_tint_enabled"
    private const val PREF_BG_IMAGE_TINT_COLOR = "pref_bg_image_tint_color"
    private const val PREF_BG_IMAGE_TINT_ALPHA = "pref_bg_image_tint_alpha"
    private const val PREF_ANTI_SPOILER_ENABLED = "pref_anti_spoiler_enabled"
    private const val PREF_UI_CARD_OPACITY = "pref_ui_card_opacity"
    private const val PREF_DETAILS_SHOW_CURRENT_TIME = "pref_details_show_current_time"
    private const val PREF_DETAILS_SHOW_END_TIME = "pref_details_show_end_time"
    private const val PREF_LOCK_UNRELEASED_EPISODES = "pref_lock_unreleased_episodes"
    private const val PREF_DETAILS_SECTION_ORDER = "pref_details_section_order"
    private const val PREF_DETAILS_DISABLED_SECTIONS = "pref_details_disabled_sections"
    private const val PREF_DOCK_ITEM_ORDER = "pref_dock_item_order"
    private const val PREF_DOCK_DISABLED_ITEMS = "pref_dock_disabled_items"
    private const val PREF_TOPBAR_SHOW_PROFILE = "pref_topbar_show_profile"
    private const val PREF_TOPBAR_SHOW_PROFILE_NAME = "pref_topbar_show_profile_name"

    private val _dockItemOrder = MutableStateFlow(
        DockItemKey.parseOrder(DesktopDataStore.getKey<String>(PREF_DOCK_ITEM_ORDER))
    )
    val dockItemOrder: StateFlow<List<DockItemKey>> = _dockItemOrder.asStateFlow()

    private val _dockDisabledItems = MutableStateFlow(
        DockItemKey.parseDisabled(DesktopDataStore.getKey<String>(PREF_DOCK_DISABLED_ITEMS))
    )
    val dockDisabledItems: StateFlow<Set<DockItemKey>> = _dockDisabledItems.asStateFlow()

    private val _topBarShowProfile = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(PREF_TOPBAR_SHOW_PROFILE) ?: true
    )
    val topBarShowProfile: StateFlow<Boolean> = _topBarShowProfile.asStateFlow()

    private val _topBarShowProfileName = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(PREF_TOPBAR_SHOW_PROFILE_NAME) ?: true
    )
    val topBarShowProfileName: StateFlow<Boolean> = _topBarShowProfileName.asStateFlow()

    private val _themeAccent = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_THEME_ACCENT) ?: "Purple")
    val themeAccent: StateFlow<String> = _themeAccent.asStateFlow()
    private val _antiSpoilerEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_ANTI_SPOILER_ENABLED) ?: true)
    val antiSpoilerEnabled: StateFlow<Boolean> = _antiSpoilerEnabled.asStateFlow()
    private val _lockUnreleasedEpisodes = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_LOCK_UNRELEASED_EPISODES) ?: true)
    val lockUnreleasedEpisodes: StateFlow<Boolean> = _lockUnreleasedEpisodes.asStateFlow()
    private val _detailsShowCurrentTime = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_CURRENT_TIME) ?: true)
    val detailsShowCurrentTime: StateFlow<Boolean> = _detailsShowCurrentTime.asStateFlow()
    private val _detailsShowEndTime = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_END_TIME) ?: true)
    val detailsShowEndTime: StateFlow<Boolean> = _detailsShowEndTime.asStateFlow()
    private val _detailsSectionOrder = MutableStateFlow(
        com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseOrder(
            DesktopDataStore.getKey<String>(PREF_DETAILS_SECTION_ORDER)
        )
    )
    val detailsSectionOrder: StateFlow<List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey>> = _detailsSectionOrder.asStateFlow()
    private val _detailsDisabledSections = MutableStateFlow(
        com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseDisabled(
            DesktopDataStore.getKey<String>(PREF_DETAILS_DISABLED_SECTIONS)
        )
    )
    val detailsDisabledSections: StateFlow<Set<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey>> = _detailsDisabledSections.asStateFlow()
    private val _amoledMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMOLED_MODE) ?: false)
    val amoledMode: StateFlow<Boolean> = _amoledMode.asStateFlow()
    private val _isLightMode = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_LIGHT_MODE) ?: false)
    val isLightMode: StateFlow<Boolean> = _isLightMode.asStateFlow()
    private val _gridScale = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_GRID_SCALE) ?: "Normal")
    val gridScale: StateFlow<String> = _gridScale.asStateFlow()
    private val _ambientGlowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_AMBIENT_GLOW) ?: true)
    val ambientGlowEnabled: StateFlow<Boolean> = _ambientGlowEnabled.asStateFlow()
    private val _ambientGlowIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_AMBIENT_GLOW_INTENSITY) ?: 0.15f)
    val ambientGlowIntensity: StateFlow<Float> = _ambientGlowIntensity.asStateFlow()
    private val _ambientGlowPositions = MutableStateFlow((DesktopDataStore.getKey<String>(PREF_AMBIENT_GLOW_POSITION) ?: "Center").split(",").filter { it.isNotBlank() }.toSet())
    val ambientGlowPositions: StateFlow<Set<String>> = _ambientGlowPositions.asStateFlow()
    private val _heroBackgroundBlurEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_HERO_BACKGROUND_BLUR) ?: true)
    val heroBackgroundBlurEnabled: StateFlow<Boolean> = _heroBackgroundBlurEnabled.asStateFlow()
    private val _heroBackdropBlurRadius = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_BLUR_RADIUS) ?: 80f)
    val heroBackdropBlurRadius: StateFlow<Float> = _heroBackdropBlurRadius.asStateFlow()
    private val _heroBackdropDarkening = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_DARKENING) ?: 0.65f)
    val heroBackdropDarkening: StateFlow<Float> = _heroBackdropDarkening.asStateFlow()
    private val _globalUiScale = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_GLOBAL_UI_SCALE) ?: 1.0f)
    val globalUiScale: StateFlow<Float> = _globalUiScale.asStateFlow()
    private val _navigationStyle = MutableStateFlow(NavigationStyle.fromString(DesktopDataStore.getKey<String>(PREF_NAVIGATION_STYLE)))
    val navigationStyle: StateFlow<NavigationStyle> = _navigationStyle.asStateFlow()
    private val _dockPosition = MutableStateFlow(DockPosition.fromString(DesktopDataStore.getKey<String>(PREF_DOCK_POSITION) ?: "Left"))
    val dockPosition: StateFlow<DockPosition> = _dockPosition.asStateFlow()
    private val _selectedFont = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_FONT) ?: "Inter")
    val selectedFont: StateFlow<String> = _selectedFont.asStateFlow()
    private val _screensaverEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SCREENSAVER_ENABLED) ?: true)
    val screensaverEnabled: StateFlow<Boolean> = _screensaverEnabled.asStateFlow()
    private val _heroAutoSlideDelaySeconds = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HERO_AUTO_SLIDE_DELAY) ?: 10)
    val heroAutoSlideDelaySeconds: StateFlow<Int> = _heroAutoSlideDelaySeconds.asStateFlow()
    private val _continueWatchingStyle = MutableStateFlow(ContinueWatchingStyle.fromString(DesktopDataStore.getKey<String>(PREF_CONTINUE_WATCHING_STYLE)))
    val continueWatchingStyle: StateFlow<ContinueWatchingStyle> = _continueWatchingStyle.asStateFlow()
    private val _posterHoverGlowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_POSTER_HOVER_GLOW_ENABLED) ?: true)
    val posterHoverGlowEnabled: StateFlow<Boolean> = _posterHoverGlowEnabled.asStateFlow()
    private val _posterTitlePosition = MutableStateFlow(PosterTitlePosition.fromString(DesktopDataStore.getKey<String>(PREF_POSTER_TITLE_POSITION)))
    val posterTitlePosition: StateFlow<PosterTitlePosition> = _posterTitlePosition.asStateFlow()
    private val _homeSpacingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HOME_SPACING_DP) ?: 12)
    val homeSpacingDp: StateFlow<Int> = _homeSpacingDp.asStateFlow()
    private val _homeVerticalSpacingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_HOME_VERTICAL_SPACING_DP) ?: 0)
    val homeVerticalSpacingDp: StateFlow<Int> = _homeVerticalSpacingDp.asStateFlow()
    private val _posterWidthDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_POSTER_WIDTH) ?: 190)
    val posterWidthDp: StateFlow<Int> = _posterWidthDp.asStateFlow()
    private val _posterRoundingDp = MutableStateFlow(DesktopDataStore.getKey<Int>(PREF_POSTER_ROUNDING) ?: 12)
    val posterRoundingDp: StateFlow<Int> = _posterRoundingDp.asStateFlow()
    private val _customThemeAccent = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CUSTOM_THEME_ACCENT) ?: "#7C6BFF")
    val customThemeAccent: StateFlow<String> = _customThemeAccent.asStateFlow()
    private val _appThemeBackground = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_APP_THEME_BACKGROUND) ?: "Navy")
    val appThemeBackground: StateFlow<String> = _appThemeBackground.asStateFlow()
    private val _customAppThemeBackground = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CUSTOM_APP_THEME_BACKGROUND) ?: "#0C0C16")
    val customAppThemeBackground: StateFlow<String> = _customAppThemeBackground.asStateFlow()
    private val _heroEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_HERO_ENABLED) ?: true)
    val heroEnabled: StateFlow<Boolean> = _heroEnabled.asStateFlow()
    private val _showPosterRating = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_RATING) ?: true)
    val showPosterRating: StateFlow<Boolean> = _showPosterRating.asStateFlow()
    private val _showPosterQuality = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_QUALITY) ?: true)
    val showPosterQuality: StateFlow<Boolean> = _showPosterQuality.asStateFlow()
    private val _showPosterLanguage = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_LANGUAGE) ?: true)
    val showPosterLanguage: StateFlow<Boolean> = _showPosterLanguage.asStateFlow()
    private val _textDropShadowEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_TEXT_DROP_SHADOW_ENABLED) ?: true)
    val textDropShadowEnabled: StateFlow<Boolean> = _textDropShadowEnabled.asStateFlow()
    private val _textDropShadowBlur = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_TEXT_DROP_SHADOW_BLUR) ?: 8f)
    val textDropShadowBlur: StateFlow<Float> = _textDropShadowBlur.asStateFlow()
    private val _elementShadowsEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_ELEMENT_SHADOWS_ENABLED) ?: true)
    val elementShadowsEnabled: StateFlow<Boolean> = _elementShadowsEnabled.asStateFlow()
    private val _elementShadowMultiplier = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_ELEMENT_SHADOW_MULTIPLIER) ?: 1.0f)
    val elementShadowMultiplier: StateFlow<Float> = _elementShadowMultiplier.asStateFlow()
    private val _appPresetTheme = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_APP_PRESET_THEME) ?: "preset_cyberpunk")
    val appPresetTheme: StateFlow<String> = _appPresetTheme.asStateFlow()
    private val _backgroundGradientEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BACKGROUND_GRADIENT_ENABLED) ?: true)
    val backgroundGradientEnabled: StateFlow<Boolean> = _backgroundGradientEnabled.asStateFlow()
    private val _backgroundGradientType = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BACKGROUND_GRADIENT_TYPE) ?: "Radial")
    val backgroundGradientType: StateFlow<String> = _backgroundGradientType.asStateFlow()
    private val _backgroundGradientIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BACKGROUND_GRADIENT_INTENSITY) ?: 0.5f)
    val backgroundGradientIntensity: StateFlow<Float> = _backgroundGradientIntensity.asStateFlow()
    private val _clockMode = MutableStateFlow(ClockDisplayMode.fromString(DesktopDataStore.getKey<String>(PREF_CLOCK_MODE)))
    val clockMode: StateFlow<ClockDisplayMode> = _clockMode.asStateFlow()
    private val _clockTimeFormat = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CLOCK_TIME_FORMAT) ?: "HH:mm")
    val clockTimeFormat: StateFlow<String> = _clockTimeFormat.asStateFlow()
    private val _clockDateFormat = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_CLOCK_DATE_FORMAT) ?: "EEE, dd MMM")
    val clockDateFormat: StateFlow<String> = _clockDateFormat.asStateFlow()
    private val _backgroundImagePath = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BG_IMAGE_PATH) ?: "")
    val backgroundImagePath: StateFlow<String> = _backgroundImagePath.asStateFlow()
    private val _backgroundImageBlur = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BLUR) ?: 20f)
    val backgroundImageBlur: StateFlow<Float> = _backgroundImageBlur.asStateFlow()
    private val _backgroundImageBrightness = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BRIGHTNESS) ?: 0.35f)
    val backgroundImageBrightness: StateFlow<Float> = _backgroundImageBrightness.asStateFlow()
    private val _backgroundImageOpacity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_OPACITY) ?: 1.0f)
    val backgroundImageOpacity: StateFlow<Float> = _backgroundImageOpacity.asStateFlow()
    private val _backgroundImageSaturation = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_SATURATION) ?: 1.0f)
    val backgroundImageSaturation: StateFlow<Float> = _backgroundImageSaturation.asStateFlow()
    private val _backgroundImageVignetteEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_VIGNETTE) ?: false)
    val backgroundImageVignetteEnabled: StateFlow<Boolean> = _backgroundImageVignetteEnabled.asStateFlow()
    private val _backgroundImageVignetteIntensity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_VIGNETTE_INTENSITY) ?: 0.7f)
    val backgroundImageVignetteIntensity: StateFlow<Float> = _backgroundImageVignetteIntensity.asStateFlow()
    private val _backgroundImageTintEnabled = MutableStateFlow(DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_TINT_ENABLED) ?: false)
    val backgroundImageTintEnabled: StateFlow<Boolean> = _backgroundImageTintEnabled.asStateFlow()
    private val _backgroundImageTintColor = MutableStateFlow(DesktopDataStore.getKey<String>(PREF_BG_IMAGE_TINT_COLOR) ?: "#7C6BFF")
    val backgroundImageTintColor: StateFlow<String> = _backgroundImageTintColor.asStateFlow()
    private val _backgroundImageTintAlpha = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_TINT_ALPHA) ?: 0.3f)
    val backgroundImageTintAlpha: StateFlow<Float> = _backgroundImageTintAlpha.asStateFlow()
    private val _uiCardOpacity = MutableStateFlow(DesktopDataStore.getKey<Float>(PREF_UI_CARD_OPACITY) ?: 0.4f)
    val uiCardOpacity: StateFlow<Float> = _uiCardOpacity.asStateFlow()
    private val customPresetsJson = DesktopDataStore.getKey<String>(PREF_CUSTOM_PRESETS) ?: "[]"
    private val _customPresets = MutableStateFlow(
        try {
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(customPresetsJson, object : com.fasterxml.jackson.core.type.TypeReference<List<ThemePreset>>() {})
        } catch (e: Exception) {
            emptyList()
        },
    )
    val customPresets: StateFlow<List<ThemePreset>> = _customPresets.asStateFlow()

    fun setThemeAccent(colorName: String) {
        _themeAccent.value = colorName
        DesktopDataStore.setKey(PREF_THEME_ACCENT, colorName)
    }

    fun setAntiSpoilerEnabled(enabled: Boolean) {
        _antiSpoilerEnabled.value = enabled
        DesktopDataStore.setKey(PREF_ANTI_SPOILER_ENABLED, enabled)
    }

    fun setAmoledMode(enabled: Boolean) {
        _amoledMode.value = enabled
        DesktopDataStore.setKey(PREF_AMOLED_MODE, enabled)
    }

    fun setLightMode(enabled: Boolean) {
        if (_isLightMode.value == enabled) return
        _isLightMode.value = enabled
        DesktopDataStore.setKey(PREF_LIGHT_MODE, enabled)

        val currentPreset = (com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets.presets + _customPresets.value).find { it.id == _appPresetTheme.value }
        if (currentPreset != null && currentPreset.isLightMode != enabled) {
            val defaultPreset = com.lagradost.cloudstream3.desktop.ui.theme.BuiltInPresets.presets.firstOrNull { it.isLightMode == enabled }
            if (defaultPreset != null) {
                applyPreset(defaultPreset)
            }
        }
    }

    fun setGridScale(scale: String) {
        _gridScale.value = scale
        DesktopDataStore.setKey(PREF_GRID_SCALE, scale)
    }

    fun setAmbientGlowEnabled(enabled: Boolean) {
        _ambientGlowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW, enabled)
    }

    fun setAmbientGlowIntensity(intensity: Float) {
        _ambientGlowIntensity.value = intensity
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW_INTENSITY, intensity)
    }

    fun toggleAmbientGlowPosition(position: String) {
        val current = _ambientGlowPositions.value.toMutableSet()
        if (current.contains(position)) {
            current.remove(position)
        } else {
            current.add(position)
        }
        if (current.isEmpty()) current.add("Center")
        _ambientGlowPositions.value = current
        DesktopDataStore.setKey(PREF_AMBIENT_GLOW_POSITION, current.joinToString(","))
    }

    fun setHeroBackgroundBlurEnabled(enabled: Boolean) {
        _heroBackgroundBlurEnabled.value = enabled
        DesktopDataStore.setKey(PREF_HERO_BACKGROUND_BLUR, enabled)
    }

    fun setHeroBackdropBlurRadius(radius: Float) {
        _heroBackdropBlurRadius.value = radius
        DesktopDataStore.setKey(PREF_HERO_BACKDROP_BLUR_RADIUS, radius)
    }

    fun setHeroBackdropDarkening(darkening: Float) {
        _heroBackdropDarkening.value = darkening
        DesktopDataStore.setKey(PREF_HERO_BACKDROP_DARKENING, darkening)
    }

    fun setGlobalUiScale(scale: Float, notify: Boolean = true) {
        val clamped = (scale.coerceIn(0.70f, 1.80f) * 100).toInt() / 100f
        if (kotlin.math.abs(_globalUiScale.value - clamped) < 0.001f) return
        _globalUiScale.value = clamped
        DesktopDataStore.setKey(PREF_GLOBAL_UI_SCALE, clamped)
        if (notify) {
            val percent = (clamped * 100).toInt()
            com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showToast(
                text = if (percent == 100) "UI Scale: 100% (Default)" else "UI Scale: $percent%",
                durationMs = 1500L,
            )
        }
    }

    fun zoomIn() {
        setGlobalUiScale(_globalUiScale.value + 0.10f, notify = true)
    }

    fun zoomOut() {
        setGlobalUiScale(_globalUiScale.value - 0.10f, notify = true)
    }

    fun resetZoom() {
        setGlobalUiScale(1.0f, notify = true)
    }

    fun setNavigationStyle(style: NavigationStyle) {
        _navigationStyle.value = style
        DesktopDataStore.setKey(PREF_NAVIGATION_STYLE, style.name)
    }

    fun setDockPosition(position: DockPosition) {
        _dockPosition.value = position
        DesktopDataStore.setKey(PREF_DOCK_POSITION, position.label)
    }

    fun setSelectedFont(font: String) {
        _selectedFont.value = font
        DesktopDataStore.setKey(PREF_FONT, font)
    }

    fun setScreensaverEnabled(enabled: Boolean) {
        _screensaverEnabled.value = enabled
        DesktopDataStore.setKey(PREF_SCREENSAVER_ENABLED, enabled)
    }

    fun setHeroAutoSlideDelaySeconds(seconds: Int) {
        _heroAutoSlideDelaySeconds.value = seconds
        DesktopDataStore.setKey(PREF_HERO_AUTO_SLIDE_DELAY, seconds)
    }

    fun setHomeSpacingDp(dp: Int) {
        _homeSpacingDp.value = dp
        DesktopDataStore.setKey(PREF_HOME_SPACING_DP, dp)
    }

    fun setHomeVerticalSpacingDp(dp: Int) {
        _homeVerticalSpacingDp.value = dp
        DesktopDataStore.setKey(PREF_HOME_VERTICAL_SPACING_DP, dp)
    }

    fun setPosterWidthDp(width: Int) {
        _posterWidthDp.value = width
        DesktopDataStore.setKey(PREF_POSTER_WIDTH, width)
    }

    fun setPosterRoundingDp(dp: Int) {
        _posterRoundingDp.value = dp
        DesktopDataStore.setKey(PREF_POSTER_ROUNDING, dp)
    }

    fun setCustomThemeAccent(hex: String) {
        _customThemeAccent.value = hex
        DesktopDataStore.setKey(PREF_CUSTOM_THEME_ACCENT, hex)
    }

    fun setAppThemeBackground(themeName: String) {
        _appThemeBackground.value = themeName
        DesktopDataStore.setKey(PREF_APP_THEME_BACKGROUND, themeName)
    }

    fun setCustomAppThemeBackground(hex: String) {
        _customAppThemeBackground.value = hex
        DesktopDataStore.setKey(PREF_CUSTOM_APP_THEME_BACKGROUND, hex)
    }

    fun setHeroEnabled(enabled: Boolean) {
        _heroEnabled.value = enabled
        DesktopDataStore.setKey(PREF_HERO_ENABLED, enabled)
    }

    fun setContinueWatchingStyle(style: ContinueWatchingStyle) {
        _continueWatchingStyle.value = style
        DesktopDataStore.setKey(PREF_CONTINUE_WATCHING_STYLE, style.name)
    }

    fun setPosterHoverGlowEnabled(enabled: Boolean) {
        _posterHoverGlowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_POSTER_HOVER_GLOW_ENABLED, enabled)
    }

    fun setPosterTitlePosition(position: PosterTitlePosition) {
        _posterTitlePosition.value = position
        DesktopDataStore.setKey(PREF_POSTER_TITLE_POSITION, position.name)
    }

    fun setShowPosterRating(enabled: Boolean) {
        _showPosterRating.value = enabled
        DesktopDataStore.setKey(PREF_SHOW_POSTER_RATING, enabled)
    }

    fun setShowPosterQuality(enabled: Boolean) {
        _showPosterQuality.value = enabled
        DesktopDataStore.setKey(PREF_SHOW_POSTER_QUALITY, enabled)
    }

    fun setShowPosterLanguage(show: Boolean) {
        _showPosterLanguage.value = show
        DesktopDataStore.setKey(PREF_SHOW_POSTER_LANGUAGE, show)
    }

    fun setTextDropShadowEnabled(enabled: Boolean) {
        _textDropShadowEnabled.value = enabled
        DesktopDataStore.setKey(PREF_TEXT_DROP_SHADOW_ENABLED, enabled)
    }

    fun setTextDropShadowBlur(blur: Float) {
        _textDropShadowBlur.value = blur
        DesktopDataStore.setKey(PREF_TEXT_DROP_SHADOW_BLUR, blur)
    }

    fun setElementShadowsEnabled(enabled: Boolean) {
        _elementShadowsEnabled.value = enabled
        DesktopDataStore.setKey(PREF_ELEMENT_SHADOWS_ENABLED, enabled)
    }

    fun setElementShadowMultiplier(multiplier: Float) {
        _elementShadowMultiplier.value = multiplier
        DesktopDataStore.setKey(PREF_ELEMENT_SHADOW_MULTIPLIER, multiplier)
    }

    fun setAppPresetTheme(presetId: String) {
        _appPresetTheme.value = presetId
        DesktopDataStore.setKey(PREF_APP_PRESET_THEME, presetId)
    }

    fun setBackgroundGradientEnabled(enabled: Boolean) {
        _backgroundGradientEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_ENABLED, enabled)
    }

    fun setBackgroundGradientType(type: String) {
        _backgroundGradientType.value = type
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_TYPE, type)
    }

    fun setBackgroundGradientIntensity(intensity: Float) {
        _backgroundGradientIntensity.value = intensity
        DesktopDataStore.setKey(PREF_BACKGROUND_GRADIENT_INTENSITY, intensity)
    }

    fun setClockMode(mode: ClockDisplayMode) {
        _clockMode.value = mode
        DesktopDataStore.setKey(PREF_CLOCK_MODE, mode.name)
    }

    fun setClockTimeFormat(format: String) {
        _clockTimeFormat.value = format
        DesktopDataStore.setKey(PREF_CLOCK_TIME_FORMAT, format)
    }

    fun setClockDateFormat(format: String) {
        _clockDateFormat.value = format
        DesktopDataStore.setKey(PREF_CLOCK_DATE_FORMAT, format)
    }

    fun setDetailsShowCurrentTime(enabled: Boolean) {
        _detailsShowCurrentTime.value = enabled
        DesktopDataStore.setKey(PREF_DETAILS_SHOW_CURRENT_TIME, enabled)
    }

    fun setDetailsShowEndTime(enabled: Boolean) {
        _detailsShowEndTime.value = enabled
        DesktopDataStore.setKey(PREF_DETAILS_SHOW_END_TIME, enabled)
    }

    fun setLockUnreleasedEpisodes(enabled: Boolean) {
        _lockUnreleasedEpisodes.value = enabled
        DesktopDataStore.setKey(PREF_LOCK_UNRELEASED_EPISODES, enabled)
    }

    fun setBackgroundImagePath(path: String) {
        _backgroundImagePath.value = path
        DesktopDataStore.setKey(PREF_BG_IMAGE_PATH, path)
    }

    fun setBackgroundImageBlur(blur: Float) {
        _backgroundImageBlur.value = blur
        DesktopDataStore.setKey(PREF_BG_IMAGE_BLUR, blur)
    }

    fun setBackgroundImageBrightness(brightness: Float) {
        _backgroundImageBrightness.value = brightness
        DesktopDataStore.setKey(PREF_BG_IMAGE_BRIGHTNESS, brightness)
    }

    fun clearBackgroundImage() {
        _backgroundImagePath.value = ""
        DesktopDataStore.setKey(PREF_BG_IMAGE_PATH, "")
    }

    fun setBackgroundImageOpacity(opacity: Float) {
        _backgroundImageOpacity.value = opacity
        DesktopDataStore.setKey(PREF_BG_IMAGE_OPACITY, opacity)
    }

    fun setBackgroundImageSaturation(saturation: Float) {
        _backgroundImageSaturation.value = saturation
        DesktopDataStore.setKey(PREF_BG_IMAGE_SATURATION, saturation)
    }

    fun setBackgroundImageVignetteEnabled(enabled: Boolean) {
        _backgroundImageVignetteEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BG_IMAGE_VIGNETTE, enabled)
    }

    fun setBackgroundImageVignetteIntensity(intensity: Float) {
        _backgroundImageVignetteIntensity.value = intensity
        DesktopDataStore.setKey(PREF_BG_IMAGE_VIGNETTE_INTENSITY, intensity)
    }

    fun setBackgroundImageTintEnabled(enabled: Boolean) {
        _backgroundImageTintEnabled.value = enabled
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_ENABLED, enabled)
    }

    fun setBackgroundImageTintColor(hex: String) {
        _backgroundImageTintColor.value = hex
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_COLOR, hex)
    }

    fun setBackgroundImageTintAlpha(alpha: Float) {
        _backgroundImageTintAlpha.value = alpha
        DesktopDataStore.setKey(PREF_BG_IMAGE_TINT_ALPHA, alpha)
    }

    fun setUiCardOpacity(opacity: Float) {
        _uiCardOpacity.value = opacity
        DesktopDataStore.setKey(PREF_UI_CARD_OPACITY, opacity)
    }

    fun saveCustomPreset(preset: ThemePreset) {
        val currentList = _customPresets.value.toMutableList()
        val index = currentList.indexOfFirst { it.id == preset.id }
        if (index != -1) {
            currentList[index] = preset
        } else {
            currentList.add(preset)
        }
        _customPresets.value = currentList
        saveCustomPresetsToDisk(currentList)
        setAppPresetTheme(preset.id)
    }

    fun deleteCustomPreset(id: String) {
        val currentList = _customPresets.value.filter { it.id != id }
        _customPresets.value = currentList
        saveCustomPresetsToDisk(currentList)
        if (_appPresetTheme.value == id) {
            setAppPresetTheme("preset_cyberpunk")
        }
    }

    private fun saveCustomPresetsToDisk(list: List<ThemePreset>) {
        try {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val json = mapper.writeValueAsString(list)
            DesktopDataStore.setKey(PREF_CUSTOM_PRESETS, json)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applyPreset(preset: ThemePreset) {
        setAppPresetTheme(preset.id)
        setLightMode(preset.isLightMode)
        setThemeAccent(preset.themeAccent)
        if (preset.themeAccent == "Custom") {
            setCustomThemeAccent(preset.customThemeAccent)
        }
        setAppThemeBackground(preset.appThemeBackground)
        if (preset.appThemeBackground == "Custom") {
            setCustomAppThemeBackground(preset.customAppThemeBackground)
        }
        setBackgroundGradientEnabled(preset.backgroundGradientEnabled)
        setBackgroundGradientType(preset.backgroundGradientType)
        setBackgroundGradientIntensity(preset.backgroundGradientIntensity)
    }

    fun setDetailsSectionOrder(order: List<com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey>) {
        _detailsSectionOrder.value = order
        DesktopDataStore.setKey(
            PREF_DETAILS_SECTION_ORDER,
            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.serialize(order),
        )
    }

    fun toggleDetailsSection(key: com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey, enabled: Boolean) {
        val current = _detailsDisabledSections.value.toMutableSet()
        if (enabled) {
            current.remove(key)
        } else {
            current.add(key)
        }
        _detailsDisabledSections.value = current
        DesktopDataStore.setKey(
            PREF_DETAILS_DISABLED_SECTIONS,
            com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.serialize(current),
        )
    }

    fun moveDetailsSection(fromIndex: Int, toIndex: Int) {
        val current = _detailsSectionOrder.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            setDetailsSectionOrder(current)
        }
    }

    fun resetDetailsSectionOrder() {
        setDetailsSectionOrder(com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.defaultOrder)
        _detailsDisabledSections.value = emptySet()
        DesktopDataStore.removeKey(PREF_DETAILS_DISABLED_SECTIONS)
    }

    fun setDockItemOrder(order: List<DockItemKey>) {
        _dockItemOrder.value = order
        DesktopDataStore.setKey(PREF_DOCK_ITEM_ORDER, DockItemKey.serialize(order))
    }

    fun toggleDockItem(key: DockItemKey, enabled: Boolean) {
        if (key.isRequired) return // Always required items (Home, Settings) cannot be disabled
        val current = _dockDisabledItems.value.toMutableSet()
        if (enabled) {
            current.remove(key)
        } else {
            current.add(key)
        }
        _dockDisabledItems.value = current
        DesktopDataStore.setKey(PREF_DOCK_DISABLED_ITEMS, DockItemKey.serialize(current))
    }

    fun moveDockItem(fromIndex: Int, toIndex: Int) {
        val current = _dockItemOrder.value.toMutableList()
        if (fromIndex in current.indices && toIndex in current.indices && fromIndex != toIndex) {
            val item = current.removeAt(fromIndex)
            current.add(toIndex, item)
            setDockItemOrder(current)
        }
    }

    fun resetDockItemOrder() {
        setDockItemOrder(DockItemKey.DEFAULT_ORDER)
        _dockDisabledItems.value = DockItemKey.DEFAULT_DISABLED
        DesktopDataStore.removeKey(PREF_DOCK_ITEM_ORDER)
        DesktopDataStore.removeKey(PREF_DOCK_DISABLED_ITEMS)
    }

    fun setTopBarShowProfile(enabled: Boolean) {
        _topBarShowProfile.value = enabled
        DesktopDataStore.setKey(PREF_TOPBAR_SHOW_PROFILE, enabled)
    }

    fun setTopBarShowProfileName(enabled: Boolean) {
        _topBarShowProfileName.value = enabled
        DesktopDataStore.setKey(PREF_TOPBAR_SHOW_PROFILE_NAME, enabled)
    }

    fun reloadFromDataStore() {
        _dockItemOrder.value = DockItemKey.parseOrder(DesktopDataStore.getKey<String>(PREF_DOCK_ITEM_ORDER))
        _dockDisabledItems.value = DockItemKey.parseDisabled(DesktopDataStore.getKey<String>(PREF_DOCK_DISABLED_ITEMS))
        _topBarShowProfile.value = DesktopDataStore.getKey<Boolean>(PREF_TOPBAR_SHOW_PROFILE) ?: true
        _topBarShowProfileName.value = DesktopDataStore.getKey<Boolean>(PREF_TOPBAR_SHOW_PROFILE_NAME) ?: true
        _themeAccent.value = DesktopDataStore.getKey<String>(PREF_THEME_ACCENT) ?: "Purple"
        _antiSpoilerEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_ANTI_SPOILER_ENABLED) ?: true
        _lockUnreleasedEpisodes.value = DesktopDataStore.getKey<Boolean>(PREF_LOCK_UNRELEASED_EPISODES) ?: true
        _detailsShowCurrentTime.value = DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_CURRENT_TIME) ?: true
        _detailsShowEndTime.value = DesktopDataStore.getKey<Boolean>(PREF_DETAILS_SHOW_END_TIME) ?: true
        _detailsSectionOrder.value = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseOrder(DesktopDataStore.getKey<String>(PREF_DETAILS_SECTION_ORDER))
        _detailsDisabledSections.value = com.lagradost.cloudstream3.desktop.ui.screens.details.contract.DetailsSectionKey.parseDisabled(DesktopDataStore.getKey<String>(PREF_DETAILS_DISABLED_SECTIONS))
        _amoledMode.value = DesktopDataStore.getKey<Boolean>(PREF_AMOLED_MODE) ?: false
        _isLightMode.value = DesktopDataStore.getKey<Boolean>(PREF_LIGHT_MODE) ?: false
        _gridScale.value = DesktopDataStore.getKey<String>(PREF_GRID_SCALE) ?: "Normal"
        _ambientGlowEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_AMBIENT_GLOW) ?: true
        _ambientGlowIntensity.value = DesktopDataStore.getKey<Float>(PREF_AMBIENT_GLOW_INTENSITY) ?: 0.15f
        _ambientGlowPositions.value = (DesktopDataStore.getKey<String>(PREF_AMBIENT_GLOW_POSITION) ?: "Center").split(",").filter { it.isNotBlank() }.toSet()
        _heroBackgroundBlurEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_HERO_BACKGROUND_BLUR) ?: true
        _heroBackdropBlurRadius.value = DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_BLUR_RADIUS) ?: 80f
        _heroBackdropDarkening.value = DesktopDataStore.getKey<Float>(PREF_HERO_BACKDROP_DARKENING) ?: 0.65f
        _globalUiScale.value = DesktopDataStore.getKey<Float>(PREF_GLOBAL_UI_SCALE) ?: 1.0f
        _navigationStyle.value = NavigationStyle.fromString(DesktopDataStore.getKey<String>(PREF_NAVIGATION_STYLE))
        _dockPosition.value = DockPosition.fromString(DesktopDataStore.getKey<String>(PREF_DOCK_POSITION) ?: "Left")
        _selectedFont.value = DesktopDataStore.getKey<String>(PREF_FONT) ?: "Inter"
        _screensaverEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_SCREENSAVER_ENABLED) ?: true
        _heroAutoSlideDelaySeconds.value = DesktopDataStore.getKey<Int>(PREF_HERO_AUTO_SLIDE_DELAY) ?: 10
        _continueWatchingStyle.value = ContinueWatchingStyle.fromString(DesktopDataStore.getKey<String>(PREF_CONTINUE_WATCHING_STYLE))
        _posterHoverGlowEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_POSTER_HOVER_GLOW_ENABLED) ?: true
        _posterTitlePosition.value = PosterTitlePosition.fromString(DesktopDataStore.getKey<String>(PREF_POSTER_TITLE_POSITION))
        _homeSpacingDp.value = DesktopDataStore.getKey<Int>(PREF_HOME_SPACING_DP) ?: 12
        _homeVerticalSpacingDp.value = DesktopDataStore.getKey<Int>(PREF_HOME_VERTICAL_SPACING_DP) ?: 0
        _posterWidthDp.value = DesktopDataStore.getKey<Int>(PREF_POSTER_WIDTH) ?: 190
        _posterRoundingDp.value = DesktopDataStore.getKey<Int>(PREF_POSTER_ROUNDING) ?: 12
        _customThemeAccent.value = DesktopDataStore.getKey<String>(PREF_CUSTOM_THEME_ACCENT) ?: "#7C6BFF"
        _appThemeBackground.value = DesktopDataStore.getKey<String>(PREF_APP_THEME_BACKGROUND) ?: "Navy"
        _customAppThemeBackground.value = DesktopDataStore.getKey<String>(PREF_CUSTOM_APP_THEME_BACKGROUND) ?: "#0C0C16"
        _heroEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_HERO_ENABLED) ?: true
        _showPosterRating.value = DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_RATING) ?: true
        _showPosterQuality.value = DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_QUALITY) ?: true
        _showPosterLanguage.value = DesktopDataStore.getKey<Boolean>(PREF_SHOW_POSTER_LANGUAGE) ?: true
        _textDropShadowEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_TEXT_DROP_SHADOW_ENABLED) ?: true
        _textDropShadowBlur.value = DesktopDataStore.getKey<Float>(PREF_TEXT_DROP_SHADOW_BLUR) ?: 8f
        _elementShadowsEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_ELEMENT_SHADOWS_ENABLED) ?: true
        _elementShadowMultiplier.value = DesktopDataStore.getKey<Float>(PREF_ELEMENT_SHADOW_MULTIPLIER) ?: 1.0f
        _appPresetTheme.value = DesktopDataStore.getKey<String>(PREF_APP_PRESET_THEME) ?: "preset_cyberpunk"
        _backgroundGradientEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_BACKGROUND_GRADIENT_ENABLED) ?: true
        _backgroundGradientType.value = DesktopDataStore.getKey<String>(PREF_BACKGROUND_GRADIENT_TYPE) ?: "Radial"
        _backgroundGradientIntensity.value = DesktopDataStore.getKey<Float>(PREF_BACKGROUND_GRADIENT_INTENSITY) ?: 0.5f
        _clockMode.value = ClockDisplayMode.fromString(DesktopDataStore.getKey<String>(PREF_CLOCK_MODE))
        _clockTimeFormat.value = DesktopDataStore.getKey<String>(PREF_CLOCK_TIME_FORMAT) ?: "HH:mm"
        _clockDateFormat.value = DesktopDataStore.getKey<String>(PREF_CLOCK_DATE_FORMAT) ?: "EEE, dd MMM"
        _backgroundImagePath.value = DesktopDataStore.getKey<String>(PREF_BG_IMAGE_PATH) ?: ""
        _backgroundImageBlur.value = DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BLUR) ?: 20f
        _backgroundImageBrightness.value = DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_BRIGHTNESS) ?: 0.35f
        _backgroundImageOpacity.value = DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_OPACITY) ?: 1.0f
        _backgroundImageSaturation.value = DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_SATURATION) ?: 1.0f
        _backgroundImageVignetteEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_VIGNETTE) ?: false
        _backgroundImageVignetteIntensity.value = DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_VIGNETTE_INTENSITY) ?: 0.7f
        _backgroundImageTintEnabled.value = DesktopDataStore.getKey<Boolean>(PREF_BG_IMAGE_TINT_ENABLED) ?: false
        _backgroundImageTintColor.value = DesktopDataStore.getKey<String>(PREF_BG_IMAGE_TINT_COLOR) ?: "#7C6BFF"
        _backgroundImageTintAlpha.value = DesktopDataStore.getKey<Float>(PREF_BG_IMAGE_TINT_ALPHA) ?: 0.3f
        _uiCardOpacity.value = DesktopDataStore.getKey<Float>(PREF_UI_CARD_OPACITY) ?: 0.4f
        val customPresetsJson = DesktopDataStore.getKey<String>(PREF_CUSTOM_PRESETS) ?: "[]"
        _customPresets.value = try {
            com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .readValue(customPresetsJson, object : com.fasterxml.jackson.core.type.TypeReference<List<ThemePreset>>() {})
        } catch (e: Exception) {
            emptyList()
        }
    }
}
