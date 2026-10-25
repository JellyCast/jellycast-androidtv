package nl.jellycast.tv.feature.detail

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import nl.jellycast.tv.core.model.PlayMethod
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.PlaybackTech
import nl.jellycast.tv.core.playback.PlaybackTechProvider
import nl.jellycast.tv.testing.FakeLibraryRepository
import nl.jellycast.tv.testing.MainDispatcherRule
import nl.jellycast.tv.testing.testMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovieDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val movie = testMovie("m1", resumePositionMs = 600_000)
    private val tech = PlaybackTech(PlayMethod.DirectPlay, audio = null, replacedAudio = null)
    private val techProvider = PlaybackTechProvider { tech }

    @Test
    fun `what the bookcase already knew is shown at once, without a skeleton`() = runTest {
        val library = FakeLibraryRepository(movies = listOf(movie)).apply {
            gate =
                CompletableDeferred()
        }
        library.cachedMovies["m1"] = movie

        val viewModel = MovieDetailViewModel("m1", library, techProvider)

        assertEquals(MovieDetailUiState.Success(movie), viewModel.uiState.value)
    }

    @Test
    fun `without a cache it loads first, then shows movie and tech line`() = runTest {
        val viewModel =
            MovieDetailViewModel("m1", FakeLibraryRepository(movies = listOf(movie)), techProvider)
        assertEquals(MovieDetailUiState.Loading, viewModel.uiState.value)

        viewModel.onShown()

        assertEquals(MovieDetailUiState.Success(movie, tech), viewModel.uiState.value)
    }

    @Test
    fun `error without known data, retry succeeds`() = runTest {
        val library = FakeLibraryRepository(movies = listOf(movie)).apply { failNext = true }
        val viewModel = MovieDetailViewModel("m1", library, techProvider)

        viewModel.onShown()
        assertEquals(MovieDetailUiState.Error, viewModel.uiState.value)

        viewModel.onRetry()
        assertTrue(viewModel.uiState.value is MovieDetailUiState.Success)
    }

    @Test
    fun `resume, play from start and confirm`() = runTest {
        val viewModel =
            MovieDetailViewModel("m1", FakeLibraryRepository(movies = listOf(movie)), techProvider)
        viewModel.onShown()

        viewModel.onPlay()
        assertEquals(PlayRequest("m1", 600_000), success(viewModel).playRequest)
        viewModel.onPlayHandled()
        viewModel.onPlayFromStart()
        assertEquals(PlayRequest("m1", 0), success(viewModel).playRequest)
    }

    @Test
    fun `watched turns on and reverts on a failure`() = runTest {
        val library = FakeLibraryRepository(movies = listOf(movie))
        val viewModel = MovieDetailViewModel("m1", library, techProvider)
        viewModel.onShown()

        viewModel.onToggleWatched()
        assertTrue(success(viewModel).movie.isPlayed)
        assertEquals(0L, success(viewModel).movie.resumePositionMs)
        assertEquals(listOf("m1" to true), library.playedCalls)

        library.failNext = true
        viewModel.onToggleWatched()
        assertTrue("failure reverts to watched", success(viewModel).movie.isPlayed)
        assertFalse(library.playedCalls.contains("m1" to false))
    }

    private fun success(viewModel: MovieDetailViewModel) =
        viewModel.uiState.value as MovieDetailUiState.Success

    @Test
    fun `opening spins up the disk for the movie`() = runTest {
        val library = FakeLibraryRepository(movies = listOf(movie))
        MovieDetailViewModel("m1", library, techProvider)
        assertEquals(listOf("m1"), library.warmedUp)
    }
}
