package nl.jellycast.mobile.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.testing.FakeAdRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.HomeFakeConnectivity
import nl.jellycast.mobile.testing.HomeFakeOfflineAvailability
import nl.jellycast.mobile.ui.downloads.OfflineAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val DAY_MS = 24L * 60 * 60 * 1000
private const val NOW_MS = 100 * DAY_MS

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule(StandardTestDispatcher())

    private val connectivity = HomeFakeConnectivity()

    private fun TestScope.viewModel(
        store: FakeLibraryStore,
        availability: OfflineAvailability = NothingDownloaded,
    ): HomeViewModel {
        val viewModel =
            HomeViewModel(store, connectivity, FakeAdRepository(), availability) { NOW_MS }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        return viewModel
    }

    private fun HomeViewModel.tiles(): List<HomeTile> =
        (uiState.value.content as HomeContent.Ready).tiles

    @Test
    fun `without a library Home shows the skeleton while it syncs`() = runTest {
        val store = FakeLibraryStore().apply {
            snapshot.value = null
            syncState.value = SyncState.Syncing
        }
        val viewModel = viewModel(store)
        advanceUntilIdle()

        assertEquals(HomeContent.Loading, viewModel.uiState.value.content)
    }

    @Test
    fun `without a library a failed sync shows the error`() = runTest {
        val store = FakeLibraryStore().apply {
            snapshot.value = null
            syncState.value = SyncState.Failed("Server gone")
        }
        val viewModel = viewModel(store)
        advanceUntilIdle()

        assertEquals(
            HomeContent.Failed("Server gone", isOffline = false),
            viewModel.uiState.value.content,
        )
    }

    @Test
    fun `a library shows its tiles in one list`() = runTest {
        val store = FakeLibraryStore(
            movies = listOf(
                testMovie("watching", resumePositionMs = 60_000, lastPlayedAt = 5),
                testMovie("fresh", addedAt = NOW_MS - DAY_MS),
            ),
            series = listOf(testSeries("s1", lastEpisodeAddedAt = 1)),
            episodes = listOf(testEpisode("e1", "s1", number = 1)),
        )
        val viewModel = viewModel(store)
        advanceUntilIdle()

        assertEquals(listOf("watching", "fresh", "s1"), viewModel.tiles().map { it.contentId })
    }

    @Test
    fun `an empty library has no tiles`() = runTest {
        val viewModel = viewModel(FakeLibraryStore())
        advanceUntilIdle()

        assertTrue(viewModel.tiles().isEmpty())
    }

    @Test
    fun `a failed refresh over a cached library shows the sync problem until a sync succeeds`() =
        runTest {
            val store = FakeLibraryStore(movies = listOf(testMovie("m1")))
            store.snapshot.value =
                LibrarySnapshot(listOf(testMovie("m1")), emptyList(), emptyList(), 42)
            val viewModel = viewModel(store)
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.syncProblem)

            store.syncState.value = SyncState.Failed("Offline")
            advanceUntilIdle()
            assertEquals(
                SyncProblem("Offline", syncedAtMs = 42, isRetrying = false, isOffline = false),
                viewModel.uiState.value.syncProblem,
            )

            store.syncState.value = SyncState.Syncing
            advanceUntilIdle()
            assertEquals(true, viewModel.uiState.value.syncProblem?.isRetrying)

            store.syncState.value = SyncState.Idle
            advanceUntilIdle()
            assertNull(viewModel.uiState.value.syncProblem)
        }

    @Test
    fun `offline, only downloaded items stay available`() = runTest {
        val store = FakeLibraryStore(
            movies = listOf(
                testMovie("kept", resumePositionMs = 60_000, lastPlayedAt = 2),
                testMovie("gone", resumePositionMs = 60_000, lastPlayedAt = 1),
            ),
        )
        val viewModel = viewModel(store, availability = HomeFakeOfflineAvailability("kept"))
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.unavailableIds.isEmpty())

        connectivity.isOffline.value = true
        advanceUntilIdle()
        assertEquals(setOf("gone"), viewModel.uiState.value.unavailableIds)
    }

    @Test
    fun `pull to refresh syncs and keeps the spinner for at least a moment`() = runTest {
        val store = FakeLibraryStore()
        val viewModel = viewModel(store)
        advanceUntilIdle()

        viewModel.onRefresh()
        advanceTimeBy(100)
        assertTrue(viewModel.uiState.value.isRefreshing)
        assertEquals(1, store.refreshCalls)

        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun `try again syncs the library`() = runTest {
        val store = FakeLibraryStore()
        val viewModel = viewModel(store)

        viewModel.onRetry()
        advanceUntilIdle()

        assertEquals(1, store.refreshCalls)
    }
}
