package nl.jellycast.tv.core.model

import nl.jellycast.tv.testing.testEpisode
import nl.jellycast.tv.testing.testMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WatchNextTest {
    @Test
    fun `entries are ordered by last engagement across movies and episodes`() {
        val entries =
            watchNextEntries(
                nextUp = listOf(
                    testEpisode("e1", lastPlayedAt = 20),
                    testEpisode("e2", lastPlayedAt = 5),
                ),
                resumeMovies = listOf(testMovie("m1", resumePositionMs = 9, lastPlayedAt = 10)),
            )

        assertEquals(listOf("e1", "m1", "e2"), entries.map { it.itemId })
    }

    @Test
    fun `an episode in progress continues and an unstarted one is next`() {
        val entries =
            watchNextEntries(
                nextUp =
                    listOf(
                        testEpisode("started", resumePositionMs = 60_000, lastPlayedAt = 2),
                        testEpisode("fresh", lastPlayedAt = 1),
                    ),
                resumeMovies = emptyList(),
            )

        assertEquals(listOf(WatchNextKind.Continue, WatchNextKind.Next), entries.map { it.kind })
    }

    @Test
    fun `a card opens the play link at the resume position`() {
        val entry =
            watchNextEntries(
                nextUp = emptyList(),
                resumeMovies = listOf(
                    testMovie(ITEM_ID, resumePositionMs = 90_000, lastPlayedAt = 1),
                ),
            ).single()

        assertEquals(PlayRequest(ITEM_ID, 90_000), parsePlayLink(entry.playLink))
        assertNull(entry.episode)
    }

    @Test
    fun `episodes carry the series title and episode details`() {
        val entry =
            watchNextEntries(
                nextUp = listOf(testEpisode("e1", seriesId = "s1", season = 3, number = 4)),
                resumeMovies = emptyList(),
            ).single()

        assertEquals("Serie s1", entry.title)
        assertEquals(3, entry.episode?.seasonNumber)
        assertEquals(4, entry.episode?.episodeNumber)
    }

    @Test
    fun `at most ten cards`() {
        val episodes = (1..12).map { testEpisode("e$it", lastPlayedAt = it.toLong()) }

        assertEquals(10, watchNextEntries(episodes, emptyList()).size)
    }

    private companion object {
        const val ITEM_ID = "0123456789abcdef0123456789abcdef"
    }
}
