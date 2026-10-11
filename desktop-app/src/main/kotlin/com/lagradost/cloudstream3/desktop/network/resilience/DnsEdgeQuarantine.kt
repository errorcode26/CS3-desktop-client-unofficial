package com.lagradost.cloudstream3.desktop.network.resilience

import com.lagradost.common.logging.AppLogger
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.Protocol
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Proxy
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap

/**
 * Resilient DNS edge management. Quarantines dead CDN edge IPs that reset TCP/TLS handshakes
 * and rapidly re-routes requests to healthy alternative IP addresses returned by DNS.
 */
object DnsEdgeQuarantine {
    private const val TAG = "DnsEdgeQuarantine"
    private const val QUARANTINE_DURATION_MS = 600_000L // 10 minutes

    private val quarantinedIps = ConcurrentHashMap<String, Long>()

    private fun makeKey(host: String, address: InetAddress): String =
        "${host.lowercase()}|${address.hostAddress}"

    fun isQuarantined(host: String, address: InetAddress): Boolean {
        val key = makeKey(host, address)
        val expiry = quarantinedIps[key] ?: return false
        if (System.currentTimeMillis() > expiry) {
            quarantinedIps.remove(key)
            return false
        }
        return true
    }

    fun markFailed(host: String, address: InetAddress) {
        if (quarantinedIps.size > 1000) quarantinedIps.clear()
        quarantinedIps[makeKey(host, address)] = System.currentTimeMillis() + QUARANTINE_DURATION_MS
        AppLogger.w("$TAG: Quarantined failing CDN edge address ${address.hostAddress} for host $host.")
    }

    fun markSuccess(host: String, address: InetAddress) {
        quarantinedIps.remove(makeKey(host, address))
    }

    fun isTransientNetworkFailure(e: IOException): Boolean {
        if (e is ConnectException || e is NoRouteToHostException) return true
        val msg = e.message?.lowercase() ?: ""
        return e is SocketException && (
            msg.contains("reset") ||
            msg.contains("refused") ||
            msg.contains("closed") ||
            msg.contains("broken pipe") ||
            msg.contains("unreachable") ||
            msg.contains("aborted")
        )
    }

    val eventListenerFactory = EventListener.Factory {
        object : EventListener() {
            override fun connectFailed(
                call: Call,
                inetSocketAddress: InetSocketAddress,
                proxy: Proxy,
                protocol: Protocol?,
                ioe: IOException,
            ) {
                val addr = inetSocketAddress.address ?: return
                if (isTransientNetworkFailure(ioe)) {
                    markFailed(call.request().url.host, addr)
                }
            }

            override fun connectEnd(
                call: Call,
                inetSocketAddress: InetSocketAddress,
                proxy: Proxy,
                protocol: Protocol?,
            ) {
                val addr = inetSocketAddress.address ?: return
                markSuccess(call.request().url.host, addr)
            }
        }
    }

    fun wrapDns(upstream: Dns): Dns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val addresses = upstream.lookup(hostname)
            if (addresses.size <= 1) return addresses

            val healthy = mutableListOf<InetAddress>()
            val bad = mutableListOf<InetAddress>()

            for (addr in addresses) {
                if (isQuarantined(hostname, addr)) {
                    bad.add(addr)
                } else {
                    healthy.add(addr)
                }
            }

            return if (healthy.isNotEmpty()) healthy + bad else addresses
        }
    }

    object EdgeRetryInterceptor : Interceptor {
        private const val MAX_RETRIES = 3
        private const val QUICK_FAIL_LIMIT_NS = 3_000_000_000L // 3 seconds

        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            if (request.method != "GET" && request.method != "HEAD") {
                return chain.proceed(request)
            }

            var attempt = 0
            while (true) {
                val startNs = System.nanoTime()
                try {
                    return chain.proceed(request)
                } catch (e: IOException) {
                    attempt++
                    val durationNs = System.nanoTime() - startNs
                    val canRetry = attempt < MAX_RETRIES &&
                        !chain.call().isCanceled() &&
                        durationNs < QUICK_FAIL_LIMIT_NS &&
                        isTransientNetworkFailure(e)

                    if (!canRetry) {
                        throw e
                    }
                    AppLogger.d("$TAG: Re-routing failed request ($attempt/$MAX_RETRIES) for ${request.url.host} to alternative edge IP...")
                }
            }
        }
    }
}
