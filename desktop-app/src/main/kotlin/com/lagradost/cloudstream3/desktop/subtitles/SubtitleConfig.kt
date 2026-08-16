package com.lagradost.cloudstream3.desktop.subtitles

import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SubtitleConfig {
    const val PREF_OPENSUBTITLES_ENABLED = "subtitle_opensubtitles_enabled"

    private val _openSubtitlesEnabled = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(PREF_OPENSUBTITLES_ENABLED) ?: true,
    )
    val openSubtitlesEnabled: StateFlow<Boolean> = _openSubtitlesEnabled.asStateFlow()

    fun setOpenSubtitlesEnabled(enabled: Boolean) {
        DesktopDataStore.setKey(PREF_OPENSUBTITLES_ENABLED, enabled)
        _openSubtitlesEnabled.value = enabled
    }
}
