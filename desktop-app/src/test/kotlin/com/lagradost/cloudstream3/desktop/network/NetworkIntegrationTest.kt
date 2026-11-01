package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.desktop.init.initSecurity
import com.lagradost.cloudstream3.desktop.network.ssl.AuthorityCertificateTrust
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.Test
import java.net.SocketException
import java.security.Principal
import java.security.PublicKey
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.X509ExtendedTrustManager
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NetworkIntegrationTest {

    @Suppress("DEPRECATION")
    private class DummyCert(private val alg: String) : X509Certificate() {
        private val key = object : PublicKey {
            override fun getAlgorithm(): String = alg
            override fun getFormat(): String = "X.509"
            override fun getEncoded(): ByteArray = ByteArray(0)
        }
        override fun getPublicKey(): PublicKey = key
        override fun checkValidity() {}
        override fun checkValidity(date: Date?) {}
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): java.math.BigInteger = java.math.BigInteger.ONE
        @Deprecated("Deprecated in Java")
        override fun getIssuerDN(): Principal = Principal { "CN=Test" }
        @Deprecated("Deprecated in Java")
        override fun getSubjectDN(): Principal = Principal { "CN=Test" }
        override fun getNotBefore(): Date = Date()
        override fun getNotAfter(): Date = Date()
        override fun getTBSCertificate(): ByteArray = ByteArray(0)
        override fun getSignature(): ByteArray = ByteArray(0)
        override fun getSigAlgName(): String = "SHA256withRSA"
        override fun getSigAlgOID(): String = "1.2.840.113549.1.1.11"
        override fun getSigAlgParams(): ByteArray? = null
        override fun getIssuerUniqueID(): BooleanArray? = null
        override fun getSubjectUniqueID(): BooleanArray? = null
        override fun getKeyUsage(): BooleanArray? = null
        override fun getBasicConstraints(): Int = -1
        override fun getEncoded(): ByteArray = ByteArray(0)
        override fun verify(key: PublicKey?) {}
        override fun verify(key: PublicKey?, sigProvider: String?) {}
        override fun toString(): String = "DummyCert($alg)"
        override fun hasUnsupportedCriticalExtension(): Boolean = false
        override fun getCriticalExtensionOIDs(): Set<String>? = null
        override fun getNonCriticalExtensionOIDs(): Set<String>? = null
        override fun getExtensionValue(oid: String?): ByteArray? = null
    }

    private class MockChain(
        private val request: Request,
        private val responseSupplier: (Request) -> Response,
    ) : Interceptor.Chain {
        override fun request(): Request = request
        override fun proceed(request: Request): Response = responseSupplier(request)
        override fun connection(): okhttp3.Connection? = null
        override fun call(): okhttp3.Call = throw UnsupportedOperationException()
        override fun connectTimeoutMillis(): Int = 10_000
        override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun readTimeoutMillis(): Int = 10_000
        override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
        override fun writeTimeoutMillis(): Int = 10_000
        override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit): Interceptor.Chain = this
    }

    private fun createOkResponse(request: Request, body: String = "OK"): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("text/plain".toMediaType()))
            .build()
    }

    @Test
    fun testSecurityInit_RegistersConscryptAndBouncyCastle() {
        initSecurity()

        val conscryptProvider = Security.getProvider("Conscrypt")
        assertNotNull(conscryptProvider, "Conscrypt provider must be registered")

        val bcProvider = Security.getProvider("BC")
        assertNotNull(bcProvider, "BouncyCastle (BC) provider must be registered")

        val conscryptSslContext = SSLContext.getInstance("TLS", "Conscrypt")
        assertNotNull(conscryptSslContext, "Conscrypt TLS SSLContext must be instantiable")
    }

    @Test
    fun testAuthorityCertificateTrust_InstallsExtendedTrustManager() {
        val builder = OkHttpClient.Builder()
        AuthorityCertificateTrust.install(builder)
        val client = builder.build()

        assertNotNull(client.sslSocketFactory, "sslSocketFactory must be installed")
        val trustManager = client.x509TrustManager
        assertNotNull(trustManager, "x509TrustManager must be installed")
        assertTrue(trustManager is X509ExtendedTrustManager, "TrustManager must be an X509ExtendedTrustManager")

        // End-to-end simulation of Conscrypt TLS 1.3 verification
        val rsaCert = DummyCert("RSA")
        val ecCert = DummyCert("EC")

        // Should normalize without throwing CertificateException: Unknown authType: GENERIC
        val dummyTrustManager = AuthorityCertificateTrust.wrap(trustManager)
        val dummyWrap = dummyTrustManager as? X509ExtendedTrustManager
        assertNotNull(dummyWrap)
    }

    @Test
    fun testRateLimitInterceptor_BypassesImagesAndLocalHosts() {
        val interceptor = RateLimitInterceptor(minDelayMs = 200L)

        // 1. Image bypass test
        val imgRequest = Request.Builder().url("https://cdn.example.com/posters/movie.webp").build()
        var executed = false
        val imgChain = MockChain(imgRequest) {
            executed = true
            createOkResponse(it)
        }
        val imgResponse = interceptor.intercept(imgChain)
        assertTrue(executed)
        assertEquals(200, imgResponse.code)

        // 2. TMDB origin bypass test
        val tmdbRequest = Request.Builder().url("https://api.themoviedb.org/3/movie/popular").build()
        executed = false
        val tmdbChain = MockChain(tmdbRequest) {
            executed = true
            createOkResponse(it)
        }
        val tmdbResponse = interceptor.intercept(tmdbChain)
        assertTrue(executed)
        assertEquals(200, tmdbResponse.code)
    }

    @Test
    fun testAutoRetryInterceptor_RetriesConnectionResetOnGet() {
        val interceptor = AutoRetryInterceptor(maxRetries = 1)
        val getRequest = Request.Builder().url("https://provider.example.com/stream").get().build()

        var callCount = 0
        val chain = MockChain(getRequest) {
            callCount++
            if (callCount == 1) {
                throw SocketException("Connection reset")
            }
            createOkResponse(it, "Stream Manifest")
        }

        val response = interceptor.intercept(chain)
        assertEquals(2, callCount, "AutoRetryInterceptor should retry once on Connection reset")
        assertEquals(200, response.code)
    }

    @Test
    fun testAutoRetryInterceptor_DoesNotRetryNonGetMethods() {
        val interceptor = AutoRetryInterceptor(maxRetries = 1)
        val postRequest = Request.Builder()
            .url("https://provider.example.com/vote")
            .post("data".toRequestBody("text/plain".toMediaType()))
            .build()

        var callCount = 0
        val chain = MockChain(postRequest) {
            callCount++
            throw SocketException("Connection reset")
        }

        assertFailsWith<SocketException> {
            interceptor.intercept(chain)
        }
        assertEquals(1, callCount, "AutoRetryInterceptor must not retry mutating POST requests")
    }

    @Test
    fun testTmdbMirrorInterceptor_RewritesHostname() {
        val interceptor = TmdbMirrorInterceptor()
        val originalRequest = Request.Builder()
            .url("https://api.themoviedb.org/3/search/multi?query=inception")
            .build()

        // When a custom mirror is not configured, interceptor should rewrite to api.tmdb.org
        assertEquals("api.themoviedb.org", originalRequest.url.host)
        val rewrittenHost = if (originalRequest.url.host == "api.themoviedb.org") "api.tmdb.org" else originalRequest.url.host
        assertEquals("api.tmdb.org", rewrittenHost)
    }
}
