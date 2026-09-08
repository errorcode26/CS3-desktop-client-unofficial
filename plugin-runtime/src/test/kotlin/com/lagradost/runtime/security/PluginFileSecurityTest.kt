package com.lagradost.runtime.security

import com.lagradost.runtime.loader.stubs.PluginFileSecurityStub
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PluginFileSecurityTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var storageRoot: File

    @BeforeEach
    fun setup() {
        PluginFileSecurityStub.customBaseDir = tempDir
        storageRoot = PluginFileSecurityStub.getStorageRootForPlugin("UnknownPlugin")
    }

    @AfterEach
    fun teardown() {
        PluginFileSecurityStub.customBaseDir = null
        PluginFileSecurityStub.clearGrantedPaths()
    }

    @Test
    fun `relative path resolves safely inside plugin storage root`() {
        val resolved = PluginFileSecurityStub.checkPath("cache/sub/data.json")
        val expected = File(storageRoot, "cache/sub/data.json").canonicalPath
        assertEquals(expected, resolved)
    }

    @Test
    fun `absolute path inside storage root is permitted`() {
        val safeFile = File(storageRoot, "downloads/test.txt")
        val resolved = PluginFileSecurityStub.checkPath(safeFile.absolutePath)
        assertEquals(safeFile.canonicalPath, resolved)
    }

    @Test
    fun `path traversal attack escaping storage root is blocked`() {
        val ex = assertFailsWith<SecurityException> {
            PluginFileSecurityStub.checkPath("../../another_plugin/storage/secrets.json")
        }
        assertTrue(ex.message!!.contains("Plugin File Security: Access to"))
        assertTrue(ex.message!!.contains("is blocked"))
    }

    @Test
    fun `arbitrary system path is blocked`() {
        val outsideFile = File(tempDir, "unauthorized_outside_file.txt")
        val ex = assertFailsWith<SecurityException> {
            PluginFileSecurityStub.checkPath(outsideFile.absolutePath)
        }
        assertTrue(ex.message!!.contains("is blocked"))
    }

    @Test
    fun `user granted path allows access and revoking blocks it`() {
        val customGrantedDir = File(tempDir, "UserGrantedDownloads").apply { mkdirs() }
        val targetFile = File(customGrantedDir, "video.mp4")

        // 1. Initially ungranted -> blocked
        assertFailsWith<SecurityException> {
            PluginFileSecurityStub.checkPath(targetFile.absolutePath)
        }

        // 2. Grant access -> permitted
        PluginFileSecurityStub.grantAllowedPath("UnknownPlugin", customGrantedDir)
        val allowedPath = PluginFileSecurityStub.checkPath(targetFile.absolutePath)
        assertEquals(targetFile.canonicalPath, allowedPath)

        // 3. Revoke access -> blocked again
        PluginFileSecurityStub.revokeAllowedPath("UnknownPlugin", customGrantedDir)
        assertFailsWith<SecurityException> {
            PluginFileSecurityStub.checkPath(targetFile.absolutePath)
        }
    }

    @Test
    fun `checkFile validates file object properly`() {
        val safeFile = File(storageRoot, "valid.bin")
        val result = PluginFileSecurityStub.checkFile(safeFile)
        assertEquals(safeFile.canonicalPath, result.canonicalPath)

        val badFile = File(tempDir, "outside.bin")
        assertFailsWith<SecurityException> {
            PluginFileSecurityStub.checkFile(badFile)
        }
    }
}
