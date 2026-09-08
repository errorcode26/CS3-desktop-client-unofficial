package com.lagradost.cloudstream3.desktop.network

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopCookieJarTest {

    private lateinit var cookieJar: DesktopCookieJar

    @BeforeEach
    fun setup() {
        cookieJar = DesktopCookieJar()
        cookieJar.removeAll()
    }

    @AfterEach
    fun teardown() {
        cookieJar.removeAll()
    }

    @Test
    fun testSubdomainInheritance() {
        val url = "https://example.com".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("session_token")
            .value("xyz123")
            .domain("example.com")
            .path("/")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))

        val subUrl = "https://api.sub.example.com/data".toHttpUrl()
        val loaded = cookieJar.loadForRequest(subUrl)

        assertEquals(1, loaded.size)
        assertEquals("session_token", loaded[0].name)
        assertEquals("xyz123", loaded[0].value)
    }

    @Test
    fun testHostOnlyCookieIsolation() {
        val url = "https://api.example.com".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("host_only")
            .value("isolated")
            .hostOnlyDomain("api.example.com")
            .path("/")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))

        val otherSubUrl = "https://web.example.com/".toHttpUrl()
        val loadedOther = cookieJar.loadForRequest(otherSubUrl)
        assertTrue(loadedOther.isEmpty(), "Host-only cookie should not be accessible from sibling subdomains")

        val targetUrl = "https://api.example.com/endpoint".toHttpUrl()
        val loadedTarget = cookieJar.loadForRequest(targetUrl)
        assertEquals(1, loadedTarget.size)
        assertEquals("host_only", loadedTarget[0].name)
    }

    @Test
    fun testPathScoping() {
        val url = "https://example.com/api".toHttpUrl()
        val cookie = Cookie.Builder()
            .name("api_auth")
            .value("auth_token_99")
            .domain("example.com")
            .path("/api")
            .build()

        cookieJar.saveFromResponse(url, listOf(cookie))

        val webUrl = "https://example.com/web/dashboard".toHttpUrl()
        val loadedWeb = cookieJar.loadForRequest(webUrl)
        assertTrue(loadedWeb.isEmpty(), "Cookie scoped to /api should not match /web")

        val apiUrl = "https://example.com/api/v1/stream".toHttpUrl()
        val loadedApi = cookieJar.loadForRequest(apiUrl)
        assertEquals(1, loadedApi.size)
        assertEquals("api_auth", loadedApi[0].name)
    }

    @Test
    fun testDirectCookieInjection() {
        val cookie1 = Cookie.Builder()
            .name("cf_clearance")
            .value("test_clearance_value_1234567890")
            .domain("streamhost.net")
            .path("/")
            .build()

        val cookie2 = Cookie.Builder()
            .name("PHPSESSID")
            .value("sess_987654321")
            .domain("streamhost.net")
            .path("/")
            .build()

        cookieJar.saveCookies(listOf(cookie1, cookie2))

        val targetUrl = "https://tv.streamhost.net/watch".toHttpUrl()
        val loaded = cookieJar.loadForRequest(targetUrl)

        assertEquals(2, loaded.size)
        val names = loaded.map { it.name }.toSet()
        assertTrue(names.contains("cf_clearance"))
        assertTrue(names.contains("PHPSESSID"))
    }

    @Test
    fun testSettledPageCache() {
        val testUrl = "https://example.com/tv"
        val testHtml = "<html><body><script>const otp = [1, 2, 3, 4, 5, 6];</script></body></html>"
        val testUa = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/133.0.0.0"

        SettledPageCache.put(testUrl, testHtml, testUa)

        val entry = SettledPageCache.get("https://example.com/tv/")
        assertNotNull(entry)
        assertEquals(testHtml, entry.html)
        assertEquals(testUa, entry.userAgent)

        SettledPageCache.remove(testUrl)
        assertNull(SettledPageCache.get(testUrl))
    }
}
