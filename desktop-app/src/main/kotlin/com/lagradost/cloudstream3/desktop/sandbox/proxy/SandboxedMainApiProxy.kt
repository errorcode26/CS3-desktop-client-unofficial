package com.lagradost.cloudstream3.desktop.sandbox.proxy

import com.fasterxml.jackson.core.type.TypeReference
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageData
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.desktop.sandbox.host.SandboxedPluginHost
import com.lagradost.cloudstream3.desktop.sandbox.protocol.SandboxedProviderMetadata
import com.lagradost.cloudstream3.desktop.sandbox.protocol.TypedHomePageResponse
import com.lagradost.cloudstream3.desktop.sandbox.protocol.TypedItem
import com.lagradost.cloudstream3.desktop.sandbox.protocol.WorkerProtocolSerializer
import org.slf4j.LoggerFactory

/**
 * High-performance proxy living in the main Compose process representing
 * an untrusted third-party provider running inside the low-privilege AppContainer worker.
 */
class SandboxedMainApiProxy(
    val metadata: SandboxedProviderMetadata,
    private val host: SandboxedPluginHost,
) : MainAPI() {

    private val logger = LoggerFactory.getLogger(SandboxedMainApiProxy::class.java)

    init {
        this.name = metadata.name
        this.mainUrl = metadata.mainUrl
        this.lang = metadata.lang
        this.sourcePlugin = metadata.sourcePlugin
    }

    override val supportedTypes: Set<TvType> = metadata.supportedTypes.mapNotNull { typeStr ->
        try {
            TvType.valueOf(typeStr)
        } catch (_: Throwable) {
            null
        }
    }.toSet().ifEmpty { setOf(TvType.Movie, TvType.TvSeries) }

    override val hasMainPage: Boolean = metadata.hasMainPage
    override val hasQuickSearch: Boolean = metadata.hasQuickSearch

    override val mainPage: List<MainPageData> = metadata.mainPage.map {
        MainPageData(name = it.name, data = it.data, horizontalImages = it.horizontalImages)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        return try {
            val json = host.search(this.name, query) ?: return emptyList()
            val typedItems = WorkerProtocolSerializer.mapper.readValue(
                json,
                object : TypeReference<List<TypedItem>>() {}
            )
            typedItems.mapNotNull { item ->
                try {
                    val clazz = Class.forName(item.className)
                    WorkerProtocolSerializer.mapper.treeToValue(item.json, clazz) as? SearchResponse
                } catch (e: Throwable) {
                    try {
                        WorkerProtocolSerializer.mapper.treeToValue(item.json, SearchResponse::class.java)
                    } catch (_: Throwable) {
                        logger.warn("Failed to deserialize SearchResponse of type ${item.className}: ${e.message}")
                        null
                    }
                }
            }
        } catch (e: Throwable) {
            logger.error("Sandboxed search failed for provider '$name'", e)
            emptyList()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        return try {
            val json = host.load(this.name, url) ?: return null
            val typed = WorkerProtocolSerializer.mapper.readValue(json, TypedItem::class.java)
            val clazz = Class.forName(typed.className)
            WorkerProtocolSerializer.mapper.treeToValue(typed.json, clazz) as? LoadResponse
        } catch (e: Throwable) {
            logger.error("Sandboxed load failed for provider '$name' at URL '$url'", e)
            null
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        return try {
            val json = host.getMainPage(
                providerName = this.name,
                page = page,
                requestName = request.name,
                requestData = request.data,
                horizontalImages = request.horizontalImages
            ) ?: return null
            val typedResp = WorkerProtocolSerializer.mapper.readValue(json, TypedHomePageResponse::class.java)
            val lists = typedResp.items.map { typedList ->
                val searchResponses = typedList.list.mapNotNull { item ->
                    try {
                        val clazz = Class.forName(item.className)
                        WorkerProtocolSerializer.mapper.treeToValue(item.json, clazz) as? SearchResponse
                    } catch (_: Throwable) {
                        try {
                            WorkerProtocolSerializer.mapper.treeToValue(item.json, SearchResponse::class.java)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
                HomePageList(
                    name = typedList.name,
                    list = searchResponses,
                    isHorizontalImages = typedList.isHorizontalImages
                )
            }
            newHomePageResponse(lists, hasNext = typedResp.hasNext)
        } catch (e: Throwable) {
            logger.error("Sandboxed getMainPage failed for provider '$name'", e)
            null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        return try {
            val streamedLinkUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
            val streamedSubUrls = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

            val json = host.loadLinks(
                providerName = this.name,
                data = data,
                isCasting = isCasting,
                subtitleCallback = { sub ->
                    if (streamedSubUrls.add(sub.url)) {
                        subtitleCallback(sub)
                    }
                },
                callback = { link ->
                    if (streamedLinkUrls.add(link.url)) {
                        callback(link)
                    }
                }
            )

            if (json == null) {
                return streamedLinkUrls.isNotEmpty()
            }

            val payload = WorkerProtocolSerializer.mapper.readTree(json)

            val linksNode = payload.get("links")
            if (linksNode != null && linksNode.isArray) {
                for (elem in linksNode) {
                    try {
                        val link = WorkerProtocolSerializer.mapper.treeToValue(elem, ExtractorLink::class.java)
                        if (link != null && streamedLinkUrls.add(link.url)) {
                            callback(link)
                        }
                    } catch (e: Throwable) {
                        logger.warn("Failed to deserialize ExtractorLink: ${e.message}")
                    }
                }
            }

            val subsNode = payload.get("subs")
            if (subsNode != null && subsNode.isArray) {
                for (elem in subsNode) {
                    try {
                        val sub = WorkerProtocolSerializer.mapper.treeToValue(elem, SubtitleFile::class.java)
                        if (sub != null && streamedSubUrls.add(sub.url)) {
                            subtitleCallback(sub)
                        }
                    } catch (e: Throwable) {
                        logger.warn("Failed to deserialize SubtitleFile: ${e.message}")
                    }
                }
            }

            streamedLinkUrls.isNotEmpty() || (linksNode != null && !linksNode.isEmpty)
        } catch (e: Throwable) {
            logger.error("Sandboxed loadLinks failed for provider '$name'", e)
            false
        }
    }
}
