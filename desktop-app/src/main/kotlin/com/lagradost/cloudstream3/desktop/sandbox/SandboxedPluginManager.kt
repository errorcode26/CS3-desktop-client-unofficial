package com.lagradost.cloudstream3.desktop.sandbox

import com.lagradost.cloudstream3.APIHolder
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.desktop.core.preference.PreferenceKeys
import com.lagradost.cloudstream3.desktop.sandbox.host.SandboxedPluginHost
import com.lagradost.cloudstream3.desktop.sandbox.proxy.SandboxedMainApiProxy
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Singleton supervisor managing the lifecycle of the low-privilege sandboxed JVM host.
 */
class SandboxedDummyPlugin(jarPath: String) : com.lagradost.cloudstream3.plugins.Plugin() {
    init {
        this.filename = jarPath
    }
}

object SandboxedPluginManager : Closeable {

    init {
        initSandboxInterceptor()
    }

    fun initSandboxInterceptor() {
        if (!isEnabled()) return
        com.lagradost.runtime.loader.ExtensionLoader.sandboxLoadInterceptor = { jarFile ->
            val proxies = kotlinx.coroutines.runBlocking {
                loadPlugin(jarFile)
            }
            if (proxies.isNotEmpty()) {
                val dummy = SandboxedDummyPlugin(jarFile.absolutePath)
                val canonical = try { jarFile.canonicalPath } catch (_: Throwable) { jarFile.absolutePath }
                com.lagradost.runtime.loader.ExtensionLoader.plugins[canonical] = dummy
                com.lagradost.runtime.loader.ExtensionLoader.plugins[jarFile.absolutePath] = dummy
                dummy
            } else {
                null
            }
        }
        AppLogger.i("SandboxedPluginManager", "Universal Sandbox Load Interceptor installed on ExtensionLoader.")
    }

    private val registeredProxies = ConcurrentHashMap<String, MutableList<SandboxedMainApiProxy>>()
    private val hostLock = Any()
    @Volatile
    private var activeHost: SandboxedPluginHost? = null

    fun isSupported(): Boolean {
        return System.getProperty("os.name").lowercase().contains("windows")
    }

    fun isEnabled(): Boolean {
        if (!isSupported()) return false
        if (activeHost?.isHostAvailable == false) return false
        return DesktopDataStore.getKey<Boolean>(PreferenceKeys.PREF_SANDBOX_ENABLED) ?: true
    }

    fun getHost(): SandboxedPluginHost {
        return activeHost ?: synchronized(hostLock) {
            activeHost ?: SandboxedPluginHost().also { activeHost = it }
        }
    }

    suspend fun loadPlugin(jarFile: File): List<MainAPI> {
        val host = getHost()
        try {
            host.syncSettings(DesktopDataStore.rawKeyCache)
        } catch (_: Throwable) {}

        val allMetadata = try {
            host.loadPlugin(jarFile)
        } catch (e: Throwable) {
            AppLogger.w("SandboxedPluginManager", "Failed to load plugin in sandbox: ${jarFile.name} (${e.message})")
            null
        }
        if (allMetadata == null) {
            AppLogger.w("SandboxedPluginManager", "Plugin sandbox unavailable or failed for ${jarFile.name}; will fall back to in-process")
            return emptyList()
        }

        try {
            val internalName = jarFile.nameWithoutExtension.removeSuffix("-jvm").removeSuffix("-secure")
            com.lagradost.runtime.loader.ExtensionLoader.scanAllXmlPreferences(jarFile, internalName)
        } catch (_: Throwable) {}

        val canonicalPath = try { jarFile.canonicalPath } catch (_: Throwable) { jarFile.absolutePath }

        // Find providers corresponding to this jar
        val matchingMeta = allMetadata.filter { meta ->
            meta.sourcePlugin == jarFile.absolutePath || meta.sourcePlugin == canonicalPath
        }.ifEmpty {
            val existingNames = APIHolder.allProviders.map { it.name }.toSet()
            allMetadata.filter { it.name !in existingNames }
        }

        val proxies = matchingMeta.map { meta ->
            SandboxedMainApiProxy(
                metadata = meta.copy(sourcePlugin = jarFile.absolutePath),
                host = host
            )
        }

        synchronized(APIHolder.allProviders) {
            proxies.forEach { proxy ->
                APIHolder.allProviders.removeAll { it.name == proxy.name }
                APIHolder.allProviders.add(proxy)
                APIHolder.addPluginMapping(proxy)
            }
        }

        registeredProxies.getOrPut(canonicalPath) { mutableListOf() }.addAll(proxies)
        val dummy = SandboxedDummyPlugin(jarFile.absolutePath)
        com.lagradost.runtime.loader.ExtensionLoader.plugins[canonicalPath] = dummy
        com.lagradost.runtime.loader.ExtensionLoader.plugins[jarFile.absolutePath] = dummy
        AppLogger.i("SandboxedPluginManager", "Loaded ${proxies.size} sandboxed provider(s) for ${jarFile.name}")
        return proxies
    }

    suspend fun unloadPlugin(jarFile: File) {
        val canonicalPath = try { jarFile.canonicalPath } catch (_: Throwable) { jarFile.absolutePath }
        val paths = setOf(jarFile.absolutePath, canonicalPath)

        val removedProxies = (registeredProxies.remove(canonicalPath) ?: emptyList()) +
            (registeredProxies.remove(jarFile.absolutePath) ?: emptyList())
        synchronized(APIHolder.allProviders) {
            val namesToRemove = removedProxies.map { it.name }.toSet()
            APIHolder.allProviders.removeIf { it.name in namesToRemove || it.sourcePlugin in paths }
            removedProxies.forEach { proxy ->
                APIHolder.removePluginMapping(proxy)
            }
        }

        paths.forEach { path ->
            com.lagradost.runtime.loader.ExtensionLoader.plugins.remove(path)
        }

        activeHost?.unloadPlugin(jarFile)
        AppLogger.i("SandboxedPluginManager", "Unloaded sandboxed plugin: ${jarFile.name}")
    }

    fun syncSettings(settings: Map<String, String>) {
        if (!isEnabled()) return
        val host = activeHost ?: return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                host.syncSettings(settings)
            } catch (e: Throwable) {
                AppLogger.d("SandboxedPluginManager", "Failed to sync settings: ${e.message}")
            }
        }
    }

    override fun close() {
        synchronized(hostLock) {
            activeHost?.close()
            activeHost = null
            registeredProxies.clear()
        }
    }
}
