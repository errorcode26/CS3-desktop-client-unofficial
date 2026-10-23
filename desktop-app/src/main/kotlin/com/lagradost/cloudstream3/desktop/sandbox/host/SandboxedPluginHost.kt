package com.lagradost.cloudstream3.desktop.sandbox.host

import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerProtocolSerializer
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerResponse
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerLauncher
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsJobObject
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsSandboxAcl
import com.lagradost.common.platform.PlatformPaths
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import org.slf4j.LoggerFactory
import java.io.BufferedWriter
import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Supervisor running in the main Compose app process.
 * Manages the lifecycle of the low-privilege sandboxed JVM worker,
 * multiplexing requests over anonymous pipes with automatic crash recovery.
 */
class SandboxedPluginHost(
    val containerName: String = "CloudStream.PluginWorker.Sandbox",
    val memoryLimitBytes: Long = 1024L * 1024L * 1024L // 1 GB
) : Closeable {

    private val logger = LoggerFactory.getLogger(SandboxedPluginHost::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mutex = Mutex()
    private val nextRequestId = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<WorkerResponse>>()

    private val loadedPluginPaths = ConcurrentHashMap.newKeySet<String>()

    private var currentJobObject: WindowsJobObject? = null
    private var currentProcess: WindowsAppContainerLauncher.SandboxedProcess? = null
    private var processWriter: BufferedWriter? = null

    /**
     * Initializes and starts the sandboxed worker process.
     */
    suspend fun start() {
        mutex.withLock {
            if (currentProcess?.isAlive() == true) return
            launchWorkerInternal()
        }
    }

    private fun launchWorkerInternal() {
        logger.info("Initializing sandboxed extension worker in container '$containerName' (1 GB Cap)...")

        val sid = WindowsAppContainerApi.getOrCreateAppContainer(
            containerName = containerName,
            displayName = "CloudStream Extension Sandbox",
            description = "Sandboxed low-privilege runtime for CloudStream plugins",
            capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT)
        ) ?: throw IOException("Failed to obtain AppContainer SID for '$containerName'")

        // Grant AppContainer permissions to plugin storage and extensions directories
        val extensionsDir = PlatformPaths.extensionsDir
        if (!extensionsDir.exists()) extensionsDir.mkdirs()
        WindowsSandboxAcl.grantFolderAccess(extensionsDir, sid, readOnly = false)

        // Grant read access to classpath libraries so child JVM can load dependencies
        val classpath = System.getProperty("java.class.path") ?: ""
        val classpathEntries = classpath.split(File.pathSeparator).filter { it.isNotBlank() }
        val grantedRoots = mutableSetOf<String>()

        for (entry in classpathEntries) {
            val file = File(entry)
            val parent = (if (file.isDirectory) file else file.parentFile)?.absolutePath ?: continue
            if (grantedRoots.add(parent)) {
                try {
                    WindowsSandboxAcl.grantFolderAccess(File(parent), sid, readOnly = true)
                } catch (_: Throwable) {}
            }
        }

        val javaHome = File(System.getProperty("java.home"))
        val javaExe = File(javaHome, "bin/java.exe").absolutePath

        val job = WindowsJobObject(
            jobName = "CS3_PluginWorker_Job_${System.currentTimeMillis()}",
            memoryLimitBytes = memoryLimitBytes
        )
        this.currentJobObject = job

        val commandLine = "\"$javaExe\" -Djava.awt.headless=true -Xms32m -Xmx768m -XX:MaxMetaspaceSize=128m -cp \"$classpath\" com.lagradost.cloudstream3.desktop.sandbox.worker.PluginWorkerMain"

        val process = WindowsAppContainerLauncher.launch(
            commandLine = commandLine,
            containerName = containerName,
            workingDir = File(System.getenv("SystemRoot") ?: "C:\\Windows", "System32"),
            capabilities = listOf(WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT),
            jobObject = job
        )

        this.currentProcess = process
        this.processWriter = BufferedWriter(OutputStreamWriter(process.stdin, StandardCharsets.UTF_8))

        // Start background stdout reader
        scope.launch {
            val reader = process.stdout.bufferedReader(StandardCharsets.UTF_8)
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break
                    val trimmed = line.trim()
                    if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
                        logger.debug("Ignoring non-JSON line from worker stdout: {}", trimmed)
                        continue
                    }

                    try {
                        val resp = WorkerProtocolSerializer.deserializeResponse(trimmed)
                        val deferred = pendingRequests.remove(resp.id)
                        deferred?.complete(resp)
                    } catch (e: Throwable) {
                        logger.error("Failed to parse response JSON from worker: $line", e)
                    }
                }
            } catch (e: Throwable) {
                logger.warn("Worker stdout reader stream closed: ${e.message}")
            } finally {
                handleWorkerTermination()
            }
        }

        // Start background stderr logger
        scope.launch {
            val errReader = process.stderr.bufferedReader(StandardCharsets.UTF_8)
            try {
                while (isActive) {
                    val errLine = errReader.readLine() ?: break
                    if (errLine.isNotBlank()) {
                        logger.warn("[Worker STDERR] $errLine")
                    }
                }
            } catch (_: Throwable) {}
        }

        logger.info("Sandboxed worker process started successfully (PID: ${process.pid})")
    }

    private fun handleWorkerTermination() {
        val pending = pendingRequests.values.toList()
        pendingRequests.clear()

        for (p in pending) {
            p.completeExceptionally(IOException("Sandboxed worker process terminated unexpectedly"))
        }

        currentProcess?.close()
        currentProcess = null
        processWriter = null
        currentJobObject?.close()
        currentJobObject = null

        logger.warn("Sandboxed worker process terminated. Ready for auto-recovery on next query.")
    }

    private suspend fun sendRequest(request: WorkerRequest, timeoutMs: Long = 30_000L): WorkerResponse {
        val deferred = CompletableDeferred<WorkerResponse>()
        pendingRequests[request.id] = deferred

        mutex.withLock {
            if (currentProcess?.isAlive() != true) {
                launchWorkerInternal()
                // Re-register previously loaded plugins upon fresh process reboot
                for (path in loadedPluginPaths) {
                    sendDirect(WorkerRequest(id = nextRequestId.getAndIncrement(), action = WorkerRequest.ACTION_LOAD_PLUGIN, pluginPath = path))
                }
            }
            sendDirect(request)
        }

        return withTimeout(timeoutMs) {
            deferred.await()
        }
    }

    private fun sendDirect(request: WorkerRequest) {
        val writer = processWriter ?: throw IOException("Process stdin writer is not available")
        val json = WorkerProtocolSerializer.serializeRequest(request)
        writer.write(json)
        writer.newLine()
        writer.flush()
    }

    // High-Level Suspending API

    suspend fun ping(): Boolean {
        val req = WorkerRequest(id = nextRequestId.getAndIncrement(), action = WorkerRequest.ACTION_PING)
        val resp = sendRequest(req, timeoutMs = 5000L)
        return resp.success
    }

    suspend fun loadPlugin(jarFile: File): Boolean {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD_PLUGIN,
            pluginPath = jarFile.absolutePath
        )
        val resp = sendRequest(req, timeoutMs = 30_000L)
        if (resp.success) {
            loadedPluginPaths.add(jarFile.absolutePath)
        }
        return resp.success
    }

    suspend fun search(providerName: String, query: String): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_SEARCH,
            providerName = providerName,
            query = query
        )
        val resp = sendRequest(req, timeoutMs = 45_000L)
        if (!resp.success) throw IOException(resp.error ?: "Search failed")
        return resp.payloadJson
    }

    suspend fun load(providerName: String, url: String): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD,
            providerName = providerName,
            url = url
        )
        val resp = sendRequest(req, timeoutMs = 45_000L)
        if (!resp.success) throw IOException(resp.error ?: "Load failed")
        return resp.payloadJson
    }

    suspend fun loadLinks(providerName: String, data: String, isCasting: Boolean = false): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD_LINKS,
            providerName = providerName,
            data = data,
            isCasting = isCasting
        )
        val resp = sendRequest(req, timeoutMs = 45_000L)
        if (!resp.success) throw IOException(resp.error ?: "LoadLinks failed")
        return resp.payloadJson
    }

    suspend fun getLoadedProviders(): List<String> {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_GET_LOADED_PROVIDERS
        )
        val resp = sendRequest(req, timeoutMs = 10_000L)
        if (!resp.success || resp.payloadJson == null) return emptyList()
        return WorkerProtocolSerializer.mapper.readValue(
            resp.payloadJson,
            WorkerProtocolSerializer.mapper.typeFactory.constructCollectionType(List::class.java, String::class.java)
        )
    }

    override fun close() {
        try {
            currentProcess?.destroy()
        } catch (_: Throwable) {}
        currentProcess = null
        currentJobObject?.close()
        currentJobObject = null
    }
}
