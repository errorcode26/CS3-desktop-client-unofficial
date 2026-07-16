package com.lagradost.cloudstream3.desktop.plugins.vega

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import com.lagradost.cloudstream3.app

object VegaExtensionManager {
    private const val DEFAULT_REPO = "vega-providers"
    private const val DEFAULT_BRANCH = "main"
    private const val RAW_GITHUB_HOST = "raw.githubusercontent.com"

    private fun buildRawGithubUrl(author: String, repo: String = DEFAULT_REPO, branch: String = DEFAULT_BRANCH): String {
        return "https://$RAW_GITHUB_HOST/$author/$repo/refs/heads/$branch"
    }

    fun parseSourceUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        
        if (trimmed.startsWith("http")) {
            return trimmed
        }
        
        // Treat as author shortcode
        val author = trimmed.removePrefix("@")
        return buildRawGithubUrl(author)
    }

    private fun getManifestDir(): File {
        val dir = File("../vega-bridge/manifests")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun getProvidersDir(): File {
        val dir = File("../vega-bridge/providers")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun installExtension(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val manifestUrl = if (!url.endsWith(".json")) {
                "${url.removeSuffix("/")}/manifest.json"
            } else {
                url
            }
            println("Downloading Vega manifest in Kotlin: $manifestUrl")
            
            // Fetch manifest using NiceHttp client (which handles DoH and bypasses ISP blocks)
            val response = app.get(manifestUrl).text
            
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            val tree = mapper.readTree(response)
            if (!tree.isArray) {
                println("Invalid manifest format: Not an array")
                return@withContext false
            }

            // Save locally to manifests folder
            val hash = md5(url)
            val manifestFile = File(getManifestDir(), "$hash.json")
            
            val data = mapOf(
                "url" to url,
                "providers" to tree
            )
            mapper.writeValue(manifestFile, data)
            println("Successfully saved manifest to ${manifestFile.absolutePath}")
            return@withContext true
        } catch (e: Exception) {
            println("Failed to download or parse manifest in Kotlin: ${e.message}")
        }
        return@withContext false
    }

    suspend fun installProvider(provider: String, baseUrl: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destDir = File(getProvidersDir(), provider)
            if (!destDir.exists()) destDir.mkdirs()

            val files = listOf("posts", "meta", "stream", "catalog", "episodes")
            var successFiles = 0

            for (file in files) {
                var fileContent: String? = null
                val distUrl = "${baseUrl.removeSuffix("/")}/dist/$provider/$file.js"
                try {
                    println("Downloading Vega provider JS: $distUrl")
                    fileContent = app.get(distUrl).text
                } catch (e: Exception) {
                    val fallbackUrl = "${baseUrl.removeSuffix("/")}/$provider/$file.js"
                    try {
                        println("Downloading Vega provider JS (fallback): $fallbackUrl")
                        fileContent = app.get(fallbackUrl).text
                    } catch (err: Exception) {
                        // Optional files like episodes.js might not exist
                    }
                }

                if (fileContent != null && fileContent.isNotBlank()) {
                    val localFile = File(destDir, "$file.js")
                    localFile.writeText(fileContent)
                    successFiles++
                }
            }

            if (successFiles > 0) {
                println("Successfully installed provider JS files: $provider")
                // Reload providers array
                VegaBridgeManager.loadProviders()
                return@withContext true
            }
        } catch (e: Exception) {
            println("Failed to install provider $provider: ${e.message}")
        }
        return@withContext false
    }
}
