package com.lagradost.runtime.loader

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExtensionLoaderMarkerTest {

    private fun createDummyDexPlugin(jarFile: File) {
        ZipOutputStream(FileOutputStream(jarFile)).use { zos ->
            val entry = ZipEntry("classes.dex")
            zos.putNextEntry(entry)
            zos.write(byteArrayOf(0x64, 0x65, 0x78, 0x0a, 0x30, 0x33, 0x35, 0x00))
            zos.closeEntry()
        }
    }

    @Test
    fun testClearFailedMarker_DeletesSentinelFile(@TempDir tempDir: Path) {
        val jarFile = File(tempDir.toFile(), "SamplePlugin.jar")
        val failedMarker = File(tempDir.toFile(), "SamplePlugin.d2j_failed")
        failedMarker.createNewFile()
        assertTrue(failedMarker.exists())

        ExtensionLoader.clearFailedMarker(jarFile)
        assertFalse(failedMarker.exists(), ".d2j_failed marker should be deleted by clearFailedMarker")
    }

    @Test
    fun testClearFailedMarker_HandlesJvmAndSecureSuffixes(@TempDir tempDir: Path) {
        val baseMarker = File(tempDir.toFile(), "SamplePlugin.d2j_failed")

        val jvmJar = File(tempDir.toFile(), "SamplePlugin-jvm.jar")
        baseMarker.createNewFile()
        assertTrue(baseMarker.exists())
        ExtensionLoader.clearFailedMarker(jvmJar)
        assertFalse(baseMarker.exists(), "clearFailedMarker should resolve -jvm suffix to base marker")

        val secureJar = File(tempDir.toFile(), "SamplePlugin-secure.jar")
        baseMarker.createNewFile()
        assertTrue(baseMarker.exists())
        ExtensionLoader.clearFailedMarker(secureJar)
        assertFalse(baseMarker.exists(), "clearFailedMarker should resolve -secure suffix to base marker")
    }

    @Test
    fun testLoadJar_SkipsOnFailedMarkerWithoutForceRetry(@TempDir tempDir: Path) {
        val jarFile = File(tempDir.toFile(), "FailingPlugin.jar")
        createDummyDexPlugin(jarFile)

        val failedMarker = File(tempDir.toFile(), "FailingPlugin.d2j_failed")
        failedMarker.createNewFile()
        assertTrue(failedMarker.exists())

        val ex = assertFailsWith<IllegalStateException> {
            ExtensionLoader.loadJar(jarFile, forceRetry = false)
        }
        assertTrue(
            ex.message!!.contains("Skipping previously failed Dalvik DEX translation"),
            "loadJar must immediately skip transpilation when failed marker exists",
        )
    }

    @Test
    fun testLoadJar_ClearsMarkerWhenForceRetryIsTrue(@TempDir tempDir: Path) {
        val jarFile = File(tempDir.toFile(), "RetryPlugin.jar")
        createDummyDexPlugin(jarFile)

        val failedMarker = File(tempDir.toFile(), "RetryPlugin.d2j_failed")
        failedMarker.createNewFile()
        assertTrue(failedMarker.exists())

        // With forceRetry=true, loadJar must NOT skip with "Skipping previously failed".
        // Instead, it must clear the marker and re-attempt transpilation (which fails on dummy bytes).
        val ex = assertFailsWith<IllegalStateException> {
            ExtensionLoader.loadJar(jarFile, forceRetry = true)
        }
        assertFalse(
            ex.message!!.contains("Skipping previously failed Dalvik DEX translation"),
            "forceRetry=true must not skip previously failed Dalvik DEX translation",
        )
        assertTrue(
            ex.message!!.contains("Dex2Jar translation") || ex.message!!.contains("Failed to transpile"),
            "forceRetry=true must actively attempt transpilation",
        )
    }

    @Test
    fun testSandboxFailClosed_ThrowsSecurityExceptionWhenInterceptorFails(@TempDir tempDir: Path) {
        val jarFile = File(tempDir.toFile(), "FailingSandboxPlugin.jar")
        createDummyDexPlugin(jarFile)
        try {
            ExtensionLoader.sandboxLoadInterceptor = { throw RuntimeException("Simulated worker crash") }
            val ex = assertFailsWith<SecurityException> {
                ExtensionLoader.loadAndInit(jarFile)
            }
            assertTrue(ex.message!!.contains("Security boundary enforced"), "Must throw SecurityException on worker failure")
        } finally {
            ExtensionLoader.sandboxLoadInterceptor = null
        }
    }

    @Test
    fun testSandboxFailClosed_ThrowsSecurityExceptionWhenInterceptorReturnsNull(@TempDir tempDir: Path) {
        val jarFile = File(tempDir.toFile(), "NullSandboxPlugin.jar")
        createDummyDexPlugin(jarFile)
        try {
            ExtensionLoader.sandboxLoadInterceptor = { null }
            val ex = assertFailsWith<SecurityException> {
                ExtensionLoader.loadAndInit(jarFile)
            }
            assertTrue(ex.message!!.contains("Security boundary enforced"), "Must throw SecurityException when sandbox rejects")
        } finally {
            ExtensionLoader.sandboxLoadInterceptor = null
        }
    }

    @Test
    fun testDirectLoadJarBlocked_WhenSandboxActive(@TempDir tempDir: Path) {
        val jarFile = File(tempDir.toFile(), "DirectLoadJarPlugin.jar")
        createDummyDexPlugin(jarFile)
        try {
            ExtensionLoader.sandboxLoadInterceptor = { null }
            val ex = assertFailsWith<SecurityException> {
                ExtensionLoader.loadJar(jarFile)
            }
            assertTrue(ex.message!!.contains("Security boundary enforced"), "Direct loadJar must be blocked when sandbox is active")
        } finally {
            ExtensionLoader.sandboxLoadInterceptor = null
        }
    }
}
