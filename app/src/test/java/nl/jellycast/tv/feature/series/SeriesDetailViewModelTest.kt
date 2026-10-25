package nl.jellycast.tv.feature.series

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.testing.FakeLibraryRepository
import nl.jellycast.tv.testing.MainDispatcherRule
import nl.jellycast.tv.testing.testEpisode
import nl.jellycast.tv.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SeriesDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val show = testSeries("s1")
    private val episodes =
        listOf(
            testEpisode("e3", "s1", season = 2, number = 1),
            testEpisode("e1", "s1", season = 1, number = 1, isPlayed = true),
            testEpisode("e2", "s1", season = 1, number = 2, resumePositionMs = 90_000),
            testEpisode("x1", "s1", season = 0, number = 1),
        )
    private val library =
        FakeLibraryRepository(
            series = listOf(show),
            episodes = mapOf("s1" to episodes),
        )

    @Test
    fun `seasons from 1, episodes in order, specials last`() {
        val seasons = groupSeasons(episodes)

        assertEquals(listOf(1, 2, 0), seasons.map { it.number })
        assertEquals(listOf("e1", "e2"), seasons[0].episodes.map { it.id })
        assertEquals(1, seasons[0].watchedCount)
    }

    @Test
    fun `header and prefetched episodes show immediately`() = runTest {
        library.cachedSeries["s1"] = show
        library.cachedEpisodes["s1"] = episodes

        val viewModel = SeriesDetailViewModel("s1", library)

        assertEquals(show, viewModel.uiState.value.series)
        assertEquals(3, viewModel.uiState.value.seasons?.size)
    }

    @Test
    fun `loading gives the seasons and the Continue button`() = runTest {
        val viewModel = SeriesDetailViewModel("s1", library)
        assertNull(viewModel.uiState.value.seasons)

        viewModel.onShown()

        val state = viewModel.uiState.value
        assertEquals(show, state.series)
        assertEquals("e2", state.next?.id)
        assertFalse(state.isUnstarted)
    }

    @Test
    fun `Continue resumes, a watched episode starts from the beginning`() = runTest {
        val viewModel = SeriesDetailViewModel("s1", library)
        viewModel.onShown()

        viewModel.onContinueClick()
        assertEquals(PlayRequest("e2", 90_000), viewModel.uiState.value.playRequest)
        viewModel.onPlayHandled()

        viewModel.onEpisodeClick(episodes[1].copy(resumePositionMs = 50_000))
        assertEquals(PlayRequest("e1", 0), viewModel.uiState.value.playRequest)
    }

    @Test
    fun `top block shows the episode only after 300 ms, Continue resets it`() =
        runTest(mainDispatcherRule.testDispatcher) {
            val viewModel = SeriesDetailViewModel("s1", library)
            viewModel.onShown()

            viewModel.onFocusEpisode(episodes[0])
            advanceTimeBy(HERO_DELAY_MS - 1)
            assertNull(viewModel.uiState.value.focusedEpisode)
            advanceTimeBy(2)
            assertEquals("e3", viewModel.uiState.value.focusedEpisode?.id)

            viewModel.onFocusContinue()
            assertNull(viewModel.uiState.value.focusedEpisode)
        }

    @Test
    fun `error without episodes, then retry succeeds`() = runTest {
        library.failCount = 3
        val viewModel = SeriesDetailViewModel("s1", library)

        viewModel.onShown()
        assertTrue(viewModel.uiState.value.isError)

        viewModel.onRetry()
        assertFalse(viewModel.uiState.value.isError)
        assertEquals(3, viewModel.uiState.value.seasons?.size)
    }

    @Test
    fun `nothing watched is labelled Play`() {
        val state =
            SeriesDetailUiState(
                seasons = groupSeasons(listOf(testEpisode("a", season = 1, number = 1))),
                next = testEpisode("a", season = 1, number = 1),
            )

        assertTrue(state.isUnstarted)
    }

    @Test
    fun `marking Continue as watched advances Continue and tells the server`() = runTest {
        val viewModel = SeriesDetailViewModel("s1", library)
        viewModel.onShown()
        // The server state after marking.
        library.episodes =
            mapOf("s1" to episodes.map { if (it.id == "e2") it.copy(isPlayed = true) else it })

        viewModel.onMarkNextClick()

        val state = viewModel.uiState.value
        assertEquals(listOf("e2" to true), library.playedCalls)
        assertEquals("e3", state.next?.id)
        assertTrue(state.seasons!![0].isWatched)
        assertEquals("e3", state.markableNext?.id)
        // e3 is the last regular episode (specials do not count): after it the button is gone.
        assertTrue(state.markingNextHidesButton)
    }

    @Test
    fun `a failed mark restores the state the server has`() = runTest {
        val viewModel = SeriesDetailViewModel("s1", library)
        viewModel.onShown()
        assertFalse(viewModel.uiState.value.markingNextHidesButton)
        library.failNext = true

        viewModel.onMarkNextClick()

        val state = viewModel.uiState.value
        assertEquals("e2", state.next?.id)
        assertFalse(state.seasons!![0].isWatched)
    }

    @Test
    fun `no mark button once the last episode is watched`() = runTest {
        val last = testEpisode("e1", "s1", season = 1, number = 1)
        library.episodes = mapOf("s1" to listOf(last))
        val viewModel = SeriesDetailViewModel("s1", library)
        viewModel.onShown()
        assertTrue(viewModel.uiState.value.markingNextHidesButton)
        library.episodes = mapOf("s1" to listOf(last.copy(isPlayed = true)))

        viewModel.onMarkNextClick()

        assertTrue(viewModel.uiState.value.isWatched)
        assertNull(viewModel.uiState.value.markableNext)
    }

    @Test
    fun `season watched uses the season id, toggling again reverts it`() = runTest {
        val season2 = listOf(
            testEpisode("a", "s1", season = 2, number = 1, seasonId = "sz2"),
            testEpisode("b", "s1", season = 2, number = 2, seasonId = "sz2"),
        )
        library.episodes = mapOf("s1" to season2)
        val viewModel = SeriesDetailViewModel("s1", library)
        viewModel.onShown()
        library.gate = CompletableDeferred()

        viewModel.onSeasonWatchedToggle("season-2")
        assertTrue(viewModel.uiState.value.seasons!![0].isWatched)
        viewModel.onSeasonWatchedToggle("season-2")
        assertFalse(viewModel.uiState.value.seasons!![0].isWatched)
        library.gate?.complete(Unit)

        assertEquals(listOf("sz2" to true, "sz2" to false), library.playedCalls)
    }

    @Test
    fun `series watched marks everything through the series id`() = runTest {
        val viewModel = SeriesDetailViewModel("s1", library)
        viewModel.onShown()
        library.gate = CompletableDeferred()

        viewModel.onSeriesWatchedToggle()

        assertTrue(viewModel.uiState.value.isWatched)
        assertNull(viewModel.uiState.value.markableNext)
        library.gate?.complete(Unit)
        assertEquals(listOf("s1" to true), library.playedCalls)
    }

    @Test
    fun `row ends with the season card, episodes keep their place`() {
        val season = groupSeasons(episodes)[0]

        assertEquals(listOf("e1", "e2", "mark-season-1"), season.rowItems.map { it.key })
    }
}
