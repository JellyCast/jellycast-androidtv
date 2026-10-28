package nl.jellycast.mobile.feature.home

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.NewItem
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
        val viewModel = HomeViewModel(store, connectivity, availability) { NOW_MS }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        return viewModel
    }

    private fun HomeViewModel.rows(): HomeRows = (uiState.value.content as HomeContent.Ready).rows

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
    fun `the most recent movie in progress is the hero and the rest is up next`() = runTest {
        val store = FakeLibraryStore(
            movies = listOf(
                testMovie("older", resumePositionMs = 60_000, lastPlayedAt = 1),
                testMovie("recent", resumePositionMs = 60_000, lastPlayedAt = 5),
            ),
        )
        val viewModel = viewModel(store)
        advanceUntilIdle()

        val rows = viewModel.rows()
        assertEquals("recent", rows.hero?.item?.id)
        assertEquals(HeroLabel.ContinueWatching, rows.hero?.label)
        assertEquals(listOf("older"), rows.upNext.map { it.item.id })
    }

    @Test
    fun `the next episode after a watched one is the hero as Next episode`() = runTest {
        val series = testSeries("s1", hasPlayedEpisodes = true).copy(
            artwork = Artwork(backdrop = "series-backdrop", logo = "series-logo"),
        )
        val store = FakeLibraryStore(
            series = listOf(series),
            episodes = listOf(
                testEpisode("e1", "s1", number = 1, isPlayed = true, lastPlayedAt = 3),
                testEpisode("e2", "s1", number = 2),
            ),
        )
        val viewModel = viewModel(store)
        advanceUntilIdle()

        val hero = viewModel.rows().hero
        assertEquals("e2", hero?.item?.id)
        assertEquals(HeroLabel.NextEpisode, hero?.label)
        assertEquals("series-backdrop", hero?.backdropUrl)
        assertEquals("series-logo", hero?.logoUrl)
        assertEquals(series.title, hero?.title)
        assertTrue(viewModel.rows().upNext.isEmpty())
    }

    @Test
    fun `with nothing to continue the hero starts the newest series nobody started`() = runTest {
        val store = FakeLibraryStore(
            movies = listOf(testMovie("m1")),
            series = listOf(
                testSeries("old", lastEpisodeAddedAt = 1),
                testSeries("new", lastEpisodeAddedAt = 9),
            ),
            episodes = listOf(
                testEpisode("old-1", "old", number = 1),
                testEpisode("special", "new", season = 0, number = 1),
                testEpisode("new-1", "new", number = 1),
            ),
        )
        val viewModel = viewModel(store)
        advanceUntilIdle()

        val hero = viewModel.rows().hero
        assertEquals("new-1", hero?.item?.id)
        assertEquals(HeroLabel.StartSomethingNew, hero?.label)
    }

    @Test
    fun `without series to start the hero is the first unwatched movie`() = runTest {
        val store = FakeLibraryStore(
            movies = listOf(testMovie("seen", isPlayed = true), testMovie("unseen")),
        )
        val viewModel = viewModel(store)
        advanceUntilIdle()

        assertEquals(PlayableItem.MovieItem(testMovie("unseen")), viewModel.rows().hero?.item)
        assertEquals(HeroLabel.StartSomethingNew, viewModel.rows().hero?.label)
    }

    @Test
    fun `an empty library has no hero and no rows`() = runTest {
        val viewModel = viewModel(FakeLibraryStore())
        advanceUntilIdle()

        assertEquals(HomeRows(null, emptyList(), emptyList(), emptyList()), viewModel.rows())
    }

    @Test
    fun `an up next episode added this week and not started is new`() = runTest {
        val store = FakeLibraryStore(
            movies = listOf(testMovie("m1", resumePositionMs = 60_000, lastPlayedAt = NOW_MS)),
            series = listOf(testSeries("a"), testSeries("b")),
            episodes = listOf(
                testEpisode("a1", "a", number = 1, isPlayed = true, lastPlayedAt = 2),
                testEpisode("a2", "a", number = 2, addedAt = NOW_MS - 2 * DAY_MS),
                testEpisode("b1", "b", number = 1, isPlayed = true, lastPlayedAt = 1),
                testEpisode("b2", "b", number = 2, addedAt = NOW_MS - 10 * DAY_MS),
            ),
        )
        val viewModel = viewModel(store)
        advanceUntilIdle()

        val upNext = viewModel.rows().upNext.associate { it.item.id to it.isNew }
        assertEquals(mapOf("a2" to true, "b2" to false), upNext)
    }

    @Test
    fun `new and not started rows come from the library index`() = runTest {
        val store = FakeLibraryStore(
            movies = listOf(testMovie("fresh", addedAt = NOW_MS - DAY_MS)),
            series = listOf(testSeries("s1", lastEpisodeAddedAt = 1)),
            episodes = listOf(
                testEpisode("e1", "s1", season = 1, number = 1),
                testEpisode("e2", "s1", season = 2, number = 1),
                testEpisode("sp", "s1", season = 0, number = 1),
            ),
        )
        val viewModel = viewModel(store)
        advanceUntilIdle()

        val rows = viewModel.rows()
        assertEquals(listOf("fresh"), rows.newEntries.map { it.itemId })
        assertTrue(rows.newEntries.single().item is NewItem.NewMovie)
        assertEquals(listOf("s1" to 2), rows.notStarted.map { it.series.id to it.seasonCount })
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
