package nl.jellycast.tv.feature.home

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.jellycast.tv.core.common.Clock
import nl.jellycast.tv.core.common.HomeSectionLeaves
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.testing.FakeLibraryRepository
import nl.jellycast.tv.testing.FakeWatchNextRepository
import nl.jellycast.tv.testing.MainDispatcherRule
import nl.jellycast.tv.testing.testEpisode
import nl.jellycast.tv.testing.testMovie
import nl.jellycast.tv.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeSuggestionViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day
    private val clock = Clock { now }

    // Stack: movie in progress, episode in progress, recently added movie.
    private val library =
        FakeLibraryRepository(
            movies =
                listOf(
                    testMovie("watched", isPlayed = true),
                    testMovie("m1", resumePositionMs = 9, lastPlayedAt = now - day),
                    testMovie("m2", addedAt = now - 2 * day),
                ),
            series = listOf(testSeries("s1")),
            nextUp =
                listOf(
                    testEpisode(
                        "e2",
                        seriesId = "s1",
                        resumePositionMs = 7,
                        lastPlayedAt = now - 2 * day,
                    ),
                ),
        )

    private val watchNext = FakeWatchNextRepository()

    private fun viewModel() =
        HomeSuggestionViewModel(library, watchNext, clock, HomeSectionLeaves())

    private fun HomeSuggestionViewModel.success() = uiState.value as HomeSuggestionUiState.Success

    private fun HomeSuggestionViewModel.ids() = success().stack.map { it.id }

    @Test
    fun `loading the stack publishes continue watching to the home screen`() = runTest {
        viewModel()

        assertEquals(listOf("m1", "e2"), watchNext.published?.map { it.itemId })
    }

    @Test
    fun `the stack is in Home order`() = runTest {
        val viewModel = viewModel()

        assertEquals(listOf("m1", "e2", "m2"), viewModel.ids())
        assertEquals(0, viewModel.success().index)
    }

    @Test
    fun `an episode comes with its series`() = runTest {
        val suggestion = viewModel().success().stack[1] as Suggestion.SeriesSuggestion

        assertEquals("s1", suggestion.series.id)
        assertEquals(SuggestionReason.ContinueWatching, suggestion.reason)
    }

    @Test
    fun `browsing does not wrap around`() = runTest {
        val viewModel = viewModel()

        viewModel.onPrevious()
        assertEquals(0, viewModel.success().index)
        viewModel.onNext()
        viewModel.onNext()
        viewModel.onNext()
        assertEquals(2, viewModel.success().index)
        viewModel.onPrevious()
        assertEquals(1, viewModel.success().index)
    }

    @Test
    fun `Back resets the stack to the first suggestion`() = runTest {
        val viewModel = viewModel()
        viewModel.onNext()

        viewModel.onBackToStart()

        assertEquals(0, viewModel.success().index)
    }

    @Test
    fun `play starts the shown suggestion and is acknowledged`() = runTest {
        val viewModel = viewModel()
        viewModel.onNext()

        viewModel.onPlay()
        assertEquals(PlayRequest("e2", 7), viewModel.success().playRequest)

        viewModel.onPlayHandled()
        assertEquals(null, viewModel.success().playRequest)
    }

    @Test
    fun `nothing to suggest`() = runTest {
        library.movies = listOf(testMovie("watched", isPlayed = true))
        library.nextUp = emptyList()

        assertEquals(HomeSuggestionUiState.Empty, viewModel().uiState.value)
    }

    @Test
    fun `error, then retry succeeds`() = runTest {
        library.failNext = true
        val viewModel = viewModel()
        assertEquals(HomeSuggestionUiState.Error, viewModel.uiState.value)

        viewModel.onRetry()
        assertTrue(viewModel.uiState.value is HomeSuggestionUiState.Success)
    }

    private fun HomeSuggestionViewModel.playAndReturn(changeWhileAway: () -> Unit) {
        onPlay()
        onPlayHandled()
        onHidden()
        changeWhileAway()
        onVisible()
    }

    @Test
    fun `returning from the player refreshes the stack and starts at suggestion 1`() = runTest {
        val viewModel = viewModel()
        viewModel.onNext()

        viewModel.playAndReturn {
            library.nextUp =
                listOf(testEpisode("e3", seriesId = "s1", number = 3, lastPlayedAt = now))
        }

        assertEquals(listOf("m1", "e3", "m2"), viewModel.ids())
        assertEquals(0, viewModel.success().index)
    }

    @Test
    fun `browsing during a refresh keeps the shown suggestion`() = runTest {
        val viewModel = viewModel()
        viewModel.onPlay()
        viewModel.onPlayHandled()
        val gate = CompletableDeferred<Unit>()
        library.gate = gate
        viewModel.onVisible()

        viewModel.onNext()
        viewModel.onNext()
        library.movies = library.movies.filter { it.id != "m1" }
        gate.complete(Unit)

        assertEquals("m2", viewModel.success().suggestion.id)
    }

    @Test
    fun `leaving resets the stack to suggestion 1`() = runTest {
        val viewModel = viewModel()
        viewModel.onNext()

        viewModel.onHidden()

        assertEquals(0, viewModel.success().index)
    }

    @Test
    fun `returning refreshes when something was watched elsewhere`() = runTest {
        val viewModel = viewModel()
        viewModel.onHidden()
        library.movies = library.movies + testMovie("m3", resumePositionMs = 5, lastPlayedAt = now)

        viewModel.onVisible()

        assertEquals(listOf("m3", "m1", "e2", "m2"), viewModel.ids())
    }

    @Test
    fun `a failed refresh keeps the old stack`() = runTest {
        val viewModel = viewModel()
        library.failCount = 4

        viewModel.onVisible()

        assertEquals(listOf("m1", "e2", "m2"), viewModel.ids())
    }

    @Test
    fun `the shown suggestion warms up the disk once it settles`() = runTest {
        val viewModel = viewModel()
        advanceTimeBy(301)
        runCurrent()
        assertEquals(listOf("m1"), library.warmedUp)

        viewModel.onNext()
        viewModel.onNext()
        advanceTimeBy(301)
        runCurrent()
        assertEquals(listOf("m1", "m2"), library.warmedUp)
    }
}
