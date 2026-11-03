package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.common.platform.PlatformPaths
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PluginFileUtilsTest {

    @Test
    fun cacheArtifactsForStemMatchesExactStemOnly() {
        val cacheDir = PlatformPaths.pluginCacheDir
        val stem = "Cs3StemTest${System.nanoTime()}"
        val ownedNames = setOf(
            "$stem-jvm.jar",
            "$stem-secure.jar",
            "$stem.dex",
            "$stem.d2j_failed",
            "$stem.load_failed",
            "${stem}_${System.currentTimeMillis()}-secure.jar",
        )
        val foreignNames = setOf(
            "${stem}Provider-jvm.jar",
            "${stem}Extra-secure.jar",
        )
        val created = (ownedNames + foreignNames).map { name ->
            File(cacheDir, name).apply { writeText("test") }
        }
        try {
            val found = PluginFileUtils.cacheArtifactsForStem(stem)
                .map { it.name }
                .filter { it.startsWith(stem, ignoreCase = true) }
                .toSet()
            assertEquals(ownedNames, found, "Only exact-stem cache artifacts may be selected")
        } finally {
            created.forEach { it.delete() }
        }
    }

    @Test
    fun cleanStaleTempFilesRemovesOldFallbacksAndOrphans() {
        val cacheDir = PlatformPaths.pluginCacheDir
        val stem = "Cs3SweepTest${System.nanoTime()}"
        val old = System.currentTimeMillis() - (20 * 60 * 1000L)
        val now = System.currentTimeMillis()
        val oldStamp = now - (20 * 60 * 1000L)

        val oldFallback = File(cacheDir, "${stem}_${oldStamp}-secure.jar").apply {
            writeText("old")
            setLastModified(old)
        }
        val freshFallback = File(cacheDir, "${stem}_${now}-secure.jar").apply {
            writeText("fresh")
            setLastModified(now)
        }
        val orphanArtifact = File(cacheDir, "$stem-jvm.jar").apply {
            writeText("orphan")
            setLastModified(old)
        }

        try {
            PluginFileUtils.cleanStaleTempFiles()

            assertFalse(oldFallback.exists(), "Old timestamped fallback jars must be reclaimed")
            assertTrue(freshFallback.exists(), "Fresh cache artifacts must be preserved")
            assertFalse(orphanArtifact.exists(), "Cache artifacts without a source plugin must be reclaimed")
        } finally {
            oldFallback.delete()
            freshFallback.delete()
            orphanArtifact.delete()
        }
    }

    @Test
    fun downloadPluginRemovesSupersededCs3Artifact() = runBlocking {
        val repoName = "Cs3RepoTest${System.nanoTime()}"
        val internalName = "Cs3PluginTest${System.nanoTime()}"
        val repoDir = File(PlatformPaths.extensionsDir, repoName)
        repoDir.mkdirs()
        val legacyCs3 = File(repoDir, "$internalName.cs3").apply { writeText("legacy") }
        val archive = buildPluginArchive(internalName)

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/plugin.jar") { exchange ->
            exchange.sendResponseHeaders(200, archive.size.toLong())
            exchange.responseBody.use { it.write(archive) }
        }
        server.start()
        try {
            val plugin = SitePlugin(
                name = "Test Plugin",
                internalName = internalName,
                url = "http://127.0.0.1:${server.address.port}/plugin.jar",
                version = 1,
            )
            val downloaded = PluginFileUtils.downloadPlugin(repoName, plugin)

            val downloadedFile = assertNotNull(downloaded)
            assertTrue(downloadedFile.exists(), "Downloaded plugin must be written to disk")
            assertFalse(legacyCs3.exists(), "A superseded .cs3 for the same plugin must be removed")
        } finally {
            server.stop(0)
            repoDir.deleteRecursively()
        }
    }

    private fun buildPluginArchive(internalName: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            val manifest =
                """{"internalName":"$internalName","name":"Test Plugin","pluginClassName":"test.Test","version":1}"""
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write(ByteArray(256) { it.toByte() })
            zip.closeEntry()
        }
        return out.toByteArray()
    }
}
