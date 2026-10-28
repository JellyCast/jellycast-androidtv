package nl.jellycast.mobile.feature.detail

import kotlinx.coroutines.test.runTest
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testMovie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class MovieDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows the cached movie and then the loaded one`() = runTest {
        val cached = testMovie("m1", title = "Cached")
        val library =
            FakeLibraryRepository(movies = listOf(testMovie("m1", title = "Loaded"))).apply {
                cachedMovies["m1"] = cached
            }

        assertEquals("Loaded", MovieDetailViewModel("m1", library).uiState.value.movie?.title)
    }

    @Test
    fun `marking as watched clears the progress`() = runTest {
        val library =
            FakeLibraryRepository(movies = listOf(testMovie("m1", resumePositionMs = 60_000)))
        val viewModel = MovieDetailViewModel("m1", library)

        viewModel.onWatchedClick()

        val movie = viewModel.uiState.value.movie
        assertEquals(true, movie?.isPlayed)
        assertEquals(0L, movie?.resumePositionMs)
        assertEquals(listOf("m1" to true), library.playedCalls)
    }

    @Test
    fun `a failed mark is undone`() = runTest {
        val library = FakeLibraryRepository(movies = listOf(testMovie("m1")))
        val viewModel = MovieDetailViewModel("m1", library)

        library.failNext = true
        viewModel.onWatchedClick()

        assertEquals(false, viewModel.uiState.value.movie?.isPlayed)
    }

    @Test
    fun `without a cached movie a failed load is an error`() = runTest {
        val library = FakeLibraryRepository(movies = listOf(testMovie("m1"))).apply {
            failNext =
                true
        }

        assertTrue(MovieDetailViewModel("m1", library).uiState.value.failed)
    }
}
