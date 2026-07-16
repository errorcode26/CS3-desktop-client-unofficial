import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.system.exitProcess

@JsonIgnoreProperties(ignoreUnknown = true)
data class RepositoryConfig(
    @JsonProperty("name") val name: String,
    @JsonProperty("pluginLists") val pluginLists: List<String>,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class SitePluginConfig(
    @JsonProperty("name") val name: String,
    @JsonProperty("url") val url: String,
    @JsonProperty("internalName") val internalName: String,
)

fun main(args: Array<String>) {
    println("--- CloudStream Repo Fetcher ---")
    if (args.isEmpty()) {
        println("Please provide a repository URL.")
        println("Example: ./gradlew :sandbox:runFetcher --args=\"https://raw.githubusercontent.com/.../repo.json\"")
        exitProcess(1)
    }

    val repoUrl = args[0]
    val baseOutputDir = File("test_plugins")
    if (!baseOutputDir.exists()) baseOutputDir.mkdirs()

    val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build()
    val mapper = ObjectMapper().registerKotlinModule()

    try {
        println("Fetching repository config from: $repoUrl")
        val request = HttpRequest.newBuilder().uri(URI.create(repoUrl)).GET().build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        if (response.statusCode() != 200) {
            println("❌ Failed to fetch repo. HTTP ${response.statusCode()}")
            exitProcess(1)
        }

        val config = mapper.readValue<RepositoryConfig>(response.body())
        println("Found repository: ${config.name}")
        println("Found ${config.pluginLists.size} plugin lists.")

        val sanitizedRepoName = config.name.replace("[\\\\/:*?\"<>|]".toRegex(), "_")
        val repoOutputDir = File(baseOutputDir, sanitizedRepoName)
        if (!repoOutputDir.exists()) repoOutputDir.mkdirs()

        config.pluginLists.forEach { pluginListUrl ->
            println("\nFetching plugin list: $pluginListUrl")
            val listRequest = HttpRequest.newBuilder().uri(URI.create(pluginListUrl)).GET().build()
            val listResponse = client.send(listRequest, HttpResponse.BodyHandlers.ofString())

            if (listResponse.statusCode() == 200) {
                val plugins = mapper.readValue<List<SitePluginConfig>>(listResponse.body())
                println("Found ${plugins.size} plugins in list. Downloading...")

                plugins.forEach { plugin ->
                    val file = File(repoOutputDir, "${plugin.internalName}.cs3")
                    println("Downloading ${plugin.name} -> ${file.name}")

                    try {
                        val downloadRequest = HttpRequest.newBuilder().uri(URI.create(plugin.url)).GET().build()
                        val downloadResponse = client.send(downloadRequest, HttpResponse.BodyHandlers.ofInputStream())
                        if (downloadResponse.statusCode() == 200) {
                            Files.copy(downloadResponse.body(), file.toPath(), StandardCopyOption.REPLACE_EXISTING)
                            println("   Saved.")
                        } else {
                            println("   Failed HTTP ${downloadResponse.statusCode()}")
                        }
                    } catch (e: Exception) {
                        println("   Failed to download ${plugin.name}: ${e.message}")
                    }
                }
            } else {
                println("Failed to fetch list. HTTP ${listResponse.statusCode()}")
            }
        }

        println("\nFetch complete! You can now run the Sandbox Analyzer on the downloaded plugins.")
    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
        exitProcess(1)
    }
}
