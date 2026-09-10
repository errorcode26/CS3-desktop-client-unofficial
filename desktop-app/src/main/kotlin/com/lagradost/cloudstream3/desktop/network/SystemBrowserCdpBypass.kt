package com.lagradost.cloudstream3.desktop.network

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.File
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

object SystemBrowserCdpBypass {
    private const val TAG = "SystemBrowserCdpBypass"
    private val mapper = jacksonObjectMapper()
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val bypassMutex = Mutex()
    private var isBrowserOpen = false

    data class ProxySession(
        val domain: String,
        val port: Int,
        val process: Process,
        val sessionDirName: String,
        val userDataDir: File,
        @Volatile var settledDomain: String? = null,
        @Volatile var settledUrl: String? = null,
        @Volatile var webSocket: WebSocket? = null,
        @Volatile var lastActivity: Long = System.currentTimeMillis(),
        @Volatile var userAgent: String? = null,
        val pendingFetches: ConcurrentHashMap<Int, CompletableDeferred<String?>> = ConcurrentHashMap(),
        val messageId: AtomicInteger = AtomicInteger(10000),
        var watchdogJob: Job? = null,
    ) {
        val apexDomain: String get() = domain
    }

    // Active proxy sessions keyed by domain
    private val activeSessions = ConcurrentHashMap<String, ProxySession>()
    @Volatile private var pendingSession: ProxySession? = null
    private const val PROXY_IDLE_TIMEOUT_MS = 300_000L // 5 minutes idle timeout

    /**
     * Look up an active proxy session for a host using exact domain match
     * or matching against the settled domain where the browser landed after redirects.
     * Cross-origin subdomains are strictly excluded to avoid browser Same-Origin Policy (CORS) blocks.
     */
    fun getSessionForHost(host: String): ProxySession? {
        val cleanHost = host.lowercase().trim()
        if (cleanHost.isBlank()) return null
        // 1. Direct match on registered session domain
        activeSessions[cleanHost]?.let { session ->
            if (session.webSocket != null && session.process.isAlive) return session
        }
        // 2. Direct match on settled domain (where the browser landed after any Turnstile redirects)
        val settledMatch = activeSessions.values.firstOrNull { session ->
            session.webSocket != null && session.process.isAlive &&
                (cleanHost.equals(session.domain, ignoreCase = true) ||
                 cleanHost.equals(session.settledDomain, ignoreCase = true))
        }
        if (settledMatch != null) return settledMatch

        return null
    }

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    data class CdpTarget(
        val id: String,
        val type: String,
        val url: String,
        @com.fasterxml.jackson.annotation.JsonProperty("webSocketDebuggerUrl") val webSocketDebuggerUrl: String? = null,
    )

    data class ClearanceResult(
        val cookies: List<Cookie>,
        val cookieMap: Map<String, String>,
        val userAgent: String,
        val settledUrl: String,
        val settledHtml: String,
        val webSocket: WebSocket? = null,
    )

    data class CdpFetchResult(
        val statusCode: Int,
        val contentType: String?,
        val body: String,
        val bodyBytes: ByteArray? = null,
    )

    /**
     * Launches a single Edge/Chrome window targeting the root domain of the site.
     * Allows the user to manually solve the Turnstile challenge in a genuine browser.
     * Polls cookies via CDP every 1s until cf_clearance is acquired (or window closed).
     */
    suspend fun launchManualClearance(targetUrl: String, hostName: String? = null): Boolean = bypassMutex.withLock {
        val uri = try { URI(targetUrl) } catch (_: Exception) { null }
        val host = (hostName?.ifBlank { null } ?: uri?.host ?: "").lowercase().trim()

        // 1. If targetUrl HTML is already in SettledPageCache, skip browser launch
        if (SettledPageCache.get(targetUrl) != null) {
            AppLogger.i("$TAG: Settled HTML already available in cache for $targetUrl. Skipping browser launch.")
            return true
        }

        // 2. If an active proxy session already exists for this domain, navigate the existing tab to targetUrl
        val activeSession = getSessionForHost(host)
        if (activeSession != null && activeSession.webSocket != null) {
            AppLogger.i("$TAG: Active browser proxy found for $host. Navigating tab to $targetUrl...")
            val navigated = navigateSessionToUrl(activeSession, targetUrl, host)
            if (navigated) {
                return true
            }
        }

        if (isBrowserOpen) {
            AppLogger.w("$TAG: A manual clearance window is already open. Ignoring duplicate request.")
            return false
        }
        isBrowserOpen = true

        val rootUrl = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            targetUrl
        } else if (host.isNotBlank()) {
            "https://$host/"
        } else {
            targetUrl
        }

        val port = (9222..9999).random()
        val sessionDirName = "CloudStream_CF_${System.currentTimeMillis()}"
        val userDataDir = File(System.getProperty("java.io.tmpdir"), sessionDirName).apply { mkdirs() }

        val edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
        val chromePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"

        val browserPath = when {
            File(edgePath).exists() -> edgePath
            File(chromePath).exists() -> chromePath
            else -> {
                AppLogger.e("$TAG: Neither Edge nor Chrome was found on standard paths.")
                isBrowserOpen = false
                return false
            }
        }

        val dohUrl = NetworkConfig.getCurrentDohUrl()
        if (dohUrl != null) {
            try {
                val defaultDir = File(userDataDir, "Default").apply { mkdirs() }
                val prefFile = File(defaultDir, "Preferences")
                prefFile.writeText("""{"dns_over_https":{"mode":"secure","templates":"$dohUrl"}}""")
            } catch (_: Exception) {}
        }

        AppLogger.i("$TAG: Launching manual verification window for $rootUrl on port $port")
        val privateFlag = if (browserPath.contains("msedge", ignoreCase = true)) "--inprivate" else "--incognito"
        val processArgs = mutableListOf(
            browserPath,
            "--app=$rootUrl",
            privateFlag,
            "--user-data-dir=${userDataDir.absolutePath}",
            "--remote-debugging-port=$port",
            "--remote-allow-origins=*",
            "--window-size=900,800",
            "--disable-extensions",
            "--disable-component-extensions-with-background-pages",
            "--no-default-browser-check",
            "--no-first-run",
            "--disable-sync",
            "--disable-features=msImplicitSignIn,msEdgeSingleSignOn,Sync,IdentityConsistency,EnableTokenBinding,msProfilePicker,msHub",
            "--password-store=basic",
            "--disable-background-networking",
            "--disable-default-apps",
            "--disable-component-update",
            "--disable-gpu",
            // Prevent Chromium from throttling background timers/renderers when solver is minimized
            "--disable-background-timer-throttling",
            "--disable-backgrounding-occluded-windows",
            "--disable-renderer-backgrounding",
        )

        if (dohUrl != null) {
            val encodedDoh = java.net.URLEncoder.encode(dohUrl, "UTF-8")
            processArgs.add("--enable-features=DnsOverHttps")
            processArgs.add("--dns-over-https-templates=$dohUrl")
            processArgs.add("--force-fieldtrials=DoHTrial/Group1")
            processArgs.add("--force-fieldtrial-params=DoHTrial.Group1:Fallback/false/Templates/$encodedDoh")
            AppLogger.i("$TAG: Configured Chromium solver with DoH: $dohUrl")
        }

        val process = ProcessBuilder(processArgs).start()

        val session = ProxySession(
            domain = host,
            port = port,
            process = process,
            sessionDirName = sessionDirName,
            userDataDir = userDataDir,
        )

        try {
            val result = waitForClearance(session, rootUrl, host)
            if (result != null) {
                AppLogger.i("$TAG: Manual Cloudflare verification succeeded for $host!")
                CloudflareKiller.saveClearance(host, userAgent = result.userAgent, okCookies = result.cookies)
                session.userAgent = result.userAgent

                val settledHost = try { URI(result.settledUrl).host } catch (_: Exception) { null }?.lowercase()?.trim()
                session.settledDomain = settledHost
                session.settledUrl = result.settledUrl
                if (!settledHost.isNullOrBlank() && settledHost != host) {
                    activeSessions[settledHost] = session
                    CloudflareKiller.saveClearance(settledHost, userAgent = result.userAgent, okCookies = result.cookies)
                    AppLogger.i("$TAG: Registered settled redirected domain alias: $settledHost for $host.")
                }

                // 1. Sync captured cookies to DesktopCookieJar
                (com.lagradost.cloudstream3.app.baseClient.cookieJar as? DesktopCookieJar)?.saveCookies(result.cookies)

                // 2. Cache settled HTML in SettledPageCache
                if (result.settledHtml.isNotBlank()) {
                    SettledPageCache.put(targetUrl, result.settledHtml, result.userAgent)
                    if (result.settledUrl.isNotBlank() && result.settledUrl != targetUrl) {
                        SettledPageCache.put(result.settledUrl, result.settledHtml, result.userAgent)
                    }
                }

                // 3. Sync captured clearance cookies to Android CookieManager stub
                try {
                    val cookieManager = android.webkit.CookieManager.getInstance()
                    result.cookies.forEach { cookie ->
                        cookieManager.setCookie(targetUrl, "${cookie.name}=${cookie.value}")
                    }
                } catch (e: Exception) {
                    AppLogger.w("$TAG: Failed to sync cookies to CookieManager: ${e.message}")
                }

                // Hold session as pending — will be closed immediately when cache or OkHttp succeeds,
                // or promoted to activeSessions if TLS fingerprint rejection requires a persistent proxy.
                session.lastActivity = System.currentTimeMillis()
                pendingSession = session

                isBrowserOpen = false
                return true
            } else {
                AppLogger.w("$TAG: Verification window closed before cf_clearance was obtained.")
                destroyBrowserSession(process, sessionDirName, userDataDir)
                isBrowserOpen = false
                return false
            }
        } catch (e: Exception) {
            destroyBrowserSession(process, sessionDirName, userDataDir)
            isBrowserOpen = false
            throw e
        }
    }

    private fun parseCdpCookie(cookieNode: com.fasterxml.jackson.databind.JsonNode): Cookie? {
        try {
            val name = cookieNode.get("name")?.asText()?.trim().orEmpty()
            val value = cookieNode.get("value")?.asText().orEmpty()
            if (name.isEmpty()) return null

            val rawDomain = cookieNode.get("domain")?.asText().orEmpty()
            val domain = rawDomain.trimStart('.').lowercase()
            if (domain.isEmpty()) return null

            val path = cookieNode.get("path")?.asText()?.ifBlank { "/" } ?: "/"
            val expiresSec = cookieNode.get("expires")?.asDouble() ?: -1.0
            val isSecure = cookieNode.get("secure")?.asBoolean() ?: false
            val isHttpOnly = cookieNode.get("httpOnly")?.asBoolean() ?: false

            val builder = Cookie.Builder()
                .name(name)
                .value(value)
                .domain(domain)
                .path(path)

            if (expiresSec > 0) {
                builder.expiresAt((expiresSec * 1000).toLong())
            } else {
                builder.expiresAt(System.currentTimeMillis() + 86_400_000L)
            }

            if (isSecure) builder.secure()
            if (isHttpOnly) builder.httpOnly()

            return builder.build()
        } catch (_: Exception) {
            return null
        }
    }

    private suspend fun waitForClearance(session: ProxySession, targetUrl: String, host: String): ClearanceResult? {
        val port = session.port
        var wsUrl: String? = null
        for (i in 1..40) { // Wait up to 20 seconds for CDP endpoint
            delay(500)
            try {
                val req = Request.Builder().url("http://127.0.0.1:$port/json").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyStr = resp.body.string()
                        val targets = mapper.readValue<List<CdpTarget>>(bodyStr)
                        val pageTarget = targets.find { it.type == "page" }
                        if (pageTarget?.webSocketDebuggerUrl != null) {
                            wsUrl = pageTarget.webSocketDebuggerUrl
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.d("$TAG: CDP probe on port $port: ${e.message}")
            }
        }

        val finalWsUrl = wsUrl
        if (finalWsUrl == null) {
            AppLogger.e("$TAG: Failed to connect to browser CDP at port $port")
            return null
        }

        return suspendCancellableCoroutine { cont ->
            var resumed = false
            var hasDetectedClearance = false
            var capturedCookies: List<Cookie>? = null
            var capturedCookieMap: Map<String, String>? = null
            var capturedHtml: String? = null
            var capturedUa: String? = null
            var capturedSettledUrl: String = targetUrl

            val wsReq = Request.Builder().url(finalWsUrl).build()
            val webSocket = client.newWebSocket(
                wsReq,
                object : WebSocketListener() {
                    var messageId = 1
                    var pollingJob: Job? = null
                    var settleJob: Job? = null

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        session.webSocket = webSocket
                        pollingJob = CoroutineScope(Dispatchers.IO).launch {
                            try {
                                webSocket.send("""{"id": 1, "method": "Network.enable"}""")
                                webSocket.send("""{"id": 99999, "method": "Page.setBypassCSP", "params": {"enabled": true}}""")
                                while (isActive && !resumed && !hasDetectedClearance) {
                                    delay(1000)
                                    if (resumed || hasDetectedClearance) break
                                    webSocket.send("""{"id": ${++messageId}, "method": "Network.getAllCookies"}""")
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val tree = mapper.readTree(text)
                            if (tree.get("method")?.asText() == "Inspector.targetCrashed") {
                                AppLogger.e("$TAG: Browser tab crashed!")
                                session.pendingFetches.values.forEach { it.complete(null) }
                                session.pendingFetches.clear()
                                return
                            }
                            val id = tree.get("id")?.asInt() ?: -1

                            // Handle fetch responses for active proxy sessions
                            val pendingDeferred = session.pendingFetches[id]
                            if (pendingDeferred != null) {
                                val result = tree.get("result")
                                if (result != null && result.has("result")) {
                                    pendingDeferred.complete(result.get("result").get("value")?.asText())
                                } else if (result != null && result.has("exceptionDetails")) {
                                    val errorMsg = result.get("exceptionDetails")?.get("exception")?.get("description")?.asText() ?: "Unknown CDP error"
                                    AppLogger.e("$TAG: Fetch proxy JS error: $errorMsg")
                                    pendingDeferred.complete(null)
                                } else {
                                    pendingDeferred.complete(null)
                                }
                                session.pendingFetches.remove(id)
                                return
                            }

                            // 1. Polling check during challenge: search for cf_clearance
                            if (id in 2..8000 && tree.has("result")) {
                                val cookiesNode = tree.get("result").get("cookies")
                                if (cookiesNode != null && cookiesNode.isArray && !hasDetectedClearance) {
                                    for (cookie in cookiesNode) {
                                        val name = cookie.get("name")?.asText().orEmpty()
                                        val value = cookie.get("value")?.asText().orEmpty()
                                        if (name == "cf_clearance" && value.length > 20) {
                                            hasDetectedClearance = true
                                            AppLogger.i("$TAG: Detected valid cf_clearance in browser session! Waiting for page navigation to settle...")
                                            pollingJob?.cancel()
                                            startSettleWatch(webSocket)
                                            break
                                        }
                                    }
                                }
                                return
                            }

                            // 2. Page settle check response (ID 8888)
                            if (id == 8888) {
                                val value = tree.get("result")?.get("result")?.get("value")?.asText() ?: ""
                                val parts = value.split(":::", limit = 3)
                                val title = parts.getOrNull(0)?.trim() ?: ""
                                val readyState = parts.getOrNull(1)?.trim() ?: ""
                                val currentUrl = parts.getOrNull(2)?.trim() ?: targetUrl
                                val isChallenge = title.contains("Just a moment", ignoreCase = true) ||
                                    title.contains("Attention Required", ignoreCase = true) ||
                                    title.contains("Cloudflare", ignoreCase = true)
                                val isSettled = !isChallenge && readyState == "complete" &&
                                    (title.isNotEmpty() || currentUrl.contains("/api") || currentUrl != targetUrl)
                                if (isSettled) {
                                    AppLogger.i("$TAG: Page settled on real site: '$title' ($currentUrl). Initiating atomic state capture...")
                                    settleJob?.cancel()
                                    capturedSettledUrl = currentUrl
                                    // Trigger atomic capture sequence:
                                    // 9001: GetAllCookies, 9002: OuterHTML, 9003: UserAgent, 9004: GetWindow
                                    webSocket.send("""{"id": 9001, "method": "Network.getAllCookies"}""")
                                    webSocket.send("""{"id": 9002, "method": "Runtime.evaluate", "params": {"expression": "document.documentElement.outerHTML"}}""")
                                    webSocket.send("""{"id": 9003, "method": "Browser.getVersion"}""")
                                    webSocket.send("""{"id": 9004, "method": "Browser.getWindowForTarget"}""")
                                }
                                return
                            }

                            // 3. Atomic Capture: GetAllCookies (ID 9001)
                            if (id == 9001 && tree.has("result")) {
                                val cookiesNode = tree.get("result").get("cookies")
                                if (cookiesNode != null && cookiesNode.isArray) {
                                    val okCookies = mutableListOf<Cookie>()
                                    val cookieMap = mutableMapOf<String, String>()
                                    for (cn in cookiesNode) {
                                        val cookie = parseCdpCookie(cn)
                                        if (cookie != null) {
                                            okCookies.add(cookie)
                                            cookieMap[cookie.name] = cookie.value
                                        }
                                    }
                                    capturedCookies = okCookies
                                    capturedCookieMap = cookieMap
                                    AppLogger.i("$TAG: Captured ${okCookies.size} settled cookies with full metadata.")
                                    checkCompletion()
                                }
                                return
                            }

                            // 4. Atomic Capture: OuterHTML (ID 9002)
                            if (id == 9002 && tree.has("result")) {
                                val html = tree.get("result")?.get("result")?.get("value")?.asText() ?: ""
                                capturedHtml = html
                                AppLogger.i("$TAG: Captured settled DOM HTML (length=${html.length}).")
                                checkCompletion()
                                return
                            }

                            // 5. Atomic Capture: User-Agent (ID 9003)
                            if (id == 9003 && tree.has("result")) {
                                val ua = tree.get("result")?.get("userAgent")?.asText() ?: ""
                                capturedUa = ua
                                AppLogger.i("$TAG: Captured User-Agent: $ua.")
                                checkCompletion()
                                return
                            }

                            // 6. Minimize window (ID 9004 -> 9005)
                            if (id == 9004 && tree.has("result")) {
                                val windowId = tree.get("result")?.get("windowId")?.asInt()
                                if (windowId != null) {
                                    webSocket.send("""{"id": 9005, "method": "Browser.setWindowBounds", "params": {"windowId": $windowId, "bounds": {"windowState": "minimized"}}}""")
                                }
                                return
                            }
                        } catch (e: Exception) {
                            AppLogger.e("$TAG: Error parsing CDP message: ${e.message}")
                        }
                    }

                    private fun startSettleWatch(ws: WebSocket) {
                        settleJob = CoroutineScope(Dispatchers.IO).launch {
                            var attempts = 0
                            while (isActive && attempts++ < 20) {
                                delay(500)
                                if (!isActive) break
                                ws.send("""{"id": 8888, "method": "Runtime.evaluate", "params": {"expression": "document.title + ':::' + document.readyState + ':::' + window.location.href"}}""")
                            }
                            if (isActive && !resumed) {
                                AppLogger.w("$TAG: Page settle poll timed out, requesting capture anyway.")
                                ws.send("""{"id": 9001, "method": "Network.getAllCookies"}""")
                                ws.send("""{"id": 9002, "method": "Runtime.evaluate", "params": {"expression": "document.documentElement.outerHTML"}}""")
                                ws.send("""{"id": 9003, "method": "Browser.getVersion"}""")
                            }
                        }
                    }

                    private fun checkCompletion() {
                        val cookies = capturedCookies ?: return
                        val html = capturedHtml ?: return
                        val ua = capturedUa ?: return
                        if (!resumed) {
                            resumed = true
                            pollingJob?.cancel()
                            settleJob?.cancel()
                            cont.resume(
                                ClearanceResult(
                                    cookies = cookies,
                                    cookieMap = capturedCookieMap ?: emptyMap(),
                                    userAgent = ua,
                                    settledUrl = capturedSettledUrl,
                                    settledHtml = html,
                                    webSocket = session.webSocket,
                                )
                            )
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        pollingJob?.cancel()
                        settleJob?.cancel()
                        if (!resumed) {
                            resumed = true
                            cont.resume(null)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        pollingJob?.cancel()
                        settleJob?.cancel()
                        if (!resumed) {
                            resumed = true
                            cont.resume(null)
                        }
                    }
                },
            )

            cont.invokeOnCancellation {
                session.webSocket?.close(1000, "Cancelled")
            }
        }
    }

    // ── Proxy Session Lifecycle ──────────────────────────────────────────

    private fun destroyBrowserSession(process: Process, sessionDirName: String, userDataDir: File) {
        runCatching { process.destroy() }
        runCatching {
            val script = "Get-CimInstance Win32_Process -Filter \"Name = 'msedge.exe' OR Name = 'chrome.exe'\" | Where-Object { \$_.CommandLine -match '$sessionDirName' } | Invoke-CimMethod -MethodName Terminate"
            ProcessBuilder("powershell", "-NoProfile", "-Command", script).start().waitFor()
        }
        runCatching {
            Thread.sleep(1000)
            userDataDir.deleteRecursively()
        }
    }

    /** Check if a fetch proxy is active for the given host or its domain. */
    fun hasActiveProxy(host: String): Boolean {
        return getSessionForHost(host)?.webSocket != null
    }

    /** Close pending browser session (OkHttp retry succeeded, proxy not needed). */
    fun closePendingSession() {
        val session = pendingSession ?: return
        pendingSession = null
        AppLogger.i("$TAG: Clearance resolved and verified, terminating browser solver session for ${session.domain}.")
        closeSession(session)
    }

    /** Close the active proxy session for a specific host or all sessions. */
    fun closeProxySession(host: String? = null) {
        if (host != null) {
            val session = getSessionForHost(host)
            if (session != null) {
                AppLogger.i("$TAG: Closing proxy session for ${session.domain}.")
                closeSession(session)
            }
        } else {
            AppLogger.i("$TAG: Closing all active proxy sessions.")
            activeSessions.values.forEach { closeSession(it) }
            activeSessions.clear()
        }
    }

    private fun closeSession(session: ProxySession) {
        session.watchdogJob?.cancel()
        session.webSocket?.close(1000, "Session ended")
        session.webSocket = null
        session.pendingFetches.values.forEach { it.complete(null) }
        session.pendingFetches.clear()
        activeSessions.remove(session.domain)
        session.settledDomain?.let { activeSessions.remove(it) }
        CoroutineScope(Dispatchers.IO).launch {
            destroyBrowserSession(session.process, session.sessionDirName, session.userDataDir)
        }
    }

    /**
     * Connect persistent proxy WebSocket to the still-running browser.
     * Called when OkHttp retry confirms TLS fingerprint binding.
     */
    suspend fun activateFetchProxy(host: String = ""): Boolean {
        val cleanHost = host.lowercase().trim()
        val session = pendingSession?.takeIf { cleanHost.isBlank() || it.domain == cleanHost || cleanHost.endsWith(".${it.domain}") }
            ?: (if (cleanHost.isNotBlank()) getSessionForHost(cleanHost) else null)
            ?: pendingSession

        if (session == null) {
            AppLogger.e("$TAG: No pending browser session to activate as proxy for $host.")
            return false
        }

        if (session.webSocket != null) {
            AppLogger.i("$TAG: Promoting existing browser session to active proxy for ${session.domain}.")
            session.lastActivity = System.currentTimeMillis()
            activeSessions[session.domain] = session
            if (pendingSession === session) {
                pendingSession = null
            }
            startWatchdog(session)
            return true
        }

        val port = session.port
        val domain = session.domain

        // Discover CDP endpoint on the still-running browser
        var wsUrl: String? = null
        for (i in 1..10) {
            delay(300)
            try {
                val req = Request.Builder().url("http://127.0.0.1:$port/json").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val targets = mapper.readValue<List<CdpTarget>>(resp.body.string())
                        val pageTarget = targets.find { it.type == "page" && it.webSocketDebuggerUrl != null }
                        if (pageTarget != null) {
                            wsUrl = pageTarget.webSocketDebuggerUrl
                        }
                    }
                }
                if (wsUrl != null) break
            } catch (e: Exception) {
                AppLogger.d("$TAG: Proxy CDP probe attempt $i: ${e.message}")
            }
        }

        val finalWsUrl = wsUrl
        if (finalWsUrl == null) {
            AppLogger.e("$TAG: Failed to reconnect to browser CDP for proxy on port $port.")
            closePendingSession()
            return false
        }

        AppLogger.i("$TAG: Activating fetch proxy for $domain via CDP at $finalWsUrl")

        val wsReq = Request.Builder().url(finalWsUrl).build()
        session.webSocket = client.newWebSocket(wsReq, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"id": 1, "method": "Network.enable"}""")
                webSocket.send("""{"id": 2, "method": "Browser.getWindowForTarget"}""")
                webSocket.send("""{"id": 99999, "method": "Page.setBypassCSP", "params": {"enabled": true}}""")
                AppLogger.i("$TAG: Proxy WebSocket connected for $domain.")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val tree = mapper.readTree(text)
                    if (tree.get("method")?.asText() == "Inspector.targetCrashed") {
                        AppLogger.e("$TAG: Proxy browser tab crashed!")
                        session.pendingFetches.values.forEach { it.complete(null) }
                        session.pendingFetches.clear()
                        return
                    }
                    val msgId = tree.get("id")?.asInt() ?: return

                    // Minimize the browser window once we have the window ID
                    if (msgId == 2 && tree.has("result")) {
                        val windowId = tree.get("result")?.get("windowId")?.asInt()
                        if (windowId != null) {
                            webSocket.send("""{"id": 3, "method": "Browser.setWindowBounds", "params": {"windowId": $windowId, "bounds": {"windowState": "minimized"}}}""")
                        }
                        return
                    }

                    // Dispatch fetch responses to waiting callers
                    val deferred = session.pendingFetches[msgId]
                    if (deferred != null) {
                        val result = tree.get("result")
                        if (result != null && result.has("result")) {
                            deferred.complete(result.get("result").get("value")?.asText())
                        } else if (result != null && result.has("exceptionDetails")) {
                            val errorMsg = result.get("exceptionDetails")?.get("exception")?.get("description")?.asText() ?: "Unknown CDP error"
                            AppLogger.e("$TAG: Fetch proxy JS error: $errorMsg")
                            deferred.complete(null)
                        } else {
                            deferred.complete(null)
                        }
                        session.pendingFetches.remove(msgId)
                    }
                } catch (e: Exception) {
                    AppLogger.e("$TAG: Error parsing proxy CDP message: ${e.message}")
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                AppLogger.e("$TAG: Proxy WebSocket failed for $domain: ${t.message}")
                session.webSocket = null
                session.pendingFetches.values.forEach { it.complete(null) }
                session.pendingFetches.clear()
                activeSessions.remove(domain)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                AppLogger.i("$TAG: Proxy WebSocket closed for $domain ($code: $reason)")
                session.webSocket = null
                session.pendingFetches.values.forEach { it.complete(null) }
                session.pendingFetches.clear()
                activeSessions.remove(domain)
            }
        })

        session.lastActivity = System.currentTimeMillis()
        activeSessions[domain] = session
        if (pendingSession === session) {
            pendingSession = null
        }

        startWatchdog(session)
        return true
    }

    private fun startWatchdog(session: ProxySession) {
        session.watchdogJob?.cancel()
        session.watchdogJob = CoroutineScope(Dispatchers.IO).launch {
            while (isActive) {
                delay(15_000)
                if (System.currentTimeMillis() - session.lastActivity > PROXY_IDLE_TIMEOUT_MS) {
                    AppLogger.i("$TAG: Proxy session for ${session.domain} idle for ${PROXY_IDLE_TIMEOUT_MS / 1000}s. Auto-closing.")
                    closeSession(session)
                    break
                }
            }
        }
    }

    /**
     * Make an HTTP request through the browser's Chromium network stack.
     * Sends Runtime.evaluate with a fetch() call over the persistent CDP WebSocket.
     * The browser handles TLS, cookies, and User-Agent natively.
     */
    suspend fun fetchViaProxy(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        isBinary: Boolean = false,
    ): CdpFetchResult? {
        val uri = try { URI(url) } catch (_: Exception) { null } ?: return null
        val host = uri.host ?: return null
        val session = getSessionForHost(host) ?: return null
        if (!session.process.isAlive) {
            AppLogger.e("$TAG: Cannot fetch via proxy — browser process has terminated for $host.")
            closeSession(session)
            return null
        }
        val ws = session.webSocket ?: return null
        session.lastActivity = System.currentTimeMillis()

        val msgId = session.messageId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        session.pendingFetches[msgId] = deferred

        // Never forward browser-controlled or fingerprint-sensitive headers to fetch().
        // The browser's native Chromium engine manages these automatically to match its TLS session.
        val forbiddenHeaders = setOf(
            "user-agent", "cookie", "host", "content-length", "transfer-encoding",
            "connection", "accept-encoding", "referer", "origin",
            "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform",
            "sec-fetch-site", "sec-fetch-mode", "sec-fetch-user", "sec-fetch-dest"
        )
        val filteredHeaders = headers.filterKeys {
            it.lowercase() !in forbiddenHeaders
        }

        var targetFetchUrl = url
        val settledHost = session.settledDomain
        if (!settledHost.isNullOrBlank() && host.equals(session.domain, ignoreCase = true) && !host.equals(settledHost, ignoreCase = true)) {
            val newUri = URI(uri.scheme, uri.userInfo, settledHost, uri.port, uri.path, uri.query, uri.fragment)
            targetFetchUrl = newUri.toString()
            AppLogger.d("$TAG: Rewrote fetch URL origin from $host to settled domain $settledHost: $targetFetchUrl")
        }

        val requestPayload = mapper.writeValueAsString(mapOf(
            "url" to targetFetchUrl,
            "method" to method,
            "headers" to filteredHeaders,
            "body" to body,
        ))
        val b64 = java.util.Base64.getEncoder().encodeToString(requestPayload.toByteArray())

        val expression = if (isBinary) {
            """(async()=>{try{const req=JSON.parse(atob("$b64"));const opts={method:req.method,headers:req.headers||{},credentials:"include"};if(req.body)opts.body=req.body;const r=await fetch(req.url,opts);const buf=await r.arrayBuffer();const bytes=new Uint8Array(buf);let bin='';const chunk=8192;for(let i=0;i<bytes.length;i+=chunk){bin+=String.fromCharCode.apply(null,bytes.subarray(i,i+chunk));}return JSON.stringify({s:r.status,ct:r.headers.get("content-type")||"",b64:btoa(bin)})}catch(e){return JSON.stringify({s:0,ct:"",b64:"",err:(e&&e.stack)?e.stack:""+e})}})()"""
        } else {
            """(async()=>{try{const req=JSON.parse(atob("$b64"));const opts={method:req.method,headers:req.headers||{},credentials:"include"};if(req.body)opts.body=req.body;const r=await fetch(req.url,opts);const t=await r.text();return JSON.stringify({s:r.status,ct:r.headers.get("content-type")||"",b:t})}catch(e){return JSON.stringify({s:0,ct:"",b:(e&&e.stack)?e.stack:""+e})}})()"""
        }

        val command = mapper.writeValueAsString(mapOf(
            "id" to msgId,
            "method" to "Runtime.evaluate",
            "params" to mapOf(
                "expression" to expression,
                "awaitPromise" to true,
                "returnByValue" to true,
            ),
        ))

        ws.send(command)

        return try {
            val resultJson = withTimeout(30_000) { deferred.await() } ?: return null
            val resultTree = mapper.readTree(resultJson)
            val statusCode = resultTree.get("s")?.asInt() ?: 0
            val contentType = resultTree.get("ct")?.asText()?.ifBlank { null }
            if (statusCode == 0) {
                val err = resultTree.get("err")?.asText() ?: resultTree.get("b")?.asText().orEmpty()
                AppLogger.e("$TAG: fetchViaProxy error (HTTP 0) for $url: $err")
                return null
            }
            if (isBinary) {
                val b64Data = resultTree.get("b64")?.asText().orEmpty()
                val bytes = try {
                    java.util.Base64.getDecoder().decode(b64Data)
                } catch (_: Exception) {
                    ByteArray(0)
                }
                AppLogger.i("$TAG: fetchViaProxy binary HTTP $statusCode ($contentType), size: ${bytes.size} bytes")
                CdpFetchResult(
                    statusCode = statusCode,
                    contentType = contentType,
                    body = "",
                    bodyBytes = bytes,
                )
            } else {
                val bodyText = resultTree.get("b")?.asText() ?: ""
                val preview = bodyText.take(300).replace("\r", " ").replace("\n", " ")
                AppLogger.i("$TAG: fetchViaProxy HTTP $statusCode ($contentType), body preview: $preview")
                CdpFetchResult(
                    statusCode = statusCode,
                    contentType = contentType,
                    body = bodyText,
                    bodyBytes = null,
                )
            }
        } catch (e: Exception) {
            AppLogger.e("$TAG: fetchViaProxy failed: ${e.message}")
            session.pendingFetches.remove(msgId)
            null
        }
    }

    private suspend fun evaluateJs(session: ProxySession, expression: String, timeoutMs: Long = 5000L): String? {
        val ws = session.webSocket ?: return null
        val msgId = session.messageId.incrementAndGet()
        val deferred = CompletableDeferred<String?>()
        session.pendingFetches[msgId] = deferred
        val command = mapper.writeValueAsString(mapOf(
            "id" to msgId,
            "method" to "Runtime.evaluate",
            "params" to mapOf(
                "expression" to expression,
                "awaitPromise" to true,
                "returnByValue" to true,
            ),
        ))
        ws.send(command)
        return try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (_: Exception) {
            session.pendingFetches.remove(msgId)
            null
        }
    }

    private suspend fun navigateSessionToUrl(session: ProxySession, targetUrl: String, host: String): Boolean {
        val rootUrl = if (targetUrl.startsWith("http://") || targetUrl.startsWith("https://")) {
            targetUrl
        } else {
            "https://$host/"
        }
        AppLogger.i("$TAG: Navigating active session to $rootUrl...")
        val navExpr = "(function(){ location.href = " + mapper.writeValueAsString(rootUrl) + "; return 'navigating'; })()"
        evaluateJs(session, navExpr, 3000L)

        // Poll for settlement (up to 15 seconds)
        val pollExpr = "(function(){ return (document.title || '') + ':::' + document.readyState + ':::' + location.href; })()"
        for (i in 1..30) {
            delay(500)
            val settleVal = evaluateJs(session, pollExpr, 2000L) ?: continue
            val parts = settleVal.split(":::", limit = 3)
            val title = parts.getOrNull(0)?.trim().orEmpty()
            val readyState = parts.getOrNull(1)?.trim().orEmpty()
            val currentUrl = parts.getOrNull(2)?.trim() ?: rootUrl

            val isChallenge = title.contains("Just a moment", ignoreCase = true) ||
                title.contains("Attention Required", ignoreCase = true) ||
                title.contains("Cloudflare", ignoreCase = true)
            val isSettled = !isChallenge && readyState == "complete" &&
                (title.isNotEmpty() || currentUrl.contains("/api") || currentUrl != rootUrl)
            if (isSettled) {
                AppLogger.i("$TAG: Navigated tab settled on: '$title' ($currentUrl)")
                val html = evaluateJs(session, "document.documentElement.outerHTML", 5000L)
                if (!html.isNullOrBlank()) {
                    val ua = session.userAgent
                        ?: com.lagradost.cloudstream3.network.CloudflareKiller.getSavedUserAgent(host)
                        ?: com.lagradost.cloudstream3.USER_AGENT
                    SettledPageCache.put(targetUrl, html, ua)
                    if (currentUrl != targetUrl) {
                        SettledPageCache.put(currentUrl, html, ua)
                    }
                    AppLogger.i("$TAG: Captured settled DOM HTML for $targetUrl via active session (length=${html.length})")
                    return true
                }
            }
        }
        AppLogger.w("$TAG: Active session navigation timed out or did not settle for $rootUrl")
        return false
    }

    fun launchStandaloneIsolatedBrowser(url: String) {
        val sessionDirName = "CloudStream_Sandbox_${System.currentTimeMillis()}"
        val userDataDir = File(System.getProperty("java.io.tmpdir"), sessionDirName).apply { mkdirs() }

        val edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
        val chromePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"

        val browserPath = when {
            File(edgePath).exists() -> edgePath
            File(chromePath).exists() -> chromePath
            else -> {
                AppLogger.e("$TAG: No Edge or Chrome found for standalone sandbox.")
                return
            }
        }

        AppLogger.i("$TAG: Launching standalone sandbox for $url")
        val process = ProcessBuilder(
            browserPath,
            "--app=$url",
            "--user-data-dir=${userDataDir.absolutePath}",
            "--window-size=1280,720",
            "--block-new-web-contents",
            "--disable-popup-blocking=false",
            "--disable-extensions",
            "--disable-component-extensions-with-background-pages",
            "--disable-background-networking",
            "--disable-sync",
            "--no-default-browser-check",
            "--no-first-run",
        ).start()

        Thread {
            try {
                process.waitFor()
                Thread.sleep(2000)
                userDataDir.deleteRecursively()
                AppLogger.i("$TAG: Standalone sandbox closed, cleaned up $sessionDirName")
            } catch (e: Exception) {
                AppLogger.e("$TAG: Error cleaning up standalone sandbox: ${e.message}")
            }
        }.start()
    }
}
