package com.lagradost.cloudstream3.desktop.data.providers

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.ProviderType
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.domain.providers.repository.ActiveProviderRepository
import com.lagradost.cloudstream3.desktop.repo.DesktopRepositoryManager
import com.lagradost.cloudstream3.desktop.sandbox.SandboxedPluginManager
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.platform.PlatformPaths
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.runtime.loader.ExtensionLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

open class LazyPluginProvider(
    nameStr: String,
    sourcePluginPath: String,
    override val supportedTypes: Set<TvType> = setOf(TvType.Movie, TvType.TvSeries),
) : MainAPI() {
    override val mainPage: List<com.lagradost.cloudstream3.MainPageData> = emptyList()

    init {
        this.name = nameStr
        this.sourcePlugin = sourcePluginPath
        this.mainUrl = "https://unloaded-plugin"
    }
}

class ActiveProviderRepositoryImpl(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : ActiveProviderRepository {
    private val _allRealProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    override val allRealProviders: StateFlow<List<MainAPI>> = _allRealProviders.asStateFlow()

    private val _activeProviderKeys = MutableStateFlow<List<String>>(emptyList())
    override val activeProviderKeys: StateFlow<List<String>> = _activeProviderKeys.asStateFlow()

    private val _activeProviders = MutableStateFlow<List<MainAPI>>(emptyList())
    override val activeProviders: StateFlow<List<MainAPI>> = _activeProviders.asStateFlow()

    private val _currentSelectedProvider = MutableStateFlow<MainAPI?>(null)
    override val currentSelectedProvider: StateFlow<MainAPI?> = _currentSelectedProvider.asStateFlow()

    private val inFlightLoads = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        // Initial load from storage
        scope.launch(Dispatchers.IO) {
            val savedKeys = DesktopDataStore.getKey<List<String>>(PreferenceKeys.PREF_ACTIVE_PROVIDERS)
            val fallbackKey = DesktopDataStore.getKey<String>(PreferenceKeys.PREF_SELECTED_PROVIDER)
            val initialKeys = savedKeys ?: listOfNotNull(fallbackKey)
            _activeProviderKeys.value = initialKeys
            refreshProviders()
        }

        // Re-sync whenever repositories/plugins change
        scope.launch {
            DesktopRepositoryManager.syncGeneration.collectLatest {
                refreshProviders()
            }
        }
    }

    override fun isRealContentProvider(api: MainAPI): Boolean {
        if (api.name.equals("NONE", ignoreCase = true)) return false
        if (api.providerType == ProviderType.MetaProvider) return false
        return true
    }

    override fun getProviderKey(api: MainAPI): String {
        val src = api.sourcePlugin
        if (!src.isNullOrBlank() && src != "built-in") {
            val folder = File(src).parentFile?.name ?: ""
            if (folder.isNotBlank()) return "$folder::${api.name}"
        }
        return api.name
    }

    override fun matchesKey(api: MainAPI, key: String): Boolean {
        val provKey = getProviderKey(api)
        if (provKey.equals(key, ignoreCase = true)) return true

        if (key.contains("::")) {
            val keyFolder = key.substringBefore("::")
            val keyName = key.substringAfter("::")
            val myFolder = api.sourcePlugin?.let { File(it).parentFile?.name } ?: ""
            if (myFolder.equals(keyFolder, ignoreCase = true) && api.name.equals(keyName, ignoreCase = true)) {
                return true
            }
        }
        return api.name.equals(key, ignoreCase = true)
    }

    private fun parseTvTypes(raw: Any?, name: String): Set<TvType> {
        val result = mutableSetOf<TvType>()
        if (raw is List<*>) {
            raw.forEach { item ->
                try {
                    result.add(TvType.valueOf(item.toString()))
                } catch (_: Throwable) {}
            }
        }
        if (result.isEmpty()) {
            val lower = name.lowercase()
            if (lower.contains("sport") || lower.contains("cricify") || lower.contains("live") || lower.contains("tv")) {
                result.add(TvType.Live)
            } else {
                result.add(TvType.Movie)
                result.add(TvType.TvSeries)
            }
        }
        return result
    }

    override fun refreshProviders() {
        val loadedRealApis = APIHolder.allProviders.filter { isRealContentProvider(it) }
        val loadedJarPaths = loadedRealApis.mapNotNull { it.sourcePlugin }.toSet()

        val extensionsDir = PlatformPaths.extensionsDir
        val unmountedStubs = mutableListOf<MainAPI>()

        if (extensionsDir.exists()) {
            val jarFiles = extensionsDir.walkTopDown()
                .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
                .filter { !it.name.endsWith("-jvm.jar") && !it.name.endsWith(".dex") && !it.name.contains("-secure") }
                .toList()

            for (jar in jarFiles) {
                val absPath = jar.absolutePath
                val canPath = try {
                    jar.canonicalPath
                } catch (_: Throwable) {
                    absPath
                }
                val isLoaded = ExtensionLoader.plugins.containsKey(absPath) ||
                    ExtensionLoader.plugins.containsKey(canPath) ||
                    loadedJarPaths.any { it == absPath || it == canPath }

                if (!isLoaded) {
                    val manifest = DesktopRepositoryManager.readPluginManifest(jar)
                    val manifestName = manifest?.get("name")?.toString() ?: jar.nameWithoutExtension

                    if (jar.name.contains("CNC Verse", ignoreCase = true) || manifestName.equals("CNC Verse", ignoreCase = true)) {
                        val cncStudios = listOf("Netflix", "Prime Video", "Hotstar", "Disney", "Marvel", "Star Wars", "Pixar")
                        for (studio in cncStudios) {
                            unmountedStubs.add(LazyPluginProvider(nameStr = studio, sourcePluginPath = absPath))
                        }
                    } else if (jar.nameWithoutExtension.equals("StreamPlay", ignoreCase = true) || manifestName.equals("StreamPlay", ignoreCase = true)) {
                        unmountedStubs.add(LazyPluginProvider(nameStr = "StreamPlay", sourcePluginPath = absPath, supportedTypes = parseTvTypes(manifest?.get("tvTypes"), "StreamPlay")))
                        unmountedStubs.add(LazyPluginProvider(nameStr = "StreamPlay-Anime", sourcePluginPath = absPath, supportedTypes = setOf(TvType.Anime, TvType.Movie, TvType.TvSeries)))
                    } else {
                        val types = parseTvTypes(manifest?.get("tvTypes"), manifestName)
                        unmountedStubs.add(LazyPluginProvider(nameStr = manifestName, sourcePluginPath = absPath, supportedTypes = types))
                    }
                }
            }
        }

        val allApis = (loadedRealApis + unmountedStubs)
            .distinctBy { getProviderKey(it) }
            .sortedBy { it.name.lowercase() }

        _allRealProviders.value = allApis

        val currentKeys = _activeProviderKeys.value
        val resolvedActive = currentKeys.mapNotNull { key ->
            allApis.firstOrNull { matchesKey(it, key) }
        }.ifEmpty {
            allApis.take(1)
        }

        _activeProviders.value = resolvedActive

        val selected = _currentSelectedProvider.value
        if (selected == null || resolvedActive.none { it.name == selected.name && it.sourcePlugin == selected.sourcePlugin }) {
            val newSelected = resolvedActive.firstOrNull() ?: allApis.firstOrNull()
            if (newSelected != null) {
                setSelectedProvider(newSelected)
            }
        }
    }

    override fun setActiveProviders(keys: List<String>) {
        _activeProviderKeys.value = keys

        val currentAll = _allRealProviders.value
        val lazyProvidersToMount = currentAll.filter { api ->
            api is LazyPluginProvider && keys.any { matchesKey(api, it) }
        }

        if (lazyProvidersToMount.isNotEmpty()) {
            scope.launch(Dispatchers.IO) {
                val jarsToLoad = lazyProvidersToMount.mapNotNull { it.sourcePlugin?.let { p -> File(p) } }.distinct()
                var anyMounted = false
                for (jar in jarsToLoad) {
                    val canonical = try {
                        jar.canonicalPath
                    } catch (_: Throwable) {
                        jar.absolutePath
                    }
                    val isAlreadyLoaded = (SandboxedPluginManager.isEnabled() && APIHolder.allProviders.any { it.sourcePlugin == jar.absolutePath || it.sourcePlugin == canonical }) ||
                        ExtensionLoader.isPluginLoaded(jar.absolutePath) || ExtensionLoader.isPluginLoaded(canonical)
                    if (jar.exists() && !isAlreadyLoaded && inFlightLoads.add(canonical)) {
                        try {
                            if (SandboxedPluginManager.isEnabled()) {
                                SandboxedPluginManager.loadPlugin(jar)
                            } else {
                                ExtensionLoader.loadAndInit(jar)
                            }
                            anyMounted = true
                            AppLogger.i("Mounted lazy plugin on-demand: ${jar.name}")
                        } catch (e: Throwable) {
                            AppLogger.e("Failed to mount lazy plugin ${jar.name}", e)
                        } finally {
                            inFlightLoads.remove(canonical)
                        }
                    }
                }
                if (anyMounted) {
                    refreshProviders()
                }
            }
        } else {
            refreshProviders()
        }

        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_ACTIVE_PROVIDERS, keys)
        }
    }

    override fun setSelectedProvider(api: MainAPI) {
        val previous = _currentSelectedProvider.value
        if (previous != null && previous.name != api.name) {
            com.lagradost.cloudstream3.desktop.network.SystemBrowserCdpBypass.closeProxySession()
        }
        _currentSelectedProvider.value = api
        val key = getProviderKey(api)
        scope.launch(Dispatchers.IO) {
            DesktopDataStore.setKey(PreferenceKeys.PREF_SELECTED_PROVIDER, key)
            val jarPath = api.sourcePlugin
            if (!jarPath.isNullOrBlank() && jarPath != "built-in") {
                DesktopDataStore.setKey(PreferenceKeys.PREF_LAST_ACTIVE_PLUGIN_JAR, jarPath)
            }
        }

        if (api is LazyPluginProvider) {
            val jarPath = api.sourcePlugin
            if (!jarPath.isNullOrBlank()) {
                val jar = File(jarPath)
                val canonical = try {
                    jar.canonicalPath
                } catch (_: Throwable) {
                    jar.absolutePath
                }
                val isAlreadyLoaded = (SandboxedPluginManager.isEnabled() && APIHolder.allProviders.any { it.sourcePlugin == jar.absolutePath || it.sourcePlugin == canonical }) ||
                    ExtensionLoader.isPluginLoaded(jar.absolutePath) || ExtensionLoader.isPluginLoaded(canonical)
                if (jar.exists() && !isAlreadyLoaded && inFlightLoads.add(canonical)) {
                    scope.launch(Dispatchers.IO) {
                        try {
                            if (SandboxedPluginManager.isEnabled()) {
                                SandboxedPluginManager.loadPlugin(jar)
                            } else {
                                ExtensionLoader.loadAndInit(jar)
                            }
                            AppLogger.i("Mounted lazy selected plugin on-demand: ${jar.name}")
                            refreshProviders()
                        } catch (e: Throwable) {
                            AppLogger.e("Failed to mount lazy selected plugin ${jar.name}", e)
                        } finally {
                            inFlightLoads.remove(canonical)
                        }
                    }
                }
            }
        }
    }

    override fun setSelectedProviderByName(name: String, sourcePlugin: String?, mainUrl: String?) {
        val all = _allRealProviders.value
        val matched = all.firstOrNull {
            it.name == name && (sourcePlugin == null || it.sourcePlugin == sourcePlugin) && (mainUrl == null || it.mainUrl == mainUrl)
        } ?: all.firstOrNull {
            it.name == name && (sourcePlugin == null || it.sourcePlugin == sourcePlugin)
        } ?: all.firstOrNull { it.name == name }
        if (matched != null) {
            setSelectedProvider(matched)
        } else {
            scope.launch(Dispatchers.IO) {
                if (com.lagradost.cloudstream3.desktop.init.loadPluginForProvider(name)) {
                    refreshProviders()
                    val newlyMatched = _allRealProviders.value.firstOrNull { it.name == name }
                    if (newlyMatched != null) {
                        setSelectedProvider(newlyMatched)
                    }
                }
            }
        }
    }
}
