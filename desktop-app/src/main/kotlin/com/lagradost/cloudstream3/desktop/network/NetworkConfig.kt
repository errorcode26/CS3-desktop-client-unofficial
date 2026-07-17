@file:OptIn(com.lagradost.cloudstream3.Prerelease::class, com.lagradost.cloudstream3.UnsafeSSL::class)

package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.insecureApp
import com.lagradost.cloudstream3.network.CloudflareKiller
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.nicehttp.ignoreAllSSLErrors
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.net.ssl.*

enum class DohProvider(val title: String) {
    NONE("Off (System Default)"),
    GOOGLE("Google"),
    CLOUDFLARE("Cloudflare"),
    ADGUARD("AdGuard"),
    QUAD9("Quad9"),
    DNSWATCH("DNSWatch"),
    DNSSB("DNS.SB"),
    CANADIAN_SHIELD("Canadian Shield"),
}

/**
 * Named interceptor class so it can be identified and deduplicated
 * when updateGlobalNetworkClients() is called multiple times (e.g. on DoH switch).
 */
class TmdbMirrorInterceptor : okhttp3.Interceptor {
    
    // A lazy client dedicated to TMDB that uses HTTP/1.1 to bypass the HTTP/2 network hang.
    // We clone the baseClient but remove this interceptor to avoid an infinite loop.
    private val http11Client by lazy {
        val builder = com.lagradost.cloudstream3.app.baseClient.newBuilder()
            .protocols(listOf(okhttp3.Protocol.HTTP_1_1))
        
        // Remove this interceptor from the clone so we don't infinitely recurse
        val interceptors = builder.interceptors()
        val toRemove = interceptors.filterIsInstance<TmdbMirrorInterceptor>()
        toRemove.forEach { builder.interceptors().remove(it) }
        
        builder.build()
    }

    override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response {
        var request = chain.request()

        // Redirect TMDB requests. Treat "api.themoviedb.org" itself as the blocked origin
        // and always fall back to "api.tmdb.org" unless the user set a custom mirror.
        val host = request.url.host
        if (host == "api.themoviedb.org" || host == "api.tmdb.org") {
            val saved = DesktopDataStore.getKey<String>(NetworkConfig.PREF_TMDB_API_MIRROR)
            val mirror = saved
                ?.takeIf { it.isNotBlank() && it != "api.themoviedb.org" }
                ?: "api.tmdb.org"
            val newUrl = request.url.newBuilder().host(mirror).build()
            request = request.newBuilder().url(newUrl).build()
            
            AppLogger.d("-> [HTTP/1.1 Fallback] ${request.method} ${request.url}")
            return try {
                // Execute using the dedicated HTTP/1.1 client instead of the chain
                val response = http11Client.newCall(request).execute()
                AppLogger.d("<- [HTTP/1.1 Fallback] ${response.code} ${request.url}")
                response
            } catch (e: Exception) {
                AppLogger.d("<- ERROR [HTTP/1.1 Fallback] ${request.url} : ${e.message}")
                throw e
            }
        }

        AppLogger.d("-> ${request.method} ${request.url}")
        request.headers.forEach { (name, value) -> AppLogger.d("   H: $name: $value") }
        return try {
            val response = chain.proceed(request)
            AppLogger.d("<- ${response.code} ${request.url}")
            response
        } catch (e: Exception) {
            AppLogger.d("<- ERROR ${request.url} : ${e.message}")
            throw e
        }
    }
}

object NetworkConfig {
    const val PREF_DOH_PROVIDER = "doh_provider"
    const val PREF_TMDB_API_MIRROR = "tmdb_api_mirror"

    /**
     * Rebuilds and assigns the global NiceHttp clients (`app.baseClient` and `insecureApp.baseClient`)
     * using the current DNS over HTTPS configuration from the DesktopDataStore.
     */
    fun updateGlobalNetworkClients() {
        val providerIndex = DesktopDataStore.getKey<Int>(PREF_DOH_PROVIDER) ?: 0
        val provider = DohProvider.values().getOrNull(providerIndex) ?: DohProvider.NONE

        val baseBuilder = app.baseClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .cookieJar(DesktopCookieJar())

        // Apply DoH Provider
        when (provider) {
            DohProvider.GOOGLE -> baseBuilder.addGoogleDns()
            DohProvider.CLOUDFLARE -> baseBuilder.addCloudFlareDns()
            DohProvider.ADGUARD -> baseBuilder.addAdGuardDns()
            DohProvider.QUAD9 -> baseBuilder.addQuad9Dns()
            DohProvider.DNSWATCH -> baseBuilder.addDNSWatchDns()
            DohProvider.DNSSB -> baseBuilder.addDnsSbDns()
            DohProvider.CANADIAN_SHIELD -> baseBuilder.addCanadianShieldDns()
            DohProvider.NONE -> { /* Use System DNS */ }
        }

        // Apply CloudflareKiller interceptor only if not already present
        val hasCloudflareKiller = baseBuilder.interceptors().any { it is CloudflareKiller }
        if (!hasCloudflareKiller) {
            baseBuilder.addInterceptor(CloudflareKiller())
        }

        // CRITICAL: Strip all IPv6 addresses from DNS responses.
        // Windows frequently advertises IPv6 capability but many ISPs/routers silently
        // blackhole IPv6 traffic, causing OkHttp's Happy Eyeballs to hang for 30+ seconds
        // on many hosts before falling back to IPv4.
        try {
            val upstreamDns = baseBuilder.build().dns
            baseBuilder.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    val addresses = upstreamDns.lookup(hostname)
                    val ipv4Only = addresses.filter { it is java.net.Inet4Address }
                    return ipv4Only.ifEmpty { addresses }
                }
            })
        } catch (e: Exception) {
            AppLogger.e("Failed to configure IPv4-only DNS: ${e.message}", e)
        }

        // Add TMDB mirror interceptor only if not already present (prevents stacking on reload)
        val hasTmdbMirror = baseBuilder.interceptors().any { it is TmdbMirrorInterceptor }
        if (!hasTmdbMirror) {
            baseBuilder.addInterceptor(TmdbMirrorInterceptor())
        }

        // Apply to main client
        app.baseClient = baseBuilder.build()
        // CRITICAL: Restore defaultHeaders that NiceHttp uses for ALL requests.
        // Without this, OkHttp sends 'okhttp/4.x' as User-Agent which Cloudflare blocks.
        app.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        // Apply to insecure client
        val insecureBuilder = app.baseClient.newBuilder()
        try {
            insecureBuilder.ignoreAllSSLErrors()
        } catch (e: Exception) {
            AppLogger.e("Failed to apply insecure SSL bypass: ${e.message}", e)
        }
        insecureApp.baseClient = insecureBuilder.build()
        insecureApp.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        java.util.logging.Logger.getLogger(OkHttpClient::class.java.name).level = java.util.logging.Level.ALL
        java.util.logging.Logger.getLogger(okhttp3.internal.platform.Platform::class.java.name).level = java.util.logging.Level.ALL

        AppLogger.i("Initialized global NiceHttp clients with DoH Provider: ${provider.title}")
    }
}
