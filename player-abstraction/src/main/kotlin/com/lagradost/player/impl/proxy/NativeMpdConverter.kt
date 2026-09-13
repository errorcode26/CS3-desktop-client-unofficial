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
        private const val MAX_LIVE_SEGMENTS = 5000
        private const val DEFAULT_LIVE_WINDOW_SECONDS = 180.0

        // Stateful tracker for live HLS MEDIA-SEQUENCE.
        // Maps "$mpdUrl|$repId" -> Pair<List of Segment Timestamps, Last Sequence Number>
        // Synchronized LRU map capped at 200 entries to prevent memory leaks during long playback sessions.
        private val sequenceState = java.util.Collections.synchronizedMap(
            object : java.util.LinkedHashMap<String, Pair<List<Long>, Int>>(100, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, Pair<List<Long>, Int>>): Boolean {
                    return size > 200
                }
            }
        )

        /**
         * Clears cached sequence states (useful when playback stops or in unit tests).
         */
        fun clearSequenceState(stateKey: String? = null) {
            if (stateKey != null) {
                sequenceState.remove(stateKey)
            } else {
                sequenceState.clear()
            }
        }
    }

    private data class MpdSegment(
        val url: String,
        val duration: Double,
        val number: Long,
        val time: Long,
        val timescale: Long,
        val periodIndex: Int,
        val initUrl: String?,
    )

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
        tracksListener: ProxyTracksListener? = null,
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
        val periodList = if (periods.length > 0) {
            (0 until periods.length).map { periods.item(it) as Element }
        } else {
            listOf(mpd)
        }

        val audioTracks = mutableListOf<String>()
        val lazyAudioTracks = mutableListOf<ProxyTrack>()
        val seenAudioReps = mutableSetOf<String>()

        // 1. Find Audio Tracks across all periods
        for (period in periodList) {
            val adaptationSets = period.getElementsByTagName("AdaptationSet")
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
                        if (seenAudioReps.contains(repId)) continue
                        seenAudioReps.add(repId)

                        val encodedMpdUrl = Base64.getUrlEncoder().withoutPadding().encodeToString(mpdUrl.toByteArray(Charsets.UTF_8))
                        var mediaUrl = "http://127.0.0.1:$port/proxy?s=$sessionId&u=$encodedMpdUrl&action=dash&rep=$repId"
                        if (clearKey != null) mediaUrl += "&ck=$clearKey"

                        val isDefault = audioTracks.isEmpty()
                        sb.appendLine("""#EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio",LANGUAGE="$lang",NAME="${lang.uppercase()}",DEFAULT=${if (isDefault) "YES" else "NO"},AUTOSELECT=YES,URI="$mediaUrl"""")
                        audioTracks.add(repId)
                        lazyAudioTracks.add(ProxyTrack(mediaUrl, lang.uppercase(), lang))
                    }
                }
            }
        }

        if (audioTracks.isNotEmpty()) sb.appendLine()

        val lazyVideoTracks = mutableListOf<ProxyTrack>()
        val seenVideoReps = mutableSetOf<String>()

        // 2. Find Video Tracks across all periods
        for (period in periodList) {
            val adaptationSets = period.getElementsByTagName("AdaptationSet")
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
                        if (seenVideoReps.contains(repId)) continue
                        seenVideoReps.add(repId)

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
        }

        tracksListener?.onTracksDiscovered(lazyAudioTracks, emptyList(), lazyVideoTracks)

        return sb.toString()
    }

    private fun getFirstDirectChild(parent: Element?, tagName: String): Element? {
        if (parent == null) return null
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            if (child is Element && child.tagName == tagName) return child
        }
        return null
    }

    private fun formatUrlTemplate(template: String, repId: String, bandwidth: String, number: Long, time: Long): String {
        var res = template
            .replace("\$RepresentationID\$", repId)
            .replace("\$Bandwidth\$", bandwidth)
            .replace("\$Number\$", number.toString())
            .replace("\$Time\$", time.toString())

        res = res.replace(Regex("\\\$Number%0(\\d+)d\\\$")) { match ->
            val w = match.groupValues[1].toIntOrNull() ?: 1
            number.toString().padStart(w, '0')
        }
        res = res.replace(Regex("\\\$Time%0(\\d+)d\\\$")) { match ->
            val w = match.groupValues[1].toIntOrNull() ?: 1
            time.toString().padStart(w, '0')
        }
        res = res.replace(Regex("\\\$Bandwidth%0(\\d+)d\\\$")) { match ->
            val w = match.groupValues[1].toIntOrNull() ?: 1
            bandwidth.padStart(w, '0')
        }
        return res
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
        val availabilityStartTimeMs = if (isLive) parseIsoInstant(mpd.getAttribute("availabilityStartTime")) else null

        // Parse timeShiftBufferDepth for live DVR windowing (default to 180s)
        val liveWindowSeconds = if (isLive) {
            parseMpdDuration(mpd.getAttribute("timeShiftBufferDepth")) ?: DEFAULT_LIVE_WINDOW_SECONDS
        } else {
            DEFAULT_LIVE_WINDOW_SECONDS
        }

        val periods = mpd.getElementsByTagName("Period")
        val periodList = if (periods.length > 0) {
            (0 until periods.length).map { periods.item(it) as Element }
        } else {
            listOf(mpd)
        }

        val sb = StringBuilder()
        sb.appendLine("#EXTM3U")
        sb.appendLine("#EXT-X-VERSION:$HLS_VERSION")
        sb.appendLine("#EXT-X-INDEPENDENT-SEGMENTS")

        val useDecryption = !clearKey.isNullOrBlank()
        val allSegments = mutableListOf<MpdSegment>()
        var periodIndex = 0
        var firstPeriodMediaTemplate = ""

        for (period in periodList) {
            var targetRep: Element? = null
            var adaptSet: Element? = null

            val reps = period.getElementsByTagName("Representation")
            for (i in 0 until reps.length) {
                val rep = reps.item(i) as Element
                if (rep.getAttribute("id") == repId) {
                    targetRep = rep
                    adaptSet = rep.parentNode as? Element
                    break
                }
            }

            if (targetRep == null) continue

            val template = getFirstDirectChild(targetRep, "SegmentTemplate")
                ?: getFirstDirectChild(adaptSet, "SegmentTemplate")
                ?: getFirstDirectChild(period, "SegmentTemplate")

            val segmentList = getFirstDirectChild(targetRep, "SegmentList")
                ?: getFirstDirectChild(adaptSet, "SegmentList")
                ?: getFirstDirectChild(period, "SegmentList")

            val bandwidth = targetRep.getAttribute("bandwidth") ?: adaptSet?.getAttribute("bandwidth") ?: "0"
            val currentPeriodIndex = periodIndex++

            if (template != null) {
                val timescale = template.getAttribute("timescale")?.toLongOrNull() ?: 1L
                val initAttr = template.getAttribute("initialization")
                    ?.replace("\$RepresentationID\$", repId)
                    ?.replace("\$Bandwidth\$", bandwidth)
                val mediaAttr = template.getAttribute("media")
                    ?.replace("\$RepresentationID\$", repId)
                    ?.replace("\$Bandwidth\$", bandwidth) ?: ""

                if (firstPeriodMediaTemplate.isBlank()) {
                    firstPeriodMediaTemplate = mediaAttr
                }

                val initUrl = initAttr?.let { resolveUrl(baseUrl, it) }
                val timeline = template.getElementsByTagName("SegmentTimeline").item(0) as? Element

                if (timeline != null) {
                    val sElements = timeline.getElementsByTagName("S")
                    var time = 0L
                    val startSegNum = template.getAttribute("startNumber")?.toIntOrNull() ?: 1
                    var segNum = startSegNum

                    for (i in 0 until sElements.length) {
                        val s = sElements.item(i) as Element
                        val t = s.getAttribute("t")?.toLongOrNull()
                        val d = s.getAttribute("d")?.toLongOrNull() ?: continue
                        val r = s.getAttribute("r")?.toIntOrNull() ?: 0

                        if (t != null) time = t
                        val repeat = if (r < 0) (if (isLive) MAX_LIVE_SEGMENTS else 500) else r

                        for (j in 0..repeat) {
                            val duration = d.toDouble() / timescale.toDouble()
                            val segUrl = formatUrlTemplate(mediaAttr, repId, bandwidth, segNum.toLong(), time)
                            val absoluteSegUrl = resolveUrl(baseUrl, segUrl)

                            val proxySeg = if (useDecryption) {
                                encodeProxyUrl(port, sessionId, absoluteSegUrl, "decrypt", clearKey, initUrl)
                            } else {
                                encodeProxyUrl(port, sessionId, absoluteSegUrl, "stream")
                            }

                            allSegments.add(
                                MpdSegment(
                                    url = proxySeg,
                                    duration = duration,
                                    number = segNum.toLong(),
                                    time = time,
                                    timescale = timescale,
                                    periodIndex = currentPeriodIndex,
                                    initUrl = initUrl,
                                )
                            )

                            time += d
                            segNum++
                            if (allSegments.size >= MAX_LIVE_SEGMENTS) break
                        }
                        if (allSegments.size >= MAX_LIVE_SEGMENTS) break
                    }
                } else {
                    val d = template.getAttribute("duration")?.toLongOrNull() ?: 1L
                    val duration = d.toDouble() / timescale.toDouble()

                    if (isLive) {
                        val availStartTime = parseIsoInstant(mpd.getAttribute("availabilityStartTime"))
                        val nowMs = System.currentTimeMillis()
                        val startNum = template.getAttribute("startNumber")?.toIntOrNull() ?: 1

                        val currentLiveSegIndex = if (availStartTime != null && duration > 0.0) {
                            val elapsedSeconds = (nowMs - availStartTime) / 1000.0
                            startNum + (elapsedSeconds / duration).toLong()
                        } else {
                            startNum.toLong()
                        }

                        val windowSize = if (duration > 0.0) {
                            ceil(liveWindowSeconds / duration).toInt().coerceAtLeast(6)
                        } else {
                            8
                        }
                        val endSeg = (currentLiveSegIndex - 2).coerceAtLeast(startNum.toLong())
                        val startSeg = (endSeg - windowSize + 1).coerceAtLeast(startNum.toLong())

                        for (segNum in startSeg..endSeg) {
                            val time = (segNum - startNum) * d
                            val segUrl = formatUrlTemplate(mediaAttr, repId, bandwidth, segNum, time)
                            val absoluteSegUrl = resolveUrl(baseUrl, segUrl)

                            val proxySeg = if (useDecryption) {
                                encodeProxyUrl(port, sessionId, absoluteSegUrl, "decrypt", clearKey, initUrl)
                            } else {
                                encodeProxyUrl(port, sessionId, absoluteSegUrl, "stream")
                            }

                            allSegments.add(
                                MpdSegment(
                                    url = proxySeg,
                                    duration = duration,
                                    number = segNum,
                                    time = time,
                                    timescale = timescale,
                                    periodIndex = currentPeriodIndex,
                                    initUrl = initUrl,
                                )
                            )
                        }
                    } else {
                        val startNum = template.getAttribute("startNumber")?.toIntOrNull() ?: 1
                        val mpdDurRaw = mpd.getAttribute("mediaPresentationDuration")
                        val periodDurRaw = period.getAttribute("duration")

                        val totalSecs = parseMpdDuration(mpdDurRaw)
                            ?: parseMpdDuration(periodDurRaw)
                            ?: 0.0

                        val numSegments = if (totalSecs > 0.0 && duration > 0.0) {
                            ceil(totalSecs / duration).toInt().coerceAtLeast(1)
                        } else {
                            500
                        }

                        var time = 0L
                        for (i in 0 until numSegments) {
                            val segNum = startNum + i
                            val segUrl = formatUrlTemplate(mediaAttr, repId, bandwidth, segNum.toLong(), time)
                            val absoluteSegUrl = resolveUrl(baseUrl, segUrl)

                            val proxySeg = if (useDecryption) {
                                encodeProxyUrl(port, sessionId, absoluteSegUrl, "decrypt", clearKey, initUrl)
                            } else {
                                encodeProxyUrl(port, sessionId, absoluteSegUrl, "stream")
                            }

                            allSegments.add(
                                MpdSegment(
                                    url = proxySeg,
                                    duration = duration,
                                    number = segNum.toLong(),
                                    time = time,
                                    timescale = timescale,
                                    periodIndex = currentPeriodIndex,
                                    initUrl = initUrl,
                                )
                            )
                            time += d
                        }
                    }
                }
            } else if (segmentList != null) {
                val timescale = segmentList.getAttribute("timescale")?.toLongOrNull() ?: 1L
                val durationAttr = segmentList.getAttribute("duration")?.toLongOrNull() ?: 1L
                val duration = durationAttr.toDouble() / timescale.toDouble()

                val initEl = getFirstDirectChild(segmentList, "Initialization")
                val initUrl = initEl?.getAttribute("sourceURL")?.let { resolveUrl(baseUrl, it) }

                val segUrls = segmentList.getElementsByTagName("SegmentURL")
                for (k in 0 until segUrls.length) {
                    val segUrlEl = segUrls.item(k) as Element
                    val mediaRel = segUrlEl.getAttribute("media") ?: continue
                    val absUrl = resolveUrl(baseUrl, mediaRel)

                    val proxySeg = if (useDecryption) {
                        encodeProxyUrl(port, sessionId, absUrl, "decrypt", clearKey, initUrl)
                    } else {
                        encodeProxyUrl(port, sessionId, absUrl, "stream")
                    }

                    allSegments.add(
                        MpdSegment(
                            url = proxySeg,
                            duration = duration,
                            number = (k + 1).toLong(),
                            time = k * durationAttr,
                            timescale = timescale,
                            periodIndex = currentPeriodIndex,
                            initUrl = initUrl,
                        )
                    )
                }
            }
        }

        // Apply rolling DVR windowing for live streams
        val segments = if (isLive && allSegments.size > 1) {
            var totalDuration = 0.0
            val liveSegments = mutableListOf<MpdSegment>()
            for (seg in allSegments.reversed()) {
                liveSegments.add(0, seg)
                totalDuration += seg.duration
                if (totalDuration >= liveWindowSeconds || liveSegments.size >= MAX_LIVE_SEGMENTS) {
                    break
                }
            }
            liveSegments
        } else {
            allSegments
        }

        val maxDuration = segments.maxOfOrNull { it.duration } ?: DEFAULT_TARGET_DURATION.toDouble()
        val targetDuration = ceil(maxDuration).toInt().coerceAtLeast(DEFAULT_TARGET_DURATION)
        sb.appendLine("#EXT-X-TARGETDURATION:$targetDuration")

        // Strictly monotonic MEDIA-SEQUENCE calculation for live streams
        val mediaSequence = if (isLive && segments.isNotEmpty()) {
            val stateKey = "$mpdUrl|$repId"
            val currentTimes = segments.map { it.time }
            val prevState = sequenceState[stateKey]

            val newSeq = if (prevState == null) {
                val initialSeq = if (firstPeriodMediaTemplate.contains("\$Number\$")) {
                    segments.first().number.toInt()
                } else {
                    0
                }
                initialSeq
            } else {
                val oldTimes = prevState.first
                val oldSeq = prevState.second
                val newFirstTime = currentTimes.first()
                val indexInOld = oldTimes.indexOf(newFirstTime)

                if (indexInOld > 0) {
                    // Window slid forward by exactly indexInOld segments
                    oldSeq + indexInOld
                } else if (indexInOld == 0) {
                    // Window did not slide
                    oldSeq
                } else {
                    // Missed refreshes or timeline gap: estimate sequence advance
                    val timeDiff = newFirstTime - oldTimes.first()
                    if (timeDiff > 0) {
                        val firstSeg = segments.first()
                        val modeDur = segments.map { Math.round(it.duration * firstSeg.timescale) }
                            .groupBy { it }.maxByOrNull { it.value.size }?.key?.coerceAtLeast(1L) ?: 1L
                        val estimatedJump = Math.round(timeDiff.toDouble() / modeDur.toDouble()).toInt().coerceAtLeast(1)
                        oldSeq + estimatedJump
                    } else {
                        oldSeq + 1
                    }
                }
            }
            sequenceState[stateKey] = Pair(currentTimes, newSeq)
            newSeq
        } else {
            0
        }

        sb.appendLine("#EXT-X-MEDIA-SEQUENCE:$mediaSequence")

        if (isLive) {
            sb.appendLine("#EXT-X-START:TIME-OFFSET=-20.0,PRECISE=NO")
        } else {
            sb.appendLine("#EXT-X-PLAYLIST-TYPE:VOD")
        }

        // Emit initial EXT-X-MAP if present
        val firstInitUrl = segments.firstOrNull()?.initUrl
        if (firstInitUrl != null) {
            val proxyInitUrl = if (useDecryption) {
                encodeProxyUrl(port, sessionId, firstInitUrl, "init_decrypt")
            } else {
                encodeProxyUrl(port, sessionId, firstInitUrl, "stream")
            }
            sb.appendLine("""#EXT-X-MAP:URI="$proxyInitUrl"""")
        }

        var lastPeriodIndex = segments.firstOrNull()?.periodIndex ?: 0
        var lastInitUrlEmitted = firstInitUrl

        for ((index, segment) in segments.withIndex()) {
            if (index > 0 && segment.periodIndex != lastPeriodIndex) {
                sb.appendLine("#EXT-X-DISCONTINUITY")
                if (segment.initUrl != null && segment.initUrl != lastInitUrlEmitted) {
                    val proxyInitUrl = if (useDecryption) {
                        encodeProxyUrl(port, sessionId, segment.initUrl, "init_decrypt")
                    } else {
                        encodeProxyUrl(port, sessionId, segment.initUrl, "stream")
                    }
                    sb.appendLine("""#EXT-X-MAP:URI="$proxyInitUrl"""")
                    lastInitUrlEmitted = segment.initUrl
                }
                lastPeriodIndex = segment.periodIndex
            }

            if (isLive) {
                val segTimescale = segment.timescale.coerceAtLeast(1L)
                val timeSec = segment.time.toDouble() / segTimescale.toDouble()
                try {
                    val epochMs = if (availabilityStartTimeMs != null) {
                        availabilityStartTimeMs + (timeSec * 1000).toLong()
                    } else {
                        (timeSec * 1000).toLong()
                    }
                    val instant = java.time.Instant.ofEpochMilli(epochMs)
                    sb.appendLine("#EXT-X-PROGRAM-DATE-TIME:$instant")
                } catch (_: Exception) {
                    sb.appendLine("#EXT-X-PROGRAM-DATE-TIME:2024-01-01T00:00:00.000Z")
                }
            }

            sb.appendLine("#EXTINF:${String.format(java.util.Locale.US, "%.3f", segment.duration)},")
            sb.appendLine(segment.url)
        }

        if (!isLive) {
            sb.appendLine("#EXT-X-ENDLIST")
        }

        return sb.toString()
    }

    private fun parseMpdDuration(raw: String?): Double? {
        if (raw.isNullOrBlank() || !raw.startsWith("PT", ignoreCase = true)) return null
        return try {
            val hoursMatch = Regex("(\\d+(?:\\.\\d+)?)H").find(raw)
            val minsMatch = Regex("(\\d+(?:\\.\\d+)?)M").find(raw)
            val secsMatch = Regex("(\\d+(?:\\.\\d+)?)S").find(raw)
            val hours = hoursMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val mins = minsMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val secs = secsMatch?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
            val total = hours * 3600.0 + mins * 60.0 + secs
            if (total > 0.0) total else null
        } catch (_: Exception) {
            null
        }
    }

    private fun parseIsoInstant(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.format.DateTimeFormatter.ISO_DATE_TIME.parse(raw, java.time.Instant::from).toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun parseXml(xml: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        val builder = factory.newDocumentBuilder()
        return builder.parse(ByteArrayInputStream(xml.trim().toByteArray(Charsets.UTF_8)))
    }
}
