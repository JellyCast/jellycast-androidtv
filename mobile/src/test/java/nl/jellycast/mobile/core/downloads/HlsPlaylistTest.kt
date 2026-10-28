package nl.jellycast.mobile.core.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsPlaylistTest {
    @Test
    fun `the variant is resolved against the master playlist`() {
        val master = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=5000000\nmain.m3u8?ApiKey=x\n"

        val url = HlsPlaylist.variantUrl(master, "http://server:8096/videos/1/master.m3u8?a=1")

        assertEquals("http://server:8096/videos/1/main.m3u8?ApiKey=x", url)
    }

    @Test
    fun `a variant playlist gives the init segment and every segment with its duration`() {
        val text =
            """
            #EXTM3U
            #EXT-X-TARGETDURATION:3
            #EXT-X-MAP:URI="hls1/main/-1.mp4?x=1"
            #EXTINF:3.000000, nodesc
            hls1/main/0.mp4?x=1
            #EXTINF:1.500000, nodesc
            hls1/main/1.mp4?x=1
            #EXT-X-ENDLIST
            """.trimIndent()

        val playlist = HlsPlaylist.parse(text, "http://server/videos/1/main.m3u8")

        requireNotNull(playlist)
        assertEquals("http://server/videos/1/hls1/main/-1.mp4?x=1", playlist.initSegmentUrl)
        assertEquals(2, playlist.segments.size)
        assertEquals(4.5, playlist.totalSeconds, 0.001)
    }

    @Test
    fun `a playlist without an init segment is refused`() {
        assertNull(HlsPlaylist.parse("#EXTM3U\n#EXTINF:3,\na.mp4\n", "http://server/x.m3u8"))
    }

    @Test
    fun `the local playlist gives each segment its byte range in the file`() {
        val playlist =
            localPlaylist("compact.mp4", 50, listOf(SavedSegment(100, 3.0), SavedSegment(80, 2.5)))

        assertTrue(playlist.contains("#EXT-X-MAP:URI=\"compact.mp4\",BYTERANGE=\"50@0\""))
        assertTrue(playlist.contains("#EXT-X-BYTERANGE:100@50"))
        assertTrue(playlist.contains("#EXT-X-BYTERANGE:80@150"))
        assertTrue(playlist.contains("#EXTINF:2.500000,"))
        assertTrue(playlist.trimEnd().endsWith("#EXT-X-ENDLIST"))
    }

    @Test
    fun `the plan estimates compact from the runtime`() {
        val plan = DownloadPlan.of(runtimeMs = 3_600_000, originalBytes = 10_000_000_000)

        assertEquals(2_205_000_000L, plan.compactBytes)
        assertEquals(DownloadKind.Compact, plan.defaultKind)
        assertEquals(DownloadKind.Original, DownloadPlan.of(null, 1).defaultKind)
    }
}
