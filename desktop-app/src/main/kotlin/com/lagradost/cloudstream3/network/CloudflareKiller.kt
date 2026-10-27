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
        private val inFlightSolvers = ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<Boolean>>()

        // Unified per-host clearance and failure state
        private val hostStates = ConcurrentHashMap<String, HostClearance>()

        var globalCookieJar: CookieJar? = null
        val isWorker: Boolean get() = System.getProperty("cloudstream.sandbox.worker") == "true"

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
            return getClearance(host)
        }

        fun getClearance(host: String): HostClearance? {
            val clean = host.lowercase().trim()
            hostStates[clean]?.let { if (!it.isFailed) return it }
            val suffixMatch = hostStates.entries.firstOrNull { (k, v) ->
                !v.isFailed && (clean.endsWith(".$k") || k.endsWith(".$clean"))
            }?.value
            if (suffixMatch != null) return suffixMatch

            // Fallback to secure persistent DPAPI vault
            val vaultCookie = com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.getClearanceCookie(clean)
            if (vaultCookie != null) {
                val allVault = com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.getAllUnexpired()
                    .filter { clean == it.domain.trimStart('.') || clean.endsWith(".${it.domain.trimStart('.')}") }
                val restored = HostClearance(
                    host = clean,
                    cookies = allVault.ifEmpty { listOf(vaultCookie) },
                    userAgent = com.lagradost.cloudstream3.USER_AGENT,
                    isTlsBound = false,
                    isFailed = false,
                    timestamp = System.currentTimeMillis(),
                )
                hostStates[clean] = restored
                return restored
            }
            return null
        }

        /**
         * Resolves the most appropriate clearance for an image or CDN asset by checking:
         * 1. Exact host or parent domain match
         * 2. Referer host if provided in the request
         * 3. Substring/token correlation with any actively registered clearance host
         * Returns a Pair of the matched [HostClearance] and the inferred origin host.
         */
        fun getClearanceForAsset(url: HttpUrl, refererUrl: String? = null): Pair<HostClearance, String>? {
            // 1. Direct match
            getClearance(url)?.let { return it to it.host }

            // 2. Check Referer host
            if (!refererUrl.isNullOrBlank()) {
                val refHttp = try {
                    refererUrl.toHttpUrlOrNull()
                } catch (_: Exception) {
                    null
                }
                if (refHttp != null) {
                    getClearance(refHttp)?.let { return it to refHttp.host }
                }
            }

            // 3. Token correlation (e.g. "i.provider.ru" matches "provider.pw")
            val hostParts = url.host.lowercase().split(".").filter { it.length >= 4 }
            for (token in hostParts) {
                val match = hostStates.entries.firstOrNull { (k, v) ->
                    !v.isFailed && k.contains(token)
                }
                if (match != null) {
                    return match.value to match.key
                }
            }

            // 4. DPAPI Vault correlation
            val allVault = com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.getAllUnexpired()
            for (token in hostParts) {
                val vaultMatch = allVault.firstOrNull { it.domain.contains(token) }
                if (vaultMatch != null) {
                    val cleanDomain = vaultMatch.domain.trimStart('.')
                    val restored = getClearance(cleanDomain)
                    if (restored != null) {
                        return restored to cleanDomain
                    }
                }
            }

            return null
        }

        fun getSavedCookies(host: String): Map<String, String> {
            val clearance = getClearance(host)
            return clearance?.cookies?.associate { it.name to it.value } ?: emptyMap()
        }

        fun getSavedUserAgent(host: String): String? {
            return getClearance(host)?.userAgent
        }

        const val PREF_TLS_BOUND_HOSTS = "pref_tls_bound_hosts"

        fun isTlsBound(host: String): Boolean {
            val clean = host.lowercase().trim()
            if (hostStates[clean]?.isTlsBound == true) return true
            if (hostStates.entries.any { (k, v) -> (clean == k || clean.endsWith(".$k") || k.endsWith(".$clean")) && v.isTlsBound }) return true
            val saved = try {
                com.lagradost.common.storage.DesktopDataStore.getKey<Set<String>>(PREF_TLS_BOUND_HOSTS)
            } catch (_: Throwable) { null }
            if (saved != null && (saved.contains(clean) || saved.any { clean.endsWith(".$it") || it.endsWith(".$clean") })) {
                val current = hostStates[clean]
                if (current != null && !current.isTlsBound) {
                    hostStates[clean] = current.copy(isTlsBound = true)
                }
                return true
            }
            return false
        }

        fun markTlsBound(host: String) {
            val clean = host.lowercase().trim()
            val current = hostStates[clean]
            if (current != null) {
                hostStates[clean] = current.copy(isTlsBound = true)
            }
            try {
                val saved = com.lagradost.common.storage.DesktopDataStore.getKey<Set<String>>(PREF_TLS_BOUND_HOSTS).orEmpty().toMutableSet()
                if (saved.add(clean)) {
                    com.lagradost.common.storage.DesktopDataStore.setKey(PREF_TLS_BOUND_HOSTS, saved)
                }
            } catch (_: Throwable) {}
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

        fun unmarkAllFailed() {
            hostStates.entries.forEach { (k, v) ->
                if (v.isFailed) {
                    hostStates[k] = v.copy(isFailed = false)
                }
            }
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
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()

            val effectiveTlsBound = isTlsBound || isTlsBound(cleanHost)
            if (effectiveTlsBound) {
                markTlsBound(cleanHost)
            }

            AppLogger.i("$TAG: Registered Cloudflare clearance for $cleanHost (${finalCookies.size} cookies, UA=$userAgent, isTlsBound=$effectiveTlsBound)")
            hostStates[cleanHost] = HostClearance(
                host = cleanHost,
                cookies = finalCookies,
                userAgent = userAgent,
                isTlsBound = effectiveTlsBound,
                isFailed = false,
                timestamp = System.currentTimeMillis(),
            )

            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            jar?.saveCookies(finalCookies)
            if (!isWorker) {
                com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.saveCookies(finalCookies)
            }
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
            if (!isWorker) {
                try {
                    val vaultCookies = com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.getAllUnexpired()
                    for (cookie in vaultCookies) {
                        val domainKey = cookie.domain.trimStart('.').lowercase()
                        val list = jarCookies.getOrPut(domainKey) { mutableListOf() } as? MutableList<Cookie>
                            ?: jarCookies[domainKey]?.toMutableList()?.also { jarCookies[domainKey] = it }
                        if (list != null && list.none { it.name.equals(cookie.name, ignoreCase = true) }) {
                            list.add(cookie)
                        }
                    }
                } catch (_: Throwable) {}
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
            if (!isWorker) {
                com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.removeForHost(clean)
                if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                    com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.clearBrowserCookies(clean)
                }
            }
            if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.closeSession(clean)
            }
        }

        fun clearAllClearance() {
            hostStates.clear()
            runCatching {
                com.lagradost.common.storage.DesktopDataStore.removeKey(PREF_TLS_BOUND_HOSTS)
            }
            val jar = globalCookieJar as? com.lagradost.cloudstream3.desktop.network.DesktopCookieJar
                ?: com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance
            jar?.removeAll()
            if (!isWorker) {
                com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.clearAll()
                if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                    com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.clearBrowserCookies(null)
                }
            }
            if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.closeAllSessions()
            }
        }

        internal fun solveWithEmbeddedWebView(targetUrl: String, hostName: String, headers: Map<String, String> = emptyMap()): Boolean {
            if (!com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable ||
                com.lagradost.cloudstream3.desktop.ui.GlobalPlayerState.isPlaybackActive.value
            ) {
                if (com.lagradost.cloudstream3.desktop.ui.GlobalPlayerState.isPlaybackActive.value) {
                    AppLogger.w("$TAG: Suppressing embedded WebView2 clearance for $hostName because media playback is active.")
                }
                return false
            }

            val cleanHost = hostName.lowercase().trim()
            val apex = getApexDomain(cleanHost).ifBlank { cleanHost }
            if (com.lagradost.common.storage.DesktopDataStore.isDomainCfSilenced(cleanHost) ||
                (apex.isNotBlank() && com.lagradost.common.storage.DesktopDataStore.isDomainCfSilenced(apex))
            ) {
                AppLogger.i("$TAG: Domain $cleanHost is ignored/silenced. Skipping embedded WebView2 solve.")
                return false
            }
            val lockKey = "GLOBAL_CF_SOLVER_LOCK"

            val myFuture = java.util.concurrent.CompletableFuture<Boolean>()
            val existing = inFlightSolvers.putIfAbsent(lockKey, myFuture)
            if (existing != null) {
                AppLogger.i("$TAG: Cloudflare solve already in progress for $lockKey, awaiting existing solver...")
                return try {
                    existing.get(65, java.util.concurrent.TimeUnit.SECONDS)
                } catch (e: Throwable) {
                    AppLogger.w("$TAG: Timed out or interrupted waiting for active solver on $lockKey: ${e.message}")
                    false
                }
            }

            try {
                val solved = executeEmbeddedWebViewSolve(targetUrl, hostName, headers)
                myFuture.complete(solved)
                return solved
            } catch (t: Throwable) {
                myFuture.complete(false)
                throw t
            } finally {
                inFlightSolvers.remove(lockKey)
            }
        }

        private fun executeEmbeddedWebViewSolve(targetUrl: String, hostName: String, headers: Map<String, String> = emptyMap()): Boolean {
            AppLogger.i("$TAG: Initializing in-process WebView2 solver for $hostName...")

            val start = System.currentTimeMillis()

            unmarkFailed(hostName)

            val session = com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Session()
            val active = com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.startSession(
                session = session,
                targetUrl = targetUrl,
                hostName = hostName,
            )

            // If target URL is an API or data endpoint, navigate to origin root so interactive Turnstile renders
            val navigateUrl = if (targetUrl.contains("/api", ignoreCase = true) || targetUrl.endsWith(".json", ignoreCase = true)) {
                val httpUrl = targetUrl.toHttpUrlOrNull()
                if (httpUrl != null) "${httpUrl.scheme}://${httpUrl.host}/" else targetUrl
            } else {
                targetUrl
            }

            session.navigate(navigateUrl)

            val maxWaitMs = 60_000L
            val navStart = System.currentTimeMillis()
            val minStabilizeMs = 2_000L

            while (System.currentTimeMillis() - start < maxWaitMs) {
                if (active.isDismissed.get()) {
                    AppLogger.w("$TAG: Verification dialog dismissed by user for $hostName.")
                    com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.closeSessionSafely(session)
                    return false
                }

                // Check that challenge page has settled past turnstile screen
                val elapsedSinceNav = System.currentTimeMillis() - navStart
                val isChallengeOngoing = if (elapsedSinceNav < minStabilizeMs) {
                    true // Give Turnstile DOM at least 2s to mount before checking DOM
                } else {
                    runCatching {
                    val statusJson = kotlinx.coroutines.runBlocking {
                        session.evaluateJs(
                            """
                            (function() {
                                if (document.location.href === 'about:blank' || document.location.href === '') return 'LOADING';
                                if (document.readyState === 'loading') return 'LOADING';
                                var title = document.title || '';
                                var body = document.body ? document.body.innerHTML : '';
                                if (title.includes('Just a moment') || title.includes('Attention Required') || title.includes('Cloudflare') || title.includes('Verify you are human') || body.includes('cf-please-wait') || body.includes('cf-spinner') || body.includes('challenges.cloudflare.com')) {
                                    return 'CHALLENGE';
                                }
                                return 'CLEAR';
                            })();
                            """.trimIndent(),
                            2000L,
                        )
                    } ?: ""

                    !statusJson.contains("CLEAR") // True if LOADING, CHALLENGE, or error
                }.getOrDefault(true)
                }

                val clearance = getClearance(hostName)
                val hasClearanceCookie = clearance?.cookies?.any { it.name.equals("cf_clearance", ignoreCase = true) } == true

                if (isChallengeOngoing && elapsedSinceNav >= 3_500L && com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.activeSession.value?.isDialogVisible != true) {
                    com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.requestDialogVisibility()
                }

                if (hasClearanceCookie || (!isChallengeOngoing && elapsedSinceNav >= 12_000L && clearance != null && clearance.cookies.size >= 3)) {
                    if (clearance != null && clearance.cookies.isNotEmpty()) {
                        // Wait briefly for post-Turnstile redirect to settle and all session cookies to arrive
                        val settleDeadline = System.currentTimeMillis() + 1_500L
                        while (System.currentTimeMillis() < settleDeadline) {
                            val settled = getClearance(hostName)
                            if (settled != null && settled.cookies.size >= 2) break
                            try {
                                Thread.sleep(200)
                            } catch (_: InterruptedException) {
                                break
                            }
                        }
                    }

                    AppLogger.i("$TAG: Cloudflare challenge cleared for $hostName (${System.currentTimeMillis() - start}ms).")
                    com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.finishSuccess()
                    return true
                }

                try {
                    Thread.sleep(250)
                } catch (_: InterruptedException) {
                    break
                }
            }

            com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.dismiss()
            com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.closeSessionSafely(session)
            return false
        }

        /**
         * Route a request through the browser's Chromium network stack via CDP or WebView2.
         * Constructs a synthetic OkHttp Response so the caller is unaware of the proxy.
         */
        internal fun fetchViaBrowserProxy(request: Request, isBinary: Boolean = false): Response? {
            val host = request.url.host.lowercase()
            val isApiEndpoint = request.url.encodedPath.contains("/api", ignoreCase = true) ||
                request.url.encodedPath.endsWith(".json", ignoreCase = true)

            val preparedHeaders = buildMap {
                for (name in request.headers.names()) {
                    val lower = name.lowercase()
                    if (lower == "sec-ch-ua-platform" || lower == "sec-ch-ua-mobile") continue
                    put(name, request.header(name) ?: "")
                }
                if (isApiEndpoint && keys.none { it.equals("X-Requested-With", ignoreCase = true) }) {
                    put("X-Requested-With", "XMLHttpRequest")
                }
                if (keys.none { it.equals("Referer", ignoreCase = true) }) {
                    put("Referer", "${request.url.scheme}://${request.url.host}/")
                }
            }
            val wv2Session = if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.getOrCreateSessionForHost(host)
            } else {
                null
            }

            val result = if (wv2Session != null) {
                kotlinx.coroutines.runBlocking {
                    wv2Session.awaitOriginAndFetch(
                        url = request.url.toString(),
                        method = request.method,
                        headers = preparedHeaders,
                        body = request.body?.let { body ->
                            val buffer = okio.Buffer()
                            body.writeTo(buffer)
                            buffer.readUtf8()
                        },
                    )
                }
            } else {
                null
            }

            if (result == null || result.statusCode == 0) {
                AppLogger.e("$TAG: fetchViaBrowserProxy returned null/error for ${request.url}")
                return null
            }

            AppLogger.i("$TAG: Browser proxy returned HTTP ${result.statusCode} for ${request.url}")
            val contentTypeString = result.contentType ?: "application/octet-stream"
            val bodyBytes = result.bodyBytes ?: result.body.toByteArray(Charsets.UTF_8)

            val preview = if (result.body.length > 250) result.body.substring(0, 250) else result.body
            AppLogger.i("$TAG: Proxy response body start: ${preview.replace("\n", " ")}")

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

    init {
        if (cookieJar != null) {
            globalCookieJar = cookieJar
        }
    }

    fun getCookieHeaders(url: String): Headers {
        val httpUrl = try {
            url.toHttpUrlOrNull()
        } catch (_: Exception) {
            null
        }
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

        val isWorker = System.getProperty("cloudstream.sandbox.worker") == "true"
        val hasWv2 = !isWorker && com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable &&
            com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.getSessionForHost(host) != null
        val hasActiveProxy = hasWv2

        // If an active browser proxy exists for host, ensure not marked as failed
        if (hasActiveProxy) {
            unmarkFailed(host)
        }

        // If host has permanently failed, proceed normally
        if (isFailed(host)) {
            return chain.proceed(request)
        }

        // If host has an active browser proxy, route dynamic requests through it (HTML/JSON/API).
        // Static assets (images/posters) proceed directly via OkHttp with clearance cookies to prevent CDP socket saturation.
        if (!isStatic && hasActiveProxy) {
            val proxyResponse = fetchViaBrowserProxy(request, isBinary = false)
            if (proxyResponse != null && proxyResponse.code !in ERROR_CODES) return proxyResponse
            AppLogger.w("$TAG: Browser proxy failed for $host, falling through to normal path.")
        } else if (!isStatic && isWorker && isTlsBound(host)) {
            val proxyResult = kotlinx.coroutines.runBlocking {
                com.lagradost.cloudstream3.desktop.sandbox.worker.WorkerHostBridge.requestProxyFetch(
                    url = request.url.toString(),
                    host = host,
                    method = request.method,
                    headers = request.headers.toMap(),
                    body = request.body?.let { b ->
                        val buf = Buffer()
                        b.writeTo(buf)
                        buf.readUtf8()
                    }
                )
            }
            if (proxyResult != null && proxyResult.statusCode in 200..299) {
                val cType = proxyResult.contentType ?: "application/json"
                val bodyBytes = proxyResult.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                return Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(proxyResult.statusCode)
                    .message("OK")
                    .header("content-type", cType)
                    .header("content-length", bodyBytes.size.toString())
                    .body(bodyBytes.toResponseBody(cType.toMediaTypeOrNull()))
                    .build()
            }
        } else if (!isStatic && !isWorker && isTlsBound(host) && !hasActiveProxy) {
            AppLogger.w("$TAG: Browser proxy is dead for TLS-bound host $host. Clearing clearance state.")
            hostStates.remove(host)
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
            val bodyPreview = try {
                response.peekBody(4096).string()
            } catch (_: Exception) {
                ""
            }
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
            if (isWorker) {
                // If we already have stored clearance or cookies for this host, test them via browser proxy FIRST
                // before requesting an interactive solve, because OkHttp might have failed purely due to TLS fingerprint blocking.
                if (!isStatic) {
                    val candidateClearance = usedClearance ?: getClearance(request.url) ?: getClearance(host)
                    if (candidateClearance != null && candidateClearance.cookies.isNotEmpty()) {
                        val proxyResult = kotlinx.coroutines.runBlocking {
                            com.lagradost.cloudstream3.desktop.sandbox.worker.WorkerHostBridge.requestProxyFetch(
                                url = request.url.toString(),
                                host = host,
                                method = request.method,
                                headers = request.headers.toMap(),
                                body = request.body?.let { b ->
                                    val buf = Buffer()
                                    b.writeTo(buf)
                                    buf.readUtf8()
                                }
                            )
                        }
                        if (proxyResult != null && proxyResult.statusCode in 200..299) {
                            AppLogger.i("$TAG: Existing credentials succeeded via host browser proxy for $host (HTTP ${proxyResult.statusCode}). Marking as TLS-bound.")
                            markTlsBound(host)
                            response.close()
                            val cType = proxyResult.contentType ?: "application/json"
                            val bodyBytes = proxyResult.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                            return Response.Builder()
                                .request(request)
                                .protocol(Protocol.HTTP_1_1)
                                .code(proxyResult.statusCode)
                                .message("OK")
                                .header("content-type", cType)
                                .header("content-length", bodyBytes.size.toString())
                                .body(bodyBytes.toResponseBody(cType.toMediaTypeOrNull()))
                                .build()
                        }
                    }
                }

                AppLogger.w("$TAG: Cloudflare challenge detected inside sandboxed worker for $host. Requesting host challenge solve via IPC bridge...")
                val solveResult = kotlinx.coroutines.runBlocking {
                    com.lagradost.cloudstream3.desktop.sandbox.worker.WorkerHostBridge.requestCloudflareSolve(
                        url = request.url.toString(),
                        host = host,
                        headers = request.headers.toMap()
                    )
                }
                if (solveResult != null && solveResult.cookies.isNotEmpty()) {
                    AppLogger.i("$TAG: Host solved challenge successfully for $host (${solveResult.cookies.size} cookies). Retrying request.")
                    com.lagradost.cloudstream3.desktop.network.DesktopCookieJar.activeInstance?.saveCookies(solveResult.cookies)
                    val ua = solveResult.userAgent?.takeIf { it.isNotBlank() } ?: com.lagradost.cloudstream3.USER_AGENT
                    saveClearance(host = host, userAgent = ua, okCookies = solveResult.cookies)
                    response.close()
                    val newRequest = request.newBuilder().build()
                    val retryResponse = proceed(chain, newRequest, solveResult.cookies, ua)
                    if (retryResponse.code in ERROR_CODES && !isStatic) {
                        AppLogger.w("$TAG: Worker OkHttp retry returned HTTP ${retryResponse.code} for $host (TLS fingerprint blocked). Requesting host browser proxy fetch via IPC bridge...")
                        retryResponse.close()
                        val proxyResult = kotlinx.coroutines.runBlocking {
                            com.lagradost.cloudstream3.desktop.sandbox.worker.WorkerHostBridge.requestProxyFetch(
                                url = newRequest.url.toString(),
                                host = host,
                                method = newRequest.method,
                                headers = newRequest.headers.toMap(),
                                body = newRequest.body?.let { b ->
                                    val buf = Buffer()
                                    b.writeTo(buf)
                                    buf.readUtf8()
                                }
                            )
                        }
                        if (proxyResult != null && proxyResult.statusCode in 200..299) {
                            AppLogger.i("$TAG: Worker browser proxy fetch succeeded for $host (HTTP ${proxyResult.statusCode})")
                            markTlsBound(host)
                            hostStates[host] = HostClearance(host, solveResult.cookies, ua, isTlsBound = true)
                            val cType = proxyResult.contentType ?: "application/json"
                            val bodyBytes = proxyResult.body?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
                            return Response.Builder()
                                .request(newRequest)
                                .protocol(Protocol.HTTP_1_1)
                                .code(proxyResult.statusCode)
                                .message("OK")
                                .header("content-type", cType)
                                .header("content-length", bodyBytes.size.toString())
                                .body(bodyBytes.toResponseBody(cType.toMediaTypeOrNull()))
                                .build()
                        } else {
                            AppLogger.w("$TAG: Worker browser proxy fetch failed for $host (status: ${proxyResult?.statusCode}, error: ${proxyResult?.error})")
                        }
                    }
                    return retryResponse
                } else {
                    AppLogger.w("$TAG: Host was unable to solve challenge for $host. Returning original response.")
                    return response
                }
            }
            lastChallengedHost = host
            lastChallengedUrl = request.url.toString()
            lastChallengeTimestamp = System.currentTimeMillis()

            if (usedClearance != null && !isTlsBound(host)) {
                AppLogger.w("$TAG: Expired or rejected Cloudflare credentials for $host. Removing from cache.")
                clearClearanceForDomain(host)
                val apex = getApexDomain(host)
                if (apex.isNotBlank() && apex != host) {
                    clearClearanceForDomain(apex)
                }
                // Keep vault cookies intact; new solve credentials will overwrite if needed
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
                return response
            }

            if (!isFailed(host)) {
                val solved: Boolean = synchronized(CloudflareKiller::class.java) {
                    val current = getClearance(request.url)
                    if (current != null && current.cookies.isNotEmpty()) {
                        return@synchronized true
                    }

                    if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                        solveWithEmbeddedWebView(request.url.toString(), host, request.headers.toMap())
                    } else {
                        AppLogger.w("$TAG: Cloudflare challenge for $host cannot be solved; embedded WebView2 is unavailable.")
                        try {
                            // removed
                                // removed
                                // removed
                            // )
                        } catch (_: Throwable) {}
                        kotlinx.coroutines.runBlocking {
                            false // launchManualClearance removed
                            // targetUrl removed
                            // hostName removed
                            // )
                        }
                    }
                }

                val rawClearance = getClearance(request.url)
                // If we solved it but got zero cookies, it means CF let the WebView in instantly without issuing a clearance cookie.
                // This happens when CF is only blocking OkHttp's TLS fingerprint, not Edge's.
                // We MUST proceed anyway, because the proxy (which uses Edge) will work perfectly!
                val solvedClearance = rawClearance ?: HostClearance(request.url.host, emptyList(), com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.userAgent)

                if (solved) {
                    AppLogger.i("$TAG: Successfully cleared challenge gate for $host. Retrying request.")
                    try {
                        com.lagradost.cloudstream3.desktop.ui.components.AppToastManager.showSuccess(
                            "Cloudflare clearance resolved successfully.",
                            durationMs = 3000L,
                        )
                    } catch (_: Throwable) {}
                    response.close()

                    val proxyActivated = if (!com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                        kotlinx.coroutines.runBlocking {
                            false // activateFetchProxy removed
                        }
                    } else {
                        false
                    }
                    if (proxyActivated) {
                        hostStates[host] = solvedClearance.copy(isTlsBound = true)
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
                    val newRequest = request.newBuilder()
                        .headers(request.headers)
                        .apply {
                            if (isApiEndpoint && !request.headers.names().any { it.equals("X-Requested-With", ignoreCase = true) }) {
                                AppLogger.i("$TAG: [DEBUG] Injecting X-Requested-With for API endpoint OkHttp retry")
                                addHeader("X-Requested-With", "XMLHttpRequest")
                            }
                        }
                        .build()

                    val retryResponse = proceed(chain, newRequest, solvedClearance.cookies, solvedClearance.userAgent)

                    AppLogger.i("$TAG: [DEBUG] OkHttp retry returned HTTP ${retryResponse.code} for ${retryResponse.request.url}")
                    AppLogger.i("$TAG: [DEBUG] Response Headers: ${retryResponse.headers.toMap()}")
                    AppLogger.i("$TAG: [DEBUG] Response Body Preview: ${retryResponse.peekBody(512).string().replace("\n", " ")}")

                    if (retryResponse.code !in ERROR_CODES) {
                        // Check if The provider redirected us to the homepage and returned HTML instead of JSON
                        if (isApiEndpoint && retryResponse.header("Content-Type")?.contains("text/html", ignoreCase = true) == true) {
                            AppLogger.w("$TAG: [DEBUG] OkHttp returned HTML for API endpoint! This is a redirect to the homepage! Forcing proxy fallback!")
                            retryResponse.close() // Close the bad response and fall through to proxy
                        } else {
                            return retryResponse
                        }
                    }
                    AppLogger.w("$TAG: OkHttp retry rejected (HTTP ${retryResponse.code}). TLS fingerprint mismatch confirmed for $host.")

                    val wv2Session = if (com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.isAvailable) {
                        com.lagradost.cloudstream3.desktop.network.webview.EmbeddedVerificationState.getSessionForHost(host)
                    } else {
                        null
                    }

                    if (wv2Session != null && !isStatic) {
                        hostStates[host] = solvedClearance.copy(isTlsBound = true)
                        var proxyResponse = fetchViaBrowserProxy(newRequest, isBinary = false)
                        if (proxyResponse != null && proxyResponse.code in ERROR_CODES) {
                            // Browser may still be completing the post-solve redirect; retry once
                            proxyResponse.close()
                            try {
                                Thread.sleep(1500)
                            } catch (_: InterruptedException) {}
                            proxyResponse = fetchViaBrowserProxy(newRequest, isBinary = false)
                        }
                        if (proxyResponse != null && proxyResponse.code !in ERROR_CODES) {
                            retryResponse.close()
                            return proxyResponse
                        }
                    } else if (!proxyActivated && !isStatic) {
                        val activated = kotlinx.coroutines.runBlocking {
                            false // activateFetchProxy removed
                        }
                        if (activated) {
                            val proxyResponse = fetchViaBrowserProxy(request, isBinary = false)
                            if (proxyResponse != null && proxyResponse.code !in ERROR_CODES) {
                                retryResponse.close()
                                return proxyResponse
                            }
                        }
                    }

                    // Don't permanently blacklist — let the next request retry the full solve flow
                    AppLogger.w("$TAG: Browser navigation failed for $host. Returning last response.")
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
            val hostLower = request.url.host.lowercase()
            val apex = getApexDomain(hostLower)
            val isSubdomain = apex.isNotBlank() && apex != hostLower

            val site = if (isSubdomain) "same-site" else "same-origin"
            val mode = if (isStatic) "no-cors" else "cors"
            val dest = if (isStatic) "image" else "empty"

            builder.header("sec-fetch-site", site)
            builder.header("sec-fetch-mode", mode)
            builder.header("sec-fetch-dest", dest)
            builder.header("accept-language", "en-US,en;q=0.9")
            if (request.header("referer") == null) {
                val refHost = if (isSubdomain) apex else hostLower
                builder.header("referer", "${request.url.scheme}://$refHost/")
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
                } catch (_: Exception) {
                    null
                }
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
}
