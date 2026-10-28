package nl.jellycast.mobile.core.library

import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotUpdatesTest {
    private val snapshot =
        LibrarySnapshot(
            movies = listOf(testMovie("m1", resumePositionMs = 500, lastPlayedAt = 3)),
            series = listOf(testSeries("s1")),
            episodes =
                listOf(
                    testEpisode("e1", seriesId = "s1", seasonId = "season1", resumePositionMs = 9),
                    testEpisode("e2", seriesId = "s1", seasonId = "season2"),
                    testEpisode("x1", seriesId = "s2"),
                ),
            syncedAtMs = 0,
        )

    @Test
    fun `marking a series watched marks all its episodes and clears positions`() {
        val updated = snapshot.withPlayed(listOf("s1"), played = true, nowMs = 50)

        assertTrue(updated.episodesById.getValue("e1").isPlayed)
        assertEquals(0L, updated.episodesById.getValue("e1").resumePositionMs)
        assertEquals(50L, updated.episodesById.getValue("e2").lastPlayedAt)
        assertFalse(updated.episodesById.getValue("x1").isPlayed)
        assertTrue(updated.seriesById.getValue("s1").isPlayed)
    }

    @Test
    fun `unwatching keeps the last played time`() {
        val updated = snapshot.withPlayed(listOf("m1"), played = false, nowMs = 50)

        val movie = updated.moviesById.getValue("m1")
        assertFalse(movie.isPlayed)
        assertEquals(0L, movie.resumePositionMs)
        assertEquals(3L, movie.lastPlayedAt)
    }

    @Test
    fun `a stopped play keeps its position, a finished one is watched`() {
        val stopped = snapshot.withPlayback("e2", positionMs = 70, finished = false, nowMs = 9)
        val finished = snapshot.withPlayback("m1", positionMs = 70, finished = true, nowMs = 9)

        assertEquals(70L, stopped.episodesById.getValue("e2").resumePositionMs)
        assertFalse(stopped.episodesById.getValue("e2").isPlayed)
        assertTrue(finished.moviesById.getValue("m1").isPlayed)
        assertEquals(0L, finished.moviesById.getValue("m1").resumePositionMs)
        assertEquals(9L, finished.moviesById.getValue("m1").lastPlayedAt)
    }

    @Test
    fun `an offline position only wins when it is newer than the server's`() {
        val updated =
            snapshot.withOfflineProgress(
                mapOf(
                    "m1" to OfflinePosition(positionMs = 100, played = false, watchedAtMs = 2),
                    "e2" to OfflinePosition(positionMs = 200, played = true, watchedAtMs = 2),
                ),
            )

        assertEquals(500L, updated.moviesById.getValue("m1").resumePositionMs)
        assertEquals(200L, updated.episodesById.getValue("e2").resumePositionMs)
        assertTrue(updated.episodesById.getValue("e2").isPlayed)
        assertSame(snapshot, snapshot.withOfflineProgress(emptyMap()))
    }
}
