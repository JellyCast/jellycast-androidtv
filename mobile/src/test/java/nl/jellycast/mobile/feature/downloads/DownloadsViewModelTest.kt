package nl.jellycast.mobile.feature.downloads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.downloads.CompactDownloader
import nl.jellycast.mobile.core.downloads.DownloadFiles
import nl.jellycast.mobile.core.downloads.DownloadKind
import nl.jellycast.mobile.core.downloads.DownloadStore
import nl.jellycast.mobile.core.downloads.OfflineProgress
import nl.jellycast.mobile.core.downloads.OriginalDownloader
import nl.jellycast.mobile.testing.FakeDownloadConnectivity
import nl.jellycast.mobile.testing.FakeDownloadRepository
import nl.jellycast.mobile.testing.FakeDownloadSettings
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.FakeStorageSpace
import nl.jellycast.mobile.testing.storeScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadsViewModelTest {
    @get:Rule
    val main = MainDispatcherRule()

    @get:Rule
    val folder = TemporaryFolder()

    private val repository = FakeDownloadRepository()
    private val settings = FakeDownloadSettings()
    private val movie = testMovie("m1", resumePositionMs = 600_000, runtimeMs = 7_200_000)
    private val series = testSeries("s1", title = "The Pitt")
    private val episodes =
        listOf(
            testEpisode("e2", seriesId = "s1", number = 2),
            testEpisode("e1", seriesId = "s1", number = 1, isPlayed = true),
        )
    private val library =
        FakeLibraryStore(movies = listOf(movie), series = listOf(series), episodes = episodes)
    private var now = 1_000L

    @Test
    fun `without downloads the screen is empty`() = runTest {
        val viewModel = viewModel(store())
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isEmpty)
    }

    @Test
    fun `finished downloads are grouped per movie and per series, newest first`() = runTest {
        val store = store()
        val viewModel = viewModel(store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        store.enqueue(PlayableItem.MovieItem(movie), DownloadKind.Original, 10)
        now += 1
        episodes.forEach { store.enqueue(PlayableItem.EpisodeItem(it), DownloadKind.Original, 10) }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val seriesGroup = state.groups[0] as DownloadGroup.SeriesGroup
        assertEquals("The Pitt", seriesGroup.name)
        assertEquals(listOf("e1", "e2"), seriesGroup.downloads.map { it.id })
        assertEquals(1, seriesGroup.unwatchedCount)
        val movieGroup = state.groups[1] as DownloadGroup.MovieGroup
        assertEquals(MovieWatch.InProgress(6_600_000), movieGroup.watch)
        assertEquals(1, state.storage.movieCount)
        assertEquals(2, state.storage.episodeCount)
        assertTrue(state.active.isEmpty())
    }

    @Test
    fun `removing a series removes its episodes and stops keeping it ahead`() = runTest {
        val store = store()
        val viewModel = viewModel(store)
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
        advanceUntilIdle()
        episodes.forEach { store.enqueue(PlayableItem.EpisodeItem(it), DownloadKind.Original, 10) }
        settings.keep("s1")
        advanceUntilIdle()

        viewModel.onRemoveGroup(viewModel.uiState.value.groups.single())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.groups.isEmpty())
        assertTrue(settings.keptSeriesIds.value.isEmpty())
    }

    private fun viewModel(store: DownloadStore) =
        DownloadsViewModel(store, library, FakeDownloadConnectivity())

    private fun TestScope.store(): DownloadStore {
        val files = DownloadFiles(folder.root)
        val scope = storeScope()
        return DownloadStore(
            files = files,
            repository = repository,
            compact = CompactDownloader(repository, files, scope),
            original = OriginalDownloader(repository, files),
            offlineProgress = OfflineProgress(files, repository, Clock { now }),
            settings = settings,
            connectivity = FakeDownloadConnectivity(),
            library = library,
            storage = FakeStorageSpace(),
            clock = Clock { now },
            scope = scope,
            io = StandardTestDispatcher(testScheduler),
            foreground = {},
        ).also { it.start() }
    }
}
