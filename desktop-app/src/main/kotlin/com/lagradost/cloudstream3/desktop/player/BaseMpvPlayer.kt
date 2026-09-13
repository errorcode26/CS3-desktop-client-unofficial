package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.lagradost.cloudstream3.desktop.player.ytdl.DesktopYtDlpBinary
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
    val scope = rememberCoroutineScope()
    var showShortcutsModal by remember { mutableStateOf(false) }
    var isEngineReady by remember { mutableStateOf(false) }

    val currentOnPlaybackReady by rememberUpdatedState(onPlaybackReady)
    val currentOnPlaybackError by rememberUpdatedState(onPlaybackError)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val currentOnPositionChange by rememberUpdatedState(onPositionChange)
    val currentOnCloseRequest by rememberUpdatedState(onCloseRequest)
    val currentOnFullscreenToggle by rememberUpdatedState(onFullscreenToggle)
    val currentOnShowShortcuts: () -> Unit by rememberUpdatedState({ showShortcutsModal = true })

    val engine = remember {
        DesktopMpvEngine(
            scope = scope,
            playerState = playerState,
            onPlaybackReady = { currentOnPlaybackReady() },
            onPlaybackError = { currentOnPlaybackError(it) },
            onFinished = { currentOnFinished() },
            onPositionChange = { pos, dur -> currentOnPositionChange(pos, dur) },
            onEventLoopReady = onEventLoopReady,
            isLive = isLive,
        )
    }

    LaunchedEffect(playerState, isLive) {
        engine.playerState = playerState
        engine.isLive = isLive
    }

    LaunchedEffect(title, isEngineReady) {
        if (isEngineReady && !title.isNullOrBlank()) {
            engine.setPropertyString("force-media-title", title)
            engine.setPropertyString("title", title)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.stopStream()
        }
    }

    LaunchedEffect(link, isEngineReady) {
        if (!isEngineReady) return@LaunchedEffect

        if (link == null) {
            com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.stopStream()
            engine.executeCommand("stop")
            engine.setPropertyString("pause", "yes")
            playerState?._isPaused?.value = true
            playerState?.reset()
            return@LaunchedEffect
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

        val isYouTube = DesktopYtDlpBinary.isYouTubeUrl(resolvedLink.url) ||
            resolvedLink.extractorData == "yt-dlp"

        if (isYouTube) {
            val ytdlBinary = DesktopYtDlpBinary()
            if (!ytdlBinary.isInstalled()) {
                com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer", "YouTube URL detected, downloading yt-dlp binary...")
                try {
                    val job = ytdlBinary.downloadWithManager()
                    job.join()
                    if (!ytdlBinary.isInstalled()) {
                        currentOnPlaybackError("Failed to download yt-dlp Stream Resolver")
                        return@LaunchedEffect
                    }
                    engine.configureYtDlpIfInstalled()
                } catch (e: Exception) {
                    currentOnPlaybackError("Stream Resolver Error: ${e.message}")
                    return@LaunchedEffect
                }
            } else {
                engine.configureYtDlpIfInstalled()
            }
            val isLiveStream = isLive || resolvedLink.name.contains("Live", ignoreCase = true) || resolvedLink.url.contains("live", ignoreCase = true)
            val ytdlFormat = when {
                isLiveStream -> "best[protocol^=m3u8]/best"
                resolvedLink.quality > 0 -> "bestvideo[height<=${resolvedLink.quality}][vcodec^=avc1]+bestaudio/bestvideo[height<=${resolvedLink.quality}][vcodec!*=?av01]+bestaudio/best[height<=${resolvedLink.quality}]/best"
                else -> "bestvideo[vcodec^=avc1]+bestaudio/bestvideo[vcodec!*=?av01]+bestaudio/best"
            }
            engine.setPropertyString("ytdl-format", ytdlFormat)
            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer", "Configured yt-dlp format: $ytdlFormat for quality ${resolvedLink.quality} (isLive=$isLiveStream)")
        } else {
            engine.setPropertyString("ytdl-format", "bestvideo+bestaudio/best")
            engine.setPropertyString("ytdl-raw-options", "")
        }

        val validated = PlayerLinkHandler.validate(resolvedLink, title).getOrElse {
            currentOnPlaybackError(it.message ?: "Validation failed")
            return@LaunchedEffect
        }

        playerState?.reset()

        // Clear previous lavf options to prevent bleeding across stream loads
        engine.setPropertyString("demuxer-lavf-o", "")
        engine.setPropertyString("stream-lavf-o", "")

        val isLiveStream = isLive || resolvedLink.name.contains("Live", ignoreCase = true) || resolvedLink.url.contains("live", ignoreCase = true)

        if (isYouTube) {
            // YouTube / yt-dlp streams:
            // 1. Leave demuxer-lavf-o and stream-lavf-o empty so FFmpeg does not send raw reconnect Range headers to Google CDN.
            // 2. force-seekable MUST be "no" to prevent Matroska demuxer from seeking to end of file for Cues.
            // 3. Do not overwrite user-agent or http-header-fields (preserves yt-dlp signed client tokens).
            // 4. Configure generous MPV RAM caching for instant intra-cache seeking.
            engine.setPropertyString("cache", "yes")
            engine.setPropertyString("demuxer-max-bytes", "250000000")
            engine.setPropertyString("demuxer-max-back-bytes", "100000000")
            engine.setPropertyString("cache-secs", if (isLiveStream) "15" else "60")
            engine.setPropertyString("demuxer-readahead-secs", if (isLiveStream) "15" else "60")
            engine.setPropertyString("cache-pause-initial", "no")
            engine.setPropertyString("cache-pause-wait", if (isLiveStream) "0.5" else "2")
            engine.setPropertyString("demuxer-seekable-cache", "yes")
            engine.setPropertyString("hr-seek", "yes")
            engine.setPropertyString("hr-seek-framedrop", "yes")
            engine.setPropertyString("force-seekable", "no")
            // Reconnect must be set at the stream (transport) layer, not the demuxer layer.
            // Handles CDN edge-cache 4xx/5xx drops and mid-segment connection resets.
            engine.setPropertyString("demuxer-lavf-o", "")
            engine.setPropertyString("stream-lavf-o", "reconnect_on_http_error=4xx,5xx,reconnect_delay_max=30,reconnect_streamed=1,reconnect_on_network_error=1")
            engine.setPropertyString("referrer", "")
            engine.setPropertyString("ytdl-raw-options", "")
        } else {
            when (validated.streamKind) {
                PlayerLinkHandler.StreamKind.HLS -> {
                    engine.setPropertyString("hls-bitrate", "max")
                    val forwardBuf = if (isLiveStream) "150000000" else "100000000"
                    val backBuf = if (isLiveStream) "80000000" else "30000000"
                    engine.setPropertyString("demuxer-max-bytes", forwardBuf)
                    engine.setPropertyString("demuxer-max-back-bytes", backBuf)
                    engine.setPropertyString("cache", "yes")
                    engine.setPropertyString("cache-secs", if (isLiveStream) "15" else "30")
                    engine.setPropertyString("demuxer-readahead-secs", if (isLiveStream) "15" else "30")
                    engine.setPropertyString("cache-pause-initial", "no")
                    engine.setPropertyString("cache-pause-wait", if (isLiveStream) "0.5" else "3")
                    engine.setPropertyString(
                        "demuxer-lavf-o",
                        "extension_picky=0,http_persistent=0,fflags=+discardcorrupt,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4",
                    )
                    engine.setPropertyString("demuxer-seekable-cache", "yes")
                    engine.setPropertyString("force-seekable", if (isLiveStream) "no" else "yes")
                    engine.setPropertyString("demuxer-lavf-probesize", "1048576")
                }
                PlayerLinkHandler.StreamKind.DASH -> {
                    engine.setPropertyString("demuxer-max-bytes", "100000000")
                    engine.setPropertyString("demuxer-max-back-bytes", "30000000")
                    engine.setPropertyString("cache", "yes")
                    engine.setPropertyString("cache-secs", "30")
                    engine.setPropertyString("demuxer-readahead-secs", "30")
                    engine.setPropertyString("cache-pause-initial", "no")
                    engine.setPropertyString("cache-pause-wait", "3")
                    engine.setPropertyString("demuxer-lavf-o", "http_persistent=0,fflags=+discardcorrupt,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4")
                    engine.setPropertyString("demuxer-seekable-cache", "yes")
                    engine.setPropertyString("force-seekable", "yes")
                    engine.setPropertyString("demuxer-lavf-probesize", "1048576")
                }
                PlayerLinkHandler.StreamKind.PROGRESSIVE -> {
                    engine.setPropertyString("demuxer-max-bytes", "100000000")
                    engine.setPropertyString("demuxer-max-back-bytes", "30000000")
                    engine.setPropertyString("cache", "yes")
                    engine.setPropertyString("cache-secs", "30")
                    engine.setPropertyString("demuxer-readahead-secs", "30")
                    engine.setPropertyString("cache-pause-initial", "no")
                    engine.setPropertyString("cache-pause-wait", "3")
                    engine.setPropertyString("demuxer-lavf-o", "http_persistent=0,reconnect=1,reconnect_streamed=1,reconnect_delay_max=4")
                    engine.setPropertyString("demuxer-seekable-cache", "yes")
                    engine.setPropertyString("force-seekable", "yes")
                }
            }

            val referer = validated.headers.entries.firstOrNull {
                it.key.equals("referer", ignoreCase = true) || it.key.equals("referrer", ignoreCase = true)
            }?.value
            val userAgent = validated.headers.entries.firstOrNull {
                it.key.equals("user-agent", ignoreCase = true)
            }?.value

            engine.setPropertyString("referrer", referer ?: "")
            engine.setPropertyString("user-agent", userAgent ?: com.lagradost.cloudstream3.USER_AGENT)

            val headersStr = validated.headers.entries.joinToString(",") { "${it.key}: ${it.value.replace(",", "\\,")}" }
            engine.setPropertyString("http-header-fields", headersStr)
        }

        val startSec = startPositionMs / 1000.0
        if (startSec > 0 && !isLiveStream) {
            engine.setPropertyString("start", startSec.toString())
        }

        // Reset video track selection so new video files don't inherit disabled video
        engine.setPropertyString("vid", "auto")

        if (validated.displayTitle.isNotBlank()) {
            engine.setPropertyString("force-media-title", validated.displayTitle)
            engine.setPropertyString("title", validated.displayTitle)
        }

        val cleanUrl = if (isYouTube) validated.url.substringBefore("#q=") else validated.url
        val urlTarget = if (validated.useUrlFile) {
            PlayerLinkHandler.writeUrlListFile("cloudstream_mpv_url_", validated.displayTitle, cleanUrl).absolutePath
        } else {
            cleanUrl
        }

        val safeUrl = urlTarget.replace("\\", "/")
        com.lagradost.common.logging.AppLogger.i("Loading embedded MPV URL: $safeUrl")

        engine.executeCommand("stop")

        var waitAttempts = 0
        while (waitAttempts < 10) {
            try {
                val pos = engine.getPropertyDouble("time-pos", 0.0)
                if (pos <= 0.0) break
            } catch (_: Exception) {
                break
            }
            kotlinx.coroutines.delay(50)
            waitAttempts++
        }

        val cmdResult = try {
            engine.executeCommandArray(arrayOf("loadfile", safeUrl, "replace", null))
        } catch (_: Throwable) {
            -1
        }

        if (cmdResult != 0) {
            engine.executeCommand("loadfile \"$safeUrl\"")
        }

        val shouldPause = shouldPauseForResume && startSec > 0
        engine.setPropertyString("pause", if (shouldPause) "yes" else "no")
        playerState?._isPaused?.value = shouldPause

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

        engine.setPropertyString("network-timeout", "20")

        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            finalSubtitles.forEach { sub ->
                if (engine.isHandleValid()) {
                    val escapedSub = sub.url.replace("\\", "\\\\").replace("\"", "\\\"")
                    val escapedTitle = sub.lang.replace("\\", "\\\\").replace("\"", "\\\"")
                    try {
                        val subEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(PlayerConfig.PREF_SUB_ENABLED)
                            ?: (com.lagradost.common.storage.DesktopDataStore.getKey<String>(PlayerConfig.PREF_PREFERRED_SUB_LANG) != "off")
                        val flag = if (!subEnabled) "no" else "auto"
                        engine.executeCommand("sub-add \"$escapedSub\" $flag \"$escapedTitle\"")
                    } catch (_: Throwable) {}
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

                if (engine.isHandleValid()) return

                val isWindows = System.getProperty("os.name").lowercase().contains("win")
                val mpvExe = resolveMpvExecutable(isWindows)
                if (mpvExe == null) {
                    currentOnPlaybackError("MPV executable not found.")
                    return
                }
                val mpvDir = mpvExe.parentFile
                System.setProperty("jna.library.path", mpvDir.absolutePath)

                val targets = listOf("libmpv-2", "mpv-2", "mpv-1", "mpv", "libmpv", "mpv-3.dll")
                targets.forEach { target ->
                    com.sun.jna.NativeLibrary.addSearchPath(target, mpvDir.absolutePath)
                }

                val wid = com.sun.jna.Native.getComponentID(this)
                val handle = engine.createAndInitialize(
                    canvasWid = wid,
                    width = this.width,
                    height = this.height,
                    onPreInit = { h, w, widthVal, heightVal ->
                        onPreInitialize?.invoke(h, w, widthVal, heightVal)
                    },
                    onPostInit = { h ->
                        onPostInitialize?.invoke(h)
                    },
                )
                if (handle == null) return

                playerState?.attachEngine(engine)
                isEngineReady = true

                val canvas = this
                canvas.addMouseMotionListener(object : MouseMotionAdapter() {
                    override fun mouseMoved(e: MouseEvent) {
                        engine.executeCommand("mouse ${e.x} ${e.y}")
                    }
                    override fun mouseDragged(e: MouseEvent) {
                        engine.executeCommand("mouse ${e.x} ${e.y}")
                    }
                })

                canvas.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        if (e.button == 4) {
                            engine.executeCommand("seek -10")
                            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerSeekFeedback) window.triggerSeekFeedback('left');")
                            return
                        } else if (e.button == 5) {
                            engine.executeCommand("seek 10")
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
                        engine.executeCommand("keydown $btn")
                    }
                    override fun mouseReleased(e: MouseEvent) {
                        if (e.button == 4 || e.button == 5) return
                        val btn = when (e.button) {
                            MouseEvent.BUTTON1 -> "MBTN_LEFT"
                            MouseEvent.BUTTON2 -> "MBTN_MID"
                            MouseEvent.BUTTON3 -> "MBTN_RIGHT"
                            else -> return
                        }
                        engine.executeCommand("keyup $btn")
                    }
                })

                canvas.addFocusListener(object : java.awt.event.FocusAdapter() {
                    override fun focusGained(e: java.awt.event.FocusEvent?) {
                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.focusWebView()
                    }
                })

                canvas.addMouseWheelListener { e ->
                    val key = if (e.wheelRotation < 0) "WHEEL_UP" else "WHEEL_DOWN"
                    engine.executeCommand("keypress $key")
                }

                this.keyDispatcher = java.awt.KeyEventDispatcher { e ->
                    if (e.id == KeyEvent.KEY_PRESSED) {
                        val mpvKey = awtKeyToMpv(e)
                        val lower = mpvKey?.lowercase() ?: ""
                        val isSeek = lower == "left" || lower == "right" || lower == "shift+left" || lower == "shift+right" || lower == "ctrl+right" || (lower.length == 1 && lower[0].isDigit())
                        com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.onNativeKeyActivity) window.onNativeKeyActivity($isSeek);")
                        if (mpvKey?.contains("QUIT_OVERRIDE") == true || e.keyCode == KeyEvent.VK_ESCAPE) {
                            currentOnCloseRequest()
                        } else if (mpvKey != null) {
                            when {
                                lower == "space" || lower == "k" -> engine.executeCommand("cycle pause")
                                lower == "left" -> {
                                    engine.executeCommand("seek -10")
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                }
                                lower == "right" -> {
                                    engine.executeCommand("seek 10")
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                }
                                lower == "shift+left" -> {
                                    engine.executeCommand("seek -2")
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                }
                                lower == "shift+right" -> {
                                    engine.executeCommand("seek 2")
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                }
                                lower == "ctrl+right" -> {
                                    engine.executeCommand("seek 85")
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.triggerKeyboardSeekingHud) window.triggerKeyboardSeekingHud();")
                                }
                                lower == "up" -> {
                                    engine.executeCommand("add volume 5")
                                    val vol = ((playerState?._volume?.value ?: 100f) + 5f).coerceIn(0f, 100f)
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.showVolumeOsd) window.showVolumeOsd($vol);")
                                }
                                lower == "down" -> {
                                    engine.executeCommand("add volume -5")
                                    val vol = ((playerState?._volume?.value ?: 100f) - 5f).coerceIn(0f, 100f)
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.showVolumeOsd) window.showVolumeOsd($vol);")
                                }
                                lower == "m" -> {
                                    playerState?.toggleMute()
                                    val isM = playerState?._isMuted?.value ?: false
                                    val vol = playerState?._volume?.value ?: 100f
                                    com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.showVolumeOsd) window.showVolumeOsd($vol, $isM);")
                                }
                                lower == "+" || lower == "=" || lower == "]" -> engine.executeCommand("add speed 0.25")
                                lower == "-" || lower == "_" || lower == "[" -> engine.executeCommand("add speed -0.25")
                                lower == "bs" || lower == "backspace" -> engine.executeCommand("set speed 1.0")
                                lower == "z" -> engine.executeCommand("add sub-delay -0.1")
                                lower == "x" -> engine.executeCommand("add sub-delay 0.1")
                                lower == "c" -> engine.executeCommand("cycle sub")
                                lower == "shift+s" || lower == "ctrl+s" -> {
                                    engine.executeCommand("screenshot video")
                                    val dirName = com.lagradost.common.platform.PlatformPaths.screenshotsDir.name
                                    playerState?.showToast("Screenshot saved to $dirName")
                                }
                                lower == "v" -> engine.executeCommand("cycle sub-visibility")
                                lower == "f" -> currentOnFullscreenToggle?.invoke()
                                lower == "?" || lower == "f1" || lower == "h" -> currentOnShowShortcuts()
                                lower.length == 1 && lower[0].isDigit() -> {
                                    val pct = (lower[0] - '0') * 10
                                    engine.executeCommand("seek $pct absolute-percent")
                                }
                            }
                        }
                    }
                    false
                }
                java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this.keyDispatcher)
            }

            override fun removeNotify() {
                val focusManager = java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
                this.keyDispatcher?.let {
                    focusManager.removeKeyEventDispatcher(it)
                }
                isEngineReady = false
                playerState?.detachEngine()
                engine.destroy()
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
            isEngineReady = false
            playerState?.detachEngine()
            engine.destroy()
        }
    }

    PlayerShortcutsModal(
        show = showShortcutsModal,
        onDismissRequest = { showShortcutsModal = false },
    )

    videoRenderer(videoCanvas, null)
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
