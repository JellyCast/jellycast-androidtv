package nl.jellycast.mobile.feature.home

import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `continue watching mixes movies and episodes, most recently touched first`() {
        val movie = testMovie("m1", resumePositionMs = 60_000, lastPlayedAt = 100)
        val episode = testEpisode("e1", resumePositionMs = 30_000, lastPlayedAt = 200)
        val notStarted = testEpisode("e2", lastPlayedAt = 300)

        val items = continueWatching(listOf(movie), listOf(episode, notStarted))

        assertEquals(listOf("e1", "m1"), items.map { it.id })
        assertTrue(items.first() is PlayableItem.EpisodeItem)
    }

    @Test
    fun `next up holds only episodes that are not in progress`() = runTest {
        val library =
            FakeLibraryRepository(
                nextUp =
                    listOf(
                        testEpisode("e1", resumePositionMs = 30_000),
                        testEpisode("e2"),
                    ),
            )

        val state = HomeViewModel(library).uiState.value

        assertEquals(listOf("e2"), state.nextEpisodes.map { it.id })
        assertEquals(listOf("e1"), state.continueWatching.map { it.id })
    }

    @Test
    fun `search filters movies ignoring case and accents`() = runTest {
        val library =
            FakeLibraryRepository(
                movies = listOf(testMovie("m1", title = "Amélie"), testMovie("m2", title = "Heat")),
            )
        val viewModel = HomeViewModel(library)

        viewModel.onTabSelect(HomeTab.Movies)
        viewModel.onQueryChange("AMELIE")

        assertEquals(listOf("m1"), viewModel.uiState.value.movies.valueOrNull?.map { it.id })
    }

    @Test
    fun `switching tab clears the search`() = runTest {
        val viewModel = HomeViewModel(FakeLibraryRepository(series = listOf(testSeries("s1"))))
        viewModel.onQueryChange("x")

        viewModel.onTabSelect(HomeTab.Series)

        assertEquals("", viewModel.uiState.value.query)
        assertEquals(1, viewModel.uiState.value.series.valueOrNull?.size)
    }

    @Test
    fun `the rows show while the full library loads on its own`() = runTest {
        val viewModel = HomeViewModel(FakeLibraryRepository(movies = listOf(testMovie("m1"))))

        val state = viewModel.uiState.value
        assertFalse(state.rowsLoading)
        assertTrue(state.movies is Loadable.Ready)
    }

    @Test
    fun `only the parts that failed show an error, and retry loads them again`() = runTest {
        // Calls run in order: resume, new movies, next up, movies, series; the first three fail.
        val library = FakeLibraryRepository(movies = listOf(testMovie("m1"))).apply {
            failCount = 3
        }
        val viewModel = HomeViewModel(library)
        assertTrue(viewModel.uiState.value.rowsFailed)
        assertTrue(viewModel.uiState.value.movies is Loadable.Ready)

        viewModel.onRetry()

        assertFalse(viewModel.uiState.value.rowsFailed)
    }

    @Test
    fun `a failed refresh keeps what is shown`() = runTest {
        val library = FakeLibraryRepository(movies = listOf(testMovie("m1")))
        val viewModel = HomeViewModel(library)

        library.failCount = 5
        viewModel.onShown()

        assertEquals(listOf("m1"), viewModel.uiState.value.movies.valueOrNull?.map { it.id })
        assertFalse(viewModel.uiState.value.rowsFailed)
    }
}
