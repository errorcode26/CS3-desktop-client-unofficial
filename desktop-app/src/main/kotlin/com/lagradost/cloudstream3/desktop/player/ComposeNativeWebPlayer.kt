package com.lagradost.cloudstream3.desktop.player

// TODO: Yes, I know this file shares like 40KB of JNA event loops, keyboard hacks, and copy-pasted canvas code with BaseMpvPlayer.kt.
// It is an absolute copy-paste crime scene. But it works, and if we touch it, JNI will probably explode and spit out a garbage memory pointer.
// Do NOT touch it. Let future-us suffer.

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.player.impl.PlayerLinkHandler
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.awt.Canvas
import java.awt.Color
import java.awt.event.*
import java.io.File

/**
 * Extract a string-typed value from a flat JSON object.
 * Handles: {"key":"value", ...}
 * More robust than substringAfter/Before which breaks if field order changes or
 * the value contains special characters.
 */
private fun extractJsonString(json: String, key: String): String {
    // Match "key":"<value>" — value stops at the next unescaped quote
    val regex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"")
    return regex.find(json)?.groupValues?.getOrNull(1) ?: ""
}

/**
 * Extract a value (string OR numeric) from a flat JSON object.
 * For string values ("value":"123") returns the inner string.
 * For numeric values ("value":123) returns the numeric literal as a string.
 */
private fun extractJsonValue(json: String, key: String): String {
    // Try string value first
    val strResult = extractJsonString(json, key)
    if (strResult.isNotEmpty()) return strResult
    // Fall back to numeric/boolean value: "key": 123 or "key": true
    val numRegex = Regex("\"${Regex.escape(key)}\"\\s*:\\s*([0-9.eE+\\-]+|true|false|null)")
    return numRegex.find(json)?.groupValues?.getOrNull(1) ?: ""
}

@Composable
fun ComposeNativeWebPlayer(
    modifier: Modifier = Modifier.fillMaxSize(),
    link: ExtractorLink?,
    title: String? = null,
    seriesPosterUrl: String? = null,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile> = emptyList(),
    startPositionMs: Long,
    shouldPauseForResume: Boolean = false,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    isExiting: Boolean = false,
    onSkipScraping: (() -> Unit)? = null,
    onFullscreenToggle: (() -> Unit)? = null,
    playerState: com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState? = null,
    links: List<ExtractorLink> = emptyList(),
    currentLinkIndex: Int = 0,
    episodes: List<com.lagradost.cloudstream3.Episode> = emptyList(),
    currentEpisodeId: String? = null,
    isLoading: Boolean = false,
    loadingStatusText: String? = null,
    isProbing: Boolean = false,
    failedLinks: Set<Int> = emptySet(),
    backdropUrl: String? = null,
    logoUrl: String? = null,
    onLinkChange: ((Int) -> Unit)? = null,
    onEpisodeChange: ((String) -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onReplayEpisode: (() -> Unit)? = null,
) {
    var mpvHandle by remember { mutableStateOf<com.sun.jna.Pointer?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var hasEverPlayed by remember { mutableStateOf(false) }
    var waitingForTimePosReset by remember { mutableStateOf(false) }
    var loadStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    val persistentSubtitles = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    // Tracks when the last loadfile command was sent. 0L = no loadfile issued yet.
    // Used to gate the idle-active fast-fail check — MPV starts in idle-active=yes
    // and also returns to idle-active=yes after stop(), so we must not check until
    // AFTER loadfile has been issued and had time to take effect.
    var loadfileIssuedAt by remember { mutableStateOf(0L) }

    val primaryColor = androidx.compose.material3.MaterialTheme.colorScheme.primary
    val accentColorHex = remember(primaryColor) {
        String.format("#%02X%02X%02X", (primaryColor.red * 255).toInt(), (primaryColor.green * 255).toInt(), (primaryColor.blue * 255).toInt())
    }
    val accentColorRgb = remember(primaryColor) {
        "${(primaryColor.red * 255).toInt()}, ${(primaryColor.green * 255).toInt()}, ${(primaryColor.blue * 255).toInt()}"
    }

    val currentOnPlaybackReady by rememberUpdatedState(onPlaybackReady)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnFullscreenToggle by rememberUpdatedState(onFullscreenToggle)

    var isUiReady by remember { mutableStateOf(false) }
    val audioTracks by (playerState?.audioTracks ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(emptyList())
    val subtitleTracks by (playerState?.subtitleTracks ?: kotlinx.coroutines.flow.flowOf(emptyList())).collectAsState(emptyList())
    val isBuffering by (playerState?.isBuffering ?: kotlinx.coroutines.flow.flowOf(false)).collectAsState(false)

    val proxyAudioTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.collectAsState()
    val proxySubtitleTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazySubtitleTracks.collectAsState()
    val proxyVideoTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.collectAsState()

    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentLoadingStatusText by rememberUpdatedState(loadingStatusText)

    LaunchedEffect(isUiReady, links, currentLinkIndex, episodes, currentEpisodeId, audioTracks, subtitleTracks, proxyAudioTracks, proxySubtitleTracks, proxyVideoTracks, loadingStatusText, isProbing, failedLinks, backdropUrl, logoUrl, title) {
        if (isUiReady) {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val payload = mapOf(
                "type" to "metadata_update",
                "isProbing" to isProbing,
                "backdropUrl" to backdropUrl,
                "logoUrl" to logoUrl,
                "currentLinkIndex" to currentLinkIndex,
                "failedLinks" to failedLinks.toList(),
                "links" to links.mapIndexed { index, l ->
                    mapOf("index" to index, "name" to l.name, "quality" to l.quality, "isActive" to (index == currentLinkIndex))
                },
                "episodes" to episodes.map {
                    mapOf(
                        "id" to it.data,
                        "title" to (it.name ?: "Episode ${it.episode}"),
                        "season" to it.season,
                        "episode" to it.episode,
                        "isActive" to (it.data == currentEpisodeId),
                        "posterUrl" to (it.posterUrl ?: seriesPosterUrl),
                        "description" to it.description,
                        "runTime" to it.runTime,
                    )
                },
                "audioTracks" to audioTracks.map {
                    mapOf("id" to it.id, "name" to it.name, "isSelected" to it.isSelected)
                },
                "subTracks" to subtitleTracks.map {
                    mapOf("id" to it.id, "name" to it.name, "isSelected" to it.isSelected)
                },
                "lazyAudioTracks" to proxyAudioTracks.map {
                    mapOf("url" to it.url, "name" to it.name, "language" to it.language)
                },
                "lazySubTracks" to proxySubtitleTracks.map {
                    mapOf("url" to it.url, "name" to it.name, "language" to it.language)
                },
                "lazyVideoTracks" to proxyVideoTracks.map {
                    mapOf("url" to it.url, "name" to it.name, "language" to it.language)
                },
                "startPositionMs" to startPositionMs,
                "title" to (title ?: "CloudStream"),
            )
            NativePlayerBridge.postMessage(mapper.writeValueAsString(payload))
        }
    }

    fun pushMetadataToWebView() {
        try {
            val vol = playerState?.volume?.value ?: 100f
            val isMuted = playerState?.isMuted?.value ?: false
            val isBuf = playerState?.isBuffering?.value == true

            var currentlyLoading = isBuf
            var isAppScraping = false
            var escapedLoadingText: String? = null
            try {
                // These are Compose rememberUpdatedState properties.
                // Reading them from Dispatchers.IO can sometimes throw Snapshot exceptions.
                currentlyLoading = isBuf
                isAppScraping = currentIsLoading
                escapedLoadingText = currentLoadingStatusText?.replace("\"", "\\\"")?.replace("\n", "\\n")
            } catch (e: Throwable) {
                // Fallback if we can't read Compose state from this thread
            }

            val loadingTextJson = if (escapedLoadingText != null) "\"$escapedLoadingText\"" else "null"

            NativePlayerBridge.postMessage(
                "{\"type\":\"app_state_update\",\"volume\":$vol,\"isMuted\":$isMuted,\"isAppLoading\":$isAppScraping,\"loadingStatusText\":$loadingTextJson,\"debugWait\":$waitingForTimePosReset,\"debugHasEver\":$hasEverPlayed,\"debugPos\":0.0}",
            )
        } catch (e: Throwable) {
            com.lagradost.common.logging.AppLogger.e("pushMetadataToWebView error: ${e.message}")
        }
    }

    LaunchedEffect(isLoading, isBuffering, loadingStatusText) {
        if (isUiReady && mpvHandle != null) {
            pushMetadataToWebView()
        }
    }

    LaunchedEffect(isUiReady, mpvHandle) {
        val h = mpvHandle
        if (isUiReady && h != null) {
            NativePlayerBridge.startMpvSync(com.sun.jna.Pointer.nativeValue(h))
        }
    }

    LaunchedEffect(hasEverPlayed) {
        val h = mpvHandle
        if (hasEverPlayed && h != null) {
            persistentSubtitles.forEach { subUrl ->
                MpvLibrary.INSTANCE.mpv_command_string(h, "sub-add \"$subUrl\"")
            }
        }
    }

    LaunchedEffect(mpvHandle) {
        val h = mpvHandle
        if (h != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Register native property observers. These fire MPV_EVENT_PROPERTY_CHANGE (id=22)
                // at video framerate for Double props, or instantly for Flag props.
                // If MPV crashes here because of some JNI pointer bullshit, I'm quitting my job.
                MpvLibrary.INSTANCE.mpv_observe_property(h, 1L, "time-pos", 5) // MPV_FORMAT_DOUBLE
                MpvLibrary.INSTANCE.mpv_observe_property(h, 2L, "duration", 5)
                MpvLibrary.INSTANCE.mpv_observe_property(h, 3L, "pause", 3) // MPV_FORMAT_FLAG
                MpvLibrary.INSTANCE.mpv_observe_property(h, 4L, "eof-reached", 3)
                MpvLibrary.INSTANCE.mpv_observe_property(h, 5L, "volume", 5)
                MpvLibrary.INSTANCE.mpv_observe_property(h, 6L, "speed", 5)
                MpvLibrary.INSTANCE.mpv_observe_property(h, 7L, "core-idle", 3)
                MpvLibrary.INSTANCE.mpv_observe_property(h, 8L, "mute", 3)
                MpvLibrary.INSTANCE.mpv_observe_property(h, 9L, "paused-for-cache", 3)

                var lastPos = 0.0
                var lastDur = 0.0
                var lastEofReached = false
                var diagnosticLogged = false
                var playbackStartedAt = 0L

                // Separate timers so heavy track-list polling never blocks position updates
                var lastPositionPollMs = 0L // 100ms cadence — governs seek-bar smoothness
                var lastTrackPollMs = 0L // 2000ms cadence — track list / stats are slow
                var lastUiEmitMs = 0L // prevents seek-bar spam to WebView

                var lastErrorLoadfileAt = -1L // debounces error callbacks per link attempt

                while (isActive) {
                    try {
                        // Wait for an mpv event (50ms max)
                        // Keeping timeout short (50ms) ensures the fallback position poll
                        // fires at ≥10 Hz even when mpv sends no events (e.g. during initial
                        // buffering where time-pos is still 0 and no PROPERTY_CHANGE fires).
                        val eventPtr = MpvLibrary.INSTANCE.mpv_wait_event(h, 0.05)
                        if (eventPtr != null) {
                            val event = MpvLibrary.MpvEvent(eventPtr)
                            val eventId = event.event_id

                            when (eventId) {
                                2 -> break // MPV_EVENT_SHUTDOWN

                                6 -> { // MPV_EVENT_START_FILE
                                    waitingForTimePosReset = false
                                    hasEverPlayed = false
                                    playbackStartedAt = 0L
                                    diagnosticLogged = false
                                }

                                7 -> { // MPV_EVENT_END_FILE
                                    val endFilePtr = event.data
                                    if (endFilePtr != null) {
                                        val endFile = MpvLibrary.MpvEventEndFile(endFilePtr)
                                        if (endFile.reason == 4) {
                                            com.lagradost.common.logging.AppLogger.e("MPV: MPV_END_FILE_REASON_ERROR")
                                            // Don't break, let the new link restart playback!
                                            val currentLoad = loadfileIssuedAt
                                            if (lastErrorLoadfileAt != currentLoad) {
                                                lastErrorLoadfileAt = currentLoad
                                                currentOnPlaybackError("Connection rejected by source (HTTP error or dead link).")
                                            }
                                        }
                                    }
                                }

                                // MPV_EVENT_FILE_LOADED (8) or MPV_EVENT_PLAYBACK_RESTART (21)
                                // These are the EARLIEST reliable signals that frames are rendering.
                                8, 21 -> {
                                    if (!hasEverPlayed && !waitingForTimePosReset) {
                                        hasEverPlayed = true
                                        playbackStartedAt = System.currentTimeMillis()
                                        playerState?.isBuffering?.value = false
                                        playerState?.isProbing?.value = false
                                        currentOnPlaybackReady()
                                        pushMetadataToWebView() // Force push updated state now that hasEverPlayed=true
                                        // THEN wait so MPV renders actual frames before overlay fades.
                                        // Also gives Compose time to recompose and send isProbing:false.
                                        kotlinx.coroutines.delay(500)
                                        // Belt-and-suspenders: direct JS call regardless of message delivery
                                        NativePlayerBridge.executeScript("window.__dismissProbingOverlay && window.__dismissProbingOverlay()")
                                        com.lagradost.common.logging.AppLogger.i("MPV: playback started via event $eventId")
                                    }
                                }

                                22 -> { // MPV_EVENT_PROPERTY_CHANGE
                                    val propPtr = event.data
                                    if (propPtr != null) {
                                        val prop = MpvLibrary.MpvEventProperty(propPtr)
                                        val name = prop.name
                                        if (name != null && prop.format != 0 && prop.data != null) {
                                            when (name) {
                                                "time-pos" -> {
                                                    if (prop.format == 5) { // MPV_FORMAT_DOUBLE
                                                        val newPos = prop.data!!.getDouble(0)
                                                        // Accept 0.0 so that seeking to the beginning works.
                                                        // The JS UI's isSeeking lock prevents erroneous snaps to 0.
                                                        if (newPos >= 0.0) lastPos = newPos

                                                        if (!hasEverPlayed && lastPos > 0.1 && !waitingForTimePosReset) {
                                                            hasEverPlayed = true
                                                            playbackStartedAt = System.currentTimeMillis()
                                                            playerState?.isBuffering?.value = false
                                                            playerState?.isProbing?.value = false
                                                            currentOnPlaybackReady()
                                                            pushMetadataToWebView()
                                                            com.lagradost.common.logging.AppLogger.i("MPV: playback started via time-pos event pos=$lastPos")
                                                        }

                                                        // 30s one-shot diagnostic
                                                        if (!diagnosticLogged && playbackStartedAt > 0 &&
                                                            System.currentTimeMillis() - playbackStartedAt > 30_000
                                                        ) {
                                                            diagnosticLogged = true
                                                            val voInfo = MpvLibrary.getPropertyString(h, "current-vo")
                                                            val hwdecInfo = MpvLibrary.getPropertyString(h, "hwdec-current")
                                                            val codecInfo = MpvLibrary.getPropertyString(h, "video-codec")
                                                            val fpsInfo = MpvLibrary.getPropertyString(h, "estimated-vf-fps")
                                                            val widthInfo = MpvLibrary.getPropertyString(h, "width")
                                                            val heightInfo = MpvLibrary.getPropertyString(h, "height")
                                                            com.lagradost.common.logging.AppLogger.i(
                                                                "MPV 30s Diag -> VO: $voInfo, HWDEC: $hwdecInfo, Codec: $codecInfo, FPS: $fpsInfo, Res: ${widthInfo}x$heightInfo",
                                                            )
                                                        }

                                                        // Push to Kotlin state + WebView (throttled to 100ms)
                                                        val now = System.currentTimeMillis()
                                                        if (now - lastUiEmitMs >= 100) {
                                                            lastUiEmitMs = now
                                                            val posMs = (lastPos * 1000).toLong()
                                                            playerState?.updatePositionFromPlayer(posMs)
                                                            currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                                            pushMetadataToWebView()
                                                        }
                                                    }
                                                }
                                                "duration" -> {
                                                    if (prop.format == 5) {
                                                        lastDur = prop.data!!.getDouble(0)
                                                        playerState?.durationMs?.value = (lastDur * 1000).toLong()
                                                    }
                                                }
                                                "pause" -> {
                                                    if (prop.format == 3) {
                                                        playerState?.isPaused?.value = prop.data!!.getInt(0) != 0
                                                        pushMetadataToWebView()
                                                    }
                                                }
                                                "eof-reached" -> {
                                                    if (prop.format == 3) lastEofReached = prop.data!!.getInt(0) != 0
                                                }
                                                "mute" -> {
                                                    if (prop.format == 3) {
                                                        playerState?.isMuted?.value = prop.data!!.getInt(0) != 0
                                                        pushMetadataToWebView()
                                                    }
                                                }
                                                "volume" -> {
                                                    if (prop.format == 5) {
                                                        playerState?.volume?.value = prop.data!!.getDouble(0).toFloat()
                                                        pushMetadataToWebView()
                                                    }
                                                }
                                                "speed" -> {
                                                    if (prop.format == 5) {
                                                        playerState?.playbackSpeed?.value = prop.data!!.getDouble(0).toFloat()
                                                    }
                                                }
                                                "paused-for-cache" -> {
                                                    if (prop.format == 3) {
                                                        val isBufferingNow = prop.data!!.getInt(0) != 0
                                                        playerState?.isBuffering?.value = isBufferingNow
                                                        pushMetadataToWebView()
                                                    }
                                                }
                                                "core-idle" -> {
                                                    if (prop.format == 3 && !hasEverPlayed) {
                                                        playerState?.isProbing?.value = prop.data!!.getInt(0) != 0
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Fallback position poll (100ms)
                        // This fires even when mpv sends no events (buffering, idle).
                        // It is the PRIMARY seek-bar driver when native events are sparse.
                        val now = System.currentTimeMillis()
                        if (now - lastPositionPollMs >= 100L) {
                            lastPositionPollMs = now

                            // Poll duration first so seek bar percentage is correct
                            val currentDur = MpvLibrary.getPropertyDouble(h, "duration", -1.0)
                            if (currentDur > 0.0) {
                                lastDur = currentDur
                                playerState?.durationMs?.value = (currentDur * 1000).toLong()
                            }

                            // Poll position
                            val pollPos = MpvLibrary.getPropertyDouble(h, "time-pos", -1.0)
                            if (pollPos >= 0.0) lastPos = pollPos

                            // DEBUG LOG
                            if (now % 2000 < 100) {
                                com.lagradost.common.logging.AppLogger.i("DEBUG_MPV: pollPos=$pollPos, currentDur=$currentDur")
                            }

                            if (pollPos >= 0.0) {
                                lastPos = pollPos

                                // Trigger playback-ready if native events haven't done so yet
                                if (!hasEverPlayed && lastPos > 0.1 && !waitingForTimePosReset) {
                                    hasEverPlayed = true
                                    playbackStartedAt = System.currentTimeMillis()
                                    playerState?.isBuffering?.value = false
                                    playerState?.isProbing?.value = false
                                    // Same order as event handler: Compose first, then direct JS dismiss
                                    currentOnPlaybackReady()
                                    kotlinx.coroutines.delay(500)
                                    NativePlayerBridge.executeScript("window.__dismissProbingOverlay && window.__dismissProbingOverlay()")
                                    com.lagradost.common.logging.AppLogger.i("MPV: playback started via fallback poll pos=$lastPos")
                                }
                            }

                            // Always push to WebView + Kotlin state (guarantees UI stays responsive even if time-pos is missing)
                            if (now - lastUiEmitMs >= 100) {
                                lastUiEmitMs = now
                                val posMs = (lastPos * 1000).toLong()
                                playerState?.updatePositionFromPlayer(posMs)
                                currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                // Also refresh buffering flag so loading spinner stays accurate
                                MpvLibrary.getPropertyString(h, "paused-for-cache")?.let { s ->
                                    playerState?.isBuffering?.value = s == "yes"
                                }
                                pushMetadataToWebView()
                            }

                            // Completion / fast-fail / timeout checks
                            val currentLoad = loadfileIssuedAt
                            if (lastErrorLoadfileAt != currentLoad && currentLoad > 0) {
                                if (lastEofReached) {
                                    val isSeekable = MpvLibrary.getPropertyString(h, "seekable") == "yes"
                                    if (isSeekable) {
                                        val timeSinceLoad = System.currentTimeMillis() - loadStartTime
                                        if (timeSinceLoad < 2000) {
                                            lastErrorLoadfileAt = currentLoad
                                            currentOnPlaybackError("Stream is empty or corrupt.")
                                        } else if (hasEverPlayed) {
                                            lastErrorLoadfileAt = currentLoad
                                            if (lastDur > 0.0 && (lastDur - lastPos > 10.0)) {
                                                currentOnPlaybackError("Connection lost (Stream ended prematurely).")
                                            } else {
                                                currentOnFinished()
                                            }
                                        } else {
                                            lastErrorLoadfileAt = currentLoad
                                            currentOnPlaybackError("Stream failed to load or instantly ended.")
                                        }
                                    } else if (!hasEverPlayed) {
                                        lastErrorLoadfileAt = currentLoad
                                        currentOnPlaybackError("Stream failed to load or instantly ended.")
                                    }
                                    // else: live stream EOF — let mpv reconnect
                                }

                                if (lastErrorLoadfileAt != currentLoad && !hasEverPlayed && now - currentLoad > 800) {
                                    if (MpvLibrary.getPropertyString(h, "idle-active") == "yes") {
                                        lastErrorLoadfileAt = currentLoad
                                        com.lagradost.common.logging.AppLogger.e("MPV: idle-active after loadfile — dead stream")
                                        currentOnPlaybackError("Stream failed to load (connection rejected or forbidden).")
                                    }
                                }

                                val timeoutStr = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_AUTO_PLAY_TIMEOUT)
                                val userTimeoutMs = timeoutStr?.toLongOrNull() ?: 20_000L
                                val timeoutMs = maxOf(userTimeoutMs, 90_000L)
                                if (lastErrorLoadfileAt != currentLoad && !hasEverPlayed && now - loadStartTime > timeoutMs) {
                                    lastErrorLoadfileAt = currentLoad
                                    com.lagradost.common.logging.AppLogger.e("MPV: timeout after ${timeoutMs}ms")
                                    currentOnPlaybackError("Connection timed out. The stream might be dead or too slow.")
                                }
                            }
                        }

                        // Heavy track + stats poll (2000ms)
                        // Separated from position poll so ~20 mpv_get_property_string calls
                        // never stall the 100ms seek-bar update window.
                        if (now - lastTrackPollMs >= 2000L) {
                            lastTrackPollMs = now

                            val trackCount = MpvLibrary.getPropertyString(h, "track-list/count")?.toIntOrNull() ?: 0
                            val audioTracks = mutableListOf<PlayerState.VideoTrack>()
                            val subTracks = mutableListOf<PlayerState.VideoTrack>()
                            val videoTracks = mutableListOf<PlayerState.VideoTrack>()

                            for (i in 0 until trackCount) {
                                val id = MpvLibrary.getPropertyString(h, "track-list/$i/id")?.toIntOrNull() ?: continue
                                val type = MpvLibrary.getPropertyString(h, "track-list/$i/type") ?: continue
                                val lang = MpvLibrary.getPropertyString(h, "track-list/$i/lang")
                                val ttl = MpvLibrary.getPropertyString(h, "track-list/$i/title")
                                val sel = MpvLibrary.getPropertyString(h, "track-list/$i/selected") == "yes"

                                val trackName = buildString {
                                    if (!lang.isNullOrBlank()) append(lang.uppercase())
                                    if (!ttl.isNullOrBlank()) {
                                        if (isNotEmpty()) append(" - ")
                                        append(ttl)
                                    }
                                    if (isEmpty()) {
                                        append(
                                            when (type) {
                                                "audio" -> "Audio $id"
                                                "video" -> "Video $id"
                                                else -> "Subtitle $id"
                                            },
                                        )
                                    }
                                }

                                when (type) {
                                    "audio" -> audioTracks.add(PlayerState.VideoTrack(id, trackName, sel))
                                    "sub" -> subTracks.add(PlayerState.VideoTrack(id, trackName, sel))
                                    "video" -> {
                                        val res = MpvLibrary.getPropertyString(h, "track-list/$i/demux-h") ?: ""
                                        val fpsVal = MpvLibrary.getPropertyString(h, "track-list/$i/demux-fps")?.toDoubleOrNull() ?: 0.0
                                        val fn = if (res.isNotEmpty()) {
                                            if (fpsVal > 30.0) "${res}p ${fpsVal.toInt()}fps" else "${res}p"
                                        } else {
                                            trackName
                                        }
                                        videoTracks.add(PlayerState.VideoTrack(id, fn, sel))
                                    }
                                }
                            }

                            playerState?.audioTracks?.value = audioTracks
                            playerState?.subtitleTracks?.value = subTracks
                            playerState?.videoTracks?.value = videoTracks

                            if (playerState != null && playerState.showStats.value) {
                                playerState.videoCodec.value = MpvLibrary.getPropertyString(h, "video-codec") ?: "Unknown"
                                playerState.audioCodec.value = MpvLibrary.getPropertyString(h, "audio-codec") ?: "Unknown"
                                playerState.hwdecCurrent.value = MpvLibrary.getPropertyString(h, "hwdec-current") ?: "Unknown"
                                playerState.droppedFrames.value = MpvLibrary.getPropertyString(h, "vo-drop-frame-count")?.toLongOrNull() ?: 0L
                                playerState.fps.value = MpvLibrary.getPropertyString(h, "container-fps")?.toDoubleOrNull() ?: 0.0
                                val w = MpvLibrary.getPropertyString(h, "width") ?: "0"
                                val hw = MpvLibrary.getPropertyString(h, "height") ?: "0"
                                playerState.resolution.value = "${w}x$hw"
                                playerState.videoBitrate.value = MpvLibrary.getPropertyString(h, "video-bitrate")?.toLongOrNull() ?: 0L
                                playerState.audioBitrate.value = MpvLibrary.getPropertyString(h, "audio-bitrate")?.toLongOrNull() ?: 0L
                            }
                        }
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        break
                    } catch (e: Throwable) {
                        com.lagradost.common.logging.AppLogger.e("MPV event loop error: ${e.message}")
                        kotlinx.coroutines.delay(100)
                        continue
                    }
                }
            }
        }
    }

    LaunchedEffect(link, title, mpvHandle) {
        // Reset IMMEDIATELY so the concurrent event loop never sees stale `true`
        // from the previous link attempt when event 8 fires for the new link.
        // This MUST happen before any delay or blocking work below.
        hasEverPlayed = false
        waitingForTimePosReset = true
        loadfileIssuedAt = 0L

        val handle = mpvHandle ?: return@LaunchedEffect
        if (link == null) return@LaunchedEffect

        val validated = PlayerLinkHandler.validate(link, title).getOrElse {
            currentOnPlaybackError(it.message ?: "Validation failed")
            return@LaunchedEffect
        }

        val lib = MpvLibrary.INSTANCE

        // NOTE: We intentionally do NOT inject headers into demuxer-lavf-o because the
        // option string is comma-delimited and header values containing commas (like
        // User-Agent with "(KHTML, like Gecko)") break AVOption parsing and corrupt all
        // subsequent options including cenc_decryption_key.
        // MPV's http-header-fields property IS forwarded by the stream callback handler
        // to every sub-request FFmpeg makes (init.mp4, segments, etc.) as proven by
        // segments downloading successfully without 403 in testing.

        // Clear previous lavf options to prevent bleeding across stream loads
        lib.mpv_set_property_string(handle, "demuxer-lavf-o", "")
        lib.mpv_set_property_string(handle, "stream-lavf-o", "")

        val headerStr = validated.headers.entries.joinToString(separator = "\r\n", postfix = "\r\n") { (k, v) ->
            "$k: ${v.replace("\r", "").replace("\n", "")}"
        }
        val byteLength = headerStr.toByteArray(Charsets.UTF_8).size

        when (validated.streamKind) {
            PlayerLinkHandler.StreamKind.HLS -> {
                lib.mpv_set_property_string(handle, "hls-bitrate", "max")
                // 200MB forward is plenty for most 1080p HLS streams (segments are typically 2-6MB each).
                // 400MB was causing CDN rate-limiting: MPV/FFmpeg would burst-request many segments
                // at once to fill the cache, hitting 429 errors from Cloudflare/Akamai/Fastly after 20-30s.
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", "200000000") // 200MB forward
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", "30000000") // 30MB back
                lib.mpv_set_property_string(handle, "cache", "yes")
                // 30s lookahead is aggressive but won't overwhelm CDNs the way 60s did.
                lib.mpv_set_property_string(handle, "cache-secs", "30")
                lib.mpv_set_property_string(handle, "demuxer-readahead-secs", "30")
                lib.mpv_set_property_string(handle, "cache-pause-wait", "3")

                // CRITICAL: Must use mpv_set_property_string here, NOT mpv_set_option_string!
                // Options can only be set before mpv_initialize(). This runs after init,
                // so mpv_set_option_string silently ignores the entire string.
                //
                // NOTE: Do NOT include fflags=+ignidx+igndts here. Those flags disable FFmpeg's
                // index loading and DTS decoding. For well-formed HLS this breaks packet reordering,
                // causing progressive A/V desync that only becomes visible once the initial buffer
                // drains (~20-30s). extension_picky=0 alone is sufficient to accept non-standard URLs.
                lib.mpv_set_property_string(
                    handle,
                    "demuxer-lavf-o",
                    "extension_picky=0,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,reconnect_on_http_error=4xx",
                )
                // Allow demuxer to seek ahead aggressively:
                lib.mpv_set_property_string(handle, "demuxer-seekable-cache", "yes")
                lib.mpv_set_property_string(handle, "force-seekable", "yes")
            }
            PlayerLinkHandler.StreamKind.DASH -> {
                // Build DASH lavf options. cenc_decryption_key MUST be standalone —
                // do not mix with headers= as commas in header values corrupt the parse.
                val lavfDashOpts = buildString {
                    // Reconnect on HTTP errors. Commas MUST be avoided in the value to prevent
                    // corrupting MPV's option parser (which uses commas to separate key=val pairs).
                    append("extension_picky=0,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,reconnect_on_http_error=4xx")
                    if (validated.clearKeyHex != null) {
                        append(",cenc_decryption_key=${validated.clearKeyHex}")
                    }
                }
                lib.mpv_set_property_string(handle, "demuxer-lavf-o", lavfDashOpts)
                // DASH: 30MB forward buffer is plenty for 1080p segments (~4MB each)
                // Back-buffer: 5MB for live, could be more for VOD but keep conservative
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", "30000000") // 30MB forward
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", "5000000") // 5MB back
                lib.mpv_set_property_string(handle, "cache", "yes")
            }
            PlayerLinkHandler.StreamKind.PROGRESSIVE -> {
                // Raw MPEG-TS / progressive HTTP live streams need reconnect too.
                // Without this, FFmpeg treats the end of an HTTP chunk as permanent EOF.
                lib.mpv_set_property_string(
                    handle,
                    "demuxer-lavf-o",
                    "extension_picky=0,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,reconnect_on_http_error=4xx",
                )
                // Stream-level reconnect is critical for live MPEG-TS over HTTP.
                // Setting method=GET prevents Cloudflare Workers from returning 403 Forbidden to HEAD requests.
                lib.mpv_set_property_string(
                    handle,
                    "stream-lavf-o",
                    "reconnect=1,reconnect_streamed=1,reconnect_delay_max=4,method=GET",
                )
                // MKV/MP4 files from CDNs like Pixeldrain often use one-time download links.
                // Seeking forces MPV to open a new HTTP connection with a Range header, which returns 404.
                // By massively increasing the buffer and forcing seekable-cache, MPV can seek entirely in memory!
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", "400000000") // 400MB forward
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", "100000000") // 100MB back
                lib.mpv_set_property_string(handle, "cache", "yes")
                lib.mpv_set_property_string(handle, "demuxer-seekable-cache", "yes")
                lib.mpv_set_property_string(handle, "force-seekable", "yes")
            }
        }

        // Disable auto-probing of subtitles and pre-select English audio.
        // Critical for complex HLS streams with 20+ tracks.
        lib.mpv_set_property_string(handle, "alang", "eng,en")
        lib.mpv_set_property_string(handle, "sub-auto", "no")
        lib.mpv_set_property_string(handle, "sid", "no")
        lib.mpv_set_property_string(handle, "aid", "auto")

        // Boost volume up to 200%
        lib.mpv_set_property_string(handle, "volume-max", "200")

        // Force MPV to override stylized SSA/ASS subtitles (e.g. anime) so our UI settings apply
        lib.mpv_set_property_string(handle, "sub-ass-override", "force")

        val startSec = startPositionMs / 1000L
        if (startSec > 0) {
            lib.mpv_set_property_string(handle, "start", startSec.toString())
        } else {
            lib.mpv_set_property_string(handle, "start", "0")
        }

        if (validated.displayTitle.isNotBlank()) {
            lib.mpv_set_property_string(handle, "force-media-title", validated.displayTitle)
            lib.mpv_set_property_string(handle, "title", validated.displayTitle)
        }

        // Apply headers dynamically via property
        val headersStr = validated.headers.entries.joinToString(",") { "${it.key}: ${it.value.replace(",", "\\,")}" }
        if (headersStr.isNotBlank()) {
            lib.mpv_set_property_string(handle, "http-header-fields", headersStr)
        }

        val urlTarget = if (validated.useUrlFile) {
            PlayerLinkHandler.writeUrlListFile("cloudstream_mpv_url_", validated.displayTitle, validated.url).absolutePath
        } else {
            validated.url
        }

        val safeUrl = urlTarget.replace("\\", "/")
        com.lagradost.common.logging.AppLogger.i("Loading embedded MPV URL: $safeUrl")

        // Prevent "Immediate exit requested" double-load bug by safely stopping any existing internal playback first
        lib.mpv_command_string(handle, "stop")

        // Wait for MPV to clear the time-pos (so we don't accidentally fire onPlaybackReady for the old video)
        var waitAttempts = 0
        while (waitAttempts < 10) {
            try {
                val pos = MpvLibrary.getPropertyDouble(handle, "time-pos", 0.0)
                if (pos <= 0.0) break
            } catch (_: Exception) {
                break
            }
            kotlinx.coroutines.delay(50)
            waitAttempts++
        }

        loadStartTime = System.currentTimeMillis() // Reset the buffering timeout clock
        loadfileIssuedAt = System.currentTimeMillis() // Mark that loadfile is about to be sent

        lib.mpv_command_string(handle, "loadfile \"$safeUrl\"")

        // Ensure the player is unpaused when loading a new link,
        // since the UI might have paused it while buffering.
        // However, if we are resuming from a saved position, pause it so the UI can show the "Resume" dialog.
        val shouldPause = shouldPauseForResume && startSec > 0
        lib.mpv_set_property_string(handle, "pause", if (shouldPause) "yes" else "no")
        playerState?.isPaused?.value = shouldPause

        val sessionId = validated.proxySessionId
        val videoUrlHost = try {
            java.net.URI(link.url).host
        } catch (e: Exception) {
            com.lagradost.common.logging.AppLogger.w("Failed to extract host from video URL", e)
            null
        }

        val finalSubtitles = subtitles.map { sub ->
            var fixedUrl = sub.url
            if (fixedUrl.contains("*") && videoUrlHost != null) {
                try {
                    val subUri = java.net.URI(fixedUrl)
                    if (subUri.host?.contains("*") == true) {
                        fixedUrl = fixedUrl.replace(subUri.host, videoUrlHost)
                    }
                } catch (e: Exception) {
                    com.lagradost.common.logging.AppLogger.w("Failed to parse subtitle URI: $fixedUrl", e)
                }
            }
            if (sessionId != null) {
                sub.copy(url = com.lagradost.player.impl.proxy.LocalStreamProxy.buildProxyUrl(sessionId, fixedUrl))
            } else {
                sub.copy(url = fixedUrl)
            }
        }

        val capturedHandle = handle
        launch(kotlinx.coroutines.Dispatchers.IO) {
            // Wait for playback to actually start before adding subtitle tracks.
            // The main event loop owns the hasEverPlayed flag and onPlaybackReady callback.
            // We simply wait here so sub-add commands aren't sent before the file is loaded.
            var attempts = 0
            while (attempts < 150) {
                if (mpvHandle == null) break
                var waitTime = 0
                while (MpvLibrary.getPropertyDouble(capturedHandle, "time-pos", 0.0) <= 0.0 && waitTime < 30) {
                    kotlinx.coroutines.delay(200)
                    waitTime++
                }
                if (MpvLibrary.getPropertyDouble(capturedHandle, "time-pos", 0.0) > 0.0) break
                kotlinx.coroutines.delay(200)
                attempts++
            }

            val defaultSub = finalSubtitles.firstOrNull()
            finalSubtitles.forEach { sub ->
                if (mpvHandle != null) {
                    val escapedSub = sub.url.replace("\\", "\\\\").replace("\"", "\\\"")
                    val escapedTitle = sub.lang.replace("\\", "\\\\").replace("\"", "\\\"")
                    try {
                        val flag = if (sub == defaultSub) "select" else "auto"
                        lib.mpv_command_string(capturedHandle, "sub-add \"$escapedSub\" $flag \"$escapedTitle\"")
                    } catch (e: Error) {
                        // handle freed, ignore
                    }
                }
            }
        }
    }

    val videoCanvas = remember {
        object : java.awt.Canvas() {
            init {
                background = java.awt.Color.BLACK
            }
            var keyDispatcher: java.awt.KeyEventDispatcher? = null

            // Fill with black during AWT repaints to avoid exposing white window background
            // during aggressive window resizing before the C++ child container catches up.
            override fun paint(g: java.awt.Graphics?) {
                g?.color = java.awt.Color.BLACK
                g?.fillRect(0, 0, width, height)
            }

            override fun update(g: java.awt.Graphics?) {
                paint(g)
            }

            override fun addNotify() {
                super.addNotify()

                if (mpvHandle != null) return // Prevent multiple initializations (multi-audio bug)

                // Find MPV directory and tell JNA where to find the DLL
                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val mpvExe = resolveMpvExecutable(isWindows)
                if (mpvExe == null) {
                    currentOnPlaybackError("MPV executable not found.")
                    return
                }
                val mpvDir = mpvExe.parentFile
                System.setProperty("jna.library.path", mpvDir.absolutePath)

                // CRITICAL: JNA caches jna.library.path on initialization.
                // We MUST use addSearchPath to inject our bundled DLL path dynamically.
                val targets = listOf("libmpv-2", "mpv-2", "mpv-1", "mpv", "libmpv", "mpv-3.dll")
                targets.forEach { target ->
                    com.sun.jna.NativeLibrary.addSearchPath(target, mpvDir.absolutePath)
                }

                val lib = MpvLibrary.INSTANCE
                val handle = lib.mpv_create() ?: run {
                    currentOnPlaybackError("Failed to initialize MPV Engine.")
                    return
                }
                mpvHandle = handle
                playerState?.attachMpv(handle)

                // Disable native UI since we draw our own Compose UI
                lib.mpv_set_option_string(handle, "osc", "no")
                lib.mpv_set_option_string(handle, "osd-level", "0")
                lib.mpv_set_option_string(handle, "osd-bar", "no")

                // VO: use the modern 'gpu-next' output with 'd3d11' API.
                // This creates a Direct3D 11 swapchain which integrates properly with
                // Windows DirectComposition, allowing the WebView2 overlay to draw on top.
                lib.mpv_set_option_string(handle, "vo", "gpu")
                lib.mpv_set_option_string(handle, "gpu-api", "d3d11")

                // Apply User Settings & Logging
                PlayerConfig.applyMpvSettings(handle, lib)

                val widLong = com.sun.jna.Native.getComponentID(this)
                val childHwnd = NativePlayerBridge.initWebView(widLong, this.width, this.height)

                lib.mpv_set_option_string(handle, "wid", childHwnd.toString())

                lib.mpv_set_option_string(handle, "input-default-bindings", "no")
                lib.mpv_set_option_string(handle, "input-vo-keyboard", "no")
                lib.mpv_set_option_string(handle, "save-position-on-quit", "no")
                lib.mpv_set_option_string(handle, "resume-playback", "no")
                lib.mpv_set_option_string(handle, "keep-open", "yes")
                lib.mpv_set_option_string(handle, "ytdl", "no")
                lib.mpv_set_option_string(handle, "idle", "yes")

                com.lagradost.common.logging.AppLogger.i("Initializing embedded MPV Engine")
                lib.mpv_initialize(handle)

                // Post-init diagnostics: log what MPV actually chose for VO/hwdec
                // so we can debug rendering issues without enabling verbose logging
                val actualVo = MpvLibrary.getPropertyString(handle, "current-vo")
                val actualHwdec = MpvLibrary.getPropertyString(handle, "hwdec-current")
                com.lagradost.common.logging.AppLogger.i("MPV initialized: vo=$actualVo, hwdec=$actualHwdec")

                // Initialize WebView2 — write HTML into the WebView2 user data folder.
                // IMPORTANT: We write into %TEMP%\CloudStreamWebView2\ (the same folder used
                // as WebView2's userData path in C++) because WebView2 blocks file:// URLs
                // that are outside its trusted scope in production installs. Writing into
                // the userData folder makes the file:// URL trusted by WebView2.
                val webView2DataDir = File(System.getProperty("java.io.tmpdir"), "CloudStreamWebView2")
                webView2DataDir.mkdirs()
                val tempFile = File(webView2DataDir, "cloudstream_controls.html")
                val htmlContent = NativePlayerBridge::class.java.getResourceAsStream("/player-ui/controls.html")
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?.replace("{{ACCENT_COLOR}}", accentColorHex)
                    ?.replace("{{ACCENT_COLOR_RGB}}", accentColorRgb)
                    ?: ""
                if (htmlContent.isNotEmpty()) {
                    tempFile.writeText(htmlContent, Charsets.UTF_8)
                    // URL loading moved to DisposableEffect to prevent race condition with MessageListener
                } else {
                    com.lagradost.common.logging.AppLogger.e("[NativePlayer] controls.html resource not found!")
                }

                val canvas = this
                NativePlayerBridge.setEventListener(object : NativePlayerBridge.NativePlayerEventListener {
                    override fun onPlayerEvent(type: String, value: String) {
                        val h = mpvHandle ?: return
                        if (type != "message") return

                        // The C++ layer uses get_WebMessageAsJson() which returns the raw JSON
                        // object that JS posted via postMessage({type, value}).
                        // Since JS sends a raw object (not JSON.stringify'd), the C++ wstring
                        // is already a clean JSON: {"type":"togglePlay","value":""}
                        // We use regex instead of fragile substringAfter to handle both
                        // string values ("value":"123") and numeric values ("value":123).
                        val eventType = extractJsonString(value, "type")
                        val eventValue = extractJsonValue(value, "value")

                        when (eventType) {
                            "ui_ready" -> {
                                isUiReady = true
                                // WebView UI is loaded — force a resize to fix 0x0 initial bounds
                                NativePlayerBridge.resizeWebView(canvas.width, canvas.height)
                                // Push current playback state immediately so the UI isn't blank
                                val posMs = (MpvLibrary.getPropertyDouble(h, "time-pos", 0.0)).times(1000).toLong()
                                val durMs = (MpvLibrary.getPropertyDouble(h, "duration", 0.0)).times(1000).toLong()
                                pushMetadataToWebView()
                            }
                            "openOnlineSubtitleSearch" -> {
                                // Legacy no-op — search is now inside the HTML player Search tab
                            }
                            "searchSubtitles" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        // Fix JSON escape bug: extract the inner value using Jackson instead of the broken regex
                                        val cleanValue = value.replace(Regex("[\\x00-\\x1F]"), "")
                                        val rootPayload = com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanValue)
                                        val innerJson = rootPayload.get("value")?.asText()?.takeIf { it.isNotBlank() } ?: "{}"
                                        val parsed = com.fasterxml.jackson.databind.ObjectMapper().readTree(innerJson)

                                        val query = parsed["query"]?.asText() ?: ""
                                        val lang = parsed["lang"]?.asText()?.takeIf { it.isNotBlank() }
                                        val season = parsed["season"]?.asText()?.toIntOrNull()
                                        val episode = parsed["episode"]?.asText()?.toIntOrNull()

                                        val search = com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleSearch(
                                            query = query,
                                            lang = lang,
                                            seasonNumber = season,
                                            epNumber = episode,
                                        )

                                        val allResults = mutableListOf<Map<String, Any?>>()
                                        for (provider in com.lagradost.cloudstream3.syncproviders.AccountManager.subtitleProviders) {
                                            val auth = com.lagradost.cloudstream3.syncproviders.AccountManager.cachedAccounts[provider.idPrefix]?.firstOrNull()
                                            try {
                                                provider.search(auth, search)?.forEach { sub ->
                                                    allResults.add(
                                                        mapOf(
                                                            "idPrefix" to sub.idPrefix,
                                                            "name" to sub.name,
                                                            "lang" to sub.lang,
                                                            "data" to sub.data,
                                                            "source" to sub.source,
                                                            "seasonNumber" to sub.seasonNumber,
                                                            "epNumber" to sub.epNumber,
                                                        ),
                                                    )
                                                }
                                            } catch (e: Exception) {
                                                com.lagradost.common.logging.AppLogger.e("SubSearch[${provider.name}]: ${e.message}")
                                            }
                                        }

                                        val json = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                            mapOf("type" to "subtitle_search_results", "results" to allResults),
                                        )
                                        NativePlayerBridge.postMessage(json)
                                    } catch (e: Exception) {
                                        com.lagradost.common.logging.AppLogger.e("searchSubtitles: ${e.message}")
                                    }
                                }
                            }
                            "downloadSubtitle" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    try {
                                        val cleanValue = value.replace(Regex("[\\x00-\\x1F]"), "")
                                        val rootPayload = com.fasterxml.jackson.databind.ObjectMapper().readTree(cleanValue)
                                        val innerJson = rootPayload.get("value")?.asText()?.takeIf { it.isNotBlank() } ?: "{}"
                                        val parsed = com.fasterxml.jackson.databind.ObjectMapper().readTree(innerJson)

                                        val idPrefix = parsed["idPrefix"]?.asText() ?: return@launch
                                        val data = parsed["data"]?.asText() ?: return@launch
                                        val name = parsed["name"]?.asText() ?: "subtitle"

                                        val provider = com.lagradost.cloudstream3.syncproviders.AccountManager.subtitleProviders.firstOrNull { it.idPrefix == idPrefix }
                                        if (provider != null) {
                                            val auth = com.lagradost.cloudstream3.syncproviders.AccountManager.cachedAccounts[idPrefix]?.firstOrNull()

                                            // Subtitle data object
                                            val sub = com.lagradost.cloudstream3.subtitles.AbstractSubtitleEntities.SubtitleEntity(
                                                idPrefix = idPrefix,
                                                name = name,
                                                data = data,
                                                lang = parsed["lang"]?.asText() ?: "",
                                                source = parsed["source"]?.asText() ?: "",
                                            )

                                            val fileUrl = provider.load(auth, sub)
                                            if (fileUrl != null) {
                                                var finalUrl: String = fileUrl
                                                val cleanUrl = fileUrl.substringBefore("?")
                                                if (cleanUrl.endsWith(".zip", ignoreCase = true)) {
                                                    val zipFile = if (fileUrl.startsWith("http", ignoreCase = true)) {
                                                        val tmp = java.io.File.createTempFile("sub", ".zip")
                                                        val res = com.lagradost.cloudstream3.app.get(fileUrl).okhttpResponse
                                                        val bytes = res.body?.bytes()
                                                        if (bytes != null) {
                                                            tmp.writeBytes(bytes)
                                                            tmp
                                                        } else {
                                                            null
                                                        }
                                                    } else if (fileUrl.startsWith("file://", ignoreCase = true)) {
                                                        java.io.File(java.net.URI(fileUrl))
                                                    } else {
                                                        java.io.File(fileUrl)
                                                    }

                                                    if (zipFile != null && zipFile.exists()) {
                                                        java.util.zip.ZipFile(zipFile).use { zip ->
                                                            val entry = zip.entries().toList().firstOrNull {
                                                                it.name.endsWith(".srt", true) || it.name.endsWith(".vtt", true) || it.name.endsWith(".ass", true)
                                                            }
                                                            if (entry != null) {
                                                                val ext = "." + entry.name.substringAfterLast('.', "srt")
                                                                val extracted = java.io.File.createTempFile("sub_ext", ext)
                                                                zip.getInputStream(entry).use { input ->
                                                                    extracted.outputStream().use { output ->
                                                                        input.copyTo(output)
                                                                    }
                                                                }
                                                                finalUrl = extracted.absolutePath
                                                            }
                                                        }
                                                    }
                                                }
                                                // Load it into MPV (replace backslashes for Windows path escaping)
                                                val safeUrl = finalUrl.replace("\\", "/")
                                                if (!persistentSubtitles.contains(safeUrl)) {
                                                    persistentSubtitles.add(safeUrl)
                                                }
                                                MpvLibrary.INSTANCE.mpv_command_string(h, "sub-add \"$safeUrl\"")

                                                val toastJson = com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                                                    mapOf("type" to "show_toast", "message" to "Successfully extracted and loaded subtitle"),
                                                )
                                                NativePlayerBridge.postMessage(toastJson)

                                                com.lagradost.common.logging.AppLogger.i("Loaded subtitle from $provider: $finalUrl")
                                            }
                                        }
                                    } catch (e: Exception) {
                                        com.lagradost.common.logging.AppLogger.e("downloadSubtitle: ${e.message}")
                                    }
                                }
                            }
                            "togglePlay" -> {
                                // Handled directly in C++ fast-path (cycle pause).
                                // Kotlin must NOT also call cycle pause here — that would double-toggle
                                // and cancel out the C++ command, making the button appear broken.
                            }
                            "seekTo" -> {
                                val ms = eventValue.toDoubleOrNull() ?: 0.0
                                val sec = ms / 1000.0
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    // Overwrite 'start' property so if MPV hasn't finished loading the stream yet,
                                    // the new start time is respected (prevents "Start Over" from being ignored).
                                    MpvLibrary.INSTANCE.mpv_set_property_string(h, "start", sec.toString())
                                    // Use absolute seek (not keyframes) for precise scrubbing
                                    MpvLibrary.INSTANCE.mpv_command_string(h, java.lang.String.format(java.util.Locale.US, "seek %f absolute", sec))
                                }
                            }
                            "seekBy" -> {
                                val ms = eventValue.toDoubleOrNull() ?: 0.0
                                val sec = ms / 1000.0
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    MpvLibrary.INSTANCE.mpv_command_string(h, java.lang.String.format(java.util.Locale.US, "seek %f relative", sec))
                                }
                            }
                            "toggleMute" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    MpvLibrary.INSTANCE.mpv_command_string(h, "cycle mute")
                                }
                            }
                            "setVolume" -> {
                                val vol = eventValue.toDoubleOrNull()
                                if (vol != null) {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, java.lang.String.format(java.util.Locale.US, "set volume %f", vol))
                                    }
                                }
                            }
                            "toggleFullscreen" -> {
                                currentOnFullscreenToggle?.invoke()
                            }
                            "exitPlayer" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    currentOnCloseRequest()
                                }
                            }
                            "changeLink" -> {
                                eventValue.toIntOrNull()?.let { linkIdx ->
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                        onLinkChange?.invoke(linkIdx)
                                    }
                                }
                            }
                            "loadEpisode" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    onEpisodeChange?.invoke(eventValue)
                                }
                            }
                            "setAudioTrack" -> {
                                val id = eventValue.toIntOrNull()
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.setAudioTrack(id)
                                }
                            }
                            "loadLazyAudioTrack" -> {
                                val url = eventValue
                                val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.value.find { it.url == url }
                                if (track != null) {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        playerState?.loadLazyAudioTrack(
                                            com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language),
                                        )
                                    }
                                }
                            }
                            "loadLazySubtitleTrack" -> {
                                val url = eventValue
                                val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazySubtitleTracks.value.find { it.url == url }
                                if (track != null) {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        playerState?.loadLazySubtitleTrack(
                                            com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language),
                                        )
                                    }
                                }
                            }
                            "loadLazyVideoTrack" -> {
                                val url = eventValue
                                val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.value.find { it.url == url }
                                if (track != null) {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        playerState?.loadLazyVideoTrack(
                                            com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language),
                                        )
                                    }
                                }
                            }
                            "setSubtitleTrack" -> {
                                val id = eventValue.toIntOrNull()
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.setSubtitleTrack(id)
                                }
                            }
                            "loadNextEpisode" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    onNextEpisode?.invoke()
                                }
                            }
                            "replayEpisode" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    onReplayEpisode?.invoke()
                                }
                            }
                            "setMpvProperty" -> {
                                val parts = eventValue.split(":", limit = 2)
                                if (parts.size == 2) {
                                    coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                        MpvLibrary.INSTANCE.mpv_set_property_string(h, parts[0], parts[1])
                                    }
                                }
                            }
                            "toggleStats" -> {
                                // Handled directly in C++ (flips g_statsVisible to gate stats_update messages).
                                // No native MPV OSD command needed — our WebView panel replaces stats.lua.
                            }
                            "skipScraping" -> {
                                coroutineScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                    onSkipScraping?.invoke()
                                }
                            }
                        }
                    }
                })

                if (tempFile.exists()) {
                    NativePlayerBridge.loadUrl(tempFile.absoluteFile.toURI().toString())
                }

                // Listen to resize events and forward to WebView2
                this.addComponentListener(object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) {
                        NativePlayerBridge.resizeWebView(e.component.width, e.component.height)
                    }
                })
            }

            override fun removeNotify() {
                // Instantly hide and shrink the component to 0x0 to prevent the Skia/AWT
                // teardown gap from exposing a white flash.
                this.isVisible = false
                this.bounds = java.awt.Rectangle(0, 0, 0, 0)
                NativePlayerBridge.resizeWebView(0, 0)

                // Find and remove the dispatcher to prevent memory leaks
                val focusManager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                this.keyDispatcher?.let {
                    focusManager.removeKeyEventDispatcher(it)
                }

                val h = mpvHandle
                mpvHandle = null
                playerState?.detachMpv()

                // DEFERRED CLEANUP: Wait 150ms for Compose/Skia to draw the DetailsScreen
                // over the empty space before actually destroying the native window.
                Thread {
                    Thread.sleep(150)
                    NativePlayerBridge.destroyWebView()
                    if (h != null) {
                        MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                    }
                }.start()

                super.removeNotify()
            }
        }.apply {
            background = Color.BLACK
            isFocusable = true
        }
    }

    LaunchedEffect(isExiting) {
        if (isExiting) {
            videoCanvas.isVisible = false
            videoCanvas.bounds = java.awt.Rectangle(0, 0, 0, 0)
            NativePlayerBridge.resizeWebView(0, 0)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            val h = mpvHandle
            if (h != null) {
                mpvHandle = null
                NativePlayerBridge.stopMpvSync()
                Thread {
                    MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                }.start()
                playerState?.detachMpv()
            }
        }
    }

    SwingPanel(
        background = androidx.compose.ui.graphics.Color.Black,
        factory = { videoCanvas },
        modifier = modifier,
    )
}

private fun resolveMpvExecutable(isWindows: Boolean): File? {
    val names = if (isWindows) listOf("libmpv-2.dll") else listOf("libmpv.so", "libmpv.dylib")

    val resDir = System.getProperty("compose.application.resources.dir")

    val candidates = listOfNotNull(
        resDir?.let { File(it, "mpv") },
        File("mpv"),
        File("2_cloudstream_desktop/mpv"),
        File("desktop-app/mpv"),
        File("desktop-app/appResources/mpv"),
        File("desktop-app/appResources/windows/mpv"),
    )
    for (base in candidates) {
        for (name in names) {
            val f = File(base, name)
            if (f.isFile) return f.absoluteFile
        }
    }
    return null
}

private fun awtKeyToMpv(e: KeyEvent): String? {
    if (e.isShiftDown) {
        when (e.keyCode) {
            KeyEvent.VK_3 -> return "#"
            KeyEvent.VK_1 -> return "!"
            KeyEvent.VK_2 -> return "@"
            KeyEvent.VK_4 -> return "$"
            KeyEvent.VK_5 -> return "%"
            KeyEvent.VK_6 -> return "^"
            KeyEvent.VK_7 -> return "&"
            KeyEvent.VK_8 -> return "*"
            KeyEvent.VK_9 -> return "("
            KeyEvent.VK_0 -> return ")"
            KeyEvent.VK_OPEN_BRACKET -> return "{"
            KeyEvent.VK_CLOSE_BRACKET -> return "}"
            KeyEvent.VK_COMMA -> return "<"
            KeyEvent.VK_PERIOD -> return ">"
            KeyEvent.VK_MINUS -> return "_"
            KeyEvent.VK_EQUALS -> return "+"
            KeyEvent.VK_Q -> return "QUIT_OVERRIDE"
            in KeyEvent.VK_A..KeyEvent.VK_Z -> {
                val letter = KeyEvent.getKeyText(e.keyCode).uppercase()
                val ctrl = if (e.isControlDown) "Ctrl+" else ""
                val alt = if (e.isAltDown) "Alt+" else ""
                return "$ctrl$alt$letter"
            }
        }
    }

    val baseKey = when (e.keyCode) {
        KeyEvent.VK_SPACE -> "SPACE"
        KeyEvent.VK_LEFT -> "LEFT"
        KeyEvent.VK_RIGHT -> "RIGHT"
        KeyEvent.VK_UP -> "UP"
        KeyEvent.VK_DOWN -> "DOWN"
        KeyEvent.VK_ENTER -> "ENTER"
        KeyEvent.VK_ESCAPE -> "ESC"
        KeyEvent.VK_BACK_SPACE -> "BS"
        KeyEvent.VK_DELETE -> "DEL"
        KeyEvent.VK_TAB -> "TAB"
        KeyEvent.VK_PAGE_UP -> "PGUP"
        KeyEvent.VK_PAGE_DOWN -> "PGDWN"
        KeyEvent.VK_HOME -> "HOME"
        KeyEvent.VK_END -> "END"

        KeyEvent.VK_Q -> "QUIT_OVERRIDE"

        in KeyEvent.VK_A..KeyEvent.VK_Z -> KeyEvent.getKeyText(e.keyCode).lowercase()
        in KeyEvent.VK_0..KeyEvent.VK_9 -> KeyEvent.getKeyText(e.keyCode)

        KeyEvent.VK_COMMA -> ","
        KeyEvent.VK_PERIOD -> "."
        KeyEvent.VK_SLASH, KeyEvent.VK_DIVIDE -> "/"
        KeyEvent.VK_MULTIPLY -> "*"
        KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> "-"
        KeyEvent.VK_PLUS, KeyEvent.VK_ADD, KeyEvent.VK_EQUALS -> "+"
        KeyEvent.VK_OPEN_BRACKET -> "["
        KeyEvent.VK_CLOSE_BRACKET -> "]"
        KeyEvent.VK_BACK_SLASH -> "\\"
        KeyEvent.VK_SEMICOLON -> ";"
        KeyEvent.VK_QUOTE -> "'"

        else -> return null
    }

    val alt = if (e.isAltDown) "Alt+" else ""
    val ctrl = if (e.isControlDown) "Ctrl+" else ""
    val shift = if (e.isShiftDown && e.keyCode !in KeyEvent.VK_A..KeyEvent.VK_Z && baseKey.length > 1) "Shift+" else ""

    return "$ctrl$alt$shift$baseKey"
}
