import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.kotlinModule
import com.lagradost.runtime.dex.DexConverter
import com.lagradost.runtime.loader.PluginBytecodeTransformer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class RemotePluginItem(
    val name: String,
    val internalName: String,
    val downloadUrl: String,
)

fun main(args: Array<String>) {
    println("================================================================")
    println("     CloudStream Ecosystem Bytecode Scanner & Converter         ")
    println("================================================================")

    val client = OkHttpClient.Builder()
        .followRedirects(true)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .build()

    val mapper = ObjectMapper().registerModule(kotlinModule())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    val corpusDir = File("plugin-sandbox/corpus").apply { mkdirs() }
    val reportsDir = File("plugin-sandbox/reports").apply { mkdirs() }

    val filesToProcess = mutableListOf<File>()

    if (args.isEmpty()) {
        println("No arguments provided. Searching for local .cs3 files...")
        val searchDirs = listOf(
            corpusDir,
            File("plugin-sandbox/test_plugins"),
            com.lagradost.common.platform.PlatformPaths.extensionsDir,
            File(System.getProperty("user.home") ?: "", ".cncverse_bridge/plugins"),
        )
        for (dir in searchDirs) {
            val found = dir.listFiles { f -> f.extension == "cs3" || f.extension == "zip" } ?: emptyArray()
            if (found.isNotEmpty()) {
                filesToProcess.addAll(found)
                println("Found ${found.size} plugin(s) in: ${dir.path}")
            }
        }
        if (filesToProcess.isEmpty()) {
            println("⚠️ No plugins found in searched directories.")
            println("Usage options:")
            println("  1. Run MegaRepo scan:   ./gradlew :sandbox:run --args=\"megarepo\"")
            println("  2. Pass a repo URL:     ./gradlew :sandbox:run --args=\"https://raw.githubusercontent.com/.../repo.json\"")
            println("  3. Pass a folder:       ./gradlew :sandbox:run --args=\"path/to/plugins_folder\"")
            return
        }
    } else {
        var arg = args[0].trim()

        val isCatalogMode = arg.equals("all", ignoreCase = true) ||
            arg.equals("catalog", ignoreCase = true) ||
            arg.equals("cache", ignoreCase = true)

        if (isCatalogMode) {
            val pluginItems = mutableMapOf<String, RemotePluginItem>()
            println("Reading ecosystem catalog from local cache...")
            val cacheFile = File(com.lagradost.common.platform.PlatformPaths.extensionsDir, "plugins_cache.json")
            if (cacheFile.exists()) {
                val cacheTree = mapper.readTree(cacheFile)
                cacheTree.fields().forEach { (_, listNode) ->
                    if (listNode.isArray) {
                        listNode.forEach { pNode ->
                            val pUrl = pNode.get("url")?.asText()
                            val pName = pNode.get("name")?.asText() ?: "plugin"
                            val pInternal = pNode.get("internalName")?.asText() ?: pName
                            if (!pUrl.isNullOrBlank()) {
                                pluginItems[pInternal] = RemotePluginItem(pName, pInternal, pUrl)
                            }
                        }
                    }
                }
            }
            println("Discovered ${pluginItems.size} unique plugins in ecosystem catalog.")

            println("\nFound ${pluginItems.size} total unique plugins across the ecosystem.")
            println("Downloading and verifying strictly SEQUENTIALLY to preserve system resources...\n")

            pluginItems.values.forEachIndexed { idx, item ->
                val progress = "[${idx + 1}/${pluginItems.size}]"
                val targetCs3 = File(corpusDir, "${item.internalName}.cs3")

                if (!targetCs3.exists() || targetCs3.length() == 0L) {
                    print("$progress Downloading ${item.internalName}... ")
                    try {
                        val dlReq = Request.Builder().url(item.downloadUrl).build()
                        client.newCall(dlReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { input ->
                                    FileOutputStream(targetCs3).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                println("✓ (${targetCs3.length() / 1024} KB)")
                            } else {
                                println("✗ HTTP ${resp.code}")
                            }
                        }
                    } catch (e: Exception) {
                        println("✗ ${e.message}")
                    }
                } else {
                    println("$progress Using cached: ${item.internalName}.cs3 (${targetCs3.length() / 1024} KB)")
                }

                if (targetCs3.exists() && targetCs3.length() > 0L) {
                    filesToProcess.add(targetCs3)
                }
            }
        } else {
            // 1. Resolve shortcodes (e.g. "megarepo")
            if (arg.matches(Regex("^[a-zA-Z0-9!_-]+$")) && !File(arg).exists()) {
                println("Resolving shortcode: '$arg' via redirect service...")
            try {
                val req = Request.Builder().url("https://cutt.ly/$arg").build()
                noRedirectClient.newCall(req).execute().use { resp ->
                    val loc = resp.header("Location")
                    if (!loc.isNullOrBlank() && !loc.startsWith("https://cutt.ly/404")) {
                        println("Resolved '$arg' -> $loc")
                        arg = loc
                    } else {
                        println("Could not resolve shortcode '$arg'")
                        return
                    }
                }
            } catch (e: Exception) {
                println("Error resolving shortcode: ${e.message}")
                return
            }
        }

        // 2. Remote repository or MegaRepo crawling
        if (arg.startsWith("http://") || arg.startsWith("https://")) {
            val pluginItems = mutableMapOf<String, RemotePluginItem>()
            println("Fetching index from: $arg")

            fun fetchUrl(url: String): String? {
                return try {
                    val req = Request.Builder().url(url).build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string() else null
                    }
                } catch (e: Exception) {
                    println("  Failed to fetch $url: ${e.message}")
                    null
                }
            }

            val rootBody = fetchUrl(arg)
            if (rootBody == null) {
                println("Failed to fetch repository index from $arg")
                return
            }

            val memberRepoUrls = mutableListOf<String>()
            if (rootBody.trimStart().startsWith("[")) {
                // MegaRepo array of repositories
                println("Detected MegaRepo catalog! Parsing member repositories...")
                val arrayNode = mapper.readTree(rootBody)
                arrayNode.forEach { node ->
                    val repoUrl = node.get("url")?.asText()
                    if (!repoUrl.isNullOrBlank()) memberRepoUrls.add(repoUrl)
                }
                println("Found ${memberRepoUrls.size} member repositories in MegaRepo.")
            } else {
                memberRepoUrls.add(arg)
            }

            // Crawl each repository sequentially
            memberRepoUrls.forEachIndexed { rIdx, repoUrl ->
                println("[Repo ${rIdx + 1}/${memberRepoUrls.size}] Crawling: $repoUrl")
                val repoBody = fetchUrl(repoUrl) ?: return@forEachIndexed
                try {
                    val repoJson = mapper.readTree(repoBody)
                    val pluginLists = mutableListOf<String>()

                    val listsNode = repoJson.get("pluginLists")
                    if (listsNode != null && listsNode.isArray) {
                        listsNode.forEach { pluginLists.add(it.asText()) }
                    } else {
                        // Sometimes the repo URL itself directly contains plugins
                        val pluginsNode = repoJson.get("plugins")
                        if (pluginsNode != null && pluginsNode.isArray) {
                            pluginsNode.forEach { pNode ->
                                val pUrl = pNode.get("url")?.asText()
                                val pName = pNode.get("name")?.asText() ?: "plugin"
                                val pInternal = pNode.get("internalName")?.asText() ?: pName
                                if (!pUrl.isNullOrBlank()) {
                                    pluginItems[pInternal] = RemotePluginItem(pName, pInternal, pUrl)
                                }
                            }
                        }
                    }

                    for (listUrl in pluginLists) {
                        val listBody = fetchUrl(listUrl) ?: continue
                        val listJson = mapper.readTree(listBody)
                        if (listJson.isArray) {
                            listJson.forEach { pNode ->
                                val pUrl = pNode.get("url")?.asText()
                                val pName = pNode.get("name")?.asText() ?: "plugin"
                                val pInternal = pNode.get("internalName")?.asText() ?: pName
                                if (!pUrl.isNullOrBlank()) {
                                    pluginItems[pInternal] = RemotePluginItem(pName, pInternal, pUrl)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("  Error parsing repository $repoUrl: ${e.message}")
                }
            }

            println("\nFound ${pluginItems.size} total unique plugins across the ecosystem.")
            println("Downloading and verifying strictly SEQUENTIALLY to preserve system resources...\n")

            // Download each plugin sequentially into corpusDir
            pluginItems.values.forEachIndexed { idx, item ->
                val progress = "[${idx + 1}/${pluginItems.size}]"
                val targetCs3 = File(corpusDir, "${item.internalName}.cs3")

                if (!targetCs3.exists() || targetCs3.length() == 0L) {
                    print("$progress Downloading ${item.internalName}... ")
                    try {
                        val dlReq = Request.Builder().url(item.downloadUrl).build()
                        client.newCall(dlReq).execute().use { resp ->
                            if (resp.isSuccessful) {
                                resp.body?.byteStream()?.use { input ->
                                    FileOutputStream(targetCs3).use { output ->
                                        input.copyTo(output)
                                    }
                                }
                                println("✓ (${targetCs3.length() / 1024} KB)")
                            } else {
                                println("✗ HTTP ${resp.code}")
                            }
                        }
                    } catch (e: Exception) {
                        println("✗ ${e.message}")
                    }
                } else {
                    println("$progress Using cached: ${item.internalName}.cs3 (${targetCs3.length() / 1024} KB)")
                }

                if (targetCs3.exists() && targetCs3.length() > 0L) {
                    filesToProcess.add(targetCs3)
                }
            }
        } else {
            val fileOrDir = File(arg)
            if (fileOrDir.isDirectory) {
                val files = fileOrDir.listFiles { f -> f.extension == "cs3" || f.extension == "zip" } ?: emptyArray()
                filesToProcess.addAll(files)
            } else if (fileOrDir.isFile) {
                filesToProcess.add(fileOrDir)
            } else {
                println("File or directory not found: $arg")
                return
            }
        }
    }
    }

    println("\n================================================================")
    println("Beginning SEQUENTIAL Transpilation & Bytecode Analysis on ${filesToProcess.size} plugin(s)...")
    println("================================================================\n")

    val summaries = mutableListOf<PluginScanSummary>()

    filesToProcess.forEachIndexed { index, cs3File ->
        val progress = "[${index + 1}/${filesToProcess.size}]"
        print("$progress ${cs3File.name}: ")

        val targetJar = File(cs3File.parentFile, cs3File.nameWithoutExtension + "-jvm.jar")

        try {
            // 1. Production Dalvik -> JVM Transpilation (Sequential)
            if (!targetJar.exists() || targetJar.lastModified() < cs3File.lastModified()) {
                val result = DexConverter.convert(cs3File, targetJar)
                if (result.errors.isNotEmpty()) {
                    print("[Dex warnings: ${result.errors.size}] ")
                }
            }

            // 2. Production ASM Bytecode Transformation (Sequential)
            PluginBytecodeTransformer.transform(targetJar)

            // 3. Deep StubRadar Scan (Sequential)
            val summary = StubRadar.scanJar(targetJar, cs3File)
            summaries.add(summary)

            val missingCount = summary.missingApis.size
            if (missingCount == 0) {
                println("✓ 100% Resolved (${summary.androidClasses.size} Android classes)")
            } else {
                println("⚠️ $missingCount Missing API(s) (${summary.androidClasses.size} Android classes, ${summary.xmlTags.size} XML tags)")
            }
        } catch (e: Exception) {
            println("✗ FAILED: ${e.javaClass.simpleName} - ${e.message}")
            summaries.add(
                PluginScanSummary(
                    pluginName = cs3File.name,
                    jarFile = targetJar,
                    androidClasses = emptySet(),
                    xmlTags = emptySet(),
                    missingApis = emptyList(),
                    conversionError = "${e.javaClass.simpleName}: ${e.message}",
                ),
            )
        }

        // Force GC after each plugin to ensure memory never spikes
        System.gc()
    }

    val reportMarkdown = StubRadar.generateEcosystemReport(summaries)
    val reportFile = File(reportsDir, "ecosystem_report.md")
    reportFile.writeText(reportMarkdown)

    println("\n================================================================")
    println("Analysis Complete! Exhaustive report generated at:")
    println("-> ${reportFile.absolutePath}")
    println("================================================================\n")
    println(reportMarkdown)
}
