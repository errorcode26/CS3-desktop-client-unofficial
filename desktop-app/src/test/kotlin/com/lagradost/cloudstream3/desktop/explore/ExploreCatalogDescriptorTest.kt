package com.lagradost.cloudstream3.desktop.explore

import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.stremio.StremioTransport
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ExploreCatalogDescriptorTest {

    @Test
    fun `test query parameters preserved from manifest url`() {
        val manifestUrl = "https://api.example.com/api/stremio/manifest.json?token=SECRET_TOKEN_123"
        val baseUrl = StremioTransport.getBaseUrl(manifestUrl)
        val queryParams = StremioTransport.getQueryParams(manifestUrl)

        assertEquals("https://api.example.com/api/stremio", baseUrl)
        assertEquals("?token=SECRET_TOKEN_123", queryParams)

        val descriptor = ManifestCatalogDescriptor(
            addonName = "Test Addon",
            addonBaseUrl = baseUrl,
            type = "movie",
            id = "test_movies",
            name = "Test Movies",
            queryParams = queryParams,
        )

        assertEquals("?token=SECRET_TOKEN_123", descriptor.queryParams)
        assertEquals("Test Addon:movie:test_movies:", descriptor.key)

        val querySuffix = if (descriptor.queryParams.isNotBlank()) {
            if (descriptor.queryParams.startsWith("?")) descriptor.queryParams else "?${descriptor.queryParams}"
        } else {
            ""
        }

        val expectedCatalogUrl = "${descriptor.addonBaseUrl}/catalog/${descriptor.type}/${descriptor.id}.json$querySuffix"
        assertEquals(
            "https://api.example.com/api/stremio/catalog/movie/test_movies.json?token=SECRET_TOKEN_123",
            expectedCatalogUrl,
        )
    }

    @Test
    fun `test query parameters empty when manifest url has none`() {
        val manifestUrl = "https://v3-cinemeta.strem.io/manifest.json"
        val baseUrl = StremioTransport.getBaseUrl(manifestUrl)
        val queryParams = StremioTransport.getQueryParams(manifestUrl)

        assertEquals("https://v3-cinemeta.strem.io", baseUrl)
        assertTrue(queryParams.isEmpty())

        val descriptor = ManifestCatalogDescriptor(
            addonName = "Cinemeta",
            addonBaseUrl = baseUrl,
            type = "movie",
            id = "top",
            name = "Top Movies",
            queryParams = queryParams,
        )

        assertTrue(descriptor.queryParams.isEmpty())
        val querySuffix = if (descriptor.queryParams.isNotBlank()) {
            if (descriptor.queryParams.startsWith("?")) descriptor.queryParams else "?${descriptor.queryParams}"
        } else {
            ""
        }

        val expectedCatalogUrl = "${descriptor.addonBaseUrl}/catalog/${descriptor.type}/${descriptor.id}.json$querySuffix"
        assertEquals(
            "https://v3-cinemeta.strem.io/catalog/movie/top.json",
            expectedCatalogUrl,
        )
    }
}
