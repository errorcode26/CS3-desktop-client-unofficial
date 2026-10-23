package com.lagradost.cloudstream3.desktop.sandbox.worker

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerProtocolSerializer
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerResponse
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * Headless Entrypoint executing exclusively inside the Windows AppContainer and Job Object.
 * Communicates with the main Compose app via line-delimited JSON-RPC over stdin/stdout.
 */
object PluginWorkerMain {

    private val logger = LoggerFactory.getLogger(PluginWorkerMain::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        System.setProperty("java.awt.headless", "true")

        // Capture raw stdout for RPC pipe before redirecting standard console logging to stderr
        val rawStdout = System.out
        System.setOut(System.err)

        logger.info("Sandboxed PluginWorkerMain started. PID: ${ProcessHandle.current().pid()}")

        val reader = BufferedReader(InputStreamReader(System.`in`, StandardCharsets.UTF_8))
        val writer = BufferedWriter(OutputStreamWriter(rawStdout, StandardCharsets.UTF_8))

        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue

            val req = try {
                WorkerProtocolSerializer.deserializeRequest(line)
            } catch (e: Throwable) {
                logger.error("Failed to parse request JSON: $line", e)
                val errResp = WorkerResponse(id = -1, success = false, error = "Malformed JSON: ${e.message}")
                sendResponse(writer, errResp)
                continue
            }

            val resp = try {
                handleRequest(req)
            } catch (e: Throwable) {
                logger.error("Error executing action '${req.action}' for ID ${req.id}", e)
                WorkerResponse(id = req.id, success = false, error = "${e.javaClass.simpleName}: ${e.message}")
            }

            sendResponse(writer, resp)
        }

        logger.info("Sandboxed PluginWorkerMain exiting cleanly (stdin closed).")
    }

    private fun handleRequest(req: WorkerRequest): WorkerResponse {
        return when (req.action) {
            WorkerRequest.ACTION_PING -> {
                WorkerResponse(id = req.id, success = true, payloadJson = "\"pong\"")
            }

            WorkerRequest.ACTION_LOAD_PLUGIN -> {
                val path = req.pluginPath ?: error("Missing pluginPath")
                val jarFile = File(path)
                if (!jarFile.exists()) {
                    error("Plugin file not found: $path")
                }
                ExtensionLoader.loadAndInit(jarFile)
                WorkerResponse(id = req.id, success = true)
            }

            WorkerRequest.ACTION_SEARCH -> {
                val providerName = req.providerName ?: error("Missing providerName")
                val query = req.query ?: error("Missing query")
                val provider = APIHolder.allProviders.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: error("Provider '$providerName' not found")

                val results = runBlocking { provider.search(query) }
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(results)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_LOAD -> {
                val providerName = req.providerName ?: error("Missing providerName")
                val url = req.url ?: error("Missing url")
                val provider = APIHolder.allProviders.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: error("Provider '$providerName' not found")

                val details = runBlocking { provider.load(url) }
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(details)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_LOAD_LINKS -> {
                val providerName = req.providerName ?: error("Missing providerName")
                val data = req.data ?: error("Missing data")
                val provider = APIHolder.allProviders.firstOrNull { it.name.equals(providerName, ignoreCase = true) }
                    ?: error("Provider '$providerName' not found")

                val links = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorLink>()
                val subs = mutableListOf<com.lagradost.cloudstream3.SubtitleFile>()

                runBlocking {
                    provider.loadLinks(
                        data = data,
                        isCasting = req.isCasting,
                        subtitleCallback = { subs.add(it) },
                        callback = { links.add(it) }
                    )
                }

                val payload = mapOf("links" to links, "subs" to subs)
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(payload)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            WorkerRequest.ACTION_GET_LOADED_PROVIDERS -> {
                val providers = APIHolder.allProviders.map { it.name }
                val json = WorkerProtocolSerializer.mapper.writeValueAsString(providers)
                WorkerResponse(id = req.id, success = true, payloadJson = json)
            }

            else -> {
                WorkerResponse(id = req.id, success = false, error = "Unknown action '${req.action}'")
            }
        }
    }

    private fun sendResponse(writer: BufferedWriter, resp: WorkerResponse) {
        synchronized(writer) {
            val json = WorkerProtocolSerializer.serializeResponse(resp)
            writer.write(json)
            writer.newLine()
            writer.flush()
        }
    }
}
