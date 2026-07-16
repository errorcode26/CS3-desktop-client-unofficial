package com.lagradost.cloudstream3.network

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import com.lagradost.nicehttp.Requests.Companion.await
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class CloudflareKiller : Interceptor {
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

        // If we already have cookies for this host, use them directly (skip the initial request)
        if (savedCookies.containsKey(host)) {
            return proceed(chain, request, savedCookies[host] ?: emptyMap(), savedUserAgents[host])
        }

        // If this host already failed bypass, don't waste time — just proceed normally
        if (failedHosts.contains(host)) {
            return chain.proceed(request)
        }

        val response = chain.proceed(request)
        val serverHeader = response.header("Server") ?: ""
        val contentType = response.header("Content-Type") ?: ""

        // Only trigger Cloudflare bypass if ALL conditions are met:
        // 1. Response code is 403 or 503
        // 2. Server header contains "cloudflare"
        // 3. Content-Type is HTML (Cloudflare challenges are HTML pages, not JSON APIs)
        val isCloudflareChallenge = response.code in ERROR_CODES &&
            CLOUDFLARE_SERVERS.any { serverHeader.contains(it, ignoreCase = true) } &&
            contentType.contains("text/html", ignoreCase = true)

        if (!isCloudflareChallenge) {
            return response
        }

        response.close()

        // Don't launch Playwright if another thread is already resolving this host
        if (!resolvingHosts.add(host)) {
            AppLogger.d("$TAG: Already resolving $host, skipping duplicate")
            // Make a fresh request instead of returning closed response
            return chain.proceed(request)
        }

        // Only use runBlocking for the actual Cloudflare bypass (rare path)
        try {
            val bypassed = runBlocking {
                bypassCloudflare(chain, request)
            }
            if (bypassed != null) {
                return bypassed
            }
        } finally {
            resolvingHosts.remove(host)
        }

        // Bypass failed — remember this host to avoid retrying
        failedHosts.add(host)
        AppLogger.w("$TAG: Failed to bypass Cloudflare for $host (will not retry this session)")

        // Make a FRESH request since the original response was closed
        return chain.proceed(request)
    }

    private fun proceed(chain: Interceptor.Chain, request: Request, cookies: Map<String, String>, userAgent: String?): Response {
        val builder = request.newBuilder()
        if (userAgent != null) {
            builder.header("user-agent", userAgent)
        }

        val existingCookies = request.header("cookie")?.let { parseCookieMap(it) } ?: emptyMap()
        val finalCookies = cookies + existingCookies
        if (finalCookies.isNotEmpty()) {
            builder.header("cookie", finalCookies.entries.joinToString("; ") { "${it.key}=${it.value}" })
        }

        return chain.proceed(builder.build())
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
