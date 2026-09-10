package com.lagradost.cloudstream3.desktop.network

import com.lagradost.cloudstream3.network.CloudflareKiller
import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CloudflareKillerTest {

    @BeforeEach
    fun setup() {
        CloudflareKiller.clearAllClearance()
    }

    @AfterEach
    fun teardown() {
        CloudflareKiller.clearAllClearance()
    }

    @Test
    fun testHostClearanceAtomicStorage() {
        val domain = "example.com"
        val cookie1 = Cookie.Builder()
            .name("cf_clearance")
            .value("token_abc_123")
            .domain(domain)
            .path("/")
            .build()
        val cookie2 = Cookie.Builder()
            .name("__cf_bm")
            .value("bm_xyz_789")
            .domain(domain)
            .path("/")
            .build()

        val ua = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/133.0.0.0"
        CloudflareKiller.saveClearance(domain, userAgent = ua, okCookies = listOf(cookie1, cookie2))

        val clearance = CloudflareKiller.getClearance(domain)
        assertNotNull(clearance)
        assertEquals(domain, clearance.host)
        assertEquals(ua, clearance.userAgent)
        assertEquals(2, clearance.cookies.size)
        assertFalse(clearance.isFailed)
        assertFalse(clearance.isTlsBound)

        assertEquals(ua, CloudflareKiller.getSavedUserAgent(domain))
        val map = CloudflareKiller.getSavedCookies(domain)
        assertEquals("token_abc_123", map["cf_clearance"])
        assertEquals("bm_xyz_789", map["__cf_bm"])
    }

    @Test
    fun testRfc6265DomainInheritance() {
        val rootDomain = "streamhost.test"
        val clearanceCookie = Cookie.Builder()
            .name("cf_clearance")
            .value("inherited_token_999")
            .domain(rootDomain)
            .path("/")
            .build()

        CloudflareKiller.saveClearance(
            rootDomain,
            userAgent = "CustomBrowser/1.0",
            okCookies = listOf(clearanceCookie),
        )

        // Subdomain URL should match parent domain cookies according to RFC 6265
        val subUrl = "https://cdn.sub.streamhost.test/video.m3u8".toHttpUrl()
        val subClearance = CloudflareKiller.getClearance(subUrl)
        assertNotNull(subClearance, "Clearance should be inherited by subdomains")
        assertEquals(rootDomain, subClearance.host)
        assertEquals("CustomBrowser/1.0", subClearance.userAgent)
        assertTrue(subClearance.cookies.any { it.matches(subUrl) })

        // Foreign domain URL must not receive clearance
        val foreignUrl = "https://unrelated-domain.test/index.html".toHttpUrl()
        val foreignClearance = CloudflareKiller.getClearance(foreignUrl)
        assertNull(foreignClearance, "Unrelated domain must not match clearance")
    }

    @Test
    fun testMarkAndUnmarkFailed() {
        val host = "protected-service.test"
        assertFalse(CloudflareKiller.isFailed(host))

        CloudflareKiller.markFailed(host)
        assertTrue(CloudflareKiller.isFailed(host))
        assertTrue(CloudflareKiller.isFailed("sub.$host"))

        // getClearance should ignore failed clearance states
        assertNull(CloudflareKiller.getClearance(host))

        CloudflareKiller.unmarkFailed(host)
        assertFalse(CloudflareKiller.isFailed(host))
    }

    @Test
    fun testTlsBoundState() {
        val host = "tls-strict.test"
        assertFalse(CloudflareKiller.isTlsBound(host))

        val cookie = Cookie.Builder()
            .name("cf_clearance")
            .value("tls_bound_clearance")
            .domain(host)
            .path("/")
            .build()

        CloudflareKiller.saveClearance(
            host = host,
            userAgent = "Browser/2.0",
            okCookies = listOf(cookie),
            isTlsBound = true,
        )

        assertTrue(CloudflareKiller.isTlsBound(host))
        assertTrue(CloudflareKiller.isTlsBound("media.$host"))

        val clearance = CloudflareKiller.getClearance(host)
        assertNotNull(clearance)
        assertTrue(clearance.isTlsBound)
    }
}
