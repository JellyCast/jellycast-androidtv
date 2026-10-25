package nl.jellycast.tv.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMatcherTest {
    /** A real file's tracks as ExoPlayer sees them. */
    private val movieTracks =
        listOf(
            PlayerTrack("Dolby Atmos 7.1", "audio/true-hd", 8, "en"),
            PlayerTrack("Compatibility Track", "audio/ac3", 6, "en"),
            PlayerTrack(
                "Commentary by Sydney Sweeney, Glen Powell, and director Will Gluck",
                "audio/ac3",
                2,
                "en",
            ),
        )

    @Test
    fun `matches by track name`() {
        val target = ServerTrack("Compatibility Track", "ac3", 6, "eng")
        assertEquals(1, matchTrack(target, targetPosition = 2, candidates = movieTracks))
    }

    @Test
    fun `without a name matches codec, channels and language`() {
        val target = ServerTrack(null, "ac3", 6, "eng")
        assertEquals(1, matchTrack(target, targetPosition = 0, candidates = movieTracks))
    }

    @Test
    fun `channels tell 5_1 from the stereo commentary`() {
        val unnamed = movieTracks.map { it.copy(label = null) }
        assertEquals(2, matchTrack(ServerTrack(null, "ac3", 2, "eng"), 0, unnamed))
        assertEquals(1, matchTrack(ServerTrack(null, "ac3", 6, "eng"), 0, unnamed))
    }

    @Test
    fun `EAC3 with Atmos is eac3-joc in Media3`() {
        val tracks =
            listOf(
                PlayerTrack(null, "audio/true-hd", 8, "en"),
                PlayerTrack(null, "audio/eac3-joc", 8, "en"),
            )
        assertEquals(1, matchTrack(ServerTrack(null, "eac3", 8, "eng"), 0, tracks))
    }

    @Test
    fun `a duplicate name falls back to codec and channels`() {
        val tracks =
            listOf(
                PlayerTrack("Surround 5.1", "audio/vnd.dts", 6, "fr"),
                PlayerTrack("Surround 5.1", "audio/ac3", 6, "it"),
            )
        assertEquals(1, matchTrack(ServerTrack("Surround 5.1", "ac3", 6, "ita"), 0, tracks))
    }

    @Test
    fun `nothing recognizable falls back to the position`() {
        val tracks =
            listOf(PlayerTrack(null, null, null, null), PlayerTrack(null, null, null, null))
        assertEquals(1, matchTrack(ServerTrack(null, "ac3", 6, "eng"), 1, tracks))
        assertNull(matchTrack(ServerTrack(null, "ac3", 6, "eng"), 5, tracks))
    }

    @Test
    fun `subtitles match by name and type`() {
        val subs =
            listOf(
                PlayerTrack("English", "application/pgs", null, "en"),
                PlayerTrack("English SDH", "application/x-subrip", null, "en"),
            )
        assertEquals(1, matchTrack(ServerTrack(null, "subrip", null, "eng"), 0, subs))
    }

    @Test
    fun `subtitles with the same codec and language match by file order`() {
        val tracks =
            listOf(
                PlayerTrack(null, "application/x-subrip", null, "en"),
                PlayerTrack("SDH", "application/x-subrip", null, "en"),
                PlayerTrack(null, "application/x-subrip", null, "es"),
                PlayerTrack(null, "application/x-subrip", null, "es"),
            )
        assertEquals(1, matchSubtitle(ServerTrack("SDH", "subrip", null, "eng"), 1, tracks))
        assertEquals(3, matchSubtitle(ServerTrack(null, "subrip", null, "spa"), 3, tracks))
        assertEquals(0, matchSubtitle(ServerTrack(null, "subrip", null, "eng"), 0, tracks))
    }
}
