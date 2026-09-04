package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.desktop.ui.components.PlayerShortcutsModal
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

private val ISO_639_LANG_MAP = mapOf(
    "eng" to "English", "en" to "English",
    "spa" to "Spanish", "es" to "Spanish",
    "fra" to "French", "fre" to "French", "fr" to "French",
    "deu" to "German", "ger" to "German", "de" to "German",
    "hin" to "Hindi", "hi" to "Hindi",
    "tam" to "Tamil", "ta" to "Tamil",
    "tel" to "Telugu", "te" to "Telugu",
    "mal" to "Malayalam", "ml" to "Malayalam",
    "kan" to "Kannada", "kn" to "Kannada",
    "kor" to "Korean", "ko" to "Korean",
    "jpn" to "Japanese", "ja" to "Japanese",
    "rus" to "Russian", "ru" to "Russian",
    "ara" to "Arabic", "ar" to "Arabic",
    "zho" to "Chinese", "chi" to "Chinese", "zh" to "Chinese",
    "ita" to "Italian", "it" to "Italian",
    "por" to "Portuguese", "pt" to "Portuguese",
    "tur" to "Turkish", "tr" to "Turkish",
    "vie" to "Vietnamese", "vi" to "Vietnamese",
    "tha" to "Thai", "th" to "Thai",
    "ind" to "Indonesian", "id" to "Indonesian",
    "pol" to "Polish", "pl" to "Polish",
    "nld" to "Dutch", "dut" to "Dutch", "nl" to "Dutch",
    "swe" to "Swedish", "sv" to "Swedish",
    "nor" to "Norwegian", "no" to "Norwegian",
    "dan" to "Danish", "da" to "Danish",
    "fin" to "Finnish", "fi" to "Finnish",
    "ell" to "Greek", "gre" to "Greek", "el" to "Greek",
    "heb" to "Hebrew", "he" to "Hebrew",
    "hun" to "Hungarian", "hu" to "Hungarian",
    "ces" to "Czech", "cze" to "Czech", "cs" to "Czech",
    "ron" to "Romanian", "rum" to "Romanian", "ro" to "Romanian",
    "ukr" to "Ukrainian", "uk" to "Ukrainian",
    "ben" to "Bengali", "bn" to "Bengali",
    "fil" to "Filipino", "tl" to "Filipino",
    "msa" to "Malay", "may" to "Malay", "ms" to "Malay",
    "fas" to "Persian", "per" to "Persian", "fa" to "Persian",
    "und" to "Undetermined",
)

private val URL_AND_DOMAIN_REGEX = Regex("""(?i)(https?://\S+|www\.\S+|(\b[a-z0-9-]+\.(com|org|net|cc|to|is|ru|me|tv|cx|ws|site|top|club|vip|app|link|xyz|info|biz|co|in|live|stream|xyz)\b))""")
private val JUNK_PREFIX_REGEX = Regex("""(?i)^\s*(\[.*?\]|\(.*?\)|Encoded by.*|Downloaded from.*|Rip by.*|Subtitles by.*|Synced by.*|www\..*?|-)\s*""")

internal fun cleanTrackDisplayName(
    type: String,
    id: Int,
    rawTitle: String?,
    rawLang: String?,
    codec: String? = null,
    isForced: Boolean = false,
    isDefault: Boolean = false,
    isExternal: Boolean = false,
    channels: String? = null,
): String {
    val cleanLang = rawLang?.trim()?.lowercase()
    val resolvedLang = if (!cleanLang.isNullOrBlank()) {
        ISO_639_LANG_MAP[cleanLang] ?: try {
            val loc = java.util.Locale(cleanLang)
            val d = loc.getDisplayLanguage(java.util.Locale.ENGLISH)
            if (d.isNotBlank() && !d.equals(cleanLang, ignoreCase = true)) d else cleanLang.uppercase()
        } catch (_: Throwable) {
            cleanLang.uppercase()
        }
    } else null

    var title = rawTitle?.trim() ?: ""

    // Strip URLs and Domain references (e.g. www.sitename.com, site.org)
    title = URL_AND_DOMAIN_REGEX.replace(title, "").trim()
    title = JUNK_PREFIX_REGEX.replace(title, "").trim()
    title = title.replace(Regex("""^[-\s_–—:|\[\](){}]+|[-\s_–—:|\[\](){}]+$"""), "").trim()

    val lowerTitle = title.lowercase()
    val forcedDetected = isForced || lowerTitle.contains("forced")
    val sdhDetected = lowerTitle.contains("sdh") || lowerTitle.contains("cc") || lowerTitle.contains("hearing impaired") || lowerTitle.contains("hi")
    val pgsDetected = codec?.contains("pgs", ignoreCase = true) == true || lowerTitle.contains("pgs")
    val vobsubDetected = codec?.contains("vobsub", ignoreCase = true) == true || lowerTitle.contains("vobsub")

    var baseName = when {
        title.isNotBlank() && title.length >= 2 && !title.matches(Regex("""^\d+$""")) -> {
            if (resolvedLang != null) {
                val lowerLang = resolvedLang.lowercase()
                if (!lowerTitle.contains(lowerLang)) {
                    "$resolvedLang ($title)"
                } else {
                    title
                }
            } else {
                title
            }
        }
        resolvedLang != null -> resolvedLang
        else -> if (type == "audio") "Audio $id" else if (type == "video") "Video $id" else "Subtitle $id"
    }

    if (type == "sub") {
        val extraTags = mutableListOf<String>()
        if (forcedDetected && !baseName.contains("Forced", ignoreCase = true)) {
            extraTags.add("Forced")
        } else if (isDefault && !baseName.contains("Default", ignoreCase = true) && !forcedDetected) {
            extraTags.add("Default")
        }
        if (sdhDetected && !baseName.contains("SDH", ignoreCase = true)) {
            extraTags.add("SDH")
        }
        if (pgsDetected && !baseName.contains("PGS", ignoreCase = true)) {
            extraTags.add("PGS")
        } else if (vobsubDetected && !baseName.contains("VobSub", ignoreCase = true)) {
            extraTags.add("VobSub")
        }
        if (isExternal && !baseName.contains("External", ignoreCase = true)) {
            extraTags.add("External")
        }

        if (extraTags.isNotEmpty()) {
            baseName = "$baseName [${extraTags.joinToString(", ")}]"
        }
    } else if (type == "audio") {
        val ch = channels?.trim()
        if (!ch.isNullOrBlank() && !baseName.contains(ch, ignoreCase = true)) {
            val friendlyChannel = when (ch) {
                "5.1", "6" -> "5.1 Surround"
                "7.1", "8" -> "7.1 Surround"
                "2", "stereo" -> "Stereo"
                else -> ch
            }
            if (!baseName.contains(friendlyChannel, ignoreCase = true)) {
                baseName = "$baseName ($friendlyChannel)"
            }
        }
    }

    return baseName
}

@Composable
fun BaseMpvPlayer(
    link: ExtractorLink?,
    title: String?,
    subtitles: List<com.lagradost.cloudstream3.SubtitleFile>,
    startPositionMs: Long,
    shouldPauseForResume: Boolean = false,
    onPlaybackReady: () -> Unit,
    onPlaybackError: (String) -> Unit,
    onFinished: () -> Unit,
    onPositionChange: (Long, Long) -> Unit,
    onCloseRequest: () -> Unit,
    isExiting: Boolean = false,
    isLive: Boolean = false,
    onFullscreenToggle: (() -> Unit)? = null,
    playerState: com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState? = null,
    modifier: Modifier = Modifier.fillMaxSize(),
    // Called once when the MPV event loop is running, before any loadfile.
    // Used by ComposeNativeWebPlayer to start the C++ sync thread.
    onEventLoopReady: ((handle: com.sun.jna.Pointer) -> Unit)? = null,
    // Called after mpv_create() but before mpv_initialize().
    // Use this to override VO, WID, or other pre-init options.
    onPreInitialize: ((handle: com.sun.jna.Pointer, canvasWid: Long, width: Int, height: Int) -> Unit)? = null,
    // Called immediately after mpv_initialize(). Use this to run post-init setup (like WebView).
    onPostInitialize: ((handle: com.sun.jna.Pointer) -> Unit)? = null,
    videoRenderer: @Composable (videoCanvas: Canvas, mpvHandle: com.sun.jna.Pointer?) -> Unit,
) {
    var mpvHandle by remember { mutableStateOf<com.sun.jna.Pointer?>(null) }
    var hasEverPlayed by remember { mutableStateOf(false) }
    var showShortcutsModal by remember { mutableStateOf(false) }
    // Guards against false-positive onPlaybackReady after a stop()+loadfile sequence.
    // Set to true just before loadfile, cleared on MPV_EVENT_START_FILE.
    var waitingForTimePosReset by remember { mutableStateOf(false) }

    val currentOnPlaybackReady by rememberUpdatedState(onPlaybackReady)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnFullscreenToggle by rememberUpdatedState(onFullscreenToggle)
    val currentOnShowShortcuts: () -> Unit by rememberUpdatedState({ showShortcutsModal = true })

    LaunchedEffect(mpvHandle) {
        val h = mpvHandle
        if (h != null) {
            onEventLoopReady?.invoke(h)
            var loops = 0
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                // Setup native observers for critical instant-response properties
                MpvLibrary.INSTANCE.mpv_observe_property(h, 1L, "time-pos", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 2L, "duration", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 3L, "pause", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 4L, "eof-reached", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 5L, "volume", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 6L, "speed", 5) // Double
                MpvLibrary.INSTANCE.mpv_observe_property(h, 7L, "core-idle", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 8L, "paused-for-cache", 3) // Flag
                MpvLibrary.INSTANCE.mpv_observe_property(h, 9L, "mute", 3) // Flag

                var lastPos = 0.0
                var lastDur = 0.0
                // Separate timers so heavy track-list polling never blocks position updates
                var lastPositionPollMs = 0L // 100ms cadence — governs seek-bar smoothness
                var lastTrackPollMs = 0L // 2000ms cadence — track list / stats are slow
                var lastUiPositionEmit = 0L
                var diagnosticLogged = false
                var playbackStartedAt = 0L
                var hasAutoSwitchedAudio = false

                fun pollTracksAndChapters(handle: com.sun.jna.Pointer) {
                    val trackCountStr = MpvLibrary.getPropertyString(handle, "track-list/count")
                    val trackCount = trackCountStr?.toIntOrNull() ?: 0

                    val audioTracks = mutableListOf<PlayerState.VideoTrack>()
                    val subTracks = mutableListOf<PlayerState.VideoTrack>()
                    val videoTracks = mutableListOf<PlayerState.VideoTrack>()
                    val audioSearchInfo = mutableListOf<Triple<Int, Boolean, String>>()

                    for (i in 0 until trackCount) {
                        val id = MpvLibrary.getPropertyString(handle, "track-list/$i/id")?.toIntOrNull() ?: continue
                        val type = MpvLibrary.getPropertyString(handle, "track-list/$i/type") ?: continue
                        val lang = MpvLibrary.getPropertyString(handle, "track-list/$i/lang")
                        val title = MpvLibrary.getPropertyString(handle, "track-list/$i/title")
                        val codec = MpvLibrary.getPropertyString(handle, "track-list/$i/codec")
                        val isForced = MpvLibrary.getPropertyString(handle, "track-list/$i/forced") == "yes"
                        val isDefault = MpvLibrary.getPropertyString(handle, "track-list/$i/default") == "yes"
                        val isOriginal = MpvLibrary.getPropertyString(handle, "track-list/$i/original") == "yes"
                        val isExternal = MpvLibrary.getPropertyString(handle, "track-list/$i/external") == "yes"
                        val channels = MpvLibrary.getPropertyString(handle, "track-list/$i/audio-channels")
                            ?: MpvLibrary.getPropertyString(handle, "track-list/$i/demux-channel-count")
                        val selected = MpvLibrary.getPropertyString(handle, "track-list/$i/selected") == "yes"

                        val name = cleanTrackDisplayName(
                            type = type,
                            id = id,
                            rawTitle = title,
                            rawLang = lang,
                            codec = codec,
                            isForced = isForced,
                            isDefault = isDefault,
                            isExternal = isExternal,
                            channels = channels,
                        )
                        if (type == "audio") {
                            audioTracks.add(PlayerState.VideoTrack(id, name, selected))
                            audioSearchInfo.add(Triple(id, selected, "${lang.orEmpty()} ${title.orEmpty()} $name ${if (isOriginal) "original" else ""}"))
                        } else if (type == "sub") {
                            subTracks.add(PlayerState.VideoTrack(id, name, selected))
                        } else if (type == "video") {
                            val res = MpvLibrary.getPropertyString(handle, "track-list/$i/demux-h") ?: ""
                            val fpsVal = MpvLibrary.getPropertyString(handle, "track-list/$i/demux-fps")?.toDoubleOrNull() ?: 0.0
                            val finalName = if (res.isNotEmpty()) {
                                if (fpsVal > 30.0) "${res}p ${fpsVal.toInt()}fps" else "${res}p"
                            } else {
                                name
                            }
                            videoTracks.add(PlayerState.VideoTrack(id, finalName, selected))
                        }
                    }

                    // Disambiguate duplicate names so each track is uniquely identified
                    val subNameCounts = subTracks.groupingBy { it.name }.eachCount()
                    val subDupTracker = mutableMapOf<String, Int>()
                    val disambiguatedSubTracks = subTracks.map { track ->
                        if ((subNameCounts[track.name] ?: 0) > 1) {
                            val idx = (subDupTracker[track.name] ?: 0) + 1
                            subDupTracker[track.name] = idx
                            track.copy(name = "${track.name} #$idx")
                        } else {
                            track
                        }
                    }

                    val audioNameCounts = audioTracks.groupingBy { it.name }.eachCount()
                    val audioDupTracker = mutableMapOf<String, Int>()
                    val disambiguatedAudioTracks = audioTracks.map { track ->
                        if ((audioNameCounts[track.name] ?: 0) > 1) {
                            val idx = (audioDupTracker[track.name] ?: 0) + 1
                            audioDupTracker[track.name] = idx
                            track.copy(name = "${track.name} #$idx")
                        } else {
                            track
                        }
                    }

                    playerState?._audioTracks?.value = disambiguatedAudioTracks
                    playerState?._subtitleTracks?.value = disambiguatedSubTracks
                    playerState?._videoTracks?.value = videoTracks

                    // Auto-select preferred audio track if not already selected by mpv
                    if (!hasAutoSwitchedAudio && audioSearchInfo.isNotEmpty()) {
                        val prefAudio = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_AUDIO_LANG) ?: "auto"
                        if (prefAudio != "auto" && prefAudio.isNotBlank()) {
                            val target = audioSearchInfo.firstOrNull { (_, _, meta) ->
                                LanguageMatcher.matchesAudioTrack(null, null, meta, prefAudio)
                            }
                            if (target != null) {
                                if (!target.second) { // not already selected
                                    com.lagradost.common.logging.AppLogger.i("Player:MPV", "Auto-switching audio track to id=${target.first}")
                                    MpvLibrary.INSTANCE.mpv_set_property_string(handle, "aid", target.first.toString())
                                }
                                hasAutoSwitchedAudio = true
                            }
                        }
                    }

                    // Auto-attach active, preferred, or default audio track if MPV has 0 native audio tracks loaded
                    val lazyAudios = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.value
                    if (audioTracks.isEmpty() && lazyAudios.isNotEmpty()) {
                        val currentActiveUrl = playerState?.activeLazyAudioTrackUrl?.value
                        val targetTrack = if (currentActiveUrl != null) {
                            lazyAudios.find { it.url == currentActiveUrl } ?: lazyAudios.first()
                        } else {
                            val prefLang = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_AUDIO_LANG) ?: "auto"
                            if (prefLang != "auto" && prefLang.isNotBlank()) {
                                val keywords = LanguageMatcher.getKeywordsForCode(prefLang)
                                lazyAudios.firstOrNull { audio ->
                                    val combined = "${audio.language} ${audio.name}".lowercase()
                                    keywords.any { kw -> combined.contains(kw) }
                                } ?: lazyAudios.first()
                            } else {
                                lazyAudios.first()
                            }
                        }
                        com.lagradost.common.logging.AppLogger.i("Player:MPV", "Auto-attaching audio track: ${targetTrack.name}")
                        playerState?.loadLazyAudioTrack(PlayerState.LazyTrack(targetTrack.url, targetTrack.name, targetTrack.language, targetTrack.bitrate))
                    }

                    val w = MpvLibrary.getPropertyString(handle, "width") ?: "0"
                    val hw = MpvLibrary.getPropertyString(handle, "height") ?: "0"
                    if (w != "0" && hw != "0") {
                        playerState?._resolution?.value = "${w}x$hw"
                    }

                    // Poll Chapters
                    val chapterCount = MpvLibrary.getPropertyString(handle, "chapter-list/count")?.toIntOrNull() ?: 0
                    if (chapterCount > 0) {
                        val chapters = mutableListOf<PlayerState.Chapter>()
                        for (c in 0 until chapterCount) {
                            val chTitle = MpvLibrary.getPropertyString(handle, "chapter-list/$c/title") ?: "Chapter ${c + 1}"
                            val chTime = MpvLibrary.getPropertyString(handle, "chapter-list/$c/time")?.toDoubleOrNull() ?: 0.0
                            chapters.add(PlayerState.Chapter(index = c, title = chTitle, timeMs = (chTime * 1000).toLong()))
                        }
                        playerState?.updateChaptersFromPlayer(chapters)
                        val currentChapter = MpvLibrary.getPropertyString(handle, "chapter")?.toIntOrNull() ?: -1
                        playerState?._currentChapterIndex?.value = currentChapter
                        com.lagradost.common.logging.AppLogger.i("Player:MPV", "Extracted $chapterCount chapters from stream")
                    } else {
                        playerState?.updateChaptersFromPlayer(emptyList())
                        playerState?._currentChapterIndex?.value = -1
                    }
                }

                var lastStreamErrorReason: String? = null

                while (isActive) {
                    try {
                        // Block IO thread for up to 50ms waiting for an event.
                        // Keeping timeout short ensures the fallback position poll fires at ≥10 Hz
                        // even when mpv sends no events (e.g. during initial buffering).
                        val eventPtr = MpvLibrary.INSTANCE.mpv_wait_event(h, 0.05)
                        if (eventPtr != null) {
                            val event = MpvLibrary.MpvEvent(eventPtr)
                            val eventId = event.event_id

                            when (eventId) {
                                1 -> {
                                    com.lagradost.common.logging.AppLogger.i("Player:MPV", "MPV Engine shutting down (MPV_EVENT_SHUTDOWN)")
                                    break
                                }

                                6 -> { // MPV_EVENT_START_FILE — a new file is being loaded
                                    com.lagradost.common.logging.AppLogger.i("Player:MPV", "Starting new media stream (MPV_EVENT_START_FILE)")
                                    lastStreamErrorReason = null
                                    waitingForTimePosReset = false
                                    hasEverPlayed = false
                                    playbackStartedAt = 0L
                                    diagnosticLogged = false
                                    hasAutoSwitchedAudio = false
                                }

                                7 -> { // MPV_EVENT_END_FILE
                                    val endFilePtr = event.data
                                    if (endFilePtr != null) {
                                        val endFile = MpvLibrary.MpvEventEndFile(endFilePtr)
                                        com.lagradost.common.logging.AppLogger.i("Player:MPV", "Media ended (reason=${endFile.reason}, error=${endFile.error})")

                                        // 0 = EOF, 2 = STOP, 3 = QUIT, 4 = ERROR
                                        if (endFile.reason == 4) { // MPV_END_FILE_REASON_ERROR
                                            val errDesc = lastStreamErrorReason ?: when (endFile.error) {
                                                -2 -> "Failed to load stream (Dead link or HTTP Error)"
                                                -3 -> "Stream format unsupported"
                                                -4 -> "No audio/video streams found"
                                                -8 -> "Connection timed out"
                                                else -> "Stream playback error (code ${endFile.error})"
                                            }
                                            com.lagradost.common.logging.AppLogger.e("Player:MPV", "MPV stream error (MPV_END_FILE_REASON_ERROR, code=${endFile.error}): $errDesc")
                                            currentOnPlaybackError(errDesc)
                                        } else if (endFile.reason == 0) { // MPV_END_FILE_REASON_EOF
                                            if (isLive) {
                                                com.lagradost.common.logging.AppLogger.w("Player:MPV", "Live stream EOF encountered. Auto-recovering to live edge...")
                                                try {
                                                    MpvLibrary.INSTANCE.mpv_command_string(h, "seek 100 absolute-percent")
                                                } catch (e: Exception) {
                                                    com.lagradost.common.logging.AppLogger.e("Player:MPV", "Live stream auto-recovery failed", e)
                                                }
                                            } else if (!hasEverPlayed) {
                                                val errDesc = lastStreamErrorReason ?: "Stream instantly closed (Empty / EOF)"
                                                com.lagradost.common.logging.AppLogger.e("Player:MPV", "Stream instantly ended (EOF) before ever playing: $errDesc")
                                                currentOnPlaybackError(errDesc)
                                            } else if (lastDur > 0 && lastPos < lastDur - 15.0) {
                                                // Premature EOF: Stream connection was dropped before actual end of video
                                                val errDesc = lastStreamErrorReason ?: "Stream connection was interrupted"
                                                com.lagradost.common.logging.AppLogger.w("Player:MPV", "Stream closed prematurely at ${lastPos}s of ${lastDur}s: $errDesc. Triggering fallback.")
                                                currentOnPlaybackError(errDesc)
                                            } else {
                                                com.lagradost.common.logging.AppLogger.i("Player:MPV", "Stream reached genuine EOF successfully.")
                                                currentOnFinished()
                                            }
                                        }
                                    }
                                }

                                2 -> { // MPV_EVENT_LOG_MESSAGE
                                    val logData = event.data
                                    if (logData != null) {
                                        val logMsg = MpvLibrary.MpvEventLogMessage(logData)
                                        val pfx = logMsg.prefix ?: "core"
                                        val text = logMsg.text?.trimEnd('\r', '\n') ?: ""
                                        if (text.isNotBlank()) {
                                            val tag = "Player:MPV:$pfx"
                                            val level = logMsg.level?.lowercase()
                                            when (level) {
                                                "error", "fatal" -> com.lagradost.common.logging.AppLogger.e(tag, text)
                                                "warn" -> com.lagradost.common.logging.AppLogger.w(tag, text)
                                                "info", "status" -> com.lagradost.common.logging.AppLogger.i(tag, text)
                                                else -> com.lagradost.common.logging.AppLogger.d(tag, text)
                                            }

                                            // Extract descriptive error reason for UI feedback
                                            if (level in listOf("error", "fatal", "warn")) {
                                                val lower = text.lowercase()
                                                when {
                                                    lower.contains("403") || lower.contains("forbidden") -> lastStreamErrorReason = "HTTP 403 Forbidden"
                                                    lower.contains("404") || lower.contains("not found") -> lastStreamErrorReason = "HTTP 404 Not Found"
                                                    lower.contains("401") || lower.contains("unauthorized") -> lastStreamErrorReason = "HTTP 401 Unauthorized"
                                                    lower.contains("429") || lower.contains("too many requests") -> lastStreamErrorReason = "HTTP 429 Rate Limited"
                                                    lower.contains("502") || lower.contains("bad gateway") -> lastStreamErrorReason = "HTTP 502 Bad Gateway"
                                                    lower.contains("503") || lower.contains("service unavailable") -> lastStreamErrorReason = "HTTP 503 Service Unavailable"
                                                    lower.contains("500") || lower.contains("internal server error") -> lastStreamErrorReason = "HTTP 500 Server Error"
                                                    lower.contains("timed out") || lower.contains("operation timed out") || lower.contains("timeout") -> lastStreamErrorReason = "Connection Timed Out"
                                                    lower.contains("certificate") || lower.contains("tls") || lower.contains("ssl") -> lastStreamErrorReason = "SSL/TLS Handshake Error"
                                                    lower.contains("connection refused") -> lastStreamErrorReason = "Connection Refused"
                                                    lower.contains("could not resolve") || lower.contains("name resolution") -> lastStreamErrorReason = "DNS / Host Resolution Failed"
                                                    lower.contains("invalid data") || lower.contains("unsupported") -> lastStreamErrorReason = "Unsupported Stream Format"
                                                }
                                            }
                                        }
                                    }
                                }

                                // MPV_EVENT_FILE_LOADED (8) or MPV_EVENT_PLAYBACK_RESTART (21)
                                8, 21 -> {
                                    waitingForTimePosReset = false
                                    // Immediate track and chapter extraction upon load
                                    lastTrackPollMs = System.currentTimeMillis()
                                    pollTracksAndChapters(h)

                                    if (!hasEverPlayed) {
                                        hasEverPlayed = true
                                        playbackStartedAt = System.currentTimeMillis()
                                        com.lagradost.common.logging.AppLogger.i("Player:MPV", "Playback active (MPV_EVENT_FILE_LOADED / RESTART)")

                                        if (startPositionMs > 2000L) {
                                            val currentLoadedPos = MpvLibrary.getPropertyDouble(h, "time-pos", 0.0)
                                            val targetSec = startPositionMs / 1000.0
                                            if (currentLoadedPos < 1.0 || kotlin.math.abs(currentLoadedPos - targetSec) > 3.0) {
                                                com.lagradost.common.logging.AppLogger.i("Player:MPV", "Initial start position ($targetSec s) not reached by demuxer (current=$currentLoadedPos s). Executing fallback seek...")
                                                val seekRes = MpvLibrary.INSTANCE.mpv_command_string(h, "seek $targetSec absolute+exact")
                                                if (seekRes != 0) {
                                                    MpvLibrary.INSTANCE.mpv_command_string(h, "seek $targetSec absolute")
                                                }
                                            }
                                            playerState?._positionMs?.value = startPositionMs

                                            // Thread verification check after brief buffer phase
                                            Thread({
                                                try {
                                                    Thread.sleep(600)
                                                    val verifiedPos = MpvLibrary.getPropertyDouble(h, "time-pos", 0.0)
                                                    if (verifiedPos < 1.0 && startPositionMs >= 3000L) {
                                                        com.lagradost.common.logging.AppLogger.w("Player:MPV", "Initial start position retry: still at $verifiedPos s. Re-attempting seek to $targetSec s")
                                                        MpvLibrary.INSTANCE.mpv_command_string(h, "seek $targetSec absolute")
                                                    }
                                                } catch (_: InterruptedException) { }
                                            }, "cs3-seek-verify").apply {
                                                isDaemon = true
                                                start()
                                            }
                                        } else if (startPositionMs > 0) {
                                            com.lagradost.common.logging.AppLogger.i("Player:MPV", "Initial playback started at $startPositionMs ms")
                                            playerState?._positionMs?.value = startPositionMs
                                        }

                                        playerState?._isBuffering?.value = false
                                        playerState?._isProbing?.value = false
                                        currentOnPlaybackReady()
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
                                                        if (newPos >= 0.0) lastPos = newPos

                                                        if (lastPos > 0.1) waitingForTimePosReset = false

                                                        if (!hasEverPlayed && lastPos > 0.1) {
                                                            hasEverPlayed = true
                                                            playbackStartedAt = System.currentTimeMillis()
                                                            playerState?._isBuffering?.value = false
                                                            playerState?._isProbing?.value = false
                                                            currentOnPlaybackReady()
                                                        }

                                                        // One-shot 30s diagnostic
                                                        if (!diagnosticLogged && playbackStartedAt > 0 &&
                                                            System.currentTimeMillis() - playbackStartedAt > 30_000
                                                        ) {
                                                            diagnosticLogged = true
                                                            val dVo = MpvLibrary.getPropertyString(h, "current-vo")
                                                            val dHwdec = MpvLibrary.getPropertyString(h, "hwdec-current")
                                                            val dCodec = MpvLibrary.getPropertyString(h, "video-codec")
                                                            val dFps = MpvLibrary.getPropertyString(h, "estimated-vf-fps")
                                                            val dW = MpvLibrary.getPropertyString(h, "width")
                                                            val dH = MpvLibrary.getPropertyString(h, "height")
                                                            com.lagradost.common.logging.AppLogger.i(
                                                                "MPV 30s Diag -> VO: $dVo, HWDEC: $dHwdec, Codec: $dCodec, FPS: $dFps, Res: ${dW}x$dH",
                                                            )
                                                        }

                                                        // Push to Kotlin state (throttled to 100ms)
                                                        val now = System.currentTimeMillis()
                                                        if (now - lastUiPositionEmit >= 100) {
                                                            lastUiPositionEmit = now
                                                            val posMs = (lastPos * 1000).toLong()
                                                            playerState?.updatePositionFromPlayer(posMs)
                                                            currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                                        }
                                                    }
                                                }
                                                "duration" -> {
                                                    if (prop.format == 5) {
                                                        lastDur = prop.data!!.getDouble(0)
                                                        playerState?._durationMs?.value = (lastDur * 1000).toLong()
                                                    }
                                                }
                                                "pause" -> {
                                                    if (prop.format == 3) playerState?._isPaused?.value = prop.data!!.getInt(0) != 0
                                                }
                                                "paused-for-cache" -> {
                                                    if (prop.format == 3) {
                                                        playerState?._isBuffering?.value = prop.data!!.getInt(0) != 0
                                                    }
                                                }
                                                "mute" -> {
                                                    if (prop.format == 3) playerState?._isMuted?.value = prop.data!!.getInt(0) != 0
                                                }
                                                "volume" -> {
                                                    if (prop.format == 5) playerState?._volume?.value = prop.data!!.getDouble(0).toFloat()
                                                }
                                                "speed" -> {
                                                    if (prop.format == 5) playerState?._playbackSpeed?.value = prop.data!!.getDouble(0).toFloat()
                                                }
                                                "core-idle" -> {
                                                    if (prop.format == 3 && !hasEverPlayed) {
                                                        playerState?._isProbing?.value = prop.data!!.getInt(0) != 0
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            } // end when(eventId)
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
                                playerState?._durationMs?.value = (currentDur * 1000).toLong()
                            }

                            // Poll position
                            val pollPos = MpvLibrary.getPropertyDouble(h, "time-pos", -1.0)
                            if (pollPos >= 0.0) lastPos = pollPos

                            if (pollPos >= 0.0) {
                                if (lastPos > 0.1) waitingForTimePosReset = false
                                // Trigger playback-ready if native events haven't done so yet
                                if (!hasEverPlayed && lastPos > 0.1) {
                                    hasEverPlayed = true
                                    playbackStartedAt = System.currentTimeMillis()
                                    playerState?._isBuffering?.value = false
                                    playerState?._isProbing?.value = false
                                    currentOnPlaybackReady()
                                }
                            }

                            // Always emit position to Kotlin state
                            if (now - lastUiPositionEmit >= 100) {
                                lastUiPositionEmit = now
                                val posMs = (lastPos * 1000).toLong()
                                playerState?.updatePositionFromPlayer(posMs)
                                currentOnPositionChange(posMs, (lastDur * 1000).toLong())
                                // Refresh buffer indicator
                                MpvLibrary.getPropertyString(h, "paused-for-cache")?.let { s ->
                                    playerState?._isBuffering?.value = s == "yes"
                                }
                            }
                        }

                        // Heavy track + stats poll (2000ms)
                        // Separated from position poll so ~20 mpv_get_property_string calls
                        // never stall the 100ms seek-bar update window.
                        if (now - lastTrackPollMs >= 2000L) {
                            lastTrackPollMs = now
                            pollTracksAndChapters(h)

                            // Poll Video Stats
                            if (playerState != null && playerState._showStats.value) {
                                playerState._videoCodec.value = MpvLibrary.getPropertyString(h, "video-codec") ?: "Unknown"
                                playerState._audioCodec.value = MpvLibrary.getPropertyString(h, "audio-codec") ?: "Unknown"
                                playerState._hwdecCurrent.value = MpvLibrary.getPropertyString(h, "hwdec-current") ?: "Unknown"
                                playerState._droppedFrames.value = MpvLibrary.getPropertyString(h, "vo-drop-frame-count")?.toLongOrNull() ?: 0L
                                playerState._fps.value = MpvLibrary.getPropertyString(h, "container-fps")?.toDoubleOrNull() ?: 0.0
                                val w = MpvLibrary.getPropertyString(h, "width") ?: "0"
                                val hw = MpvLibrary.getPropertyString(h, "height") ?: "0"
                                playerState._resolution.value = "${w}x$hw"
                                playerState._videoBitrate.value = MpvLibrary.getPropertyString(h, "video-bitrate")?.toLongOrNull() ?: 0L
                                playerState._audioBitrate.value = MpvLibrary.getPropertyString(h, "audio-bitrate")?.toLongOrNull() ?: 0L
                            }
                        } // End of periodic track poll block
                        loops++
                    } catch (e: kotlinx.coroutines.CancellationException) {
                        break // Normal coroutine cancellation
                    } catch (e: Throwable) {
                        com.lagradost.common.logging.AppLogger.e("MPV event loop non-fatal error: ${e.message}")
                        kotlinx.coroutines.delay(100) // Prevent tight loop crash spam
                        continue
                    }
                }
            }
        }
    }

    LaunchedEffect(title, mpvHandle) {
        val handle = mpvHandle ?: return@LaunchedEffect
        if (!title.isNullOrBlank()) {
            val lib = MpvLibrary.INSTANCE
            val safeTitle = title
            lib.mpv_set_property_string(handle, "force-media-title", safeTitle)
            lib.mpv_set_property_string(handle, "title", safeTitle)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.stopStream()
        }
    }

    LaunchedEffect(link, mpvHandle) {
        // Reset guards IMMEDIATELY so the concurrent event loop never sees stale state
        // from the previous link attempt when event 8 fires for the new link.
        hasEverPlayed = false
        waitingForTimePosReset = true

        val handle = mpvHandle ?: return@LaunchedEffect
        if (link == null) {
            com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.stopStream()
            MpvLibrary.INSTANCE.mpv_command_string(handle, "stop")
            MpvLibrary.INSTANCE.mpv_set_property_string(handle, "pause", "yes")
            playerState?._isPaused?.value = true
            hasEverPlayed = false
            return@LaunchedEffect // Idle state — WebView player while scraping
        }

        val resolvedLink = if (com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isTorrentLink(link)) {
            try {
                com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.transformLink(link)
            } catch (e: Exception) {
                currentOnPlaybackError("Torrent Stream Error: ${e.message}")
                return@LaunchedEffect
            }
        } else {
            com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.stopStream()
            link
        }

        val validated = PlayerLinkHandler.validate(resolvedLink, title).getOrElse {
            currentOnPlaybackError(it.message ?: "Validation failed")
            return@LaunchedEffect
        }

        playerState?.reset()

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
                // 100MB forward is plenty for most 1080p HLS streams (segments are typically 2-6MB each).
                // 400MB was causing CDN rate-limiting: MPV/FFmpeg would burst-request many segments
                // at once to fill the cache, hitting 429 errors from Cloudflare/Akamai/Fastly after 20-30s.
                val forwardBuf = if (isLive) "150000000" else "100000000"
                val backBuf = if (isLive) "80000000" else "30000000"
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", forwardBuf)
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", backBuf)
                lib.mpv_set_property_string(handle, "cache", "yes")
                // 30s lookahead is aggressive but won't overwhelm CDNs the way 60s did.
                lib.mpv_set_property_string(handle, "cache-secs", if (isLive) "15" else "30")
                lib.mpv_set_property_string(handle, "demuxer-readahead-secs", if (isLive) "15" else "30")
                // Start playback instantly like hls.js instead of waiting for the cache to fill
                lib.mpv_set_property_string(handle, "cache-pause-initial", "no")
                lib.mpv_set_property_string(handle, "cache-pause-wait", if (isLive) "0.5" else "1")

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

                // Fast probesize and instant start for proxied HLS streams
                lib.mpv_set_property_string(handle, "demuxer-lavf-probesize", "1048576") // 1 MB
                lib.mpv_set_property_string(handle, "demuxer-lavf-analyzeduration", "0") // 0 s
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
                // Smart balanced buffer: 100MB forward (~60-90s of 1080p) + 20MB back for instant rewinds, saving ~380MB RAM
                lib.mpv_set_property_string(handle, "demuxer-max-bytes", "100000000") // 100MB forward
                lib.mpv_set_property_string(handle, "demuxer-max-back-bytes", "20000000") // 20MB back
                lib.mpv_set_property_string(handle, "cache", "yes")
                lib.mpv_set_property_string(handle, "demuxer-seekable-cache", "yes")
            }
        }

        // Set dynamic audio and subtitle selection according to user preference:
        val prefAudioLang = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_AUDIO_LANG) ?: "auto"
        if (prefAudioLang != "auto" && prefAudioLang.isNotBlank()) {
            lib.mpv_set_property_string(handle, "alang", prefAudioLang)
        } else {
            lib.mpv_set_property_string(handle, "alang", "")
        }
        lib.mpv_set_property_string(handle, "aid", "auto")

        val prefSubLang = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG) ?: "auto"
        if (prefSubLang != "auto" && prefSubLang != "off" && prefSubLang.isNotBlank()) {
            lib.mpv_set_property_string(handle, "slang", prefSubLang)
            lib.mpv_set_property_string(handle, "sid", "auto")
            lib.mpv_set_property_string(handle, "sub-auto", "all")
            lib.mpv_set_property_string(handle, "sub-visibility", "yes")
        } else if (prefSubLang == "off") {
            lib.mpv_set_property_string(handle, "sid", "no")
            lib.mpv_set_property_string(handle, "sub-auto", "no")
            lib.mpv_set_property_string(handle, "sub-visibility", "no")
        } else {
            lib.mpv_set_property_string(handle, "sid", "auto")
            lib.mpv_set_property_string(handle, "sub-auto", "fuzzy")
            lib.mpv_set_property_string(handle, "sub-visibility", "yes")
        }
        val subBg = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_SUB_BG) ?: "#00000000"
        val (mpvBgColor, borderStyle) = PlayerConfig.toMpvBackgroundColor(subBg)
        lib.mpv_set_property_string(handle, "sub-back-color", mpvBgColor)
        lib.mpv_set_property_string(handle, "sub-border-style", borderStyle)
        lib.mpv_set_property_string(handle, "cursor-autohide", "1500")

        val startSec = startPositionMs / 1000.0
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

        // NOTE: hasEverPlayed and waitingForTimePosReset are already reset at the top
        // of this LaunchedEffect.

        val cmdResult = try {
            lib.mpv_command(handle, arrayOf("loadfile", safeUrl, "replace", null))
        } catch (_: Throwable) {
            -1
        }

        if (cmdResult != 0) {
            lib.mpv_command_string(handle, "loadfile \"$safeUrl\"")
        }

        // Ensure the player is unpaused when loading a new link.
        // However, if resuming from a saved position, pause it so the UI can show a "Resume" dialog.
        val shouldPause = shouldPauseForResume && startSec > 0
        lib.mpv_set_property_string(handle, "pause", if (shouldPause) "yes" else "no")
        playerState?._isPaused?.value = shouldPause

        // Subtitles handling
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

        // Let MPV handle network timeouts natively (8s aggressive timeout)
        lib.mpv_set_property_string(capturedHandle, "network-timeout", "8")

        launch(kotlinx.coroutines.Dispatchers.IO) {
            val defaultSub = finalSubtitles.firstOrNull()
            finalSubtitles.forEach { sub ->
                if (mpvHandle != null) {
                    val escapedSub = sub.url.replace("\\", "\\\\").replace("\"", "\\\"")
                    val escapedTitle = sub.lang.replace("\\", "\\\\").replace("\"", "\\\"")
                    try {
                        val prefSubLang = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG) ?: "auto"
                        val flag = if (prefSubLang == "off") "no" else "auto"
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

            override fun paint(g: java.awt.Graphics?) {
                g?.color = java.awt.Color.BLACK
                g?.fillRect(0, 0, width.coerceAtLeast(1), height.coerceAtLeast(1))
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

                // VO: use the battle-tested 'gpu' output, NOT 'gpu-next'.
                // gpu-next requires libplacebo which may not be in the bundled libmpv,
                // causing silent fallback to a broken/software pipeline.
                lib.mpv_set_option_string(handle, "vo", "gpu")

                // Apply User Settings & Logging
                PlayerConfig.applyMpvSettings(handle, lib)

                val wid = com.sun.jna.Native.getComponentID(this)
                lib.mpv_set_option_string(handle, "wid", wid.toString())

                lib.mpv_set_option_string(handle, "input-default-bindings", "no")
                lib.mpv_set_option_string(handle, "input-vo-keyboard", "no")
                lib.mpv_set_option_string(handle, "save-position-on-quit", "no")
                lib.mpv_set_option_string(handle, "resume-playback", "no")
                lib.mpv_set_option_string(handle, "keep-open", "yes")
                lib.mpv_set_option_string(handle, "ytdl", "no")
                lib.mpv_set_option_string(handle, "idle", "yes")

                // Allow caller to override WID, VO, etc before init
                onPreInitialize?.invoke(handle, wid, this.width, this.height)

                com.lagradost.common.logging.AppLogger.i("Player:MPV", "Initializing embedded MPV Engine")
                lib.mpv_initialize(handle)
                lib.mpv_request_log_messages(handle, "info")

                onPostInitialize?.invoke(handle)

                // Post-init diagnostics: log what MPV actually chose for VO/hwdec
                val actualVo = MpvLibrary.getPropertyString(handle, "current-vo")
                val actualHwdec = MpvLibrary.getPropertyString(handle, "hwdec-current")
                com.lagradost.common.logging.AppLogger.i("Player:MPV", "MPV Engine initialized: vo=$actualVo, hwdec=$actualHwdec")

                // Setup Mouse and Keyboard interactions
                val canvas = this
                canvas.addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent) {
                        mpvHandle?.let { h -> MpvLibrary.INSTANCE.mpv_command_string(h, "mouse ${e.x} ${e.y}") }
                    }
                    override fun mouseDragged(e: MouseEvent) {
                        mpvHandle?.let { h -> MpvLibrary.INSTANCE.mpv_command_string(h, "mouse ${e.x} ${e.y}") }
                    }
                })

                canvas.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        mpvHandle?.let { h ->
                            if (e.button == 4) {
                                // Mouse 4 (Back button) -> Seek -10s
                                MpvLibrary.INSTANCE.mpv_command_string(h, "seek -10")
                                com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerSeekFeedback) window.triggerSeekFeedback('left');")
                                return
                            } else if (e.button == 5) {
                                // Mouse 5 (Forward button) -> Seek +10s
                                MpvLibrary.INSTANCE.mpv_command_string(h, "seek 10")
                                com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerSeekFeedback) window.triggerSeekFeedback('right');")
                                return
                            }
                            canvas.requestFocusInWindow()
                            val btn = when (e.button) {
                                MouseEvent.BUTTON1 -> "MBTN_LEFT"
                                MouseEvent.BUTTON2 -> "MBTN_MID"
                                MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                                else -> return
                            }
                            MpvLibrary.INSTANCE.mpv_command_string(h, "keydown $btn")
                        }
                    }
                    override fun mouseReleased(e: MouseEvent) {
                        mpvHandle?.let { h ->
                            if (e.button == 4 || e.button == 5) return
                            val btn = when (e.button) {
                                MouseEvent.BUTTON1 -> "MBTN_LEFT"
                                MouseEvent.BUTTON2 -> "MBTN_MID"
                                MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                                else -> return
                            }
                            MpvLibrary.INSTANCE.mpv_command_string(h, "keyup $btn")
                        }
                    }
                })

                canvas.addFocusListener(object : java.awt.event.FocusAdapter() {
                    override fun focusGained(e: java.awt.event.FocusEvent?) {
                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.focusWebView()
                    }
                })

                canvas.addMouseWheelListener { e ->
                    mpvHandle?.let { h ->
                        val key = if (e.wheelRotation < 0) "WHEEL_UP" else "WHEEL_DOWN"
                        MpvLibrary.INSTANCE.mpv_command_string(h, "keypress $key")
                    }
                }

                this.keyDispatcher = java.awt.KeyEventDispatcher { e ->
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        val mpvKey = awtKeyToMpv(e)
                        val lower = mpvKey?.lowercase() ?: ""
                        val isSeek = lower == "left" || lower == "right" || lower == "shift+left" || lower == "shift+right" || lower == "ctrl+right" || (lower.length == 1 && lower[0].isDigit())
                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.onNativeKeyActivity) window.onNativeKeyActivity($isSeek);")
                        mpvHandle?.let { h ->
                            if (mpvKey?.contains("QUIT_OVERRIDE") == true || e.keyCode == KeyEvent.VK_ESCAPE) {
                                currentOnCloseRequest()
                            } else if (mpvKey != null) {
                                when {
                                    lower == "space" || lower == "k" -> MpvLibrary.INSTANCE.mpv_command_string(h, "cycle pause")
                                    lower == "left" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "seek -10")
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                    }
                                    lower == "right" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "seek 10")
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                    }
                                    lower == "shift+left" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "seek -2")
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                    }
                                    lower == "shift+right" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "seek 2")
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                    }
                                    lower == "ctrl+right" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "seek 85")
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                    }
                                    lower == "up" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "add volume 5")
                                        val vol = ((playerState?._volume?.value ?: 100f) + 5f).coerceIn(0f, 100f)
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.showVolumeOsd) window.showVolumeOsd($vol);")
                                    }
                                    lower == "down" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "add volume -5")
                                        val vol = ((playerState?._volume?.value ?: 100f) - 5f).coerceIn(0f, 100f)
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.showVolumeOsd) window.showVolumeOsd($vol);")
                                    }
                                    lower == "m" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "cycle mute")
                                        val isM = !(playerState?._isMuted?.value ?: false)
                                        val vol = playerState?._volume?.value ?: 100f
                                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.showVolumeOsd) window.showVolumeOsd($vol, $isM);")
                                    }
                                    lower == "+" || lower == "=" || lower == "]" -> MpvLibrary.INSTANCE.mpv_command_string(h, "add speed 0.25")
                                    lower == "-" || lower == "_" || lower == "[" -> MpvLibrary.INSTANCE.mpv_command_string(h, "add speed -0.25")
                                    lower == "bs" || lower == "backspace" -> MpvLibrary.INSTANCE.mpv_command_string(h, "set speed 1.0")
                                    lower == "z" -> MpvLibrary.INSTANCE.mpv_command_string(h, "add sub-delay -0.1")
                                    lower == "x" -> MpvLibrary.INSTANCE.mpv_command_string(h, "add sub-delay 0.1")
                                    lower == "c" -> MpvLibrary.INSTANCE.mpv_command_string(h, "cycle sub")
                                    lower == "shift+s" || lower == "ctrl+s" -> {
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "screenshot video")
                                        val dirName = com.lagradost.common.platform.PlatformPaths.screenshotsDir.name
                                        playerState?.showToast("Screenshot saved to $dirName")
                                    }
                                    lower == "v" -> MpvLibrary.INSTANCE.mpv_command_string(h, "cycle sub-visibility")
                                    lower == "f" || lower == "f11" -> currentOnFullscreenToggle?.invoke()
                                    lower == "?" || lower == "f1" || lower == "h" -> currentOnShowShortcuts()
                                    lower.length == 1 && lower[0].isDigit() -> {
                                        val pct = (lower[0] - '0') * 10
                                        MpvLibrary.INSTANCE.mpv_command_string(h, "seek $pct absolute-percent")
                                    }
                                }
                            }
                        }
                    }
                    false
                }
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this.keyDispatcher)
            }

            override fun removeNotify() {
                // Find and remove the dispatcher to prevent memory leaks
                val focusManager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                this.keyDispatcher?.let {
                    focusManager.removeKeyEventDispatcher(it)
                }

                val h = mpvHandle
                mpvHandle = null
                playerState?.detachMpv()

                // Teardown the MPV handle off the EDT to avoid deadlocking with Compose
                if (h != null) {
                    com.lagradost.cloudstream3.desktop.utils.appScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                        } catch (e: Throwable) {
                            com.lagradost.common.logging.AppLogger.e("BaseMpvPlayer", "Failed to destroy mpv handle: ${e.message}")
                        }
                    }
                }
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
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            videoCanvas.isVisible = false
            val h = mpvHandle
            if (h != null) {
                mpvHandle = null
                playerState?.detachMpv()

                // Push the heavy C++ teardown to a background daemon thread
                // to prevent blocking the Compose EDT.
                java.lang.Thread({
                    com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Destroying mpv engine on daemon thread...")
                    MpvLibrary.INSTANCE.mpv_terminate_destroy(h)
                }, "cs3-player-dispose").apply {
                    isDaemon = true
                    start()
                }
            }
        }
    }

    PlayerShortcutsModal(
        show = showShortcutsModal,
        onDismissRequest = { showShortcutsModal = false },
    )

    videoRenderer(videoCanvas, mpvHandle)
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

        KeyEvent.VK_F11 -> "F11"
        KeyEvent.VK_F12 -> "F12"

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
