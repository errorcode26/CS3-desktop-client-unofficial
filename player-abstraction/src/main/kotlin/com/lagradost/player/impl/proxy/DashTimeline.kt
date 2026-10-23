package com.lagradost.player.impl.proxy

import org.w3c.dom.Element
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/**
 * What a live DASH manifest says about its segments: for every representation the address pattern of its media segments
 * and the newest segment that exists (by number or by time). [DashProxy] uses it to hold the player's request for a segment
 * the CDN has not published yet until the manifest lists it, instead of asking the CDN too early.
 *
 * Supported: SegmentTemplate with `$Number$` or `$Time$` (also `%0Nd` widths, `$RepresentationID$`, `$Bandwidth$`), with a
 * SegmentTimeline (the last listed segment) or with a fixed `duration` (the newest one follows from availabilityStartTime and the
 * clock). Anything else (SegmentList, SegmentBase, open ended timelines) gives no track and the proxy passes the request through.
 */
class DashTimeline private constructor(val tracks: List<Track>) {

    class Track(
        val id: String,
        private val pattern: Regex,
        /** true: the number in the address is a time (`$Time$`), false: a sequence number */
        val byTime: Boolean,
        /** the newest listed segment (number or start time); for duration templates computed from the clock, see [newest] */
        private val lastListed: Long,
        /** duration templates: newest = startNumber + (now - availabilityStart) / duration - 1 */
        private val clock: Clock?,
        /** typical length of a segment, ms */
        val segmentMs: Long,
    ) {
        class Clock(val startNumber: Long, val availabilityStartMs: Long, val durationMs: Double)

        fun valueOf(rest: String): Long? = pattern.find(rest)?.groupValues?.get(1)?.toLongOrNull()

        /** The newest segment that is available at [serverNowMs] */
        fun newest(serverNowMs: Long): Long = if (clock == null) {
            lastListed
        } else {
            clock.startNumber + ((serverNowMs - clock.availabilityStartMs) / clock.durationMs).toLong() - 1
        }
    }

    /** The track whose segment address [rest] is, with the number or time in it */
    fun match(rest: String): Pair<Track, Long>? {
        for (t in tracks) {
            val v = t.valueOf(rest) ?: continue
            return t to v
        }
        return null
    }

    companion object {
        private val TOKEN = Regex("""\$(Number|Time|RepresentationID|Bandwidth)(%0(\d+)d)?\$""")

        fun parse(xml: String): DashTimeline? = runCatching {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = false
                runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            }
            val doc = factory.newDocumentBuilder().parse(org.xml.sax.InputSource(StringReader(xml)))
            val mpd = doc.documentElement
            val ast = mpd.getAttribute("availabilityStartTime").takeIf { it.isNotBlank() }?.let { runCatching { java.time.OffsetDateTime.parse(it).toInstant().toEpochMilli() }.getOrNull() ?: runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrNull() }
            val tracks = ArrayList<Track>()
            for (period in mpd.children("Period")) {
                val periodStartMs = isoDurationMs(period.getAttribute("start")) ?: 0L
                for (set in period.children("AdaptationSet")) {
                    val setTemplate = set.children("SegmentTemplate").firstOrNull()
                    for (rep in set.children("Representation")) {
                        val repTemplate = rep.children("SegmentTemplate").firstOrNull()
                        fun attr(name: String): String? = repTemplate?.getAttribute(name)?.takeIf { it.isNotBlank() } ?: setTemplate?.getAttribute(name)?.takeIf { it.isNotBlank() }
                        val media = attr("media") ?: continue
                        val timescale = attr("timescale")?.toLongOrNull()?.takeIf { it > 0 } ?: 1L
                        val startNumber = attr("startNumber")?.toLongOrNull() ?: 1L
                        val timeline = (repTemplate?.children("SegmentTimeline")?.firstOrNull() ?: setTemplate?.children("SegmentTimeline")?.firstOrNull())
                        val byTime = media.contains("\$Time")
                        if (!byTime && !media.contains("\$Number")) continue
                        val id = rep.getAttribute("id")
                        val pattern = patternOf(media, id, rep.getAttribute("bandwidth")) ?: continue
                        if (timeline != null) {
                            var time = 0L
                            var number = startNumber
                            var lastTime = -1L
                            var lastNumber = startNumber - 1
                            var durations = 0L
                            var count = 0L
                            var openEnded = false
                            for (s in timeline.children("S")) {
                                s.getAttribute("t").toLongOrNull()?.let { time = it }
                                val d = s.getAttribute("d").toLongOrNull() ?: continue
                                val r = s.getAttribute("r").toLongOrNull() ?: 0L
                                if (r < 0) {
                                    openEnded = true
                                    break
                                }
                                val n = r + 1
                                lastTime = time + (n - 1) * d
                                lastNumber = number + n - 1
                                time += n * d
                                number += n
                                durations += n * d
                                count += n
                            }
                            if (openEnded || count == 0L) continue
                            val segmentMs = durations * 1000 / timescale / count
                            tracks.add(Track(id, pattern, byTime, if (byTime) lastTime else lastNumber, null, segmentMs))
                        } else if (!byTime && ast != null) {
                            val duration = attr("duration")?.toLongOrNull()?.takeIf { it > 0 } ?: continue
                            val durationMs = duration * 1000.0 / timescale
                            tracks.add(Track(id, pattern, false, Long.MAX_VALUE, Track.Clock(startNumber, ast + periodStartMs, durationMs), durationMs.toLong()))
                        }
                    }
                }
            }
            if (tracks.isEmpty()) null else DashTimeline(tracks)
        }.getOrNull()

        /** The media address as a pattern that finds the number (or time) at the end of a request path */
        private fun patternOf(address: String, id: String, bandwidth: String): Regex? {
            val media = address.removePrefix("./")
            val sb = StringBuilder("(?:^|/)")
            var at = 0
            var groups = 0
            for (m in TOKEN.findAll(media)) {
                sb.append(Regex.escape(media.substring(at, m.range.first).replace("$$", "$")))
                when (m.groupValues[1]) {
                    "Number", "Time" -> {
                        sb.append("(\\d+)")
                        groups++
                    }
                    "RepresentationID" -> sb.append(Regex.escape(id))
                    "Bandwidth" -> sb.append(Regex.escape(bandwidth))
                }
                at = m.range.last + 1
            }
            sb.append(Regex.escape(media.substring(at).replace("$$", "$")))
            sb.append("$")
            if (groups != 1) return null
            return Regex(sb.toString())
        }

        /** ISO 8601 duration of the manifest (PT2.300S, PT1H2M) in ms */
        fun isoDurationMs(text: String?): Long? {
            if (text.isNullOrBlank()) return null
            return runCatching { java.time.Duration.parse(text).toMillis() }.getOrNull()
        }

        private fun Element.children(tag: String): List<Element> {
            val out = ArrayList<Element>()
            var n = firstChild
            while (n != null) {
                if (n is Element && (n.tagName == tag || n.tagName.endsWith(":$tag"))) out.add(n)
                n = n.nextSibling
            }
            return out
        }
    }
}
