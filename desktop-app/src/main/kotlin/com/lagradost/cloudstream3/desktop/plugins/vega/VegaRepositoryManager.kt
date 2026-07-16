package com.lagradost.cloudstream3.desktop.plugins.vega

import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.lagradost.common.platform.PlatformPaths
import java.security.MessageDigest

data class VegaRepository(
    val name: String,
    val url: String
)

object VegaRepositoryManager {
    private val mapper = jacksonObjectMapper()
    private val file = File(PlatformPaths.appDataDir, "vega_repos.json")
    
    private val _savedRepositories = MutableStateFlow<List<VegaRepository>>(emptyList())
    val savedRepositories: StateFlow<List<VegaRepository>> = _savedRepositories.asStateFlow()

    init {
        load()
    }

    private fun load() {
        try {
            if (file.exists()) {
                val list: List<VegaRepository> = mapper.readValue(file)
                _savedRepositories.value = list
            } else {
                // Add default vega-org repo if empty
                val defaultRepo = VegaRepository("Vega Official", "https://raw.githubusercontent.com/vega-org/vega-providers/refs/heads/main")
                _savedRepositories.value = listOf(defaultRepo)
                save()
            }
        } catch (e: Exception) {
            println("Failed to load vega_repos.json: ${e.message}")
        }
    }

    private fun save() {
        try {
            mapper.writeValue(file, _savedRepositories.value)
        } catch (e: Exception) {
            println("Failed to save vega_repos.json: ${e.message}")
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    suspend fun addRepository(url: String): Boolean = withContext(Dispatchers.IO) {
        val resolvedUrl = VegaExtensionManager.parseSourceUrl(url) ?: return@withContext false
        
        // Fetch/Download manifest to verify it's a valid Vega repo and cache it
        val success = VegaExtensionManager.installExtension(resolvedUrl)
        if (success) {
            val authorName = resolvedUrl.removePrefix("https://raw.githubusercontent.com/").substringBefore("/")
            val displayName = if (authorName.isNotBlank() && !resolvedUrl.equals(url, ignoreCase = true)) "$authorName Repo" else "Vega Repo"
            
            val list = _savedRepositories.value.toMutableList()
            // Remove if already exists
            list.removeIf { it.url.equals(resolvedUrl, ignoreCase = true) }
            list.add(VegaRepository(displayName, resolvedUrl))
            _savedRepositories.value = list
            save()
            return@withContext true
        }
        return@withContext false
    }

    suspend fun removeRepository(url: String) = withContext(Dispatchers.IO) {
        val list = _savedRepositories.value.toMutableList()
        list.removeIf { it.url.equals(url, ignoreCase = true) }
        _savedRepositories.value = list
        save()

        try {
            val hash = md5(url)
            val manifestFile = File("../vega-bridge/manifests", "$hash.json")
            if (manifestFile.exists()) {
                manifestFile.delete()
                println("Deleted Vega manifest file: ${manifestFile.absolutePath}")
            }
        } catch (e: Exception) {
            println("Failed to delete manifest for repository $url: ${e.message}")
        }
    }
    
    suspend fun syncAll() = withContext(Dispatchers.IO) {
        _savedRepositories.value.forEach { repo ->
            VegaExtensionManager.installExtension(repo.url)
        }
    }
}
