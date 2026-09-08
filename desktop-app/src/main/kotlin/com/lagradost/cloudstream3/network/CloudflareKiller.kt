package com.lagradost.cloudstream3.network

import com.lagradost.common.logging.AppLogger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class CloudflareKiller(private val cookieJar: CookieJar? = null) : Interceptor {
    companion object {
        const val TAG = "CloudflareKiller"
        private val ERROR_CODES = listOf(403, 503)
        private val CLOUDFLARE_SERVERS = listOf("cloudflare-nginx", "cloudflare")

        // Track hosts currently being resolved to avoid duplicate Playwright launches
        private val resolvingHosts = ConcurrentHashMap.newKeySet<String>()

        // Track hosts where bypass has already failed — don't retry during this session
        private val failedHosts = ConcurrentHashMap.newKeySet<String>()

        // Hosts confirmed to require browser-level TLS (cf_clearance bound to TLS fingerprint)
        val tlsBoundHosts: MutableSet<String> = ConcurrentHashMap.newKeySet()

        val savedCookies: MutableMap<String, Map<String, String>> = ConcurrentHashMap()
        val savedUserAgents: MutableMap<String, String> = ConcurrentHashMap()
        var globalCookieJar: CookieJar? = null

        @Volatile var lastChallengedHost: String? = null
        @Volatile var lastChallengedUrl: String? = null
        @Volatile var lastChallengeTimestamp: Long = 0L

        /** Resolve apex/root domain for subdomain inheritance (e.g. cdn.example.com -> example.com). */
        fun getApexDomain(host: String): String {
            val cleanHost = host.lowercase().trim()
            val parts = cleanHost.split(".")
            if (parts.size <= 2) return cleanHost
            val twoPartTlds = setOf("co.uk", "org.uk", "com.au", "net.au", "co.jp", "com.br", "co.in", "net.in", "org.in")
            val lastTwo = "${parts[parts.size - 2]}.${parts.last()}"
            return if (twoPartTlds.contains(lastTwo) && parts.size > 3) {
                "${parts[parts.size - 3]}.$lastTwo"
            } else {
                lastTwo
            }
        }

        fun getSavedCookies(host: String): Map<String, String> {
            return savedCookies[host] ?: savedCookies[getApexDomain(host)] ?: emptyMap()
        }

        fun getSavedUserAgent(host: String): String? {
            return savedUserAgents[host] ?: savedUserAgents[getApexDomain(host)]
        }

        fun isTlsBound(host: String): Boolean {
            return tlsBoundHosts.contains(host) || tlsBoundHosts.contains(getApexDomain(host))
        }

        fun isImageAsset(url: okhttp3.HttpUrl): Boolean {
            val path = url.encodedPath.lowercase()
            return path.endsWith(".webp") || path.endsWith(".jpg") || path.endsWith(".jpeg") ||
                path.endsWith(".png") || path.endsWith(".gif") || path.endsWith(".svg") ||
                path.endsWith(".ico") || path.endsWith(".avif")
        }

        fun isStaticAsset(url: okhttp3.HttpUrl): Boolean {
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
            cookies: Map<String, String>,
            userAgent: String,
            url: okhttp3.HttpUrl? = null,
            okCookies: List<Cookie>? = null,
        ) {
            val apex = getApexDomain(host)
            AppLogger.i("$TAG: Manually registered Cloudflare clearance for $host (apex=$apex, cookies=${cookies.keys})")
            savedCookies[host] = cookies
            savedCookies[apex] = cookies
            savedUserAgents[host] = userAgent
            savedUserAgents[apex] = userAgent

            globalCookieJar?.let { jar ->
                if (okCookies != null && jar is com.lagradost.cloudstream3.desktop.network.DesktopCookieJar) {
                    jar.saveCookies(okCookies)
                } else {
                    val targetUrl = url ?: okhttp3.HttpUrl.Builder().scheme("https").host(apex).build()
                    val parsedCookies = cookies.mapNotNull { (k, v) ->
                        try {
                            Cookie.Builder().domain(apex).name(k).value(v).path("/").build()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    jar.saveFromResponse(targetUrl, parsedCookies)
                }
            }
        }

        fun getAllStoredCookies(): Map<String, List<Cookie>> {
            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            val jarCookies = jar?.getAllStoredCookies()?.toMutableMap() ?: mutableMapOf()

            for ((host, cookies) in savedCookies) {
                val apex = getApexDomain(host)
                if (!jarCookies.containsKey(apex) && !jarCookies.containsKey(host)) {
                    val mockList = cookies.mapNotNull { (k, v) ->
                        try {
                            Cookie.Builder().name(k).value(v).domain(apex).path("/").build()
                        } catch (_: Exception) {
                            null
                        }
                    }
                    if (mockList.isNotEmpty()) {
                        jarCookies[apex] = mockList
                    }
                }
            }
            return jarCookies
        }

        fun clearClearanceForDomain(domain: String) {
            val clean = domain.lowercase().trimStart('.')
            val apex = getApexDomain(clean)
            savedCookies.remove(clean)
            savedCookies.remove(apex)
            savedUserAgents.remove(clean)
            savedUserAgents.remove(apex)
            tlsBoundHosts.remove(clean)
            tlsBoundHosts.remove(apex)
            failedHosts.remove(clean)
            failedHosts.remove(apex)

            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            jar?.removeCookiesForDomain(clean)
            if (apex != clean) {
                jar?.removeCookiesForDomain(apex)
            }
        }

        fun clearAllClearance() {
            savedCookies.clear()
            savedUserAgents.clear()
            tlsBoundHosts.clear()
            failedHosts.clear()
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
        val host = try {
            URI(url).host
        } catch (e: Exception) {
            null
        }
        val builder = Headers.Builder()

        host?.let { h ->
            val cookieMap = savedCookies[h] ?: emptyMap()
            val userAgent = savedUserAgents[h]

            if (userAgent != null) {
                builder.add("user-agent", userAgent)
            }
            if (cookieMap.isNotEmpty()) {
                builder.add("cookie", cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" })
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
        val host = request.url.host
        val apex = getApexDomain(host)

        val isStatic = isStaticAsset(request.url)

        // If settled HTML is cached for this URL, serve it immediately without touching network
        val cachedPage = com.lagradost.cloudstream3.desktop.network.SettledPageCache.get(request.url.toString())
        if (cachedPage != null) {
            AppLogger.i("$TAG: Serving settled HTML from cache for ${request.url}")
            val bodyBytes = cachedPage.html.toByteArray(Charsets.UTF_8)
            val mediaType = "text/html; charset=utf-8".toMediaTypeOrNull()
            return Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("content-type", "text/html; charset=utf-8")
                .header("content-length", bodyBytes.size.toString())
                .body(bodyBytes.toResponseBody(mediaType))
                .build()
        }

        // If an active browser proxy exists for apex, apex domain is guaranteed alive
        if (com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.hasActiveProxy(apex)) {
            failedHosts.remove(apex)
        }

        // If this host or its apex already failed bypass, don't waste time — just proceed normally
        if (failedHosts.contains(host) || (!com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.hasActiveProxy(apex) && failedHosts.contains(apex))) {
            return chain.proceed(request)
        }

        // If this host (or its apex) requires browser-level TLS and proxy is active, route through it.
        // Images on TLS-bound hosts can be routed via binary browser proxy.
        // Video streaming chunks (.ts, .mp4, .m3u8) must NOT be routed through browser proxy.
        val isImage = isImageAsset(request.url)
        val isStream = isStatic && !isImage
        if (!isStream && isTlsBound(host) &&
            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.hasActiveProxy(host)
        ) {
            val proxyResponse = fetchViaBrowserProxy(request, isBinary = isImage)
            if (proxyResponse != null) return proxyResponse
            AppLogger.w("$TAG: Browser proxy failed for $host, falling through to normal path.")
        }

        var response: Response? = null
        var usedSavedCookie = false

        // Try with saved cookies first if we have them (checking host or apex)
        val currentCookies = getSavedCookies(host)
        val currentUa = getSavedUserAgent(host)
        if (currentCookies.isNotEmpty()) {
            usedSavedCookie = true
            response = proceed(chain, request, currentCookies, currentUa)
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
            // Ignore API JSON responses (e.g. GraphQL, REST API errors)
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

            // If we used a saved cookie and it STILL returned a challenge:
            // Only clear cookies if host is NOT TLS-bound (an OkHttp 403 on TLS-bound host is expected, not expired)
            if (usedSavedCookie && !isTlsBound(host)) {
                AppLogger.w("$TAG: Expired or rejected Cloudflare credentials for $host. Removing from cache.")
                savedCookies.remove(host)
                savedCookies.remove(apex)
                savedUserAgents.remove(host)
                savedUserAgents.remove(apex)
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
                failedHosts.add(host)
                return response
            }

            if (!failedHosts.contains(host) && !failedHosts.contains(apex)) {
                val solved = synchronized(CloudflareKiller::class.java) {
                    // Check if another concurrent thread already resolved it while we were waiting
                    if (getSavedCookies(host).isNotEmpty()) {
                        return@synchronized true
                    }

                    AppLogger.i("$TAG: Opening manual verification window for $host (apex=$apex)...")
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

                val solvedCookies = getSavedCookies(host)
                if (solved && solvedCookies.isNotEmpty()) {
                    AppLogger.i("$TAG: Successfully acquired clearance for $host. Retrying request.")
                    try {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                            "Cloudflare clearance resolved successfully.",
                            durationMs = 3000L,
                        )
                    } catch (_: Throwable) {}
                    response.close()

                    // If settled HTML is cached for this URL, serve it immediately without touching network
                    val cachedPage = com.lagradost.cloudstream3.desktop.network.SettledPageCache.get(request.url.toString())
                    if (cachedPage != null) {
                        AppLogger.i("$TAG: Serving settled HTML from cache after clearance for ${request.url}")
                        com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.closePendingSession()
                        val bodyBytes = cachedPage.html.toByteArray(Charsets.UTF_8)
                        val mediaType = "text/html; charset=utf-8".toMediaTypeOrNull()
                        return Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(200)
                            .message("OK")
                            .header("content-type", "text/html; charset=utf-8")
                            .header("content-length", bodyBytes.size.toString())
                            .body(bodyBytes.toResponseBody(mediaType))
                            .build()
                    }

                    val retryResponse = proceed(chain, request, solvedCookies, getSavedUserAgent(host))
                    if (retryResponse.code !in ERROR_CODES) {
                        // OkHttp retry succeeded — browser proxy not needed, close the pending session
                        com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.closePendingSession()
                        return retryResponse
                    }

                    // TLS fingerprint rejection confirmed — activate browser as fetch proxy
                    AppLogger.w("$TAG: OkHttp retry rejected (HTTP ${retryResponse.code}). TLS fingerprint mismatch confirmed for $host.")
                    retryResponse.close()
                    tlsBoundHosts.add(host)
                    tlsBoundHosts.add(apex)

                    val proxyActivated = kotlinx.coroutines.runBlocking {
                        com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.activateFetchProxy(host)
                    }

                    if (proxyActivated) {
                        val proxyResponse = fetchViaBrowserProxy(request)
                        if (proxyResponse != null && proxyResponse.code !in ERROR_CODES) return proxyResponse
                    }

                    AppLogger.e("$TAG: Browser proxy also failed for $host. Marking as failed.")
                    failedHosts.add(host)
                    if (host.equals(apex, ignoreCase = true)) {
                        failedHosts.add(apex)
                    }
                } else {
                    AppLogger.w("$TAG: Manual clearance was closed or failed for $host.")
                    try {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showWarning(
                            "Cloudflare verification window was closed before completion.",
                            durationMs = 4000L,
                        )
                    } catch (_: Throwable) {}
                    failedHosts.add(host)
                    if (host.equals(apex, ignoreCase = true)) {
                        failedHosts.add(apex)
                    }
                }
            }
        }

        // Return the response directly. Do NOT close it, so the caller or plugin can handle it.
        return response
    }

    private fun proceed(chain: Interceptor.Chain, request: Request, cookies: Map<String, String>, userAgent: String?): Response {
        val builder = request.newBuilder()
        if (userAgent != null) {
            builder.header("user-agent", userAgent)

            // Cloudflare binds cf_clearance to the exact Sec-Ch-Ua headers. If they are missing, it throws a 403.
            val chromeVersionMatch = Regex("Chrome/([0-9]+)").find(userAgent)
            val edgeVersionMatch = Regex("Edg/([0-9]+)").find(userAgent)
            val version = edgeVersionMatch?.groupValues?.get(1) ?: chromeVersionMatch?.groupValues?.get(1) ?: "133"

            val brand = if (userAgent.contains("Edg/")) {
                "\"Not(A:Brand\";v=\"99\", \"Microsoft Edge\";v=\"$version\", \"Chromium\";v=\"$version\""
            } else {
                "\"Not(A:Brand\";v=\"99\", \"Google Chrome\";v=\"$version\", \"Chromium\";v=\"$version\""
            }

            builder.header("sec-ch-ua", brand)
            builder.header("sec-ch-ua-mobile", "?0")
            builder.header("sec-ch-ua-platform", "\"Windows\"")

            val host = request.url.host
            val apex = getApexDomain(host)
            val isStatic = isStaticAsset(request.url)

            val site = if (host.equals(apex, ignoreCase = true)) "same-origin" else "same-site"
            val mode = if (isStatic) "no-cors" else "cors"
            val dest = if (isStatic) "image" else "empty"

            // Add WAF-bypassing headers (Sec-Fetch and Referer)
            builder.header("sec-fetch-site", site)
            builder.header("sec-fetch-mode", mode)
            builder.header("sec-fetch-dest", dest)
            builder.header("accept-language", "en-US,en;q=0.9")
            builder.header("referer", "https://$apex/")
        }

        val existingCookies = request.header("cookie")?.let { parseCookieMap(it) } ?: emptyMap()
        val finalCookies = existingCookies + cookies
        if (finalCookies.isNotEmpty()) {
            builder.header("cookie", finalCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
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
            // In PlaywrightResolverImpl we don't have access to the cookies inside this callback
            // easily without blocking, but PlaywrightResolverImpl itself polls for cf_clearance.
            // So we just return false here so PlaywrightResolver doesn't exit early,
            // and instead relies on its internal cf_clearance check!
            false
        }

        val resolvedRequest = result.first ?: return null

        val cookieHeader = resolvedRequest.header("cookie")
        val userAgentHeader = resolvedRequest.header("user-agent")

        if (cookieHeader != null && cookieHeader.contains("cf_clearance")) {
            savedCookies[host] = parseCookieMap(cookieHeader)
            if (userAgentHeader != null) {
                savedUserAgents[host] = userAgentHeader
            }
            solved = true
        }

        if (solved) {
            AppLogger.i("$TAG: Cloudflare bypassed successfully for $host")
            return proceed(chain, request, savedCookies[host] ?: emptyMap(), savedUserAgents[host])
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
        val mediaType = (result.contentType ?: "application/octet-stream").toMediaTypeOrNull()
        val bodyBytes = result.bodyBytes ?: result.body.toByteArray(Charsets.UTF_8)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(result.statusCode)
            .message(if (result.statusCode in 200..299) "OK" else "Proxied")
            .body(bodyBytes.toResponseBody(mediaType))
            .build()
    }
}
