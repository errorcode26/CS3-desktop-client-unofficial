package com.lagradost.cloudstream3.desktop.network

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.dnsoverhttps.DnsOverHttps
import java.net.InetAddress
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.UnknownHostException
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit

/**
 * Based on https://github.com/tachiyomiorg/tachiyomi/blob/master/app/src/main/java/eu/kanade/tachiyomi/network/DohProviders.kt
 */

fun OkHttpClient.Builder.addGenericDns(url: String, ips: List<String>) = dns(
    DnsOverHttps
        .Builder()
        .client(build())
        .url(url.toHttpUrl())
        .bootstrapDnsHosts(
            ips.map { InetAddress.getByName(it) },
        )
        .build(),
)

fun OkHttpClient.Builder.addGoogleDns() = (
    addGenericDns(
        "https://dns.google/dns-query",
        listOf(
            "8.8.4.4",
            "8.8.8.8",
        ),
    )
    )

fun OkHttpClient.Builder.addCloudFlareDns() = (
    addGenericDns(
        "https://cloudflare-dns.com/dns-query",
        // https://www.cloudflare.com/ips/
        listOf(
            "1.1.1.1",
            "1.0.0.1",
            "2606:4700:4700::1111",
            "2606:4700:4700::1001",
        ),
    )
    )

fun OkHttpClient.Builder.addAdGuardDns() = (
    addGenericDns(
        "https://dns.adguard.com/dns-query",
        // https://github.com/AdguardTeam/AdGuardDNS
        listOf(
            // "Non-filtering"
            "94.140.14.140",
            "94.140.14.141",
        ),
    )
    )

fun OkHttpClient.Builder.addDNSWatchDns() = (
    addGenericDns(
        "https://resolver2.dns.watch/dns-query",
        // https://dns.watch/
        listOf(
            "84.200.69.80",
            "84.200.70.40",
        ),
    )
    )

fun OkHttpClient.Builder.addQuad9Dns() = (
    addGenericDns(
        "https://dns.quad9.net/dns-query",
        // https://www.quad9.net/service/service-addresses-and-features
        listOf(
            "9.9.9.9",
            "149.112.112.112",
        ),
    )
    )

fun OkHttpClient.Builder.addDnsSbDns() = (
    addGenericDns(
        "https://doh.dns.sb/dns-query",
        // https://dns.sb/guide/
        listOf(
            "185.222.222.222",
            "45.11.45.11",
        ),
    )
    )

fun normalizeCustomDnsEndpoint(input: String): String {
    val clean = input.trim()
    return when {
        clean.startsWith("https://", ignoreCase = true) || clean.startsWith("http://", ignoreCase = true) -> clean
        clean.matches(Regex("""(?i)[a-z0-9_-]+\.dns\.nextdns\.io""")) -> {
            val id = clean.substringBefore(".dns.nextdns.io")
            "https://dns.nextdns.io/$id"
        }
        else -> "https://$clean/dns-query"
    }
}

class UdpDns(private val serverIp: String, private val timeoutMs: Int = 3000) : okhttp3.Dns {
    private val serverAddress: InetAddress by lazy { InetAddress.getByName(serverIp) }

    override fun lookup(hostname: String): List<InetAddress> {
        val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
        if (ipv4Regex.matches(hostname)) {
            return listOf(InetAddress.getByName(hostname))
        }

        val socket = DatagramSocket().apply { soTimeout = timeoutMs }
        try {
            val queryBytes = buildDnsQuery(hostname)
            val sendPacket = DatagramPacket(queryBytes, queryBytes.size, serverAddress, 53)
            socket.send(sendPacket)

            val buffer = ByteArray(512)
            val receivePacket = DatagramPacket(buffer, buffer.size)
            socket.receive(receivePacket)

            val ips = parseDnsResponse(buffer, receivePacket.length)
            if (ips.isEmpty()) {
                throw UnknownHostException("No IPv4 address found for $hostname via $serverIp")
            }
            return ips
        } finally {
            socket.close()
        }
    }

    private fun buildDnsQuery(hostname: String): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeShort(0x1A2B)
        dos.writeShort(0x0100)
        dos.writeShort(1)
        dos.writeShort(0)
        dos.writeShort(0)
        dos.writeShort(0)

        val labels = hostname.split(".")
        for (label in labels) {
            val bytes = label.toByteArray(Charsets.US_ASCII)
            dos.writeByte(bytes.size)
            dos.write(bytes)
        }
        dos.writeByte(0)

        dos.writeShort(1)
        dos.writeShort(1)
        return baos.toByteArray()
    }

    private fun parseDnsResponse(data: ByteArray, length: Int): List<InetAddress> {
        if (length < 12) return emptyList()
        val buffer = ByteBuffer.wrap(data, 0, length)
        buffer.short
        val flags = buffer.short.toInt() and 0xFFFF
        if ((flags and 0x000F) != 0) return emptyList()

        val qdCount = buffer.short.toInt() and 0xFFFF
        val anCount = buffer.short.toInt() and 0xFFFF
        buffer.short
        buffer.short

        var offset = 12
        for (i in 0 until qdCount) {
            while (offset < length) {
                val len = data[offset].toInt() and 0xFF
                if (len == 0) { offset++; break }
                if ((len and 0xC0) == 0xC0) { offset += 2; break }
                offset += 1 + len
            }
            offset += 4
        }

        val result = mutableListOf<InetAddress>()
        for (i in 0 until anCount) {
            if (offset >= length) break
            while (offset < length) {
                val len = data[offset].toInt() and 0xFF
                if (len == 0) { offset++; break }
                if ((len and 0xC0) == 0xC0) { offset += 2; break }
                offset += 1 + len
            }
            if (offset + 10 > length) break
            val type = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 8
            val rdLength = ((data[offset].toInt() and 0xFF) shl 8) or (data[offset + 1].toInt() and 0xFF)
            offset += 2
            if (type == 1 && rdLength == 4 && offset + 4 <= length) {
                val ipBytes = data.copyOfRange(offset, offset + 4)
                result.add(InetAddress.getByAddress(ipBytes))
            }
            offset += rdLength
        }
        return result
    }
}

fun buildCustomDnsResolver(endpoint: String, bootstrapClient: OkHttpClient? = null): okhttp3.Dns {
    val clean = endpoint.trim()
    if (clean.isBlank()) return okhttp3.Dns.SYSTEM

    val ipv4Regex = Regex("""^(\d{1,3}\.){3}\d{1,3}$""")
    if (ipv4Regex.matches(clean)) {
        return UdpDns(clean)
    }

    val dohUrl = normalizeCustomDnsEndpoint(clean)
    val httpUrl = dohUrl.toHttpUrlOrNull() ?: return okhttp3.Dns.SYSTEM

    val client = bootstrapClient ?: OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    val builder = DnsOverHttps.Builder()
        .client(client)
        .url(httpUrl)

    if (!ipv4Regex.matches(httpUrl.host)) {
        try {
            val bootstrap = InetAddress.getAllByName(httpUrl.host).toList()
            if (bootstrap.isNotEmpty()) {
                builder.bootstrapDnsHosts(bootstrap)
            } else {
                builder.bootstrapDnsHosts(listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("8.8.8.8")))
            }
        } catch (_: Exception) {
            try {
                builder.bootstrapDnsHosts(listOf(InetAddress.getByName("1.1.1.1"), InetAddress.getByName("8.8.8.8")))
            } catch (_: Exception) {}
        }
    }
    return builder.build()
}

fun OkHttpClient.Builder.addCustomDns(endpoint: String): OkHttpClient.Builder {
    return dns(buildCustomDnsResolver(endpoint, build()))
}

fun OkHttpClient.Builder.addCanadianShieldDns() = (
    addGenericDns(
        "https://private.canadianshield.cira.ca/dns-query",
        // https://www.cira.ca/en/canadian-shield/configure/summary-cira-canadian-shield-dns-resolver-addresses/
        listOf(
            "149.112.121.10",
            "149.112.122.10",
        ),
    )
    )
