package com.synthetic.test

import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Synthetic Plugin implementation compiled by Gradle during test execution.
 * Used for live sandbox verification, aggregator file I/O tests, and hot-reload tests.
 */
class SyntheticTestPlugin : Plugin() {
    override fun load(context: Context) {
        registerMainAPI(SyntheticAggregatorApi())
    }
}

class SyntheticAggregatorApi : MainAPI() {
    override var name = "Synthetic Aggregator Provider"
    override var mainUrl = "https://synthetic.aggregator.local"
    override var lang = "en"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)

    fun performAggregatorOperations(baseDir: File): Int {
        val scratchDir = File(baseDir, "aggregator_scratch").apply { if (!exists()) mkdirs() }
        var count = 0
        for (i in 1..25) {
            val file = File(scratchDir, "cache_entry_$i.json")
            file.writeText("{\"id\": $i, \"name\": \"Cached Item $i\", \"status\": \"OK\"}", Charsets.UTF_8)
            if (file.readText(Charsets.UTF_8).contains("Cached Item $i")) {
                count++
            }
        }
        return count
    }
}

/**
 * Dynamic in-memory generator for synthetic Plugin JAR files.
 * Provides real .jar archives with valid manifests and JVM bytecode for automated tests.
 */
object SyntheticPluginFixture {

    fun createPluginJar(
        destinationFile: File,
        version: Int = 1,
        internalName: String = "SyntheticAggregator",
        displayName: String = "Synthetic Aggregator Provider",
    ): File {
        destinationFile.parentFile?.mkdirs()

        val manifestJson = """
            {
              "name": "$displayName",
              "pluginClassName": "${SyntheticTestPlugin::class.java.name}",
              "version": $version,
              "internalName": "$internalName",
              "authors": ["Automated Test Suite"],
              "description": "Synthetic aggregator plugin for zero-manual-testing automated verification"
            }
        """.trimIndent()

        val pluginClassRes = SyntheticTestPlugin::class.java.name.replace('.', '/') + ".class"
        val apiClassRes = SyntheticAggregatorApi::class.java.name.replace('.', '/') + ".class"

        val pluginClassBytes = SyntheticTestPlugin::class.java.classLoader.getResourceAsStream(pluginClassRes)?.readBytes()
            ?: error("Could not locate compiled bytecode for $pluginClassRes")
        val apiClassBytes = SyntheticAggregatorApi::class.java.classLoader.getResourceAsStream(apiClassRes)?.readBytes()
            ?: error("Could not locate compiled bytecode for $apiClassRes")

        FileOutputStream(destinationFile).use { fos ->
            ZipOutputStream(fos).use { zos ->
                // 1. Add manifest.json
                zos.putNextEntry(ZipEntry("manifest.json"))
                zos.write(manifestJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()

                // 2. Add compiled Plugin class
                zos.putNextEntry(ZipEntry(pluginClassRes))
                zos.write(pluginClassBytes)
                zos.closeEntry()

                // 3. Add compiled MainAPI class
                zos.putNextEntry(ZipEntry(apiClassRes))
                zos.write(apiClassBytes)
                zos.closeEntry()
            }
        }

        return destinationFile
    }
}
