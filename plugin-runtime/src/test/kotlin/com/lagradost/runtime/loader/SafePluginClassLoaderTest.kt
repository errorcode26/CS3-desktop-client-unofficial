package com.lagradost.runtime.loader

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SafePluginClassLoaderTest {

    @Test
    fun testAllowedClassesLoadedNormally() {
        val classLoader = SafePluginClassLoader(this::class.java.classLoader)

        // java.lang.String is perfectly safe
        val strClass = classLoader.loadClass("java.lang.String")
        assertEquals("java.lang.String", strClass.name)

        // In-memory NIO buffers and charsets are allowed for decoders
        val byteBufferClass = classLoader.loadClass("java.nio.ByteBuffer")
        assertEquals("java.nio.ByteBuffer", byteBufferClass.name)

        val charsetsClass = classLoader.loadClass("java.nio.charset.StandardCharsets")
        assertEquals("java.nio.charset.StandardCharsets", charsetsClass.name)

        // File and FileInputStream are allowed (jailed at runtime via PluginFileSecurityStub)
        val fileClass = classLoader.loadClass("java.io.File")
        assertEquals("java.io.File", fileClass.name)

        val fileInputStreamClass = classLoader.loadClass("java.io.FileInputStream")
        assertEquals("java.io.FileInputStream", fileInputStreamClass.name)
    }

    @Test
    fun testDangerousClassesBlocked() {
        val classLoader = SafePluginClassLoader(this::class.java.classLoader)

        // These should throw SecurityException
        val blockedClasses = listOf(
            "java.lang.Thread",
            "java.lang.ProcessBuilder",
            "java.lang.ProcessHandle",
            "java.lang.ClassLoader",
            "java.lang.invoke.MethodHandles\$Lookup",
            "java.util.concurrent.Executors",
            "java.util.ServiceLoader",
            "java.net.Socket",
            "java.net.ServerSocket",
            "java.nio.file.Files",
            "java.nio.file.Paths",
            "java.nio.file.Path",
            "java.awt.Desktop",
            "java.awt.Robot",
            "java.net.NetworkInterface",
            "java.io.RandomAccessFile",
            "sun.misc.Unsafe",
        )

        for (className in blockedClasses) {
            assertFailsWith<SecurityException>("Expected $className to be blocked") {
                classLoader.loadClass(className)
            }
        }

        // Native library loading must return null
        assertNull(classLoader.findLibrary("native_exploit"))
    }

    @Test
    fun testUnknownPluginClassThrowsClassNotFound() {
        val classLoader = SafePluginClassLoader(this::class.java.classLoader)

        // Unknown 3rd party or plugin internal classes must throw ClassNotFoundException
        // so the child CompatPluginClassLoader checks the plugin's JAR
        assertFailsWith<ClassNotFoundException> {
            classLoader.loadClass("com.example.provider.MyCustomExtractor")
        }
    }

    @Test
    fun testGhostStubGenerationForAndroidAndGoogleClasses() {
        val classLoader = SafePluginClassLoader(this::class.java.classLoader)
        val stubClass = classLoader.loadClass("com.google.android.material.bottomsheet.BottomSheetDialogFragment")
        assertEquals("com.google.android.material.bottomsheet.BottomSheetDialogFragment", stubClass.name)
    }

    @Test
    fun testPrivacySpooferReturnsGenericData() {
        val locale = com.lagradost.cloudstream3.PrivacySpoofer.getSpoofedLocale()
        assertEquals("en", locale.language)
        assertEquals("US", locale.country)

        val tz = com.lagradost.cloudstream3.PrivacySpoofer.getSpoofedTimeZone()
        assertEquals("UTC", tz.id)

        val zoneId = com.lagradost.cloudstream3.PrivacySpoofer.getSpoofedZoneId()
        assertEquals("UTC", zoneId.id)
    }

    @Test
    fun testSystemStubSafeReturns() {
        assertEquals("\n", com.lagradost.runtime.loader.stubs.SystemStub.getProperty("line.separator"))
        assertEquals("/", com.lagradost.runtime.loader.stubs.SystemStub.getProperty("file.separator"))
        assertEquals("17", com.lagradost.runtime.loader.stubs.SystemStub.getProperty("java.version"))
        assertNull(com.lagradost.runtime.loader.stubs.SystemStub.getProperty("user.home"))
        assertNull(com.lagradost.runtime.loader.stubs.SystemStub.getenv("AWS_SECRET_KEY"))
        assertEquals(emptyMap(), com.lagradost.runtime.loader.stubs.SystemStub.getenv())
    }
}
