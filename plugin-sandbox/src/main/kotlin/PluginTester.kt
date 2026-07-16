import com.lagradost.cloudstream3.*
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.zip.ZipFile

fun main(args: Array<String>) {
    println("--- CloudStream Plugin Sandbox ---")
    if (args.isEmpty()) {
        println("⚠️ Please provide a path to a .cs3 file.")
        println("Example: ./gradlew :sandbox:run --args=\"plugin-sandbox/test_plugins/my_plugin.cs3\"")
        println("Place your plugins in the 'plugin-sandbox/test_plugins/' folder!")
        return
    }

    val pluginFile = File(args[0])
    if (!pluginFile.exists()) {
        println("Error: File not found: ${pluginFile.absolutePath}")
        return
    }

    val dexFile = File(pluginFile.parentFile, pluginFile.nameWithoutExtension + ".dex")
    ZipFile(pluginFile).use { zip ->
        val dexEntry = zip.getEntry("classes.dex")
        if (dexEntry != null) {
            zip.getInputStream(dexEntry).use { input ->
                java.nio.file.Files.copy(input, dexFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
    val jarFile = File(pluginFile.parentFile, pluginFile.nameWithoutExtension + "-jvm.jar")
    if (!jarFile.exists()) {
        try {
            com.googlecode.dex2jar.tools.Dex2jarCmd().doMain("-f", dexFile.absolutePath, "-o", jarFile.absolutePath)
        } catch (e: Exception) {
            com.googlecode.dex2jar.tools.Dex2jarCmd.main("-f", dexFile.absolutePath, "-o", jarFile.absolutePath)
        }
    }

    if (jarFile.exists()) {
        val finalReport = StringBuilder()
        finalReport.append(StubRadar.scanForMissingStubs(jarFile))

        finalReport.append("\n--- Deep Execution Testing ---\n")

        // --- 1. Initialize Runtime Environment (Mirroring Main.kt) ---
        println("Initializing Desktop Environment...")

        // Proxy Server
        com.lagradost.player.impl.proxy.LocalStreamProxy.start()

        // Security Providers
        java.security.Security.insertProviderAt(org.bouncycastle.jce.provider.BouncyCastleProvider(), 1)

        // DataStore
        com.lagradost.common.storage.DesktopDataStore.init()

        // Global NiceHttp clients
        com.lagradost.cloudstream3.desktop.network.NetworkConfig.updateGlobalNetworkClients()

        // Patch Jackson mapper for dex2jar Kotlin reflection bugs
        val mapper = com.lagradost.cloudstream3.mapper
        val fallbackModule = object : com.fasterxml.jackson.databind.module.SimpleModule() {
            override fun setupModule(context: SetupContext) {
                super.setupModule(context)
                context.addDeserializers(object : com.fasterxml.jackson.databind.deser.Deserializers.Base() {
                    override fun findBeanDeserializer(
                        type: com.fasterxml.jackson.databind.JavaType,
                        config: com.fasterxml.jackson.databind.DeserializationConfig,
                        beanDesc: com.fasterxml.jackson.databind.BeanDescription,
                    ): com.fasterxml.jackson.databind.JsonDeserializer<*>? {
                        if (type.rawClass.name.contains("VerifiedRepo")) {
                            return object : com.fasterxml.jackson.databind.JsonDeserializer<Any>() {
                                override fun deserialize(
                                    p: com.fasterxml.jackson.core.JsonParser,
                                    ctxt: com.fasterxml.jackson.databind.DeserializationContext,
                                ): Any? {
                                    val node = p.codec.readTree<com.fasterxml.jackson.databind.JsonNode>(p)
                                    val name = node.get("name")?.asText() ?: ""
                                    val url = node.get("url")?.asText() ?: ""
                                    try {
                                        val clazz = type.rawClass
                                        var instance: Any? = null
                                        val c = clazz.constructors.find { it.parameterCount == 2 } ?: clazz.constructors.firstOrNull()
                                        if (c != null) {
                                            c.isAccessible = true
                                            instance = try {
                                                c.newInstance(name, url)
                                            } catch (e: Exception) {
                                                try {
                                                    c.newInstance(url, name)
                                                } catch (e2: Exception) {
                                                    null
                                                }
                                            }
                                        }
                                        if (instance == null) {
                                            println("FATAL: Could not instantiate VerifiedRepo via direct mapping!")
                                        }
                                        return instance
                                    } catch (e: Exception) {
                                        return null
                                    }
                                }
                            }
                        }
                        return null
                    }
                })
            }
        }
        mapper.registerModule(fallbackModule)

        // Initialize WebViewResolver
        com.lagradost.cloudstream3.network.WebViewResolver.webViewHandler = { request, callback ->
            com.lagradost.cloudstream3.desktop.network.CdpResolverImpl.resolve(request, callback)
        }

        try {
            // --- 2. Load Plugin Safely (Using ExtensionLoader to trigger StaticVerifier) ---
            println("Loading and Verifying Plugin via ExtensionLoader...")
            val pluginClassName = com.lagradost.runtime.loader.ExtensionLoader.loadAndInit(jarFile)

            if (pluginClassName != null) {
                finalReport.append("Found and Verified plugin class: $pluginClassName\n")

                finalReport.append("\n--- Deep Runtime Test ---\n")
                val providers = com.lagradost.cloudstream3.APIHolder.allProviders
                finalReport.append("Plugin registered ${providers.size} providers.\n")

                var passed = 0
                var failed = 0

                providers.forEach { provider ->
                    finalReport.append("\nTesting Provider: ${provider.name}\n")
                    try {
                        kotlinx.coroutines.runBlocking {
                            finalReport.append(" -> Calling getMainPage()\n")
                            provider.getMainPage(1, com.lagradost.cloudstream3.MainPageRequest("", "", false))
                            finalReport.append(" -> Calling search(\"test\")\n")
                            provider.search("test")
                        }
                        finalReport.append(" ✅ ${provider.name} ran successfully without missing Android APIs.\n")
                        passed++
                    } catch (e: Throwable) {
                        finalReport.append(" ❌ ${provider.name} crashed!\n")
                        finalReport.append("    Reason: ${e.javaClass.name}: ${e.message}\n")
                        failed++
                    }
                }

                finalReport.append("\n--- Final Score ---\n")
                finalReport.append("Passed: $passed / ${providers.size}\n")
                if (failed > 0) finalReport.append("Failed: $failed. Check the crash reasons for missing Android stubs!\n")
            } else {
                finalReport.append("Could not load plugin! StaticVerifier may have rejected it, or manifest.json is invalid.\n")
            }
        } catch (e: Exception) {
            finalReport.append("Execution failed: ${e.javaClass.name}: ${e.message}\n")
            e.printStackTrace()
        }

        println(finalReport.toString())

        val reportDir = File(pluginFile.parentFile, "reports")
        if (!reportDir.exists()) reportDir.mkdirs()
        val reportFile = File(reportDir, "${pluginFile.nameWithoutExtension}_report.txt")
        reportFile.writeText(finalReport.toString())
        println("\nReport saved to: ${reportFile.absolutePath}")
    } else {
        println("Failed to load extension into a JAR.")
    }
}
