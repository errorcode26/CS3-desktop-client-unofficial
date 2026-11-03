package com.lagradost.cloudstream3.desktop.repo

import com.lagradost.common.platform.PlatformPaths
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopRepositoryManagerRollbackTest {

    private val extensionsDir = PlatformPaths.extensionsDir
    private val reposFile = File(extensionsDir, "repos.json")
    private val repoCacheFile = File(extensionsDir, "repo_cache.json")
    private val pluginsCacheFile = File(extensionsDir, "plugins_cache.json")

    private var originalReposText: String? = null
    private var originalRepoCacheText: String? = null
    private var originalPluginsCacheText: String? = null

    private var server: HttpServer? = null
    private var repoDir: File? = null
    private var pluginStem: String? = null

    @BeforeEach
    fun setUp() {
        originalReposText = reposFile.takeIf { it.exists() }?.readText()
        originalRepoCacheText = repoCacheFile.takeIf { it.exists() }?.readText()
        originalPluginsCacheText = pluginsCacheFile.takeIf { it.exists() }?.readText()
        DesktopRepositoryManager.clearCaches()
    }

    @AfterEach
    fun tearDown() {
        try {
            server?.stop(0)
        } catch (_: Throwable) {}
        try {
            repoDir?.deleteRecursively()
        } catch (_: Throwable) {}
        pluginStem?.let { stem ->
            val cacheDir = PlatformPaths.pluginCacheDir
            listOf(
                "$stem-secure.jar",
                "$stem-jvm.jar",
                "$stem.d2j_failed",
                "$stem.load_failed",
                "$stem-secure.jar.tmp",
            ).forEach { name ->
                try {
                    File(cacheDir, name).delete()
                } catch (_: Throwable) {}
            }
        }
        restoreFile(reposFile, originalReposText)
        restoreFile(repoCacheFile, originalRepoCacheText)
        restoreFile(pluginsCacheFile, originalPluginsCacheText)
        DesktopRepositoryManager.clearCaches()
        runBlocking {
            try {
                DesktopRepositoryManager.initialize()
            } catch (_: Throwable) {}
        }
    }

    @Test
    fun testFailedUpdateRestoresOriginalJarAndReportsFailure() = runBlocking {
        val suffix = System.nanoTime().toString()
        val repoName = "RollbackRepo_$suffix"
        val internalName = "RollbackTest_$suffix"
        val displayName = "Rollback Test $suffix"
        pluginStem = internalName

        val localRepoDir = File(extensionsDir, repoName).apply { mkdirs() }
        repoDir = localRepoDir
        val localJar = File(localRepoDir, "$internalName.jar")

        val originalJarBytes = createPluginJar(
            destination = localJar,
            internalName = internalName,
            displayName = displayName,
            version = 1,
            includePluginClassName = true,
        )

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server = httpServer
        val port = httpServer.address.port
        val baseUrl = "http://127.0.0.1:$port"

        val repoJson = """
            {
              "name": "$repoName",
              "description": "Rollback test repository",
              "manifestVersion": 1,
              "pluginLists": ["$baseUrl/plugins.json"]
            }
        """.trimIndent()
        val pluginsJson = """
            [
              {
                "name": "$displayName",
                "internalName": "$internalName",
                "url": "$baseUrl/plugin.jar",
                "version": 2,
                "status": 1
              }
            ]
        """.trimIndent()
        val brokenJarFile = File.createTempFile("rollback-update-", ".jar")
        val brokenUpdateJar = try {
            createPluginJar(
                destination = brokenJarFile,
                internalName = internalName,
                displayName = displayName,
                version = 2,
                includePluginClassName = false,
            )
        } finally {
            brokenJarFile.delete()
        }

        httpServer.createContext("/repo.json") { exchange ->
            respond(exchange, repoJson.toByteArray(Charsets.UTF_8), "application/json")
        }
        httpServer.createContext("/plugins.json") { exchange ->
            respond(exchange, pluginsJson.toByteArray(Charsets.UTF_8), "application/json")
        }
        httpServer.createContext("/plugin.jar") { exchange ->
            respond(exchange, brokenUpdateJar, "application/java-archive")
        }
        httpServer.start()

        val reposJson = """
            [
              {
                "name": "$repoName",
                "url": "$baseUrl/repo.json",
                "iconUrl": null
              }
            ]
        """.trimIndent()
        reposFile.parentFile?.mkdirs()
        reposFile.writeText(reposJson)
        repoCacheFile.delete()
        pluginsCacheFile.delete()
        DesktopRepositoryManager.initialize()

        assertTrue(
            DesktopRepositoryManager.getSavedRepositories().any { it.url == "$baseUrl/repo.json" },
            "Test repository must be visible to the update manager",
        )

        val updates = DesktopRepositoryManager.autoUpdatePlugins(force = true)

        val failure = updates.singleOrNull { it.pluginName == displayName }
        assertNotNull(failure, "Failed update must be recorded in the update history")
        assertFalse(failure.isSuccess, "Malformed update must not be reported as successful")
        assertTrue(
            failure.errorMessage?.contains("verification", ignoreCase = true) == true ||
                failure.errorMessage?.contains("transpil", ignoreCase = true) == true ||
                failure.errorMessage?.contains("bytecode", ignoreCase = true) == true,
            "Failure should come from bytecode verification, not a download error: ${failure.errorMessage}",
        )

        assertTrue(localJar.exists(), "Rollback must leave the original plugin jar in place")
        assertContentEquals(originalJarBytes, localJar.readBytes(), "Rollback must restore the original jar bytes")
        assertFalse(
            File(localRepoDir, "${localJar.name}.bak").exists(),
            "Backup file must be removed after a completed rollback",
        )
        assertEquals(
            1,
            DesktopRepositoryManager.readPluginManifest(localJar)?.get("version")?.toString()?.toIntOrNull(),
            "Restored plugin must still be the original version",
        )
    }

    private fun respond(exchange: HttpExchange, body: ByteArray, contentType: String) {
        exchange.responseHeaders.add("Content-Type", contentType)
        exchange.sendResponseHeaders(200, body.size.toLong())
        exchange.responseBody.use { it.write(body) }
    }

    /**
     * Writes a minimal plugin archive that passes [PluginFileUtils.validatePluginArchive]
     * but contains deliberately invalid JVM bytecode. Omitting pluginClassName forces
     * verification to fail deterministically before any class is instantiated.
     */
    private fun createPluginJar(
        destination: File,
        internalName: String,
        displayName: String,
        version: Int,
        includePluginClassName: Boolean,
    ): ByteArray {
        destination.parentFile?.mkdirs()
        val manifest = buildString {
            append("{")
            append("\"name\":\"$displayName\",")
            append("\"internalName\":\"$internalName\",")
            append("\"version\":$version")
            if (includePluginClassName) {
                append(",\"pluginClassName\":\"com.example.RollbackTestPlugin\"")
            }
            append("}")
        }
        ZipOutputStream(destination.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("manifest.json"))
            zip.write(manifest.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("Broken.class"))
            zip.write(ByteArray(512) { 0x42 })
            zip.closeEntry()
        }
        return destination.readBytes()
    }

    private fun restoreFile(file: File, content: String?) {
        try {
            if (content != null) {
                file.parentFile?.mkdirs()
                file.writeText(content)
            } else {
                file.delete()
            }
        } catch (_: Throwable) {}
    }
}
