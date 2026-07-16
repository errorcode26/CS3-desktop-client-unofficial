package com.lagradost.player.api

data class PlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val duration: Long = 0,
    val position: Long = 0,
    val isFinished: Boolean = false,
    val error: String? = null,
    val isLoading: Boolean = false,
    val currentUrl: String? = null,
)
