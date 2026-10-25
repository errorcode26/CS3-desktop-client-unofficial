package com.lagradost.cloudstream3.desktop.sandbox.host

import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedProviderMetadata
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SerializableCookie
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerCallbackRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerCallbackResponse
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerProtocolSerializer
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerRequest
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerResponse
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerApi
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsAppContainerLauncher
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsJobObject
import com.lagradost.cloudstream3.desktop.sandbox.windows.WindowsSandboxAcl
import com.lagradost.common.platform.PlatformPaths
import okhttp3.RequestBody.Companion.toRequestBody
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
    val memoryLimitBytes: Long = 2048L * 1024L * 1024L // 2 GB
) : Closeable {

    companion object {
        private val grantedSids = ConcurrentHashMap.newKeySet<String>()
    }

    private val logger = LoggerFactory.getLogger(SandboxedPluginHost::class.java)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val mutex = Mutex()
    private val nextRequestId = AtomicLong(1)
    private val pendingRequests = ConcurrentHashMap<Long, CompletableDeferred<WorkerResponse>>()

    private val loadedPluginPaths = ConcurrentHashMap.newKeySet<String>()

    data class ActiveLinkConsumer(
        val onLink: ((com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit)?,
        val onSubtitle: ((com.lagradost.cloudstream3.SubtitleFile) -> Unit)?,
    )

    private val activeLinkConsumers = ConcurrentHashMap<Long, ActiveLinkConsumer>()

    private var currentJobObject: WindowsJobObject? = null
    private var currentProcess: WindowsAppContainerLauncher.SandboxedProcess? = null
    private var processWriter: BufferedWriter? = null
    @Volatile
    var isHostAvailable: Boolean = true

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
            capabilities = listOf(
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT,
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT_SERVER,
                WindowsAppContainerApi.CAPABILITY_PRIVATE_NETWORK_CLIENT_SERVER
            )
        ) ?: throw IOException("Failed to obtain AppContainer SID for '$containerName'")

        if (grantedSids.add(sid)) {
            // Grant AppContainer permissions to plugin storage and dedicated sandbox directories
            val extensionsDir = PlatformPaths.extensionsDir
            if (!extensionsDir.exists()) extensionsDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(extensionsDir, sid, readOnly = false)

            val sandboxDir = PlatformPaths.sandboxDir
            if (!sandboxDir.exists()) sandboxDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(sandboxDir, sid, readOnly = false)

            val sandboxTmp = File(sandboxDir, "tmp")
            if (!sandboxTmp.exists()) sandboxTmp.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(sandboxTmp, sid, readOnly = false)

            // Grant read access to broad roots covering JRE, Gradle caches, and workspace modules
            val topRoots = mutableSetOf<File>()

            val gradleCache = File(System.getProperty("user.home"), ".gradle/caches")
            if (gradleCache.exists()) topRoots.add(gradleCache)

            val projectRoot = File(System.getProperty("user.dir"))
            if (projectRoot.exists()) topRoots.add(projectRoot)

            val javaHome = File(System.getProperty("java.home"))
            if (javaHome.exists()) topRoots.add(javaHome)

            for (root in topRoots) {
                try {
                    WindowsSandboxAcl.grantFolderAccess(root, sid, readOnly = true)
                } catch (t: Throwable) {
                    logger.warn("Failed granting ACL to root ${root.absolutePath}: ${t.message}")
                }
            }

            // Grant read access to any external classpath entry not already covered by the top roots
            val classpath = System.getProperty("java.class.path") ?: ""
            val classpathEntries = classpath.split(File.pathSeparator).filter { it.isNotBlank() }
            val extraRoots = mutableSetOf<String>()

            for (entry in classpathEntries) {
                val file = File(entry)
                val absPath = file.absolutePath
                val alreadyCovered = topRoots.any { root ->
                    absPath.startsWith(root.absolutePath, ignoreCase = true)
                }
                if (!alreadyCovered) {
                    val parent = (if (file.isDirectory) file else file.parentFile)?.absolutePath ?: continue
                    if (extraRoots.add(parent)) {
                        try {
                            WindowsSandboxAcl.grantFolderAccess(File(parent), sid, readOnly = true)
                        } catch (_: Throwable) {}
                    }
                }
            }
        }

        val javaHome = File(System.getProperty("java.home"))
        var javaExeFile = File(javaHome, "bin/java.exe")
        if (!javaExeFile.exists()) {
            javaExeFile = File(javaHome, "java.exe")
        }
        if (!javaExeFile.exists()) {
            isHostAvailable = false
            throw java.io.FileNotFoundException("Sandbox worker executable not found at ${javaExeFile.absolutePath}")
        }
        val javaExe = javaExeFile.absolutePath

        val job = WindowsJobObject(
            jobName = "CS3_PluginWorker_Job_${System.currentTimeMillis()}",
            memoryLimitBytes = memoryLimitBytes
        )
        this.currentJobObject = job

        val sandboxTmp = File(PlatformPaths.sandboxDir, "tmp").apply { if (!exists()) mkdirs() }
        var classpath = System.getProperty("java.class.path") ?: ""
        if (classpath.isBlank() || !classpath.contains(File.pathSeparator)) {
            try {
                val codeSource = SandboxedPluginHost::class.java.protectionDomain?.codeSource?.location
                if (codeSource != null) {
                    val locationFile = File(codeSource.toURI())
                    val appDir = if (locationFile.isDirectory) locationFile else locationFile.parentFile
                    if (appDir != null && appDir.isDirectory) {
                        val bundledJars = appDir.listFiles { f -> f.extension.equals("jar", ignoreCase = true) }
                        if (!bundledJars.isNullOrEmpty()) {
                            val resolvedCp = bundledJars.joinToString(File.pathSeparator) { it.absolutePath }
                            if (resolvedCp.isNotBlank()) {
                                classpath = resolvedCp
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                logger.warn("Could not dynamically resolve packaged app classpath: ${t.message}")
            }
        }
        val commandLine = "\"$javaExe\" " +
            "-Djava.awt.headless=true " +
            "-Dcloudstream.sandbox.worker=true " +
            "-Djava.io.tmpdir=\"${sandboxTmp.absolutePath}\" " +
            "-Djna.tmpdir=\"${sandboxTmp.absolutePath}\" " +
            "-Djava.util.prefs.PreferencesFactory=com.lagradost.cloudstream3.desktop.sandbox.worker.NoOpPreferencesFactory " +
            "-Xms64m -Xmx768m -XX:MaxMetaspaceSize=384m -XX:ReservedCodeCacheSize=128m -XX:+UseG1GC " +
            "-cp \"$classpath\" " +
            "com.lagradost.cloudstream3.desktop.sandbox.worker.PluginWorkerMain"

        val process = WindowsAppContainerLauncher.launch(
            commandLine = commandLine,
            containerName = containerName,
            workingDir = PlatformPaths.sandboxDir,
            capabilities = listOf(
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT,
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT_SERVER,
                WindowsAppContainerApi.CAPABILITY_PRIVATE_NETWORK_CLIENT_SERVER
            ),
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

                    if (trimmed.contains("\"type\":\"CALLBACK_REQUEST\"") || trimmed.contains("\"callbackId\"")) {
                        try {
                            val cbReq = WorkerProtocolSerializer.deserializeCallbackRequest(trimmed)
                            scope.launch {
                                handleWorkerCallback(cbReq)
                            }
                        } catch (e: Throwable) {
                            logger.error("Failed to parse callback request from worker: $trimmed", e)
                        }
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

        val exit = try { currentProcess?.exitValue() } catch (_: Throwable) { null }
        val exitHex = exit?.let { "0x" + Integer.toHexString(it).uppercase() } ?: "unknown"

        for (p in pending) {
            p.completeExceptionally(IOException("Sandboxed worker process terminated unexpectedly (exit: $exitHex)"))
        }

        currentProcess?.close()
        currentProcess = null
        processWriter = null
        currentJobObject?.close()
        currentJobObject = null

        logger.warn("Sandboxed worker process terminated. Exit code: $exit ($exitHex). Ready for auto-recovery on next query.")
    }

    private suspend fun sendRequest(request: WorkerRequest, timeoutMs: Long = 30_000L): WorkerResponse {
        val deferred = CompletableDeferred<WorkerResponse>()
        pendingRequests[request.id] = deferred

        mutex.withLock {
            if (currentProcess?.isAlive() != true) {
                launchWorkerInternal()
                // Synchronize settings into fresh worker first so PREF_TLS_BOUND_HOSTS and configurations are available
                val settings = com.lagradost.common.storage.DesktopDataStore.rawKeyCache.toMap()
                if (settings.isNotEmpty()) {
                    runCatching {
                        sendDirectAwaitLocked(
                            WorkerRequest(
                                id = nextRequestId.getAndIncrement(),
                                action = WorkerRequest.ACTION_SYNC_SETTINGS,
                                settingsMap = settings
                            ),
                            5_000L
                        )
                    }
                }
                // Synchronize existing cookies into the fresh worker synchronously
                val vaultCookies = com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.getAllUnexpired().map { c ->
                    SerializableCookie(
                        name = c.name,
                        value = c.value,
                        domain = c.domain,
                        path = c.path,
                        expiresAt = c.expiresAt,
                        secure = c.secure,
                        httpOnly = c.httpOnly
                    )
                }
                if (vaultCookies.isNotEmpty()) {
                    runCatching {
                        sendDirectAwaitLocked(WorkerRequest(id = nextRequestId.getAndIncrement(), action = WorkerRequest.ACTION_SYNC_COOKIES, cookies = vaultCookies), 5_000L)
                    }
                }
                // Re-register previously loaded plugins synchronously before allowing subsequent requests
                for (path in loadedPluginPaths) {
                    runCatching {
                        sendDirectAwaitLocked(WorkerRequest(id = nextRequestId.getAndIncrement(), action = WorkerRequest.ACTION_LOAD_PLUGIN, pluginPath = path), 20_000L)
                    }
                }
            }
            sendDirect(request)
        }

        return withTimeout(timeoutMs) {
            deferred.await()
        }
    }

    private suspend fun sendDirectAwaitLocked(request: WorkerRequest, timeoutMs: Long): WorkerResponse {
        val deferred = CompletableDeferred<WorkerResponse>()
        pendingRequests[request.id] = deferred
        sendDirect(request)
        return withTimeout(timeoutMs) {
            deferred.await()
        }
    }

    private fun sendDirect(request: WorkerRequest) {
        val writer = processWriter ?: throw IOException("Process stdin writer is not available")
        val json = WorkerProtocolSerializer.serializeRequest(request)
        synchronized(writer) {
            writer.write(json)
            writer.newLine()
            writer.flush()
        }
    }

    private suspend fun handleWorkerCallback(cbReq: WorkerCallbackRequest) {
        if (cbReq.action == WorkerCallbackRequest.ACTION_EMIT_LINK) {
            val reqId = cbReq.requestId ?: return
            val linkJson = cbReq.linkJson ?: return
            val consumer = activeLinkConsumers[reqId] ?: return
            try {
                val link = WorkerProtocolSerializer.mapper.readValue(linkJson, com.lagradost.cloudstream3.utils.ExtractorLink::class.java)
                if (link != null) {
                    consumer.onLink?.invoke(link)
                }
            } catch (e: Throwable) {
                logger.warn("Failed to deserialize streamed ExtractorLink: ${e.message}")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_EMIT_SUBTITLE) {
            val reqId = cbReq.requestId ?: return
            val subJson = cbReq.subtitleJson ?: return
            val consumer = activeLinkConsumers[reqId] ?: return
            try {
                val sub = WorkerProtocolSerializer.mapper.readValue(subJson, com.lagradost.cloudstream3.SubtitleFile::class.java)
                if (sub != null) {
                    consumer.onSubtitle?.invoke(sub)
                }
            } catch (e: Throwable) {
                logger.warn("Failed to deserialize streamed SubtitleFile: ${e.message}")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_PROXY_FETCH) {
            val url = cbReq.url ?: return
            val headers = cbReq.headers ?: emptyMap()
            val method = cbReq.method ?: "GET"
            val body = cbReq.body

            logger.info("Handling browser proxy fetch callback from sandboxed worker for $url")
            val (statusCode, contentType, respBody, err) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    val reqBuilder = okhttp3.Request.Builder().url(url)
                    val reqBody = body?.toRequestBody(null)
                    if (method.equals("POST", ignoreCase = true) || method.equals("PUT", ignoreCase = true)) {
                        reqBuilder.method(method, reqBody ?: ByteArray(0).toRequestBody(null))
                    } else {
                        reqBuilder.method(method, null)
                    }
                    headers.forEach { (k, v) ->
                        if (!k.equals("content-length", ignoreCase = true)) {
                            reqBuilder.header(k, v)
                        }
                    }
                    val okReq = reqBuilder.build()
                    val proxyResp = com.lagradost.cloudstream3.network.CloudflareKiller.fetchViaBrowserProxy(okReq)
                    if (proxyResp != null) {
                        val cType = proxyResp.header("content-type") ?: "text/html"
                        val bStr = proxyResp.body.string()
                        listOf(proxyResp.code, cType, bStr, null)
                    } else {
                        listOf(502, "text/plain", "", "Proxy response was null")
                    }
                } catch (t: Throwable) {
                    listOf(500, "text/plain", "", t.message)
                }
            }

            val cbResp = WorkerCallbackResponse(
                callbackId = cbReq.callbackId,
                success = (statusCode as Int) in 200..299,
                statusCode = statusCode,
                contentType = contentType as String?,
                body = respBody as String?,
                error = err as String?
            )
            sendCallbackResponse(cbResp)
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_SOLVE_CLOUDFLARE) {
            val url = cbReq.url ?: return
            val host = cbReq.host ?: return
            val headers = cbReq.headers ?: emptyMap()

            if (com.lagradost.cloudstream3.desktop.init.PluginWarmupState.progress.value.isWarmingUp) {
                logger.warn("Suppressing interactive Cloudflare verification modal for $host during background warmup.")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "Suppressed during background warmup"
                    )
                )
                return
            }

            logger.info("Handling Cloudflare solve callback from sandboxed worker for host: $host")
            val solved = kotlinx.coroutines.withContext(Dispatchers.IO) {
                com.lagradost.cloudstream3.network.CloudflareKiller.solveWithEmbeddedWebView(url, host, headers)
            }

            val clearanceCookies = com.lagradost.cloudstream3.network.CloudflareKiller.getClearance(host)?.cookies.orEmpty()
            val vaultCookies = com.lagradost.cloudstream3.desktop.network.vault.WindowsCookieVault.getAllUnexpired().filter { cookie ->
                val cd = cookie.domain.trimStart('.').lowercase()
                cd == host || host.endsWith(".$cd") || cd.endsWith(".$host")
            }
            val cookies = (clearanceCookies + vaultCookies).distinctBy { "${it.name}|${it.domain}" }

            val serializable = cookies.map { c ->
                SerializableCookie(
                    name = c.name,
                    value = c.value,
                    domain = c.domain,
                    path = c.path,
                    expiresAt = c.expiresAt,
                    secure = c.secure,
                    httpOnly = c.httpOnly
                )
            }

            val userAgent = com.lagradost.cloudstream3.desktop.network.webview.WindowsWebView2Runtime.userAgent
            val cbResp = WorkerCallbackResponse(
                callbackId = cbReq.callbackId,
                success = solved,
                cookies = serializable,
                userAgent = userAgent
            )

            sendCallbackResponse(cbResp)
        }
    }

    private fun sendCallbackResponse(response: WorkerCallbackResponse) {
        val writer = processWriter ?: return
        val json = WorkerProtocolSerializer.serializeCallbackResponse(response)
        synchronized(writer) {
            writer.write(json)
            writer.newLine()
            writer.flush()
        }
    }

    // High-Level Suspending API

    suspend fun ping(timeoutMs: Long = 15_000L): Boolean {
        val req = WorkerRequest(id = nextRequestId.getAndIncrement(), action = WorkerRequest.ACTION_PING)
        val resp = sendRequest(req, timeoutMs = timeoutMs)
        return resp.success
    }

    suspend fun syncSettings(settings: Map<String, String>): Boolean {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_SYNC_SETTINGS,
            settingsMap = settings
        )
        val resp = sendRequest(req, timeoutMs = 10_000L)
        return resp.success
    }

    /**
     * Loads a plugin JAR in the sandboxed worker. On success, returns the full list of
     * provider metadata registered by all currently loaded plugins (no second round-trip needed).
     * Returns null if the load failed.
     */
    suspend fun loadPlugin(jarFile: File): List<SandboxedProviderMetadata>? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD_PLUGIN,
            pluginPath = jarFile.absolutePath
        )
        val resp = try {
            sendRequest(req, timeoutMs = 15_000L)
        } catch (t: Throwable) {
            logger.warn("Sandboxed worker failed during plugin load for ${jarFile.name}: ${t.message}")
            isHostAvailable = false
            return null
        }
        if (!resp.success) return null
        loadedPluginPaths.add(jarFile.absolutePath)
        val payload = resp.payloadJson ?: return emptyList()
        val loadResult = try {
            WorkerProtocolSerializer.mapper.readValue(payload, com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedPluginLoadResult::class.java)
        } catch (_: Throwable) {
            val list = WorkerProtocolSerializer.mapper.readValue<List<SandboxedProviderMetadata>>(
                payload,
                WorkerProtocolSerializer.mapper.typeFactory.constructCollectionType(
                    List::class.java, SandboxedProviderMetadata::class.java
                )
            )
            com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedPluginLoadResult(providers = list)
        }

        loadResult.schemas.forEach { s ->
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                pluginPrefName = s.pluginPrefName,
                key = s.key,
                type = s.type,
                defaultValue = s.defaultValue,
                isGlobal = s.isGlobal,
                options = s.options,
                storageKey = s.storageKey,
            )
        }

        return loadResult.providers
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

    suspend fun loadLinks(
        providerName: String,
        data: String,
        isCasting: Boolean = false,
        subtitleCallback: ((com.lagradost.cloudstream3.SubtitleFile) -> Unit)? = null,
        callback: ((com.lagradost.cloudstream3.utils.ExtractorLink) -> Unit)? = null
    ): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD_LINKS,
            providerName = providerName,
            data = data,
            isCasting = isCasting
        )
        if (callback != null || subtitleCallback != null) {
            activeLinkConsumers[req.id] = ActiveLinkConsumer(
                onLink = callback,
                onSubtitle = subtitleCallback
            )
        }
        return try {
            val resp = sendRequest(req, timeoutMs = 90_000L)
            if (!resp.success) throw IOException(resp.error ?: "LoadLinks failed")
            resp.payloadJson
        } finally {
            activeLinkConsumers.remove(req.id)
        }
    }

    suspend fun getMainPage(
        providerName: String,
        page: Int,
        requestName: String,
        requestData: String,
        horizontalImages: Boolean = false
    ): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_MAIN_PAGE,
            providerName = providerName,
            page = page,
            requestName = requestName,
            requestData = requestData,
            horizontalImages = horizontalImages
        )
        val resp = sendRequest(req, timeoutMs = 45_000L)
        if (!resp.success) throw IOException(resp.error ?: "MainPage failed")
        return resp.payloadJson
    }

    suspend fun getLoadedProviders(): List<SandboxedProviderMetadata> {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_GET_LOADED_PROVIDERS
        )
        val resp = sendRequest(req, timeoutMs = 10_000L)
        if (!resp.success || resp.payloadJson == null) return emptyList()
        return try {
            val loadResult = WorkerProtocolSerializer.mapper.readValue(
                resp.payloadJson,
                com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedPluginLoadResult::class.java
            )
            loadResult.providers
        } catch (_: Throwable) {
            WorkerProtocolSerializer.mapper.readValue(
                resp.payloadJson,
                WorkerProtocolSerializer.mapper.typeFactory.constructCollectionType(List::class.java, SandboxedProviderMetadata::class.java)
            )
        }
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
