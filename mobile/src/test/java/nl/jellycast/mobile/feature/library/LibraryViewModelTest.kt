package nl.jellycast.mobile.feature.library

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.model.PersonMatch
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.testing.FakeConnectivity
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.FakePeopleSearchRepository
import nl.jellycast.mobile.testing.FakeRecentSearchStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val store =
        FakeLibraryStore(
            movies =
                listOf(
                    testMovie("m1", title = "Interstellar"),
                    testMovie("m2", title = "Inception", isPlayed = true),
                ),
            series = listOf(testSeries("s1", title = "Dark")),
            episodes = listOf(testEpisode("e1", seriesId = "s1").copy(title = "Secrets")),
        )
    private val people =
        FakePeopleSearchRepository(listOf(PersonMatch("p1", "Christopher Nolan", null)))
    private val recent = FakeRecentSearchStore()

    private fun TestScope.viewModel(): LibraryViewModel {
        val viewModel = LibraryViewModel(store, people, recent, Clock { 0 }, FakeConnectivity())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        return viewModel
    }

    private val LibraryViewModel.ready: LibraryBody.Ready
        get() = uiState.value.content as LibraryBody.Ready

    @Test
    fun `opens on not watched with every kind, newest first`() = runTest {
        val viewModel = viewModel()

        assertEquals(LibraryFilters(), viewModel.uiState.value.filters)
        assertEquals(WatchStatus.NotStarted, viewModel.uiState.value.filters.status)
        assertEquals(
            setOf("m1", "s1"),
            viewModel.ready.sections.flatMap { it.entries }.map { it.id }.toSet(),
        )
        assertEquals(2, viewModel.ready.statusCount)
    }

    @Test
    fun `without a snapshot it loads, and a failed first sync is an error`() = runTest {
        store.snapshot.value = null
        val viewModel = viewModel()
        assertEquals(LibraryBody.Loading, viewModel.uiState.value.content)

        store.syncState.value = SyncState.Failed("no server")

        assertEquals(LibraryBody.Failed("no server"), viewModel.uiState.value.content)
    }

    @Test
    fun `a failed sync over a cached library shows the sync problem`() = runTest {
        val viewModel = viewModel()

        store.syncState.value = SyncState.Failed("no server")

        assertEquals("no server", viewModel.uiState.value.syncProblem?.message)
        assertTrue(viewModel.uiState.value.content is LibraryBody.Ready)
    }

    @Test
    fun `typing a term drops the status filter and finds titles and episodes`() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("in")

        assertNull(viewModel.uiState.value.filters.status)
        assertEquals(
            listOf("m1", "m2"),
            viewModel.ready.sections.flatMap { it.entries }.map { it.id }.sorted(),
        )
        viewModel.onQueryChange("secr")
        assertEquals(listOf("e1"), viewModel.ready.episodes.map { it.id })
    }

    @Test
    fun `episodes are left out when only movies or a genre show`() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("secr")

        viewModel.onFiltersChange(viewModel.uiState.value.filters.copy(kind = LibraryKind.Movies))

        assertTrue(viewModel.ready.episodes.isEmpty())
    }

    @Test
    fun `people are searched once the term rests for 300 ms`() = runTest {
        val viewModel = viewModel()

        viewModel.onQueryChange("no")
        viewModel.onQueryChange("nol")
        advanceTimeBy(299)
        runCurrent()
        assertTrue(viewModel.ready.people.isEmpty())
        advanceTimeBy(2)
        runCurrent()

        assertEquals(listOf("nol"), people.terms)
        assertEquals(listOf("p1"), viewModel.ready.people.map { it.id })
    }

    @Test
    fun `showing newest sorts by date and drops the status filter`() = runTest {
        val viewModel = viewModel()
        viewModel.onFiltersChange(LibraryFilters(sort = LibrarySort.Year))

        viewModel.onShowNewest()

        assertEquals(LibrarySort.Newest, viewModel.uiState.value.filters.sort)
        assertNull(viewModel.uiState.value.filters.status)
    }

    @Test
    fun `a search that led somewhere is remembered and can be forgotten`() = runTest {
        val viewModel = viewModel()
        viewModel.onQueryChange("  dark ")

        viewModel.onRemember()
        assertEquals(listOf("dark"), viewModel.uiState.value.recentSearches)

        viewModel.onForget("dark")
        assertTrue(viewModel.uiState.value.recentSearches.isEmpty())
    }

    @Test
    fun `refresh asks the store for a sync`() = runTest {
        val viewModel = viewModel()

        viewModel.onRefresh()

        assertEquals(1, store.refreshCalls)
    }
}
