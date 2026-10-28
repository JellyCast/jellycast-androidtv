package nl.jellycast.mobile.feature.movie

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testMovie
import nl.jellycast.mobile.testing.FakeLibraryStore
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MovieViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows the movie from the library`() = runTest {
        val viewModel = MovieViewModel("m", FakeLibraryStore(movies = listOf(testMovie("m"))))

        assertEquals("m", (viewModel.uiState.value as MovieUiState.Ready).movie.id)
    }

    @Test
    fun `a movie gone from the library is missing, and retry refreshes`() = runTest {
        val store = FakeLibraryStore()
        val viewModel = MovieViewModel("m", store)

        assertEquals(MovieUiState.Missing, viewModel.uiState.value)
        viewModel.onRetry()
        assertEquals(1, store.refreshCalls)
    }

    @Test
    fun `loading until the first library arrives`() = runTest {
        val store = FakeLibraryStore().apply { snapshot.value = null }
        val viewModel = MovieViewModel("m", store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        assertEquals(MovieUiState.Loading, viewModel.uiState.value)
    }

    @Test
    fun `toggling watched marks an unwatched movie watched and back`() = runTest {
        val store =
            FakeLibraryStore(movies = listOf(testMovie("m"), testMovie("w", isPlayed = true)))

        MovieViewModel("m", store).onToggleWatched()
        MovieViewModel("w", store).onToggleWatched()

        assertEquals(listOf(listOf("m") to true, listOf("w") to false), store.playedCalls)
    }
}
