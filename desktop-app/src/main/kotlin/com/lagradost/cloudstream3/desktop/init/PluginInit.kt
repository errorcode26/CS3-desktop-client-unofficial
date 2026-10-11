package com.lagradost.cloudstream3.desktop.init

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.utils.appScope
import com.lagradost.cloudstream3.desktop.stremio.StremioApi
import com.lagradost.cloudstream3.metaproviders.CrossTmdbProvider
import com.lagradost.cloudstream3.metaproviders.TmdbProvider
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * Register the built-in meta-providers that ship with the client.
 */
fun initProviders() {
    val builtIns = listOf(
        TmdbProvider(),
        TraktProvider(),
        CrossTmdbProvider(),
        StremioApi(),
    )
    synchronized(APIHolder.allProviders) {
        builtIns.forEach { provider ->
            provider.sourcePlugin = "built-in"
            APIHolder.allProviders.add(provider)
            APIHolder.addPluginMapping(provider)
        }
    }
    AppLogger.i("Registered ${builtIns.size} built-in meta-providers")
}

private val isPluginsInitialized = java.util.concurrent.atomic.AtomicBoolean(false)
private val pluginInitLock = Any()

/**
 * Load installed plugins and cloned sites.
 */
fun initPlugins() {
    synchronized(pluginInitLock) {
        if (isPluginsInitialized.getAndSet(true)) {
            AppLogger.i("initPlugins() already executed; skipping duplicate invocation.")
            return
        }
        if (SafeModeState.isSafeMode.value) {
            AppLogger.w("[SafeMode] Third-party plugins bypassed. Only built-in providers will be loaded.")
            try {
                com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
            } catch (e: Throwable) {
                AppLogger.e("Failed to refresh ActiveProviderRepository in Safe Mode", e)
            }
            return
        }
        loadInstalledPlugins()
        loadClonedSites()
        try {
            com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
            AppLogger.i("ActiveProviderRepository refreshed after plugin initialization.")
        } catch (e: Throwable) {
            AppLogger.e("Failed to refresh ActiveProviderRepository after plugin init", e)
        }
    }
}

/**
 * Loads only the active plugin JAR at startup, deferring all others until requested on-demand.
 */
private fun loadInstalledPlugins() {
    val extensionsDir = PlatformPaths.extensionsDir
    if (!extensionsDir.exists()) {
        AppLogger.w("No extensions directory found at ${extensionsDir.absolutePath}")
        return
    }

    val jarFiles = extensionsDir.walkTopDown()
        .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
        .filter { !it.name.endsWith("-jvm.jar") && !it.name.endsWith(".dex") && !it.name.contains("-secure") }
        .sortedBy { it.lastModified() }
        .toList()

    if (jarFiles.isEmpty()) {
        AppLogger.i("No plugins found in ${extensionsDir.absolutePath}")
        return
    }

    val lastActiveJarPath = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PreferenceKeys.PREF_LAST_ACTIVE_PLUGIN_JAR)
    val lastSelectedKey = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PreferenceKeys.PREF_SELECTED_PROVIDER)
    val activeKeys = com.lagradost.common.storage.DesktopDataStore.getKey<List<String>>(PreferenceKeys.PREF_ACTIVE_PROVIDERS) ?: emptyList()

    val jarsToLoad = mutableSetOf<java.io.File>()

    if (!lastActiveJarPath.isNullOrBlank()) {
        val f = java.io.File(lastActiveJarPath)
        if (f.exists()) jarsToLoad.add(f)
    }

    if (jarsToLoad.isEmpty()) {
        val keysToMatch = (listOfNotNull(lastSelectedKey) + activeKeys).distinct()
        for (key in keysToMatch) {
            val folderOrProvider = if (key.contains("::")) key.substringBefore("::") else key
            val providerName = if (key.contains("::")) key.substringAfter("::") else key

            val match = jarFiles.firstOrNull { jar ->
                val repoFolder = jar.parentFile?.name.orEmpty()
                val manifest = DesktopRepositoryManager.readPluginManifest(jar)
                val manifestName = manifest?.get("name")?.toString().orEmpty()
                val manifestInternal = manifest?.get("internalName")?.toString().orEmpty()

                (folderOrProvider.isNotBlank() && repoFolder.equals(folderOrProvider, ignoreCase = true)) ||
                manifestName.equals(providerName, ignoreCase = true) ||
                manifestInternal.equals(providerName, ignoreCase = true) ||
                jar.nameWithoutExtension.equals(providerName, ignoreCase = true)
            }
            if (match != null) jarsToLoad.add(match)
        }
    }

    // Fallback: if no active provider was matched but plugins exist, load the most recent one
    if (jarsToLoad.isEmpty() && jarFiles.isNotEmpty()) {
        jarsToLoad.add(jarFiles.last())
    }

    var loaded = 0
    for (jarFile in jarsToLoad) {
        try {
            ExtensionLoader.loadAndInit(jarFile)
            loaded++
            AppLogger.i("Loaded active plugin at startup: ${jarFile.name}")
        } catch (e: Throwable) {
            AppLogger.e("Failed to load active plugin: ${jarFile.name}", e)
        }
    }
    AppLogger.i("Startup plugin loading complete: $loaded active plugin(s) loaded (of ${jarFiles.size} installed)")
}

/**
 * Loads an inactive plugin into the JVM on-demand.
 */
fun loadPluginForJar(jarFile: java.io.File): Boolean {
    return try {
        ExtensionLoader.loadAndInit(jarFile)
        com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
        true
    } catch (e: Throwable) {
        AppLogger.e("Failed to load plugin on-demand: ${jarFile.name}", e)
        false
    }
}

/**
 * Resolves and loads an inactive plugin by provider key or name on-demand.
 */
fun loadPluginForProvider(providerKey: String): Boolean {
    val extensionsDir = PlatformPaths.extensionsDir
    if (!extensionsDir.exists()) return false

    val jarFiles = extensionsDir.walkTopDown()
        .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
        .filter { !it.name.endsWith("-jvm.jar") && !it.name.endsWith(".dex") && !it.name.contains("-secure") }
        .toList()

    val folderOrProvider = if (providerKey.contains("::")) providerKey.substringBefore("::") else providerKey
    val providerName = if (providerKey.contains("::")) providerKey.substringAfter("::") else providerKey

    val match = jarFiles.firstOrNull { jar ->
        val repoFolder = jar.parentFile?.name.orEmpty()
        val manifest = DesktopRepositoryManager.readPluginManifest(jar)
        val manifestName = manifest?.get("name")?.toString().orEmpty()
        val manifestInternal = manifest?.get("internalName")?.toString().orEmpty()

        (folderOrProvider.isNotBlank() && repoFolder.equals(folderOrProvider, ignoreCase = true)) ||
        manifestName.equals(providerName, ignoreCase = true) ||
        manifestInternal.equals(providerName, ignoreCase = true) ||
        jar.nameWithoutExtension.equals(providerName, ignoreCase = true)
    }

    return if (match != null) {
        loadPluginForJar(match)
    } else false
}

/**
 * Loads all remaining installed plugins on-demand (e.g. when opening the provider selection dialog).
 */
fun loadAllRemainingPlugins(): Int {
    val extensionsDir = PlatformPaths.extensionsDir
    if (!extensionsDir.exists()) return 0

    val jarFiles = extensionsDir.walkTopDown()
        .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
        .filter { !it.name.endsWith("-jvm.jar") && !it.name.endsWith(".dex") && !it.name.contains("-secure") }
        .toList()

    var count = 0
    for (jar in jarFiles) {
        val isAlreadyLoaded = ExtensionLoader.plugins.containsKey(jar.absolutePath) ||
            ExtensionLoader.plugins.containsKey(try { jar.canonicalPath } catch (_: Throwable) { jar.absolutePath })
        if (!isAlreadyLoaded) {
            try {
                ExtensionLoader.loadAndInit(jar)
                count++
                com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
            } catch (e: Throwable) {
                AppLogger.w("Failed to load plugin on-demand: ${jar.name}: ${e.message}")
            }
        }
    }
    PluginWarmupState.update(
        PluginWarmupProgress(
            current = jarFiles.size,
            total = jarFiles.size,
            currentPluginName = "",
            isWarmingUp = false,
            isComplete = true,
        )
    )
    return count
}

data class PluginWarmupProgress(
    val current: Int = 0,
    val total: Int = 0,
    val currentPluginName: String = "",
    val isWarmingUp: Boolean = false,
    val isComplete: Boolean = true,
)

object PluginWarmupState {
    private val _progress = MutableStateFlow(PluginWarmupProgress())
    val progress: StateFlow<PluginWarmupProgress> = _progress.asStateFlow()

    fun update(progress: PluginWarmupProgress) {
        _progress.value = progress
    }
}

private val isWarmingUpActive = java.util.concurrent.atomic.AtomicBoolean(false)

/**
 * Progressively loads all remaining installed plugins sequentially in the background.
 * Uses cooperative yielding and delays between each plugin to prevent CPU spikes or RAM ballooning.
 */
suspend fun warmupRemainingPluginsProgressively() {
    if (SafeModeState.isSafeMode.value) return
    if (!isWarmingUpActive.compareAndSet(false, true)) return

    try {
        val extensionsDir = PlatformPaths.extensionsDir
        if (!extensionsDir.exists()) {
            PluginWarmupState.update(PluginWarmupProgress(isWarmingUp = false, isComplete = true))
            return
        }

        val jarFiles = extensionsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
            .filter { !it.name.endsWith("-jvm.jar") && !it.name.endsWith(".dex") && !it.name.contains("-secure") }
            .sortedBy { it.lastModified() }
            .toList()

        val uninitialized = jarFiles.filter { jar ->
            !ExtensionLoader.plugins.containsKey(jar.absolutePath) &&
                !ExtensionLoader.plugins.containsKey(try { jar.canonicalPath } catch (_: Throwable) { jar.absolutePath })
        }

        if (uninitialized.isEmpty()) {
            PluginWarmupState.update(PluginWarmupProgress(isWarmingUp = false, isComplete = true))
            return
        }

        val total = uninitialized.size
        PluginWarmupState.update(
            PluginWarmupProgress(
                current = 0,
                total = total,
                currentPluginName = "",
                isWarmingUp = true,
                isComplete = false,
            )
        )

        // Initial grace period to allow the Home Screen to fetch its content with full network/CPU bandwidth
        delay(2_000L)

        var loadedCount = 0
        for ((index, jar) in uninitialized.withIndex()) {
            if (SafeModeState.isSafeMode.value) {
                AppLogger.w("Safe mode enabled during plugin warmup; aborting remainder.")
                break
            }

            val manifest = DesktopRepositoryManager.readPluginManifest(jar)
            val pluginName = manifest?.get("name")?.toString() ?: jar.nameWithoutExtension

            PluginWarmupState.update(
                PluginWarmupProgress(
                    current = index + 1,
                    total = total,
                    currentPluginName = pluginName,
                    isWarmingUp = true,
                    isComplete = false,
                )
            )

            val isAlreadyLoaded = ExtensionLoader.plugins.containsKey(jar.absolutePath) ||
                ExtensionLoader.plugins.containsKey(try { jar.canonicalPath } catch (_: Throwable) { jar.absolutePath })

            if (!isAlreadyLoaded) {
                try {
                    withTimeout(3_500L) {
                        ExtensionLoader.loadAndInit(jar)
                    }
                    loadedCount++
                    AppLogger.d("Background warmed up plugin (${index + 1}/$total): ${jar.name}")
                } catch (e: TimeoutCancellationException) {
                    AppLogger.w("Background warmup timed out for ${jar.name} after 3.5s; skipping to next plugin.")
                } catch (e: Throwable) {
                    AppLogger.w("Failed to warmup plugin in background: ${jar.name}: ${e.message}")
                }
            }

            // Batch refresh providers periodically and on the final plugin to minimize recompositions
            if (loadedCount > 0 && (loadedCount % 4 == 0 || index == uninitialized.lastIndex)) {
                try {
                    com.lagradost.cloudstream3.desktop.repo.ActiveProviderRepository.refreshProviders()
                } catch (_: Throwable) {}
            }

            // Pacing delay to avoid CPU/RAM spikes and give JVM GC breathing room
            yield()
            delay(200L)
        }

        AppLogger.i("Background plugin warmup finished: $loadedCount plugin(s) warmed up.")
    } finally {
        PluginWarmupState.update(
            PluginWarmupProgress(
                current = PluginWarmupState.progress.value.total,
                total = PluginWarmupState.progress.value.total,
                currentPluginName = "",
                isWarmingUp = false,
                isComplete = true,
            )
        )
        isWarmingUpActive.set(false)
    }
}

/**
 * Reads CustomSite configurations from DesktopDataStore and instantiates cloned providers with custom URLs.
 */
fun loadClonedSites() {
    try {
        val clonedSitesJson = com.lagradost.common.storage.DesktopDataStore.getKey<String>(PreferenceKeys.USER_PROVIDER_API)
        if (clonedSitesJson != null) {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val list = mapper.readValue<List<com.lagradost.cloudstream3.desktop.models.CustomSite>>(
                clonedSitesJson,
                object : com.fasterxml.jackson.core.type.TypeReference<List<com.lagradost.cloudstream3.desktop.models.CustomSite>>() {},
            )
            if (list.isEmpty()) return

            synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                list.distinctBy { "${it.name}_${it.url.trimEnd('/')}" }.forEach { custom ->
                    val cleanUrl = custom.url.trimEnd('/')
                    com.lagradost.cloudstream3.APIHolder.allProviders.firstOrNull { it.javaClass.simpleName == custom.parentJavaClass }?.let { baseProvider ->
                        // If the clone points to the exact same URL as the base provider, skip it!
                        if (baseProvider.mainUrl.trimEnd('/') == cleanUrl) {
                            AppLogger.i("Skipping redundant clone '${custom.name}' (matches base provider URL: $cleanUrl)")
                            return@forEach
                        }
                        val alreadyExists = com.lagradost.cloudstream3.APIHolder.allProviders.any {
                            it.name == custom.name && it.mainUrl.trimEnd('/') == cleanUrl
                        }
                        if (!alreadyExists) {
                            val clone = baseProvider.javaClass.getDeclaredConstructor().newInstance()
                            clone.name = custom.name
                            clone.lang = custom.lang
                            clone.mainUrl = cleanUrl
                            clone.canBeOverridden = false
                            com.lagradost.cloudstream3.APIHolder.allProviders.add(clone)
                            com.lagradost.cloudstream3.APIHolder.addPluginMapping(clone)
                        }
                    }
                }
            }
            AppLogger.i("Loaded ${list.size} cloned sites")
        }
    } catch (e: Exception) {
        AppLogger.e("Failed to load cloned sites", e)
    }
}

/**
 * Launches the background auto-updater that syncs all repositories.
 */
fun launchAutoUpdater(force: Boolean = false) {
    appScope.launch(Dispatchers.IO) {
        try {
            DesktopRepositoryManager.syncAll(force = force)
        } catch (e: Exception) {
            AppLogger.e("Startup sync failed", e)
        }
    }
}
