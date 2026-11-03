package com.lagradost.cloudstream3.desktop

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

/**
 * Tests for the version comparison logic in AppUpdater.
 * This is critical — a bug here means users never get notified of updates,
 * or get a false update notification on every launch.
 *
 * Uses reflection to access the private compareVersions() method.
 */
class AppUpdaterTest {

    // Testing compareVersions directly now that it's internal.

    @Test
    fun `newer remote version is detected correctly`() {
        assert(AppUpdater.compareVersions("0.1.3", "0.1.2") > 0) { "0.1.3 should be newer than 0.1.2" }
        assert(AppUpdater.compareVersions("1.0.0", "0.9.9") > 0) { "1.0.0 should be newer than 0.9.9" }
        assert(AppUpdater.compareVersions("0.2.0", "0.1.9") > 0) { "0.2.0 should be newer than 0.1.9" }
    }

    @Test
    fun `same version returns zero`() {
        assertEquals(0, AppUpdater.compareVersions("0.1.2", "0.1.2"))
        assertEquals(0, AppUpdater.compareVersions("1.0.0", "1.0.0"))
    }

    @Test
    fun `older remote version returns negative`() {
        assert(AppUpdater.compareVersions("0.1.1", "0.1.2") < 0) { "0.1.1 should be older than 0.1.2" }
        assert(AppUpdater.compareVersions("0.0.9", "1.0.0") < 0) { "0.0.9 should be older than 1.0.0" }
    }

    @Test
    fun `version with extra patch segment handled correctly`() {
        // e.g. "0.1.2.1" vs "0.1.2"
        assert(AppUpdater.compareVersions("0.1.2.1", "0.1.2") > 0)
        assert(AppUpdater.compareVersions("0.1.2", "0.1.2.1") < 0)
    }

    @Test
    fun `malformed version segment treated as zero`() {
        // Should not throw — gracefully treat non-numeric as 0
        assertEquals(0, AppUpdater.compareVersions("0.1.2", "0.1.2"))
        assert(AppUpdater.compareVersions("1.0.0", "0.x.y") > 0)
    }

    @Test
    fun `parseSha256 parses standard checksum format`() {
        val dummyChecksums = """
            # Checksums for Release v0.2.2
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  CloudStream-Setup-v0.2.2.exe
            ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb *CloudStream-Portable-v0.2.2.zip
        """.trimIndent()

        val exeHash = com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.parseSha256(
            dummyChecksums,
            "CloudStream-Setup-v0.2.2.exe"
        )
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", exeHash)

        val zipHash = com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.parseSha256(
            dummyChecksums,
            "CloudStream-Portable-v0.2.2.zip"
        )
        assertEquals("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb", zipHash)
    }

    @Test
    fun `computeSha256 produces valid SHA-256 hash for file`() {
        val tempFile = java.io.File.createTempFile("test_sha", ".txt")
        try {
            tempFile.writeText("hello world")
            val hash = com.lagradost.cloudstream3.desktop.updates.UnifiedUpdateManager.computeSha256(tempFile)
            assertEquals("b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9", hash)
        } finally {
            tempFile.delete()
        }
    }
}
