package com.lagradost.cloudstream3.desktop.stremio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StremioStreamRoutingTest {

    @Test
    fun `test buildStreamUrl with custom namespaced id and token`() {
        val manifestUrl = "https://api.example.com/api/stremio/manifest.json?token=TEST_TOKEN_XYZ"
        val customId = "customprefix:6820fae9d1f9d64912f80c8c"

        val streamUrl = StremioTransport.buildStreamUrl(
            manifestUrl = manifestUrl,
            type = "movie",
            id = customId,
        )

        assertEquals(
            "https://api.example.com/api/stremio/stream/movie/customprefix%3A6820fae9d1f9d64912f80c8c.json?token=TEST_TOKEN_XYZ",
            streamUrl,
        )
    }

    @Test
    fun `test buildStreamUrl with standard imdb id and no token`() {
        val manifestUrl = "https://addon.example.com/manifest.json"
        val imdbId = "tt0437833"

        val streamUrl = StremioTransport.buildStreamUrl(
            manifestUrl = manifestUrl,
            type = "movie",
            id = imdbId,
        )

        assertEquals(
            "https://addon.example.com/stream/movie/tt0437833.json",
            streamUrl,
        )
    }

    @Test
    fun `test addon idPrefixes matching rules`() {
        val customAddon = ManagedStremioAddon(
            manifestUrl = "https://api.example.com/api/stremio/manifest.json?token=TEST_TOKEN_XYZ",
            name = "Custom Catalog",
            enabled = true,
            providesStreams = true,
            idPrefixes = listOf("customprefix"),
        )

        val imdbAddon = ManagedStremioAddon(
            manifestUrl = "https://addon.example.com/manifest.json",
            name = "Mock Addon",
            enabled = true,
            providesStreams = true,
            idPrefixes = listOf("tt"),
        )

        val genericAddon = ManagedStremioAddon(
            manifestUrl = "https://generic.example.com/manifest.json",
            name = "Generic Scraper",
            enabled = true,
            providesStreams = true,
            idPrefixes = emptyList(),
        )

        val customMediaId = "customprefix:6820fae9d1f9d64912f80c8c"
        val imdbMediaId = "tt0437833"

        // Custom Addon matches custom ID, rejects IMDb ID
        assertTrue(customAddon.idPrefixes.any { customMediaId.startsWith(it, ignoreCase = true) })
        assertFalse(customAddon.idPrefixes.any { imdbMediaId.startsWith(it, ignoreCase = true) })

        // IMDb Addon matches IMDb ID, rejects custom ID
        assertTrue(imdbAddon.idPrefixes.any { imdbMediaId.startsWith(it, ignoreCase = true) })
        assertFalse(imdbAddon.idPrefixes.any { customMediaId.startsWith(it, ignoreCase = true) })

        // Generic Addon allows IMDb fallback because idPrefixes is empty
        assertTrue(genericAddon.idPrefixes.isEmpty())
    }

    @Test
    fun `test cleanMediaId strips URI schemes and path segments`() {
        assertEquals("customprefix:6820fae9d1f9d64912f80c8c", StremioAddonManager.cleanMediaId("stremio:///customprefix:6820fae9d1f9d64912f80c8c"))
        assertEquals("customprefix:6820fae9d1f9d64912f80c8c", StremioAddonManager.cleanMediaId("stremio://customprefix:6820fae9d1f9d64912f80c8c"))
        assertEquals("customprefix:6820fae9d1f9d64912f80c8c", StremioAddonManager.cleanMediaId("stremio:customprefix:6820fae9d1f9d64912f80c8c"))
        assertEquals("customprefix:6820fae9d1f9d64912f80c8c", StremioAddonManager.cleanMediaId("stremio:///movie/customprefix:6820fae9d1f9d64912f80c8c"))
        assertEquals("customprefix:6820fae9d1f9d64912f80c8c", StremioAddonManager.cleanMediaId("stremio:///series/customprefix:6820fae9d1f9d64912f80c8c"))
        assertEquals("tt0437833:1:2", StremioAddonManager.cleanMediaId("stremio:///tt0437833:1:2"))
        assertEquals("tt0437833", StremioAddonManager.cleanMediaId("tt0437833"))
    }

    @Test
    fun `test prefix matching with cleaned mediaId`() {
        val addon = ManagedStremioAddon(
            manifestUrl = "https://api.example.com/manifest.json",
            name = "Custom Addon",
            enabled = true,
            providesStreams = true,
            idPrefixes = listOf("customprefix"),
        )
        val rawUri = "stremio:///customprefix:6820fae9d1f9d64912f80c8c"
        val cleaned = StremioAddonManager.cleanMediaId(rawUri)

        // Raw fails, cleaned succeeds
        assertFalse(addon.idPrefixes.any { rawUri.startsWith(it, ignoreCase = true) })
        assertTrue(addon.idPrefixes.any { cleaned.startsWith(it, ignoreCase = true) })
    }

    @Test
    fun `test manifest parser combines root and resource idPrefixes`() {
        val manifestJson = """
            {
                "id": "org.example.test",
                "name": "Test Addon",
                "version": "1.0.0",
                "resources": [
                    {
                        "name": "stream",
                        "types": ["movie", "series"],
                        "idPrefixes": ["streamprefix"]
                    }
                ],
                "types": ["movie", "series"],
                "idPrefixes": ["rootprefix"]
            }
        """.trimIndent()

        val parsed = StremioManifestParser.parse("https://example.com/manifest.json", manifestJson)
        assertTrue(parsed.idPrefixes.contains("rootprefix"))
        assertTrue(parsed.idPrefixes.contains("streamprefix"))
    }

    @Test
    fun `test stream response JSON parses embedded subtitles`() {
        val json = """
            {
                "streams": [
                    {
                        "name": "AddonName",
                        "title": "Stream Title",
                        "url": "https://example.com/stream.m3u8",
                        "subtitles": [
                            {
                                "id": "sub-1",
                                "lang": "eng",
                                "url": "https://example.com/sub.srt"
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val parsed = mapper.readValue(json, StremioStreamResponse::class.java)
        assertEquals(1, parsed.streams?.size)
        val stream = parsed.streams?.firstOrNull()
        assertNotNull(stream)
        assertEquals("https://example.com/stream.m3u8", stream!!.url)
        val subs = stream.subtitles
        assertNotNull(subs)
        assertEquals(1, subs!!.size)
        assertEquals("eng", subs.first().lang)
        assertEquals("https://example.com/sub.srt", subs.first().url)
    }
}
