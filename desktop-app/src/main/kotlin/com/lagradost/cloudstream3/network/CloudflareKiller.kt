package com.lagradost.cloudstream3.network

import com.lagradost.common.logging.AppLogger
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
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
    }

    val savedCookies: MutableMap<String, Map<String, String>> = ConcurrentHashMap()
    val savedUserAgents: MutableMap<String, String> = ConcurrentHashMap()

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

        // If this host already failed bypass, don't waste time — just proceed normally
        if (failedHosts.contains(host)) {
            return chain.proceed(request)
        }

        var response: Response? = null
        var usedSavedCookie = false

        // Try with saved cookies first if we have them
        if (savedCookies.containsKey(host)) {
            usedSavedCookie = true
            response = proceed(chain, request, savedCookies[host] ?: emptyMap(), savedUserAgents[host])
        } else {
            response = chain.proceed(request)
        }

        val serverHeader = response?.header("Server") ?: ""

        val isCloudflareChallenge = response?.code in ERROR_CODES && (
            CLOUDFLARE_SERVERS.any { serverHeader.contains(it, ignoreCase = true) } ||
                response?.peekBody(2048)?.string()?.contains("Just a moment...") == true
            )

        // If we used a saved cookie and it STILL returned a challenge (or WAF block disguised as a challenge),
        // our saved cookie is invalid/expired. We must clear it and trigger bypass again!
        if (isCloudflareChallenge) {
            val isBypassAllowed = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(
                com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS,
            ) ?: false

            if (!isBypassAllowed) {
                AppLogger.e("$TAG: Cloudflare challenge detected for $host, but Experimental Cloudflare Bypass is OFF. Aborting.")
                response.close()
                failedHosts.add(host) // Prevent repeated CF detection spam for this host this session
                throw java.io.IOException("Cloudflare clearance is disabled. Enable it in Settings -> Network to access this provider.")
            }

            AppLogger.w("$TAG: Cloudflare challenge detected for $host. Triggering CDP Browser Bypass.")

            val existingCfCookie = savedCookies[host]?.get("cf_clearance")

            // Clear the invalid saved cookies so we don't get stuck in a loop if bypass fails
            if (usedSavedCookie) {
                savedCookies.remove(host)
                savedUserAgents.remove(host)
            }

            val result = synchronized(CloudflareKiller::class.java) {
                val currentCf = savedCookies[host]?.get("cf_clearance")
                if (currentCf != null && currentCf != existingCfCookie) {
                    AppLogger.i("$TAG: Another thread cleared Cloudflare for $host! Re-using token.")
                    return@synchronized com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.ExtractedData(
                        cookies = savedCookies[host]!!,
                        userAgent = savedUserAgents[host] ?: "",
                        responseBody = null,
                    )
                }

                kotlinx.coroutines.runBlocking {
                    com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.resolveCloudflare(request.url.toString())
                }
            }

            if (result != null) {
                // Only log and save if it was actually solved by THIS thread (indicated by a new bypass)
                if (savedCookies[host]?.get("cf_clearance") != result.cookies["cf_clearance"]) {
                    AppLogger.i("$TAG: Successfully cleared Cloudflare for $host")
                    savedCookies[host] = result.cookies
                    savedUserAgents[host] = result.userAgent

                    cookieJar?.let { jar ->
                        val okCookies = result.cookies.mapNotNull { (k, v) ->
                            try {
                                Cookie.Builder().domain(host).name(k).value(v).path("/").build()
                            } catch (e: Exception) {
                                null
                            }
                        }
                        jar.saveFromResponse(request.url, okCookies)
                    }
                }

                response.close() // Close the 403 response before retrying

                // If it was an API request and CDP captured the JSON response directly from Edge,
                // we can return it immediately, completely bypassing OkHttp's HTTP/2 fingerprinting!
                if (result.responseBody != null && result.responseBody.startsWith("{")) {
                    AppLogger.i("$TAG: Intercepted API response body directly from CDP. Returning mock 200 OK.")
                    return Response.Builder()
                        .request(request)
                        .protocol(okhttp3.Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK (CDP Mock)")
                        .body(okhttp3.ResponseBody.create(null, result.responseBody.toByteArray()))
                        .build()
                }

                val retryResponse = proceed(chain, request, result.cookies, result.userAgent)
                if (retryResponse.code in ERROR_CODES) {
                    AppLogger.e("$TAG: Retry STILL failed with ${retryResponse.code}. Marking host as failed to prevent further bypass attempts.")
                    retryResponse.close()
                    failedHosts.add(host)
                    throw java.io.IOException("Cloudflare Turnstile bypass failed: Desktop OkHttp blocked by WAF/JA3 fingerprint.")
                }
                return retryResponse
            } else {
                AppLogger.e("$TAG: CDP Bypass failed for $host")
                failedHosts.add(host)
            }
        }

        // Return the response directly. Do NOT close it, so the caller can read the body if needed.
        return response!!
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

            // Add WAF-bypassing headers (Sec-Fetch and Referer)
            builder.header("sec-fetch-site", "same-origin")
            builder.header("sec-fetch-mode", "cors")
            builder.header("sec-fetch-dest", "empty")
            builder.header("accept-language", "en-US,en;q=0.9")
            builder.header("referer", "https://${request.url.host}/")
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
}
