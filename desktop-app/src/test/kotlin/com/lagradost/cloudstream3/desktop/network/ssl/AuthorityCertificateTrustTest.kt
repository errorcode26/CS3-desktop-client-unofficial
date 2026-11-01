package com.lagradost.cloudstream3.desktop.network.ssl

import org.junit.jupiter.api.Test
import java.math.BigInteger
import java.security.Principal
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.X509TrustManager
import kotlin.test.assertEquals

class AuthorityCertificateTrustTest {

    @Suppress("DEPRECATION")
    private class DummyX509Certificate(private val keyAlgorithm: String) : X509Certificate() {
        private val key = object : PublicKey {
            override fun getAlgorithm(): String = keyAlgorithm
            override fun getFormat(): String = "X.509"
            override fun getEncoded(): ByteArray = ByteArray(0)
        }
        override fun getPublicKey(): PublicKey = key
        override fun checkValidity() {}
        override fun checkValidity(date: Date?) {}
        override fun getVersion(): Int = 3
        override fun getSerialNumber(): BigInteger = BigInteger.ONE
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
        override fun toString(): String = "DummyX509Certificate($keyAlgorithm)"
        override fun hasUnsupportedCriticalExtension(): Boolean = false
        override fun getCriticalExtensionOIDs(): Set<String>? = null
        override fun getNonCriticalExtensionOIDs(): Set<String>? = null
        override fun getExtensionValue(oid: String?): ByteArray? = null
    }

    @Test
    fun testNormalizeAuthType_ConvertsGenericToRsaForRsaKey() {
        var capturedAuthType: String? = null
        val baseTm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                capturedAuthType = authType
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val wrapped = AuthorityCertificateTrust.wrap(baseTm)
        val rsaCert = DummyX509Certificate("RSA")

        wrapped.checkServerTrusted(arrayOf(rsaCert), "GENERIC")
        assertEquals("RSA", capturedAuthType, "authType 'GENERIC' must be normalized to 'RSA' for RSA certificates")
    }

    @Test
    fun testNormalizeAuthType_ConvertsGenericToEcdsaForEcKey() {
        var capturedAuthType: String? = null
        val baseTm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                capturedAuthType = authType
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val wrapped = AuthorityCertificateTrust.wrap(baseTm)
        val ecCert = DummyX509Certificate("EC")

        wrapped.checkServerTrusted(arrayOf(ecCert), "GENERIC")
        assertEquals("ECDSA", capturedAuthType, "authType 'GENERIC' must be normalized to 'ECDSA' for EC certificates")
    }

    @Test
    fun testNormalizeAuthType_PreservesStandardAuthType() {
        var capturedAuthType: String? = null
        val baseTm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
                capturedAuthType = authType
            }
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }

        val wrapped = AuthorityCertificateTrust.wrap(baseTm)
        val rsaCert = DummyX509Certificate("RSA")

        wrapped.checkServerTrusted(arrayOf(rsaCert), "ECDHE_RSA")
        assertEquals("ECDHE_RSA", capturedAuthType, "Standard authType 'ECDHE_RSA' must be preserved unchanged")
    }

    @Test
    fun testExtendedTrustManagerMethods_NormalizeAuthType() {
        var capturedSocketAuthType: String? = null
        var capturedEngineAuthType: String? = null

        val baseTm = object : javax.net.ssl.X509ExtendedTrustManager() {
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?, socket: java.net.Socket?) {}
            override fun checkClientTrusted(chain: Array<X509Certificate>?, authType: String?, engine: javax.net.ssl.SSLEngine?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?, socket: java.net.Socket?) {
                capturedSocketAuthType = authType
            }
            override fun checkServerTrusted(chain: Array<X509Certificate>?, authType: String?, engine: javax.net.ssl.SSLEngine?) {
                capturedEngineAuthType = authType
            }
        }

        val wrapped = AuthorityCertificateTrust.wrap(baseTm)
        val rsaCert = DummyX509Certificate("RSA")

        wrapped.checkServerTrusted(arrayOf(rsaCert), "GENERIC", null as java.net.Socket?)
        assertEquals("RSA", capturedSocketAuthType)

        wrapped.checkServerTrusted(arrayOf(rsaCert), "GENERIC", null as javax.net.ssl.SSLEngine?)
        assertEquals("RSA", capturedEngineAuthType)
    }
}
