package com.lagradost.cloudstream3.desktop.player

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import com.lagradost.cloudstream3.desktop.player.ipc.PlayerInboundEvent
import com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge
import com.lagradost.cloudstream3.utils.ExtractorLink
import kotlinx.coroutines.launch
import java.awt.event.*
import java.io.File

private val playerObjectMapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()

private val EMPTY_LIST_FLOW = kotlinx.coroutines.flow.flowOf(emptyList<Nothing>())
private val FALSE_FLOW = kotlinx.coroutines.flow.flowOf(false)
private val MINUS_ONE_FLOW = kotlinx.coroutines.flow.flowOf(-1)
private val NONE_FLOW = kotlinx.coroutines.flow.flowOf("None")
private val NULL_STRING_FLOW = kotlinx.coroutines.flow.flowOf<String?>(null)
private val NULL_SKIP_INTERVAL_FLOW = kotlinx.coroutines.flow.flowOf<com.lagradost.cloudstream3.desktop.player.skip.SkipInterval?>(null)

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
    currentEpisodeNumber: Int? = null,
    currentSeasonNumber: Int? = null,
    isLoading: Boolean = false,
    loadingStatusText: String? = null,
    isProbing: Boolean = false,
    isScraping: Boolean = false,
    failedLinks: Map<Int, String> = emptyMap(),
    backdropUrl: String? = null,
    logoUrl: String? = null,
    onLinkChange: ((String) -> Unit)? = null,
    onEpisodeChange: ((String) -> Unit)? = null,
    onNextEpisode: (() -> Unit)? = null,
    onReplayEpisode: (() -> Unit)? = null,
    plot: String? = null,
    year: Int? = null,
    tags: List<String>? = null,
    contentRating: String? = null,
    rating: Double? = null,
    actors: List<com.lagradost.cloudstream3.ActorData> = emptyList(),
    isLive: Boolean = false,
    isExhausted: Boolean = false,
    exhaustionReason: String? = null,
    exhaustionDiagnostics: String? = null,
    onRetryPlayback: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val persistentSubtitles = remember { androidx.compose.runtime.mutableStateListOf<String>() }
    val window = com.lagradost.cloudstream3.desktop.ui.LocalComposeWindow.current

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
    val audioTracks by (playerState?.audioTracks ?: EMPTY_LIST_FLOW).collectAsState(emptyList())
    val subtitleTracks by (playerState?.subtitleTracks ?: EMPTY_LIST_FLOW).collectAsState(emptyList())
    val videoTracks by (playerState?.videoTracks ?: EMPTY_LIST_FLOW).collectAsState(emptyList())
    val isAudioOnlyStream by (playerState?.isAudioOnlyStream ?: FALSE_FLOW).collectAsState(false)
    val isAudioMode by (playerState?.isAudioMode ?: FALSE_FLOW).collectAsState(false)
    val chapters by (playerState?.chapters ?: EMPTY_LIST_FLOW).collectAsState(emptyList())
    val currentChapterIndex by (playerState?.currentChapterIndex ?: MINUS_ONE_FLOW).collectAsState(-1)
    val isBuffering by (playerState?.isBuffering ?: FALSE_FLOW).collectAsState(false)

    val proxyAudioTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.collectAsState()
    val proxySubtitleTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazySubtitleTracks.collectAsState()
    val proxyVideoTracks by com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.collectAsState()

    val currentIsLoading by rememberUpdatedState(isLoading)
    val currentLoadingStatusText by rememberUpdatedState(loadingStatusText)
    val activeShader by (playerState?.activeShader ?: NONE_FLOW).collectAsState("None")
    val activeLazyVideoTrackUrl by (playerState?.activeLazyVideoTrackUrl ?: NULL_STRING_FLOW).collectAsState(null)
    val activeLazyAudioTrackUrl by (playerState?.activeLazyAudioTrackUrl ?: NULL_STRING_FLOW).collectAsState(null)
    val activeSkipInterval by (playerState?.activeSkipInterval ?: NULL_SKIP_INTERVAL_FLOW).collectAsState(null)
    val skipIntervals by (playerState?.skipIntervals ?: EMPTY_LIST_FLOW).collectAsState(emptyList())
    val resolution by (playerState?.resolution ?: NULL_STRING_FLOW).collectAsState(null)
    val activeSubtitleOverrideEnabled by produceState(false) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE) ?: false
        }
    }

    var hasAutoSelectedQuality by remember(link) { mutableStateOf(false) }

    LaunchedEffect(proxyVideoTracks, link) {
        if (!hasAutoSelectedQuality && proxyVideoTracks.isNotEmpty() && link?.quality != null && link.quality != com.lagradost.cloudstream3.utils.Qualities.Unknown.value) {
            val targetRes = link.quality
            // Try to find exact match first, fallback to closest resolution
            val match = proxyVideoTracks.find { it.name.contains("${targetRes}p") }
                ?: proxyVideoTracks.minByOrNull {
                    val res = it.name.substringBefore("p").toIntOrNull() ?: Int.MAX_VALUE
                    kotlin.math.abs(res - targetRes)
                }
            if (match != null) {
                playerState?.loadLazyVideoTrack(
                    com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(match.url, match.name, match.language, match.bitrate),
                )
            }
            hasAutoSelectedQuality = true
        }
    }

    fun pushSyncStateToWebView() {
        try {
            val rawBackdrop = backdropUrl?.let { raw -> if (raw.startsWith("//")) "https:$raw" else raw }
            val rawLogo = logoUrl?.let { raw -> if (raw.startsWith("//")) "https:$raw" else raw }
            val safeBackdrop = com.lagradost.cloudstream3.desktop.utils.ImageUtils.getCachedDiskFileUri(rawBackdrop) ?: rawBackdrop
            val safeLogo = com.lagradost.cloudstream3.desktop.utils.ImageUtils.getCachedDiskFileUri(rawLogo) ?: rawLogo

            val starringCast = actors.filter { actorData ->
                val role = actorData.roleString ?: actorData.role?.name
                role?.equals("Director", ignoreCase = true) != true &&
                    role?.equals("Creator", ignoreCase = true) != true &&
                    role?.equals("Writer", ignoreCase = true) != true &&
                    role?.equals("Producer", ignoreCase = true) != true &&
                    role?.equals("Executive Producer", ignoreCase = true) != true
            }

            val safeActors = starringCast.take(6).map { actorData ->
                val rawImg = actorData.actor.image?.let { raw -> if (raw.startsWith("//")) "https:$raw" else raw }
                val enhancedImg = com.lagradost.cloudstream3.desktop.utils.ImageUtils.enhanceProfileUrl(rawImg) ?: rawImg
                val safeImg = enhancedImg?.let { com.lagradost.cloudstream3.desktop.utils.ImageUtils.getCachedDiskFileUri(it) ?: it }
                ActorPayload(
                    name = actorData.actor.name,
                    role = actorData.roleString ?: actorData.role?.name,
                    image = safeImg,
                )
            }

            val availableShaders = com.lagradost.cloudstream3.desktop.player.ShaderManager.getAvailableShaders()
            val availableFonts = com.lagradost.cloudstream3.desktop.ui.theme.CustomFontManager.getAvailableFonts()
            val activeSubtitleFont = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_FONT)
            val activeSubtitleBackground = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BG)
            val activeSubtitleBorderColor = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_COLOR)
            val activeSubtitleBorderSize = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_SIZE)
            val activeSubtitleShadowColor = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_COLOR)
            val activeSubtitleShadowOffset = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_OFFSET)
            val activeSubtitleBlur = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BLUR)
            val activeSubtitleBold = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BOLD)
            val activeSubtitleItalic = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_ITALIC)

            val payload = PlayerUiSyncState(
                plot = plot,
                year = year,
                tags = tags,
                contentRating = contentRating,
                rating = rating,
                isProbing = isProbing,
                isScraping = isScraping,
                backdropUrl = safeBackdrop,
                logoUrl = safeLogo,
                currentLinkIndex = currentLinkIndex,
                failedLinks = failedLinks.map { FailedLinkPayload(it.key, it.value) },
                links = links.mapIndexed { index, l ->
                    val isTorrent = com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.isTorrentLink(l)
                    LinkPayload(
                        index = index,
                        name = l.name,
                        quality = l.quality,
                        isActive = (index == currentLinkIndex),
                        isM3u8 = l.isM3u8,
                        isDash = l.isDash,
                        url = l.url,
                        isTorrent = isTorrent,
                        source = l.source,
                    )
                },
                episodes = episodes.map {
                    val epRawPoster = it.posterUrl?.takeIf { p -> p.isNotBlank() }
                    val resolvedEpPoster = if (epRawPoster != null) {
                        if (epRawPoster.startsWith("//")) "https:$epRawPoster" else epRawPoster
                    } else {
                        seriesPosterUrl
                    }
                    val isCurrent = (currentEpisodeId != null && it.data == currentEpisodeId) ||
                        (currentEpisodeNumber != null && it.episode == currentEpisodeNumber && (currentSeasonNumber == null || it.season == null || it.season == currentSeasonNumber))
                    EpisodePayload(
                        id = it.data,
                        title = it.name ?: "Episode ${it.episode}",
                        season = it.season,
                        episode = it.episode,
                        isActive = isCurrent,
                        posterUrl = resolvedEpPoster?.let { url -> if (url.startsWith("//")) "https:$url" else url },
                        description = it.description,
                        runTime = it.runTime,
                        score = it.score?.toFloat(10)?.toDouble(),
                    )
                },
                audioTracks = audioTracks.map {
                    SubtitleTrackPayload(it.id, it.name, it.isSelected)
                },
                subTracks = subtitleTracks.map {
                    SubtitleTrackPayload(it.id, it.name, it.isSelected)
                },
                videoTracks = videoTracks.map {
                    SubtitleTrackPayload(it.id, it.name, it.isSelected)
                },
                lazyAudioTracks = proxyAudioTracks.map {
                    LazyTrackPayload(it.url, it.name, it.language)
                },
                lazySubTracks = proxySubtitleTracks.map {
                    LazyTrackPayload(it.url, it.name, it.language)
                },
                lazyVideoTracks = proxyVideoTracks.map {
                    LazyTrackPayload(it.url, it.name, it.language)
                },
                startPositionMs = startPositionMs,
                title = title ?: "",
                shaders = availableShaders,
                activeShader = activeShader,
                activeSubtitleFont = activeSubtitleFont,
                availableSubtitleFonts = availableFonts,
                activeSubtitleBackground = activeSubtitleBackground,
                activeSubtitleBorderColor = activeSubtitleBorderColor,
                activeSubtitleBorderSize = activeSubtitleBorderSize,
                activeSubtitleShadowColor = activeSubtitleShadowColor,
                activeSubtitleShadowOffset = activeSubtitleShadowOffset,
                activeSubtitleBlur = activeSubtitleBlur,
                activeSubtitleBold = activeSubtitleBold,
                activeSubtitleItalic = activeSubtitleItalic,
                activeLazyVideoTrackUrl = activeLazyVideoTrackUrl,
                activeLazyAudioTrackUrl = activeLazyAudioTrackUrl,
                resolution = resolution,
                activeSubtitleOverrideEnabled = activeSubtitleOverrideEnabled,
                isLive = isLive,
                isAudioOnlyStream = isAudioOnlyStream,
                isAudioMode = isAudioMode,
                chapters = chapters.map { ChapterPayload(it.index, it.title, it.timeMs) },
                currentChapterIndex = currentChapterIndex,
                activeSkipInterval = activeSkipInterval?.let {
                    SkipIntervalPayload(it.startMs, it.endMs, it.type.name, it.label, it.providerId)
                },
                skipIntervals = skipIntervals.map {
                    SkipIntervalPayload(it.startMs, it.endMs, it.type.name, it.label, it.providerId)
                },
                actors = safeActors,
                isExhausted = isExhausted,
                exhaustionReason = exhaustionReason,
                exhaustionDiagnostics = exhaustionDiagnostics,
            )

            val wrapper = MetadataUpdatePayloadWrapper(
                value = payload,
            )
            NativePlayerBridge.postMessage(playerObjectMapper.writeValueAsString(wrapper))
        } catch (e: Throwable) {
            com.lagradost.common.logging.AppLogger.e("pushSyncStateToWebView error: ${e.message}")
        }
    }

    LaunchedEffect(isUiReady, isLoading, links, currentLinkIndex, episodes, currentEpisodeId, currentEpisodeNumber, currentSeasonNumber, audioTracks, subtitleTracks, videoTracks, chapters, currentChapterIndex, proxyAudioTracks, proxySubtitleTracks, proxyVideoTracks, loadingStatusText, isProbing, isScraping, failedLinks, backdropUrl, logoUrl, title, activeShader, activeLazyVideoTrackUrl, activeLazyAudioTrackUrl, activeSkipInterval, skipIntervals, resolution, plot, year, tags, contentRating, rating, activeSubtitleOverrideEnabled, isLive, isAudioOnlyStream, isAudioMode, actors, isExhausted, exhaustionReason, exhaustionDiagnostics) {
        if (isUiReady) {
            if (isScraping && !isExhausted && !isLoading) {
                kotlinx.coroutines.delay(60L)
            }
            pushSyncStateToWebView()
        }
    }

    val toastMessage by (playerState?.toastMessage ?: kotlinx.coroutines.flow.flowOf(null)).collectAsState(null)
    LaunchedEffect(toastMessage) {
        if (isUiReady && toastMessage != null) {
            val payload = mapOf(
                "type" to "show_toast",
                "message" to toastMessage,
            )
            NativePlayerBridge.postMessage(playerObjectMapper.writeValueAsString(payload))
        }
    }

    val p2pTelemetry by com.lagradost.cloudstream3.desktop.torrent.DesktopTorrentEngine.telemetry.collectAsState()
    LaunchedEffect(isUiReady, p2pTelemetry) {
        if (isUiReady) {
            val payload = com.lagradost.cloudstream3.desktop.player.P2pStatsUpdatePayload(
                active = p2pTelemetry.active,
                downloadSpeed = p2pTelemetry.downloadSpeed,
                uploadSpeed = p2pTelemetry.uploadSpeed,
                peers = p2pTelemetry.peers,
                seeds = p2pTelemetry.seeds,
                preloadedBytes = p2pTelemetry.preloadedBytes,
                totalBytes = p2pTelemetry.totalBytes,
                progressPercent = p2pTelemetry.progressPercent,
                statusText = p2pTelemetry.statusText,
            )
            try {
                NativePlayerBridge.postMessage(playerObjectMapper.writeValueAsString(payload))
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("pushP2pTelemetry error: ${e.message}")
            }
        }
    }

    fun pushMetadataToWebView() {
        try {
            val vol = playerState?._volume?.value ?: 100f
            val isMuted = playerState?._isMuted?.value ?: false
            val isBuf = playerState?._isBuffering?.value == true

            var currentlyLoading = isBuf
            var isAppScraping = false
            var escapedLoadingText: String? = null
            try {
                currentlyLoading = isBuf
                isAppScraping = currentIsLoading
                escapedLoadingText = currentLoadingStatusText?.replace("\"", "\\\"")?.replace("\n", "\\n")
            } catch (e: Throwable) {
                com.lagradost.common.logging.AppLogger.e("Error escaping loading text", e)
            }

            val loadingTextJson = if (escapedLoadingText != null) "\"$escapedLoadingText\"" else "null"

            val interpolationEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_INTERPOLATION) ?: false
            val autoPlayEnabled = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY) ?: true
            val showEndTime = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SHOW_END_TIME) ?: false
            val showClock = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SHOW_CLOCK) ?: false
            val showServerQuality = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SHOW_SERVER_QUALITY) ?: false
            val pauseInfoMode = com.lagradost.common.storage.DesktopDataStore.getKey<String>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_PAUSE_INFO_MODE) ?: "delay_5s"
            val showPauseCast = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_PAUSE_SHOW_CAST) ?: true

            val payload = AppStateUpdatePayload(
                volume = vol,
                isMuted = isMuted,
                isAppLoading = isAppScraping,
                loadingStatusText = currentLoadingStatusText,
                debugWait = false,
                debugHasEver = true,
                debugPos = 0.0,
                interpolationEnabled = interpolationEnabled,
                autoPlayEnabled = autoPlayEnabled,
                showEndTime = showEndTime,
                showClock = showClock,
                showServerQuality = showServerQuality,
                pauseInfoMode = pauseInfoMode,
                showPauseCast = showPauseCast,
            )
            NativePlayerBridge.postMessage(playerObjectMapper.writeValueAsString(payload))
        } catch (e: Throwable) {
            com.lagradost.common.logging.AppLogger.e("pushMetadataToWebView error: ${e.message}")
        }
    }

    LaunchedEffect(isLoading, isBuffering, loadingStatusText) {
        if (isUiReady && playerState?.getNativeHandleValue() != null) {
            pushMetadataToWebView()
        }
    }

    BaseMpvPlayer(
        modifier = modifier,
        link = link,
        title = title,
        subtitles = subtitles,
        startPositionMs = startPositionMs,
        shouldPauseForResume = shouldPauseForResume,
        onPlaybackReady = {
            com.lagradost.cloudstream3.desktop.player.webview.NativePlayerBridge.executeScript("if (window.__dismissProbingOverlay) window.__dismissProbingOverlay();")
            currentOnPlaybackReady()
        },
        onPlaybackError = currentOnPlaybackError,
        onFinished = currentOnFinished,
        onPositionChange = { posMs, durMs ->
            currentOnPositionChange(posMs, durMs)
            if (isUiReady) pushMetadataToWebView()
        },
        onCloseRequest = currentOnCloseRequest,
        isExiting = isExiting,
        isLive = isLive,
        onFullscreenToggle = currentOnFullscreenToggle,
        playerState = playerState,
        onEventLoopReady = { h ->
            playerState?.updateAudioFilters()
            if (isUiReady) {
                NativePlayerBridge.startMpvSync(com.sun.jna.Pointer.nativeValue(h))
            }
        },
        onPreInitialize = { handle, canvasWid, w, h ->
            val childHwnd = NativePlayerBridge.initWebView(canvasWid, w, h)
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "wid", childHwnd.toString())
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "vo", "gpu")
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "gpu-api", "d3d11")

            val delaySec = com.lagradost.common.storage.DesktopDataStore.getKey<Float>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_DELAY) ?: 0f
            MpvLibrary.INSTANCE.mpv_set_option_string(handle, "audio-delay", delaySec.toString())

            val audioMax = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_VOLUME_MAX) ?: false
            if (audioMax) {
                MpvLibrary.INSTANCE.mpv_set_option_string(handle, "volume-max", "200")
            }
        },
        onPostInitialize = { handle ->
            val webView2DataDir = File(System.getProperty("java.io.tmpdir"), "CloudStreamWebView2")
            webView2DataDir.mkdirs()
            val tempFile = File(webView2DataDir, "cloudstream_controls.html")

            val htmlTemplate = NativePlayerBridge.loadPlayerUiResource("/player-ui/player.html")
            val cssContent = NativePlayerBridge.loadPlayerUiResource("/player-ui/player.css")
            val jsContent = NativePlayerBridge.loadPlayerUiResource("/player-ui/player.js")

            val rawInitialBackdrop = backdropUrl ?: (episodes.find { it.data == currentEpisodeId }?.posterUrl ?: "")
            val initialBackdropUrl = com.lagradost.cloudstream3.desktop.utils.ImageUtils.getCachedDiskFileUri(rawInitialBackdrop) ?: rawInitialBackdrop
            val initialBackdropClass = if (initialBackdropUrl.isNotEmpty()) "loaded" else ""
            val rawInitialLogo = logoUrl ?: ""
            val initialLogoUrl = com.lagradost.cloudstream3.desktop.utils.ImageUtils.getCachedDiskFileUri(rawInitialLogo) ?: rawInitialLogo
            val hasLogo = initialLogoUrl.isNotEmpty()
            val initialLogoStyle = if (hasLogo) "display: block;" else "display: none;"
            val initialTitleStyle = if (hasLogo) "display: none;" else "display: block;"

            val activeEp = episodes.find { it.data == currentEpisodeId }
            val initialTitle = (title ?: "").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            val initialSubtitle = if (activeEp != null) {
                val s = activeEp.season ?: 1
                val ep = activeEp.episode
                val epTitle = activeEp.name?.ifEmpty { "Episode $ep" } ?: "Episode $ep"
                "S$s:E$ep • $epTitle"
            } else ""
            val initialSubtitleStyle = if (initialSubtitle.isNotEmpty()) "display: block;" else "display: none;"

            val htmlContent = htmlTemplate
                .replace("/* CSS_INJECT */", cssContent)
                .replace("/* JS_INJECT */", jsContent)
                .replace("{{ACCENT_COLOR}}", accentColorHex)
                .replace("{{ACCENT_COLOR_RGB}}", accentColorRgb)
                .replace("{{INITIAL_BACKDROP_URL}}", initialBackdropUrl)
                .replace("{{INITIAL_BACKDROP_CLASS}}", initialBackdropClass)
                .replace("{{INITIAL_LOGO_URL}}", initialLogoUrl)
                .replace("{{INITIAL_LOGO_STYLE}}", initialLogoStyle)
                .replace("{{INITIAL_TITLE}}", initialTitle)
                .replace("{{INITIAL_TITLE_STYLE}}", initialTitleStyle)
                .replace("{{INITIAL_SUBTITLE}}", initialSubtitle)
                .replace("{{INITIAL_SUBTITLE_STYLE}}", initialSubtitleStyle)

            if (htmlContent.isNotEmpty() && htmlTemplate.isNotEmpty()) {
                tempFile.writeText(htmlContent, Charsets.UTF_8)
            } else {
                com.lagradost.common.logging.AppLogger.e("[NativePlayer] player-ui resources not found!")
            }

            NativePlayerBridge.setEventListener(object : NativePlayerBridge.NativePlayerEventListener {
                override fun onPlayerEvent(type: String, value: String) {
                    if (type != "message") return

                    val rootNode = try {
                        playerObjectMapper.readTree(value)
                    } catch (t: Throwable) {
                        null
                    }
                    when (val event = PlayerInboundEvent.fromJson(rootNode, value)) {
                        is PlayerInboundEvent.UiReady -> {
                            isUiReady = true
                            pushMetadataToWebView()
                            pushSyncStateToWebView()
                            val nativeHandle = playerState?.getNativeHandleValue() ?: com.sun.jna.Pointer.nativeValue(handle)
                            if (nativeHandle != 0L) {
                                NativePlayerBridge.startMpvSync(nativeHandle)
                            }
                            NativePlayerBridge.focusWebView()
                        }
                        is PlayerInboundEvent.SelectShader -> {
                            playerState?.setShader(event.name)
                        }
                        is PlayerInboundEvent.SearchSubtitles -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    com.lagradost.common.logging.AppLogger.i("Player:Web", "Searching online subtitles: query='${event.query}', lang='${event.lang}', season=${event.season}, episode=${event.episode}")
                                    val allResults = SubtitleExtractionService.searchSubtitles(event.query, event.lang, event.season, event.episode)
                                    com.lagradost.common.logging.AppLogger.i("Player:Web", "Subtitle search complete: returned ${allResults.size} tracks")

                                    val json = playerObjectMapper.writeValueAsString(
                                        mapOf("type" to "subtitle_search_results", "results" to allResults),
                                    )
                                    NativePlayerBridge.postMessage(json)
                                } catch (e: Exception) {
                                    com.lagradost.common.logging.AppLogger.e("Player:Web", "searchSubtitles error: ${e.message}", e)
                                }
                            }
                        }
                        is PlayerInboundEvent.DownloadSubtitle -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                try {
                                    val idPrefix = event.idPrefix.takeIf { it.isNotBlank() }
                                    val data = event.data.takeIf { it.isNotBlank() }
                                    val name = event.name
                                    val lang = event.lang
                                    val source = event.source

                                    com.lagradost.common.logging.AppLogger.i("Player:Web", "Received downloadSubtitle event: idPrefix=$idPrefix, name=$name, lang=$lang, source=$source, dataLen=${data?.length}")

                                    if (idPrefix == null || data == null) {
                                        com.lagradost.common.logging.AppLogger.w("Player:Web", "downloadSubtitle aborted: idPrefix=$idPrefix, data=$data")
                                        return@launch
                                    }

                                    com.lagradost.common.logging.AppLogger.i("Player:Web", "Downloading subtitle '$name' from provider prefix '$idPrefix'...")
                                    val safeUrl = SubtitleExtractionService.downloadAndExtractSubtitle(
                                        idPrefix = idPrefix,
                                        data = data,
                                        name = name,
                                        lang = lang,
                                        source = source,
                                    )

                                    if (safeUrl != null) {
                                        if (!persistentSubtitles.contains(safeUrl)) {
                                            persistentSubtitles.add(safeUrl)
                                        }
                                        val cleanPath = safeUrl.replace("\\", "/")
                                        val cleanName = name.replace("\"", "").replace("\n", "").trim()
                                        val cleanLang = lang.replace("\"", "").trim()

                                        com.lagradost.common.logging.AppLogger.i("Player:Web", "Applying subtitle to MPV: path='$cleanPath', name='$cleanName', lang='$cleanLang'")
                                        playerState?.executeCommand("sub-add \"$cleanPath\" select \"$cleanName\" \"$cleanLang\"")
                                        playerState?.setMpvProperty("sub-visibility", "yes")

                                        kotlinx.coroutines.delay(250)
                                        pushMetadataToWebView()

                                        val toastJson = playerObjectMapper.writeValueAsString(
                                            mapOf("type" to "show_toast", "message" to "Loaded subtitle: $cleanName"),
                                        )
                                        NativePlayerBridge.postMessage(toastJson)
                                    } else {
                                        com.lagradost.common.logging.AppLogger.w("Player:Web", "Failed to extract/download subtitle for '$name'")
                                        val errToastJson = playerObjectMapper.writeValueAsString(
                                            mapOf("type" to "show_toast", "message" to "Failed to download subtitle: $name"),
                                        )
                                        NativePlayerBridge.postMessage(errToastJson)
                                    }
                                } catch (e: Exception) {
                                    com.lagradost.common.logging.AppLogger.e("Player:Web", "downloadSubtitle error: ${e.message}", e)
                                }
                            }
                        }
                        is PlayerInboundEvent.SkipInterval -> {
                            playerState?.skipCurrentInterval()
                        }
                        is PlayerInboundEvent.SeekTo -> {
                            playerState?.seekTo(event.positionMs.toLong())
                        }
                        is PlayerInboundEvent.SeekBy -> {
                            playerState?.seekBy(event.deltaMs.toLong())
                        }
                        is PlayerInboundEvent.SeekLive -> {
                            playerState?.seekLive()
                        }
                        is PlayerInboundEvent.TogglePlay -> {
                            val isPaused = playerState?.isPaused?.value ?: false
                            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Received togglePlay event. isPaused=$isPaused")
                            if (isPaused) {
                                playerState?.play()
                            } else {
                                playerState?.pause()
                            }
                        }
                        is PlayerInboundEvent.Play -> {
                            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Received play event")
                            playerState?.play()
                        }
                        is PlayerInboundEvent.Pause -> {
                            com.lagradost.common.logging.AppLogger.i("BaseMpvPlayer: Received pause event")
                            playerState?.pause()
                        }
                        is PlayerInboundEvent.ToggleMute -> {
                            val currentMuted = playerState?.isMuted?.value ?: false
                            val nextMuted = event.forcedState ?: !currentMuted
                            playerState?.setMute(nextMuted)
                            pushMetadataToWebView()
                        }
                        is PlayerInboundEvent.SetVolume -> {
                            playerState?.setVolume(event.volume.toFloat())
                        }
                        is PlayerInboundEvent.SetSpeed -> {
                            playerState?.setSpeed(event.speed.toFloat())
                        }
                        is PlayerInboundEvent.SetSubDelay -> {
                            playerState?.executeCommand("add sub-delay ${event.delaySeconds}")
                        }
                        is PlayerInboundEvent.CycleSubtitles -> {
                            playerState?.cycleSubtitles()
                        }
                        is PlayerInboundEvent.ToggleSubVisibility -> {
                            playerState?.toggleSubtitleVisibility()
                        }
                        is PlayerInboundEvent.ToggleLoop -> {
                            val loopVal = if (event.loop) "inf" else "no"
                            playerState?.setMpvProperty("loop-file", loopVal)
                        }
                        is PlayerInboundEvent.ToggleAudioMode -> {
                            playerState?.toggleAudioMode(event.enabled)
                            pushSyncStateToWebView()
                        }
                        is PlayerInboundEvent.SetPauseInfoMode -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_PAUSE_INFO_MODE, event.mode)
                                pushMetadataToWebView()
                            }
                        }
                        is PlayerInboundEvent.SetPauseShowCast -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                val currentVal = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_PAUSE_SHOW_CAST) ?: true
                                val nextVal = event.forcedState ?: !currentVal
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_PAUSE_SHOW_CAST, nextVal)
                                pushMetadataToWebView()
                            }
                        }
                        is PlayerInboundEvent.Screenshot -> {
                            playerState?.executeCommand("screenshot video")
                            val dirName = com.lagradost.common.platform.PlatformPaths.screenshotsDir.name
                            playerState?.showToast("Screenshot saved to $dirName")
                        }
                        is PlayerInboundEvent.TogglePip -> {
                            val nextPip = !com.lagradost.cloudstream3.desktop.ui.PipState.isPipMode.value
                            com.lagradost.cloudstream3.desktop.ui.PipState.setPipMode(nextPip)
                            NativePlayerBridge.executeScript("if(window.setPipUi) window.setPipUi($nextPip);")
                        }
                        is PlayerInboundEvent.StartWindowDrag -> {
                            val hwnd = com.sun.jna.Native.getComponentID(window)
                            val hWin = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(hwnd))
                            com.lagradost.cloudstream3.desktop.init.ExtUser32.INSTANCE.ReleaseCapture()
                            com.sun.jna.platform.win32.User32.INSTANCE.PostMessage(
                                hWin,
                                0x0112,
                                com.sun.jna.platform.win32.WinDef.WPARAM(0xF012),
                                com.sun.jna.platform.win32.WinDef.LPARAM(0),
                            )
                        }
                        is PlayerInboundEvent.StartWindowResize -> {
                            val hwnd = com.sun.jna.Native.getComponentID(window)
                            val hWin = com.sun.jna.platform.win32.WinDef.HWND(com.sun.jna.Pointer(hwnd))
                            com.lagradost.cloudstream3.desktop.init.ExtUser32.INSTANCE.ReleaseCapture()

                            val hitTest = when (event.direction) {
                                "left" -> 1
                                "right" -> 2
                                "top" -> 3
                                "top-left" -> 4
                                "top-right" -> 5
                                "bottom" -> 6
                                "bottom-left" -> 7
                                "bottom-right" -> 8
                                else -> 8
                            }

                            com.sun.jna.platform.win32.User32.INSTANCE.PostMessage(
                                hWin,
                                0x0112,
                                com.sun.jna.platform.win32.WinDef.WPARAM((0xF000 + hitTest).toLong()),
                                com.sun.jna.platform.win32.WinDef.LPARAM(0),
                            )
                        }
                        is PlayerInboundEvent.ToggleFullscreen -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                currentOnFullscreenToggle?.invoke()
                            }
                        }
                        is PlayerInboundEvent.FocusWebView -> {
                            NativePlayerBridge.focusWebView()
                        }
                        is PlayerInboundEvent.ExitPlayer -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                currentOnCloseRequest()
                            }
                        }
                        is PlayerInboundEvent.RetryPlayback -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onRetryPlayback?.invoke()
                            }
                        }
                        is PlayerInboundEvent.CopyDiagnostics -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                com.lagradost.cloudstream3.desktop.utils.ExternalLinkHandler.copyToClipboard(event.text)
                                val toastJson = playerObjectMapper.writeValueAsString(
                                    mapOf("type" to "show_toast", "message" to "Diagnostics copied to clipboard"),
                                )
                                NativePlayerBridge.postMessage(toastJson)
                            }
                        }
                        is PlayerInboundEvent.ChangeLink -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                if (event.linkUrl.isNotEmpty()) onLinkChange?.invoke(event.linkUrl)
                            }
                        }
                        is PlayerInboundEvent.LoadEpisode -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onEpisodeChange?.invoke(event.episodeId)
                            }
                        }
                        is PlayerInboundEvent.SetAudioTrack -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setAudioTrack(event.id)
                            }
                        }
                        is PlayerInboundEvent.SetVideoTrack -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setVideoTrack(event.id)
                            }
                        }
                        is PlayerInboundEvent.NextChapter -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.nextChapter()
                            }
                        }
                        is PlayerInboundEvent.PreviousChapter -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.previousChapter()
                            }
                        }
                        is PlayerInboundEvent.SeekToChapter -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.seekToChapter(event.index)
                            }
                        }
                        is PlayerInboundEvent.LazyAudioTrack -> {
                            val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyAudioTracks.value.find { it.url == event.url }
                            if (track != null) {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.loadLazyAudioTrack(
                                        com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language),
                                    )
                                }
                            }
                        }
                        is PlayerInboundEvent.LazySubtitleTrack -> {
                            val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazySubtitleTracks.value.find { it.url == event.url }
                            if (track != null) {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.loadLazySubtitleTrack(
                                        com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language),
                                    )
                                }
                            }
                        }
                        is PlayerInboundEvent.LazyVideoTrack -> {
                            val track = com.lagradost.player.impl.proxy.LocalStreamProxyState.lazyVideoTracks.value.find { it.url == event.url }
                            if (track != null) {
                                scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                    playerState?.loadLazyVideoTrack(
                                        com.lagradost.cloudstream3.desktop.ui.screens.player.PlayerState.LazyTrack(track.url, track.name, track.language, track.bitrate),
                                    )
                                }
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleTrack -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setSubtitleTrack(event.id)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleFont -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_FONT, event.fontName ?: "")
                                playerState?.setSubtitleFont(event.fontName)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleOverrideEnabled -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setSubtitleOverrideEnabled(event.enabled)
                            }
                        }
                        is PlayerInboundEvent.ResetSubtitleSettings -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_FONT)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_COLOR)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SIZE)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BG)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_COLOR)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_SIZE)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_COLOR)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_OFFSET)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BLUR)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BOLD)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_ITALIC)
                                com.lagradost.common.storage.DesktopDataStore.removeKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_ENABLE_SUB_OVERRIDE)

                                playerState?.setSubtitleFont(null)
                                playerState?.setSubtitleOverrideEnabled(false)
                                playerState?.setMpvProperty("sub-color", "#FFFFFF")
                                playerState?.setMpvProperty("sub-font-size", "45")
                                val (defMpvBg, defBorderStyle) = com.lagradost.cloudstream3.desktop.player.PlayerConfig.toMpvBackgroundColor(null)
                                playerState?.setMpvProperty("sub-back-color", defMpvBg)
                                playerState?.setMpvProperty("sub-border-style", defBorderStyle)
                                playerState?.setMpvProperty("sub-border-color", "#000000")
                                playerState?.setMpvProperty("sub-border-size", "3")
                                playerState?.setMpvProperty("sub-shadow-color", "#000000")
                                playerState?.setMpvProperty("sub-shadow-offset", "0")
                                playerState?.setMpvProperty("sub-blur", "0")
                                playerState?.setMpvProperty("sub-bold", "no")
                                playerState?.setMpvProperty("sub-italic", "no")
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleBackground -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BG, event.backgroundKey)
                                val (mpvBgColor, borderStyle) = com.lagradost.cloudstream3.desktop.player.PlayerConfig.toMpvBackgroundColor(event.backgroundKey)
                                playerState?.setMpvProperty("sub-back-color", mpvBgColor)
                                playerState?.setMpvProperty("sub-border-style", borderStyle)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleBorderColor -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_COLOR, event.color)
                                playerState?.setMpvProperty("sub-border-color", event.color)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleBorderSize -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BORDER_SIZE, event.size)
                                playerState?.setMpvProperty("sub-border-size", event.size)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleShadowColor -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_COLOR, event.color)
                                playerState?.setMpvProperty("sub-shadow-color", event.color)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleShadowOffset -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SHADOW_OFFSET, event.offset)
                                playerState?.setMpvProperty("sub-shadow-offset", event.offset)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleBlur -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BLUR, event.blur)
                                playerState?.setMpvProperty("sub-blur", event.blur)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleBold -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_BOLD, event.bold)
                                playerState?.setMpvProperty("sub-bold", event.bold)
                            }
                        }
                        is PlayerInboundEvent.SetSubtitleItalic -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_ITALIC, event.italic)
                                playerState?.setMpvProperty("sub-italic", event.italic)
                            }
                        }
                        is PlayerInboundEvent.ToggleInterpolation -> {
                            playerState?.setInterpolation(event.enabled)
                        }
                        is PlayerInboundEvent.ToggleDeband -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_DEBAND, event.enabled)
                                playerState?.setMpvProperty("deband", if (event.enabled) "yes" else "no")
                            }
                        }
                        is PlayerInboundEvent.SetAudioNormalization -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORMALIZATION, event.enabled)
                                playerState?.updateAudioFilters()
                            }
                        }
                        is PlayerInboundEvent.SetAudioNormStrength -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_NORM_STRENGTH, event.strength)
                                playerState?.updateAudioFilters()
                            }
                        }
                        is PlayerInboundEvent.SetAudioSpatial -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_SPATIAL, event.enabled)
                                playerState?.updateAudioFilters()
                            }
                        }
                        is PlayerInboundEvent.SetAudioEqPreset -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_EQ_PRESET, event.preset)
                                playerState?.updateAudioFilters()
                            }
                        }
                        is PlayerInboundEvent.SetAudioVolumeMax -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_VOLUME_MAX, event.enabled)
                                playerState?.setMpvProperty("volume-max", if (event.enabled) "200" else "100")
                            }
                        }
                        is PlayerInboundEvent.SetAudioDelay -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUDIO_DELAY, event.delaySec)
                                playerState?.setMpvProperty("audio-delay", event.delaySec.toString())
                            }
                        }
                        is PlayerInboundEvent.ToggleAutoPlay -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_AUTO_PLAY, event.enabled)
                            }
                        }
                        is PlayerInboundEvent.SetPrefShowEndTime -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SHOW_END_TIME, event.enabled)
                            }
                        }
                        is PlayerInboundEvent.SetPrefShowClock -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SHOW_CLOCK, event.enabled)
                            }
                        }
                        is PlayerInboundEvent.SetPrefShowServerQuality -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SHOW_SERVER_QUALITY, event.enabled)
                            }
                        }
                        is PlayerInboundEvent.LoadNextEpisode -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onNextEpisode?.invoke()
                            }
                        }
                        is PlayerInboundEvent.ReplayEpisode -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onReplayEpisode?.invoke()
                            }
                        }
                        is PlayerInboundEvent.SetMpvProperty -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                                playerState?.setMpvProperty(event.property, event.value)
                                when (event.property) {
                                    "sub-color" -> com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_COLOR, event.value)
                                    "sub-font-size" -> com.lagradost.common.storage.DesktopDataStore.setKey(com.lagradost.cloudstream3.desktop.player.PlayerConfig.PREF_SUB_SIZE, event.value)
                                }
                            }
                        }
                        is PlayerInboundEvent.SkipScraping -> {
                            scope.launch(kotlinx.coroutines.Dispatchers.Main) {
                                onSkipScraping?.invoke()
                            }
                        }
                        is PlayerInboundEvent.Unknown -> {
                            com.lagradost.common.logging.AppLogger.w("Player:IPC", "Unhandled or unknown inbound player event: ${event.type} -> ${event.rawValue}")
                        }
                    }
                }
            })

            if (tempFile.exists()) {
                NativePlayerBridge.loadUrl(tempFile.absoluteFile.toURI().toString())
            }
        },
        videoRenderer = { videoCanvas, _ ->
            DisposableEffect(Unit) {
                val componentListener = object : ComponentAdapter() {
                    override fun componentResized(e: ComponentEvent) {
                        NativePlayerBridge.resizeWebView(e.component.width, e.component.height)
                    }
                }
                videoCanvas.addComponentListener(componentListener)
                // Force initial layout push so WebView isn't hidden until the first resize
                NativePlayerBridge.resizeWebView(videoCanvas.width, videoCanvas.height)
                onDispose {
                    videoCanvas.isVisible = false
                    videoCanvas.removeComponentListener(componentListener)
                    NativePlayerBridge.resizeWebView(0, 0)
                    NativePlayerBridge.stopMpvSync()
                    NativePlayerBridge.setEventListener(null)

                    if (com.lagradost.cloudstream3.desktop.ui.PipState.isPipMode.value) {
                        com.lagradost.cloudstream3.desktop.ui.PipState.setPipMode(false)
                    }

                    // Push the heavy WebView teardown to a background daemon thread
                    // to prevent blocking the Compose EDT on first exit.
                    java.lang.Thread({
                        com.lagradost.common.logging.AppLogger.i("NativePlayer: Destroying WebView on daemon thread...")
                        NativePlayerBridge.destroyWebView()
                    }, "cs3-webview-dispose").apply {
                        isDaemon = true
                        start()
                    }
                }
            }

            LaunchedEffect(isExiting) {
                if (isExiting) {
                    videoCanvas.isVisible = false
                    videoCanvas.bounds = java.awt.Rectangle(0, 0, 0, 0)
                    NativePlayerBridge.resizeWebView(0, 0)
                }
            }

            SwingPanel(
                background = androidx.compose.ui.graphics.Color.Black,
                factory = { videoCanvas },
                modifier = Modifier.fillMaxSize(),
            )
        },
    )
}
