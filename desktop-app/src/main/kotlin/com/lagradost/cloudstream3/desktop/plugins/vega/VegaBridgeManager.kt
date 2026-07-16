package com.lagradost.cloudstream3.desktop.plugins.vega

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.File
import java.net.ServerSocket
import com.lagradost.cloudstream3.utils.AppUtils
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.delay
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue

object VegaBridgeManager {
    private var process: Process? = null
    var port: Int = 3000
        private set
        
    val client = OkHttpClient()

    // Configured ObjectMapper that ignores unknown JSON properties to prevent crashes on provider schema changes
    val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private fun getFreePort(): Int {
        ServerSocket(0).use { return it.localPort }
    }

    // Standard Java GET helper to bypass global OkHttp proxy/DNS interceptors for localhost
    // readTimeoutMs: use a longer value (e.g. 60000) for endpoints like /getStream that
    // involve chained HTTP redirects in the Node bridge and can take 20-40 seconds.
    inline fun <reified T> makeLocalGet(urlStr: String, readTimeoutMs: Int = 3000): T? {
        return try {
            val url = java.net.URL(urlStr)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = readTimeoutMs
            connection.useCaches = false
            
            val responseCode = connection.responseCode
            if (responseCode == java.net.HttpURLConnection.HTTP_OK) {
                val json = connection.inputStream.bufferedReader().use { it.readText() }
                mapper.readValue<T>(json)
            } else {
                null
            }
        } catch (e: Exception) {
            println("Local GET request failed for $urlStr: ${e.message}")
            null
        }
    }

    suspend fun startBridge() = withContext(Dispatchers.IO) {
        if (process?.isAlive == true) return@withContext

        port = getFreePort()
        println("Starting Vega Bridge on port $port...")

        val workingDir = File("../vega-bridge")
        val isWindows = System.getProperty("os.name").lowercase().contains("win")
        val command = if (isWindows) {
            listOf("cmd.exe", "/c", "node server.js $port")
        } else {
            listOf("node", "server.js", "$port")
        }

        try {
            process = ProcessBuilder(command)
                .directory(workingDir)
                .redirectErrorStream(true)
                .start()

            // Spawn coroutine to pipe stdout/stderr to JVM logs
            @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                process?.inputStream?.bufferedReader()?.use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        println("[Vega Node Bridge] $line")
                    }
                }
            }

            // Ping the bridge until it's ready (max 5 seconds)
            var ready = false
            for (i in 1..25) {
                delay(200)
                try {
                    val pingUrl = "http://127.0.0.1:$port/getProviders"
                    val connection = java.net.URL(pingUrl).openConnection() as java.net.HttpURLConnection
                    connection.connectTimeout = 200
                    connection.readTimeout = 200
                    if (connection.responseCode == 200) {
                        ready = true
                        break
                    }
                } catch (e: Exception) {}
            }

            if (ready) {
                println("Vega Bridge started successfully on port $port")
                loadProviders()
                
                // Sync in background and reload to fetch updates/missing repos
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                    try {
                        VegaRepositoryManager.syncAll()
                        loadProviders()
                    } catch (e: Exception) {
                        println("Background Vega sync failed: ${e.message}")
                    }
                }
            } else {
                println("Vega Bridge failed to respond to ping")
            }
        } catch (e: Exception) {
            println("Failed to start Vega Bridge: ${e.message}")
        }
    }

    suspend fun getInstalledProviders(): List<String> = withContext(Dispatchers.IO) {
        val url = "http://127.0.0.1:$port/getProviders"
        return@withContext makeLocalGet<List<String>>(url) ?: emptyList()
    }

    suspend fun loadProviders() {
        val available = getAvailableProviders().map { it.value }.toSet()
        val installed = getInstalledProviders()
        
        // Clean up orphaned local provider directories that don't belong to any active manifests
        installed.forEach { providerName ->
            if (!available.contains(providerName)) {
                try {
                    val folder = File("../vega-bridge/providers", providerName)
                    if (folder.exists()) {
                        folder.deleteRecursively()
                        println("Automatically cleaned up orphaned Vega provider folder: $providerName")
                    }
                } catch (e: Exception) {
                    println("Failed to cleanup orphaned folder $providerName: ${e.message}")
                }
            }
        }

        // Fetch the clean list after orphaned cleanup
        val cleanInstalled = getInstalledProviders()
        println("Loaded ${cleanInstalled.size} Vega providers: $cleanInstalled")
        
        synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
            // Clean up old instances to avoid duplicates upon hot-reloads
            com.lagradost.cloudstream3.APIHolder.allProviders.removeIf { it is VegaProxyProvider }
            
            cleanInstalled.forEach { providerName ->
                val proxy = VegaProxyProvider(providerName)
                com.lagradost.cloudstream3.APIHolder.allProviders.add(proxy)
            }
        }
    }

    suspend fun getAvailableProviders(): List<VegaAvailableProvider> = withContext(Dispatchers.IO) {
        val url = "http://127.0.0.1:$port/getAvailableProviders"
        return@withContext makeLocalGet<List<VegaAvailableProvider>>(url) ?: emptyList()
    }

    fun stopBridge() {
        if (process?.isAlive == true) {
            println("Stopping Vega Bridge...")
            process?.destroy()
            process = null
        }
    }

    suspend fun uninstallProvider(providerName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val destDir = File("../vega-bridge/providers", providerName)
            if (destDir.exists()) {
                destDir.deleteRecursively()
                println("Recursively deleted Vega provider folder: ${destDir.absolutePath}")
            }
            synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                com.lagradost.cloudstream3.APIHolder.allProviders.removeIf { 
                    it.name.equals(providerName, ignoreCase = true) 
                }
            }
            return@withContext true
        } catch (e: Exception) {
            println("Failed to uninstall Vega provider: ${e.message}")
        }
        return@withContext false
    }
}

data class VegaAvailableProvider(
    val value: String,
    val display_name: String,
    val icon: String,
    val type: String,
    val version: String,
    val baseUrl: String
)
