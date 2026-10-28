package nl.jellycast.mobile.core.downloads

import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.DownloadServerException
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.testing.FakeDownloadConnectivity
import nl.jellycast.mobile.testing.FakeDownloadRepository
import nl.jellycast.mobile.testing.FakeDownloadSettings
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.FakeStorageSpace
import nl.jellycast.mobile.testing.networkDrop
import nl.jellycast.mobile.testing.storeScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class DownloadStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val repository = FakeDownloadRepository()
    private val connectivity = FakeDownloadConnectivity()
    private val settings = FakeDownloadSettings()
    private val movie = PlayableItem.MovieItem(testMovie("m1"))
    private val episode = PlayableItem.EpisodeItem(testEpisode("e1", seriesId = "s1"))
    private val library =
        FakeLibraryStore(
            movies = listOf(movie.movie),
            series = listOf(testSeries("s1")),
            episodes = listOf(episode.episode),
        )
    private var now = 1_000L

    private fun TestScope.store(root: File = folder.root, scope: CoroutineScope = storeScope()) =
        storeIn(root, scope, StandardTestDispatcher(testScheduler))

    private fun storeIn(
        root: File,
        scope: CoroutineScope,
        io: CoroutineDispatcher,
    ): DownloadStore {
        val files = DownloadFiles(root)
        return DownloadStore(
            files = files,
            repository = repository,
            compact = CompactDownloader(repository, files, scope),
            original = OriginalDownloader(repository, files),
            offlineProgress = OfflineProgress(files, repository, Clock { now }),
            settings = settings,
            connectivity = connectivity,
            library = library,
            storage = FakeStorageSpace(),
            clock = Clock { now },
            scope = scope,
            io = io,
            foreground = {},
        ).also { it.start() }
    }

    @Test
    fun `an original download ends on the device with its size`() = runTest {
        val store = store()
        advanceUntilIdle()

        store.enqueue(movie, DownloadKind.Original, estimatedBytes = 10)
        advanceUntilIdle()

        val download = requireNotNull(store.download("m1"))
        assertEquals(DownloadStatus.Done, download.status)
        assertEquals(10L, download.bytes)
        assertEquals("original.mkv", store.localFile("m1")?.name)
        assertTrue(store.isOnDevice("m1"))
    }

    @Test
    fun `a compact download writes one file and a seekable playlist over it`() = runTest {
        val store = store()
        advanceUntilIdle()

        store.enqueue(episode, DownloadKind.Compact, estimatedBytes = null)
        advanceUntilIdle()

        val download = requireNotNull(store.download("e1"))
        assertEquals(DownloadStatus.Done, download.status)
        assertEquals(4L + 3 * FakeDownloadRepository.SEGMENT_BYTES, download.bytes)
        val playlist = requireNotNull(store.localFile("e1"))
        assertEquals(CompactDownloader.PLAYLIST_NAME, playlist.name)
        assertTrue(playlist.readText().contains("#EXT-X-BYTERANGE:100@104"))
        assertEquals(listOf("session-e1"), repository.stoppedTranscodes)
    }

    @Test
    fun `downloads run one at a time in the order they were added`() = runTest {
        repository.originalGate = CompletableDeferred()
        val store = store()
        advanceUntilIdle()

        store.enqueue(movie, DownloadKind.Original, 10)
        now += 1
        store.enqueue(episode, DownloadKind.Original, 10)
        advanceUntilIdle()

        assertEquals(DownloadStatus.Running, store.download("m1")?.status)
        assertEquals(DownloadStatus.Queued, store.download("e1")?.status)
        assertEquals("m1", store.current.value?.id)

        repository.originalGate?.complete(Unit)
        advanceUntilIdle()
        assertEquals(DownloadStatus.Done, store.download("e1")?.status)
    }

    @Test
    fun `pause stops the download and resume puts it back in the queue`() = runTest {
        repository.originalGate = CompletableDeferred()
        val store = store()
        advanceUntilIdle()
        store.enqueue(movie, DownloadKind.Original, 10)
        advanceUntilIdle()

        store.pause("m1")
        advanceUntilIdle()
        assertEquals(DownloadStatus.Paused, store.download("m1")?.status)
        assertNull(store.current.value)

        repository.originalGate = null
        store.resume("m1")
        advanceUntilIdle()
        assertEquals(DownloadStatus.Done, store.download("m1")?.status)
    }

    @Test
    fun `a dropped connection waits for the server and goes on`() = runTest {
        repository.originalFailures += networkDrop()
        val store = store()
        advanceUntilIdle()

        store.enqueue(movie, DownloadKind.Original, 10)
        advanceUntilIdle()

        assertEquals(DownloadStatus.Done, store.download("m1")?.status)
        assertEquals(2, repository.originalRuns)
    }

    @Test
    fun `a refusal by the server fails the download and the next one starts`() = runTest {
        repository.originalFailures += DownloadServerException(500)
        val store = store()
        advanceUntilIdle()

        store.enqueue(movie, DownloadKind.Original, 10)
        now += 1
        store.enqueue(episode, DownloadKind.Original, 10)
        advanceUntilIdle()

        assertTrue(store.download("m1")?.status is DownloadStatus.Failed)
        assertEquals(DownloadStatus.Done, store.download("e1")?.status)
    }

    @Test
    fun `remove deletes the download and its files`() = runTest {
        val store = store()
        advanceUntilIdle()
        store.enqueue(movie, DownloadKind.Original, 10)
        advanceUntilIdle()

        store.remove("m1")
        advanceUntilIdle()

        assertNull(store.download("m1"))
        assertFalse(File(folder.root, "m1").exists())
    }

    @Test
    fun `a series counts as on the device once one episode is downloaded`() = runTest {
        val store = store()
        advanceUntilIdle()
        store.enqueue(episode, DownloadKind.Original, 10)
        advanceUntilIdle()

        assertTrue(store.isOnDevice("s1"))
        assertFalse(store.isOnDevice("other"))
    }

    @Test
    fun `after a relaunch an interrupted download goes on by itself`() = runTest {
        val first = CoroutineScope(StandardTestDispatcher(testScheduler))
        repository.originalGate = CompletableDeferred()
        val store = storeIn(folder.root, first, StandardTestDispatcher(testScheduler))
        advanceUntilIdle()
        store.enqueue(movie, DownloadKind.Original, 10)
        advanceUntilIdle()
        first.cancel()

        repository.originalGate = null
        val relaunched = store()
        advanceUntilIdle()

        assertEquals(DownloadStatus.Done, relaunched.download("m1")?.status)
    }

    @Test
    fun `free space leaves room for what the queue still needs`() = runTest {
        repository.originalGate = CompletableDeferred()
        val store = store()
        advanceUntilIdle()
        store.enqueue(movie, DownloadKind.Original, 1_000_000_000)
        advanceUntilIdle()

        assertEquals(49_000_000_000L, store.freeBytes())
    }

    @Test
    fun `the plan offers compact unless the original is no bigger`() = runTest {
        val store = store()
        repository.sizeBytes = 100
        assertEquals(DownloadKind.Original, store.plan(movie).plan.defaultKind)

        repository.sizeBytes = 20_000_000_000
        val result = store.plan(movie)
        assertEquals(DownloadKind.Compact, result.plan.defaultKind)
        assertNotNull(result.plan.compactBytes)
        assertEquals(listOf("4K"), result.techParts)
    }
}
