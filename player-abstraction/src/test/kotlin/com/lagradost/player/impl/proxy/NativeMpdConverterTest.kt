package com.lagradost.player.impl.proxy

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NativeMpdConverterTest {

    private val converter = NativeMpdConverter()

    @BeforeEach
    fun setUp() {
        NativeMpdConverter.clearSequenceState()
    }

    private fun extractDecodedProxyUrls(playlist: String): List<String> {
        return playlist.lines()
            .filter { it.startsWith("http://127.0.0.1") }
            .mapNotNull { line ->
                val uParam = Regex("""u=([A-Za-z0-9_-]+)""").find(line)?.groupValues?.get(1)
                uParam?.let { String(Base64.getUrlDecoder().decode(it), Charsets.UTF_8) }
            }
    }

    @Test
    fun testStaticVodManifest() {
        val mpd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static" mediaPresentationDuration="PT18S">
                <Period id="0">
                    <AdaptationSet mimeType="video/mp4" contentType="video">
                        <Representation id="video_1" bandwidth="1000000" width="1280" height="720">
                            <SegmentTemplate timescale="1" initialization="init.mp4" media="seg_${'$'}Number${'$'}.mp4" startNumber="1">
                                <SegmentTimeline>
                                    <S t="0" d="6" r="2" />
                                </SegmentTimeline>
                            </SegmentTemplate>
                        </Representation>
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val playlist = converter.convertMediaPlaylist(
            mpdContent = mpd,
            repId = "video_1",
            port = 8080,
            sessionId = "test-session",
            mpdUrl = "https://example.com/stream/manifest.mpd",
        )

        assertTrue(playlist.contains("#EXT-X-PLAYLIST-TYPE:VOD"), "VOD manifest must have VOD playlist type")
        assertTrue(playlist.contains("#EXT-X-ENDLIST"), "VOD manifest must terminate with EXT-X-ENDLIST")
        assertTrue(playlist.contains("#EXT-X-MEDIA-SEQUENCE:0"), "VOD manifest must have media sequence 0")
        assertFalse(playlist.contains("#EXT-X-START:TIME-OFFSET"), "VOD manifest should not have live start offset")
        assertTrue(playlist.contains("#EXT-X-TARGETDURATION:6"), "Target duration must be at least 6")

        val decodedUrls = extractDecodedProxyUrls(playlist)
        assertEquals(3, decodedUrls.size, "Must contain exactly 3 segments")
        assertTrue(decodedUrls.any { it.endsWith("seg_1.mp4") }, "Must contain first segment")
        assertTrue(decodedUrls.any { it.endsWith("seg_3.mp4") }, "Must contain last segment")
    }

    @Test
    fun testDynamicLiveSlidingWindowSequenceIncrements() {
        val mpdUrl = "https://example.com/live/channel.mpd"

        fun createLiveMpd(times: List<Long>): String {
            val sElements = times.joinToString("\n") { """<S t="$it" d="2000" r="0" />""" }
            return """
                <?xml version="1.0" encoding="UTF-8"?>
                <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic">
                    <Period id="0">
                        <AdaptationSet mimeType="video/mp4" contentType="video">
                            <Representation id="v1" bandwidth="2000000">
                                <SegmentTemplate timescale="1000" initialization="init.mp4" media="seg_${'$'}Number${'$'}.mp4" startNumber="100">
                                    <SegmentTimeline>
                                        $sElements
                                    </SegmentTimeline>
                                </SegmentTemplate>
                            </Representation>
                        </AdaptationSet>
                    </Period>
                </MPD>
            """.trimIndent()
        }

        // Poll 1: Segments at timestamps 1000, 3000, 5000, 7000
        val p1 = converter.convertMediaPlaylist(createLiveMpd(listOf(1000L, 3000L, 5000L, 7000L)), "v1", 8080, "s1", mpdUrl)
        assertTrue(p1.contains("#EXT-X-MEDIA-SEQUENCE:100"), "First live poll must start at template startNumber (100)")
        assertTrue(p1.contains("#EXT-X-START:TIME-OFFSET=-20.0,PRECISE=NO"), "Live playlist must contain live edge offset")
        assertFalse(p1.contains("#EXT-X-ENDLIST"), "Live playlist must not have EXT-X-ENDLIST")

        // Poll 2: Window advanced by 1 segment (timestamp 1000 dropped, 9000 added)
        val p2 = converter.convertMediaPlaylist(createLiveMpd(listOf(3000L, 5000L, 7000L, 9000L)), "v1", 8080, "s1", mpdUrl)
        assertTrue(p2.contains("#EXT-X-MEDIA-SEQUENCE:101"), "Media sequence must increment by 1 when 1 segment drops")

        // Poll 3: Window advanced by 2 segments (timestamps 3000, 5000 dropped, 11000, 13000 added)
        val p3 = converter.convertMediaPlaylist(createLiveMpd(listOf(7000L, 9000L, 11000L, 13000L)), "v1", 8080, "s1", mpdUrl)
        assertTrue(p3.contains("#EXT-X-MEDIA-SEQUENCE:103"), "Media sequence must increment by 2 when 2 segments drop")

        // Poll 4: Refresh with identical segments (window did not slide)
        val p4 = converter.convertMediaPlaylist(createLiveMpd(listOf(7000L, 9000L, 11000L, 13000L)), "v1", 8080, "s1", mpdUrl)
        assertTrue(p4.contains("#EXT-X-MEDIA-SEQUENCE:103"), "Media sequence must remain unchanged when window does not slide")
    }

    @Test
    fun testLiveTimeShiftBufferDepthWindowing() {
        val mpdUrl = "https://example.com/live/dvr.mpd"

        // 10 segments of 6 seconds each = 60 seconds total.
        // timeShiftBufferDepth="PT30S" should restrict playlist to the latest 5 segments (30 seconds).
        val sElements = (0 until 10).joinToString("\n") { i ->
            """<S t="${i * 6}" d="6" r="0" />"""
        }
        val mpd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic" timeShiftBufferDepth="PT30S">
                <Period id="0">
                    <AdaptationSet mimeType="video/mp4" contentType="video">
                        <Representation id="v1" bandwidth="2000000">
                            <SegmentTemplate timescale="1" initialization="init.mp4" media="seg_${'$'}Time${'$'}.mp4">
                                <SegmentTimeline>
                                    $sElements
                                </SegmentTimeline>
                            </SegmentTemplate>
                        </Representation>
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val playlist = converter.convertMediaPlaylist(mpd, "v1", 8080, "s1", mpdUrl)
        val extInfCount = playlist.lines().count { it.startsWith("#EXTINF:") }

        assertEquals(5, extInfCount, "timeShiftBufferDepth of 30s with 6s segments should produce exactly 5 segments")

        val decodedUrls = extractDecodedProxyUrls(playlist)
        assertTrue(decodedUrls.any { it.endsWith("seg_54.mp4") }, "Must contain latest live edge segment (time 54)")
        assertFalse(decodedUrls.any { it.endsWith("seg_0.mp4") }, "Older segments past DVR window (time 0) must be trimmed")
    }

    @Test
    fun testMultiPeriodInitUrlDiscontinuity() {
        val mpd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static">
                <Period id="p1">
                    <AdaptationSet mimeType="video/mp4" contentType="video">
                        <Representation id="v1" bandwidth="2000000">
                            <SegmentTemplate timescale="1" initialization="init_p1.mp4" media="p1_seg_${'$'}Number${'$'}.mp4" startNumber="1">
                                <SegmentTimeline>
                                    <S t="0" d="6" r="0" />
                                </SegmentTimeline>
                            </SegmentTemplate>
                        </Representation>
                    </AdaptationSet>
                </Period>
                <Period id="p2">
                    <AdaptationSet mimeType="video/mp4" contentType="video">
                        <Representation id="v1" bandwidth="2000000">
                            <SegmentTemplate timescale="1" initialization="init_p2.mp4" media="p2_seg_${'$'}Number${'$'}.mp4" startNumber="2">
                                <SegmentTimeline>
                                    <S t="6" d="6" r="0" />
                                </SegmentTimeline>
                            </SegmentTemplate>
                        </Representation>
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val playlist = converter.convertMediaPlaylist(mpd, "v1", 8080, "s1", "https://example.com/manifest.mpd")

        assertTrue(playlist.contains("#EXT-X-DISCONTINUITY"), "Multi-period transition must emit EXT-X-DISCONTINUITY")
        val mapCount = playlist.lines().count { it.startsWith("#EXT-X-MAP:") }
        assertEquals(2, mapCount, "Each period with a different init segment must emit EXT-X-MAP")
    }

    @Test
    fun testDynamicLiveManifestEmitsProgramDateTime() {
        val mpd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="dynamic" availabilityStartTime="2026-01-01T00:00:00Z" timeShiftBufferDepth="PT60S">
                <Period id="0">
                    <AdaptationSet mimeType="video/mp4" contentType="video">
                        <Representation id="live_v1" bandwidth="2000000">
                            <SegmentTemplate timescale="1" initialization="init.mp4" media="live_${'$'}Number${'$'}.mp4" startNumber="100">
                                <SegmentTimeline>
                                    <S t="600" d="6" r="3" />
                                </SegmentTimeline>
                            </SegmentTemplate>
                        </Representation>
                    </AdaptationSet>
                </Period>
            </MPD>
        """.trimIndent()

        val playlist = converter.convertMediaPlaylist(
            mpdContent = mpd,
            repId = "live_v1",
            port = 8080,
            sessionId = "live-session",
            mpdUrl = "https://example.com/live/manifest.mpd",
        )

        assertFalse(playlist.contains("#EXT-X-ENDLIST"), "Live playlist must not have EXT-X-ENDLIST")
        assertTrue(playlist.contains("#EXT-X-START:TIME-OFFSET=-20.0,PRECISE=NO"), "Live playlist must have EXT-X-START")
        assertTrue(playlist.contains("#EXT-X-PROGRAM-DATE-TIME:"), "Live playlist must have EXT-X-PROGRAM-DATE-TIME")
    }
}
