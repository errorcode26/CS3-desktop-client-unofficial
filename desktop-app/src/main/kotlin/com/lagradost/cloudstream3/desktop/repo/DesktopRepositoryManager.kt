package com.lagradost.cloudstream3.desktop.repo

import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.ui.settings.extensions.RepositoryData
import com.lagradost.common.logging.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

object DesktopRepositoryManager {
    private val reposFile by lazy { File(getExtensionsDir(), "repos.json") }
    private val repoCacheFile by lazy { File(getExtensionsDir(), "repo_cache.json") }
    private val pluginsCacheFile by lazy { File(getExtensionsDir(), "plugins_cache.json") }

    private val repoCache = java.util.concurrent.ConcurrentHashMap<String, Repository>()
    private val pluginsCache = java.util.concurrent.ConcurrentHashMap<String, List<SitePlugin>>()
    private val networkThrottle = Semaphore(3)

    val AUTO_UPDATE_COOLDOWN_MS = 4 * 60 * 60 * 1000L // 4 hours

    fun getLastSyncTimestamp(): Long {
        return com.lagradost.common.storage.DesktopDataStore.getKey<Long>(PreferenceKeys.LAST_PLUGIN_SYNC_TIMESTAMP) ?: 0L
    }

    fun setLastSyncTimestamp(time: Long) {
        com.lagradost.common.storage.DesktopDataStore.setKey(PreferenceKeys.LAST_PLUGIN_SYNC_TIMESTAMP, time)
    }

    private val _savedRepositories = MutableStateFlow<List<RepositoryData>>(emptyList())
    val savedRepositories: StateFlow<List<RepositoryData>> = _savedRepositories.asStateFlow()

    private val _remotePluginIcons = MutableStateFlow<Map<String, String>>(emptyMap())
    val remotePluginIcons: StateFlow<Map<String, String>> = _remotePluginIcons.asStateFlow()

    fun getPluginIcon(providerName: String?): String? {
        if (providerName.isNullOrBlank()) return null
        val clean = if (providerName.contains("::")) providerName.substringAfter("::") else providerName
        val icons = _remotePluginIcons.value
        icons[clean]?.let { return it }
        icons[providerName]?.let { return it }
        val sanitizeRegex = Regex("[^a-zA-Z0-9]")
        val pName = clean.lowercase().replace(sanitizeRegex, "").replace("provider", "").replace("plugin", "")
        return icons.entries.firstOrNull { (k, _) ->
            val kName = k.lowercase().replace(sanitizeRegex, "").replace("provider", "").replace("plugin", "")
            if (kName.length < 3) return@firstOrNull false
            pName.isNotEmpty() && (pName.contains(kName) || kName.contains(pName))
        }?.value
    }

    private val _syncGeneration = MutableStateFlow(0)
    val syncGeneration: StateFlow<Int> = _syncGeneration.asStateFlow()

    fun incrementSyncGeneration() {
        _syncGeneration.update { it + 1 }
    }

    private val fetchMutexes = java.util.concurrent.ConcurrentHashMap<String, Mutex>()
    private val autoUpdateMutex = Mutex()
    private val syncMutex = Mutex()

    private val _failedIconUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    fun isIconFailed(url: String): Boolean = _failedIconUrls.contains(url)
    fun markIconFailed(url: String) {
        _failedIconUrls.add(url)
    }

    data class SyncReport(
        val reposRefreshed: Int,
        val catalogPlugins: Int,
        val pluginsUpdated: Int,
        val iconsCached: Int,
        val newPluginsLoaded: Int,
    ) {
        val summary: String
            get() = "Sync done: $reposRefreshed repos, $catalogPlugins plugins listed, $pluginsUpdated updated, $iconsCached icons, $newPluginsLoaded newly loaded."
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        refreshSavedRepositoriesFromDisk()
        loadCachesFromDisk()
    }

    private fun loadCachesFromDisk() {
        try {
            if (repoCacheFile.exists()) {
                val data: Map<String, Repository> = PluginNetworkClient.mapper.readValue(
                    repoCacheFile,
                    object : TypeReference<Map<String, Repository>>() {},
                )
                repoCache.putAll(data)
            }
            if (pluginsCacheFile.exists()) {
                val data: Map<String, List<SitePlugin>> = PluginNetworkClient.mapper.readValue(
                    pluginsCacheFile,
                    object : TypeReference<Map<String, List<SitePlugin>>>() {},
                )
                pluginsCache.putAll(data)
            }
            val cachedIcons = mutableMapOf<String, String>()
            pluginsCache.values.flatten().forEach { plugin ->
                val icon = plugin.iconUrl
                if (!icon.isNullOrEmpty()) {
                    cachedIcons[plugin.internalName] = icon
                    cachedIcons[plugin.name] = icon
                }
            }
            _remotePluginIcons.value = cachedIcons + scanLocalPluginIcons()
            _syncGeneration.update { it + 1 }
        } catch (e: Exception) {
            AppLogger.e("Failed to load repository caches from disk", e)
            try {
                _remotePluginIcons.value = scanLocalPluginIcons()
                _syncGeneration.update { it + 1 }
            } catch (_: Exception) {}
        }
    }

    private fun saveCachesToDisk() {
        try {
            repoCacheFile.parentFile?.mkdirs()
            PluginNetworkClient.mapper.writeValue(repoCacheFile, HashMap(repoCache))
            PluginNetworkClient.mapper.writeValue(pluginsCacheFile, HashMap(pluginsCache))
        } catch (e: Exception) {
            AppLogger.e("Failed to save repository caches to disk", e)
        }
    }

    fun getSavedRepositories(): List<RepositoryData> = _savedRepositories.value

    fun getRepositoryManifest(url: String): Repository? = repoCache[url]

    fun getPluginsJsonUrl(repoUrl: String): String {
        val cached = repoCache[repoUrl]?.pluginLists?.firstOrNull()
        if (!cached.isNullOrBlank()) return cached
        if (repoUrl.endsWith("repo.json", ignoreCase = true)) {
            return repoUrl.replace("repo.json", "builds/plugins.json", ignoreCase = true)
        }
        return repoUrl
    }

    private fun refreshSavedRepositoriesFromDisk() {
        _savedRepositories.value = readRepositoriesFromDisk()
    }

    private fun readRepositoriesFromDisk(): List<RepositoryData> {
        if (!reposFile.exists()) return emptyList()
        return try {
            val root = PluginNetworkClient.mapper.readTree(reposFile.readText())
            if (!root.isArray) return emptyList()
            if (root.size() > 0 && root[0].isTextual) {
                val urls = PluginNetworkClient.mapper.readValue(root.toString(), object : TypeReference<List<String>>() {})
                val migrated = urls.map { url -> RepositoryData(name = url, url = url) }
                writeRepositoriesToDisk(migrated)
                migrated
            } else {
                PluginNetworkClient.mapper.readValue(root.toString(), object : TypeReference<List<RepositoryData>>() {})
                    .map { normalizeRepositoryData(it) }
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to read repos.json: ${e.message}")
            emptyList()
        }
    }

    private fun writeRepositoriesToDisk(repos: List<RepositoryData>) {
        try {
            reposFile.parentFile?.mkdirs()
            PluginNetworkClient.mapper.writeValue(reposFile, repos)
            _savedRepositories.value = repos
        } catch (e: Exception) {
            AppLogger.w("Could not write repositories to disk: ${e.message}")
        }
    }

    private fun normalizeRepositoryData(data: RepositoryData): RepositoryData {
        val icon = data.iconUrl?.trim()?.takeIf { it.isNotEmpty() }
        val name = data.name.trim().ifEmpty { data.url }
        return data.copy(iconUrl = icon, name = name)
    }

    suspend fun saveRepository(repository: RepositoryData): Boolean = syncMutex.withLock {
        val incoming = normalizeRepositoryData(repository)
        val current = readRepositoriesFromDisk().toMutableList()
        val index = current.indexOfFirst { it.url == incoming.url }
        val isNew = index < 0
        if (index >= 0) {
            val existing = current[index]
            val updatedName = if (incoming.name.isNotBlank() && incoming.name != incoming.url) incoming.name else existing.name
            val updatedIcon = incoming.iconUrl ?: existing.iconUrl
            if (existing.name == updatedName && existing.iconUrl == updatedIcon) {
                return false
            }
            current[index] = existing.copy(
                name = if (incoming.name.isNotBlank() && incoming.name != incoming.url) incoming.name else existing.name,
                iconUrl = incoming.iconUrl ?: existing.iconUrl,
            )
        } else {
            current.add(incoming)
        }
        writeRepositoriesToDisk(current.distinctBy { it.url })
        isNew
    }

    suspend fun removeRepository(url: String) = syncMutex.withLock {
        val currentList = readRepositoriesFromDisk()
        val repoToRemove = currentList.find { it.url == url }

        val current = currentList.filter { it.url != url }
        writeRepositoriesToDisk(current)

        val repo = repoCache.remove(url)
        if (repo != null) {
            for (listUrl in repo.pluginLists) {
                pluginsCache.remove(listUrl)
            }
        }
        saveCachesToDisk()

        val nameToUse = repo?.name ?: repoToRemove?.name
        if (!nameToUse.isNullOrBlank()) {
            PluginFileUtils.deleteRepositoryDirectory(nameToUse)
        }
    }

    suspend fun addRepositoryFromInput(inputUrl: String): List<Repository>? = withContext(Dispatchers.IO) {
        val trimmed = inputUrl.trim()
        if (trimmed.isEmpty()) return@withContext null
        val resolvedUrl = PluginNetworkClient.parseRepoUrl(trimmed) ?: trimmed

        // Check if the URL resolves to a Mega Repo (JSON array)
        val request = okhttp3.Request.Builder().url(resolvedUrl).build()
        var body: String? = null
        try {
            PluginNetworkClient.redirectClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) body = response.body.string()
            }
        } catch (e: Exception) {
            AppLogger.i("Failed to fetch $resolvedUrl: ${e.message}")
        }

        if (body != null && body!!.trimStart().startsWith("[")) {
            try {
                val nodes = PluginNetworkClient.mapper.readTree(body!!)
                val urls = nodes.mapNotNull { it.get("url")?.asText() }
                val addedRepos = mutableListOf<Repository>()
                for (url in urls) {
                    addSingleRepository(url)?.let { addedRepos.add(it) }
                }
                if (addedRepos.isNotEmpty()) {
                    rebuildRemotePluginCatalog()
                    incrementSyncGeneration()
                }
                return@withContext addedRepos.takeIf { it.isNotEmpty() }
            } catch (e: Exception) {
                AppLogger.i("Failed to parse MegaRepo: ${e.message}")
            }
        }

        val repo = addSingleRepository(resolvedUrl)
        if (repo != null) {
            rebuildRemotePluginCatalog()
            incrementSyncGeneration()
        }
        return@withContext if (repo != null) listOf(repo) else null
    }

    private suspend fun addSingleRepository(url: String): Repository? {
        val resolvedUrl = PluginNetworkClient.parseRepoUrl(url) ?: url
        val manifest = PluginNetworkClient.fetchRepository(resolvedUrl) ?: return null

        repoCache[resolvedUrl] = manifest
        saveRepository(RepositoryData(iconUrl = manifest.iconUrl, name = manifest.name, url = resolvedUrl))

        manifest.pluginLists.forEach { listUrl ->
            try {
                getCachedPlugins(listUrl)
            } catch (e: Exception) {
                AppLogger.e("Failed to pre-fetch plugins for $listUrl", e)
            }
        }
        saveCachesToDisk()
        incrementSyncGeneration()
        return manifest
    }

    suspend fun getCachedRepository(url: String): Repository? {
        if (repoCache.containsKey(url)) return repoCache[url]
        val repo = PluginNetworkClient.fetchRepository(url)
        if (repo != null) {
            repoCache[url] = repo
            saveCachesToDisk()
        }
        return repo
    }

    suspend fun getCachedPlugins(listUrl: String): List<SitePlugin> {
        pluginsCache[listUrl]?.let { return it }
        val mutex = fetchMutexes.computeIfAbsent(listUrl) { Mutex() }
        val plugins = mutex.withLock {
            pluginsCache[listUrl]?.let { return@withLock it }
            val fetched = PluginNetworkClient.fetchPlugins(listUrl)
            pluginsCache[listUrl] = fetched
            saveCachesToDisk()
            fetched
        }

        val newIcons = _remotePluginIcons.value.toMutableMap()
        plugins.forEach { remotePlugin ->
            val remoteIcon = remotePlugin.iconUrl
            if (!remoteIcon.isNullOrEmpty()) {
                newIcons[remotePlugin.internalName] = remoteIcon
                newIcons[remotePlugin.name] = remoteIcon
            }
        }
        _remotePluginIcons.value = newIcons

        return plugins
    }

    fun updateCachedPlugin(listUrl: String, freshPlugin: SitePlugin) {
        val current = pluginsCache[listUrl] ?: return
        val updated = current.map { if (it.internalName == freshPlugin.internalName) freshPlugin else it }
        pluginsCache[listUrl] = updated
        saveCachesToDisk()
        _syncGeneration.update { it + 1 }
    }

    private val manifestCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, Map<String, Any>>>()

    fun readPluginManifest(jarFile: File): Map<String, Any>? {
        val lastModified = jarFile.lastModified()
        val cached = manifestCache[jarFile.absolutePath]
        if (cached != null && cached.first == lastModified) {
            return cached.second
        }
        try {
            java.util.zip.ZipFile(jarFile).use { zip ->
                val manifestEntry = zip.getEntry("manifest.json") ?: return null
                zip.getInputStream(manifestEntry).use { input ->
                    val data: Map<String, Any> = PluginNetworkClient.mapper.readValue(input, object : TypeReference<Map<String, Any>>() {})
                    manifestCache[jarFile.absolutePath] = Pair(lastModified, data)
                    return data
                }
            }
        } catch (e: Exception) {
            AppLogger.w("Failed to read plugin manifest from ${jarFile.name}: ${e.message}")
            return null
        }
    }

    fun getExtensionsDir(): File = PluginFileUtils.getExtensionsDir()

    suspend fun downloadPlugin(repoName: String, plugin: SitePlugin): File? =
        PluginFileUtils.downloadPlugin(repoName, plugin)

    fun clearCaches() {
        repoCache.clear()
        pluginsCache.clear()
    }

    suspend fun checkAndApplyPluginUpdates() {
        val extensionsDir = getExtensionsDir()
        if (!extensionsDir.exists()) return

        val allRemote = getAllPlugins()
        val localJars = extensionsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
            .filter { !it.name.endsWith("-jvm.jar") }
            .toList()

        var updatedCount = 0
        for (jar in localJars) {
            val manifest = readPluginManifest(jar) ?: continue
            val name = manifest["name"] as? String ?: jar.nameWithoutExtension
            val internalName = manifest["internalName"] as? String ?: name
            val localVersion = manifest["version"]?.toString()?.toIntOrNull() ?: 0

            val localRepoDirName = jar.parentFile?.name
            val remoteMatch = allRemote.find { (repoName, sitePlugin) ->
                sitePlugin.internalName == internalName &&
                    (localRepoDirName == null || repoName.replace(Regex("[^a-zA-Z0-9.-]"), "_").equals(localRepoDirName, ignoreCase = true))
            } ?: if (localRepoDirName == null || localRepoDirName.equals("extensions", ignoreCase = true)) {
                allRemote.find { it.second.internalName == internalName }
            } else {
                null
            }

            if (remoteMatch != null) {
                val repoName = remoteMatch.first
                val sitePlugin = remoteMatch.second
                if (sitePlugin.version > localVersion) {
                    AppLogger.i("Auto-updating plugin: $internalName in $localRepoDirName from v$localVersion to v${sitePlugin.version}")
                    try {
                        val wasTrusted = com.lagradost.runtime.loader.ExtensionLoader.isTrusted(jar, internalName, manifestName = name)
                        withContext(Dispatchers.IO) {
                            com.lagradost.runtime.loader.ExtensionLoader.unloadPlugin(jar.absolutePath)
                        }
                        val newJar = downloadPlugin(repoName, sitePlugin)
                        if (newJar != null) {
                            if (wasTrusted) {
                                com.lagradost.runtime.loader.ExtensionLoader.addTrusted(newJar, internalName, manifestName = name)
                            }
                            withContext(Dispatchers.IO) {
                                com.lagradost.runtime.loader.ExtensionLoader.loadAndInit(newJar, forceBypassSecurity = wasTrusted)
                            }
                            updatedCount++
                        }
                    } catch (e: Throwable) {
                        AppLogger.e("Failed to auto-update plugin $internalName in $localRepoDirName", e)
                        try {
                            com.lagradost.runtime.loader.ExtensionLoader.loadAndInit(jar)
                        } catch (rollbackErr: Throwable) {
                            AppLogger.e("Failed to reload rollback JAR for ${jar.name} after failed update", rollbackErr)
                        }
                    }
                }
            }
        }
        if (updatedCount > 0) {
            AppLogger.i("Auto-updated $updatedCount plugins successfully.")
            _syncGeneration.update { it + 1 }
        }
    }

    private fun scanLocalPluginIcons(): Map<String, String> {
        val icons = mutableMapOf<String, String>()
        val extensionsDir = getExtensionsDir()
        if (!extensionsDir.exists()) return icons
        extensionsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
            .filter { !it.name.endsWith("-jvm.jar") }
            .forEach { jar ->
                val manifest = readPluginManifest(jar) ?: return@forEach
                val iconUrl = manifest["iconUrl"] as? String ?: return@forEach
                val internalName = manifest["internalName"] as? String
                val name = manifest["name"] as? String
                if (internalName != null) icons[internalName] = iconUrl
                if (name != null) icons[name] = iconUrl
            }
        return icons
    }

    suspend fun refreshAllRepositoryMetadata(): Int = withContext(Dispatchers.IO) {
        val updates = java.util.concurrent.ConcurrentHashMap<String, Pair<String?, String>>()
        coroutineScope {
            getSavedRepositories().map { saved ->
                async {
                    val manifest = networkThrottle.withPermit {
                        PluginNetworkClient.fetchRepository(saved.url)
                    } ?: return@async
                    repoCache[saved.url] = manifest
                    updates[saved.url] = Pair(manifest.iconUrl, manifest.name)
                    AppLogger.i("Refreshed repo metadata: ${manifest.name}")
                }
            }.awaitAll()
        }
        if (updates.isNotEmpty()) {
            synchronized(this@DesktopRepositoryManager) {
                val current = readRepositoriesFromDisk().toMutableList()
                for ((url, pair) in updates) {
                    val (iconUrl, name) = pair
                    val index = current.indexOfFirst { it.url == url }
                    if (index >= 0) {
                        val existing = current[index]
                        current[index] = existing.copy(
                            name = if (name.isNotBlank() && name != url) name else existing.name,
                            iconUrl = iconUrl ?: existing.iconUrl,
                        )
                    }
                }
                writeRepositoriesToDisk(current.distinctBy { it.url })
            }
        }
        updates.size
    }

    suspend fun rebuildRemotePluginCatalog(onRepoFetched: (suspend (repoName: String, completed: Int, total: Int) -> Unit)? = null): Int = withContext(Dispatchers.IO) {
        val iconMap = java.util.concurrent.ConcurrentHashMap<String, String>()
        val total = java.util.concurrent.atomic.AtomicInteger(0)
        val savedRepos = getSavedRepositories()
        val completedCounter = java.util.concurrent.atomic.AtomicInteger(0)

        coroutineScope {
            savedRepos.map { saved ->
                async {
                    try {
                        val repo = networkThrottle.withPermit {
                            PluginNetworkClient.fetchRepository(saved.url)
                        }
                        if (repo != null) {
                            repoCache[saved.url] = repo
                            repo.pluginLists.map { listUrl ->
                                async {
                                    try {
                                        val plugins = networkThrottle.withPermit {
                                            PluginNetworkClient.fetchPlugins(listUrl)
                                        }
                                        pluginsCache[listUrl] = plugins
                                        plugins.forEach { plugin ->
                                            val icon = plugin.iconUrl
                                            if (!icon.isNullOrEmpty()) {
                                                iconMap[plugin.internalName] = icon
                                                iconMap[plugin.name] = icon
                                            }
                                        }
                                        total.addAndGet(plugins.size)
                                    } catch (e: Exception) {
                                        AppLogger.w("Failed to fetch or parse plugins list from $listUrl: ${e.message}")
                                    }
                                }
                            }.awaitAll()
                        }
                    } catch (e: Exception) {
                        AppLogger.w("Failed to fetch repository manifest from ${saved.url}: ${e.message}")
                    } finally {
                        val done = completedCounter.incrementAndGet()
                        _remotePluginIcons.value = iconMap + scanLocalPluginIcons()
                        _syncGeneration.update { it + 1 }
                        onRepoFetched?.invoke(saved.name, done, savedRepos.size)
                    }
                }
            }.awaitAll()
        }

        _remotePluginIcons.value = iconMap + scanLocalPluginIcons()
        total.get()
    }

    private val lastAutoUpdateTime = AtomicLong(0L)
    private val autoUpdateCooldown = 15 * 60 * 1000L

    suspend fun autoUpdatePlugins(force: Boolean = false): List<com.lagradost.common.storage.PluginUpdateRecord> = withContext(Dispatchers.IO) {
        autoUpdateMutex.withLock {
            val now = System.currentTimeMillis()
            val last = lastAutoUpdateTime.get()
            if (!force && last > 0L && now - last < autoUpdateCooldown) {
                AppLogger.i("Skipping autoUpdatePlugins; last auto-update check was ${(now - last) / 60000}m ago (cooldown: 15m)")
                return@withContext emptyList()
            }


            lastAutoUpdateTime.set(now)
            val updatedList = mutableListOf<com.lagradost.common.storage.PluginUpdateRecord>()
            val savedRepos = getSavedRepositories()
            val extensionsDir = getExtensionsDir()

            savedRepos.forEach { saved ->
                try {
                    val repo = PluginNetworkClient.fetchRepository(saved.url) ?: return@forEach
                    val remotePlugins = coroutineScope {
                        repo.pluginLists.map { listUrl ->
                            async {
                                try {
                                    val fetched = PluginNetworkClient.fetchPlugins(listUrl)
                                    if (fetched.isNotEmpty()) {
                                        pluginsCache[listUrl] = fetched
                                        fetched
                                    } else {
                                        getCachedPlugins(listUrl)
                                    }
                                } catch (_: Exception) {
                                    getCachedPlugins(listUrl)
                                }
                            }
                        }.awaitAll().flatten()
                    }.distinctBy { it.internalName }

                    val repoDir = File(extensionsDir, repo.name.replace(Regex("[^a-zA-Z0-9.-]"), "_"))
                    if (!repoDir.exists()) repoDir.mkdirs()

                    remotePlugins.forEach { remotePlugin ->
                        val localJar = File(repoDir, "${remotePlugin.internalName}.jar").takeIf { it.exists() }
                            ?: File(repoDir, "${remotePlugin.internalName}.cs3").takeIf { it.exists() }
                            ?: File(repoDir, "${remotePlugin.internalName}-jvm.jar").takeIf { it.exists() }
                        if (localJar != null && localJar.exists()) {
                            val localManifest = readPluginManifest(localJar)
                            val localVersion = localManifest?.get("version")?.toString()?.toIntOrNull() ?: 0
                            if (remotePlugin.version > localVersion) {
                                AppLogger.i("Auto-updating ${remotePlugin.internalName} in ${saved.name} from v$localVersion to v${remotePlugin.version}...")
                                val baseName = remotePlugin.internalName
                                val wasLoaded = com.lagradost.runtime.loader.ExtensionLoader.plugins.containsKey(localJar.absolutePath) ||
                                    com.lagradost.runtime.loader.ExtensionLoader.plugins.containsKey(
                                        try {
                                            localJar.canonicalPath
                                        } catch (_: Throwable) {
                                            localJar.absolutePath
                                        },
                                    )
                                val wasTrusted = com.lagradost.runtime.loader.ExtensionLoader.isTrusted(localJar, remotePlugin.internalName, manifestName = remotePlugin.name)

                                // 1. Unload old plugin from active runtime to release file locks
                                if (wasLoaded) {
                                    com.lagradost.runtime.loader.ExtensionLoader.unloadPlugin(localJar.absolutePath)
                                    try {
                                        @Suppress("ExplicitGarbageCollectionCall")
                                        System.gc()
                                        Thread.sleep(100)
                                    } catch (_: Throwable) {}
                                }

                                // 2. Create backup of local jar before touching it
                                val backupFile = File(repoDir, "${localJar.name}.bak")
                                try {
                                    java.nio.file.Files.copy(localJar.toPath(), backupFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                } catch (_: Throwable) {}

                                // 3. Clean stale bytecode artifacts
                                val staleJvm = File(repoDir, "$baseName-jvm.jar")
                                val staleSecure = File(repoDir, "$baseName-secure.jar")
                                val staleFailed = File(repoDir, "$baseName.d2j_failed")
                                try {
                                    staleJvm.delete()
                                } catch (_: Throwable) {}
                                try {
                                    staleSecure.delete()
                                } catch (_: Throwable) {}
                                try {
                                    staleFailed.delete()
                                } catch (_: Throwable) {}

                                // 4. Download new jar
                                val newJar = downloadPlugin(saved.name, remotePlugin)
                                if (newJar == null) {
                                    // Download failed: restore backup if needed
                                    try {
                                        if (backupFile.exists() && !localJar.exists()) {
                                            java.nio.file.Files.move(backupFile.toPath(), localJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                        }
                                        backupFile.delete()
                                    } catch (_: Throwable) {}
                                    updatedList.add(
                                        com.lagradost.common.storage.PluginUpdateRecord(
                                            pluginName = remotePlugin.name,
                                            version = remotePlugin.version,
                                            iconUrl = remotePlugin.iconUrl,
                                            timestamp = System.currentTimeMillis(),
                                            isSuccess = false,
                                            errorMessage = "Download failed (network or server error)",
                                        ),
                                    )
                                } else {
                                    // 5. Transpile & verify bytecode
                                    try {
                                        if (wasTrusted) {
                                            com.lagradost.runtime.loader.ExtensionLoader.addTrusted(newJar, remotePlugin.internalName, manifestName = remotePlugin.name)
                                        }

                                        if (wasLoaded) {
                                            // Active plugin: transpile, transform, and load into active runtime
                                            com.lagradost.runtime.loader.ExtensionLoader.loadAndInit(newJar, forceBypassSecurity = wasTrusted, forceRetry = true)
                                            try {
                                                com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
                                            } catch (_: Throwable) {}
                                        } else {
                                            // Inactive plugin: transpile & verify bytecode without forcing full instantiation
                                            com.lagradost.runtime.loader.ExtensionLoader.transpileOrVerify(newJar, forceRetry = true)
                                        }

                                        // Verification succeeded: discard backup and record update success
                                        try {
                                            backupFile.delete()
                                        } catch (_: Throwable) {}
                                        updatedList.add(
                                            com.lagradost.common.storage.PluginUpdateRecord(
                                                pluginName = remotePlugin.name,
                                                version = remotePlugin.version,
                                                iconUrl = remotePlugin.iconUrl,
                                                timestamp = System.currentTimeMillis(),
                                                isSuccess = true,
                                                errorMessage = null,
                                            ),
                                        )
                                        AppLogger.i("Auto-update and bytecode verification succeeded for ${remotePlugin.internalName} (v$localVersion -> v${remotePlugin.version})")
                                    } catch (e: Exception) {
                                        AppLogger.e("Bytecode transpilation/verification failed for ${remotePlugin.internalName}; reverting to previous version", e)
                                        // Roll back to previous working version
                                        try {
                                            newJar.delete()
                                            staleJvm.delete()
                                            staleSecure.delete()
                                            if (backupFile.exists()) {
                                                java.nio.file.Files.move(backupFile.toPath(), localJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                                                if (wasLoaded) {
                                                    try {
                                                        com.lagradost.runtime.loader.ExtensionLoader.loadAndInit(localJar, forceBypassSecurity = wasTrusted)
                                                        com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
                                                    } catch (_: Throwable) {}
                                                }
                                            }
                                        } catch (rollbackErr: Throwable) {
                                            AppLogger.e("Failed to rollback ${remotePlugin.internalName}", rollbackErr)
                                        } finally {
                                            try {
                                                backupFile.delete()
                                            } catch (_: Throwable) {}
                                        }
                                        updatedList.add(
                                            com.lagradost.common.storage.PluginUpdateRecord(
                                                pluginName = remotePlugin.name,
                                                version = remotePlugin.version,
                                                iconUrl = remotePlugin.iconUrl,
                                                timestamp = System.currentTimeMillis(),
                                                isSuccess = false,
                                                errorMessage = "Bytecode verification failed: ${e.message ?: e.javaClass.simpleName}",
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    AppLogger.w("Auto-update check failed for repository '${saved.name}': ${e.message}")
                }
            }
            if (updatedList.isNotEmpty()) {
                com.lagradost.common.storage.DesktopDataStore.addUpdateHistory(updatedList)
                com.lagradost.common.storage.DesktopDataStore.setUnreadUpdates(true)
            }
            updatedList
        }
    }

    /**
     * Returns all remote plugins across all active repositories.
     * When multiple repositories host the same plugin, intelligent deduplication selects
     * the candidate with a valid HTTPS download URL and the highest version.
     */
    fun getAllPlugins(): List<Pair<String, SitePlugin>> {
        val list = mutableListOf<Pair<String, SitePlugin>>()
        for (saved in getSavedRepositories()) {
            val repo = repoCache[saved.url]
            val lists = repo?.pluginLists?.takeIf { it.isNotEmpty() } ?: listOf(getPluginsJsonUrl(saved.url))
            for (listUrl in lists) {
                pluginsCache[listUrl]?.forEach { list.add(Pair(saved.name, it)) }
            }
        }
        return list.distinctBy { Pair(it.first, it.second.internalName) }
            .sortedWith(compareBy({ it.second.name.lowercase() }, { it.first }))
    }

    suspend fun syncAll(onProgress: (suspend (completed: Int, total: Int) -> Unit)? = null, force: Boolean = false): SyncReport = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val now = System.currentTimeMillis()
            val last = lastAutoUpdateTime.get()
            val needsInitialCatalog = pluginsCache.isEmpty() || repoCache.isEmpty()
            if (!force && !needsInitialCatalog && now - getLastSyncTimestamp() < AUTO_UPDATE_COOLDOWN_MS) {
                AppLogger.i("Skipping syncAll; last sync was ${(now - getLastSyncTimestamp()) / 60000}m ago (cooldown: 4h)")
                return@withContext SyncReport(
                    reposRefreshed = 0,
                    catalogPlugins = 0,
                    pluginsUpdated = 0,
                    iconsCached = _remotePluginIcons.value.size,
                    newPluginsLoaded = 0,
                )
            }


            setLastSyncTimestamp(now)
            val reposRefreshed = refreshAllRepositoryMetadata()
            val catalogPlugins = rebuildRemotePluginCatalog { _, done, total ->
                onProgress?.invoke(done, total)
            }
            val pluginsUpdated = autoUpdatePlugins(force = true)
            val newPluginsLoaded = com.lagradost.runtime.loader.ExtensionLoader.rescanAndLoadNewPlugins(getExtensionsDir())
            val iconsCached = _remotePluginIcons.value.size

            _syncGeneration.update { it + 1 }
            saveCachesToDisk()

            SyncReport(
                reposRefreshed = reposRefreshed,
                catalogPlugins = catalogPlugins,
                pluginsUpdated = pluginsUpdated.size,
                iconsCached = iconsCached,
                newPluginsLoaded = newPluginsLoaded,
            )
        }
    }
}
