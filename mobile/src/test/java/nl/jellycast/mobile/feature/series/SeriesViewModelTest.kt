package nl.jellycast.mobile.feature.series

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.Episode
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.FakeSeriesConnectivity
import nl.jellycast.mobile.testing.FakeSeriesPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val series = testSeries("s1", title = "Severance")

    private fun episode(
        id: String,
        season: Int,
        number: Int,
        isPlayed: Boolean = false,
        resumePositionMs: Long = 0,
        lastPlayedAt: Long? = null,
    ): Episode = testEpisode(
        id,
        seriesId = "s1",
        season = season,
        number = number,
        isPlayed = isPlayed,
        resumePositionMs = resumePositionMs,
        lastPlayedAt = lastPlayedAt,
    )

    private val connectivity = FakeSeriesConnectivity()
    private val preferences = FakeSeriesPreferences()

    private fun TestScope.viewModel(
        store: FakeLibraryStore,
        seriesId: String = "s1",
    ): SeriesViewModel {
        val viewModel = SeriesViewModel(seriesId, store, connectivity, preferences)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        return viewModel
    }

    private fun SeriesViewModel.page(): SeriesPage {
        val state = uiState.value
        assertTrue("expected Ready but was $state", state is SeriesUiState.Ready)
        return (state as SeriesUiState.Ready).page
    }

    @Test
    fun `a series nobody started plays its first episode`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes = listOf(episode("e2", 1, 2), episode("e1", 1, 1), episode("e3", 2, 1)),
            )

        val page = viewModel(store).page()

        assertEquals("e1", page.next.id)
        assertEquals(PlayKind.Play, page.playKind)
        assertNull(page.nextEpisodeId)
        assertEquals(1, page.selectedSeason.number)
        assertEquals(listOf("e1", "e2"), page.selectedSeason.episodes.map { it.id })
        assertEquals(2, page.seasonCount)
    }

    @Test
    fun `an episode in progress is continued and its season is chosen`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes =
                    listOf(
                        episode("e1", 1, 1, isPlayed = true, lastPlayedAt = 10),
                        episode("e3", 2, 1, resumePositionMs = 60_000, lastPlayedAt = 20),
                    ),
            )

        val page = viewModel(store).page()

        assertEquals("e3", page.next.id)
        assertEquals(PlayKind.Continue, page.playKind)
        assertEquals("e3", page.nextEpisodeId)
        assertEquals(2, page.selectedSeason.number)
    }

    @Test
    fun `after a watched episode the next one plays`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes =
                    listOf(
                        episode("e1", 1, 1, isPlayed = true, lastPlayedAt = 10),
                        episode("e2", 1, 2),
                    ),
            )

        val page = viewModel(store).page()

        assertEquals("e2", page.next.id)
        assertEquals(PlayKind.Play, page.playKind)
        assertEquals("e2", page.nextEpisodeId)
    }

    @Test
    fun `a watched series offers to watch again from the first episode`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes =
                    listOf(
                        episode("e1", 1, 1, isPlayed = true, lastPlayedAt = 10),
                        episode("e2", 1, 2, isPlayed = true, lastPlayedAt = 20),
                    ),
            )

        val page = viewModel(store).page()

        assertEquals("e1", page.next.id)
        assertEquals(PlayKind.WatchAgain, page.playKind)
        assertTrue(page.isWatched)
        assertTrue(page.selectedSeason.isWatched)
    }

    @Test
    fun `seasons come in order with specials last and count what was watched`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes =
                    listOf(
                        episode("sp", 0, 1),
                        episode("e3", 2, 1),
                        episode("e1", 1, 1, isPlayed = true, lastPlayedAt = 1),
                        episode("e2", 1, 2),
                    ),
            )

        val page = viewModel(store).page()

        assertEquals(listOf(1, 2, 0), page.seasons.map { it.number })
        assertEquals(1, page.seasons.first().watchedCount)
        assertEquals(2, page.seasons.first().episodeCount)
        assertEquals(2, page.seasonCount)
    }

    @Test
    fun `choosing a season shows its episodes`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes = listOf(episode("e1", 1, 1), episode("e3", 2, 1), episode("sp", 0, 1)),
            )
        val viewModel = viewModel(store)

        viewModel.onSelectSeason(0)

        assertEquals(0, viewModel.page().selectedSeason.number)
        assertEquals(listOf("sp"), viewModel.page().selectedSeason.episodes.map { it.id })
    }

    @Test
    fun `a chosen season that disappears falls back to the default one`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes = listOf(episode("e1", 1, 1), episode("e3", 2, 1)),
            )
        val viewModel = viewModel(store)
        viewModel.onSelectSeason(2)

        store.snapshot.value = store.snapshot.value?.copy(episodes = listOf(episode("e1", 1, 1)))

        assertEquals(1, viewModel.page().selectedSeason.number)
    }

    @Test
    fun `a series that is not in the library is missing`() = runTest {
        val store =
            FakeLibraryStore(series = listOf(series), episodes = listOf(episode("e1", 1, 1)))

        assertEquals(SeriesUiState.Missing, viewModel(store, seriesId = "gone").uiState.value)
    }

    @Test
    fun `a series with only specials is missing`() = runTest {
        val store =
            FakeLibraryStore(series = listOf(series), episodes = listOf(episode("sp", 0, 1)))

        assertEquals(SeriesUiState.Missing, viewModel(store).uiState.value)
    }

    @Test
    fun `before the first sync the page loads, and a failed sync makes it missing`() = runTest {
        val store = FakeLibraryStore()
        store.snapshot.value = null
        store.syncState.value = SyncState.Syncing
        val viewModel = viewModel(store)

        assertEquals(SeriesUiState.Loading, viewModel.uiState.value)

        store.syncState.value = SyncState.Failed("offline")

        assertEquals(SeriesUiState.Missing, viewModel.uiState.value)
    }

    @Test
    fun `the watched toggle marks the whole series and back`() = runTest {
        val store =
            FakeLibraryStore(
                series = listOf(series),
                episodes = listOf(episode("e1", 1, 1)),
            )
        val viewModel = viewModel(store)

        viewModel.onToggleSeriesWatched()
        store.snapshot.value =
            store.snapshot.value?.copy(episodes = listOf(episode("e1", 1, 1, isPlayed = true)))
        viewModel.onToggleSeriesWatched()

        assertEquals(listOf(listOf("s1") to true, listOf("s1") to false), store.playedCalls)
    }

    @Test
    fun `try again refreshes the library`() = runTest {
        val store = FakeLibraryStore()
        val viewModel = viewModel(store, seriesId = "gone")

        viewModel.onRetry()

        assertEquals(1, store.refreshCalls)
    }

    @Test
    fun `the page follows the network and the chosen rating site`() = runTest {
        val store =
            FakeLibraryStore(series = listOf(series), episodes = listOf(episode("e1", 1, 1)))
        val viewModel = viewModel(store)

        connectivity.isOffline.value = true
        preferences.ratingSource.value = RatingSource.Tmdb

        assertTrue(viewModel.page().isOffline)
        assertEquals(RatingSource.Tmdb, viewModel.page().ratingSource)
    }
}
