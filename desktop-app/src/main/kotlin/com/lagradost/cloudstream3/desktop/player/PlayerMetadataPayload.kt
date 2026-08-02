package com.lagradost.cloudstream3.desktop.player

data class PlayerUiSyncState(
    val plot: String?,
    val year: Int?,
    val tags: List<String>?,
    val isProbing: Boolean,
    val backdropUrl: String?,
    val logoUrl: String?,
    val currentLinkIndex: Int,
    val failedLinks: List<FailedLinkPayload>,
    val links: List<LinkPayload>,
    val episodes: List<EpisodePayload>,
    val audioTracks: List<SubtitleTrackPayload>,
    val subTracks: List<SubtitleTrackPayload>,
    val lazyAudioTracks: List<LazyTrackPayload>,
    val lazySubTracks: List<LazyTrackPayload>,
    val lazyVideoTracks: List<LazyTrackPayload>,
    val startPositionMs: Long,
    val title: String,
    val shaders: List<String>,
    val activeShader: String?,
    val activeSubtitleFont: String?,
    val availableSubtitleFonts: List<String>,
    val activeSubtitleBackground: String?,
    val activeSubtitleBorderColor: String?,
    val activeSubtitleBorderSize: String?,
    val activeSubtitleShadowColor: String?,
    val activeSubtitleShadowOffset: String?,
    val activeSubtitleBlur: String?,
    val activeSubtitleBold: String?,
    val activeSubtitleItalic: String?,
    val activeLazyVideoTrackUrl: String?,
    val resolution: String?,
    val activeSubtitleOverrideEnabled: Boolean,
)

data class FailedLinkPayload(
    val index: Int,
    val reason: String
)

data class LinkPayload(
    val index: Int,
    val name: String,
    val quality: Int,
    val isActive: Boolean,
    val isM3u8: Boolean,
    val isDash: Boolean,
    val url: String
)

data class EpisodePayload(
    val id: String?,
    val title: String,
    val season: Int?,
    val episode: Int?,
    val isActive: Boolean,
    val posterUrl: String?,
    val description: String?,
    val runTime: Int?
)

data class SubtitleTrackPayload(
    val id: Int,
    val name: String,
    val isSelected: Boolean
)

data class LazyTrackPayload(
    val url: String,
    val name: String,
    val language: String?
)

data class AppStateUpdatePayload(
    val type: String = "app_state_update",
    val volume: Float,
    val isMuted: Boolean,
    val isAppLoading: Boolean,
    val loadingStatusText: String?,
    val debugWait: Boolean = false,
    val debugHasEver: Boolean = true,
    val debugPos: Double = 0.0,
    val interpolationEnabled: Boolean = false
)

data class ToastPayload(
    val type: String = "show_toast",
    val message: String
)

data class MetadataUpdatePayloadWrapper(
    val type: String = "metadata_update",
    val value: PlayerUiSyncState
)
