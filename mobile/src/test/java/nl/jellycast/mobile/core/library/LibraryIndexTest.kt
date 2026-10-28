package nl.jellycast.mobile.core.library

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryIndexTest {
    private fun index(
        movies: List<Movie> = emptyList(),
        series: List<Series> = emptyList(),
        episodes: List<Episode> = emptyList(),
    ) = LibraryIndex(LibrarySnapshot(movies, series, episodes, 0), NOW)

    private fun episode(
        id: String,
        season: Int?,
        number: Int?,
        isPlayed: Boolean = false,
        resumePositionMs: Long = 0,
        lastPlayedAt: Long? = null,
        addedAt: Long? = null,
    ) = testEpisode(
        id = id,
        seriesId = "s1",
        season = season,
        number = number,
        isPlayed = isPlayed,
        resumePositionMs = resumePositionMs,
        lastPlayedAt = lastPlayedAt,
        addedAt = addedAt,
    )

    @Test
    fun `episodes are in air order with Specials last`() {
        val progress =
            index(
                series = listOf(testSeries("s1")),
                episodes =
                    listOf(
                        episode("sp", season = 0, number = 1),
                        episode("b", season = 2, number = 1),
                        episode("a2", season = 1, number = 2),
                        episode("a1", season = 1, number = 1),
                        episode("none", season = null, number = 1),
                    ),
            ).progress("s1")

        assertEquals(listOf("a1", "a2", "b", "sp", "none"), progress?.episodes?.map { it.id })
        assertEquals(listOf(1, 2, 0), progress?.seasons)
    }

    @Test
    fun `season summaries count watched episodes`() {
        val progress =
            index(
                series = listOf(testSeries("s1")),
                episodes =
                    listOf(
                        episode("a1", 1, 1, isPlayed = true),
                        episode("a2", 1, 2, isPlayed = true),
                        episode("b1", 2, 1),
                    ),
            ).progress("s1")

        assertEquals(
            listOf(SeasonSummary(1, 2, 2), SeasonSummary(2, 1, 0)),
            progress?.seasonSummaries,
        )
        assertTrue(progress?.seasonSummaries?.first()?.isWatched == true)
        assertEquals(2, progress?.watchedCount)
        assertEquals(SeriesStatus.InProgress, progress?.status)
    }

    @Test
    fun `next is the episode in progress`() {
        val progress =
            index(
                series = listOf(testSeries("s1")),
                episodes =
                    listOf(
                        episode("a1", 1, 1, isPlayed = true, lastPlayedAt = 1),
                        episode("a2", 1, 2, resumePositionMs = 10, lastPlayedAt = 2),
                    ),
            ).progress("s1")

        assertEquals("a2", progress?.next?.id)
    }

    @Test
    fun `next follows the most recently touched episode, not the highest`() {
        val progress =
            index(
                series = listOf(testSeries("s1")),
                episodes =
                    listOf(
                        episode("a1", 1, 1, isPlayed = true, lastPlayedAt = 5),
                        episode("a2", 1, 2),
                        episode("a3", 1, 3, isPlayed = true, lastPlayedAt = 1),
                        episode("a4", 1, 4),
                    ),
            ).progress("s1")

        assertEquals("a1", progress?.lastTouched?.id)
        assertEquals("a2", progress?.next?.id)
    }

    @Test
    fun `an episode watched without a time doesn't count as touched`() {
        val progress =
            index(
                series = listOf(testSeries("s1")),
                episodes = listOf(episode("a1", 1, 1, isPlayed = true), episode("a2", 1, 2)),
            ).progress("s1")

        assertNull(progress?.lastTouched)
        assertNull(progress?.next)
    }

    @Test
    fun `a fully watched series has no next and is watched`() {
        val progress =
            index(
                series = listOf(testSeries("s1")),
                episodes = listOf(episode("a1", 1, 1, isPlayed = true, lastPlayedAt = 1)),
            ).progress("s1")

        assertNull(progress?.next)
        assertEquals(SeriesStatus.Watched, progress?.status)
    }

    @Test
    fun `continue watching is most recent first, a newer episode counts from when it came`() {
        val index =
            index(
                movies =
                    listOf(
                        testMovie("m1", resumePositionMs = 10, lastPlayedAt = 4),
                        testMovie("m2", isPlayed = true, resumePositionMs = 10, lastPlayedAt = 9),
                    ),
                series = listOf(testSeries("s1")),
                episodes =
                    listOf(
                        episode("a1", 1, 1, isPlayed = true, lastPlayedAt = 2),
                        episode("a2", 1, 2, addedAt = 6),
                    ),
            )

        assertEquals(listOf("a2", "m1"), index.continueWatching.map { it.id })
    }

    @Test
    fun `new leaves out watched movies, fully watched series and old episodes`() {
        val old = NOW - 31L * DAY
        val recent = NOW - DAY
        val index =
            index(
                movies =
                    listOf(
                        testMovie("m1", addedAt = recent),
                        testMovie("m2", addedAt = recent, isPlayed = true),
                        testMovie("m3", addedAt = old),
                    ),
                series = listOf(testSeries("s1"), testSeries("s2")),
                episodes =
                    listOf(
                        episode("a1", 1, 1, addedAt = old),
                        episode("b1", 2, 1, addedAt = recent + 1),
                        testEpisode("x1", seriesId = "s2", isPlayed = true, addedAt = recent),
                    ),
            )

        val entries = index.newEntries
        assertEquals(2, entries.size)
        val series = entries.first().item as NewItem.NewSeries
        assertEquals("s1", series.series.id)
        assertEquals(1, series.count)
        assertTrue(series.isNewSeason)
        assertEquals("m1", (entries[1].item as NewItem.NewMovie).movie.id)
    }

    @Test
    fun `new episodes of a season already there are not a new season`() {
        val index =
            index(
                series = listOf(testSeries("s1")),
                episodes =
                    listOf(
                        episode("a1", 1, 1, addedAt = NOW - 40 * DAY),
                        episode("a2", 1, 2, addedAt = NOW),
                    ),
            )

        assertFalse((index.newEntries.single().item as NewItem.NewSeries).isNewSeason)
    }

    @Test
    fun `not started lists untouched series, most recently added first`() {
        val index =
            index(
                series =
                    listOf(
                        testSeries("s1"),
                        testSeries("s2", lastEpisodeAddedAt = 1),
                        testSeries("s3", lastEpisodeAddedAt = 5),
                    ),
                episodes = listOf(episode("a1", 1, 1, resumePositionMs = 10, lastPlayedAt = 1)),
            )

        assertEquals(listOf("s3", "s2"), index.notStarted.map { it.id })
    }

    @Test
    fun `next episode skips Specials and crosses season ends`() {
        val index =
            index(
                series = listOf(testSeries("s1")),
                episodes =
                    listOf(
                        episode("a1", 1, 1),
                        episode("sp", 0, 1),
                        episode("b1", 2, 1),
                    ),
            )

        assertEquals("b1", index.nextEpisode(index.snapshot.episodesById.getValue("a1"))?.id)
        assertNull(index.nextEpisode(index.snapshot.episodesById.getValue("b1")))
    }

    @Test
    fun `collection lists the other movies of the same collection by year`() {
        fun movie(id: String, year: Int, collection: String?) = testMovie(id).copy(
            year = year,
            externalIds = ExternalIds(tmdbCollection = collection),
        )
        val first = movie("lotr1", 2001, "c")
        val index =
            index(
                movies =
                    listOf(
                        movie("lotr3", 2003, "c"),
                        first,
                        movie("lotr2", 2002, "c"),
                        movie("other", 2002, null),
                    ),
            )

        assertEquals(listOf("lotr2", "lotr3"), index.collection(first).map { it.id })
        assertEquals(emptyList<Movie>(), index.collection(index.snapshot.movies.last()))
    }

    @Test
    fun `status, playable, new episode count and runtime`() {
        val index =
            index(
                movies = listOf(testMovie("m1", resumePositionMs = 10)),
                series = listOf(testSeries("s1"), testSeries("s2")),
                episodes =
                    listOf(
                        episode("a1", 1, 1, isPlayed = true, addedAt = NOW - 2 * DAY),
                        episode("a2", 1, 2, isPlayed = true, addedAt = NOW - 10 * DAY),
                    ),
            )

        assertEquals(WatchStatus.InProgress, index.status(index.snapshot.movies.single()))
        assertEquals(WatchStatus.Watched, index.status(index.snapshot.series[0]))
        assertEquals(WatchStatus.NotStarted, index.status(index.snapshot.series[1]))
        assertTrue(index.playable("m1") is PlayableItem.MovieItem)
        assertTrue(index.playable("a1") is PlayableItem.EpisodeItem)
        assertNull(index.playable("nope"))
        assertEquals(1, index.newEpisodeCount("s1", days = 7))
        assertEquals(6_000_000L, index.runtimeMs(index.snapshot.series[0]))
    }

    @Test
    fun `genres are split and counted`() {
        val index =
            index(
                movies =
                    listOf(
                        testMovie("m1", genres = listOf("Action", "Drama")),
                        testMovie("m2", genres = listOf("Action")),
                    ),
                series = listOf(testSeries("s1", genres = listOf("Action & Adventure", "Kids"))),
            )

        assertEquals(
            listOf("Action" to 3, "Adventure" to 1, "Drama" to 1, "Family" to 1),
            index.genreCounts,
        )
        assertEquals(listOf("Action" to 2, "Drama" to 1), index.movieGenreCounts)
        assertEquals(
            listOf("Science Fiction", "Fantasy"),
            LibraryGenres.canonical(listOf("Sci-Fi & Fantasy", "Sci-Fi")),
        )
        assertEquals(2010, decadeOf(2014))
        assertNull(decadeOf(null))
    }
}

private const val DAY = 24L * 60 * 60 * 1000
private const val NOW = 100L * DAY
