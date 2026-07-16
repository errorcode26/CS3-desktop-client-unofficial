package com.lagradost.runtime.loader

import android.content.DesktopContextProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.googlecode.dex2jar.tools.Dex2jarCmd
import com.lagradost.cloudstream3.plugins.BasePlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.common.logging.AppLogger
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile

object ExtensionLoader {

    private val mapper = ObjectMapper().registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    // Keep track of loaded plugins by absolute path
    val plugins: MutableMap<String, BasePlugin> = mutableMapOf()

    // Map class loader to plugin name
    val classLoaders: MutableMap<ClassLoader, String> = java.util.concurrent.ConcurrentHashMap()

    // Map class loader to jar file
    val classLoaderToJar: MutableMap<ClassLoader, File> = java.util.concurrent.ConcurrentHashMap()

    // Map class loader to class names loaded from its jar
    val classLoaderToClassNames: MutableMap<ClassLoader, Set<String>> = java.util.concurrent.ConcurrentHashMap()

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

    fun loadJar(jarFile: File, fallbackPluginClassName: String? = null, forceBypassSecurity: Boolean = false): BasePlugin {
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
                jarToLoad = jarFile
                AppLogger.i("[PluginLoader] Native JVM JAR detected: ${jarFile.name}")
            } else if (dexEntry != null) {
                val convertedJar = File(jarFile.parentFile, jarFile.nameWithoutExtension + "-jvm.jar")
                val isCacheValid = convertedJar.exists() && convertedJar.lastModified() >= jarFile.lastModified() &&
                    (pluginClassName == null || checkJarHasClass(convertedJar, pluginClassName!!))

                if (!isCacheValid) {
                    AppLogger.i("[PluginLoader] Transpiling Dalvik DEX -> JVM JAR for ${jarFile.name}...")
                    val dexFile = File(jarFile.parentFile, jarFile.nameWithoutExtension + ".dex")
                    zip.getInputStream(dexEntry).use { input ->
                        Files.copy(input, dexFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }

                    try {
                        Dex2jarCmd().doMain("-f", dexFile.absolutePath, "-o", convertedJar.absolutePath)
                    } catch (e: Exception) {
                        Dex2jarCmd.main("-f", dexFile.absolutePath, "-o", convertedJar.absolutePath)
                    }

                    PluginBytecodeTransformer.transform(convertedJar)
                    dexFile.delete()
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

        AppLogger.i("[PluginLoader] Initializing class $pluginClassName from ${jarToLoad.name}")

        val isPluginTrusted = forceBypassSecurity || isTrusted(jarToLoad)
        if (forceBypassSecurity) {
            addTrusted(jarToLoad)
        }

        AppLogger.i("Running static bytecode security verification on ${jarToLoad.name} (Trusted: $isPluginTrusted)...")
        com.lagradost.runtime.security.PluginSecurityVerifier.verifyJar(jarToLoad, finalInternalName, isPluginTrusted)

        val nativeIntercept = nativePluginInterceptor?.invoke(pluginClassName!!)
        val pluginInstance: BasePlugin = if (nativeIntercept != null) {
            AppLogger.i("Intercepted plugin $pluginClassName! Injecting native JVM implementation.")
            nativeIntercept
        } else {
            val isPluginTrusted = forceBypassSecurity || isTrusted(jarToLoad)
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

            // Proactively scan for any Android XML preferences and populate schema registry
            scanAllXmlPreferences(jarToLoad, finalInternalName)

            if (finalInternalName == "CineStream") {
                try {
                    val registryClass = classLoader.loadClass("com.megix.ProviderRegistry")
                    val instanceField = registryClass.getField("INSTANCE")
                    val registryInstance = instanceField.get(null)
                    val getBuiltInProvidersMethod = registryClass.getMethod("getBuiltInProviders")
                    val providers = getBuiltInProvidersMethod.invoke(registryInstance) as List<*>

                    for (provider in providers) {
                        val getKeyMethod = provider!!.javaClass.getMethod("getKey")
                        val key = getKeyMethod.invoke(provider) as String

                        com.lagradost.common.storage.PluginSettingsSchemaRegistry.register(
                            pluginPrefName = "CineStream_",
                            key = key,
                            type = "String",
                            defaultValue = "false",
                            isGlobal = false,
                        )
                    }
                    AppLogger.i("CineStream: Proactively registered ${providers.size} sub-providers in settings registry.")
                } catch (e: Exception) {
                    AppLogger.e("CineStream: Failed to proactively register sub-providers", e)
                }
            }

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
                        defaultValue = -1,
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
                        key = "disabled_providers",
                        type = "StringSet",
                        defaultValue = emptySet<String>(),
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

            instance
        }

        pluginInstance.filename = jarFile.absolutePath
        // store plugin instance for later unloading
        plugins[jarFile.absolutePath] = pluginInstance

        // Backfill sourcePlugin for any provider/extractor registered during constructor init
        // when pluginInstance.filename was not yet assigned
        try {
            synchronized(com.lagradost.cloudstream3.APIHolder.allProviders) {
                com.lagradost.cloudstream3.APIHolder.allProviders.forEach { provider ->
                    if (provider.sourcePlugin == null && provider.sourcePlugin != "built-in") {
                        provider.sourcePlugin = jarFile.absolutePath
                    }
                }
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
            }
        } catch (t: Throwable) {
            AppLogger.i("Failed to backfill sourcePlugin for ${jarFile.name}: ${t.message}")
        }

        return pluginInstance
    }

    private fun getTrustedList(): MutableList<String> {
        val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
        val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_prefs")
        val json = prefs.get("trusted_plugins", "[]")
        return try {
            mapper.readValue(json, object : com.fasterxml.jackson.core.type.TypeReference<MutableList<String>>() {})
        } catch (e: Exception) {
            mutableListOf()
        }
    }

    private fun isTrusted(jarFile: File): Boolean {
        val name = jarFile.nameWithoutExtension.removeSuffix("-jvm")
        return getTrustedList().contains(name)
    }

    private fun addTrusted(jarFile: File) {
        val name = jarFile.nameWithoutExtension.removeSuffix("-jvm")
        val trusted = getTrustedList()
        if (!trusted.contains(name)) {
            trusted.add(name)
            val mapper = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper()
            val prefs = java.util.prefs.Preferences.userRoot().node("cloudstream_desktop_prefs")
            prefs.put("trusted_plugins", mapper.writeValueAsString(trusted))
        }
    }

    fun loadAndInit(jarFile: File, fallbackPluginClassName: String? = null, forceBypassSecurity: Boolean = false): BasePlugin {
        val pluginInstance = loadJar(jarFile, fallbackPluginClassName, forceBypassSecurity)
        initializePlugin(pluginInstance)
        return pluginInstance
    }

    fun initializePlugin(pluginInstance: BasePlugin) {
        if (pluginInstance is Plugin) {
            pluginInstance.load(DesktopContextProvider.context)
        } else {
            pluginInstance.load()
        }
    }

    fun unloadPlugin(absolutePath: String) {
        val normPath = File(absolutePath).absolutePath
        val canonicalPath = try { File(absolutePath).canonicalPath } catch (_: Throwable) { normPath }
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
        } catch (t: Throwable) {
            AppLogger.i("Failed to remove plugin mappings for $absolutePath: ${t.message}")
        }

        try {
            synchronized(com.lagradost.cloudstream3.utils.extractorApis) {
                com.lagradost.cloudstream3.utils.extractorApis.removeIf { pathsToRemove.contains(it.sourcePlugin) }
            }
        } catch (t: Throwable) {
            // ignore
        }

        try {
            com.lagradost.cloudstream3.actions.VideoClickActionHolder.allVideoClickActions.removeIf { pathsToRemove.contains(it.sourcePlugin) }
        } catch (t: Throwable) {
            // ignore
        }

        // Remove from tracked plugins across all possible path keys
        pathsToRemove.forEach { plugins.remove(it) }
    }

    fun isPluginLoaded(absolutePath: String): Boolean = plugins.containsKey(absolutePath)

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
                        AppLogger.i("Rescan: failed ${jar.name}: ${e.message}")
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
                val xmlEntries = zip.entries().toList().filter { it.name.startsWith("res/xml/") && it.name.endsWith(".xml") }
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

                                    when (element.tagName) {
                                        "CheckBoxPreference", "SwitchPreference", "SwitchPreferenceCompat" -> {
                                            type = "Boolean"
                                            defValue = defValueStr.equals("true", ignoreCase = true)
                                        }
                                        "EditTextPreference", "ListPreference" -> {
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
        } catch (e: Exception) {
            AppLogger.e("Failed to parse plugin preferences", e)
        }
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
}

abstract class VerifiedRepoMixIn {
    @com.fasterxml.jackson.annotation.JsonCreator
    constructor(
        @com.fasterxml.jackson.annotation.JsonProperty("url") url: String?,
        @com.fasterxml.jackson.annotation.JsonProperty("verified") verified: Boolean?,
    )
}
