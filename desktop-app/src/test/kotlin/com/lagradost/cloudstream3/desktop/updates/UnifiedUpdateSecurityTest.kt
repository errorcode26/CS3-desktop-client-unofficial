package com.lagradost.cloudstream3.desktop.updates

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class UnifiedUpdateSecurityTest {

    @Test
    fun testComputeSha256HashMatchesKnownDigest() {
        val tempFile = File.createTempFile("cs3_test_hash", ".tmp")
        try {
            tempFile.writeText("CloudStream Security Test Payload", Charsets.UTF_8)
            val hash = UnifiedUpdateManager.computeSha256(tempFile)
            // SHA-256 for "CloudStream Security Test Payload"
            val expectedHash = "6401f5f8f5b8c2f4d10ecd0ac116285b9e7df7b59ed46a16f280ef4c9fba91c2"
            assertEquals(expectedHash, hash)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testParseSha256FromChecksumsManifest() {
        val manifest = """
            # Checksums for CloudStream Desktop Release
            e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855  CloudStream-Setup.exe
            a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90 *CloudStream-Portable-v0.1.4-windows-x64.zip
            11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff  CloudStream-Desktop-0.1.4.msi
        """.trimIndent()

        val exeHash = UnifiedUpdateManager.parseSha256(manifest, "CloudStream-Setup.exe")
        assertNotNull(exeHash)
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", exeHash)

        val zipHash = UnifiedUpdateManager.parseSha256(manifest, "CloudStream-Portable-v0.1.4-windows-x64.zip")
        assertNotNull(zipHash)
        assertEquals("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90", zipHash)

        val msiHash = UnifiedUpdateManager.parseSha256(manifest, "CloudStream-Desktop-0.1.4.msi")
        assertNotNull(msiHash)
        assertEquals("11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff", msiHash)

        val missing = UnifiedUpdateManager.parseSha256(manifest, "NonExistentBinary.exe")
        assertNull(missing)
    }

    @Test
    fun testParseSha256FromSingleHashFile() {
        val singleHash = "a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90\n"
        val parsed = UnifiedUpdateManager.parseSha256(singleHash, "AnyTarget.exe")
        assertNotNull(parsed)
        assertEquals("a1b2c3d4e5f60718293a4b5c6d7e8f90a1b2c3d4e5f60718293a4b5c6d7e8f90", parsed)
    }
}
