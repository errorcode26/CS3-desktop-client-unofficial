package com.lagradost.cloudstream3.desktop.network.ssl

import com.lagradost.common.logging.AppLogger
import okhttp3.OkHttpClient
import java.io.ByteArrayInputStream
import java.net.HttpURLConnection
import java.net.URI
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * Resolves incomplete X.509 certificate chains via RFC 5280 Authority Information Access (AIA).
 * Automatically retrieves missing intermediate certificates emitted by modern certificate authorities.
 */
object AuthorityCertificateTrust {
    private const val TAG = "AuthorityCertTrust"
    private const val CA_ISSUERS_OID = "1.3.6.1.5.5.7.48.2"
    private const val AIA_SYNTAX_OID = "1.3.6.1.5.5.7.1.1"
    private const val CACHE_EXPIRY_MS = 600_000L // 10 minutes

    private data class CachedIssuer(val certificates: List<X509Certificate>, val timestamp: Long)

    private val certCache = ConcurrentHashMap<String, CachedIssuer>()
    private val certFactory = CertificateFactory.getInstance("X.509")

    @Volatile
    private var cachedSocketFactory: javax.net.ssl.SSLSocketFactory? = null

    @Volatile
    private var cachedTrustManager: X509TrustManager? = null
    private val initLock = Any()

    fun install(builder: OkHttpClient.Builder) {
        try {
            var trustManager = cachedTrustManager
            var socketFactory = cachedSocketFactory

            if (trustManager == null || socketFactory == null) {
                synchronized(initLock) {
                    trustManager = cachedTrustManager
                    socketFactory = cachedSocketFactory
                    if (trustManager == null || socketFactory == null) {
                        val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                        tmf.init(null as KeyStore?)
                        val systemTrustManager = tmf.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull() ?: return
                        val resilientTrustManager = wrap(systemTrustManager)

                        val sslContext = runCatching {
                            SSLContext.getInstance("TLS", "Conscrypt")
                        }.getOrElse {
                            SSLContext.getInstance("TLS")
                        }
                        sslContext.init(null, arrayOf(resilientTrustManager), null)
                        socketFactory = sslContext.socketFactory
                        trustManager = resilientTrustManager
                        cachedSocketFactory = socketFactory
                        cachedTrustManager = trustManager
                        AppLogger.i("$TAG: Successfully installed RFC 5280 intermediate certificate resolver.")
                    }
                }
            }

            if (socketFactory != null && trustManager != null) {
                builder.sslSocketFactory(socketFactory, trustManager)
            }
        } catch (t: Throwable) {
            AppLogger.w("$TAG: Failed to install AIA certificate resolver: ${t.message}")
        }
    }

    private fun wrap(base: X509TrustManager) = object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            base.checkClientTrusted(chain, authType)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = base.acceptedIssuers

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                base.checkServerTrusted(chain, authType)
            } catch (initialException: CertificateException) {
                val completedChain = resolveMissingIntermediates(chain) ?: throw initialException
                try {
                    base.checkServerTrusted(completedChain, authType)
                    AppLogger.i("$TAG: Successfully verified ${chain.firstOrNull()?.subjectX500Principal?.name} after resolving ${completedChain.size - chain.size} intermediate certificate(s).")
                } catch (_: CertificateException) {
                    throw initialException
                }
            }
        }
    }

    private fun resolveMissingIntermediates(chain: Array<X509Certificate>): Array<X509Certificate>? {
        val workingChain = chain.toMutableList()
        repeat(3) {
            val lastCert = workingChain.last()
            // If self-signed or root reached, stop
            if (lastCert.subjectX500Principal == lastCert.issuerX500Principal) {
                return if (workingChain.size > chain.size) workingChain.toTypedArray() else null
            }

            val nextIssuer = fetchIssuer(lastCert) ?: return if (workingChain.size > chain.size) workingChain.toTypedArray() else null
            workingChain.add(nextIssuer)
        }
        return if (workingChain.size > chain.size) workingChain.toTypedArray() else null
    }

    private fun fetchIssuer(cert: X509Certificate): X509Certificate? {
        val urls = extractCaIssuerUrls(cert)
        for (url in urls) {
            val downloadedCerts = downloadCertificates(url)
            for (candidate in downloadedCerts) {
                if (candidate.subjectX500Principal != cert.issuerX500Principal) continue
                if (runCatching { cert.verify(candidate.publicKey) }.isSuccess) {
                    return candidate
                }
            }
        }
        return null
    }

    private fun extractCaIssuerUrls(cert: X509Certificate): List<String> {
        val extBytes = cert.getExtensionValue(AIA_SYNTAX_OID) ?: return emptyList()
        val urls = mutableListOf<String>()

        try {
            // Scan raw DER octet string for embedded http/https URI strings
            val ascii = String(extBytes, Charsets.ISO_8859_1)
            val regex = Regex("""http[s]?://[^\u0000-\u001F\s"<>]+""")
            regex.findAll(ascii).forEach { match ->
                val candidate = match.value.trim()
                if (candidate.endsWith(".crt", ignoreCase = true) || candidate.endsWith(".cer", ignoreCase = true) || candidate.contains("ca", ignoreCase = true)) {
                    urls.add(candidate)
                }
            }
        } catch (_: Exception) {}

        return urls
    }

    private fun downloadCertificates(urlStr: String): List<X509Certificate> {
        val now = System.currentTimeMillis()
        certCache[urlStr]?.let { cached ->
            if (cached.certificates.isNotEmpty() || now - cached.timestamp < CACHE_EXPIRY_MS) {
                return cached.certificates
            }
        }

        return try {
            val uri = URI(urlStr)
            if (uri.scheme != "http" && uri.scheme != "https") return emptyList()

            val conn = uri.toURL().openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = true

            val bytes = conn.inputStream.use { it.readBytes() }
            val certs = certFactory.generateCertificates(ByteArrayInputStream(bytes))
                .filterIsInstance<X509Certificate>()

            certCache[urlStr] = CachedIssuer(certs, now)
            certs
        } catch (e: Exception) {
            certCache[urlStr] = CachedIssuer(emptyList(), now)
            emptyList()
        }
    }
}
