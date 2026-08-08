package com.lagradost.player.impl.proxy

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Data model for audio, subtitle, and video variant tracks discovered during manifest parsing.
 */
data class ProxyTrack(
    val url: String,
    val name: String,
    val language: String,
    val bitrate: Int? = null,
)

/**
 * Listener interface for track discovery events emitted by [HlsRewriter].
 * Enables decoupling the network proxy and parser from UI state management.
 */
interface ProxyTracksListener {
    fun onTracksDiscovered(
        audioTracks: List<ProxyTrack>,
        subtitleTracks: List<ProxyTrack>,
        videoTracks: List<ProxyTrack>,
    )
    fun onLoadingComplete()
}

/**
 * Global reactive state container for proxy tracks.
 * Implements [ProxyTracksListener] so it can act as the default consumer for legacy UI observers.
 */
object LocalStreamProxyState : ProxyTracksListener {
    val loadingStatus = MutableStateFlow<String?>(null)
    val lazyAudioTracks = MutableStateFlow<List<ProxyTrack>>(emptyList())
    val lazySubtitleTracks = MutableStateFlow<List<ProxyTrack>>(emptyList())
    val lazyVideoTracks = MutableStateFlow<List<ProxyTrack>>(emptyList())

    override fun onTracksDiscovered(
        audioTracks: List<ProxyTrack>,
        subtitleTracks: List<ProxyTrack>,
        videoTracks: List<ProxyTrack>,
    ) {
        lazyAudioTracks.value = audioTracks
        lazySubtitleTracks.value = subtitleTracks
        lazyVideoTracks.value = videoTracks
        loadingStatus.value = null
    }

    override fun onLoadingComplete() {
        loadingStatus.value = null
    }

    fun reset() {
        lazyAudioTracks.value = emptyList()
        lazySubtitleTracks.value = emptyList()
        lazyVideoTracks.value = emptyList()
        loadingStatus.value = null
    }
}
