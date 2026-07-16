package com.lagradost.player.impl.proxy

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalStreamProxyTest {

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            LocalStreamProxy.start()
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            LocalStreamProxy.stop()
        }
    }

    @Test
    fun testServerStartsAndAssignsPort() {
        assertTrue(LocalStreamProxy.port > 0, "Server should assign a valid port > 0")
    }

    @Test
    fun testSessionRegistration() {
        val headers = mapOf("Authorization" to "Bearer test_token")
        val sessionId = LocalStreamProxy.registerSession(headers)

        assertTrue(sessionId.isNotEmpty(), "Session ID should not be empty")
        
        // Build URL
        val url = "https://example.com/video.m3u8"
        val proxyUrl = LocalStreamProxy.buildProxyUrl(sessionId, url)
        
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        
        assertEquals(
            "http://127.0.0.1:${LocalStreamProxy.port}/proxy?s=$sessionId&u=$encodedUrl",
            proxyUrl,
            "Proxy URL should be correctly formatted with base64 encoded URL"
        )
    }

    @Test
    fun testResolveUrlTokenInheritance() {
        val base = "https://cdn.example.com/hls/master.m3u8?t=token123&s=999&expires=3600"

        // Case 1: Relative URL without query parameters
        val relRes = LocalStreamProxy.resolveUrl(base, "seg-1.ts")
        assertEquals("https://cdn.example.com/hls/seg-1.ts?t=token123&s=999&expires=3600", relRes)

        // Case 2: Absolute URL without query parameters (was previously failing and causing 403s on secondary tracks)
        val absRes = LocalStreamProxy.resolveUrl(base, "https://cdn.example.com/hls/seg-1-audio2.ts")
        assertEquals("https://cdn.example.com/hls/seg-1-audio2.ts?t=token123&s=999&expires=3600", absRes)

        // Case 3: Relative URL that already has a minor query parameter (e.g. asn=55836)
        val partialRes = LocalStreamProxy.resolveUrl(base, "seg-2.ts?asn=55836")
        assertEquals("https://cdn.example.com/hls/seg-2.ts?asn=55836&t=token123&s=999&expires=3600", partialRes)

        // Case 4: Cross-domain absolute URL should NOT inherit tokens for security
        val crossRes = LocalStreamProxy.resolveUrl(base, "https://analytics.tracker.com/event")
        assertEquals("https://analytics.tracker.com/event", crossRes)
    }
}
