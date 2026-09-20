package com.lagradost.cloudstream3.network

import com.lagradost.common.logging.AppLogger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

data class HostClearance(
    val host: String,
    val cookies: List<Cookie>,
    val userAgent: String,
    val isTlsBound: Boolean = false,
    val isFailed: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
)

class CloudflareKiller(private val cookieJar: CookieJar? = null) : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        // Track hosts currently being resolved to avoid duplicate solver launches
        private val resolvingHosts = ConcurrentHashMap.newKeySet<String>()

        // Unified per-host clearance and failure state
        private val hostStates = ConcurrentHashMap<String, HostClearance>()

        var globalCookieJar: CookieJar? = null

        @Volatile var lastChallengedHost: String? = null
        @Volatile var lastChallengedUrl: String? = null
        @Volatile var lastChallengeTimestamp: Long = 0L

        /**
         * Resolves the active [HostClearance] for a target URL by checking exact host match
         * or parent domain suffixes if domain-scoped cookies match RFC 6265 criteria.
         */
        fun getClearance(url: HttpUrl): HostClearance? {
            val host = url.host.lowercase()
            hostStates[host]?.let { if (!it.isFailed) return it }

            val parts = host.split(".")
            if (parts.size > 2) {
                for (i in 1 until parts.size - 1) {
                    val parent = parts.subList(i, parts.size).joinToString(".")
                    val parentClearance = hostStates[parent]
                    if (parentClearance != null && !parentClearance.isFailed) {
                        if (parentClearance.cookies.any { it.matches(url) }) {
                            return parentClearance
                        }
                    }
                }
            }
            return null
        }

        fun getClearance(host: String): HostClearance? {
            val clean = host.lowercase().trim()
            hostStates[clean]?.let { if (!it.isFailed) return it }
            return hostStates.entries.firstOrNull { (k, v) ->
                !v.isFailed && (clean.endsWith(".$k") || k.endsWith(".$clean"))
            }?.value
        }

        fun getSavedCookies(host: String): Map<String, String> {
            val clearance = getClearance(host)
            return clearance?.cookies?.associate { it.name to it.value } ?: emptyMap()
        }

        fun getSavedUserAgent(host: String): String? {
            return getClearance(host)?.userAgent
        }

        fun isTlsBound(host: String): Boolean {
            val clean = host.lowercase().trim()
            return hostStates[clean]?.isTlsBound == true ||
                hostStates.entries.any { (k, v) -> (clean == k || clean.endsWith(".$k")) && v.isTlsBound }
        }

        fun getApexDomain(host: String): String {
            val clean = host.lowercase().trim().removePrefix("www.")
            val parts = clean.split(".")
            if (parts.size <= 2) return clean
            return if (parts[parts.size - 2].length <= 3 && parts.last().length <= 3 && parts.size >= 3) {
                parts.takeLast(3).joinToString(".")
            } else {
                parts.takeLast(2).joinToString(".")
            }
        }

        private fun markSingleFailed(target: String) {
            val existing = hostStates[target]
            if (existing != null) {
                hostStates[target] = existing.copy(isFailed = true)
            } else {
                hostStates[target] = HostClearance(
                    host = target,
                    cookies = emptyList(),
                    userAgent = "",
                    isFailed = true,
                )
            }
        }

        fun markFailed(host: String) {
            val clean = host.lowercase().trim()
            val apex = getApexDomain(clean)
            markSingleFailed(clean)
            if (apex.isNotBlank() && apex != clean) {
                markSingleFailed(apex)
            }
        }

        fun isFailed(host: String): Boolean {
            val clean = host.lowercase().trim()
            val apex = getApexDomain(clean)
            if (hostStates[clean]?.isFailed == true || (apex.isNotBlank() && hostStates[apex]?.isFailed == true)) return true
            return hostStates.entries.any { (k, v) ->
                v.isFailed && (clean == k || clean.endsWith(".$k") || k == apex || (apex.isNotBlank() && apex.endsWith(".$k")))
            }
        }

        fun unmarkFailed(host: String) {
            val clean = host.lowercase().trim()
            val apex = getApexDomain(clean)
            hostStates[clean]?.let { if (it.isFailed) hostStates[clean] = it.copy(isFailed = false) }
            if (apex.isNotBlank()) {
                hostStates[apex]?.let { if (it.isFailed) hostStates[apex] = it.copy(isFailed = false) }
            }
            hostStates.entries.filter { (k, v) ->
                (clean == k || clean.endsWith(".$k") || k.endsWith(".$clean") || (apex.isNotBlank() && (k == apex || apex.endsWith(".$k")))) && v.isFailed
            }.forEach { (k, v) -> hostStates[k] = v.copy(isFailed = false) }
        }

        fun isImageAsset(url: HttpUrl): Boolean {
            val path = url.encodedPath.lowercase()
            return path.endsWith(".webp") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".svg") ||
                path.endsWith(".ico") || path.endsWith(".avif")
        }

        fun isStaticAsset(url: HttpUrl): Boolean {
            val path = url.encodedPath.lowercase()
            return isImageAsset(url) || path.endsWith(".mp4") ||
                path.endsWith(".m3u8") || path.endsWith(".ts") || path.endsWith(".mpd")
        }

        fun parseCookieMap(cookie: String): Map<String, String> {
            return cookie.split(";")
                .mapNotNull { pair ->
                    val split = pair.split("=", limit = 2)
                    val key = split.getOrNull(0)?.trim().orEmpty()
                    val value = split.getOrNull(1)?.trim().orEmpty()
                    if (key.isNotEmpty() && value.isNotEmpty()) key to value else null
                }
                .toMap()
        }

        fun saveClearance(
            host: String,
            cookies: Map<String, String>? = null,
            userAgent: String,
            url: HttpUrl? = null,
            okCookies: List<Cookie>? = null,
            isTlsBound: Boolean = false,
        ) {
            val cleanHost = host.lowercase().trim()
            val finalCookies = okCookies ?: cookies?.mapNotNull { (k, v) ->
                try {
                    Cookie.Builder().name(k).value(v).domain(cleanHost).path("/").build()
                } catch (_: Exception) { null }
            } ?: emptyList()

            AppLogger.i("$TAG: Registered Cloudflare clearance for $cleanHost (${finalCookies.size} cookies, UA=$userAgent)")
            hostStates[cleanHost] = HostClearance(
                host = cleanHost,
                cookies = finalCookies,
                userAgent = userAgent,
                isTlsBound = isTlsBound,
                isFailed = false,
                timestamp = System.currentTimeMillis(),
            )

            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            jar?.saveCookies(finalCookies)
        }

        fun getAllStoredCookies(): Map<String, List<Cookie>> {
            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            val jarCookies = jar?.getAllStoredCookies()?.toMutableMap() ?: mutableMapOf()

            for ((h, clearance) in hostStates) {
                if (!jarCookies.containsKey(h) && clearance.cookies.isNotEmpty()) {
                    jarCookies[h] = clearance.cookies
                }
            }
            return jarCookies
        }

        fun clearClearanceForDomain(domain: String) {
            val clean = domain.lowercase().trimStart('.')
            hostStates.remove(clean)
            hostStates.keys.filter { it.endsWith(".$clean") }.forEach { hostStates.remove(it) }

            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            jar?.removeCookiesForDomain(clean)
        }

        fun clearAllClearance() {
            hostStates.clear()
            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            jar?.removeAll()
        }
    }

    init {
        if (cookieJar != null) {
            globalCookieJar = cookieJar
        }
    }

    fun getCookieHeaders(url: String): Headers {
        val httpUrl = try { url.toHttpUrlOrNull() } catch (_: Exception) { null }
        val builder = Headers.Builder()

        if (httpUrl != null) {
            val clearance = getClearance(httpUrl)
            if (clearance != null && clearance.cookies.isNotEmpty()) {
                if (clearance.userAgent.isNotBlank()) {
                    builder.add("user-agent", clearance.userAgent)
                }
                val matching = clearance.cookies.filter { it.matches(httpUrl) }
                if (matching.isNotEmpty()) {
                    builder.add("cookie", matching.joinToString("; ") { "${it.name}=${it.value}" })
                }
            }
        }
        return builder.build()
    }

    /**
     * Intercept method that does NOT use runBlocking on the hot path.
     * Only the rare Cloudflare bypass triggers runBlocking to avoid
     * exhausting OkHttp's thread pool when plugins fire many parallel requests.
     */
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host.lowercase()
        val isStatic = isStaticAsset(request.url)


        // If an active browser proxy exists for host, ensure not marked as failed
        if (com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.hasActiveProxy(host)) {
            unmarkFailed(host)
        }

        // If host has permanently failed, proceed normally
        if (isFailed(host)) {
            return chain.proceed(request)
        }

        // If host has an active browser proxy, route requests through it (binary for static assets, text for HTML/JSON)
        if (com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.hasActiveProxy(host)) {
            val proxyResponse = fetchViaBrowserProxy(request, isBinary = isStatic)
            if (proxyResponse != null) return proxyResponse
            AppLogger.w("$TAG: Browser proxy failed for $host, falling through to normal path.")
        }

        var response: Response? = null
        var usedClearance: HostClearance? = null

        val clearance = getClearance(request.url)
        if (clearance != null && clearance.cookies.isNotEmpty()) {
            usedClearance = clearance
            response = proceed(chain, request, clearance.cookies, clearance.userAgent)
        } else {
            response = chain.proceed(request)
        }

        val serverHeader = response.header("Server") ?: ""
        val cfMitigated = response.header("cf-mitigated") ?: ""
        val isCloudflareServer = CLOUDFLARE_SERVERS.any { serverHeader.contains(it, ignoreCase = true) }

        val isCloudflareChallenge = !isStatic && response.code in ERROR_CODES && isCloudflareServer && run {
            if (cfMitigated.equals("challenge", ignoreCase = true)) return@run true
            val bodyPreview = try { response.peekBody(4096).string() } catch (_: Exception) { "" }
            val trimmed = bodyPreview.trim()
            if (trimmed.startsWith("{") || trimmed.startsWith("[")) return@run false

            bodyPreview.contains("Just a moment...") ||
                bodyPreview.contains("challenge-platform") ||
                bodyPreview.contains("cf-chl-") ||
                bodyPreview.contains("_cf_chl_opt") ||
                bodyPreview.contains("turnstile", ignoreCase = true)
        }

        if (isCloudflareChallenge) {
            AppLogger.w("$TAG: Cloudflare challenge (HTTP ${response.code}) detected for $host.")
            lastChallengedHost = host
            lastChallengedUrl = request.url.toString()
            lastChallengeTimestamp = System.currentTimeMillis()

            if (usedClearance != null && !isTlsBound(host)) {
                AppLogger.w("$TAG: Expired or rejected Cloudflare credentials for $host. Removing from cache.")
                hostStates.remove(host)
            }

            val isBypassAllowed = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(
                com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS,
            ) ?: false

            if (!isBypassAllowed) {
                AppLogger.w("$TAG: Cloudflare challenge detected for $host, but browser solver is disabled by user.")
                try {
                    com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showWarning(
                        "This source requires Cloudflare clearance. Enable 'Experimental Cloudflare Solver' in Settings > Network. It uses a secure, isolated sandbox browser with zero access to your personal data, passwords, or accounts.",
                        durationMs = 7000L,
                    )
                } catch (_: Throwable) {}
                markFailed(host)
                return response
            }

            if (!isFailed(host)) {
                val solved = synchronized(CloudflareKiller::class.java) {
                    val current = getClearance(request.url)
                    if (current != null && current.cookies.isNotEmpty()) {
                        return@synchronized true
                    }

                    AppLogger.i("$TAG: Opening manual verification window for $host...")
                    try {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showInfo(
                            "Opening isolated sandbox browser to resolve Cloudflare...",
                            durationMs = 3000L,
                        )
                    } catch (_: Throwable) {}
                    kotlinx.coroutines.runBlocking {
                        com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.launchManualClearance(
                            targetUrl = request.url.toString(),
                            hostName = host,
                        )
                    }
                }

                val solvedClearance = getClearance(request.url)
                if (solved && solvedClearance != null && solvedClearance.cookies.isNotEmpty()) {
                    AppLogger.i("$TAG: Successfully acquired clearance for $host. Retrying request.")
                    try {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                            "Cloudflare clearance resolved successfully.",
                            durationMs = 3000L,
                        )
                    } catch (_: Throwable) {}
                    response.close()

                    // Promote the solver session to an active background fetch proxy immediately,
                    // marking the host as TLS-bound so subsequent requests (search, episode lists, details)
                    // route through genuine browser TLS without repeat challenges or opening new windows.
                    hostStates[host] = solvedClearance.copy(isTlsBound = true)
                    val proxyActivated = kotlinx.coroutines.runBlocking {
                        com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.activateFetchProxy(host)
                    }
                    // Prioritize background browser fetch proxy if active, ensuring genuine HTTP responses
                    // and uncorrupted JSON payloads without DOM formatting artifacts.
                    if (proxyActivated) {
                        val proxyResponse = fetchViaBrowserProxy(request)
                        if (proxyResponse != null && proxyResponse.code !in ERROR_CODES) return proxyResponse
                    }

                    val isApiEndpoint = request.url.encodedPath.contains("/api", ignoreCase = true) ||
                        request.url.encodedPath.endsWith(".json", ignoreCase = true)

                    if (!isApiEndpoint) {
                        val cachedPage = com.lagradost.cloudstream3.desktop.network.SettledPageCache.consume(request.url.toString())
                        if (cachedPage != null) {
                            AppLogger.i("$TAG: Serving settled HTML from cache after clearance for ${request.url}")
                            val bodyBytes = cachedPage.html.toByteArray(Charsets.UTF_8)
                            return Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(200)
                                .message("OK")
                                .header("content-type", "text/html; charset=utf-8")
                                .header("content-length", bodyBytes.size.toString())
                                .body(bodyBytes.toResponseBody("text/html; charset=utf-8".toMediaTypeOrNull()))
                                .build()
                        }
                    }

                    val retryResponse = proceed(chain, request, solvedClearance.cookies, solvedClearance.userAgent)
                    if (retryResponse.code !in ERROR_CODES) {
                        return retryResponse
                    }

                    AppLogger.w("$TAG: OkHttp retry rejected (HTTP ${retryResponse.code}). TLS fingerprint mismatch confirmed for $host.")

                    if (!proxyActivated) {
                        val activated = kotlinx.coroutines.runBlocking {
                            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.activateFetchProxy(host)
                        }
                        if (activated) {
                            val proxyResponse = fetchViaBrowserProxy(request, isBinary = isStatic)
                            if (proxyResponse != null && proxyResponse.code !in ERROR_CODES) {
                                retryResponse.close()
                                return proxyResponse
                            }
                        }
                    }

                    AppLogger.e("$TAG: Browser proxy also failed for $host. Marking as failed.")
                    markFailed(host)
                    return retryResponse
                } else {
                    AppLogger.w("$TAG: Manual clearance was closed or failed for $host.")
                    try {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showWarning(
                            "Cloudflare verification window was closed before completion.",
                            durationMs = 4000L,
                        )
                    } catch (_: Throwable) {}
                    markFailed(host)
                }
            }
        }

        return response
    }

    private fun proceed(chain: Interceptor.Chain, request: Request, cookies: List<Cookie>, userAgent: String?): Response {
        val builder = request.newBuilder()
        if (!userAgent.isNullOrBlank()) {
            builder.header("user-agent", userAgent)

            val chromeVersionMatch = Regex("Chrome/([0-9]+)").find(userAgent)
            val edgeVersionMatch = Regex("Edg/([0-9]+)").find(userAgent)
            val version = edgeVersionMatch?.groupValues?.get(1) ?: chromeVersionMatch?.groupValues?.get(1) ?: "133"

            val brand = if (userAgent.contains("Edg/")) {
                "\"Not(A:Brand\";v=\"99\", \"Microsoft Edge\";v=\"$version\", \"Chromium\";v=\"$version\""
            } else {
                "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"$version\", \"Chromium\";v=\"$version\""
            }

            val platform = when {
                System.getProperty("os.name", "").lowercase().contains("mac") -> "\"macOS\""
                System.getProperty("os.name", "").lowercase().contains("linux") -> "\"Linux\""
                else -> "\"Windows\""
            }
            builder.header("sec-ch-ua", brand)
            builder.header("sec-ch-ua-mobile", "?0")
            builder.header("sec-ch-ua-platform", platform)

            val isStatic = isStaticAsset(request.url)
            val site = "same-origin"
            val mode = if (isStatic) "no-cors" else "cors"
            val dest = if (isStatic) "image" else "empty"

            builder.header("sec-fetch-site", site)
            builder.header("sec-fetch-mode", mode)
            builder.header("sec-fetch-dest", dest)
            builder.header("accept-language", "en-US,en;q=0.9")
            if (request.header("referer") == null) {
                builder.header("referer", "${request.url.scheme}://${request.url.host}/")
            }
        }

        val matchingCookies = cookies.filter { it.matches(request.url) }
        val cookieHeader = request.header("cookie")
        val existingMap = cookieHeader?.let { parseCookieMap(it) } ?: emptyMap()
        val mergedMap = existingMap.toMutableMap()
        matchingCookies.forEach { mergedMap[it.name] = it.value }

        if (mergedMap.isNotEmpty()) {
            builder.header("cookie", mergedMap.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }

        val finalRequest = builder.build()
        AppLogger.d("$TAG: Retrying request to ${finalRequest.url} with headers: ${finalRequest.headers}")

        val response = chain.proceed(finalRequest)
        AppLogger.d("$TAG: Retry response code: ${response.code}, CipherSuite: ${response.handshake?.cipherSuite}")

        return response
    }

    private suspend fun bypassCloudflare(chain: Interceptor.Chain, request: Request): Response? {
        val url = request.url.toString()
        val host = request.url.host
        AppLogger.i("$TAG: Loading Native Edge/Chrome to solve Cloudflare for $host")

        var solved = false
        val result = WebViewResolver(
            Regex(".^"), // never exit early based on URL
            additionalUrls = listOf(Regex(".")), // match all sub-requests to poll cookies
            userAgent = null,
            useOkhttp = false,
        ).resolveUsingWebView(url) {
            false
        }

        val resolvedRequest = result.first ?: return null

        val cookieHeader = resolvedRequest.header("cookie")
        val userAgentHeader = resolvedRequest.header("user-agent")

        if (cookieHeader != null && cookieHeader.contains("cf_clearance")) {
            val parsed = parseCookieMap(cookieHeader)
            val cookiesList = parsed.mapNotNull { (k, v) ->
                try {
                    Cookie.Builder().name(k).value(v).domain(host).path("/").build()
                } catch (_: Exception) { null }
            }
            saveClearance(host, parsed, userAgentHeader ?: "", okCookies = cookiesList)
            solved = true
        }

        if (solved) {
            AppLogger.i("$TAG: Cloudflare bypassed successfully for $host")
            val clearance = getClearance(host)
            return proceed(chain, request, clearance?.cookies ?: emptyList(), clearance?.userAgent)
        }

        return null
    }

    /**
     * Route a request through the browser's Chromium network stack via CDP.
     * Constructs a synthetic OkHttp Response so the caller is unaware of the proxy.
     */
    private fun fetchViaBrowserProxy(request: Request, isBinary: Boolean = false): Response? {
        val result = kotlinx.coroutines.runBlocking {
            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.fetchViaProxy(
                url = request.url.toString(),
                method = request.method,
                headers = buildMap {
                    for (name in request.headers.names()) {
                        put(name, request.header(name) ?: "")
                    }
                },
                body = request.body?.let { body ->
                    val buffer = okio.Buffer()
                    body.writeTo(buffer)
                    buffer.readUtf8()
                },
                isBinary = isBinary,
            )
        }

        if (result == null || result.statusCode == 0) {
            AppLogger.e("$TAG: fetchViaBrowserProxy returned null/error for ${request.url}")
            return null
        }

        AppLogger.i("$TAG: Browser proxy returned HTTP ${result.statusCode} for ${request.url}")
        val contentTypeString = result.contentType ?: "application/octet-stream"
        val bodyBytes = result.bodyBytes ?: result.body.toByteArray(Charsets.UTF_8)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(result.statusCode)
            .message(if (result.statusCode in 200..299) "OK" else "Proxied")
            .header("content-type", contentTypeString)
            .header("content-length", bodyBytes.size.toString())
            .body(bodyBytes.toResponseBody(contentTypeString.toMediaTypeOrNull()))
            .build()
    }
}
