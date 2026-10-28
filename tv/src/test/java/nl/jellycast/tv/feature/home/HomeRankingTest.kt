package nl.jellycast.tv.feature.home

import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRankingTest {
    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    private fun rank(
        nextUp: List<nl.jellycast.core.model.Episode> = emptyList(),
        resume: List<nl.jellycast.core.model.Movie> = emptyList(),
        latest: List<nl.jellycast.core.model.Movie> = emptyList(),
        random: nl.jellycast.core.model.Movie? = null,
    ) = rankHome(nextUp, resume, latest, random, now)

    private fun List<Candidate>.ids() = map { it.id }

    @Test
    fun `Home is ordered by group`() {
        val result =
            rank(
                nextUp =
                    listOf(
                        testEpisode("next", seriesId = "a", touchedAt = now - 10 * day),
                        testEpisode(
                            "new",
                            seriesId = "b",
                            touchedAt = now - 20 * day,
                            addedAt = now - 2 * day,
                        ),
                    ),
                resume = listOf(testMovie("half", resumePositionMs = 5, lastPlayedAt = now - day)),
                latest = listOf(testMovie("fresh", addedAt = now - 5 * day)),
                random = testMovie("random"),
            )

        assertEquals(listOf("half", "new", "next", "fresh", "random"), result.ids())
        assertEquals(
            listOf(
                SuggestionReason.ContinueWatching,
                SuggestionReason.NewEpisode,
                SuggestionReason.NextEpisode,
                SuggestionReason.RecentlyAdded,
                SuggestionReason.ForTonight,
            ),
            result.map { it.reason },
        )
    }

    @Test
    fun `in progress for more than 3 days ranks below a new episode`() {
        val result =
            rank(
                nextUp =
                    listOf(
                        testEpisode(
                            "new",
                            seriesId = "b",
                            touchedAt = now - 20 * day,
                            addedAt = now - day,
                        ),
                    ),
                resume = listOf(
                    testMovie("half", resumePositionMs = 5, lastPlayedAt = now - 5 * day),
                ),
            )

        assertEquals(listOf("new", "half"), result.ids())
        assertEquals(SuggestionReason.ContinueWatching, result[1].reason)
    }

    @Test
    fun `an episode that existed when the previous one was watched is not new`() {
        val result =
            rank(
                nextUp =
                    listOf(
                        testEpisode(
                            "old",
                            seriesId = "a",
                            touchedAt = now - day,
                            addedAt = now - 2 * day,
                        ),
                    ),
            )

        assertEquals(SuggestionReason.NextEpisode, result.single().reason)
    }

    @Test
    fun `a new episode older than 14 days is just the next episode`() {
        val result =
            rank(
                nextUp =
                    listOf(
                        testEpisode(
                            "e",
                            seriesId = "a",
                            touchedAt = now - 40 * day,
                            addedAt = now - 20 * day,
                        ),
                    ),
                latest = listOf(testMovie("fresh", addedAt = now - day)),
            )

        // Series untouched for over 30 days: after the new movie, in the fallback.
        assertEquals(listOf("fresh", "e"), result.ids())
        assertEquals(SuggestionReason.NextEpisode, result[1].reason)
    }

    @Test
    fun `an episode in progress is continue watching`() {
        val result =
            rank(nextUp = listOf(testEpisode("e", resumePositionMs = 9, touchedAt = now - day)))

        assertEquals(SuggestionReason.ContinueWatching, result.single().reason)
    }

    @Test
    fun `movies older than 30 days are not recently added`() {
        val result = rank(latest = listOf(testMovie("old", addedAt = now - 40 * day)))

        assertEquals(emptyList<String>(), result.ids())
    }

    @Test
    fun `at most 5 suggestions, each once`() {
        val movies = (1..8).map { testMovie("m$it", addedAt = now - it * day) }

        val result = rank(latest = movies, random = movies.first())

        assertEquals(listOf("m1", "m2", "m3", "m4", "m5"), result.ids())
    }

    @Test
    fun `the fallback only when there is room`() {
        val result = rank(random = testMovie("random"))

        assertEquals(listOf("random"), result.ids())
    }
}
