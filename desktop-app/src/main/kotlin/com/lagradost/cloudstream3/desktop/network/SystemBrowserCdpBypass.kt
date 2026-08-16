package com.lagradost.cloudstream3.desktop.network

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

object SystemBrowserCdpBypass {
    private const val TAG = "SystemBrowserCdpBypass"
    private val mapper = jacksonObjectMapper()
    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val bypassMutex = Mutex()
    private var isBrowserOpen = false

    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    data class CdpTarget(
        val id: String,
        val type: String,
        val url: String,
        @com.fasterxml.jackson.annotation.JsonProperty("webSocketDebuggerUrl") val webSocketDebuggerUrl: String? = null,
    )

    data class ExtractedData(
        val cookies: Map<String, String>,
        val userAgent: String,
        val responseBody: String? = null,
    )

    suspend fun resolveCloudflare(url: String): ExtractedData? = bypassMutex.withLock {
        if (isBrowserOpen) return null
        isBrowserOpen = true

        val port = (9222..9999).random()
        val sessionDirName = "CloudStream_CF_${System.currentTimeMillis()}"
        val userDataDir = File(System.getProperty("java.io.tmpdir"), sessionDirName).apply { mkdirs() }

        val edgePath = "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe"
        val chromePath = "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"

        val browserPath = when {
            File(edgePath).exists() -> edgePath
            File(chromePath).exists() -> chromePath
            else -> {
                AppLogger.e("$TAG: No Edge or Chrome found on standard paths.")
                isBrowserOpen = false
                return null
            }
        }

        AppLogger.i("$TAG: Launching browser $browserPath on port $port")
        val process = ProcessBuilder(
            browserPath,
            "--app=$url",
            "--user-data-dir=${userDataDir.absolutePath}",
            "--remote-debugging-port=$port",
            "--remote-allow-origins=*",
            "--window-size=600,750",
            "--block-new-web-contents", // Strictly block all popups/new tabs (even on click)
            "--disable-popup-blocking=false",
            "--disable-extensions", // Prevent global extensions from opening welcome tabs
            "--disable-component-extensions-with-background-pages",
            "--disable-background-networking",
            "--disable-sync",
            "--no-default-browser-check",
            "--no-first-run",
        ).start()

        try {
            return waitForClearance(port, url.contains("/api"))
        } finally {
            // Kill the process and any descendants safely
            runCatching { process.destroy() }

            // Because Edge forks and the parent exits, we must use WMI to kill the actual renderer/browser processes
            runCatching {
                val script = "Get-CimInstance Win32_Process -Filter \"Name = 'msedge.exe' OR Name = 'chrome.exe'\" | Where-Object { \$_.CommandLine -match '$sessionDirName' } | Invoke-CimMethod -MethodName Terminate"
                ProcessBuilder("powershell", "-NoProfile", "-Command", script).start().waitFor()
            }

            // Give OS a moment to release file locks, then clean up the 200MB profile dir
            runCatching {
                Thread.sleep(1000)
                userDataDir.deleteRecursively()
            }

            isBrowserOpen = false
        }
    }

    private suspend fun waitForClearance(port: Int, isApiRequest: Boolean): ExtractedData? {
        var wsUrl: String? = null
        for (i in 1..40) { // Wait up to 20 seconds
            delay(500)
            try {
                val req = Request.Builder().url("http://127.0.0.1:$port/json").build()
                client.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val targets = mapper.readValue<List<CdpTarget>>(resp.body!!.string())
                        val pageTarget = targets.find { it.type == "page" }
                        if (pageTarget?.webSocketDebuggerUrl != null) {
                            wsUrl = pageTarget.webSocketDebuggerUrl
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.d("$TAG: CDP probe error on port $port: ${e.message}")
            }
        }

        if (wsUrl == null) {
            AppLogger.e("$TAG: Failed to connect to CDP at port $port")
            return null
        }

        return suspendCancellableCoroutine { cont ->
            var resumed = false
            var tempCookies: Map<String, String>? = null
            var tempUserAgent: String? = null

            val wsReq = Request.Builder().url(wsUrl!!).build()
            val webSocket = client.newWebSocket(
                wsReq,
                object : WebSocketListener() {
                    var messageId = 1

                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        CoroutineScope(Dispatchers.IO).launch {
                            while (!resumed && isActive) {
                                val msg = """{"id": $messageId, "method": "Network.getAllCookies"}"""
                                webSocket.send(msg)
                                messageId++
                                delay(1000)
                            }
                        }
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        try {
                            val tree = mapper.readTree(text)

                            // Handle Runtime.evaluate response (body extraction)
                            if (tree.has("id") && tree.get("id").asInt() == 8888) {
                                if (!resumed && tempCookies != null && tempUserAgent != null) {
                                    resumed = true
                                    val bodyText = tree.get("result")?.get("result")?.get("value")?.asText()
                                    AppLogger.i("$TAG: Captured cf_clearance, User-Agent, and API response body.")
                                    cont.resume(ExtractedData(tempCookies!!, tempUserAgent!!, bodyText))
                                    webSocket.close(1000, "Done")
                                }
                                return
                            }

                            // Handle Browser.getVersion response
                            if (tree.has("id") && tree.get("id").asInt() == 9999) {
                                if (!resumed) {
                                    val ua = tree.get("result")?.get("userAgent")?.asText() ?: ""
                                    tempUserAgent = ua
                                    if (isApiRequest) {
                                        // It's an API request. Evaluate document.body.innerText to get the JSON!
                                        val evalMsg = """{"id": 8888, "method": "Runtime.evaluate", "params": {"expression": "document.body.innerText"}}"""
                                        webSocket.send(evalMsg)
                                    } else {
                                        // Normal request, no need for body
                                        resumed = true
                                        AppLogger.i("$TAG: Captured cf_clearance and User-Agent: $ua")
                                        cont.resume(ExtractedData(tempCookies ?: emptyMap(), ua))
                                        webSocket.close(1000, "Done")
                                    }
                                }
                                return
                            }

                            // Handle Network.getAllCookies response
                            if (tree.has("id") && tree.has("result")) {
                                val cookiesNode = tree.get("result").get("cookies")
                                if (cookiesNode != null && cookiesNode.isArray) {
                                    val cookiesMap = mutableMapOf<String, String>()
                                    var hasClearance = false
                                    for (cookie in cookiesNode) {
                                        val name = cookie.get("name").asText()
                                        val value = cookie.get("value").asText()
                                        cookiesMap[name] = value
                                        if (name == "cf_clearance" && value.length > 20) {
                                            hasClearance = true
                                        }
                                    }

                                    if (hasClearance && tempCookies == null) {
                                        tempCookies = cookiesMap
                                        webSocket.send("""{"id": 9999, "method": "Browser.getVersion"}""")
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            AppLogger.e("$TAG: Error parsing CDP message: ${e.message}")
                        }
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        if (!resumed) {
                            resumed = true
                            cont.resume(null)
                        }
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        if (!resumed) {
                            resumed = true
                            cont.resume(null)
                        }
                    }
                },
            )

            cont.invokeOnCancellation {
                webSocket.close(1000, "Cancelled")
            }
        }
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

        // Background thread to wait for browser to close and clean up
        Thread {
            try {
                process.waitFor()
                Thread.sleep(2000) // Give OS a moment to release locks
                userDataDir.deleteRecursively()
                AppLogger.i("$TAG: Standalone sandbox closed, cleaned up $sessionDirName")
            } catch (e: Exception) {
                AppLogger.e("$TAG: Error cleaning up standalone sandbox: ${e.message}")
            }
        }.start()
    }
}
