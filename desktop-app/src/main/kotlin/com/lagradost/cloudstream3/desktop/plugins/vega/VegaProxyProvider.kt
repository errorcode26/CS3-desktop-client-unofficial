package com.lagradost.cloudstream3.desktop.plugins.vega

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.SubtitleFile
import java.net.URLEncoder
import com.fasterxml.jackson.module.kotlin.readValue

class VegaProxyProvider(val providerName: String) : MainAPI() {
    override var name = providerName
    override var mainUrl = "http://127.0.0.1"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime)
    
    override val hasMainPage = true

    override val mainPage: List<MainPageData> by lazy {
        val url = "$baseUrl/getCatalog?provider=${providerName}"
        val list = VegaBridgeManager.makeLocalGet<List<Map<String, String>>>(url)
        list?.map { map ->
            MainPageData(
                name = map["title"] ?: "",
                data = map["filter"] ?: "",
                horizontalImages = false
            )
        } ?: emptyList()
    }
    
    // Dynamic getter for the current port
    private val baseUrl: String
        get() = "http://127.0.0.1:${VegaBridgeManager.port}"

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$baseUrl/getSearchPosts?provider=${providerName}&query=${URLEncoder.encode(query, "UTF-8")}"
        val response = VegaBridgeManager.makeLocalGet<List<VegaPost>>(url) ?: emptyList()
        
        return response.map { post ->
            newMovieSearchResponse(
                name = post.title,
                url = post.link,
                type = TvType.Movie
            ) {
                this.posterUrl = post.image
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = "$baseUrl/getPosts?provider=${providerName}&filter=${request.data}&page=$page"
        val response = VegaBridgeManager.makeLocalGet<List<VegaPost>>(url) ?: emptyList()
        
        val items = response.map { post ->
            newMovieSearchResponse(
                name = post.title,
                url = post.link,
                type = TvType.Movie
            ) {
                this.posterUrl = post.image
            }
        }
        
        return newHomePageResponse(request.name, items)
    }

    override suspend fun load(url: String): LoadResponse? {
        val encodedLink = URLEncoder.encode(url, "UTF-8")
        val metaUrl = "$baseUrl/getMeta?provider=${providerName}&link=$encodedLink"
        val info = VegaBridgeManager.makeLocalGet<VegaInfo>(metaUrl) ?: return null
        
        val episodes = mutableListOf<Episode>()
        var seasonNum = 1
        
        info.linkList?.forEach { linkItem ->
            if (!linkItem.episodesLink.isNullOrBlank()) {
                val epUrl = "$baseUrl/getEpisodes?provider=${providerName}&url=${URLEncoder.encode(linkItem.episodesLink, "UTF-8")}"
                val epList = VegaBridgeManager.makeLocalGet<List<VegaEpisodeLink>>(epUrl) ?: emptyList()
                
                epList.forEachIndexed { index, ep ->
                    val escapedLink = VegaBridgeManager.mapper.writeValueAsString(ep.link)
                    val linkData = """{"link":$escapedLink,"type":"series"}"""
                    episodes.add(
                        newEpisode(data = linkData) {
                            this.name = ep.title
                            this.season = seasonNum
                            this.episode = index + 1
                        }
                    )
                }
            } else if (!linkItem.directLinks.isNullOrEmpty()) {
                linkItem.directLinks.forEachIndexed { index, ep ->
                    val escapedLink = VegaBridgeManager.mapper.writeValueAsString(ep.link)
                    val linkData = """{"link":$escapedLink,"type":"series"}"""
                    episodes.add(
                        newEpisode(data = linkData) {
                            this.name = ep.title
                            this.season = seasonNum
                            this.episode = index + 1
                        }
                    )
                }
            }
            seasonNum++
        }
        
        
        if (episodes.isEmpty() && info.type?.lowercase() == "movie") {
            val linkData = """{"link":"$url","type":"movie"}"""
            return newMovieLoadResponse(info.title, url, TvType.Movie, linkData) {
                this.posterUrl = info.image
                this.plot = info.synopsis
            }
        }
        
        return newTvSeriesLoadResponse(info.title, url, TvType.TvSeries, episodes) {
            this.posterUrl = info.image
            this.plot = info.synopsis
        }
    }

    private data class VegaLinkData(val link: String, val type: String)

    @Suppress("DEPRECATION", "DEPRECATION_ERROR")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        var link = data
        var type = "series"
        
        if (data.startsWith("{")) {
            try {
                val parsed = VegaBridgeManager.mapper.readValue<VegaLinkData>(data)
                link = parsed.link
                type = parsed.type
            } catch (e: Exception) {}
        }
        
        val encodedLink = URLEncoder.encode(link, "UTF-8")
        val streamUrl = "$baseUrl/getStream?provider=${providerName}&link=$encodedLink&type=$type"
        val streams = VegaBridgeManager.makeLocalGet<List<VegaStream>>(streamUrl, readTimeoutMs = 60000) ?: emptyList()
        
        synchronized(VegaStreamCache) {
            if (System.currentTimeMillis() - VegaStreamCache.lastLoadTime > 2000) {
                VegaStreamCache.seenUrls.clear()
            }
            VegaStreamCache.lastLoadTime = System.currentTimeMillis()
        }

        streams.forEach { stream ->
            val linkUrl = stream.link ?: ""
            val isDuplicate = synchronized(VegaStreamCache) { !VegaStreamCache.seenUrls.add(linkUrl) }
            if (linkUrl.isEmpty() || isDuplicate) return@forEach

            val qualityMap = mapOf(
                "360" to Qualities.P360,
                "480" to Qualities.P480,
                "720" to Qualities.P720,
                "1080" to Qualities.P1080
            )
            val matchedQuality = qualityMap.entries.find { 
                stream.title?.contains(it.key) == true || stream.server?.contains(it.key) == true || linkUrl.contains(it.key)
            }?.value ?: Qualities.Unknown
            
            if (linkUrl.isNotEmpty()) {
                val isM3u8 = stream.type?.contains("m3u8", ignoreCase = true) == true || linkUrl.contains(".m3u8")
                val streamName = stream.server ?: stream.title ?: name
                
                // These are already-resolved direct video file CDN/storage URLs.
                // Do NOT run loadExtractor on them — it will try to scrape the file as HTML and OOM.
                val isDirectVideo = linkUrl.contains("googleusercontent.com")
                    || linkUrl.contains("cloudflarestorage.com")
                    || linkUrl.contains("pixeldrain.dev/api/file/")
                    || linkUrl.contains("pixeldrain.com/api/file/")
                    || linkUrl.contains("fsl.gigabytes.icu")
                    || linkUrl.contains("fsl-buckets.work")
                    || linkUrl.contains("cdn.fsl")
                    || linkUrl.contains(".mkv?token=")
                    || linkUrl.contains(".mp4?token=")
                    || linkUrl.endsWith(".mkv")
                    || linkUrl.endsWith(".mp4")
                    || linkUrl.endsWith(".m3u8")
                
                if (isDirectVideo) {
                    // Direct CDN links — pass straight to player with any headers the provider returned
                    val referer = stream.headers?.entries
                        ?.firstOrNull { it.key.equals("Referer", ignoreCase = true) }?.value ?: ""
                    val extractorLink = com.lagradost.cloudstream3.utils.ExtractorLink(
                        source = name,
                        name = streamName,
                        url = linkUrl,
                        referer = referer,
                        quality = matchedQuality.value,
                        isM3u8 = isM3u8,
                        headers = stream.headers?.filter { !it.key.equals("Referer", ignoreCase = true) } ?: emptyMap()
                    )
                    callback(extractorLink)
                } else {
                    // For hosting page links, try CloudStream's native extractors first
                    val handled = com.lagradost.cloudstream3.utils.loadExtractor(linkUrl, linkUrl, subtitleCallback, callback)
                    if (!handled) {
                        val extractorLink = com.lagradost.cloudstream3.utils.ExtractorLink(
                            source = name,
                            name = streamName,
                            url = linkUrl,
                            referer = linkUrl,
                            quality = matchedQuality.value,
                            isM3u8 = isM3u8
                        )
                        callback(extractorLink)
                    }
                }
            }
        }
        
        return true
    }
}

// Data models for parsing JSON from sidecar Node process
data class VegaPost(
    val title: String,
    val link: String,
    val image: String?
)

data class VegaInfo(
    val title: String,
    val image: String?,
    val synopsis: String?,
    val type: String?,
    val linkList: List<VegaMetaLink>?
)

data class VegaMetaLink(
    val title: String,
    val episodesLink: String?,
    val directLinks: List<VegaEpisodeLink>?
)

data class VegaEpisodeLink(
    val title: String,
    val link: String
)

data class VegaStream(
    val title: String?,
    val server: String?,
    val link: String?,
    val type: String?,
    val headers: Map<String, String>? = null
)

object VegaStreamCache {
    val seenUrls = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    var lastLoadTime = 0L
}
