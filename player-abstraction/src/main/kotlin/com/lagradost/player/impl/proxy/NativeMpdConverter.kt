package com.lagradost.player.impl.proxy

import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Base64
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.ceil

/**
 * Native DASH (MPD) to HLS (M3U8) Converter for Cloudstream.
 * Translates MPD manifests into HLS playlists so MPV can play them natively with server-side decryption.
 */
class NativeMpdConverter {

    companion object {
        private const val HLS_VERSION = 6
        private const val DEFAULT_TARGET_DURATION = 6
    }

    private fun getBaseUrl(url: String): String {
        return try {
            val uri = URI(url)
            val path = uri.path
            val lastSlash = path.lastIndexOf('/')
            if (lastSlash > 0) {
                URI(uri.scheme, uri.authority, path.substring(0, lastSlash + 1), null, null).toString()
            } else {
                URI(uri.scheme, uri.authority, "/", null, null).toString()
            }
        } catch (e: Exception) {
            url.substringBeforeLast('/') + "/"
        }
    }

    private fun resolveUrl(baseUrl: String, relativeUrl: String): String {
        if (relativeUrl.startsWith("http")) return relativeUrl
        val base = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return URI(base).resolve(relativeUrl).toString()
    }

    private fun encodeProxyUrl(port: Int, sessionId: String, url: String, action: String, clearKey: String? = null, initUrl: String? = null): String {
        val encodedUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(url.toByteArray(Charsets.UTF_8))
        var proxy = "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedUrl&action=$action"
        if (clearKey != null) {
            val parts = clearKey.split(":")
            if (parts.size == 2) {
                proxy += "&kid=${parts[0]}&k=${parts[1]}"
            } else if (parts.size == 1) {
                proxy += "&k=${parts[0]}"
            }
        }
        if (initUrl != null) {
            val encodedInit = Base64.getUrlEncoder().withoutPadding().encodeToString(initUrl.toByteArray(Charsets.UTF_8))
            proxy += "&init=$encodedInit"
        }
        return proxy
    }

    fun convertMasterPlaylist(
        mpdContent: String,
        port: Int,
        sessionId: String,
        mpdUrl: String,
        clearKey: String? = null,
        tracksListener: ProxyTracksListener? = null
    ): String {
        val doc = parseXml(mpdContent)
        val mpd = doc.documentElement
        val baseUrl = getBaseUrl(mpdUrl)

        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:$HLS_VERSION")
        sb.appendLine("#EXT-X-INDEPENDENT-SEGMENTS")
        sb.appendLine()

        val periods = mpd.getElementsByTagName("Period")
        val firstPeriod = if (periods.length > 0) periods.item(0) as Element else mpd
        val adaptationSets = firstPeriod.getElementsByTagName("AdaptationSet")
        val audioTracks = mutableListOf<String>()

        // 1. Find Audio Tracks
        for (i in 0 until adaptationSets.length) {
            val adapt = adaptationSets.item(i) as Element
            val adaptMime = adapt.getAttribute("mimeType") ?: ""
            val contentType = adapt.getAttribute("contentType") ?: ""

            var isAudio = adaptMime.contains("audio") || contentType.contains("audio")
            if (!isAudio) {
                val reps = adapt.getElementsByTagName("Representation")
                for (j in 0 until reps.length) {
                    val rep = reps.item(j) as Element
                    val repMime = rep.getAttribute("mimeType") ?: ""
                    if (repMime.contains("audio")) {
                        isAudio = true
                        break
                    }
                }
            }

            if (isAudio) {
                val lang = adapt.getAttribute("lang").takeIf { it.isNotBlank() } ?: "und"
                val reps = adapt.getElementsByTagName("Representation")
                for (j in 0 until reps.length) {
                    val rep = reps.item(j) as Element
                    val repId = rep.getAttribute("id")

                    val encodedMpdUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(mpdUrl.toByteArray(Charsets.UTF_8))
                    var mediaUrl = "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedMpdUrl&action=dash&rep=$repId"
                    if (clearKey != null) mediaUrl += "&ck=$clearKey"

                    val isDefault = audioTracks.isEmpty()
                    sb.appendLine("""#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="$lang",NAME="${lang.uppercase()}",DEFAULT=${if (isDefault) "YES" else "NO"},AUTOSELECT=YES,URI="$mediaUrl"""")
                    audioTracks.add(repId)
                }
            }
        }

        if (audioTracks.isNotEmpty()) sb.appendLine()

        val lazyVideoTracks = mutableListOf<ProxyTrack>()

        // 2. Find Video Tracks
        for (i in 0 until adaptationSets.length) {
            val adapt = adaptationSets.item(i) as Element
            val adaptMime = adapt.getAttribute("mimeType") ?: ""
            val contentType = adapt.getAttribute("contentType") ?: ""
            val adaptWidth = adapt.getAttribute("width") ?: ""

            var isVideo = adaptMime.contains("video") || contentType.contains("video") || adaptWidth.isNotBlank()
            if (!isVideo) {
                val reps = adapt.getElementsByTagName("Representation")
                for (j in 0 until reps.length) {
                    val rep = reps.item(j) as Element
                    val repMime = rep.getAttribute("mimeType") ?: ""
                    if (repMime.contains("video") || rep.getAttribute("width").isNotBlank()) {
                        isVideo = true
                        break
                    }
                }
            }

            if (isVideo) {
                val reps = adapt.getElementsByTagName("Representation")
                for (j in 0 until reps.length) {
                    val rep = reps.item(j) as Element
                    val repId = rep.getAttribute("id")
                    val bw = rep.getAttribute("bandwidth") ?: "0"
                    val w = rep.getAttribute("width").takeIf { it.isNotBlank() } ?: adapt.getAttribute("width")
                    val h = rep.getAttribute("height").takeIf { it.isNotBlank() } ?: adapt.getAttribute("height")
                    val codecs = rep.getAttribute("codecs").takeIf { it.isNotBlank() } ?: adapt.getAttribute("codecs")

                    val attrs = mutableListOf("BANDWIDTH=$bw")
                    if (w.isNotBlank() && h.isNotBlank()) attrs.add("RESOLUTION=${w}x$h")
                    if (codecs.isNotBlank()) attrs.add("""CODECS="$codecs"""")
                    if (audioTracks.isNotEmpty()) attrs.add("""AUDIO="audio"""")

                    val name = if (!h.isNullOrBlank()) "${h}p" else "Variant ${bw}kbps"
                    val bwInt = bw.toIntOrNull()

                    val encodedMpdUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(mpdUrl.toByteArray(Charsets.UTF_8))
                    var variantUrl = "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedMpdUrl&action=dash&rep=$repId"
                    if (clearKey != null) variantUrl += "&ck=$clearKey"

                    lazyVideoTracks.add(ProxyTrack(variantUrl, name, "eng", bwInt))

                    sb.appendLine("#EXT-X-STREAM-INF:${attrs.joinToString(",")}")
                    sb.appendLine(variantUrl)
                }
            }
        }
        
        tracksListener?.onTracksDiscovered(emptyList(), emptyList(), lazyVideoTracks)
        
        return sb.toString()
    }

    fun convertMediaPlaylist(
        mpdContent: String,
        repId: String,
        port: Int,
        sessionId: String,
        mpdUrl: String,
        clearKey: String? = null,
    ): String {
        val doc = parseXml(mpdContent)
        val mpd = doc.documentElement
        val baseUrl = getBaseUrl(mpdUrl)
        val isLive = mpd.getAttribute("type") == "dynamic"

        var targetRep: Element? = null
        var adaptSet: Element? = null

        val reps = mpd.getElementsByTagName("Representation")
        for (i in 0 until reps.length) {
            val rep = reps.item(i) as Element
            if (rep.getAttribute("id") == repId) {
                targetRep = rep
                adaptSet = rep.parentNode as? Element
                break
            }
        }

        if (targetRep == null) return ""

        val period = adaptSet?.parentNode as? Element

        fun getFirstDirectChild(parent: Element?, tagName: String): Element? {
            if (parent == null) return null
            val children = parent.childNodes
            for (i in 0 until children.length) {
                val child = children.item(i)
                if (child is Element && child.tagName == tagName) return child
            }
            return null
        }

        val template = getFirstDirectChild(targetRep, "SegmentTemplate")
            ?: getFirstDirectChild(adaptSet, "SegmentTemplate")
            ?: getFirstDirectChild(period, "SegmentTemplate")

        if (template == null) return "" // We only support SegmentTemplate for now

        val timescale = template.getAttribute("timescale")?.toLongOrNull() ?: 1L
        val bandwidth = targetRep.getAttribute("bandwidth") ?: adaptSet?.getAttribute("bandwidth") ?: "0"
        val initAttr = template.getAttribute("initialization")
            ?.replace("\$RepresentationID\$", repId)
            ?.replace("\$Bandwidth\$", bandwidth)
        val mediaAttr = template.getAttribute("media")
            ?.replace("\$RepresentationID\$", repId)
            ?.replace("\$Bandwidth\$", bandwidth) ?: ""

        val initUrl = initAttr?.let { resolveUrl(baseUrl, it) }

        val timeline = template.getElementsByTagName("SegmentTimeline").item(0) as? Element

        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:$HLS_VERSION")
        sb.appendLine("#EXT-X-INDEPENDENT-SEGMENTS")

        val useDecryption = !clearKey.isNullOrBlank()

        if (initUrl != null) {
            val proxyInitUrl = if (useDecryption) {
                encodeProxyUrl(port, sessionId, initUrl, "init_decrypt")
            } else {
                encodeProxyUrl(port, sessionId, initUrl, "stream")
            }
            sb.appendLine("""#EXT-X-MAP:URI="$proxyInitUrl"""")
        }

        if (timeline != null) {
            // SegmentTimeline processing
            val sElements = timeline.getElementsByTagName("S")
            var time = 0L
            val startSegNum = template.getAttribute("startNumber")?.toIntOrNull() ?: 1
            var segNum = startSegNum

            val segments = mutableListOf<String>()
            var maxDuration = 0.0

            for (i in 0 until sElements.length) {
                val s = sElements.item(i) as Element
                val t = s.getAttribute("t")?.toLongOrNull()
                val d = s.getAttribute("d")?.toLongOrNull() ?: continue
                val r = s.getAttribute("r")?.toIntOrNull() ?: 0

                if (t != null) time = t
                val repeat = if (r < 0) 500 else r

                for (j in 0..repeat) {
                    val duration = d.toDouble() / timescale.toDouble()
                    if (duration > maxDuration) maxDuration = duration

                    var segUrl = mediaAttr
                        .replace("\$Number\$", segNum.toString())
                        .replace("\$Time\$", time.toString())

                    segUrl = segUrl.replace(Regex("\\\$Number%0(\\d+)d\\\$")) { match ->
                        val w = match.groupValues[1].toIntOrNull() ?: 1
                        segNum.toString().padStart(w, '0')
                    }
                    val absoluteSegUrl = resolveUrl(baseUrl, segUrl)

                    val proxySeg = if (useDecryption) {
                        encodeProxyUrl(port, sessionId, absoluteSegUrl, "decrypt", clearKey, initUrl)
                    } else {
                        encodeProxyUrl(port, sessionId, absoluteSegUrl, "stream")
                    }

                    segments.add("#EXTINF:${String.format(java.util.Locale.US, "%.3f", duration)},")
                    segments.add(proxySeg)

                    time += d
                    segNum++
                    if (segments.size > 2000) break
                }
                if (segments.size > 2000) break
            }

            sb.appendLine("#EXT-X-TARGETDURATION:${ceil(maxDuration).toInt()}")
            if (isLive) sb.appendLine("#EXT-X-MEDIA-SEQUENCE:$startSegNum")
            segments.forEach { sb.appendLine(it) }
        } else {
            // Duration-based processing
            val d = template.getAttribute("duration")?.toLongOrNull() ?: 1L
            val duration = d.toDouble() / timescale.toDouble()
            sb.appendLine("#EXT-X-TARGETDURATION:${ceil(duration).toInt()}")

            val startNum = template.getAttribute("startNumber")?.toIntOrNull() ?: 1
            if (isLive) sb.appendLine("#EXT-X-MEDIA-SEQUENCE:$startNum")

            // Compute the actual segment count from the total presentation duration so the
            // full video length is exposed to MPV. Prefer mediaPresentationDuration on the
            // root MPD element, then fall back to the Period's own duration attribute.
            // If neither is present (e.g. truly unknown-length live stream), cap at 500.
            val numSegments: Int = if (isLive) {
                500
            } else {
                fun parseMpdDuration(raw: String): Double? {
                    // ISO 8601 duration: PT1H22M30.000S or PT22M30S or PT30S
                    if (!raw.startsWith("PT", ignoreCase = true)) return null
                    val hoursMatch = Regex("(\\d+(?:\\.\\d+)?)H").find(raw)
                    val minsMatch  = Regex("(\\d+(?:\\.\\d+)?)M").find(raw)
                    val secsMatch  = Regex("(\\d+(?:\\.\\d+)?)S").find(raw)
                    val hours = hoursMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
                    val mins  = minsMatch?.groupValues?.get(1)?.toDoubleOrNull()  ?: 0.0
                    val secs  = secsMatch?.groupValues?.get(1)?.toDoubleOrNull()  ?: 0.0
                    val total = hours * 3600.0 + mins * 60.0 + secs
                    return if (total > 0.0) total else null
                }

                val mpdDurRaw    = mpd.getAttribute("mediaPresentationDuration")
                val periodDurRaw = period?.getAttribute("duration")

                val totalSecs = parseMpdDuration(mpdDurRaw)
                    ?: parseMpdDuration(periodDurRaw ?: "")
                    ?: 0.0

                if (totalSecs > 0.0 && duration > 0.0) {
                    // +1 to ensure the final partial segment is included
                    ceil(totalSecs / duration).toInt() + 1
                } else {
                    // Unknown total duration — use a safe generous cap
                    500
                }
            }

            var time = 0L
            for (i in 0 until numSegments) {
                val segNum = startNum + i
                var segUrl = mediaAttr
                    .replace("\$Number\$", segNum.toString())
                    .replace("\$Time\$", time.toString())

                segUrl = segUrl.replace(Regex("\\\$Number%0(\\d+)d\\\$")) { match ->
                    val w = match.groupValues[1].toIntOrNull() ?: 1
                    segNum.toString().padStart(w, '0')
                }
                val absoluteSegUrl = resolveUrl(baseUrl, segUrl)

                val proxySeg = if (useDecryption) {
                    encodeProxyUrl(port, sessionId, absoluteSegUrl, "decrypt", clearKey, initUrl)
                } else {
                    encodeProxyUrl(port, sessionId, absoluteSegUrl, "stream")
                }

                sb.appendLine("#EXTINF:${String.format(java.util.Locale.US, "%.3f", duration)},")
                sb.appendLine(proxySeg)
                time += d
            }
        }

        if (!isLive) sb.appendLine("#EXT-X-ENDLIST")
        return sb.toString()
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
    }
}
