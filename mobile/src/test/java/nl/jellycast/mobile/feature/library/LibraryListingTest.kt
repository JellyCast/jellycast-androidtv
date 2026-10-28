package nl.jellycast.mobile.feature.library

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibrarySnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryListingTest {
    private val now = 100L * DAY

    private fun index(
        movies: List<Movie> = emptyList(),
        series: List<Series> = emptyList(),
        episodes: List<Episode> = emptyList(),
    ) = LibraryIndex(LibrarySnapshot(movies, series, episodes, 0), now)

    @Test
    fun `newest groups by this week, this month and earlier, newest first`() {
        val entries =
            LibraryListing.entries(
                index(
                    movies =
                        listOf(
                            testMovie("old", addedAt = now - 40 * DAY),
                            testMovie("week", addedAt = now - 2 * DAY),
                            testMovie("month", addedAt = now - 10 * DAY),
                            testMovie("unknown"),
                        ),
                ),
                LibraryKind.All,
            )

        val sections = LibraryListing.sections(entries, null, LibrarySort.Newest, now)

        assertEquals(
            listOf(SectionKey.AddedThisWeek, SectionKey.AddedThisMonth, SectionKey.Earlier),
            sections.map { it.key },
        )
        assertEquals(listOf("old", "unknown"), sections.last().entries.map { it.id })
    }

    @Test
    fun `a to z ignores a leading article and puts non-letters under a hash`() {
        val entries =
            LibraryListing.entries(
                index(
                    movies =
                        listOf(
                            testMovie("1", title = "The Matrix"),
                            testMovie("2", title = "Alien"),
                            testMovie("3", title = "9 Songs"),
                        ),
                ),
                LibraryKind.Movies,
            )

        val sections = LibraryListing.sections(entries, null, LibrarySort.Title, now)

        assertEquals(
            listOf(SectionKey.Letter("#"), SectionKey.Letter("A"), SectionKey.Letter("M")),
            sections.map { it.key },
        )
    }

    @Test
    fun `year groups by decade with unknown years last`() {
        val entries =
            LibraryListing.entries(
                index(
                    movies =
                        listOf(
                            testMovie("a").copy(year = 1994),
                            testMovie("b").copy(year = null),
                            testMovie("c").copy(year = 2014),
                        ),
                ),
                LibraryKind.All,
            )

        val sections = LibraryListing.sections(entries, null, LibrarySort.Year, now)

        assertEquals(
            listOf(SectionKey.Decade(2010), SectionKey.Decade(1990), SectionKey.UnknownYear),
            sections.map { it.key },
        )
    }

    @Test
    fun `a series counts the runtime of all its episodes for length`() {
        val entries =
            LibraryListing.entries(
                index(
                    movies = listOf(testMovie("m", runtimeMs = HOUR)),
                    series = listOf(testSeries("s")),
                    episodes = (1..4).map { testEpisode("e$it", seriesId = "s", number = it) },
                ),
                LibraryKind.All,
            )

        val sections = LibraryListing.sections(entries, null, LibrarySort.Length, now)

        assertEquals(listOf("s", "m"), sections.flatMap { it.entries }.map { it.id })
        assertEquals(SectionKey.OverThreeHours, sections.first().key)
    }

    @Test
    fun `the status filter keeps only titles with that status`() {
        val entries =
            LibraryListing.entries(
                index(
                    movies =
                        listOf(
                            testMovie("new"),
                            testMovie("seen", isPlayed = true),
                            testMovie("busy", resumePositionMs = 10),
                        ),
                ),
                LibraryKind.All,
            )

        val sections =
            LibraryListing.sections(entries, WatchStatus.InProgress, LibrarySort.Newest, now)

        assertEquals(listOf("busy"), sections.flatMap { it.entries }.map { it.id })
    }

    @Test
    fun `combined genres count for each part and sort by count`() {
        val entries =
            LibraryListing.entries(
                index(
                    movies =
                        listOf(
                            testMovie("1", genres = listOf("Sci-Fi & Fantasy")),
                            testMovie("2", genres = listOf("Fantasy")),
                        ),
                ),
                LibraryKind.All,
            )

        assertEquals(
            listOf(GenreCount("Fantasy", 2), GenreCount("Science Fiction", 1)),
            LibraryListing.genreCounts(entries),
        )
        assertEquals(
            listOf("1"),
            LibraryListing.filtered(entries, "Science Fiction", "").map { it.id },
        )
    }

    @Test
    fun `search matches titles ignoring case and accents`() {
        val entries =
            LibraryListing.entries(
                index(movies = listOf(testMovie("1", title = "Amélie"), testMovie("2"))),
                LibraryKind.All,
            )

        assertEquals(listOf("1"), LibraryListing.filtered(entries, null, "amelie").map { it.id })
    }

    @Test
    fun `remembered puts the term first without a copy in other case, at most five`() {
        val terms = listOf("a", "Nolan", "b", "c", "d")

        assertEquals(listOf("nolan", "a", "b", "c", "d"), remembered(terms, "nolan"))
        assertEquals(listOf("x", "a", "Nolan", "b", "c"), remembered(terms, "x"))
    }

    private companion object {
        const val DAY = 24L * 60 * 60 * 1000
        const val HOUR = 60L * 60 * 1000
    }
}
