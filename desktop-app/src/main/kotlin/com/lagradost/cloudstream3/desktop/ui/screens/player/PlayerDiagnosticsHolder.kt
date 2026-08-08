package com.lagradost.cloudstream3.desktop.ui.screens.player

import com.lagradost.player.impl.proxy.LocalStreamProxyState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Snapshot of live player & stream diagnostic properties for DevStudio.
 */
data class LivePlayerDiagnostics(
    val isAttached: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferMs: Long = 0L,
    val isPaused: Boolean = false,
    val isBuffering: Boolean = false,
    val isProbing: Boolean = false,
    val videoCodec: String = "",
    val audioCodec: String = "",
    val hwdec: String = "",
    val fps: Double = 0.0,
    val resolution: String = "",
    val droppedFrames: Long = 0L,
    val videoBitrate: Long = 0L,
    val audioBitrate: Long = 0L,
    val activeShader: String = "None",
    val isInterpolationEnabled: Boolean = false,
    val volume: Float = 100f,
    val playbackSpeed: Float = 1.0f,
    val subtitleTracksCount: Int = 0,
    val audioTracksCount: Int = 0,
    val videoTracksCount: Int = 0,
    val proxyAudioTracksCount: Int = 0,
    val proxySubtitleTracksCount: Int = 0,
    val proxyVideoTracksCount: Int = 0,
)

/**
 * Singleton holder connecting active player instances to the DevStudio diagnostics panel.
 */
object PlayerDiagnosticsHolder {
    private val _activePlayerState = MutableStateFlow<PlayerState?>(null)
    val activePlayerState: StateFlow<PlayerState?> = _activePlayerState.asStateFlow()

    fun register(playerState: PlayerState) {
        _activePlayerState.value = playerState
    }

    fun unregister(playerState: PlayerState) {
        if (_activePlayerState.value == playerState) {
            _activePlayerState.value = null
        }
    }

    fun getSnapshot(): LivePlayerDiagnostics {
        val player = _activePlayerState.value
        val proxyAudio = LocalStreamProxyState.lazyAudioTracks.value.size
        val proxySub = LocalStreamProxyState.lazySubtitleTracks.value.size
        val proxyVid = LocalStreamProxyState.lazyVideoTracks.value.size

        if (player == null) {
            return LivePlayerDiagnostics(
                isAttached = false,
                proxyAudioTracksCount = proxyAudio,
                proxySubtitleTracksCount = proxySub,
                proxyVideoTracksCount = proxyVid,
            )
        }

        return LivePlayerDiagnostics(
            isAttached = true,
            positionMs = player.positionMs.value,
            durationMs = player.durationMs.value,
            bufferMs = player.bufferMs.value,
            isPaused = player.isPaused.value,
            isBuffering = player.isBuffering.value,
            isProbing = player.isProbing.value,
            videoCodec = player.videoCodec.value,
            audioCodec = player.audioCodec.value,
            hwdec = player.hwdecCurrent.value,
            fps = player.fps.value,
            resolution = player.resolution.value,
            droppedFrames = player.droppedFrames.value,
            videoBitrate = player.videoBitrate.value,
            audioBitrate = player.audioBitrate.value,
            activeShader = player.activeShader.value,
            isInterpolationEnabled = player.isInterpolationEnabled.value,
            volume = player.volume.value,
            playbackSpeed = player.playbackSpeed.value,
            subtitleTracksCount = player.subtitleTracks.value.size,
            audioTracksCount = player.audioTracks.value.size,
            videoTracksCount = player.videoTracks.value.size,
            proxyAudioTracksCount = proxyAudio,
            proxySubtitleTracksCount = proxySub,
            proxyVideoTracksCount = proxyVid,
        )
    }
}
