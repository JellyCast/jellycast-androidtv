package nl.jellycast.mobile.core.downloads

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.progressOf
import nl.jellycast.mobile.testing.FakeDownloadConnectivity
import nl.jellycast.mobile.testing.FakeDownloadRepository
import nl.jellycast.mobile.testing.FakeDownloadSettings
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.FakeStorageSpace
import nl.jellycast.mobile.testing.storeScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class SeriesDownloadsTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val series = testSeries("s1")
    private val episodes =
        listOf(
            testEpisode("sp", seriesId = "s1", season = 0, number = 1),
            testEpisode("e1", seriesId = "s1", number = 1, isPlayed = true, lastPlayedAt = 5),
            testEpisode("e2", seriesId = "s1", number = 2),
            testEpisode("e3", seriesId = "s1", number = 3, isPlayed = true),
            testEpisode("e4", seriesId = "s1", number = 4),
        )
    private val settings = FakeDownloadSettings(keepsAhead = true)
    private var now = 1_000L

    @Test
    fun `upcoming episodes start where the series stands and skip watched ones and specials`() {
        val progress = progressOf(series, episodes)

        assertEquals(listOf("e2", "e4"), upcomingEpisodes(progress, 5).map { it.id })
        assertEquals(listOf("e2"), upcomingEpisodes(progress, 1).map { it.id })
    }

    @Test
    fun `a watched download goes a day after it was watched`() = runTest {
        val movie = testMovie("m1")
        val store = store(FakeLibraryStore(movies = listOf(movie)))
        advanceUntilIdle()
        store.enqueue(PlayableItem.MovieItem(movie), DownloadKind.Original, 10)
        advanceUntilIdle()

        val watchedAt = now + 1
        val watched = testMovie("m1", isPlayed = true, lastPlayedAt = watchedAt)
        now = watchedAt + 1_000
        store.tidy(index(LibrarySnapshot(listOf(watched), emptyList(), emptyList(), 0)))
        assertNotNull(store.download("m1"))

        now = watchedAt + DownloadSettings.WATCHED_GRACE_MS + 1
        store.tidy(index(LibrarySnapshot(listOf(watched), emptyList(), emptyList(), 0)))
        advanceUntilIdle()
        assertNull(store.download("m1"))
    }

    @Test
    fun `a kept series gets its missing episodes queued after a sync`() = runTest {
        val store = store(FakeLibraryStore(series = listOf(series), episodes = episodes))
        advanceUntilIdle()
        settings.keep("s1")

        store.tidy(index(LibrarySnapshot(emptyList(), listOf(series), episodes, 0)))
        advanceUntilIdle()

        assertNotNull(store.download("e2"))
        assertNotNull(store.download("e4"))
        assertNull(store.download("e3"))
    }

    private fun index(snapshot: LibrarySnapshot) = LibraryIndex(snapshot, now)

    private fun TestScope.store(library: FakeLibraryStore): DownloadStore {
        val repository = FakeDownloadRepository()
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
