package com.lagradost.runtime.loader

import android.content.DesktopContextProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.logging.AppLogger
import com.lagradost.common.storage.DesktopDataStore
import com.lagradost.common.storage.PluginSettingsSchemaRegistry
import com.lagradost.runtime.dex.DexConverter
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

object ExtensionLoader {

    private val mapper = ObjectMapper().registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // Keep track of loaded plugins by absolute and canonical paths
    val plugins: MutableMap<String, BasePlugin> = java.util.concurrent.ConcurrentHashMap()
    private val pluginLoadLocks: java.util.concurrent.ConcurrentHashMap<String, Any> = java.util.concurrent.ConcurrentHashMap()

    // Map class loader to plugin name
    val classLoaders: MutableMap<ClassLoader, String> = java.util.concurrent.ConcurrentHashMap()

    // Map class loader to jar file
    val classLoaderToJar: MutableMap<ClassLoader, File> = java.util.concurrent.ConcurrentHashMap()

    // Map class loader to class names loaded from its jar
    val classLoaderToClassNames: MutableMap<ClassLoader, Set<String>> = java.util.concurrent.ConcurrentHashMap()

    // Concurrency lock to ensure only one bytecode transpilation runs at a time
    private val dex2JarLock = Any()

    // Memoize permanently failed DEX translations to prevent infinite retry loops
    private val failedDexCache: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    // Master registry of all registered providers across all loaded plugins
    val masterProviders: java.util.concurrent.ConcurrentHashMap<String, com.lagradost.cloudstream3.MainAPI> = java.util.concurrent.ConcurrentHashMap()

    /**
     * Filters APIHolder.allProviders and APIHolder.apis against the user's active studio/provider settings.
     */
    fun filterActiveProviders() {
        try {
            synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                com.lagradost.cloudstream3.APIHolder.allProviders.forEach { p ->
                    masterProviders[p.name] = p
                }
                if (masterProviders.isEmpty()) return

                val active = masterProviders.values.filter { provider ->
                    isProviderActive(provider)
                }

                com.lagradost.cloudstream3.APIHolder.allProviders.clear()
                com.lagradost.cloudstream3.APIHolder.allProviders.addAll(active)

                com.lagradost.cloudstream3.APIHolder.apis = com.lagradost.cloudstream3.utils.Coroutines.atomicListOf(*active.toTypedArray())
                com.lagradost.cloudstream3.APIHolder.apiMap = null
            }
        } catch (_: Throwable) {}
    }

    private fun isProviderActive(provider: com.lagradost.cloudstream3.MainAPI): Boolean {
        try {
            // Check any registered StringSet schema that maps to provider names
            for ((_, schemas) in PluginSettingsSchemaRegistry.schemas) {
                for ((schemaKey, schema) in schemas) {
                    if (schema.type == "StringSet") {
                        val fullStorageKey = schema.storageKey
                        val savedSet = DesktopDataStore.getKey<Set<String>>(fullStorageKey)
                            ?: DesktopDataStore.getKey<Set<String>>(schemaKey)
                        if (savedSet != null) {
                            val defaultSet = (schema.defaultValue as? Set<*>)?.mapNotNull { it?.toString() }?.toSet()
                            val isDisablingKey = schemaKey.contains("disable", ignoreCase = true)
                            if (isDisablingKey) {
                                if (provider.name in savedSet) return false
                            } else if (defaultSet != null && defaultSet.contains(provider.name)) {
                                if (provider.name !in savedSet) return false
                            }
                        }
                    }
                }
            }

            // Direct fallback checks for known studio toggles
            val candidateKeys = listOf(
                "choose_studios",
                "CNC Verse__choose_studios",
                "CNC_Verse__choose_studios",
                "CNCVerse__choose_studios",
            )
            for (key in candidateKeys) {
                val studios = DesktopDataStore.getKey<Set<String>>(key)
                if (studios != null && studios.isNotEmpty()) {
                    val knownStudios = setOf("Netflix", "Prime Video", "Hotstar", "Disney", "Marvel", "Star Wars", "Pixar")
                    if (provider.name in knownStudios) {
                        return provider.name in studios
                    }
                }
            }
        } catch (_: Throwable) {}
        return true
    }

    /**
     * Creates a classloader that searches all registered plugin classloaders before
     * delegating to [fallback]. Used to fix kotlin-reflect resolution failures when
     * Jackson deserializes plugin-defined inner classes across classloader boundaries.
     */
    fun createCompositeClassLoader(fallback: ClassLoader): ClassLoader {
        val pluginLoaders = classLoaders.keys.toList()
        return object : ClassLoader(fallback) {
            override fun loadClass(name: String, resolve: Boolean): Class<*> {
                for (loader in pluginLoaders) {
                    try {
                        return loader.loadClass(name)
                    } catch (_: ClassNotFoundException) {}
                }
                return super.loadClass(name, resolve)
            }
        }
    }

    fun getCallingPluginName(): String? {
        try {
            val walker = java.lang.StackWalker.getInstance(java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE)
            val name = walker.walk { stream ->
                stream.map { it.declaringClass }
                    .filter { clazz ->
                        val loader = clazz.classLoader
                        loader != null && classLoaders.containsKey(loader)
                    }
                    .map { clazz -> classLoaders[clazz.classLoader] }
                    .findFirst()
                    .orElse(null)
            }
            if (name != null) return name
        } catch (e: Throwable) {
            // Ignored, fallback below
        }

        val stackTrace = Thread.currentThread().stackTrace
        for (element in stackTrace) {
            val className = element.className
            if (className.startsWith("com.lagradost.") || className.startsWith("java.") || className.startsWith("kotlin.")) continue

            for ((loader, name) in classLoaders) {
                val classes = classLoaderToClassNames[loader]
                if (classes != null && classes.contains(className)) {
                    return name
                }
            }
        }
        return null
    }

    // Native plugin interceptors
    var nativePluginInterceptor: ((String) -> BasePlugin?)? = null

    fun clearFailedMarker(jarFile: File) {
        try {
            val baseName = jarFile.nameWithoutExtension.substringBefore("-jvm").substringBefore("-secure")
            val failedMarker = File(jarFile.parentFile, "$baseName.d2j_failed")
            if (failedMarker.exists()) failedMarker.delete()
            failedDexCache.remove(baseName)
        } catch (_: Throwable) {}
    }

    data class TranspiledPluginInfo(
        val jarToLoad: File,
        val pluginClassName: String,
        val internalName: String,
        val displayName: String?,
        val isTrusted: Boolean,
    )

    fun transpileOrVerify(
        jarFile: File,
        fallbackPluginClassName: String? = null,
        forceBypassSecurity: Boolean = false,
        forceRetry: Boolean = false,
    ): TranspiledPluginInfo {
        if (!jarFile.exists()) {
            throw IllegalArgumentException("Jar file does not exist: ${jarFile.absolutePath}")
        }

        var pluginClassName = fallbackPluginClassName
        var internalNameFromManifest: String? = null
        var nameFromManifest: String? = null
        var jarToLoad = jarFile

        ZipFile(jarFile).use { zip ->
            // Try to extract manifest to get actual class name
            val manifestEntry = zip.getEntry("manifest.json")
            if (manifestEntry != null) {
                zip.getInputStream(manifestEntry).use { input ->
                    val manifestData = mapper.readValue(input, Map::class.java)
                    val className = manifestData["pluginClassName"] as? String
                    if (className != null) {
                        pluginClassName = className
                    }
                    internalNameFromManifest = manifestData["internalName"] as? String
                    nameFromManifest = manifestData["name"] as? String
                }
            }

            // Check if archive already contains compiled JVM .class bytecode
            val hasJvmClasses = zip.entries().asSequence().any { it.name.endsWith(".class") }

            val dexEntry = zip.getEntry("classes.dex")
            if (hasJvmClasses) {
                val secureJar = if (jarFile.name.endsWith("-secure.jar")) {
                    jarFile
                } else {
                    File(jarFile.parentFile, jarFile.nameWithoutExtension.substringBefore("-secure") + "-secure.jar")
                }
                val isCacheValid = secureJar == jarFile || (
                    secureJar.exists() && secureJar.lastModified() >= jarFile.lastModified() &&
                        checkJarHasTransformerVersion(secureJar) &&
                        (pluginClassName == null || checkJarHasClass(secureJar, pluginClassName!!))
                    )

                if (!isCacheValid) {
                    AppLogger.i("[PluginLoader] Securing Native JVM JAR: ${jarFile.name}...")
                    java.nio.file.Files.copy(jarFile.toPath(), secureJar.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    PluginBytecodeTransformer.transform(secureJar)
                } else if (secureJar != jarFile) {
                    AppLogger.i("[PluginLoader] Using cached Secure JVM JAR: ${secureJar.name}")
                }
                jarToLoad = secureJar
            } else if (dexEntry != null) {
                val convertedJar = File(jarFile.parentFile, jarFile.nameWithoutExtension + "-jvm.jar")
                val failedMarker = File(jarFile.parentFile, jarFile.nameWithoutExtension + ".d2j_failed")

                if (forceRetry || (convertedJar.exists() && checkJarHasTransformerVersion(convertedJar))) {
                    try {
                        failedMarker.delete()
                    } catch (_: Throwable) {}
                    failedDexCache.remove(jarFile.nameWithoutExtension)
                } else if (failedMarker.exists() || failedDexCache.contains(jarFile.nameWithoutExtension)) {
                    throw IllegalStateException("Skipping previously failed Dalvik DEX translation for ${jarFile.name} (un-transpilable bytecode)")
                }

                val isCacheValid = !forceRetry && convertedJar.exists() && convertedJar.lastModified() >= jarFile.lastModified() &&
                    checkJarHasTransformerVersion(convertedJar) &&
                    (pluginClassName == null || checkJarHasClass(convertedJar, pluginClassName!!))

                if (!isCacheValid) {
                    synchronized(dex2JarLock) {
                        if (!forceRetry && (failedMarker.exists() || failedDexCache.contains(jarFile.nameWithoutExtension))) {
                            throw IllegalStateException("Skipping previously failed Dalvik DEX translation for ${jarFile.name}")
                        }
                        if (forceRetry || !convertedJar.exists() || convertedJar.lastModified() < jarFile.lastModified() || !checkJarHasTransformerVersion(convertedJar)) {
                            var translationSucceeded = false
                            AppLogger.i("[PluginLoader] Transpiling Dalvik DEX -> JVM JAR for ${jarFile.name}...")
                            try {
                                AppLogger.i("[PluginLoader] Starting high-efficiency DEX translation (bitset liveness)...")
                                val result = DexConverter.convert(jarFile, convertedJar)
                                if (result.errors.isNotEmpty()) {
                                    AppLogger.w("[PluginLoader] DEX translation warnings for ${jarFile.name}: ${result.errors.joinToString("; ")}")
                                }
                                if (result.failedMethods.isNotEmpty()) {
                                    AppLogger.w("[PluginLoader] DEX translation could not translate ${result.failedMethods.size} methods in ${jarFile.name}: ${result.failedMethods.take(5).joinToString("; ")}")
                                }
                                AppLogger.i("[PluginLoader] DEX translation completed: ${result.classCount} classes in ${result.millis}ms (${result.compactedMethods.size} compacted).")
                                translationSucceeded = true
                            } catch (t: Throwable) {
                                if (convertedJar.exists() && checkJarHasTransformerVersion(convertedJar) && t is java.io.IOException) {
                                    AppLogger.w("[PluginLoader] Could not overwrite ${convertedJar.name} during DEX translation (${t.message}). Reusing existing valid transformed JAR.")
                                } else {
                                    AppLogger.e("[PluginLoader] DEX translation failed for ${jarFile.name}!", t)
                                    convertedJar.delete()
                                    try {
                                        failedMarker.createNewFile()
                                    } catch (_: Throwable) {}
                                    failedDexCache.add(jarFile.nameWithoutExtension)
                                    if (t is VirtualMachineError) {
                                        System.gc()
                                    }
                                    throw IllegalStateException("Failed to transpile Dalvik DEX to JVM bytecode for ${jarFile.name}: ${t.message}", t)
                                }
                            }

                            if (!convertedJar.exists() || convertedJar.length() == 0L) {
                                convertedJar.delete()
                                try {
                                    failedMarker.createNewFile()
                                } catch (_: Throwable) {}
                                failedDexCache.add(jarFile.nameWithoutExtension)
                                throw IllegalStateException("DEX translation finished but no valid JAR was produced at ${convertedJar.absolutePath}")
                            } else if (translationSucceeded) {
                                PluginBytecodeTransformer.transform(convertedJar)
                            }
                        }
                    }
                } else {
                    AppLogger.i("[PluginLoader] Using cached JVM JAR: ${convertedJar.name}")
                }

                jarToLoad = convertedJar
            }
        }

        if (pluginClassName == null) {
            throw IllegalArgumentException("Could not determine pluginClassName from manifest.json and no fallback provided.")
        }

        val finalInternalName = internalNameFromManifest ?: nameFromManifest ?: pluginClassName?.split(".")?.lastOrNull() ?: jarFile.nameWithoutExtension.removeSuffix("-jvm")

        val isPluginTrusted = forceBypassSecurity || isTrusted(jarToLoad, finalInternalName, pluginClassName, nameFromManifest)
        if (forceBypassSecurity) {
            addTrusted(jarToLoad, finalInternalName, pluginClassName, nameFromManifest)
        }

        AppLogger.i("Running static bytecode security verification on ${jarToLoad.name} (Trusted: $isPluginTrusted)...")
        com.lagradost.runtime.security.PluginSecurityVerifier.verifyJar(jarToLoad, finalInternalName, isPluginTrusted)

        return TranspiledPluginInfo(
            jarToLoad = jarToLoad,
            pluginClassName = pluginClassName!!,
            internalName = finalInternalName,
            displayName = nameFromManifest,
            isTrusted = isPluginTrusted,
        )
    }

    fun loadJar(
        jarFile: File,
        fallbackPluginClassName: String? = null,
        forceBypassSecurity: Boolean = false,
        forceRetry: Boolean = false,
    ): BasePlugin {
        val info = transpileOrVerify(jarFile, fallbackPluginClassName, forceBypassSecurity, forceRetry)
        val jarToLoad = info.jarToLoad
        val pluginClassName = info.pluginClassName
        val finalInternalName = info.internalName
        val isPluginTrusted = info.isTrusted

        AppLogger.i("[PluginLoader] Initializing class $pluginClassName from ${jarToLoad.name}")

        val nativeIntercept = nativePluginInterceptor?.invoke(pluginClassName!!)
        val pluginInstance: BasePlugin = if (nativeIntercept != null) {
            AppLogger.i("Intercepted plugin $pluginClassName! Injecting native JVM implementation.")
            nativeIntercept
        } else {
            val safeParentLoader = SafePluginClassLoader(this::class.java.classLoader, isPluginTrusted)
            val classLoader = CompatPluginClassLoader(arrayOf(jarToLoad.toURI().toURL()), safeParentLoader)
            val pluginClass = classLoader.loadClass(pluginClassName)

            // MegaPlugin VerifiedRepo MixIn injection
            if (pluginClassName == "com.mega.MegaPlugin") {
                try {
                    val verifiedRepoClass = classLoader.loadClass("com.mega.MegaPlugin\$getRepositories\$VerifiedRepo")
                    com.lagradost.cloudstream3.mapper.addMixIn(verifiedRepoClass, VerifiedRepoMixIn::class.java)
                } catch (e: Exception) {
                    AppLogger.i("Failed to inject VerifiedRepo MixIn for MegaPlugin (it might not be loaded yet)")
                }
            }

            val instance = pluginClass.getDeclaredConstructor().newInstance() as BasePlugin
            classLoaders[classLoader] = finalInternalName
            classLoaderToJar[classLoader] = jarFile

            val classNames = mutableSetOf<String>()
            try {
                ZipFile(jarToLoad).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        if (entry.name.endsWith(".class")) {
                            val cName = entry.name.removeSuffix(".class").replace("/", ".")
                            classNames.add(cName)
                        }
                    }
                }
            } catch (t: Throwable) {
                // Ignore zip errors
            }
            classLoaderToClassNames[classLoader] = classNames

            // Clear schema registry for this plugin to avoid stale/ghost duplicates across reloads
            com.lagradost.common.storage.PluginSettingsSchemaRegistry.schemas["${finalInternalName}_"]?.clear()

            // Proactively scan for any Android XML preferences and populate schema registry
            scanAllXmlPreferences(jarToLoad, finalInternalName)

            // Universally discover and register any sub-providers/scrapers defined in plugin classes
            autoDiscoverPluginSubProviders(classLoader, classNames, finalInternalName)

            if (finalInternalName == "StreamPlay") {
                try {
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "use_trakt_source",
                        type = "Boolean",
                        defaultValue = false,
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "provider_concurrency",
                        type = "Int",
                        defaultValue = 8,
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "enabled_plugins_saved",
                        type = "StringSet",
                        defaultValue = setOf("StreamPlay", "StreamPlay-Anime"),
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "streamplay_stremio_saved_links",
                        type = "String",
                        defaultValue = "",
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "streamplay_stremio_addon_saved_links",
                        type = "String",
                        defaultValue = "",
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "wyzie_key",
                        type = "String",
                        defaultValue = "",
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "tmdb_language_code",
                        type = "String",
                        defaultValue = "en-US",
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "token",
                        type = "String",
                        defaultValue = "",
                        isGlobal = false,
                    )
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "StreamPlay_",
                        key = "provider_profiles",
                        type = "String",
                        defaultValue = "",
                        isGlobal = false,
                    )
                    AppLogger.i("StreamPlay: Proactively registered settings keys.")
                } catch (e: Exception) {
                    AppLogger.e("StreamPlay: Failed to proactively register settings keys", e)
                }
            }

            if (finalInternalName.contains("PlayFy", ignoreCase = true)) {
                try {
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "${finalInternalName}_",
                        key = "choose_playlist",
                        type = "String",
                        defaultValue = "Default Playlist",
                        options = mapOf("Default Playlist" to "Default Playlist"),
                    )
                } catch (_: Exception) {}
            }
            if (finalInternalName.contains("Cricify", ignoreCase = true)) {
                try {
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "${finalInternalName}_",
                        key = "choose_playlist",
                        type = "String",
                        defaultValue = "Default Playlist",
                        options = mapOf("Default Playlist" to "Default Playlist"),
                    )
                } catch (_: Exception) {}
            }
            if (finalInternalName.contains("CNC", ignoreCase = true) || finalInternalName.contains("Verse", ignoreCase = true)) {
                try {
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "${finalInternalName}_",
                        key = "choose_studios",
                        type = "StringSet",
                        defaultValue = setOf("Netflix", "Prime Video", "Hotstar", "Disney", "Marvel", "Star Wars", "Pixar"),
                    )
                } catch (_: Exception) {}
            }
            if (finalInternalName.contains("MovieBox", ignoreCase = true)) {
                try {
                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                        pluginPrefName = "${finalInternalName}_",
                        key = "moviebox_host",
                        type = "String",
                        defaultValue = "https://api3.aoneroom.com",
                        options = mapOf(
                            "Server 1 (api3.aoneroom.com)" to "https://api3.aoneroom.com",
                            "Server 2 (api.aoneroom.com)" to "https://api.aoneroom.com",
                            "Server 3 (api1.aoneroom.com)" to "https://api1.aoneroom.com",
                            "Server 4 (api2.aoneroom.com)" to "https://api2.aoneroom.com",
                        ),
                    )
                } catch (_: Exception) {}
            }

            instance
        }

        pluginInstance.filename = jarFile.absolutePath
        // store plugin instance for later unloading
        plugins[jarFile.absolutePath] = pluginInstance
        try {
            plugins[jarFile.canonicalPath] = pluginInstance
        } catch (_: Throwable) {}

        // Backfill sourcePlugin for any provider/extractor registered during constructor init
        // when pluginInstance.filename was not yet assigned
        try {
            synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                com.lagradost.cloudstream3.APIHolder.allProviders.forEach { provider ->
                    if (provider.sourcePlugin == null && provider.sourcePlugin != "built-in") {
                        provider.sourcePlugin = jarFile.absolutePath
                    }
                }
                // Deduplicate providers by unique name across plugins
                val seenKeys = mutableSetOf<String>()
                val toKeep = mutableListOf<com.lagradost.cloudstream3.MainAPI>()
                for (provider in com.lagradost.cloudstream3.APIHolder.allProviders.reversed()) {
                    val uniqueKey = provider.name
                    if (seenKeys.add(uniqueKey)) {
                        toKeep.add(provider)
                    } else {
                        try {
                            com.lagradost.cloudstream3.APIHolder.removePluginMapping(provider)
                        } catch (ignored: Throwable) {}
                    }
                }
                com.lagradost.cloudstream3.APIHolder.allProviders.clear()
                com.lagradost.cloudstream3.APIHolder.allProviders.addAll(toKeep.reversed())
                toKeep.forEach { masterProviders[it.name] = it }
                filterActiveProviders()
            }
            com.lagradost.cloudstream3.APIHolder.apis.forEach { provider ->
                if (provider.sourcePlugin == null && provider.sourcePlugin != "built-in") {
                    provider.sourcePlugin = jarFile.absolutePath
                }
            }
            synchronized(com.lagradost.cloudstream3.utils.extractorApis) {
                com.lagradost.cloudstream3.utils.extractorApis.forEach { extractor ->
                    if (extractor.sourcePlugin == null) {
                        extractor.sourcePlugin = jarFile.absolutePath
                    }
                }
                // Deduplicate extractors by unique name
                val seenExtKeys = mutableSetOf<String>()
                val extsToKeep = mutableListOf<com.lagradost.cloudstream3.utils.ExtractorApi>()
                for (ext in com.lagradost.cloudstream3.utils.extractorApis.reversed()) {
                    val uniqueKey = ext.name
                    if (seenExtKeys.add(uniqueKey)) {
                        extsToKeep.add(ext)
                    }
                }
                com.lagradost.cloudstream3.utils.extractorApis.clear()
                com.lagradost.cloudstream3.utils.extractorApis.addAll(extsToKeep.reversed())
            }
        } catch (t: Throwable) {
            AppLogger.i("Failed to backfill sourcePlugin or deduplicate for ${jarFile.name}: ${t.message}")
        }

        return pluginInstance
    }

    private fun getTrustedList(): MutableList<String> {
        val list = mutableListOf<String>()
        try {
            list.addAll(com.lagradost.common.storage.DesktopDataStore.getTrustedPlugins())
        } catch (_: Throwable) {}
        try {
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_prefs")
            val json = prefs.get("trusted_plugins", "[]")
            val legacy = mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<List<String>>() {})
            for (item in legacy) {
                if (!list.contains(item)) list.add(item)
            }
        } catch (_: Throwable) {}
        return list
    }

    fun getPluginAliases(
        jarFile: File? = null,
        internalName: String? = null,
        pluginClassName: String? = null,
        manifestName: String? = null,
    ): Set<String> {
        val keys = mutableSetOf<String>()
        val repoDir = jarFile?.parentFile?.name?.lowercase()?.trim()

        fun addKey(k: String?) {
            if (k.isNullOrBlank()) return
            val clean = k.removeSuffix(".jar").removeSuffix(".cs3").removeSuffix("-jvm").removeSuffix("-secure").lowercase().trim()
            if (clean.isNotBlank()) {
                keys.add(clean)
                val stripped = clean.removeSuffix("provider").removeSuffix("plugin").removePrefix("com.")
                if (stripped.isNotBlank()) keys.add(stripped)
                val lastSegment = clean.substringAfterLast('.')
                if (lastSegment.isNotBlank()) keys.add(lastSegment)
                val lastStripped = lastSegment.removeSuffix("provider").removeSuffix("plugin")
                if (lastStripped.isNotBlank()) keys.add(lastStripped)

                if (repoDir != null && repoDir != "extensions") {
                    keys.add("$repoDir/$clean")
                    if (stripped.isNotBlank()) keys.add("$repoDir/$stripped")
                    if (lastSegment.isNotBlank()) keys.add("$repoDir/$lastSegment")
                    if (lastStripped.isNotBlank()) keys.add("$repoDir/$lastStripped")

                    val repoWithSpaces = repoDir.replace('_', ' ')
                    val repoClean = repoDir.replace(Regex("[^a-z0-9]"), "")
                    if (repoWithSpaces != repoDir) {
                        keys.add("$repoWithSpaces/$clean")
                        if (stripped.isNotBlank()) keys.add("$repoWithSpaces/$stripped")
                    }
                    if (repoClean.isNotBlank() && repoClean != repoDir) {
                        keys.add("$repoClean/$clean")
                        if (stripped.isNotBlank()) keys.add("$repoClean/$stripped")
                    }
                }
            }
        }

        jarFile?.nameWithoutExtension?.let { addKey(it) }
        internalName?.let { addKey(it) }
        manifestName?.let { addKey(it) }
        pluginClassName?.let {
            addKey(it)
            addKey(it.substringAfterLast('.'))
            addKey(it.substringBeforeLast('.'))
        }

        jarFile?.let {
            keys.add(it.absolutePath.lowercase().replace('\\', '/'))
            val relPath = "${repoDir ?: ""}/${it.nameWithoutExtension.removeSuffix("-jvm").removeSuffix("-secure")}".lowercase().trim('/')
            if (relPath.isNotBlank()) keys.add(relPath)
        }

        return keys
    }

    fun isTrusted(
        jarFile: File,
        internalName: String? = null,
        pluginClassName: String? = null,
        manifestName: String? = null,
    ): Boolean {
        val candidateKeys = getPluginAliases(jarFile, internalName, pluginClassName, manifestName)
        val list = getTrustedList().map { it.lowercase().trim() }

        val inList = candidateKeys.any { list.contains(it) }
        val inDataStore = candidateKeys.any { com.lagradost.common.storage.DesktopDataStore.isPluginTrusted(it) }
        return inList || inDataStore
    }

    fun addTrusted(
        jarFile: File,
        internalName: String? = null,
        pluginClassName: String? = null,
        manifestName: String? = null,
    ) {
        val keysToAdd = getPluginAliases(jarFile, internalName, pluginClassName, manifestName)
        val trusted = getTrustedList()
        var changed = false

        for (k in keysToAdd) {
            if (!trusted.any { it.equals(k, ignoreCase = true) }) {
                trusted.add(k)
                changed = true
            }
        }
        com.lagradost.common.storage.DesktopDataStore.setPluginsTrusted(keysToAdd, true)

        if (changed) {
            try {
                val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                val json = mapper.writeValueAsString(trusted)
                if (json.length < 8192) {
                    val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_prefs")
                    prefs.put("trusted_plugins", json)
                }
            } catch (_: Throwable) {}
        }
    }

    fun removeTrusted(
        jarFile: File? = null,
        internalName: String? = null,
        pluginClassName: String? = null,
        manifestName: String? = null,
    ) {
        val keysToRemove = getPluginAliases(jarFile, internalName, pluginClassName, manifestName)
        val trusted = getTrustedList()
        var changed = false

        for (k in keysToRemove) {
            if (trusted.removeAll { it.equals(k, ignoreCase = true) }) {
                changed = true
            }
        }
        com.lagradost.common.storage.DesktopDataStore.setPluginsTrusted(keysToRemove, false)

        if (changed) {
            try {
                val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
                val json = mapper.writeValueAsString(trusted)
                if (json.length < 8192) {
                    val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_prefs")
                    prefs.put("trusted_plugins", json)
                }
            } catch (t: Throwable) {
                AppLogger.w("ExtensionLoader: Failed to sync trusted plugins to OS preferences: ${t.message}")
            }
        }
    }

    fun loadAndInit(
        jarFile: File,
        fallbackPluginClassName: String? = null,
        forceBypassSecurity: Boolean = false,
        forceRetry: Boolean = false,
    ): BasePlugin {
        val canonicalPath = try {
            jarFile.canonicalPath
        } catch (_: Throwable) {
            jarFile.absolutePath
        }
        val lock = pluginLoadLocks.computeIfAbsent(canonicalPath) { Any() }

        synchronized(lock) {
            try {
                if (!forceRetry) {
                    val existing = plugins[canonicalPath] ?: plugins[jarFile.absolutePath]
                    if (existing != null) {
                        AppLogger.i("[ExtensionLoader] Plugin already loaded: ${jarFile.name}. Reusing active instance.")
                        return existing
                    }
                }

                val pluginInstance = loadJar(jarFile, fallbackPluginClassName, forceBypassSecurity, forceRetry)
                initializePlugin(pluginInstance)
                return pluginInstance
            } finally {
                pluginLoadLocks.remove(canonicalPath)
            }
        }
    }

    fun initializePlugin(pluginInstance: BasePlugin) {
        val loader = pluginInstance.javaClass.classLoader
        val pluginName = (loader?.let { classLoaders[it] })
            ?: pluginInstance.filename?.let { File(it).nameWithoutExtension.removeSuffix("-jvm").removeSuffix("-secure") }
            ?: "Plugin"

        if (pluginInstance is Plugin) {
            val storageRoot = com.lagradost.runtime.loader.stubs.PluginFileSecurityStub.getStorageRootForPlugin(pluginName)
            val pluginContext = DesktopContextProvider.createPluginContext(pluginName, storageRoot)
            pluginInstance.activity = pluginContext
            pluginInstance.resources = pluginContext.resources
            pluginInstance.load(pluginContext)
        } else {
            pluginInstance.load()
        }
        if (loader != null) {
            val names = classLoaderToClassNames[loader] ?: emptySet()
            synchronizePluginNetworkClients(loader, names)
            val internalName = classLoaders[loader]
            if (internalName != null) {
                com.lagradost.runtime.executor.PluginCircuitBreaker.resetProvider(internalName)
            }
        }
        val pName = pluginInstance.filename?.let { File(it).nameWithoutExtension.removeSuffix("-jvm") } ?: pluginName
        com.lagradost.runtime.executor.PluginCircuitBreaker.resetProvider(pName)
    }

    fun unloadPlugin(absolutePath: String) {
        val normPath = File(absolutePath).absolutePath
        val canonicalPath = try {
            File(absolutePath).canonicalPath
        } catch (_: Throwable) {
            normPath
        }
        val plugin = plugins[normPath] ?: plugins[absolutePath] ?: plugins[canonicalPath]

        if (plugin != null) {
            try {
                plugin.beforeUnload()
            } catch (t: Throwable) {
                AppLogger.i("Failed to run beforeUnload for $absolutePath: ${t.message}")
            }
        }

        val pathsToRemove = setOfNotNull(normPath, absolutePath, canonicalPath, plugin?.filename)

        // Close the ClassLoader to release file locks on Windows
        val classLoader = plugin?.javaClass?.classLoader
            ?: classLoaderToJar.entries.firstOrNull { pathsToRemove.contains(it.value.absolutePath) }?.key
        if (classLoader != null) {
            classLoaders.remove(classLoader) // Fix Metaspace Leak!
            classLoaderToJar.remove(classLoader)
            classLoaderToClassNames.remove(classLoader)
            if (classLoader is URLClassLoader) {
                try {
                    classLoader.close()
                } catch (t: Throwable) {
                    AppLogger.i("Failed to close URLClassLoader for $absolutePath: ${t.message}")
                }
            }
        }

        // Remove providers and mappings registered by this plugin
        try {
            com.lagradost.cloudstream3.APIHolder.apis.filter { pathsToRemove.contains(it.sourcePlugin) }.forEach {
                com.lagradost.cloudstream3.APIHolder.removePluginMapping(it)
            }
            synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                com.lagradost.cloudstream3.APIHolder.allProviders.removeIf { pathsToRemove.contains(it.sourcePlugin) }
            }
            masterProviders.entries.removeIf { pathsToRemove.contains(it.value.sourcePlugin) }
        } catch (t: Throwable) {
            AppLogger.i("Failed to remove plugin mappings for $absolutePath: ${t.message}")
        }

        try {
            synchronized(com.lagradost.cloudstream3.utils.extractorApis) {
                com.lagradost.cloudstream3.utils.extractorApis.removeIf { pathsToRemove.contains(it.sourcePlugin) }
            }
        } catch (t: Throwable) {
            AppLogger.w("ExtensionLoader: Failed to unregister extractorApis for $absolutePath: ${t.message}")
        }

        try {
            com.lagradost.cloudstream3.actions.VideoClickActionHolder.allVideoClickActions.removeIf { pathsToRemove.contains(it.sourcePlugin) }
        } catch (t: Throwable) {
            AppLogger.w("ExtensionLoader: Failed to unregister videoClickActions for $absolutePath: ${t.message}")
        }

        // Remove from tracked plugins across all possible path keys
        pathsToRemove.forEach { plugins.remove(it) }
    }

    fun unloadAllPlugins() {
        val allPaths = plugins.keys.toList()
        for (path in allPaths) {
            try {
                unloadPlugin(path)
            } catch (t: Throwable) {
                AppLogger.e("Failed to unload plugin $path: ${t.message}")
            }
        }
        for ((loader, _) in classLoaders.toList()) {
            if (loader is java.io.Closeable) {
                try {
                    loader.close()
                } catch (_: Throwable) {}
            }
        }
        classLoaders.clear()
        classLoaderToJar.clear()
        classLoaderToClassNames.clear()
        plugins.clear()
    }

    fun isPluginLoaded(absolutePath: String): Boolean {
        val file = File(absolutePath)
        val canonical = try {
            file.canonicalPath
        } catch (_: Throwable) {
            file.absolutePath
        }
        return plugins.containsKey(absolutePath) ||
            plugins.containsKey(file.absolutePath) ||
            plugins.containsKey(canonical) ||
            pluginLoadLocks.containsKey(canonical) ||
            pluginLoadLocks.containsKey(file.absolutePath)
    }

    fun getPlugin(absolutePath: String): BasePlugin? = plugins[absolutePath]

    /**
     * Loads any extension jars on disk that are not already in memory (e.g. after sync/install).
     */
    fun rescanAndLoadNewPlugins(extensionsDir: File): Int {
        if (!extensionsDir.exists()) return 0

        var loaded = 0
        extensionsDir.walkTopDown()
            .filter { it.isFile && (it.extension == "jar" || it.extension == "cs3") }
            .filter { !it.name.endsWith("-jvm.jar") }
            .sortedBy { it.lastModified() }
            .forEach { jar ->
                if (!isPluginLoaded(jar.absolutePath)) {
                    try {
                        loadAndInit(jar)
                        loaded++
                        AppLogger.i("Rescan: loaded ${jar.name}")
                    } catch (e: Throwable) {
                        AppLogger.e("Rescan: failed ${jar.name}", e)
                    }
                }
            }
        return loaded
    }

    @JvmStatic
    fun parsePluginPreferences(fragment: Any, resId: Int) {
        try {
            val classLoader = fragment.javaClass.classLoader
            val jarFile = classLoaderToJar[classLoader] ?: return
            val pluginPrefName = classLoaders[classLoader] ?: return

            scanAllXmlPreferences(jarFile, pluginPrefName)
        } catch (e: Exception) {
            AppLogger.e("Failed to parse plugin preferences", e)
        }
    }

    @JvmStatic
    fun scanAllXmlPreferences(jarFile: java.io.File, pluginPrefName: String) {
        val finalPrefName = pluginPrefName + "_"
        try {
            val jvmJar = java.io.File(jarFile.parentFile, jarFile.nameWithoutExtension.removeSuffix("-jvm") + "-jvm.jar")
            val scanTarget = if (jvmJar.exists()) jvmJar else jarFile
            com.lagradost.runtime.loader.utils.PluginSettingsScanner.scanJarForSettings(pluginPrefName, scanTarget)
            AppLogger.i("Scanning all XML preferences for $finalPrefName from ${scanTarget.absolutePath}")

            var apkFileLazy: net.dongliu.apk.parser.ApkFile? = null

            java.util.zip.ZipFile(jarFile).use { zip ->
                val allEntries = zip.entries().toList()

                // 1. First scan all res/values/*.xml (arrays.xml, strings.xml, values.xml)
                val valueEntries = allEntries.filter {
                    it.name.startsWith("res/values") && it.name.endsWith(".xml")
                }
                for (entry in valueEntries) {
                    try {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        var xmlString = String(bytes, Charsets.UTF_8)
                        if (!xmlString.trimStart().startsWith("<")) {
                            if (apkFileLazy == null) {
                                try {
                                    apkFileLazy = net.dongliu.apk.parser.ApkFile(jarFile)
                                } catch (_: Exception) {}
                            }
                            if (apkFileLazy != null) {
                                xmlString = apkFileLazy!!.transBinaryXml(entry.name) ?: ""
                            }
                        }
                        if (xmlString.isNotEmpty()) {
                            parseAndRegisterValueResources(xmlString, pluginPrefName)
                        }
                    } catch (e: Exception) {
                        AppLogger.d("Failed to parse value XML ${entry.name}: ${e.message}")
                    }
                }

                // 2. Scan preferences and layout XMLs
                val xmlEntries = allEntries.filter {
                    (it.name.startsWith("res/xml") || it.name.startsWith("res/layout")) && it.name.endsWith(".xml")
                }
                for (entry in xmlEntries) {
                    val path = entry.name
                    AppLogger.i("Found XML path: $path")

                    try {
                        val bytes = zip.getInputStream(entry).use { it.readBytes() }
                        var xmlString = String(bytes, Charsets.UTF_8)

                        // Check if it's likely a binary XML (binary XML typically doesn't start with human-readable '<')
                        if (!xmlString.trimStart().startsWith("<")) {
                            if (apkFileLazy == null) {
                                try {
                                    apkFileLazy = net.dongliu.apk.parser.ApkFile(jarFile)
                                } catch (e: Exception) {
                                    AppLogger.i("Failed to init ApkFile for binary XML decoding: ${e.message}")
                                }
                            }
                            if (apkFileLazy != null) {
                                xmlString = apkFileLazy!!.transBinaryXml(path) ?: ""
                            }
                        }

                        if (xmlString.isNullOrEmpty()) continue

                        val layoutName = path.substringAfterLast('/').substringBeforeLast('.')
                        if (path.startsWith("res/layout")) {
                            android.content.res.DesktopResourceRegistry.registerPluginLayout(pluginPrefName, 0, layoutName, xmlString)
                            android.content.res.DesktopResourceRegistry.registerLayout(0, "${finalPrefName}$layoutName", xmlString)
                        } else if (path.startsWith("res/xml")) {
                            android.content.res.DesktopResourceRegistry.registerPluginXml(pluginPrefName, 0, layoutName, xmlString)
                            android.content.res.DesktopResourceRegistry.registerXml(0, "${finalPrefName}$layoutName", xmlString)
                        }

                        val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
                        val builder = factory.newDocumentBuilder()
                        val document = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xmlString)))

                        val nodeList = document.getElementsByTagName("*")
                        for (i in 0 until nodeList.length) {
                            val node = nodeList.item(i)
                            if (node.nodeType == org.w3c.dom.Node.ELEMENT_NODE) {
                                val element = node as org.w3c.dom.Element
                                val key = element.getAttribute("android:key")
                                if (key.isNotEmpty()) {
                                    val defValueStr = element.getAttribute("android:defaultValue")
                                    var type = "String"
                                    var defValue: Any = defValueStr
                                    var optionsMap: Map<String, String>? = null

                                    if (defValueStr.startsWith("@string/")) {
                                        val resolved = android.content.res.DesktopResourceRegistry.getString(defValueStr, pluginPrefName)
                                        if (resolved != null) defValue = resolved
                                    }

                                    when (element.tagName) {
                                        "CheckBoxPreference", "SwitchPreference", "SwitchPreferenceCompat" -> {
                                            type = "Boolean"
                                            defValue = defValueStr.equals("true", ignoreCase = true)
                                        }
                                        "ListPreference", "DropDownPreference" -> {
                                            type = "String"
                                            val entriesStr = element.getAttribute("android:entries")
                                            val valuesStr = element.getAttribute("android:entryValues")

                                            val entriesList = if (entriesStr.startsWith("@")) {
                                                android.content.res.DesktopResourceRegistry.getStringArray(entriesStr, pluginPrefName)
                                            } else if (entriesStr.isNotBlank()) {
                                                entriesStr.split("|", ",").map { it.trim() }
                                            } else {
                                                null
                                            }

                                            val valuesList = if (valuesStr.startsWith("@")) {
                                                android.content.res.DesktopResourceRegistry.getStringArray(valuesStr, pluginPrefName)
                                            } else if (valuesStr.isNotBlank()) {
                                                valuesStr.split("|", ",").map { it.trim() }
                                            } else {
                                                null
                                            }

                                            if (!entriesList.isNullOrEmpty() && !valuesList.isNullOrEmpty() && entriesList.size == valuesList.size) {
                                                optionsMap = entriesList.zip(valuesList).toMap()
                                            }
                                        }
                                        "MultiSelectListPreference" -> {
                                            type = "StringSet"
                                            val entriesStr = element.getAttribute("android:entries")
                                            val valuesStr = element.getAttribute("android:entryValues")

                                            val entriesList = if (entriesStr.startsWith("@")) {
                                                android.content.res.DesktopResourceRegistry.getStringArray(entriesStr, pluginPrefName)
                                            } else if (entriesStr.isNotBlank()) {
                                                entriesStr.split("|", ",").map { it.trim() }
                                            } else {
                                                null
                                            }

                                            val valuesList = if (valuesStr.startsWith("@")) {
                                                android.content.res.DesktopResourceRegistry.getStringArray(valuesStr, pluginPrefName)
                                            } else if (valuesStr.isNotBlank()) {
                                                valuesStr.split("|", ",").map { it.trim() }
                                            } else {
                                                null
                                            }

                                            if (!entriesList.isNullOrEmpty() && !valuesList.isNullOrEmpty() && entriesList.size == valuesList.size) {
                                                optionsMap = entriesList.zip(valuesList).toMap()
                                            }
                                        }
                                        "SeekBarPreference" -> {
                                            type = "Int"
                                            defValue = defValueStr.toIntOrNull() ?: 0
                                        }
                                        "EditTextPreference" -> {
                                            type = "String"
                                        }
                                        else -> {
                                            if (defValueStr.equals("true", ignoreCase = true) || defValueStr.equals("false", ignoreCase = true)) {
                                                type = "Boolean"
                                                defValue = defValueStr.equals("true", ignoreCase = true)
                                            } else if (defValueStr.toIntOrNull() != null) {
                                                type = "Int"
                                                defValue = defValueStr.toInt()
                                            }
                                        }
                                    }
                                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                                        finalPrefName,
                                        key,
                                        type,
                                        defValue,
                                        false,
                                        optionsMap,
                                    )
                                    AppLogger.i("Registered XML plugin setting: $finalPrefName -> $key ($type = $defValue)")
                                }
                            }
                        }
                    } catch (e: Exception) {
                        AppLogger.e("Failed to parse XML path $path", e)
                    }
                }
            }
            apkFileLazy?.close()

            val cl = classLoaderToJar.entries.find {
                it.value.nameWithoutExtension.substringBefore("-jvm") == jarFile.nameWithoutExtension.substringBefore("-jvm")
            }?.key
            if (cl != null) {
                val classNames = classLoaderToClassNames[cl] ?: emptySet()
                for (cn in classNames) {
                    if (cn.endsWith("\$layout") || cn.endsWith(".R\$layout")) {
                        val clazz = runCatching { cl.loadClass(cn) }.getOrNull() ?: continue
                        for (field in clazz.declaredFields) {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType) {
                                val id = runCatching { field.getInt(null) }.getOrNull() ?: continue
                                val name = field.name
                                val xml = android.content.res.DesktopResourceRegistry.getLayoutXml(name, pluginPrefName)
                                if (xml != null) {
                                    android.content.res.DesktopResourceRegistry.registerPluginLayout(pluginPrefName, id, name, xml)
                                }
                            }
                        }
                    } else if (cn.endsWith("\$id") || cn.endsWith(".R\$id")) {
                        val clazz = runCatching { cl.loadClass(cn) }.getOrNull() ?: continue
                        for (field in clazz.declaredFields) {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType) {
                                val id = runCatching { field.getInt(null) }.getOrNull() ?: continue
                                android.content.res.DesktopResourceRegistry.registerId(field.name, id)
                            }
                        }
                    } else if (cn.endsWith("\$xml") || cn.endsWith(".R\$xml")) {
                        val clazz = runCatching { cl.loadClass(cn) }.getOrNull() ?: continue
                        for (field in clazz.declaredFields) {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType) {
                                val id = runCatching { field.getInt(null) }.getOrNull() ?: continue
                                val name = field.name
                                val xml = android.content.res.DesktopResourceRegistry.getLayoutXml(name, pluginPrefName)
                                if (xml != null) {
                                    android.content.res.DesktopResourceRegistry.registerPluginXml(pluginPrefName, id, name, xml)
                                }
                            }
                        }
                    } else if (cn.endsWith("\$array") || cn.endsWith(".R\$array")) {
                        val clazz = runCatching { cl.loadClass(cn) }.getOrNull() ?: continue
                        for (field in clazz.declaredFields) {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType) {
                                val id = runCatching { field.getInt(null) }.getOrNull() ?: continue
                                android.content.res.DesktopResourceRegistry.registerArrayId(field.name, id)
                            }
                        }
                    } else if (cn.endsWith("\$string") || cn.endsWith(".R\$string")) {
                        val clazz = runCatching { cl.loadClass(cn) }.getOrNull() ?: continue
                        for (field in clazz.declaredFields) {
                            if (java.lang.reflect.Modifier.isStatic(field.modifiers) && field.type == Int::class.javaPrimitiveType) {
                                val id = runCatching { field.getInt(null) }.getOrNull() ?: continue
                                android.content.res.DesktopResourceRegistry.registerStringId(field.name, id)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            AppLogger.e("Failed to parse plugin preferences", e)
        }
    }

    private fun parseAndRegisterValueResources(xmlString: String, pluginPrefName: String) {
        try {
            val factory = javax.xml.parsers.DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            val document = builder.parse(org.xml.sax.InputSource(java.io.StringReader(xmlString)))

            // Parse <string-array name="...">
            val arrayNodes = document.getElementsByTagName("string-array")
            for (i in 0 until arrayNodes.length) {
                val arrayElem = arrayNodes.item(i) as? org.w3c.dom.Element ?: continue
                val name = arrayElem.getAttribute("name")
                if (name.isNotEmpty()) {
                    val items = mutableListOf<String>()
                    val itemNodes = arrayElem.getElementsByTagName("item")
                    for (j in 0 until itemNodes.length) {
                        val itemElem = itemNodes.item(j)
                        items.add(itemElem.textContent?.trim() ?: "")
                    }
                    android.content.res.DesktopResourceRegistry.registerPluginStringArray(pluginPrefName, 0, name, items)
                    AppLogger.d("Registered plugin string-array: $name (${items.size} items)")
                }
            }

            // Parse <string name="...">
            val stringNodes = document.getElementsByTagName("string")
            for (i in 0 until stringNodes.length) {
                val strElem = stringNodes.item(i) as? org.w3c.dom.Element ?: continue
                val name = strElem.getAttribute("name")
                if (name.isNotEmpty()) {
                    val value = strElem.textContent?.trim() ?: ""
                    android.content.res.DesktopResourceRegistry.registerPluginString(pluginPrefName, 0, name, value)
                }
            }
        } catch (_: Exception) {}
    }

    /**
     * Universally discovers and registers internal sub-providers and scraper channels across any plugin JAR.
     * Inspects loaded classes for standard provider factory methods (e.g. buildProviders, getProviders,
     * getBuiltInProviders, getSources) and extracts item IDs and display names via reflection.
     */
    fun autoDiscoverPluginSubProviders(
        classLoader: ClassLoader,
        classNames: Set<String>,
        pluginInternalName: String,
    ) {
        val prefName = "${pluginInternalName}_"
        val candidateMethodNames = listOf("buildProviders", "getProviders", "getBuiltInProviders", "getSources", "loadProviders")

        for (className in classNames) {
            val lower = className.lowercase()
            if (!lower.contains("registry") &&
                !lower.contains("provider") &&
                !lower.contains("plugin") &&
                !lower.contains("source")
            ) {
                continue
            }

            try {
                val clazz = Class.forName(className, false, classLoader)
                for (method in clazz.methods) {
                    if (method.parameterCount == 0 && candidateMethodNames.any { method.name.equals(it, ignoreCase = true) }) {
                        val isStatic = java.lang.reflect.Modifier.isStatic(method.modifiers)
                        val target = if (isStatic) {
                            null
                        } else {
                            clazz.fields.firstOrNull { it.name == "INSTANCE" }?.get(null)
                        }
                        if (target == null && !isStatic) continue
                        val collection = method.invoke(target) as? Collection<*> ?: continue
                        if (collection.isEmpty()) continue

                        val optionsMap = LinkedHashMap<String, String>()
                        for (item in collection) {
                            if (item == null) continue
                            val itemClass = item.javaClass
                            val id = (itemClass.methods.firstOrNull { it.name in listOf("getId", "getKey", "getTag") && it.parameterCount == 0 }?.invoke(item) as? String)
                                ?: continue
                            val name = (itemClass.methods.firstOrNull { it.name in listOf("getName", "getTitle", "getDisplayName") && it.parameterCount == 0 }?.invoke(item) as? String)
                                ?: id
                            optionsMap[id] = name
                        }

                        if (optionsMap.isNotEmpty()) {
                            AppLogger.i("Auto-discovered ${optionsMap.size} sub-providers for $pluginInternalName via ${clazz.name}.${method.name}")

                            if (pluginInternalName == "CineStream") {
                                for ((id, _) in optionsMap) {
                                    com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                                        pluginPrefName = prefName,
                                        key = id,
                                        type = "String",
                                        defaultValue = "true",
                                        isGlobal = false,
                                    )
                                }
                            } else {
                                com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                                    pluginPrefName = prefName,
                                    key = "disabled_providers",
                                    type = "StringSet",
                                    defaultValue = emptySet<String>(),
                                    isGlobal = false,
                                    options = optionsMap,
                                )
                            }
                            return
                        }
                    }
                }
            } catch (_: Throwable) {}
        }
    }

    fun synchronizePluginNetworkClients(
        classLoader: ClassLoader,
        classNames: Set<String>,
    ) {
        val globalBase = app.baseClient

        fun syncRequests(requests: Any?, source: String) {
            if (requests == null) return
            try {
                if (requests is com.lagradost.nicehttp.Requests) {
                    val hasCfKiller = requests.baseClient.interceptors.any {
                        it.javaClass.name.contains("CloudflareKiller")
                    }
                    if (!hasCfKiller) {
                        requests.baseClient = globalBase
                        com.lagradost.runtime.loader.stubs.RequestsStub.syncedClients.add(requests)
                        AppLogger.d("[PluginLoader] Synchronized Requests instance ($source) to global baseClient.")
                    }
                }
            } catch (t: Throwable) {
                AppLogger.w("[PluginLoader] Failed to sync Requests instance ($source): ${t.message}")
            }
        }

        // 1. Check candidate classes in this plugin JAR for static Requests fields (lazy, initialize = false)
        val candidateClasses = classNames.filter { name ->
            val lower = name.lowercase()
            lower.contains("client") || lower.contains("util") || lower.contains("api") ||
                lower.contains("network") || lower.contains("provider") || lower.contains("plugin")
        }
        for (className in candidateClasses) {
            try {
                val clazz = Class.forName(className, false, classLoader)
                for (field in clazz.declaredFields) {
                    if (java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                        com.lagradost.nicehttp.Requests::class.java.isAssignableFrom(field.type)
                    ) {
                        field.isAccessible = true
                        val req = field.get(null)
                        syncRequests(req, "static field ${clazz.name}.${field.name}")
                    }
                }
            } catch (_: Throwable) {
                // Ignore classes that cannot be inspected
            }
        }

        // 2. Sweep only registered providers belonging to this plugin's ClassLoader
        try {
            val providersToSync = synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                com.lagradost.cloudstream3.APIHolder.allProviders.filter { it.javaClass.classLoader == classLoader }
            }
            for (provider in providersToSync) {
                var currentClass: Class<*>? = provider.javaClass
                while (currentClass != null && currentClass != Any::class.java) {
                    for (field in currentClass.declaredFields) {
                        if (!java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                            com.lagradost.nicehttp.Requests::class.java.isAssignableFrom(field.type)
                        ) {
                            field.isAccessible = true
                            val req = field.get(provider)
                            syncRequests(req, "provider field ${provider.name}.${field.name}")
                        }
                    }
                    currentClass = currentClass.superclass
                }
            }
        } catch (_: Throwable) {}

        // 3. Sweep only registered extractors belonging to this plugin's ClassLoader
        try {
            val extractorsToSync = synchronized(com.lagradost.cloudstream3.utils.extractorApis) {
                com.lagradost.cloudstream3.utils.extractorApis.filter { it.javaClass.classLoader == classLoader }
            }
            for (extractor in extractorsToSync) {
                var currentClass: Class<*>? = extractor.javaClass
                while (currentClass != null && currentClass != Any::class.java) {
                    for (field in currentClass.declaredFields) {
                        if (!java.lang.reflect.Modifier.isStatic(field.modifiers) &&
                            com.lagradost.nicehttp.Requests::class.java.isAssignableFrom(field.type)
                        ) {
                            field.isAccessible = true
                            val req = field.get(extractor)
                            syncRequests(req, "extractor field ${extractor.name}.${field.name}")
                        }
                    }
                    currentClass = currentClass.superclass
                }
            }
        } catch (_: Throwable) {}
    }

    private fun checkJarHasClass(jar: File, className: String): Boolean {
        return try {
            val entryPath = className.replace('.', '/') + ".class"
            ZipFile(jar).use { zip ->
                zip.getEntry(entryPath) != null
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun checkJarHasTransformerVersion(jar: File): Boolean {
        return try {
            ZipFile(jar).use { zip ->
                val entry = zip.getEntry("META-INF/shadowui.version")
                if (entry != null) {
                    val ver = zip.getInputStream(entry).use { it.readBytes().toString(Charsets.UTF_8).trim() }
                    ver == "2"
                } else {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }
}

abstract class VerifiedRepoMixIn {
    @com.fasterxml.jackson.annotation.JsonCreator
    constructor(
        @com.fasterxml.jackson.annotation.JsonProperty("url") url: String?,
        @com.fasterxml.jackson.annotation.JsonProperty("verified") verified: Boolean?,
    )
}
