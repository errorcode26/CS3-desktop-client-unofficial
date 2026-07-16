package com.lagradost.cloudstream3.desktop.network

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.utils.NativeBrowserManager
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import java.io.File
import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

object CdpResolverImpl {

    private val mutex = Mutex()
    private val mapper: ObjectMapper = jacksonObjectMapper()

    suspend fun resolve(request: Request, requestCallBack: (Request) -> Boolean): Pair<Request?, List<Request>> = withContext(Dispatchers.IO) {
        val executable = NativeBrowserManager.systemBrowserExecutable.value
        if (executable == null || !executable.exists()) {
            AppLogger.e("CdpResolver: No supported browser found (Edge/Chrome).")
            throw UnsupportedOperationException("WEBVIEW_NOT_INSTALLED")
        }

        mutex.withLock {
            var process: Process? = null
            val tempDir = Files.createTempDirectory("cloudstream-cdp").toFile()

            try {
                // Launch Browser
                val pb = ProcessBuilder(
                    executable.absolutePath,
                    "--headless",
                    "--disable-gpu",
                    "--disable-blink-features=AutomationControlled",
                    "--mute-audio",
                    "--remote-debugging-port=0",
                    "--user-data-dir=${tempDir.absolutePath}",
                )
                process = pb.start()

                // Read port from DevToolsActivePort
                val activePortFile = File(tempDir, "DevToolsActivePort")
                var port = -1
                for (i in 1..50) {
                    if (activePortFile.exists()) {
                        val lines = activePortFile.readLines()
                        if (lines.isNotEmpty()) {
                            port = lines[0].toIntOrNull() ?: -1
                            if (port != -1) break
                        }
                    }
                    delay(100)
                }

                if (port == -1) {
                    throw Exception("Failed to get remote debugging port.")
                }

                // Get WebSocket debugger URL
                val versionUrl = "http://127.0.0.1:$port/json/version"
                val versionReq = Request.Builder().url(versionUrl).build()
                val versionRes = app.baseClient.newCall(versionReq).execute()
                val versionBody = versionRes.body?.string() ?: ""
                versionRes.close()
                val browserWsUrl = mapper.readTree(versionBody).get("webSocketDebuggerUrl")?.asText()
                    ?: throw Exception("Failed to get webSocketDebuggerUrl")

                // Create Target
                val targetUrl = "http://127.0.0.1:$port/json/new?${request.url}"
                val targetReq = Request.Builder().url(targetUrl).method("PUT", RequestBody.create(null, ByteArray(0))).build()
                val targetRes = app.baseClient.newCall(targetReq).execute()
                val targetBody = targetRes.body?.string() ?: ""
                targetRes.close()

                val pageWsUrl = mapper.readTree(targetBody).get("webSocketDebuggerUrl")?.asText()
                    ?: throw Exception("Failed to get page webSocketDebuggerUrl")

                // Connect WebSocket
                val latch = CountDownLatch(1)
                var cfClearanceFound = false
                val collectedCookies = mutableListOf<Cookie>()
                var currentMessageId = AtomicInteger(1)

                val wsRequest = Request.Builder().url(pageWsUrl).build()
                val ws = app.baseClient.newWebSocket(
                    wsRequest,
                    object : WebSocketListener() {
                        override fun onMessage(webSocket: WebSocket, text: String) {
                            try {
                                val node = mapper.readTree(text)
                                // If response to Network.getCookies
                                if (node.has("id") && node.has("result")) {
                                    val result = node.get("result")
                                    if (result.has("cookies")) {
                                        val cookiesArr = result.get("cookies")
                                        var hasClearance = false
                                        collectedCookies.clear()
                                        for (c in cookiesArr) {
                                            val name = c.get("name").asText()
                                            val value = c.get("value").asText()
                                            val domain = c.get("domain").asText().removePrefix(".")
                                            val path = c.get("path").asText()
                                            val secure = c.get("secure").asBoolean()
                                            val httpOnly = c.get("httpOnly").asBoolean()

                                            val cb = Cookie.Builder()
                                                .name(name)
                                                .value(value)
                                                .domain(domain)
                                                .path(path)
                                            if (secure) cb.secure()
                                            if (httpOnly) cb.httpOnly()

                                            collectedCookies.add(cb.build())

                                            if (name == "cf_clearance") {
                                                hasClearance = true
                                            }
                                        }
                                        if (hasClearance) {
                                            cfClearanceFound = true
                                            latch.countDown()
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                AppLogger.e("CdpResolver: Error parsing WebSocket message", e)
                            }
                        }
                    },
                )

                // Enable Network
                ws.send("""{"id":${currentMessageId.getAndIncrement()},"method":"Network.enable"}""")

                // Poll for cookies
                for (i in 1..15) {
                    if (cfClearanceFound) break
                    ws.send("""{"id":${currentMessageId.getAndIncrement()},"method":"Network.getCookies"}""")
                    delay(1000)
                }

                ws.close(1000, "Done")

                // Save cookies to CookieJar
                if (collectedCookies.isNotEmpty()) {
                    app.baseClient.cookieJar.saveFromResponse(request.url, collectedCookies)
                }

                val cookieString = collectedCookies.joinToString("; ") { "${it.name}=${it.value}" }
                val finalOkHttpRequest = request.newBuilder()
                    .header("cookie", cookieString)
                    .build()

                return@withContext finalOkHttpRequest to emptyList()
            } catch (e: Exception) {
                AppLogger.e("CdpResolver resolution failed", e)
                return@withContext null to emptyList()
            } finally {
                // Instantly kill process to prevent zombie browsers
                try {
                    process?.destroyForcibly()
                } catch (e: Exception) {}

                // Cleanup temp dir
                try {
                    tempDir.deleteRecursively()
                } catch (e: Exception) {}
            }
        }
    }
}
