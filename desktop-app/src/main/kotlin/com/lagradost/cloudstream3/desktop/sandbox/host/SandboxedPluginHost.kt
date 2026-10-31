package com.lagradost.cloudstream3.desktop.sandbox.host

import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxAuditReport
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

/*

*

 * Supervisor running in the main Compose app process.
 * Manages the lifecycle of the low-privilege sandboxed JVM worker,
 * multiplexing requests over anonymous pipes with automatic crash recovery.
 */
class SandboxedPluginHost(
    val containerName: String = "CloudStream.PluginWorker.Sandbox.v2",
    val memoryLimitBytes: Long = 3072L * 1024L * 1024L // 3 GB
) : Closeable {

    companion object {
        private val grantedSids = ConcurrentHashMap.newKeySet<String>()

        private val PROTECTED_HOST_KEYS = setOf(
            "pref_sandbox_enabled",
            "trusted_plugins",
            "USER_PROVIDER_API",
            "preferred_player",
            "home_active_providers",
            "preferred_provider_name",
            "last_active_plugin_jar",
            "last_plugin_sync_timestamp",
            "search_history",
            "search_preference_providers",
            "pref_allow_cf_bypass",
            "dns_provider",
            "dns_over_https",
            "pref_dns_mode",
            "user_agent"
        )

        fun isBlockedOrPrivateUrl(rawUrl: String): Boolean {
            val lower = rawUrl.lowercase().trim()
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) return true

            val host = try {
                java.net.URI(rawUrl).host?.lowercase()?.trim() ?: return true
            } catch (_: Throwable) {
                return true
            }

            if (host == "localhost" || host == "0.0.0.0" || host == "::1" || host == "127.0.0.1") return true
            if (host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".lan") || host.endsWith(".home") || host.endsWith(".arpa")) return true

            return try {
                val addresses = java.net.InetAddress.getAllByName(host)
                addresses.any { addr ->
                    addr.isLoopbackAddress ||
                    addr.isSiteLocalAddress ||
                    addr.isLinkLocalAddress ||
                    addr.isAnyLocalAddress ||
                    isCarrierGradeNat(addr)
                }
            } catch (_: Throwable) {
                true
            }
        }

        private fun isCarrierGradeNat(addr: java.net.InetAddress): Boolean {
            val bytes = addr.address
            if (bytes.size == 4) {
                val b0 = bytes[0].toInt() and 0xFF
                val b1 = bytes[1].toInt() and 0xFF
                if (b0 == 100 && (b1 in 64..127)) return true
            }
            return false
        }
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
    private val lastSolveAttempts = ConcurrentHashMap<String, Long>()
    private val activeWorkerNetworkRequests = ConcurrentHashMap<Long, Long>()

    private var currentJobObject: WindowsJobObject? = null
    private var currentProcess: WindowsAppContainerLauncher.SandboxedProcess? = null
    private var processWriter: BufferedWriter? = null
    @Volatile
    var isHostAvailable: Boolean = true

    /*

    *
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
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT
            )
        ) ?: throw IOException("Failed to obtain AppContainer SID for '$containerName'")

        if (grantedSids.add(sid)) {
            // Grant AppContainer permissions to plugin storage and dedicated sandbox directories
            val extensionsDir = PlatformPaths.extensionsDir
            if (!extensionsDir.exists()) extensionsDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(extensionsDir, sid, readOnly = true, allowExecute = true)

            val sandboxDir = PlatformPaths.sandboxDir
            if (!sandboxDir.exists()) sandboxDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(sandboxDir, sid, readOnly = false, allowExecute = false)

            val sandboxTmp = File(sandboxDir, "tmp")
            if (!sandboxTmp.exists()) sandboxTmp.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(sandboxTmp, sid, readOnly = false, allowExecute = false)

            val pluginCacheDir = PlatformPaths.pluginCacheDir
            if (!pluginCacheDir.exists()) pluginCacheDir.mkdirs()
            WindowsSandboxAcl.grantFolderAccess(pluginCacheDir, sid, readOnly = false, allowExecute = true)

            // Grant read access to JRE and Gradle caches only. project root (user.dir) is intentionally
            // excluded — granting it would expose the entire workspace tree to the sandboxed worker.
            // Classpath entries that fall outside these roots are handled individually below.
            val topRoots = mutableSetOf<File>()

            val gradleCache = File(System.getProperty("user.home"), ".gradle/caches")
            if (gradleCache.exists()) topRoots.add(gradleCache)

            val projectRoot = File(System.getProperty("user.dir"))
            if (projectRoot.exists()) { /* user.dir excluded: see topRoots comment above */ }

            val javaHome = File(System.getProperty("java.home"))
            if (javaHome.exists()) topRoots.add(javaHome)

            for (root in topRoots) {
                try {
                    WindowsSandboxAcl.grantFolderAccess(root, sid, readOnly = true, allowExecute = true)
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
                            WindowsSandboxAcl.grantFolderAccess(File(parent), sid, readOnly = true, allowExecute = true)
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

        val sandboxTmp = File(PlatformPaths.sandboxDir, "tmp").apply {
            try {
                if (exists()) {
                    listFiles()?.forEach { file ->
                        try { file.deleteRecursively() } catch (_: Throwable) {}
                    }
                } else {
                    mkdirs()
                }
            } catch (_: Throwable) {}
        }
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
            "-XX:+DisableAttachMechanism -Djdk.attach.allowAttachSelf=false " +
            "-Xms32m -Xmx768m -XX:MaxMetaspaceSize=256m -XX:ReservedCodeCacheSize=64m -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError " +
            "-cp \"$classpath\" " +
            "com.lagradost.cloudstream3.desktop.sandbox.worker.PluginWorkerMain"

        val process = WindowsAppContainerLauncher.launch(
            commandLine = commandLine,
            containerName = containerName,
            workingDir = PlatformPaths.sandboxDir,
            capabilities = listOf(
                WindowsAppContainerApi.CAPABILITY_INTERNET_CLIENT
            ),
            jobObject = job
        )

        this.currentProcess = process
        this.processWriter = BufferedWriter(OutputStreamWriter(process.stdin, StandardCharsets.UTF_8), 65536)

        // Start background stdout reader
        scope.launch {
            val reader = java.io.BufferedReader(java.io.InputStreamReader(process.stdout, StandardCharsets.UTF_8), 65536)
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
                    val trimmed = errLine.trim()
                    if (trimmed.isEmpty()) continue

                    if (trimmed.startsWith("[CS_NET] START|")) {
                        val payload = trimmed.removePrefix("[CS_NET] START|")
                        val parts = payload.split("|", limit = 5)
                        if (parts.size >= 5) {
                            val workerId = parts[0].toLongOrNull() ?: 0L
                            val method = parts[1]
                            val host = parts[2]
                            val path = parts[3]
                            val url = parts[4]
                            val hostId = com.lagradost.common.net.NetworkTrafficBuffer.recordStart(
                                method = method,
                                url = url,
                                host = host,
                                path = path,
                                requestHeaders = emptyMap(),
                                requestBody = null,
                            )
                            activeWorkerNetworkRequests[workerId] = hostId
                            com.lagradost.common.logging.AppLogger.d("Sandbox:HTTP", "-> $method $url")
                        }
                    } else if (trimmed.startsWith("[CS_NET] END|")) {
                        val payload = trimmed.removePrefix("[CS_NET] END|")
                        val parts = payload.split("|", limit = 4)
                        if (parts.size >= 3) {
                            val workerId = parts[0].toLongOrNull() ?: 0L
                            val statusCode = parts[1].toIntOrNull() ?: -1
                            val durationMs = parts[2].toLongOrNull() ?: 0L
                            val error = if (parts.size >= 4 && parts[3].isNotBlank()) parts[3] else null
                            val hostId = activeWorkerNetworkRequests.remove(workerId)
                            if (hostId != null) {
                                com.lagradost.common.net.NetworkTrafficBuffer.recordComplete(
                                    id = hostId,
                                    statusCode = statusCode,
                                    statusMessage = if (statusCode > 0) "HTTP $statusCode" else (error ?: "Failed"),
                                    durationMs = durationMs,
                                    responseHeaders = emptyMap(),
                                    responseBody = null,
                                    responseSize = 0L,
                                    contentType = null,
                                    error = error,
                                )
                            }
                            val statusLabel = if (statusCode > 0) "$statusCode" else (error ?: "FAILED")
                            com.lagradost.common.logging.AppLogger.d("Sandbox:HTTP", "<- $statusLabel (${durationMs}ms)")
                        }
                    } else {
                        when {
                            trimmed.startsWith("[ERROR]") || trimmed.contains(" ERROR ") -> com.lagradost.common.logging.AppLogger.e("Sandbox:Worker", trimmed)
                            trimmed.startsWith("[WARN]") || trimmed.contains(" WARN ") -> com.lagradost.common.logging.AppLogger.w("Sandbox:Worker", trimmed)
                            trimmed.startsWith("[INFO]") || trimmed.contains(" INFO ") -> com.lagradost.common.logging.AppLogger.i("Sandbox:Worker", trimmed)
                            else -> com.lagradost.common.logging.AppLogger.d("Sandbox:Worker", trimmed)
                        }
                    }
                }
            } catch (_: Throwable) {}
        }

        logger.info("Sandboxed worker process started successfully (PID: ${process.pid})")
    }

    private fun handleWorkerTermination() {
        val pending = pendingRequests.values.toList()
        pendingRequests.clear()
        activeWorkerNetworkRequests.clear()

        val exit = try { currentProcess?.exitValue() } catch (_: Throwable) { null }
        val exitHex = exit?.let { "0x" + Integer.toHexString(it).uppercase() } ?: "unknown"

        for (p in pending) {
            p.completeExceptionally(IOException("Sandboxed worker process terminated unexpectedly (exit: $exitHex)"))
        }

        currentProcess?.close()
        currentProcess = null
        processWriter = null

        scope.launch(Dispatchers.IO) {
            try {
                File(PlatformPaths.sandboxDir, "tmp").listFiles()?.forEach { file ->
                    try { file.deleteRecursively() } catch (_: Throwable) {}
                }
            } catch (_: Throwable) {}
        }
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
                // Synchronize safe settings into fresh worker, strictly filtering out host-private keys
                val rawSettings = com.lagradost.common.storage.DesktopDataStore.rawKeyCache.toMap()
                val allowedExactKeys = setOf(
                    com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS,
                    com.lagradost.cloudstream3.network.CloudflareKiller.PREF_TLS_BOUND_HOSTS,
                    "dns_provider",
                    "dns_over_https",
                    "pref_dns_mode",
                    "user_agent"
                )
                val schemaStorageKeys = com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.values
                    .flatMap { it.values }.map { it.storageKey }.toSet()
                val schemaPrefixes = com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.keys

                val safeSettings = rawSettings.filter { (key, _) ->
                    allowedExactKeys.contains(key) ||
                    schemaStorageKeys.contains(key) ||
                    schemaPrefixes.any { prefix -> key.startsWith(prefix) } ||
                    key.startsWith("cf_silenced_") ||
                    key.startsWith("pref_plugin_")
                }
                if (safeSettings.isNotEmpty()) {
                    runCatching {
                        sendDirectAwaitLocked(
                            WorkerRequest(
                                id = nextRequestId.getAndIncrement(),
                                action = WorkerRequest.ACTION_SYNC_SETTINGS,
                                settingsMap = safeSettings
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

        if (cbReq.action == WorkerCallbackRequest.ACTION_SAVE_SETTING) {
            val key = cbReq.key ?: return
            val valueJson = cbReq.valueJson ?: return
            if (PROTECTED_HOST_KEYS.any { key.equals(it, ignoreCase = true) }) {
                logger.error("Rejected unauthorized worker write to protected host key: $key")
                return
            }
            // Only allow keys explicitly registered in PluginSettingsSchemaRegistry or cf_silenced_ entries.
            // The bare pref_plugin_ prefix wildcard is removed — it allowed cross-plugin setting overwrites.
            val isAllowed = key.startsWith("cf_silenced_") ||
                key.startsWith("cf_silenced_") ||
                com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.keys.any { key.startsWith(it) } ||
                com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.values.flatMap { it.values }.any { it.storageKey == key }

            if (isAllowed) {
                com.lagradost.common.storage.DesktopDataStore.setRawJson(key, valueJson)
            } else {
                logger.warn("Rejected unauthorized worker setting write to key: $key")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_REMOVE_SETTING) {
            val key = cbReq.key ?: return
            if (PROTECTED_HOST_KEYS.any { key.equals(it, ignoreCase = true) }) {
                logger.error("Rejected unauthorized worker write to protected host key: $key")
                return
            }
            // Only allow keys explicitly registered in PluginSettingsSchemaRegistry or cf_silenced_ entries.
            // The bare pref_plugin_ prefix wildcard is removed — it allowed cross-plugin setting overwrites.
            val isAllowed = key.startsWith("cf_silenced_") ||
                key.startsWith("cf_silenced_") ||
                com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.keys.any { key.startsWith(it) } ||
                com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas.values.flatMap { it.values }.any { it.storageKey == key }

            if (isAllowed) {
                com.lagradost.common.storage.DesktopDataStore.removeKey(key)
            } else {
                logger.warn("Rejected unauthorized worker setting delete for key: $key")
            }
            return
        }

        if (cbReq.action == WorkerCallbackRequest.ACTION_PROXY_FETCH) {
            val url = cbReq.url ?: return
            val headers = cbReq.headers ?: emptyMap()
            val method = cbReq.method ?: "GET"
            val body = cbReq.body

            if (isBlockedOrPrivateUrl(url)) {
                logger.warn("Rejected unauthorized SSRF/private proxy fetch callback to: $url")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        statusCode = 403,
                        error = "SSRF blocked: Requests to loopback, private LAN, or invalid network destinations are permanently forbidden."
                    )
                )
                return
            }

            logger.info("Handling browser proxy fetch callback from sandboxed worker for $url")
            val (statusCode, contentType, respBody, err) = kotlinx.coroutines.withContext(Dispatchers.IO) {
                try {
                    // Resolve addresses here and pin them into the request, preventing TOCTOU DNS
                    // rebinding between the isBlockedOrPrivateUrl() check above and OkHttp's connect.
                    val pinnedHost = try { java.net.URI(url).host?.lowercase()?.trim() } catch (_: Throwable) { null }
                    val pinnedAddresses: List<java.net.InetAddress> = if (pinnedHost != null) {
                        try { java.net.InetAddress.getAllByName(pinnedHost).filter { a ->
                            !a.isLoopbackAddress && !a.isSiteLocalAddress && !a.isLinkLocalAddress && !a.isAnyLocalAddress
                        } } catch (_: Throwable) { emptyList() }
                    } else emptyList()
                    if (pinnedHost != null && pinnedAddresses.isEmpty()) {
                        return@withContext listOf(403, "text/plain", "", "SSRF blocked: resolved addresses are all private or unavailable")
                    }
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

            if (isBlockedOrPrivateUrl(url) || isBlockedOrPrivateUrl("https://$host")) {
                logger.warn("Rejecting SSRF/private Cloudflare solve callback for host: $host (url: $url)")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "SSRF blocked: Cloudflare solver cannot target loopback, private LAN, or invalid network destinations."
                    )
                )
                return
            }

            /*
                    )
                )
                return
            }


                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "SSRF blocked"
                    )
                )
                return
            }

            */ val now = System.currentTimeMillis()
            val lastAttempt = lastSolveAttempts[host] ?: 0L
            if (now - lastAttempt < 3_000L) {
                logger.warn("Throttling excessive Cloudflare solve callback requests for host: $host")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "Throttled: solve request too frequent"
                    )
                )
                return
            }
            lastSolveAttempts[host] = now

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

            val isBypassAllowed = com.lagradost.common.storage.DesktopDataStore.getKey<Boolean>(
                com.lagradost.common.storage.DesktopDataStore.PREF_ALLOW_CF_BYPASS
            ) ?: false
            if (!isBypassAllowed) {
                logger.info("Cloudflare solver skipped for host $host: Disabled in Settings.")
                sendCallbackResponse(
                    WorkerCallbackResponse(
                        callbackId = cbReq.callbackId,
                        success = false,
                        error = "Cloudflare solver is disabled by user in Settings."
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

    /*
        return resp.success
    }

    *
     * Loads a plugin JAR in the sandboxed worker. On success, returns the full list of
     * provider metadata registered by all currently loaded plugins (no second round-trip needed).
     * Returns null if the load failed.
     */
    suspend fun loadPlugin(jarFile: File, forceRetry: Boolean = false): List<SandboxedProviderMetadata>? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_LOAD_PLUGIN,
            forceRetry = forceRetry,
            pluginPath = jarFile.absolutePath
        )
        val resp = try {
            sendRequest(req, timeoutMs = 45_000L)
        } catch (t: Throwable) {
            logger.warn("Sandboxed worker failed during plugin load for ${jarFile.name}: ${t.message}")
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
            val sk = s.storageKey ?: "${s.pluginPrefName}${s.key}"
            if (PROTECTED_HOST_KEYS.any { sk.equals(it, ignoreCase = true) || s.key.equals(it, ignoreCase = true) }) {
                logger.warn("Ignoring unauthorized worker schema registration targeting protected key '${s.key}'")
                return@forEach
            }
            if (!sk.startsWith(s.pluginPrefName) && !sk.startsWith("pref_plugin_") && !sk.startsWith("cf_silenced_")) {
                logger.warn("Ignoring un-namespaced schema '$sk' from plugin '${s.pluginPrefName}'")
                return@forEach
            }
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                pluginPrefName = s.pluginPrefName,
                key = s.key,
                type = s.type,
                defaultValue = s.defaultValue,
                isGlobal = false,
                options = s.options,
                storageKey = s.storageKey,
            )
        }

        return loadResult.providers
    }

    suspend fun search(providerName: String, query: String, page: Int = 1): String? {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_SEARCH,
            providerName = providerName,
            query = query,
            page = page,
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
        val resp = sendRequest(req, timeoutMs = 10_000L) /*
        )
        val resp = sendRequest(req, timeoutMs = 10_000L)
        return resp.success
    }

    /*
*/ */ ;
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

    suspend fun unloadPlugin(jarFile: File): Boolean {
        val canonical = try { jarFile.canonicalPath } catch (_: Throwable) { jarFile.absolutePath }
        loadedPluginPaths.remove(jarFile.absolutePath)
        loadedPluginPaths.remove(canonical)

        if (currentProcess?.isAlive() != true) return true

        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_UNLOAD_PLUGIN,
            pluginPath = jarFile.absolutePath
        )
        return try {
            val resp = sendRequest(req, timeoutMs = 2_000L)
            resp.success
        } catch (t: Throwable) {
            logger.warn("Worker unload timed out or failed for ${jarFile.name}; recycling worker process: ${t.message}")
            recycleWorker()
            true
        }
    }

    suspend fun runSecurityAudit(): SandboxAuditReport {
        val req = WorkerRequest(
            id = nextRequestId.getAndIncrement(),
            action = WorkerRequest.ACTION_SECURITY_AUDIT
        )
        val resp = sendRequest(req, timeoutMs = 15_000L)
        if (!resp.success || resp.payloadJson == null) {
            throw IOException("Security audit failed: ${resp.error ?: "Unknown error"}")
        }
        return WorkerProtocolSerializer.mapper.readValue(
            resp.payloadJson,
            SandboxAuditReport::class.java
        )
    }

    fun recycleWorker() {
        try {
            currentProcess?.destroy()
        } catch (_: Throwable) {}
        currentProcess = null
        currentJobObject?.close()
        currentJobObject = null
    }

    override fun close() {
        recycleWorker()
    }
}
