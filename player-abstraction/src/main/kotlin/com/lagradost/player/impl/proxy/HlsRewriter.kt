package com.lagradost.player.impl.proxy

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.net.URI

/**
 * Pure parsing and rewriting engine for HLS (M3U8) manifests.
 * Handles variant discovery, URL resolution, and tag transformation.
 */
object HlsRewriter {

    private val BW_REGEX = Regex("""BANDWIDTH=(\d+)""")
    private val RES_REGEX = Regex("""RESOLUTION=(\d+)x(\d+)""")
    private val URI_REGEX = Regex("""URI="([^"]+)"""")
    private val NAME_REGEX = Regex("""NAME="([^"]+)"""")
    private val LANG_REGEX = Regex("""LANGUAGE="([^"]+)"""")

    /**
     * Resolves relative URLs against a base URL, preserving query parameters where appropriate.
     */
    fun resolveUrl(base: String, uri: String): String {
        val rawResolved = if (uri.startsWith("http://", ignoreCase = true) || uri.startsWith("https://", ignoreCase = true)) {
            uri
        } else {
            val baseUrl = base.toHttpUrlOrNull()
            if (baseUrl != null) {
                baseUrl.resolve(uri)?.toString()
            } else {
                try {
                    URI(base).resolve(uri).toString()
                } catch (e: Exception) {
                    if (base.contains("/")) {
                        base.substringBeforeLast('/') + "/" + uri
                    } else {
                        uri
                    }
                }
            }
        } ?: uri

        val baseQuery = base.substringAfter('?', "")
        if (baseQuery.isEmpty()) {
            return rawResolved
        }

        // Only inherit query parameters if hosts match (or if one couldn't be parsed as an HttpUrl)
        val baseHost = base.toHttpUrlOrNull()?.host
        val resolvedHost = rawResolved.toHttpUrlOrNull()?.host
        if (baseHost != null && resolvedHost != null && !baseHost.equals(resolvedHost, ignoreCase = true)) {
            return rawResolved
        }

        val baseParams = baseQuery.split("&").filter { it.isNotEmpty() }
        val existingQuery = rawResolved.substringAfter('?', "")
        val existingKeys = if (existingQuery.isEmpty()) emptySet() else existingQuery.split("&").map { it.substringBefore('=') }.toSet()

        val missingParams = baseParams.filter { param ->
            val key = param.substringBefore('=')
            key !in existingKeys
        }

        if (missingParams.isEmpty()) {
            return rawResolved
        }

        val separator = if (rawResolved.contains("?")) "&" else "?"
        return rawResolved + separator + missingParams.joinToString("&")
    }

    /**
     * Rewrites an M3U8 manifest (Master or Media) to route chunks/variants through [LocalStreamProxy].
     */
    fun rewriteM3u8(
        content: String,
        baseUrl: String,
        sessionId: String,
        tracksListener: ProxyTracksListener? = LocalStreamProxyState,
    ): String {
        val lines = content.split("\n")
        val isMaster = content.contains("#EXT-X-STREAM-INF")

        if (isMaster) {
            val lazyAudios = mutableListOf<ProxyTrack>()
            val lazySubs = mutableListOf<ProxyTrack>()
            val lazyVideoTracks = mutableListOf<ProxyTrack>()

            // Pass 1: Find best video variant and default audio variant
            var maxScore = -1L
            var bestVariantUrl: String? = null
            var currentVariantLine: String? = null
            var bestAudioUrl: String? = null
            var firstAudioUrl: String? = null

            for (line in lines) {
                val trim = line.trim()
                if (trim.startsWith("#EXT-X-STREAM-INF")) {
                    currentVariantLine = trim
                } else if (currentVariantLine != null && !trim.startsWith("#")) {
                    val bwMatch = BW_REGEX.find(currentVariantLine)
                    val resMatch = RES_REGEX.find(currentVariantLine)
                    val bw = bwMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val width = resMatch?.groupValues?.get(1)?.toIntOrNull() ?: 0
                    val height = resMatch?.groupValues?.get(2)?.toIntOrNull() ?: 0
                    val effectiveRes = if (height > 0) height else width
                    val score = effectiveRes.toLong() * 1_000_000L + bw
                    if (score > maxScore) {
                        maxScore = score
                        bestVariantUrl = resolveUrl(baseUrl, trim)
                    }
                    currentVariantLine = null
                } else if (trim.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                    val uriMatch = URI_REGEX.find(trim)
                    if (uriMatch != null) {
                        val uri = uriMatch.groupValues[1]
                        if (firstAudioUrl == null) firstAudioUrl = uri
                        if (trim.contains("DEFAULT=YES", ignoreCase = true)) {
                            bestAudioUrl = uri
                        }
                    }
                    // Track the default audio group-id for Pass 2 filtering
                }
            }

            if (bestAudioUrl == null) bestAudioUrl = firstAudioUrl

            // Extract the GROUP-ID of the best audio so we can match it in Pass 2
            val bestAudioGroupId: String? = if (bestAudioUrl != null) {
                lines.firstOrNull { l ->
                    l.trim().startsWith("#EXT-X-MEDIA:TYPE=AUDIO") &&
                        URI_REGEX.find(l)?.groupValues?.get(1) == bestAudioUrl
                }?.let { Regex("""GROUP-ID="([^"]+)"""").find(it)?.groupValues?.get(1) }
            } else {
                null
            }

            // Pass 2: Reconstruct playlist keeping only best video variant, DEFAULT audio variant, and ALL subtitles as lazy
            val rewritten = buildString {
                var pendingVariantLine: String? = null

                for (line in lines) {
                    val trim = line.trim()
                    if (trim.isEmpty()) continue

                    if (trim.startsWith("#EXT-X-MEDIA:TYPE=SUBTITLES")) {
                        // Strip ALL subtitles — FFmpeg aggressively probes every single one at startup!
                        val name = NAME_REGEX.find(trim)?.groupValues?.get(1) ?: "Unknown Sub"
                        val lang = LANG_REGEX.find(trim)?.groupValues?.get(1) ?: "unk"
                        val uriMatch = URI_REGEX.find(trim)
                        if (uriMatch != null) {
                            val absolute = resolveUrl(baseUrl, uriMatch.groupValues[1])
                            val proxied = LocalStreamProxy.buildProxyUrl(sessionId, absolute) + "&flatvtt=true"
                            lazySubs.add(ProxyTrack(proxied, name, lang))
                        }
                        continue
                    }

                    if (trim.startsWith("#EXT-X-MEDIA:TYPE=AUDIO")) {
                        val name = NAME_REGEX.find(trim)?.groupValues?.get(1) ?: "Unknown Audio"
                        val lang = LANG_REGEX.find(trim)?.groupValues?.get(1) ?: "unk"
                        val uriMatch = URI_REGEX.find(trim)

                        if (uriMatch != null) {
                            val uri = uriMatch.groupValues[1]
                            val absolute = resolveUrl(baseUrl, uri)
                            val proxied = LocalStreamProxy.buildProxyUrl(sessionId, absolute)
                            lazyAudios.add(ProxyTrack(proxied, name, lang))
                        }
                        continue
                    }

                    if (trim.startsWith("#")) {
                        if (trim.contains("URI=\"")) {
                            val uriRegex = Regex("""URI="([^"]+)"""")
                            val newLine = trim.replace(uriRegex) { result ->
                                val uri = result.groupValues[1]
                                val absolute = resolveUrl(baseUrl, uri)
                                val proxied = LocalStreamProxy.buildProxyUrl(sessionId, absolute)
                                "URI=\"$proxied\""
                            }
                            appendLine(newLine)
                        } else {
                            if (trim.startsWith("#EXT-X-STREAM-INF")) {
                                pendingVariantLine = trim
                            } else {
                                appendLine(trim)
                            }
                        }
                    } else {
                        // URL line
                        if (pendingVariantLine != null) {
                            val absolute = resolveUrl(baseUrl, trim)
                            val proxied = LocalStreamProxy.buildProxyUrl(sessionId, absolute)

                            // Keep ALL variants in the proxy M3U8 so MPV can natively and seamlessly switch them!
                            val cleanedVariantLine = pendingVariantLine.replace(Regex(""",?AUDIO="[^"]+""""), "")
                            appendLine(cleanedVariantLine)
                            appendLine(proxied)

                            // Expose to Compose UI so we can use `hls-bitrate` property
                            val bwMatch = BW_REGEX.find(pendingVariantLine)
                            val resMatch = RES_REGEX.find(pendingVariantLine)
                            val width = resMatch?.groupValues?.get(1)?.toIntOrNull()
                            val height = resMatch?.groupValues?.get(2)?.toIntOrNull()
                            val res = when {
                                height != null -> "${height}p"
                                width != null -> "${width}p"
                                else -> null
                            }
                            val bw = bwMatch?.groupValues?.get(1)?.toIntOrNull()
                            val bwLabel = if (bw != null) " ${bw / 1000}kbps" else ""
                            val name = when {
                                res != null -> "$res$bwLabel"
                                bw != null -> "${bw / 1000}kbps"
                                else -> "Variant"
                            }
                            lazyVideoTracks.add(ProxyTrack(proxied, name, "eng", bw))
                        } else {
                            val absolute = resolveUrl(baseUrl, trim)
                            val proxied = LocalStreamProxy.buildProxyUrl(sessionId, absolute, "stream")
                            appendLine(proxied)
                        }
                        pendingVariantLine = null
                    }
                }
            }

            if (bestVariantUrl != null) {
                LocalStreamProxy.prefetchM3u8(sessionId, resolveUrl(baseUrl, bestVariantUrl))
            }

            tracksListener?.onTracksDiscovered(lazyAudios, lazySubs, lazyVideoTracks)

            return rewritten
        }

        // Process media (non-master) HLS playlists containing TS chunks.
        val rewritten = buildString {
            for (line in lines) {
                val trim = line.trim()
                if (trim.isEmpty()) continue

                if (trim.startsWith("#")) {
                    if (trim.contains("URI=\"")) {
                        val uriRegex = Regex("""URI="([^"]+)"""")
                        val newLine = trim.replace(uriRegex) { result ->
                            val uri = result.groupValues[1]
                            val absolute = resolveUrl(baseUrl, uri)
                            val proxied = LocalStreamProxy.buildProxyUrl(sessionId, absolute)
                            "URI=\"$proxied\""
                        }
                        appendLine(newLine)
                    } else {
                        appendLine(trim)
                    }
                } else {
                    val absolute = resolveUrl(baseUrl, trim)
                    // Proxy video segments through OkHttp to benefit from TLS connection pooling
                    // and keep-alive, which FFmpeg natively struggles with on HTTPS streams.
                    val proxied = LocalStreamProxy.buildProxyUrl(sessionId, absolute)
                    appendLine(proxied)
                }
            }

            val hasEndList = content.contains("#EXT-X-ENDLIST")
            val isLiveStream = content.contains("#EXT-X-PLAYLIST-TYPE:EVENT") ||
                (content.contains("#EXT-X-MEDIA-SEQUENCE") && !hasEndList && !content.contains("#EXT-X-PLAYLIST-TYPE:VOD"))

            // Guarantee #EXT-X-ENDLIST for VOD episodes/movies so MPV knows the exact duration and enables full seeking
            if (!hasEndList && !isLiveStream) {
                appendLine("#EXT-X-ENDLIST")
            }
        }
        return rewritten
    }
}
