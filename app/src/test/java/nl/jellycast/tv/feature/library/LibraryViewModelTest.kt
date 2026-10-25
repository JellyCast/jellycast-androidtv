package nl.jellycast.tv.feature.library

import kotlinx.coroutines.test.runTest
import nl.jellycast.tv.core.model.LibraryKind
import nl.jellycast.tv.core.model.LibrarySort
import nl.jellycast.tv.core.model.UiPreferences
import nl.jellycast.tv.testing.FakeLibraryRepository
import nl.jellycast.tv.testing.FakeUiPreferencesRepository
import nl.jellycast.tv.testing.MainDispatcherRule
import nl.jellycast.tv.testing.testMovie
import nl.jellycast.tv.testing.testSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LibraryViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // Already A–Z, as the server returns them.
    private val movies =
        listOf(
            testMovie("a", title = "Amélie", addedAt = 3).copy(year = 2001, communityRating = 7.8f),
            testMovie("b", title = "Casino", addedAt = 1).copy(year = 1995, communityRating = 8.2f),
            testMovie("c", title = "The Prestige", addedAt = 2).copy(year = 2006),
        )

    private fun viewModel(
        library: FakeLibraryRepository = FakeLibraryRepository(movies = movies),
        preferences: FakeUiPreferencesRepository = FakeUiPreferencesRepository(),
    ) = LibraryViewModel(library, preferences)

    private fun LibraryViewModel.ids(): List<String> =
        (uiState.value.content as LibraryContent.Loaded).lines.flatten().map { it.id }

    @Test
    fun `loads the last chosen kind sorted A–Z`() = runTest {
        val viewModel = viewModel()

        assertEquals(LibraryKind.Movies, viewModel.uiState.value.kind)
        assertEquals(listOf("a", "b", "c"), viewModel.ids())
        assertEquals(3, (viewModel.uiState.value.content as LibraryContent.Loaded).total)
    }

    @Test
    fun `sort cycles around and is remembered`() = runTest {
        val preferences = FakeUiPreferencesRepository()
        val viewModel = viewModel(preferences = preferences)

        viewModel.onSortClick()
        assertEquals(LibrarySort.Added, viewModel.uiState.value.sort)
        assertEquals(listOf("a", "c", "b"), viewModel.ids())

        viewModel.onSortClick()
        assertEquals(listOf("c", "a", "b"), viewModel.ids())

        // Without a rating last.
        viewModel.onSortClick()
        assertEquals(listOf("b", "a", "c"), viewModel.ids())

        viewModel.onSortClick()
        assertEquals(LibrarySort.Title, viewModel.uiState.value.sort)
        assertEquals(LibrarySort.Title, preferences.preferences.value.librarySort)
    }

    @Test
    fun `search ignores case and accents`() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("AMELIE")
        assertEquals(listOf("a"), viewModel.ids())

        viewModel.onQueryChange("zzz")
        assertTrue((viewModel.uiState.value.content as LibraryContent.Loaded).lines.isEmpty())

        viewModel.onQueryChange("")
        assertEquals(3, viewModel.ids().size)
    }

    @Test
    fun `switching to series loads them and remembers the choice`() = runTest {
        val library =
            FakeLibraryRepository(
                movies = movies,
                series = listOf(testSeries("s1", title = "Friends")),
            )
        val preferences = FakeUiPreferencesRepository()
        val viewModel = viewModel(library, preferences)

        viewModel.onKindSelect(LibraryKind.Series)

        assertEquals(listOf("s1"), viewModel.ids())
        assertEquals(LibraryKind.Series, preferences.preferences.value.libraryKind)
    }

    @Test
    fun `starts with the saved kind and sort`() = runTest {
        val preferences =
            FakeUiPreferencesRepository(
                UiPreferences(libraryKind = LibraryKind.Series, librarySort = LibrarySort.Year),
            )
        val library = FakeLibraryRepository(series = listOf(testSeries("s1"), testSeries("s2")))

        val viewModel = viewModel(library, preferences)

        assertEquals(LibraryKind.Series, viewModel.uiState.value.kind)
        assertEquals(LibrarySort.Year, viewModel.uiState.value.sort)
        assertEquals(listOf("s1", "s2"), viewModel.ids())
    }

    @Test
    fun `error without content, retry succeeds, a failed refresh keeps the list`() = runTest {
        val library = FakeLibraryRepository(movies = movies).apply { failNext = true }
        val viewModel = viewModel(library)
        assertEquals(LibraryContent.Error, viewModel.uiState.value.content)

        viewModel.onRetry()
        assertEquals(3, viewModel.ids().size)

        library.failNext = true
        viewModel.onShown()
        assertFalse(viewModel.uiState.value.content is LibraryContent.Error)
    }

    @Test
    fun `grid in lines of 8`() {
        val many = List(19) { testMovie("m$it") }

        val lines = libraryMovies(many, LibrarySort.Title, "").chunked(LIBRARY_COLUMNS)

        assertEquals(listOf(8, 8, 3), lines.map { it.size })
    }
}
