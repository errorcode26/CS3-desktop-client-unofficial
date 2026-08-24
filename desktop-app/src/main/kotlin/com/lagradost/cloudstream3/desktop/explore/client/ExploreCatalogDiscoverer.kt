package com.lagradost.cloudstream3.desktop.explore.client

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.desktop.explore.models.ManifestCatalogDescriptor
import com.lagradost.cloudstream3.desktop.stremio.ManagedStremioAddon
import com.lagradost.cloudstream3.desktop.stremio.StremioTransport
import com.lagradost.common.logging.AppLogger
import java.util.concurrent.ConcurrentHashMap

object ExploreCatalogDiscoverer {
    private const val TAG = "ExploreCatalogDiscoverer"
    private val mapper = jacksonObjectMapper()
    private val manifestCache = ConcurrentHashMap<String, List<ManifestCatalogDescriptor>>()

    suspend fun getCatalogsForAddon(addon: ManagedStremioAddon): List<ManifestCatalogDescriptor> {
        val cached = manifestCache[addon.manifestUrl]
        if (cached != null) return cached

        return try {
            val responseText = app.get(addon.manifestUrl, timeout = 6000L).text
            val root = mapper.readTree(responseText)
            val catalogsNode = root["catalogs"]
            val list = mutableListOf<ManifestCatalogDescriptor>()

            if (catalogsNode != null && catalogsNode.isArray) {
                for (cat in catalogsNode) {
                    val type = cat["type"]?.asText() ?: continue
                    val id = cat["id"]?.asText() ?: continue
                    val name = cat["name"]?.asText() ?: "$type - $id"

                    val genres = mutableListOf<String>()
                    var supportsSearch = false

                    val extraNode = cat["extra"]
                    if (extraNode != null && extraNode.isArray) {
                        for (ex in extraNode) {
                            val exName = ex["name"]?.asText()
                            if (exName.equals("genre", ignoreCase = true)) {
                                val opts = ex["options"]
                                if (opts != null && opts.isArray) {
                                    for (opt in opts) {
                                        genres.add(opt.asText())
                                    }
                                }
                            } else if (exName.equals("search", ignoreCase = true)) {
                                supportsSearch = true
                            }
                        }
                    }

                    list.add(
                        ManifestCatalogDescriptor(
                            addonName = addon.name,
                            addonBaseUrl = StremioTransport.getBaseUrl(addon.manifestUrl),
                            type = type,
                            id = id,
                            name = name,
                            genres = genres,
                            supportsSearch = supportsSearch,
                        )
                    )
                }
            }

            manifestCache[addon.manifestUrl] = list
            list
        } catch (e: Exception) {
            AppLogger.w(TAG, "Failed to discover catalogs for ${addon.name}: ${e.message}")
            emptyList()
        }
    }

    fun clearCache() {
        manifestCache.clear()
    }
}
