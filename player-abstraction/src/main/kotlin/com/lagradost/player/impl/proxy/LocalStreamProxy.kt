package com.lagradost.player.impl.proxy

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.request.httpMethod
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.Base64
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private val ProxyScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
    // Converting callbacks to coroutines is always a nightmare. If OkHttp hangs here, good luck debugging it.
    continuation.invokeOnCancellation {
        try {
            cancel()
        } catch (ex: Throwable) {
            com.lagradost.common.logging.AppLogger.w("Proxy:LocalStream", "Failed to cancel OkHttp call: ${ex.message}", ex)
        }
    }
    enqueue(object : Callback {
        override fun onResponse(call: Call, response: Response) {
            if (continuation.isCancelled) {
                response.body?.close()
                return
            }
            try {
                // Resume with onCancellation block to prevent leaks if cancelled during dispatch
                continuation.resume(response) {
                    response.body?.close()
                }
            } catch (e: Exception) {
                com.lagradost.common.logging.AppLogger.e("Proxy:LocalStream", "Error resuming coroutine onResponse: ${e.message}", e)
                response.body?.close()
            }
        }
        override fun onFailure(call: Call, e: IOException) {
            if (continuation.isCancelled) return
            try {
                continuation.resumeWithException(e)
            } catch (ignored: Exception) {
                com.lagradost.common.logging.AppLogger.e("Proxy:LocalStream", "Error resuming coroutine onFailure: ${ignored.message}", ignored)
            }
        }
    })
}

object LocalStreamProxy {
    var tracksListener: ProxyTracksListener? = LocalStreamProxyState
    var clearanceUaProvider: ((host: String) -> String?)? = null
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null
    var port: Int = 0
        private set

    data class MpdCacheEntry(val content: String, val timestamp: Long)

    data class ProxySession(
        val headers: Map<String, String>,
        val masterCache: java.util.concurrent.ConcurrentHashMap<String, kotlinx.coroutines.Deferred<ByteArray>> = java.util.concurrent.ConcurrentHashMap(),
        val mpdCache: java.util.concurrent.ConcurrentHashMap<String, MpdCacheEntry> = java.util.concurrent.ConcurrentHashMap(),
        val interceptor: okhttp3.Interceptor? = null,
        val originalUrl: String? = null,
    )

    // Fast in-memory raw and cleaned init segment cache (10 minutes TTL)
    data class InitCacheEntry(val data: ByteArray, val timestamp: Long)
    private val initSegmentCache = java.util.concurrent.ConcurrentHashMap<String, InitCacheEntry>()
    private val rawInitSegmentCache = java.util.concurrent.ConcurrentHashMap<String, InitCacheEntry>()
    private const val INIT_CACHE_TTL_MS = 600_000L // 10 minutes

    // Decrypted media segment cache (60 seconds TTL, 150 entries max)
    data class SegmentCacheEntry(val data: ByteArray, val timestamp: Long)
    private val decryptedSegmentCache = java.util.concurrent.ConcurrentHashMap<String, SegmentCacheEntry>()
    private const val SEGMENT_CACHE_TTL_MS = 60_000L
    private const val MAX_SEGMENT_CACHE_SIZE = 150

    // Prefetching scope and tracker
    private val prefetchScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob(),
    )
    private val prefetchingUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun cleanupSegmentCache() {
        val now = System.currentTimeMillis()
        decryptedSegmentCache.entries.removeIf { now - it.value.timestamp > SEGMENT_CACHE_TTL_MS }
        if (decryptedSegmentCache.size > MAX_SEGMENT_CACHE_SIZE) {
            val oldest = decryptedSegmentCache.entries.sortedBy { it.value.timestamp }
                .take(decryptedSegmentCache.size - MAX_SEGMENT_CACHE_SIZE / 2)
            oldest.forEach { decryptedSegmentCache.remove(it.key) }
        }
    }

    // Capped LRU cache to prevent memory leaks from abandoned video sessions
    private val sessions = java.util.Collections.synchronizedMap(
        object : java.util.LinkedHashMap<String, ProxySession>(100, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, ProxySession>): Boolean {
                return size > 100
            }
        },
    )

    private val proxyClient by lazy {
        app.baseClient.newBuilder()
            .fastFallback(false)
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(5, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .callTimeout(0, java.util.concurrent.TimeUnit.SECONDS)
            .connectionPool(okhttp3.ConnectionPool(32, 60, java.util.concurrent.TimeUnit.SECONDS))
            .dispatcher(
                okhttp3.Dispatcher().apply {
                    maxRequests = 256
                    // Video chunking hits the same CDN host repeatedly, requiring paced parallel limits
                    maxRequestsPerHost = 32
                },
            )
            .apply {
                interceptors().removeAll {
                    it.javaClass.simpleName == "RateLimitInterceptor" ||
                        it.javaClass.simpleName == "DevNetworkInterceptor"
                }
            }
            .build()
    }

    private fun getClientForSession(session: ProxySession?): okhttp3.OkHttpClient {
        val interceptor = session?.interceptor ?: return proxyClient
        return proxyClient.newBuilder().addInterceptor(interceptor).build()
    }

    private fun resolveEffectiveUserAgent(
        sessionHost: String?,
        requestHost: String?,
        currentUa: String?,
    ): String {
        val clearanceUa = sessionHost?.let { clearanceUaProvider?.invoke(it) }
            ?: requestHost?.let { clearanceUaProvider?.invoke(it) }
        if (!clearanceUa.isNullOrBlank()) {
            return clearanceUa
        }
        val isMobileStub = currentUa != null && (currentUa.contains("Android", ignoreCase = true) || currentUa.contains("Pixel", ignoreCase = true))
        if (!currentUa.isNullOrBlank() && !isMobileStub) {
            return currentUa
        }
        return com.lagradost.cloudstream3.USER_AGENT
    }

    fun start() {
        if (server != null) return
        val s = embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                get("/proxy") {
                    handleRequest(call)
                }
                get("/proxy/{tail...}") {
                    handleRequest(call)
                }
                get("/dash/{id}/{name...}") {
                    DashProxyHandler.handleDashRequest(call, sessions, ::getClientForSession)
                }
                get("/image") {
                    ProxyImageHandler.handleImageRequest(call)
                }
                get("/trailer") {
                    ProxyTrailerHandler.handleTrailer(call)
                }
            }
        }
        s.engineConfig.requestReadTimeoutSeconds = 0
        s.engineConfig.responseWriteTimeoutSeconds = 120
        s.engineConfig.tcpKeepAlive = true
        val cpuCores = Runtime.getRuntime().availableProcessors()
        s.engineConfig.connectionGroupSize = 2
        s.engineConfig.workerGroupSize = cpuCores.coerceIn(4, 16)
        s.engineConfig.callGroupSize = cpuCores.coerceIn(4, 16)
        server = s.start(wait = false)

        port = kotlinx.coroutines.runBlocking {
            server?.engine?.resolvedConnectors()?.firstOrNull()?.port ?: 0
        }
        AppLogger.i("Proxy:LocalStream", "LocalStreamProxy started on port $port")
    }

    fun clearMediaCaches() {
        decryptedSegmentCache.clear()
        initSegmentCache.clear()
        rawInitSegmentCache.clear()
        DashProxyHandler.clear()
    }

    fun stop() {
        server?.stop(1000, 2000)
        server = null
        sessions.clear()
        clearMediaCaches()
        initSegmentCache.clear()
    }

    fun registerSession(headers: Map<String, String>, interceptor: okhttp3.Interceptor? = null, originalUrl: String? = null): String {
        val sessionId = UUID.randomUUID().toString()
        sessions[sessionId] = ProxySession(headers = headers, interceptor = interceptor, originalUrl = originalUrl)

        // Clear previous session tracks to prevent ghost subtitles from showing in the UI for the new stream
        LocalStreamProxyState.reset()

        return sessionId
    }

    fun hasInterceptor(sessionId: String): Boolean {
        return sessions[sessionId]?.interceptor != null
    }

    fun buildProxyUrl(sessionId: String, url: String, action: String? = null, clearKey: String? = null): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        var proxy = "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedUrl"
        if (action != null) proxy += "&action=$action"
        if (clearKey != null) proxy += "&ck=$clearKey"
        return proxy
    }

    fun buildDashProxyUrl(sessionId: String, url: String, clearKey: String? = null): String =
        buildProxyUrl(sessionId, url, action = "dash", clearKey = clearKey)

    fun isDashLive(address: String): Boolean {
        val id = address.substringAfter("/dash/", "").substringBefore("/")
        return DashProxyHandler.isDashLive(address)
    }

    fun buildImageUrl(url: String): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        return ProxyImageHandler.buildImageUrl(port, url)
    }

    fun prefetchM3u8(sessionId: String, url: String) {
        val session = sessions[sessionId] ?: return
        if (session.masterCache.containsKey(url)) return

        val deferred = ProxyScope.async(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val requestBuilder = okhttp3.Request.Builder().url(url).cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                val mergedHeaders = session.headers.toMutableMap()
                val keysToRemove = mergedHeaders.keys.filter {
                    it.equals("Host", ignoreCase = true)
                }
                keysToRemove.forEach { mergedHeaders.remove(it) }
                val sessionHost = try {
                    val base = session.originalUrl
                        ?: session.headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
                        ?: session.headers.entries.firstOrNull { it.key.equals("Origin", ignoreCase = true) }?.value
                    if (base != null) java.net.URI(base).host?.lowercase() else null
                } catch (e: Exception) {
                    AppLogger.d("Proxy:LocalStream", "Session host parse note: ${e.message}")
                    null
                }
                val requestHost = try { java.net.URI(url).host?.lowercase() } catch (e: Exception) { AppLogger.d("Proxy:LocalStream", "URL host parse note: ${e.message}"); null }
                val rawUa = mergedHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                val effectiveUa = resolveEffectiveUserAgent(sessionHost, requestHost, rawUa)

                mergedHeaders.keys.filter { it.equals("User-Agent", ignoreCase = true) }.toList().forEach { mergedHeaders.remove(it) }
                mergedHeaders["User-Agent"] = effectiveUa
                mergedHeaders.forEach { (k, v) -> requestBuilder.safeHeader(k, v) }

                var response: okhttp3.Response? = null
                var lastError: Exception? = null
                val client = getClientForSession(session)
                for (attempt in 1..4) {
                    try {
                        response = client.newCall(requestBuilder.build()).await()
                        if (response.isSuccessful || response.code in 400..499) break
                    } catch (e: Exception) {
                        lastError = e
                    }
                    if (attempt < 4) {
                        response?.body?.close()
                        kotlinx.coroutines.delay(200L * attempt)
                    }
                }

                if (response == null || !response.isSuccessful) {
                    val code = response?.code
                    response?.body?.close()
                    throw Exception("Prefetch HTTP failed. Code: $code Error: ${lastError?.message}")
                }

                val m3u8Content = response.body?.source()?.readUtf8() ?: ""
                val finalUrl = response.request.url.toString()
                response.body?.close()

                val rewritten = HlsRewriter.rewriteM3u8(m3u8Content, finalUrl, sessionId, tracksListener)
                rewritten.toByteArray(Charsets.UTF_8)
            } catch (e: Exception) {
                AppLogger.e("Proxy:LocalStream", "Prefetch failed for $url", e)
                ByteArray(0)
            }
        }
        // Immediately store in masterCache to prevent race conditions when MPV requests it right away.
        // If after analysis we find it is not a master playlist, we remove it.
        session.masterCache[url] = deferred
        ProxyScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val bytes = deferred.await()
                val content = String(bytes, Charsets.UTF_8)
                val isLiveSlidingPlaylist = !content.contains("#EXT-X-STREAM-INF") &&
                    !content.contains("#EXT-X-ENDLIST") &&
                    !content.contains("#EXT-X-PLAYLIST-TYPE:VOD")
                if (isLiveSlidingPlaylist) {
                    // Media playlist — do NOT keep in masterCache, let handleRequest fetch fresh on subsequent refreshes
                    session.masterCache.remove(url)
                } else {
                    prefetchMasterRenditions(sessionId, session, content, url)
                }
            } catch (e: Exception) {
                session.masterCache.remove(url)
                com.lagradost.common.logging.AppLogger.w("Proxy:LocalStream", "Error analyzing prefetch payload for caching: ${e.message}", e)
            }
        }
    }

    private fun prefetchMasterRenditions(
        sessionId: String,
        session: ProxySession,
        masterContent: String,
        masterUrl: String,
    ) {
        val lines = masterContent.lines()
        val variantUrls = mutableListOf<String>()
        val audioUrls = mutableListOf<String>()
        var nextIsVariant = false

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXT-X-STREAM-INF")) {
                nextIsVariant = true
            } else if (line.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                val uriMatch = Regex("""URI="([^"]+)"""").find(line)
                if (uriMatch != null) {
                    val rawUri = uriMatch.groupValues[1]
                    val resolved = HlsRewriter.resolveUrl(masterUrl, rawUri)
                    if (resolved.startsWith("http://", ignoreCase = true) || resolved.startsWith("https://", ignoreCase = true)) {
                        audioUrls.add(resolved)
                    }
                }
            } else if (nextIsVariant && !line.startsWith("#")) {
                val resolved = HlsRewriter.resolveUrl(masterUrl, line)
                if (resolved.startsWith("http://", ignoreCase = true) || resolved.startsWith("https://", ignoreCase = true)) {
                    variantUrls.add(resolved)
                }
                nextIsVariant = false
            }
        }

        // Prefetch primary video variants and default audio renditions concurrently
        val targets = (variantUrls.take(2) + audioUrls.take(2)).distinct()
        for (targetUrl in targets) {
            if (session.masterCache.containsKey(targetUrl)) continue
            val variantDeferred = ProxyScope.async(Dispatchers.IO) {
                try {
                    val requestBuilder = okhttp3.Request.Builder().url(targetUrl).cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
                    val mergedHeaders = session.headers.toMutableMap()
                    mergedHeaders.remove("Host")
                    mergedHeaders.remove("Range")
                    mergedHeaders.remove("Accept-Encoding")
                    val targetHost = try { java.net.URI(targetUrl).host?.lowercase() } catch (e: Exception) { AppLogger.d("Proxy:LocalStream", "Target host parse note: ${e.message}"); null }
                    val rawUa = mergedHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
                    val effectiveUa = resolveEffectiveUserAgent(null, targetHost, rawUa)
                    mergedHeaders.keys.filter { it.equals("User-Agent", ignoreCase = true) }.toList().forEach { mergedHeaders.remove(it) }
                    mergedHeaders["User-Agent"] = effectiveUa
                    val client = getClientForSession(session)
                    mergedHeaders.forEach { (k, v) -> requestBuilder.safeHeader(k, v) }
                    val response = client.newCall(requestBuilder.build()).await()
                    if (!response.isSuccessful) {
                        response.body?.close()
                        return@async ByteArray(0)
                    }
                    val body = response.body?.source()?.readUtf8() ?: ""
                    val finalTargetUrl = response.request.url.toString()
                    response.body?.close()
                    val rewritten = HlsRewriter.rewriteM3u8(body, finalTargetUrl, sessionId, tracksListener)
                    rewritten.toByteArray(Charsets.UTF_8)
                } catch (e: Exception) {
                    AppLogger.d("Proxy:LocalStream", "Variant rendition prefetch note for $targetUrl: ${e.message}")
                    ByteArray(0)
                }
            }
            session.masterCache[targetUrl] = variantDeferred
        }
    }

    private suspend fun handleRequest(call: io.ktor.server.application.ApplicationCall) {
        try {
            val sessionId = call.request.queryParameters["s"]
            val encodedUrl = call.request.queryParameters["u"]
            val isFlatVtt = call.request.queryParameters["flatvtt"] == "true"
            val action = call.request.queryParameters["action"]
            val rep = call.request.queryParameters["rep"]
            val clearKey = call.request.queryParameters["ck"]
            val kid = call.request.queryParameters["kid"]
            val k = call.request.queryParameters["k"]
            val encodedInit = call.request.queryParameters["init"]
            val initUrl = encodedInit?.let {
                try {
                    String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8)
                } catch (e: Exception) {
                    AppLogger.d("Proxy:LocalStream", "Init URL decode note: ${e.message}")
                    null
                }
            }

            com.lagradost.common.logging.AppLogger.i("Proxy:LocalStream", "Action=$action, rep=$rep, hasCk=${clearKey != null}, hasKid=${kid != null}, hasK=${k != null}, encodedUrl=$encodedUrl")

            if (sessionId == null || encodedUrl == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            val url = String(Base64.getUrlDecoder().decode(encodedUrl), Charsets.UTF_8)
            val session = sessions[sessionId]

            if (session == null) {
                call.respond(HttpStatusCode.NotFound)
                return
            }

            // Check if we have an exact cache hit for the exact URL (useful for m3u8 requests)
            val cachedDeferred = session.masterCache[url]
            if (cachedDeferred != null) {
                val bytes = cachedDeferred.await()
                if (bytes.isNotEmpty()) {
                    call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                    call.respondBytes(bytes, status = HttpStatusCode.OK)
                    return
                }
            }

            if (action == "init_decrypt" || action == "init") {
                val cached = initSegmentCache[url]
                if (cached != null && System.currentTimeMillis() - cached.timestamp < INIT_CACHE_TTL_MS) {
                    call.response.header("Content-Type", "video/mp4")
                    call.response.header("Accept-Ranges", "bytes")
                    call.respondBytes(cached.data, status = HttpStatusCode.OK)
                    return
                }
            }

            if (action == "decrypt") {
                val cacheKey = "${url}_${kid ?: ""}_${k ?: ""}"
                val cached = decryptedSegmentCache[cacheKey]
                if (cached != null && System.currentTimeMillis() - cached.timestamp < SEGMENT_CACHE_TTL_MS) {
                    call.response.header("Content-Type", "video/mp4")
                    call.response.header("Accept-Ranges", "bytes")
                    call.respondBytes(cached.data, status = HttpStatusCode.OK)
                    return
                }
            }

            if (action == "dash" && rep != null) {
                val cachedEntry = session.mpdCache[url]
                if (cachedEntry != null) {
                    val isLive = cachedEntry.content.contains("type=\"dynamic\"") || cachedEntry.content.contains("type='dynamic'")
                    val age = System.currentTimeMillis() - cachedEntry.timestamp
                    if (!isLive || age < 1000L) {
                        val m3u8 = NativeMpdConverter().convertMediaPlaylist(cachedEntry.content, rep, port, sessionId, url, clearKey)
                        call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                        call.respondBytes(m3u8.toByteArray(Charsets.UTF_8), status = HttpStatusCode.OK)
                        return
                    }
                }
            }

            // --- LOCAL FILE INTERCEPTION ---
            // If the URL is actually a local file path (e.g. C:\... or /home/...), read and serve it directly, bypassing OkHttp.
            val isLocalFilePath = url.length >= 2 && (
                (url[1] == ':' && url[2] == '\\') || // Windows: C:\...
                    (url[1] == ':' && url[2] == '/') || // Windows: C:/...
                    url.startsWith("/") // Unix: /...
                )
            if (isLocalFilePath) {
                val file = java.io.File(url)
                if (!file.exists() || !file.isFile) {
                    call.respond(HttpStatusCode.NotFound)
                    return
                }
                val ext = file.extension.lowercase()
                val contentType = when (ext) {
                    "srt" -> io.ktor.http.ContentType.parse("text/plain; charset=utf-8")
                    "vtt" -> io.ktor.http.ContentType.parse("text/vtt; charset=utf-8")
                    "ass", "ssa" -> io.ktor.http.ContentType.parse("text/plain; charset=utf-8")
                    else -> io.ktor.http.ContentType.Application.OctetStream
                }
                val bytes = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    file.readBytes()
                }
                call.respondBytes(bytes, contentType, HttpStatusCode.OK)
                return
            }
            // --------------------------------

            val mergedHeaders = session.headers.toMutableMap()

            val keysToRemove = mergedHeaders.keys.filter {
                it.equals("Accept-Encoding", ignoreCase = true) ||
                    it.equals("Host", ignoreCase = true)
            }
            keysToRemove.forEach { mergedHeaders.remove(it) }

            val parsedUri = try {
                java.net.URI(url)
            } catch (e: Exception) {
                AppLogger.d("Proxy:LocalStream", "Parsed URI note: ${e.message}")
                null
            }
            // Cross-origin header isolation: strip domain-scoped credentials when hitting third-party CDNs
            val sessionHost = try {
                val base = session.originalUrl
                    ?: session.headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
                    ?: session.headers.entries.firstOrNull { it.key.equals("Origin", ignoreCase = true) }?.value
                if (base != null) java.net.URI(base).host?.lowercase() else null
            } catch (e: Exception) {
                AppLogger.d("Proxy:LocalStream", "Request session host note: ${e.message}")
                null
            }
            val requestHost = parsedUri?.host?.lowercase()
            val isCrossOrigin = sessionHost != null && requestHost != null &&
                !requestHost.equals(sessionHost, ignoreCase = true) &&
                !requestHost.endsWith(".$sessionHost", ignoreCase = true) &&
                !sessionHost.endsWith(".$requestHost", ignoreCase = true)

            if (isCrossOrigin) {
                val sessionOrigin = session.headers.entries.firstOrNull { it.key.equals("Origin", ignoreCase = true) }?.value
                    ?: if (sessionHost != null) "https://$sessionHost" else null
                if (sessionOrigin != null) {
                    mergedHeaders["Origin"] = sessionOrigin
                }
                val sessionReferer = session.headers.entries.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value
                    ?: if (sessionHost != null) "https://$sessionHost/" else null
                if (sessionReferer != null) {
                    mergedHeaders["Referer"] = sessionReferer
                }
            }

            val path = (parsedUri?.path ?: url.substringBefore('?')).lowercase()
            val query = (parsedUri?.query ?: url.substringAfter('?', "")).lowercase()
            val isM3u8Url = path.endsWith(".m3u8") ||
                path.endsWith(".m3u") ||
                path.contains(".m3u8") ||
                path.contains("/playlist") ||
                path.contains("/manifest") ||
                query.contains(".m3u8") ||
                query.contains("format=m3u8") ||
                query.contains("type=m3u8")

            if (!isM3u8Url) {
                // Request identity encoding to prevent CDNs from compressing binary video/audio segments,
                // avoiding edge decompression mismatches and preserving exact Content-Length for FFmpeg.
                mergedHeaders["Accept-Encoding"] = "identity"
                call.request.headers["Range"]?.let {
                    mergedHeaders["Range"] = it
                }
            } else {
                mergedHeaders.remove("Accept-Encoding")
                mergedHeaders.keys.filter { it.equals("Range", ignoreCase = true) }.forEach { mergedHeaders.remove(it) }
            }

            val currentUa = mergedHeaders.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value

            val effectiveUa = resolveEffectiveUserAgent(sessionHost, requestHost, currentUa)
            mergedHeaders.keys.filter { it.equals("User-Agent", ignoreCase = true) }.toList().forEach { mergedHeaders.remove(it) }
            mergedHeaders["User-Agent"] = effectiveUa
            val requestBuilder = okhttp3.Request.Builder().url(url).cacheControl(okhttp3.CacheControl.FORCE_NETWORK)
            mergedHeaders.forEach { (k, v) -> requestBuilder.safeHeader(k, v) }

            // Use completely async OkHttp fetch with internal retries to prevent ThreadPool exhaustion
            // and handle CDN connection drops smoothly without breaking FFmpeg.
            var response: okhttp3.Response? = null
            var lastError: Exception? = null
            val client = getClientForSession(session)
            val maxAttempts = if (isM3u8Url) 1 else 3
            for (attempt in 1..maxAttempts) {
                try {
                    response = client.newCall(requestBuilder.build()).await()
                    if (response.isSuccessful || response.code in 400..499) break
                } catch (e: Exception) {
                    lastError = e
                }
                if (attempt < maxAttempts) {
                    response?.body?.close()
                    kotlinx.coroutines.delay(100L * attempt)
                }
            }

            // If the request had a Range header and failed with 403, 400, 416, or 405 (method/range not allowed),
            // retry the request WITHOUT the Range header and let the proxy skip the bytes manually.
            // NOTE: Do NOT include 500 here — CDNs that return 500 do so regardless of Range headers,
            // so retrying without Range just wastes 3-4 extra seconds on a permanently dead segment.
            if (response != null && !response.isSuccessful && mergedHeaders.containsKey("Range")) {
                val code = response.code
                if (code == 403 || code == 400 || code == 416 || code == 405) {
                    AppLogger.w("Proxy:LocalStream", "Range request failed with HTTP $code, retrying WITHOUT Range header for URL: $url")
                    response.body?.close()
                    val retryHeaders = mergedHeaders.toMutableMap()
                    retryHeaders.remove("Range")
                    if (code == 403) {
                        retryHeaders.keys.filter { key ->
                            key.equals("Cookie", ignoreCase = true) ||
                                key.equals("Authorization", ignoreCase = true)
                        }.forEach { retryHeaders.remove(it) }
                    }
                    val retryBuilder = okhttp3.Request.Builder().url(url)
                    retryHeaders.forEach { (k, v) -> retryBuilder.safeHeader(k, v) }

                    var retryResponse: okhttp3.Response? = null
                    for (attempt in 1..3) {
                        try {
                            retryResponse = client.newCall(retryBuilder.build()).await()
                            if (retryResponse.isSuccessful || retryResponse.code in 400..499) break
                        } catch (e: Exception) {
                            lastError = e
                        }
                        if (attempt < 3) {
                            retryResponse?.body?.close()
                            kotlinx.coroutines.delay(200L * attempt)
                        }
                    }
                    if (retryResponse != null && retryResponse.isSuccessful) {
                        response = retryResponse
                    } else {
                        retryResponse?.body?.close()
                    }
                }
            }

            if (response == null) {
                AppLogger.e("Proxy:LocalStream", "Proxy Request Failed after $maxAttempts attempts! URL: $url Error: ${lastError?.message}")
                val status = if (lastError is java.net.SocketTimeoutException) HttpStatusCode.GatewayTimeout else HttpStatusCode.BadGateway
                call.respond(status)
                return
            }

            if (!response.isSuccessful) {
                AppLogger.e("Proxy:LocalStream", "Proxy Request Failed! Code: ${response.code} URL: $url")
                response.body?.close()
                call.respond(HttpStatusCode.fromValue(response.code))
                return
            }

            if (action == "init_decrypt") {
                val rawBytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body?.source()?.readByteArray() ?: ByteArray(0)
                }
                response.body?.close()
                rawInitSegmentCache[url] = InitCacheEntry(rawBytes, System.currentTimeMillis())
                val cleaned = StreamDecryptor.cleanInitSegment(rawBytes)
                initSegmentCache[url] = InitCacheEntry(cleaned, System.currentTimeMillis())
                call.response.header("Content-Type", "video/mp4")
                call.response.header("Accept-Ranges", "bytes")
                call.respondBytes(cleaned, status = HttpStatusCode.OK)
                return
            }

            if (action == "decrypt") {
                val mediaBytes = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body?.source()?.readByteArray() ?: ByteArray(0)
                }
                response.body?.close()

                var rawInitBytes: ByteArray? = null
                if (initUrl != null) {
                    rawInitBytes = rawInitSegmentCache[initUrl]?.data
                    if (rawInitBytes == null) {
                        try {
                            val initReqBuilder = okhttp3.Request.Builder().url(initUrl)
                            mergedHeaders.forEach { (k, v) -> initReqBuilder.header(k, v) }
                            val initResp = client.newCall(initReqBuilder.build()).await()
                            if (initResp.isSuccessful) {
                                val fetchedRaw = initResp.body?.source()?.readByteArray()
                                initResp.body?.close()
                                if (fetchedRaw != null && fetchedRaw.isNotEmpty()) {
                                    rawInitBytes = fetchedRaw
                                    rawInitSegmentCache[initUrl] = InitCacheEntry(fetchedRaw, System.currentTimeMillis())
                                    val cleaned = StreamDecryptor.cleanInitSegment(fetchedRaw)
                                    initSegmentCache[initUrl] = InitCacheEntry(cleaned, System.currentTimeMillis())
                                }
                            } else {
                                initResp.body?.close()
                            }
                        } catch (e: Exception) {
                            AppLogger.w("Proxy:LocalStream", "Failed to fetch init segment: ${e.message}")
                        }
                    }
                }

                val decrypted = try {
                    StreamDecryptor.decryptMediaSegment(
                        mediaSegment = mediaBytes,
                        keyIdHex = kid ?: "",
                        keyHex = k ?: "",
                        initSegment = rawInitBytes,
                    )
                } catch (e: Exception) {
                    AppLogger.e("Proxy:LocalStream", "Decryption failed for $url: ${e.message}", e)
                    mediaBytes
                }

                val cacheKey = "${url}_${kid ?: ""}_${k ?: ""}"
                decryptedSegmentCache[cacheKey] = SegmentCacheEntry(decrypted, System.currentTimeMillis())
                cleanupSegmentCache()

                prefetchNextSegments(
                    currentSegmentUrl = url,
                    initUrl = initUrl,
                    rawInitBytes = rawInitBytes,
                    kid = kid ?: "",
                    k = k ?: "",
                    session = session,
                    headers = mergedHeaders,
                )

                call.response.header("Content-Type", "video/mp4")
                call.response.header("Accept-Ranges", "bytes")
                call.respondBytes(decrypted, status = HttpStatusCode.OK)
                return
            }

            if (action == "dash") {
                val mpdContent = withContext(kotlinx.coroutines.Dispatchers.IO) {
                    response.body?.source()?.readUtf8() ?: ""
                }
                response.body?.close()
                session.mpdCache[url] = MpdCacheEntry(mpdContent, System.currentTimeMillis())
                val m3u8 = if (rep == null) {
                    NativeMpdConverter().convertMasterPlaylist(mpdContent, port, sessionId, url, clearKey, tracksListener)
                } else {
                    NativeMpdConverter().convertMediaPlaylist(mpdContent, rep, port, sessionId, url, clearKey)
                }
                call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                call.respondBytes(m3u8.toByteArray(Charsets.UTF_8), status = HttpStatusCode.OK)
                return
            }

            val upstreamContentType = response.header("Content-Type") ?: ""
            // CDNs disguise MPEG-TS/AAC segments as .jpg, .js, etc. to evade hotlink protection.
            // FFmpeg's HLS demuxer checks the MIME type and rejects non-media types like
            // 'application/javascript' or 'image/jpeg' even if the binary content is valid TS.
            // Normalize any non-media, non-m3u8 type to application/octet-stream so FFmpeg
            // always tries to decode the actual binary content.
            val rawContentType = upstreamContentType.ifBlank { "application/octet-stream" }
            val isNonMediaType = rawContentType.contains("javascript", ignoreCase = true) ||
                rawContentType.contains("text/", ignoreCase = true) ||
                (rawContentType.contains("image/", ignoreCase = true) && !rawContentType.contains("mpegurl", ignoreCase = true))
            val contentTypeStr = if (isNonMediaType) "application/octet-stream" else rawContentType
            val isM3u8 = withContext(kotlinx.coroutines.Dispatchers.IO) {
                try {
                    val s = response.body?.source()
                    if (s != null && s.request(1)) {
                        val peekLen = minOf(64L, s.buffer.size)
                        val peeked = s.peek().readUtf8(peekLen).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                        peeked.startsWith("#EXTM3U", ignoreCase = true) || peeked.startsWith("#EXT-X-", ignoreCase = true)
                    } else {
                        false
                    }
                } catch (e: Exception) {
                    false
                }
            }

            if (isM3u8) {
                try {
                    val m3u8Content = withContext(kotlinx.coroutines.Dispatchers.IO) {
                        response.body?.source()?.readUtf8() ?: ""
                    }

                    val finalUrl = response.request.url.toString()

                    if (isFlatVtt) {
                        if (m3u8Content.contains("#EXT-X-KEY") || m3u8Content.contains("#EXT-X-MAP")) {
                            // Edge Case 1: Encrypted or fMP4 subtitles cannot be flattened to text!
                            // Fallback to normal M3U8 proxying for these.
                        } else {
                            call.response.header("Content-Type", "text/vtt")
                            call.respondBytesWriter(status = HttpStatusCode.OK) {
                                writeFully("WEBVTT\n\n".toByteArray(Charsets.UTF_8))
                                val lines = m3u8Content.lines()
                                val vttUrls = lines.filter { !it.startsWith("#") && it.trim().isNotEmpty() }.map { HlsRewriter.resolveUrl(finalUrl, it.trim()) }
                                val client = getClientForSession(session)

                                val headersMap = session.headers.toMutableMap()
                                headersMap.remove("Accept-Encoding")
                                headersMap.remove("Host")
                                if (headersMap.keys.none { it.equals("User-Agent", ignoreCase = true) }) {
                                    headersMap["User-Agent"] = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
                                }

                                val chunkedBatches = vttUrls.chunked(16)
                                for (batch in chunkedBatches) {
                                    val downloadedSegments: List<String> = coroutineScope {
                                        val deferreds = batch.map { segUrl ->
                                            async(Dispatchers.IO) {
                                                val reqBuilder = okhttp3.Request.Builder().url(segUrl)
                                                headersMap.forEach { (k, v) -> reqBuilder.safeHeader(k, v) }
                                                var content = ""
                                                for (attempt in 1..2) {
                                                    try {
                                                        val segResp = client.newCall(reqBuilder.build()).await()
                                                        val segText = segResp.body?.source()?.readUtf8() ?: ""
                                                        segResp.body?.close()
                                                        if (segResp.isSuccessful && segText.isNotBlank()) {
                                                            content = segText
                                                            break
                                                        }
                                                    } catch (e: Exception) {
                                                        AppLogger.d("Proxy:LocalStream", "VTT segment fetch attempt $attempt failed for $segUrl: ${e.message}")
                                                    }
                                                }
                                                content
                                            }
                                        }
                                        deferreds.map { it.await() }
                                    }

                                    for (result in downloadedSegments) {
                                        if (result.isNotBlank()) {
                                            val segmentLines = result.trimStart('\uFEFF').lines()
                                            for (line in segmentLines) {
                                                val trimmed = line.trim()
                                                if (trimmed == "WEBVTT" || trimmed.startsWith("X-TIMESTAMP-MAP") || trimmed.startsWith("NOTE")) continue
                                                writeFully((line + "\n").toByteArray(Charsets.UTF_8))
                                            }
                                            writeFully("\n".toByteArray(Charsets.UTF_8))
                                        }
                                    }
                                    flush()
                                }
                            }
                            return
                        }
                    }

                    val rewritten = HlsRewriter.rewriteM3u8(m3u8Content, finalUrl, sessionId, tracksListener)

                    val bytes = rewritten.toByteArray(Charsets.UTF_8)

                    // Cache it for subsequent FFmpeg probes to prevent network hit,
                    // BUT ONLY if it's a MASTER playlist. Media playlists MUST NOT be cached,
                    // otherwise mpv will never discover new segments for live streams.
                    if (m3u8Content.contains("#EXT-X-STREAM-INF")) {
                        session.masterCache[url] = ProxyScope.async { bytes }
                        prefetchMasterRenditions(sessionId, session, m3u8Content, finalUrl)
                    }

                    call.response.header("Content-Type", "application/vnd.apple.mpegurl")
                    call.respondBytes(bytes, status = HttpStatusCode.OK)
                } finally {
                    withContext(kotlinx.coroutines.Dispatchers.IO) {
                        try {
                            response.body?.close()
                        } catch (e: Exception) {
                            AppLogger.d("Proxy:LocalStream", "Failed to close M3U8 response body: ${e.message}")
                        }
                    }
                }
                return
            } else {
                var finalCode = response.code
                var skipBytes = 0L
                val origRange = call.request.headers["Range"]
                if (origRange != null && origRange.startsWith("bytes=", ignoreCase = true) && response.code == 200) {
                    skipBytes = origRange.substringAfter("=").substringBefore("-").toLongOrNull() ?: 0L
                    if (skipBytes > 0) {
                        finalCode = 206
                    }
                }

                var detectedTsOffset = 0
                if (skipBytes == 0L) {
                    try {
                        val peekSource = response.body?.source()?.peek()
                        if (peekSource != null) {
                            val peekBuf = ByteArray(32768)
                            val n = peekSource.read(peekBuf)
                            if (n > 188 * 3 && peekBuf[0] != 0x47.toByte()) {
                                val offset = StreamTransferEngine.findTsSyncOffset(peekBuf, n)
                                if (offset > 0) {
                                    detectedTsOffset = offset
                                    AppLogger.i("Proxy:LocalStream", "Stripping $detectedTsOffset bytes of prepended header from segment: $url")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.d("Proxy:LocalStream", "TS sync offset probe note: ${e.message}")
                    }
                }

                val cl = response.body?.contentLength() ?: -1L
                val contentLengthParam = if (skipBytes == 0L && cl >= 0) {
                    if (detectedTsOffset > 0) cl - detectedTsOffset else cl
                } else {
                    null
                }

                val parsedContentType = try {
                    ContentType.parse(contentTypeStr)
                } catch (e: Exception) {
                    ContentType.Application.OctetStream
                }

                if (skipBytes > 0) {
                    val endPart = origRange?.substringAfter("-")
                    val endPos = if (endPart.isNullOrBlank()) (if (cl > 0) cl - 1 else "") else endPart
                    call.response.header("Content-Range", "bytes $skipBytes-$endPos/${if (cl > 0) cl else "*"}")
                } else {
                    response.header("Content-Range")?.let { call.response.header("Content-Range", it) }
                }

                call.response.header("Accept-Ranges", response.header("Accept-Ranges") ?: "bytes")

                // Stream directly to Ktor to avoid GC allocation churn from array copies.

                StreamTransferEngine.transferStream(
                    call = call,
                    response = response,
                    parsedContentType = parsedContentType,
                    finalCode = finalCode,
                    contentLengthParam = contentLengthParam,
                    skipBytes = skipBytes,
                    detectedTsOffset = detectedTsOffset,
                    url = url,
                    requestBuilder = requestBuilder,
                    mergedHeaders = mergedHeaders,
                    client = client,
                    tracksListener = tracksListener,
                )
                return
            }
        } catch (e: Exception) {
            val isChannelClosed = e is io.ktor.utils.io.ClosedWriteChannelException ||
                e.cause is io.ktor.utils.io.ClosedWriteChannelException ||
                (e is java.io.IOException && e.message?.contains("Broken pipe", ignoreCase = true) == true)
            if (isChannelClosed) {
                AppLogger.d("Proxy:LocalStream", "Client disconnected from stream (normal player seek or exit)")
            } else {
                AppLogger.e("Proxy:LocalStream", "LocalStreamProxy error: ${e.message}", e)
                try {
                    call.respond(HttpStatusCode.InternalServerError)
                } catch (ex: Exception) {
                    com.lagradost.common.logging.AppLogger.e("Proxy:LocalStream", "Failed to send 500 status to client", ex)
                }
            }
        }
    }

    fun resolveUrl(base: String, uri: String): String = HlsRewriter.resolveUrl(base, uri)

    private fun prefetchNextSegments(
        currentSegmentUrl: String,
        initUrl: String?,
        rawInitBytes: ByteArray?,
        kid: String,
        k: String,
        session: ProxySession?,
        headers: Map<String, String>,
        count: Int = 3,
    ) {
        val regex = Regex("""([_\-\./])(\d+)(\.m4[sv]|\.mp4|\.ts)""")
        val match = regex.find(currentSegmentUrl) ?: return
        val prefix = match.groupValues[1]
        val numStr = match.groupValues[2]
        val suffix = match.groupValues[3]
        val currentNum = numStr.toIntOrNull() ?: return
        val numLength = numStr.length

        for (i in 1..count) {
            val nextNum = currentNum + i
            val paddedNextNum = if (numStr.startsWith("0")) nextNum.toString().padStart(numLength, '0') else nextNum.toString()
            val nextUrl = currentSegmentUrl.replace(
                match.value,
                "$prefix$paddedNextNum$suffix",
            )
            val nextCacheKey = "${nextUrl}_${kid}_$k"

            if (decryptedSegmentCache.containsKey(nextCacheKey) || !prefetchingUrls.add(nextCacheKey)) {
                continue
            }

            prefetchScope.launch {
                try {
                    val client = getClientForSession(session)
                    val reqBuilder = okhttp3.Request.Builder().url(nextUrl)
                    headers.forEach { (hKey, hVal) -> reqBuilder.safeHeader(hKey, hVal) }
                    val resp = client.newCall(reqBuilder.build()).await()
                    if (resp.isSuccessful) {
                        val bytes = resp.body?.source()?.readByteArray()
                        resp.body?.close()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val decrypted = StreamDecryptor.decryptMediaSegment(
                                mediaSegment = bytes,
                                keyIdHex = kid,
                                keyHex = k,
                                initSegment = rawInitBytes,
                            )
                            decryptedSegmentCache[nextCacheKey] = SegmentCacheEntry(decrypted, System.currentTimeMillis())
                            cleanupSegmentCache()
                        }
                    } else {
                        resp.body?.close()
                    }
                } catch (e: Exception) {
                    AppLogger.d("Proxy:LocalStream", "Segment prefetch failed for $nextUrl: ${e.message}")
                } finally {
                    prefetchingUrls.remove(nextCacheKey)
                }
            }
        }
    }
}
