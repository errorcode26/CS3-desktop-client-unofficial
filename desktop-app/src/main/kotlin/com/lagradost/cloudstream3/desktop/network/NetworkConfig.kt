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

object NetworkConfig {
    const val PREF_DOH_PROVIDER = "doh_provider"

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
        // (since we inherit from app.baseClient.newBuilder(), a previous call may have added one)
        val hasCloudflareKiller = baseBuilder.interceptors().any { it is CloudflareKiller }
        if (!hasCloudflareKiller) {
            baseBuilder.addInterceptor(CloudflareKiller())
        }

        // CRITICAL: Strip all IPv6 addresses from DNS responses.
        // Windows frequently advertises IPv6 capability but many ISPs/routers silently
        // blackhole IPv6 traffic, causing OkHttp's Happy Eyeballs to hang for 30+ seconds
        // on hosts like HubCloud before falling back to IPv4.
        // This wraps whatever DNS is currently configured (System or DoH).
        try {
            val upstreamDns = baseBuilder.build().dns
            baseBuilder.dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    val addresses = upstreamDns.lookup(hostname)
                    val ipv4Only = addresses.filter { it is java.net.Inet4Address }
                    return ipv4Only.ifEmpty { addresses } // fallback to all if no IPv4 exists
                }
            })
        } catch (e: Exception) {
            AppLogger.e("Failed to configure IPv4-only DNS: ${e.message}", e)
        }

        baseBuilder.addInterceptor { chain ->
            val request = chain.request()
            AppLogger.d("-> ${request.method} ${request.url}")
            request.headers.forEach { (name, value) ->
                AppLogger.d("   H: $name: $value")
            }
            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                AppLogger.d("<- ERROR ${request.url} : ${e.message}")
                throw e
            }
            AppLogger.d("<- ${response.code} ${request.url}")
            response
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
        } catch (_: Exception) {}
        insecureApp.baseClient = insecureBuilder.build()
        insecureApp.defaultHeaders = mapOf("user-agent" to com.lagradost.cloudstream3.USER_AGENT)

        java.util.logging.Logger.getLogger(OkHttpClient::class.java.name).level = java.util.logging.Level.ALL
        java.util.logging.Logger.getLogger(okhttp3.internal.platform.Platform::class.java.name).level = java.util.logging.Level.ALL

        AppLogger.i("Initialized global NiceHttp clients with DoH Provider: ${provider.title}")
    }
}
