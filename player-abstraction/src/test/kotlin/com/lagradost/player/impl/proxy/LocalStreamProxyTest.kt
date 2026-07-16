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
}
