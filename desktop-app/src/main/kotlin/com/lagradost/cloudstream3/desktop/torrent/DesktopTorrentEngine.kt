package com.lagradost.cloudstream3.desktop.torrent

import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLEncoder

data class P2pLiveTelemetry(
    val active: Boolean = false,
    val hash: String? = null,
    val downloadSpeed: Long = 0L,
    val uploadSpeed: Long = 0L,
    val peers: Int = 0,
    val seeds: Int = 0,
    val preloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val progressPercent: Int = 0,
    val statusText: String? = null,
)

object DesktopTorrentEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val binary = DesktopTorrServerBinary()
    val api = DesktopTorrServerApi(binary)

    private val _telemetry = MutableStateFlow(P2pLiveTelemetry())
    val telemetry: StateFlow<P2pLiveTelemetry> = _telemetry.asStateFlow()

    private var pollingJob: Job? = null
    private var currentHash: String? = null

    val isP2pEnabled: Boolean
        get() = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_P2P_ENABLED) ?: false

    fun isTorrentProvider(provider: com.lagradost.cloudstream3.MainAPI?): Boolean {
        if (provider == null) return false
        return provider.supportedTypes.any { it.name.contains("Torrent", ignoreCase = true) } ||
                provider.name.contains("torrent", ignoreCase = true) ||
                provider.name.equals("yts", ignoreCase = true) ||
                provider.mainUrl.contains("yts", ignoreCase = true) ||
                provider.mainUrl.contains("torrent", ignoreCase = true)
    }

    fun isTorrentLink(link: ExtractorLink): Boolean {
        val url = link.url.trim()
        return link.type == ExtractorLinkType.TORRENT ||
                link.type == ExtractorLinkType.MAGNET ||
                url.startsWith("magnet:", ignoreCase = true) ||
                url.contains("magnet:?xt=", ignoreCase = true) ||
                url.endsWith(".torrent", ignoreCase = true) ||
                (url.length == 40 && url.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })
    }

    suspend fun transformLink(link: ExtractorLink): ExtractorLink = withContext(Dispatchers.IO) {
        if (!isP2pEnabled) {
            AppLogger.w("DesktopTorrentEngine: P2P Torrent Streaming is disabled in settings. Blocking playback.")
            throw IllegalStateException("P2P Torrent Streaming is disabled. Please enable it in Settings to stream torrents.")
        }

        val rawUrl = link.url.trim()
        val magnetLink = when {
            rawUrl.contains("magnet:?xt=", ignoreCase = true) -> {
                "magnet:?xt=" + rawUrl.substringAfter("magnet:?xt=")
            }
            rawUrl.length == 40 && rawUrl.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> {
                buildMagnetUri(rawUrl)
            }
            else -> rawUrl
        }

        AppLogger.i("DesktopTorrentEngine initializing stream for: ${link.name} ($magnetLink)")
        _telemetry.value = P2pLiveTelemetry(active = true, statusText = "Connecting to swarm...")

        binary.start()

        val hash = api.addTorrent(magnetLink, title = link.name)
            ?: throw IllegalStateException("Failed to add torrent to TorrServer")

        currentHash = hash

        // Wait up to 12 seconds for torrent metadata (files list) from DHT / trackers
        var fileIdx = 1
        var seeds = 0
        var peers = 0
        var totalSize = 0L

        for (i in 0..24) {
            val stats = api.getTorrentStats(hash)
            if (stats != null) {
                seeds = stats.connectedSeeders
                peers = stats.activePeers
                totalSize = stats.torrentSize
                if (stats.fileStats.isNotEmpty()) {
                    val videoFiles = stats.fileStats.filter { file ->
                        val ext = file.path.substringAfterLast('.', "").lowercase()
                        ext in listOf("mkv", "mp4", "avi", "mov", "webm", "ts", "m4v")
                    }
                    val bestFile = videoFiles.maxByOrNull { it.length } ?: stats.fileStats.maxByOrNull { it.length }
                    if (bestFile != null) {
                        fileIdx = if (bestFile.id > 0) bestFile.id else 1
                        AppLogger.i("DesktopTorrentEngine matched video file: ${bestFile.path} (id=$fileIdx, size=${bestFile.length})")
                        break
                    }
                }
            }
            delay(500)
        }

        // Start preloading the file in TorrServer ring buffer
        api.preloadTorrent(hash, fileIdx)

        // Wait up to 6 seconds for initial buffer or peer connection so MPV doesn't hit EOF
        for (i in 0..12) {
            val stats = api.getTorrentStats(hash)
            if (stats != null && (stats.preloadedBytes > 0 || stats.loadedSize > 0 || stats.downloadSpeed > 0 || stats.connectedSeeders > 0)) {
                AppLogger.i("DesktopTorrentEngine swarm active: seeds=${stats.connectedSeeders}, preloaded=${stats.preloadedBytes}")
                break
            }
            delay(500)
        }

        val encodedMagnet = URLEncoder.encode(magnetLink, "UTF-8")
        val streamUrl = "${binary.baseUrl}/stream?link=$encodedMagnet&index=$fileIdx&play"
        AppLogger.i("DesktopTorrentEngine resolved stream URL: $streamUrl (File #$fileIdx, Seeds: $seeds, Peers: $peers)")

        startStatsPolling(hash)

        newExtractorLink(
            source = link.source,
            name = link.name,
            url = streamUrl,
            type = ExtractorLinkType.VIDEO,
        ) {
            this.referer = ""
            this.quality = link.quality
        }
    }

    private fun startStatsPolling(hash: String) {
        pollingJob?.cancel()
        pollingJob = scope.launch {
            while (isActive && currentHash == hash) {
                val stats = api.getTorrentStats(hash)
                if (stats != null) {
                    val progress = if (stats.preloadSize > 0) {
                        ((stats.preloadedBytes.toDouble() / stats.preloadSize) * 100).toInt().coerceIn(0, 100)
                    } else if (stats.torrentSize > 0) {
                        ((stats.loadedSize.toDouble() / stats.torrentSize) * 100).toInt().coerceIn(0, 100)
                    } else 0

                    val stateText = when {
                        stats.stat == 1 -> "Connecting to peers..."
                        stats.stat == 2 -> "Preloading buffer ($progress%)..."
                        stats.stat == 3 -> "Downloading..."
                        else -> stats.statString ?: "Streaming"
                    }

                    _telemetry.value = P2pLiveTelemetry(
                        active = true,
                        hash = hash,
                        downloadSpeed = stats.downloadSpeedLong,
                        uploadSpeed = stats.uploadSpeedLong,
                        peers = stats.activePeers,
                        seeds = stats.connectedSeeders,
                        preloadedBytes = stats.preloadedBytes,
                        totalBytes = stats.torrentSize,
                        progressPercent = progress,
                        statusText = stateText,
                    )
                }
                delay(1000)
            }
        }
    }

    fun stopStream() {
        pollingJob?.cancel()
        pollingJob = null
        val hash = currentHash
        currentHash = null
        _telemetry.value = P2pLiveTelemetry(active = false)

        if (hash != null) {
            scope.launch {
                api.dropTorrent(hash)
            }
        }
    }

    private fun buildMagnetUri(infoHash: String): String {
        val trackers = listOf(
            "udp://tracker.opentrackr.org:1337/announce",
            "udp://open.stealth.si:80/announce",
            "udp://tracker.torrent.eu.org:451/announce",
            "udp://tracker.moeking.me:6969/announce",
            "udp://explodie.org:6969/announce",
            "udp://open.demonii.com:1337/announce",
            "http://tracker.openbittorrent.com:80/announce",
            "udp://tracker.openbittorrent.com:6969/announce",
            "udp://exodus.desync.com:6969/announce",
            "udp://tracker.bittor.pw:1337/announce",
        )
        val sb = StringBuilder("magnet:?xt=urn:btih:").append(infoHash.lowercase())
        trackers.forEach { tr ->
            sb.append("&tr=").append(URLEncoder.encode(tr, "UTF-8"))
        }
        return sb.toString()
    }
}
