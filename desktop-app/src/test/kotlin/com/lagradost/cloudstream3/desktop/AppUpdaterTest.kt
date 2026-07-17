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
}
