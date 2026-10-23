package com.lagradost.cloudstream3.desktop.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks application-wide media playback state.
 * Used to suppress intrusive popups, background clearance dialogs, and non-essential notifications
 * while the user is actively watching video content.
 */
object GlobalPlayerState {
    private val _isPlaybackActive = MutableStateFlow(false)
    val isPlaybackActive: StateFlow<Boolean> = _isPlaybackActive.asStateFlow()

    fun setPlaybackActive(active: Boolean) {
        _isPlaybackActive.value = active
    }
}
