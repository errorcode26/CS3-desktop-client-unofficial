package com.lagradost.cloudstream3.desktop.subtitles

import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SubtitleConfig {
    const val PREF_PRIMARY_STREMIO_SUB_ENABLED = "subtitle_PRIMARY_STREMIO_SUB_enabled"

    private val _PRIMARY_STREMIO_SUBEnabled = MutableStateFlow(
        DesktopDataStore.getKey<Boolean>(PREF_PRIMARY_STREMIO_SUB_ENABLED) ?: true,
    )
    val PRIMARY_STREMIO_SUBEnabled: StateFlow<Boolean> = _PRIMARY_STREMIO_SUBEnabled.asStateFlow()

    fun setPRIMARY_STREMIO_SUBEnabled(enabled: Boolean) {
        DesktopDataStore.setKey(PREF_PRIMARY_STREMIO_SUB_ENABLED, enabled)
        _PRIMARY_STREMIO_SUBEnabled.value = enabled
    }
}
