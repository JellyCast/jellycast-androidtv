package nl.jellycast.mobile.feature.home

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.NewItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val NOW_MS = 100 * DAY_MS

class HomeTilesTest {
    private fun tiles(
        movies: List<Movie> = emptyList(),
        series: List<Series> = emptyList(),
        episodes: List<Episode> = emptyList(),
    ): List<HomeTile> =
        homeTiles(LibraryIndex(LibrarySnapshot(movies, series, episodes, 0), NOW_MS))

    @Test
    fun `continue watching comes first, most recently touched first, then new, then not started`() {
        val tiles = tiles(
            movies = listOf(
                testMovie("old-watch", resumePositionMs = 60_000, lastPlayedAt = 1),
                testMovie("new-movie", addedAt = NOW_MS - DAY_MS),
                testMovie("recent-watch", resumePositionMs = 60_000, lastPlayedAt = 9),
            ),
            series = listOf(
                testSeries("following"),
                testSeries("untouched", lastEpisodeAddedAt = 1),
            ),
            episodes = listOf(
                testEpisode("f1", "following", number = 1, isPlayed = true, lastPlayedAt = 5),
                testEpisode("f2", "following", number = 2),
                testEpisode("u1", "untouched", number = 1),
            ),
        )

        assertEquals(
            listOf("recent-watch", "following", "old-watch", "new-movie", "untouched"),
            tiles.map { it.contentId },
        )
        assertEquals(
            listOf(
                HomeTile.Continue::class,
                HomeTile.Continue::class,
                HomeTile.Continue::class,
                HomeTile.New::class,
                HomeTile.NotStarted::class,
            ),
            tiles.map { it::class },
        )
    }

    @Test
    fun `a series being watched with new episodes shows once, as continue watching`() {
        val tiles = tiles(
            series = listOf(testSeries("s1")),
            episodes = listOf(
                testEpisode("e1", "s1", number = 1, isPlayed = true, lastPlayedAt = 3),
                testEpisode("e2", "s1", number = 2, addedAt = NOW_MS - DAY_MS),
            ),
        )

        val tile = tiles.single() as HomeTile.Continue
        assertEquals("e2", tile.item.id)
    }

    @Test
    fun `a new series nobody started shows once, as new`() {
        val tiles = tiles(
            series = listOf(testSeries("s1", lastEpisodeAddedAt = NOW_MS - DAY_MS)),
            episodes = listOf(
                testEpisode("e2", "s1", number = 2, addedAt = NOW_MS - DAY_MS),
                testEpisode("e1", "s1", number = 1, addedAt = NOW_MS - DAY_MS),
            ),
        )

        val item = (tiles.single() as HomeTile.New).item as NewItem.NewSeries
        assertEquals("e1", item.firstAdded.id)
        assertEquals(2, item.count)
    }

    @Test
    fun `a series nobody started counts its regular seasons and episodes`() {
        val tiles = tiles(
            series = listOf(testSeries("s1", lastEpisodeAddedAt = 1)),
            episodes = listOf(
                testEpisode("a", "s1", season = 1, number = 1),
                testEpisode("b", "s1", season = 1, number = 2),
                testEpisode("c", "s1", season = 2, number = 1),
                testEpisode("sp", "s1", season = 0, number = 1),
            ),
        )

        assertEquals(
            listOf(HomeTile.NotStarted(testSeries("s1", lastEpisodeAddedAt = 1), 2, 3)),
            tiles,
        )
    }

    @Test
    fun `watched movies and old unwatched movies are not on Home`() {
        val tiles = tiles(
            movies = listOf(
                testMovie("seen", isPlayed = true, addedAt = NOW_MS),
                testMovie("old", addedAt = NOW_MS - 60 * DAY_MS),
            ),
        )

        assertTrue(tiles.isEmpty())
    }
}
