package com.lagradost.cloudstream3.desktop.player.ipc

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.common.logging.AppLogger

internal val ipcObjectMapper = jacksonObjectMapper()

sealed interface PlayerInboundEvent {
    // Lifecycle & Windowing
    data object UiReady : PlayerInboundEvent
    data object FocusWebView : PlayerInboundEvent
    data object ExitPlayer : PlayerInboundEvent
    data object ToggleFullscreen : PlayerInboundEvent
    data object StartWindowDrag : PlayerInboundEvent
    data class StartWindowResize(val direction: String) : PlayerInboundEvent
    data object TogglePip : PlayerInboundEvent

    // Playback Control
    data object TogglePlay : PlayerInboundEvent
    data object Play : PlayerInboundEvent
    data object Pause : PlayerInboundEvent
    data class ToggleMute(val forcedState: Boolean?) : PlayerInboundEvent
    data class SetVolume(val volume: Double) : PlayerInboundEvent
    data class SetSpeed(val speed: Double) : PlayerInboundEvent
    data class SeekTo(val positionMs: Double) : PlayerInboundEvent
    data class SeekBy(val deltaMs: Double) : PlayerInboundEvent
    data object SeekLive : PlayerInboundEvent
    data object SkipInterval : PlayerInboundEvent
    data class ToggleLoop(val loop: Boolean) : PlayerInboundEvent
    data class ToggleAudioMode(val enabled: Boolean?) : PlayerInboundEvent

    // Media Tracks & Navigation
    data class SetAudioTrack(val id: Int?) : PlayerInboundEvent
    data class SetVideoTrack(val id: Int?) : PlayerInboundEvent
    data class SetSubtitleTrack(val id: Int?) : PlayerInboundEvent
    data object NextChapter : PlayerInboundEvent
    data object PreviousChapter : PlayerInboundEvent
    data class SeekToChapter(val index: Int) : PlayerInboundEvent
    data class LoadEpisode(val episodeId: String) : PlayerInboundEvent
    data class ChangeLink(val linkUrl: String) : PlayerInboundEvent
    data class LazyAudioTrack(val url: String) : PlayerInboundEvent
    data class LazySubtitleTrack(val url: String) : PlayerInboundEvent
    data class LazyVideoTrack(val url: String) : PlayerInboundEvent
    data object LoadNextEpisode : PlayerInboundEvent
    data object ReplayEpisode : PlayerInboundEvent

    // Subtitles & Visuals
    data class SelectShader(val name: String) : PlayerInboundEvent
    data class SetSubDelay(val delaySeconds: Double) : PlayerInboundEvent
    data object CycleSubtitles : PlayerInboundEvent
    data object ToggleSubVisibility : PlayerInboundEvent
    data class SetSubtitleFont(val fontName: String?) : PlayerInboundEvent
    data class SetSubtitleOverrideEnabled(val enabled: Boolean) : PlayerInboundEvent
    data object ResetSubtitleSettings : PlayerInboundEvent
    data class SetSubtitleBackground(val backgroundKey: String) : PlayerInboundEvent
    data class SetSubtitleBorderColor(val color: String) : PlayerInboundEvent
    data class SetSubtitleBorderSize(val size: String) : PlayerInboundEvent
    data class SetSubtitleShadowColor(val color: String) : PlayerInboundEvent
    data class SetSubtitleShadowOffset(val offset: String) : PlayerInboundEvent
    data class SetSubtitleBlur(val blur: String) : PlayerInboundEvent
    data class SetSubtitleBold(val bold: String) : PlayerInboundEvent
    data class SetSubtitleItalic(val italic: String) : PlayerInboundEvent
    data class SearchSubtitles(val query: String, val lang: String?, val season: Int?, val episode: Int?) : PlayerInboundEvent
    data class DownloadSubtitle(val idPrefix: String, val data: String, val name: String, val lang: String, val source: String) : PlayerInboundEvent
    data object OpenLocalSubtitlePicker : PlayerInboundEvent
    data class LoadLocalSubtitleFile(val path: String) : PlayerInboundEvent
    data object Screenshot : PlayerInboundEvent

    // Video Processing & Audio Enhancements
    data class ToggleInterpolation(val enabled: Boolean) : PlayerInboundEvent
    data class ToggleDeband(val enabled: Boolean) : PlayerInboundEvent
    data class SetAudioNormalization(val enabled: Boolean) : PlayerInboundEvent
    data class SetAudioNormStrength(val strength: String) : PlayerInboundEvent
    data class SetAudioSpatial(val enabled: Boolean) : PlayerInboundEvent
    data class SetAudioEqPreset(val preset: String) : PlayerInboundEvent
    data class SetAudioVolumeMax(val enabled: Boolean) : PlayerInboundEvent
    data class SetAudioDelay(val delaySec: Float) : PlayerInboundEvent

    // Preferences & Settings
    data class SetPauseInfoMode(val mode: String) : PlayerInboundEvent
    data class SetPauseShowCast(val forcedState: Boolean?) : PlayerInboundEvent
    data class ToggleAutoPlay(val enabled: Boolean) : PlayerInboundEvent
    data class SetPrefShowEndTime(val enabled: Boolean) : PlayerInboundEvent
    data class SetPrefShowClock(val enabled: Boolean) : PlayerInboundEvent
    data class SetPrefShowServerQuality(val enabled: Boolean) : PlayerInboundEvent
    data class SetMpvProperty(val property: String, val value: String) : PlayerInboundEvent
    data object SkipScraping : PlayerInboundEvent
    data object RetryPlayback : PlayerInboundEvent
    data class CopyDiagnostics(val text: String) : PlayerInboundEvent
    data class ClientError(val message: String) : PlayerInboundEvent
    data class ClientLog(val message: String) : PlayerInboundEvent

    // Fallback for unmapped or malformed events
    data class Unknown(val type: String, val rawValue: String) : PlayerInboundEvent

    companion object {
        fun fromJson(rootNode: JsonNode?, rawPayload: String): PlayerInboundEvent {
            if (rootNode == null) return Unknown("", rawPayload)
            val eventType = rootNode.get("type")?.asText() ?: ""
            val valueNode = rootNode.get("value")
            val eventValue = when {
                valueNode == null || valueNode.isNull -> ""
                valueNode.isTextual -> valueNode.asText()
                else -> valueNode.toString()
            }

            return when (eventType) {
                "ui_ready" -> UiReady
                "focusWebView" -> FocusWebView
                "exitPlayer", "exit", "close" -> ExitPlayer
                "toggleFullscreen" -> ToggleFullscreen
                "startWindowDrag" -> StartWindowDrag
                "startWindowResize" -> StartWindowResize(eventValue)
                "togglePip" -> TogglePip
                "retryPlayback", "retry" -> RetryPlayback
                "copyDiagnostics" -> CopyDiagnostics(eventValue)
                "client_error" -> ClientError(rootNode.get("message")?.asText() ?: eventValue)
                "clientLog" -> ClientLog(rootNode.get("message")?.asText() ?: eventValue)

                "togglePlay" -> TogglePlay
                "play" -> Play
                "pause" -> Pause
                "toggleMute" -> ToggleMute(
                    when (eventValue) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                )
                "setVolume" -> SetVolume(eventValue.toDoubleOrNull() ?: 100.0)
                "setSpeed" -> SetSpeed(eventValue.toDoubleOrNull() ?: 1.0)
                "seekTo" -> SeekTo(eventValue.toDoubleOrNull() ?: 0.0)
                "seekBy" -> SeekBy(eventValue.toDoubleOrNull() ?: 0.0)
                "seekLive", "seek_live" -> SeekLive
                "skipInterval", "skipCurrentInterval" -> SkipInterval
                "toggleLoop" -> ToggleLoop(eventValue == "true")
                "toggleAudioMode", "toggleAudioOnly" -> ToggleAudioMode(
                    when (eventValue) {
                        "true" -> true
                        "false" -> false
                        else -> null
                    }
                )

                "setAudioTrack" -> SetAudioTrack(eventValue.toIntOrNull())
                "setVideoTrack" -> SetVideoTrack(eventValue.toIntOrNull())
                "setSubtitleTrack" -> SetSubtitleTrack(eventValue.toIntOrNull())
                "nextChapter" -> NextChapter
                "previousChapter" -> PreviousChapter
                "seekToChapter" -> SeekToChapter(eventValue.toIntOrNull() ?: 0)
                "loadEpisode" -> LoadEpisode(eventValue)
                "changeLink" -> ChangeLink(eventValue)
                "loadLazyAudioTrack" -> LazyAudioTrack(eventValue)
                "loadLazySubtitleTrack" -> LazySubtitleTrack(eventValue)
                "loadLazyVideoTrack" -> LazyVideoTrack(eventValue)
                "loadNextEpisode", "nextEpisode" -> LoadNextEpisode
                "replayEpisode" -> ReplayEpisode

                "selectShader" -> SelectShader(eventValue)
                "setSubDelay" -> SetSubDelay(eventValue.toDoubleOrNull() ?: 0.0)
                "cycleSubtitles" -> CycleSubtitles
                "toggleSubVisibility" -> ToggleSubVisibility
                "setSubtitleFont" -> SetSubtitleFont(eventValue.takeIf { it.isNotBlank() })
                "setSubtitleOverrideEnabled" -> SetSubtitleOverrideEnabled(eventValue.toBoolean())
                "resetSubtitleSettings" -> ResetSubtitleSettings
                "setSubtitleBackground" -> SetSubtitleBackground(eventValue)
                "setSubtitleBorderColor" -> SetSubtitleBorderColor(eventValue)
                "setSubtitleBorderSize" -> SetSubtitleBorderSize(eventValue)
                "setSubtitleShadowColor" -> SetSubtitleShadowColor(eventValue)
                "setSubtitleShadowOffset" -> SetSubtitleShadowOffset(eventValue)
                "setSubtitleBlur" -> SetSubtitleBlur(eventValue)
                "setSubtitleBold" -> SetSubtitleBold(eventValue)
                "setSubtitleItalic" -> SetSubtitleItalic(eventValue)

                "searchSubtitles" -> parseSearchSubtitles(rootNode, rawPayload)
                "downloadSubtitle" -> parseDownloadSubtitle(rootNode, rawPayload)
                "openLocalSubtitlePicker" -> OpenLocalSubtitlePicker
                "loadLocalSubtitleFile" -> LoadLocalSubtitleFile(eventValue)
                "screenshot" -> Screenshot

                "toggleInterpolation" -> ToggleInterpolation(eventValue.toBoolean())
                "toggleDeband" -> ToggleDeband(eventValue.toBoolean())
                "setAudioNormalization" -> SetAudioNormalization(eventValue.toBoolean())
                "setAudioNormStrength" -> SetAudioNormStrength(eventValue)
                "setAudioSpatial" -> SetAudioSpatial(eventValue.toBoolean())
                "setAudioEqPreset" -> SetAudioEqPreset(eventValue)
                "setAudioVolumeMax" -> SetAudioVolumeMax(eventValue.toBoolean())
                "setAudioDelay" -> SetAudioDelay(eventValue.toFloatOrNull() ?: 0f)

                "setPauseInfoMode", "set_pause_info_mode" -> SetPauseInfoMode(eventValue)
                "setPauseShowCast", "set_pause_show_cast", "toggle_pause_show_cast" -> SetPauseShowCast(
                    eventValue.toBooleanStrictOrNull()
                )
                "toggleAutoPlay" -> ToggleAutoPlay(eventValue.toBoolean())
                "setPrefShowEndTime" -> SetPrefShowEndTime(eventValue.toBoolean())
                "setPrefShowClock" -> SetPrefShowClock(eventValue.toBoolean())
                "setPrefShowServerQuality" -> SetPrefShowServerQuality(eventValue.toBoolean())
                "setMpvProperty" -> {
                    val parts = eventValue.split(":", limit = 2)
                    if (parts.size == 2) {
                        SetMpvProperty(parts[0], parts[1])
                    } else {
                        Unknown(eventType, eventValue)
                    }
                }
                "skipScraping" -> SkipScraping

                else -> {
                    AppLogger.w("Player:IPC", "Unrecognized inbound event: type='$eventType', value='$eventValue'")
                    Unknown(eventType, eventValue)
                }
            }
        }

        private fun parseSearchSubtitles(rootNode: JsonNode, rawPayload: String): PlayerInboundEvent {
            return try {
                val clean = rawPayload.replace(Regex("[\\x00-\\x1F]"), "")
                val root = ipcObjectMapper.readTree(clean)
                val innerJson = root.get("value")?.asText()?.takeIf { it.isNotBlank() } ?: "{}"
                val parsed = ipcObjectMapper.readTree(innerJson)
                SearchSubtitles(
                    query = parsed["query"]?.asText() ?: "",
                    lang = parsed["lang"]?.asText()?.takeIf { it.isNotBlank() },
                    season = parsed["season"]?.asText()?.toIntOrNull(),
                    episode = parsed["episode"]?.asText()?.toIntOrNull(),
                )
            } catch (t: Throwable) {
                AppLogger.e("Player:IPC", "Failed to parse searchSubtitles payload: $rawPayload", t)
                Unknown("searchSubtitles", rawPayload)
            }
        }

        private fun parseDownloadSubtitle(rootNode: JsonNode, rawPayload: String): PlayerInboundEvent {
            return try {
                val valNode = rootNode.get("value")
                val parsed = when {
                    valNode == null || valNode.isNull -> rootNode
                    valNode.isObject -> valNode
                    valNode.isTextual -> ipcObjectMapper.readTree(valNode.asText())
                    else -> ipcObjectMapper.readTree(valNode.toString())
                }
                val idPrefix = parsed?.get("idPrefix")?.asText()?.takeIf { it.isNotBlank() } ?: ""
                val data = parsed?.get("data")?.asText()?.takeIf { it.isNotBlank() } ?: ""
                val name = parsed?.get("name")?.asText() ?: "subtitle"
                val lang = parsed?.get("lang")?.asText() ?: ""
                val source = parsed?.get("source")?.asText() ?: ""
                DownloadSubtitle(idPrefix, data, name, lang, source)
            } catch (t: Throwable) {
                AppLogger.e("Player:IPC", "Failed to parse downloadSubtitle payload: $rawPayload", t)
                Unknown("downloadSubtitle", rawPayload)
            }
        }
    }
}
