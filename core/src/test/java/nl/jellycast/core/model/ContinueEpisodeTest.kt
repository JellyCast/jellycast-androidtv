package nl.jellycast.core.model

import nl.jellycast.core.testing.testEpisode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContinueEpisodeTest {
    @Test
    fun `most recently touched in-progress episode beats a higher number`() {
        // S1 · E1 watched, S2 · E3 started, then S2 · E1 started.
        val episodes =
            listOf(
                testEpisode("s1e1", season = 1, number = 1, isPlayed = true, lastPlayedAt = 100),
                testEpisode("s1e2", season = 1, number = 2),
                testEpisode(
                    "s2e1",
                    season = 2,
                    number = 1,
                    resumePositionMs = 60_000,
                    lastPlayedAt = 300,
                ),
                testEpisode("s2e2", season = 2, number = 2),
                testEpisode(
                    "s2e3",
                    season = 2,
                    number = 3,
                    resumePositionMs = 60_000,
                    lastPlayedAt = 200,
                ),
            )

        assertEquals("s2e1", continueEpisode(episodes)?.id)
    }

    @Test
    fun `most recently touched watched episode gives the first unwatched one after it`() {
        val episodes =
            listOf(
                testEpisode("e1", season = 1, number = 1, isPlayed = true, lastPlayedAt = 300),
                testEpisode("e2", season = 1, number = 2, isPlayed = true, lastPlayedAt = 100),
                testEpisode("e3", season = 1, number = 3),
                testEpisode(
                    "e4",
                    season = 1,
                    number = 4,
                    resumePositionMs = 5_000,
                    lastPlayedAt = 200,
                ),
            )

        // An old episode rewatched: continue after e1, skipping watched e2.
        assertEquals("e3", continueEpisode(episodes)?.id)
    }

    @Test
    fun `across a season boundary`() {
        val episodes =
            listOf(
                testEpisode("s1e2", season = 1, number = 2, isPlayed = true, lastPlayedAt = 100),
                testEpisode("s2e1", season = 2, number = 1),
                testEpisode("s1e1", season = 1, number = 1),
            )

        assertEquals("s2e1", continueEpisode(episodes)?.id)
    }

    @Test
    fun `merely opened does not count as started`() {
        val episodes = listOf(testEpisode("e1", season = 1, number = 1, lastPlayedAt = 100))

        assertNull(continueEpisode(episodes))
    }

    @Test
    fun `everything after the most recently touched watched gives nothing`() {
        val episodes =
            listOf(
                testEpisode("e1", season = 1, number = 1, isPlayed = true, lastPlayedAt = 100),
                testEpisode("e2", season = 1, number = 2, isPlayed = true, lastPlayedAt = 200),
            )

        assertNull(continueEpisode(episodes))
    }

    @Test
    fun `a whole season marked at once picks the highest`() {
        val episodes =
            listOf(
                testEpisode("e1", season = 1, number = 1, isPlayed = true, lastPlayedAt = 100),
                testEpisode("e2", season = 1, number = 2, isPlayed = true, lastPlayedAt = 100),
                testEpisode("e3", season = 2, number = 1),
            )

        assertEquals("e3", continueEpisode(episodes)?.id)
    }

    @Test
    fun `specials do not count`() {
        val episodes =
            listOf(
                testEpisode("e1", season = 1, number = 1, isPlayed = true, lastPlayedAt = 100),
                testEpisode(
                    "x1",
                    season = 0,
                    number = 1,
                    resumePositionMs = 5_000,
                    lastPlayedAt = 200,
                ),
                testEpisode("e2", season = 1, number = 2),
            )

        assertEquals("e2", continueEpisode(episodes)?.id)
    }

    @Test
    fun `a subset starting at the most recently touched gives the same result`() {
        val episodes =
            listOf(
                testEpisode("e2", season = 1, number = 2, isPlayed = true, lastPlayedAt = 300),
                testEpisode("e3", season = 1, number = 3, isPlayed = true, lastPlayedAt = 50),
                testEpisode("e4", season = 1, number = 4),
            )

        assertEquals("e4", continueEpisode(episodes)?.id)
    }
}
