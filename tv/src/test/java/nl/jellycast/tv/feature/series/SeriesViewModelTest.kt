package nl.jellycast.tv.feature.series

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.HomeSectionLeaves
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.ShelfKind
import nl.jellycast.core.testing.FakeGenreNames
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.NOW
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SeriesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val series =
        List(8) { testSeries("s$it", genres = listOf("Drama"), lastEpisodeAddedAt = it.toLong()) }

    @Test
    fun `loading gives rows with the first card in the hero`() = runTest {
        val library = FakeLibraryRepository(series = series, nextUp = listOf(testEpisode("e1")))
        val viewModel = SeriesViewModel(library, FakeGenreNames(), HomeSectionLeaves()) { NOW }
        assertEquals(SeriesUiState.Loading, viewModel.uiState.value)

        viewModel.onShown()

        val state = viewModel.uiState.value as SeriesUiState.Success
        assertEquals(
            listOf("continue", "new", "not-started", "genre-Drama"),
            state.rows.map {
                it.key
            },
        )
        // A Continue watching card has the series id (s-e1 is the series of e1).
        assertEquals("s-e1", state.hero?.id)
    }

    @Test
    fun `load error, then retry succeeds`() = runTest {
        val library = FakeLibraryRepository(series = series).apply { failNext = true }
        val viewModel = SeriesViewModel(library, FakeGenreNames(), HomeSectionLeaves()) { NOW }

        viewModel.onShown()
        assertEquals(SeriesUiState.Error, viewModel.uiState.value)

        viewModel.onRetry()
        assertTrue(viewModel.uiState.value is SeriesUiState.Success)
    }

    @Test
    fun `a failed refresh keeps the old content`() = runTest {
        val library = FakeLibraryRepository(series = series)
        val viewModel = SeriesViewModel(library, FakeGenreNames(), HomeSectionLeaves()) { NOW }
        viewModel.onShown()
        val before = viewModel.uiState.value

        library.failNext = true
        viewModel.onShown()

        assertEquals(before, viewModel.uiState.value)
    }

    @Test
    fun `the hero changes only after focus settles for 300 ms`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel =
                SeriesViewModel(
                    FakeLibraryRepository(series = series),
                    FakeGenreNames(),
                    HomeSectionLeaves(),
                ) {
                    NOW
                }
            viewModel.onShown()
            val rows = (viewModel.uiState.value as SeriesUiState.Success).rows
            val second = rows[1].items[1]
            val third = rows[1].items[2]

            viewModel.onFocusItem(second)
            advanceTimeBy(200)
            viewModel.onFocusItem(third)
            advanceTimeBy(200)
            assertEquals("s7", hero(viewModel))

            advanceTimeBy(101)
            assertEquals(third.id, hero(viewModel))
        }

    @Test
    fun `continue watching plays the episode from its position`() = runTest {
        val episode = testEpisode("e1", resumePositionMs = 60_000)
        val viewModel =
            SeriesViewModel(
                FakeLibraryRepository(nextUp = listOf(episode)),
                FakeGenreNames(),
                HomeSectionLeaves(),
            ) {
                NOW
            }
        viewModel.onShown()

        viewModel.onEpisodeClick(episode)
        assertEquals(PlayRequest("e1", 60_000), playRequest(viewModel))

        viewModel.onPlayHandled()
        assertEquals(null, playRequest(viewModel))
    }

    @Test
    fun `marking a continue watching episode writes it and reloads the rows`() = runTest {
        val episode = testEpisode("e1", resumePositionMs = 60_000)
        val library = FakeLibraryRepository(nextUp = listOf(episode))
        val viewModel = SeriesViewModel(library, FakeGenreNames(), HomeSectionLeaves()) { NOW }
        viewModel.onShown()
        val following = testEpisode("e2").copy(seriesId = episode.seriesId)
        library.nextUp = listOf(following)

        viewModel.onSetEpisodePlayed(episode, played = true)

        assertEquals(listOf("e1" to true), library.playedCalls)
        val card = (viewModel.uiState.value as SeriesUiState.Success).rows.first().items.single()
        assertEquals("e2", (card as SeriesItem.NextUp).episode.id)
    }

    @Test
    fun `settling on a series prefetches its episodes`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val library = FakeLibraryRepository(series = series)
            val viewModel = SeriesViewModel(library, FakeGenreNames(), HomeSectionLeaves()) { NOW }
            viewModel.onShown()

            viewModel.onFocusItem(SeriesItem.Show(series[2]))
            advanceTimeBy(HERO_DELAY_MS + 1)

            assertEquals(listOf("s2"), library.prefetched)
        }

    @Test
    fun `new by date, then genres, without an all-series row or empty groups`() {
        val rows = buildSeriesRows(series, nextUp = emptyList(), FakeGenreNames(), NOW)

        assertEquals(listOf("new", "not-started", "genre-Drama"), rows.map { it.key })
        assertEquals(ShelfKind.New, rows[0].kind)
        assertEquals("s7", rows[0].items.first().id)
    }

    @Test
    fun `new excludes fully watched series`() {
        val shows =
            listOf(
                testSeries("unfinished", lastEpisodeAddedAt = 1),
                testSeries("finished", lastEpisodeAddedAt = 2, isPlayed = true),
            )

        val rows = buildSeriesRows(shows, nextUp = emptyList(), FakeGenreNames(), NOW)

        assertEquals(
            listOf("unfinished"),
            rows.single {
                it.kind == ShelfKind.New
            }.items.map { it.id },
        )
    }

    @Test
    fun `not started - nothing watched and not in progress, A-Z after new`() {
        val shows =
            listOf(
                testSeries("a"),
                testSeries("watched", hasPlayedEpisodes = true),
                testSeries("half"),
                testSeries("b", lastEpisodeAddedAt = 1),
            )
        val nextUp = listOf(testEpisode("e1", resumePositionMs = 5).copy(seriesId = "half"))

        val rows = buildSeriesRows(shows, nextUp, FakeGenreNames(), NOW)

        assertEquals(listOf("continue", "new", "not-started"), rows.map { it.key })
        assertEquals(listOf("a", "b"), rows[2].items.map { it.id })
    }

    @Test
    fun `continue watching comes first, in repository order`() {
        val nextUp =
            listOf(
                testEpisode("recent", season = 2, number = 1, resumePositionMs = 80_000),
                testEpisode("older", season = 1, number = 3),
            )

        val rows = buildSeriesRows(series, nextUp, FakeGenreNames(), NOW)

        val continueRow = rows.first()
        assertEquals(ShelfKind.ContinueWatching, continueRow.kind)
        assertEquals(listOf("s-recent", "s-older"), continueRow.items.map { it.id })
    }

    private fun hero(viewModel: SeriesViewModel) =
        (viewModel.uiState.value as SeriesUiState.Success).hero?.id

    private fun playRequest(viewModel: SeriesViewModel) =
        (viewModel.uiState.value as SeriesUiState.Success).playRequest

    @Test
    fun `settling on continue watching warms up the disk for that episode`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val episode = testEpisode("e1")
            val library = FakeLibraryRepository(nextUp = listOf(episode))
            val viewModel = SeriesViewModel(library, FakeGenreNames(), HomeSectionLeaves()) { NOW }
            viewModel.onShown()

            viewModel.onFocusItem(SeriesItem.NextUp(episode))
            advanceTimeBy(HERO_DELAY_MS + 1)

            assertEquals(listOf("e1"), library.warmedUp)
        }
}
