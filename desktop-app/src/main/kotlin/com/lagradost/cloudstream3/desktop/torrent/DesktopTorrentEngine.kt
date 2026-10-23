package com.lagradost.cloudstream3.desktop.torrent

import com.lagradost.cloudstream3.desktop.debrid.torbox.TorboxApiClient
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.net.URLEncoder

enum class TorrentPlayability {
    READY,
    NEEDS_P2P_ENABLED,
    NEEDS_TORRSERVER_INSTALL,
}

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

    private val transformMutex = Mutex()
    private var inFlightTransformJob: kotlinx.coroutines.Deferred<ExtractorLink>? = null
    private var inFlightTransformUrl: String? = null
    private var cachedResolvedLink: ExtractorLink? = null

    val isP2pEnabled: Boolean
        get() = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_P2P_ENABLED) ?: false

    val isP2pReady: Boolean
        get() = isP2pEnabled && binary.isInstalled()

    fun isTorrentProvider(provider: com.lagradost.cloudstream3.MainAPI?): Boolean {
        if (provider == null) return false
        return provider.supportedTypes.any { it.name.contains("Torrent", ignoreCase = true) } ||
            provider.name.contains("torrent", ignoreCase = true) ||
            provider.name.equals("yts", ignoreCase = true) ||
            provider.mainUrl.contains("yts", ignoreCase = true) ||
            provider.mainUrl.contains("torrent", ignoreCase = true)
    }

    fun isPureTorrentProvider(provider: com.lagradost.cloudstream3.MainAPI?): Boolean {
        if (provider == null) return false
        val types = provider.supportedTypes
        val hasTorrent = isTorrentProvider(provider)
        val hasNonTorrent = types.any { !it.name.contains("Torrent", ignoreCase = true) }
        return hasTorrent && !hasNonTorrent
    }

    fun isHybridTorrentProvider(provider: com.lagradost.cloudstream3.MainAPI?): Boolean {
        if (provider == null) return false
        val types = provider.supportedTypes
        val hasTorrent = isTorrentProvider(provider)
        val hasNonTorrent = types.any { !it.name.contains("Torrent", ignoreCase = true) }
        return hasTorrent && hasNonTorrent
    }

    fun isTorrentUrl(rawUrl: String?): Boolean {
        if (rawUrl.isNullOrBlank()) return false
        val url = rawUrl.trim()
        val lower = url.lowercase(java.util.Locale.ROOT)
        return lower.startsWith("magnet:") ||
            lower.contains("magnet:?xt=") ||
            lower.contains("xt=urn:btih:") ||
            lower.contains("urn:btih:") ||
            lower.contains("info_hash=") ||
            lower.endsWith(".torrent") ||
            lower.contains(".torrent?") ||
            lower.contains("127.0.0.1:8091") ||
            lower.contains("localhost:8091") ||
            lower.contains("/torrent/play") ||
            lower.contains("/play/torrent") ||
            ((url.length == 40 || url.length == 32) && url.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' })
    }

    fun isTorrentLink(link: ExtractorLink?): Boolean {
        if (link == null) return false
        if (link.type == ExtractorLinkType.TORRENT || link.type == ExtractorLinkType.MAGNET) return true
        if (isTorrentUrl(link.url)) return true
        if (link.extractorData?.contains("torrent", ignoreCase = true) == true ||
            link.extractorData?.contains("magnet", ignoreCase = true) == true
        ) {
            return true
        }
        val lowerName = link.name.lowercase(java.util.Locale.ROOT)
        return lowerName.contains("[torrent]") || lowerName.contains(" p2p") || lowerName.contains("[p2p]")
    }

    fun checkTorrentPlayability(link: ExtractorLink?): TorrentPlayability {
        if (!isTorrentLink(link)) return TorrentPlayability.READY
        val torboxApiKey = DesktopDataStore.getKey<String>(DesktopDataStore.PREF_TORBOX_API_KEY)
        val torboxAutoResolve = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_TORBOX_AUTO_RESOLVE) ?: true
        if (!torboxApiKey.isNullOrBlank() && torboxAutoResolve) {
            return TorrentPlayability.READY
        }
        if (!isP2pEnabled) return TorrentPlayability.NEEDS_P2P_ENABLED
        if (!binary.isInstalled()) return TorrentPlayability.NEEDS_TORRSERVER_INSTALL
        return TorrentPlayability.READY
    }

    suspend fun transformLink(link: ExtractorLink): ExtractorLink = withContext(Dispatchers.IO) {
        val rawUrl = link.url.trim()
        if (rawUrl.contains("/stream?") || rawUrl.contains("/stream/")) {
            AppLogger.i("DesktopTorrentEngine stream already transformed: $rawUrl")
            return@withContext link
        }

        val torboxApiKey = DesktopDataStore.getKey<String>(DesktopDataStore.PREF_TORBOX_API_KEY)
        val torboxAutoResolve = DesktopDataStore.getKey<Boolean>(DesktopDataStore.PREF_TORBOX_AUTO_RESOLVE) ?: true
        if (!torboxApiKey.isNullOrBlank() && torboxAutoResolve) {
            try {
                val resolvedTorbox = resolveViaTorbox(link, rawUrl, torboxApiKey)
                if (resolvedTorbox != null) {
                    AppLogger.i("DesktopTorrentEngine: Successfully resolved via Torbox cloud CDN: ${resolvedTorbox.url}")
                    return@withContext resolvedTorbox
                }
            } catch (e: Exception) {
                AppLogger.w("DesktopTorrentEngine: Torbox resolve failed, falling back to local TorrServer", e)
            }
        }

        if (!isP2pEnabled) {
            AppLogger.w("DesktopTorrentEngine: P2P Torrent Streaming is disabled in settings. Blocking playback.")
            throw IllegalStateException("P2P Torrent Streaming is disabled. Please enable it in Settings > Network or connect Torbox in Settings > Accounts to stream torrents.")
        }

        if (!binary.isInstalled()) {
            AppLogger.w("DesktopTorrentEngine: TorrServer engine is not installed on disk. Blocking playback.")
            throw IllegalStateException("TorrServer engine is required for local torrent playback. Please install it in Settings > Updates or connect Torbox in Settings > Accounts.")
        }

        val jobToAwait = transformMutex.withLock {
            if (inFlightTransformUrl == rawUrl) {
                cachedResolvedLink?.let {
                    AppLogger.i("DesktopTorrentEngine: Reusing cached resolved stream for $rawUrl")
                    return@withContext it
                }
                if (inFlightTransformJob?.isActive == true) {
                    AppLogger.i("DesktopTorrentEngine: Joining in-flight transform for $rawUrl")
                    return@withLock inFlightTransformJob
                }
            }

            inFlightTransformJob?.cancel()
            cachedResolvedLink = null
            val deferred = scope.async {
                doTransformLink(link, rawUrl)
            }
            inFlightTransformJob = deferred
            inFlightTransformUrl = rawUrl
            deferred
        }

        val resolved = jobToAwait!!.await()
        transformMutex.withLock {
            if (inFlightTransformUrl == rawUrl) {
                cachedResolvedLink = resolved
            }
        }
        resolved
    }

    private suspend fun doTransformLink(link: ExtractorLink, rawUrl: String): ExtractorLink {
        val magnetLink = when {
            rawUrl.contains("magnet:?", ignoreCase = true) -> {
                "magnet:?" + rawUrl.substringAfter("magnet:?")
            }
            rawUrl.length == 40 && rawUrl.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> {
                buildMagnetUri(rawUrl)
            }
            rawUrl.contains("xt=urn:btih:", ignoreCase = true) -> {
                "magnet:?xt=urn:btih:" + rawUrl.substringAfter("xt=urn:btih:")
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

        return newExtractorLink(
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
                    } else {
                        0
                    }

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
        inFlightTransformJob?.cancel()
        inFlightTransformJob = null
        inFlightTransformUrl = null
        cachedResolvedLink = null
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

    fun extractInfoHash(uri: String?): String? {
        if (uri.isNullOrBlank()) return null
        val lower = uri.lowercase()
        val match = Regex("urn:btih:([a-f0-9]{40}|[a-z2-7]{32})").find(lower)
        if (match != null) return match.groupValues[1]
        val trimmed = uri.trim()
        if ((trimmed.length == 40 || trimmed.length == 32) && trimmed.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }) {
            return trimmed.lowercase()
        }
        return null
    }

    private suspend fun resolveViaTorbox(link: ExtractorLink, rawUrl: String, apiKey: String): ExtractorLink? {
        val magnetLink = when {
            rawUrl.contains("magnet:?", ignoreCase = true) -> {
                "magnet:?" + rawUrl.substringAfter("magnet:?")
            }
            rawUrl.length == 40 && rawUrl.all { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' } -> {
                buildMagnetUri(rawUrl)
            }
            rawUrl.contains("xt=urn:btih:", ignoreCase = true) -> {
                "magnet:?xt=urn:btih:" + rawUrl.substringAfter("xt=urn:btih:")
            }
            else -> rawUrl
        }

        val hash = extractInfoHash(magnetLink) ?: return null

        AppLogger.i("DesktopTorrentEngine: Checking Torbox cache for hash: $hash")
        val cachedMap = TorboxApiClient.checkCached(apiKey, listOf(hash))
        val isCached = cachedMap.containsKey(hash) || cachedMap.keys.any { it.equals(hash, ignoreCase = true) }

        if (!isCached) {
            AppLogger.i("DesktopTorrentEngine: Hash $hash is not cached on Torbox. Falling back to TorrServer.")
            return null
        }

        AppLogger.i("DesktopTorrentEngine: Hash $hash is CACHED on Torbox! Creating cloud torrent reference...")
        val torrentId = TorboxApiClient.createTorrent(apiKey, magnetLink)
            ?: run {
                AppLogger.w("DesktopTorrentEngine: Torbox createTorrent returned null for $hash")
                return null
            }

        AppLogger.i("DesktopTorrentEngine: Torbox torrent ID: $torrentId. Inspecting files...")
        val torrentData = TorboxApiClient.getTorrent(apiKey, torrentId)
        val files = torrentData?.files.orEmpty()
        val bestFile = TorboxApiClient.selectBestVideoFile(files)
        val fileId = bestFile?.id

        AppLogger.i("DesktopTorrentEngine: Selected Torbox file: ${bestFile?.name} (id=$fileId). Requesting direct CDN stream link...")
        val directStreamUrl = TorboxApiClient.requestDownloadLink(apiKey, torrentId, fileId)
            ?: run {
                AppLogger.w("DesktopTorrentEngine: Torbox requestDownloadLink failed")
                return null
            }

        AppLogger.i("DesktopTorrentEngine: Torbox direct CDN stream URL resolved successfully!")
        val sizeStr = bestFile?.size?.takeIf { it > 0L }?.let { bytes ->
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            if (gb >= 1.0) {
                String.format(java.util.Locale.US, "%.1f GB", gb)
            } else {
                String.format(java.util.Locale.US, "%.0f MB", bytes.toDouble() / (1024.0 * 1024.0))
            }
        }
        val sizeTag = if (sizeStr != null) " [$sizeStr]" else ""
        return newExtractorLink(
            source = "Torbox",
            name = "[Torbox] " + (bestFile?.name?.ifBlank { null } ?: link.name) + sizeTag,
            url = directStreamUrl,
            type = ExtractorLinkType.VIDEO,
        ) {
            this.referer = ""
        }
    }
}
