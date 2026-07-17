package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.app
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import java.net.InetAddress
import java.util.concurrent.TimeUnit

/**
 * Data class holding the result of a single diagnostic test.
 */
data class DiagnosticResult(
    val name: String,
    val passed: Boolean,
    val timeMs: Long,
    val detail: String,
)

/**
 * Runs network diagnostic tests to help users identify connectivity issues.
 * Each test has a 5-second timeout and returns a [DiagnosticResult].
 */
object DiagnosticsRunner {

    private const val TIMEOUT_MS = 5000L

    /**
     * Runs all diagnostic tests sequentially and reports results via the callback.
     * The callback is invoked once per test completion so the UI can update in real time.
     */
    suspend fun runAll(onResult: (DiagnosticResult) -> Unit) {
        onResult(testInternet())
        onResult(testDnsResolution())
        onResult(testDohProvider())
        onResult(testTmdbHttp11())
        onResult(testTmdbHttp2())
        onResult(testTmdbImages())
        onResult(testGithubRaw())
        testPluginEndpoints().forEach { onResult(it) }
    }

    /** Test 1: Basic internet connectivity via google.com */
    suspend fun testInternet(): DiagnosticResult = runTest("Internet Connectivity") {
        val client = buildClient()
        val request = Request.Builder().url("https://www.google.com").head().build()
        val response = client.newCall(request).execute()
        response.close()
        "HTTP ${response.code}"
    }

    /** Test 2: System DNS resolution for api.tmdb.org */
    suspend fun testDnsResolution(): DiagnosticResult = runTest("DNS Resolution") {
        val addresses = InetAddress.getAllByName("api.tmdb.org")
        "Resolved to ${addresses.joinToString { it.hostAddress ?: "?" }}"
    }

    /** Test 3: DoH provider health check */
    suspend fun testDohProvider(): DiagnosticResult {
        val providerIndex = DesktopDataStore.getKey<Int>(NetworkConfig.PREF_DOH_PROVIDER) ?: 0
        val provider = DohProvider.values().getOrNull(providerIndex) ?: DohProvider.NONE
        val testName = "DoH Provider (${provider.title})"

        if (provider == DohProvider.NONE) {
            return DiagnosticResult(testName, true, 0, "System DNS — no DoH configured")
        }

        return runTest(testName) {
            // Use the app's configured client (which has DoH) to resolve a known host
            val client = app.baseClient.newBuilder()
                .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
                .build()
            val request = Request.Builder().url("https://api.tmdb.org/3/").head().build()
            val response = client.newCall(request).execute()
            response.close()
            "DoH resolved + connected (HTTP ${response.code})"
        }
    }

    /** Test 4: TMDB API over HTTP/1.1 (our workaround protocol) */
    suspend fun testTmdbHttp11(): DiagnosticResult = runTest("TMDB API (HTTP/1.1)") {
        val client = buildClient(protocols = listOf(Protocol.HTTP_1_1))
        val request = Request.Builder()
            .url("https://api.tmdb.org/3/configuration?api_key=d56e51fb77b081a9cb5192571b7c679d")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val body = response.body?.string()?.take(80) ?: ""
        response.close()
        "HTTP ${response.code} — ${body.take(60)}..."
    }

    /** Test 5: TMDB API over HTTP/2 (detects ISP blocking) */
    suspend fun testTmdbHttp2(): DiagnosticResult = runTest("TMDB API (HTTP/2)") {
        val client = buildClient(protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
        val request = Request.Builder()
            .url("https://api.tmdb.org/3/configuration?api_key=d56e51fb77b081a9cb5192571b7c679d")
            .get()
            .build()
        val response = client.newCall(request).execute()
        val proto = response.protocol
        val body = response.body?.string()?.take(80) ?: ""
        response.close()
        "Protocol: $proto — HTTP ${response.code} — ${body.take(40)}..."
    }

    /** Test 6: TMDB image CDN */
    suspend fun testTmdbImages(): DiagnosticResult = runTest("TMDB Images") {
        val client = buildClient()
        // Small known poster path
        val request = Request.Builder()
            .url("https://image.tmdb.org/t/p/w92/wwemzKWzjKYJFfCeiB57q3r4Bcm.png")
            .head()
            .build()
        val response = client.newCall(request).execute()
        val contentLength = response.header("Content-Length") ?: "unknown"
        response.close()
        "HTTP ${response.code} — Size: ${contentLength} bytes"
    }

    /** Test 7: GitHub raw (needed for plugin repos) */
    suspend fun testGithubRaw(): DiagnosticResult = runTest("GitHub Raw") {
        val client = buildClient()
        val request = Request.Builder()
            .url("https://raw.githubusercontent.com/nicehash/NiceHashQuickMiner/main/README.md")
            .head()
            .build()
        val response = client.newCall(request).execute()
        response.close()
        "HTTP ${response.code}"
    }

    /** Test 8: Loaded plugin base URLs (top 5) */
    suspend fun testPluginEndpoints(): List<DiagnosticResult> {
        val results = mutableListOf<DiagnosticResult>()

        try {
            val apis = com.lagradost.cloudstream3.APIHolder.apis
            val uniqueApis = apis
                .filter { it.mainUrl.startsWith("http") }
                .distinctBy { it.mainUrl }
                .take(5)

            for (api in uniqueApis) {
                val result = runTest("Plugin: ${api.name}") {
                    val client = buildClient()
                    val request = Request.Builder()
                        .url(api.mainUrl)
                        .head()
                        .build()
                    val response = client.newCall(request).execute()
                    response.close()
                    "HTTP ${response.code} — ${api.mainUrl}"
                }
                results.add(result)
            }
        } catch (e: Exception) {
            results.add(DiagnosticResult("Plugin Endpoints", false, 0, "Error: ${e.message}"))
        }

        return results
    }

    /**
     * Formats all results into a clean, shareable plain-text report.
     */
    fun formatReport(results: List<DiagnosticResult>): String {
        val providerIndex = DesktopDataStore.getKey<Int>(NetworkConfig.PREF_DOH_PROVIDER) ?: 0
        val provider = DohProvider.values().getOrNull(providerIndex) ?: DohProvider.NONE

        val sb = StringBuilder()
        sb.appendLine("CloudStream Desktop — Network Diagnostics")
        sb.appendLine("Date: ${java.time.LocalDateTime.now()}")
        sb.appendLine("DoH: ${provider.title}")
        sb.appendLine("─".repeat(50))

        for (r in results) {
            val status = if (r.passed) "PASS" else "FAIL"
            val time = if (r.timeMs > 0) "${r.timeMs}ms" else "--"
            sb.appendLine("[${status}]  ${r.name.padEnd(28)} ${time.padStart(7)}  ${r.detail}")
        }

        sb.appendLine("─".repeat(50))
        return sb.toString()
    }

    // ── Internal helpers ──

    private fun buildClient(
        protocols: List<Protocol> = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1),
    ): OkHttpClient {
        return OkHttpClient.Builder()
            .protocols(protocols)
            .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .writeTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .callTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private suspend fun runTest(name: String, block: suspend () -> String): DiagnosticResult {
        return withContext(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            try {
                val result = withTimeoutOrNull(TIMEOUT_MS) {
                    block()
                }
                val elapsed = System.currentTimeMillis() - start

                if (result != null) {
                    AppLogger.i("[Diagnostics] PASS: $name (${elapsed}ms) — $result")
                    DiagnosticResult(name, true, elapsed, result)
                } else {
                    AppLogger.w("[Diagnostics] FAIL: $name — TIMEOUT after ${elapsed}ms")
                    DiagnosticResult(name, false, elapsed, "TIMEOUT after ${elapsed}ms")
                }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - start
                val msg = e.message ?: e.javaClass.simpleName
                AppLogger.w("[Diagnostics] FAIL: $name — $msg")
                DiagnosticResult(name, false, elapsed, msg)
            }
        }
    }
}
