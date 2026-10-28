package nl.jellycast.mobile.core.library

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.LibraryContent
import nl.jellycast.core.data.repository.SessionExpiredException
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.testing.FakeLibraryRepository
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.TestServer
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.testing.FakeConnectivity
import nl.jellycast.mobile.testing.FakeLibraryCache
import nl.jellycast.mobile.testing.FakeLibrarySyncRepository
import nl.jellycast.mobile.testing.TestAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefaultLibraryStoreTest {
    private val serverContent =
        LibraryContent(
            movies = listOf(testMovie("m1"), testMovie("m2")),
            series = listOf(testSeries("s1")),
            episodes =
                listOf(
                    testEpisode("e2", seriesId = "s1", number = 2, seasonId = "season1"),
                    testEpisode("e1", seriesId = "s1", number = 1, seasonId = "season1"),
                ),
        )
    private val sync = FakeLibrarySyncRepository(serverContent)
    private val library = FakeLibraryRepository()
    private val session = FakeSessionRepository(AuthState.LoggedIn(TestServer, "han"))
    private val cache = FakeLibraryCache()
    private val connectivity = FakeConnectivity()
    private var pending = emptyMap<String, OfflinePosition>()

    private fun TestScope.store(): DefaultLibraryStore = DefaultLibraryStore(
        sync = sync,
        library = library,
        session = session,
        cache = cache,
        connectivity = connectivity,
        pendingProgress = { pending },
        clock = Clock { NOW_MS },
        scope = backgroundScope,
    ).also { runCurrent() }

    @Test
    fun `start shows the cache first and then the synced library`() = runTest {
        val cached = LibrarySnapshot(listOf(testMovie("old")), emptyList(), emptyList(), 1)
        cache.stored = TestAccount to cached
        sync.gate = CompletableDeferred()
        val store = store()

        launch { store.start() }
        runCurrent()

        assertEquals(listOf("old"), store.snapshot.value?.movies?.map { it.id })
        assertEquals(SyncState.Syncing, store.syncState.value)

        sync.gate?.complete(Unit)
        runCurrent()

        assertEquals(listOf("m1", "m2"), store.snapshot.value?.movies?.map { it.id })
        assertEquals(SyncState.Idle, store.syncState.value)
        assertEquals(NOW_MS, store.snapshot.value?.syncedAtMs)
        assertEquals(store.snapshot.value, cache.stored?.second)
    }

    @Test
    fun `episodes are kept in air order`() = runTest {
        val store = store()

        store.start()

        assertEquals(listOf("e1", "e2"), store.snapshot.value?.episodes?.map { it.id })
    }

    @Test
    fun `a cache of another account is not shown`() = runTest {
        cache.stored =
            TestAccount.copy(userId = "other") to
            LibrarySnapshot(listOf(testMovie("old")), emptyList(), emptyList(), 1)
        sync.gate = CompletableDeferred()
        val store = store()

        launch { store.start() }
        runCurrent()

        assertNull(store.snapshot.value)
        sync.gate?.complete(Unit)
    }

    @Test
    fun `a failed sync keeps the cached library`() = runTest {
        val cached = LibrarySnapshot(listOf(testMovie("old")), emptyList(), emptyList(), 1)
        cache.stored = TestAccount to cached
        sync.failure = IllegalStateException("down")
        val store = store()

        store.start()

        assertEquals(cached, store.snapshot.value)
        assertEquals(SyncState.Failed("down"), store.syncState.value)
    }

    @Test
    fun `a refused sign-in signs out and drops the library`() = runTest {
        cache.stored = TestAccount to LibrarySnapshot(emptyList(), emptyList(), emptyList(), 1)
        sync.failure = SessionExpiredException()
        val store = store()

        store.start()
        runCurrent()

        assertEquals(1, session.logoutCalls)
        assertNull(store.snapshot.value)
        assertNull(cache.stored)
    }

    @Test
    fun `signing out anywhere clears the library and its cache`() = runTest {
        val store = store()
        store.start()

        session.auth.value = AuthState.LoggedOut(TestServer)
        runCurrent()

        assertNull(store.snapshot.value)
        assertNull(cache.stored)
    }

    @Test
    fun `a sync that finishes after sign-out doesn't bring the library back`() = runTest {
        sync.gate = CompletableDeferred()
        val store = store()
        launch { store.start() }
        runCurrent()

        store.clear()
        sync.gate?.complete(Unit)
        runCurrent()

        assertNull(store.snapshot.value)
        assertNull(cache.stored)
    }

    @Test
    fun `a refresh while one runs is skipped`() = runTest {
        sync.gate = CompletableDeferred()
        val store = store()
        launch { store.refresh() }
        runCurrent()

        store.refresh()
        sync.gate?.complete(Unit)
        runCurrent()

        assertEquals(1, sync.fetches)
    }

    @Test
    fun `coming back online after a failed sync refreshes`() = runTest {
        sync.failure = IllegalStateException("down")
        val store = store()
        store.start()
        connectivity.isOffline.value = true
        runCurrent()

        sync.failure = null
        connectivity.isOffline.value = false
        runCurrent()

        assertEquals(2, sync.fetches)
        assertEquals(SyncState.Idle, store.syncState.value)
    }

    @Test
    fun `setPlayed on a series marks its episodes locally and on the server`() = runTest {
        val store = store()
        store.start()

        val result = store.setPlayed(listOf("s1"), played = true)

        assertTrue(result.isSuccess)
        assertTrue(store.snapshot.value?.episodes.orEmpty().all { it.isPlayed })
        assertEquals(listOf("s1" to true), library.playedCalls)
    }

    @Test
    fun `setPlayed on a season marks only that season`() = runTest {
        sync.content =
            serverContent.copy(
                episodes =
                    serverContent.episodes +
                        testEpisode("e9", seriesId = "s1", season = 2, seasonId = "season2"),
            )
        val store = store()
        store.start()

        store.setPlayed(listOf("season1"), played = true)

        val played = store.snapshot.value?.episodes.orEmpty().filter { it.isPlayed }.map { it.id }
        assertEquals(listOf("e1", "e2"), played)
    }

    @Test
    fun `a failed setPlayed is undone`() = runTest {
        val store = store()
        store.start()
        val before = store.snapshot.value
        library.failNext = true

        val result = store.setPlayed(listOf("m1"), played = true)

        assertTrue(result.isFailure)
        assertEquals(before, store.snapshot.value)
        assertTrue(store.syncState.value is SyncState.Failed)
    }

    @Test
    fun `recordPlayback moves the position before the next sync`() = runTest {
        val store = store()
        store.start()

        store.recordPlayback("e1", positionMs = 60_000, finished = false)
        store.recordPlayback("m1", positionMs = 90_000, finished = true)

        val episode = store.snapshot.value?.episodesById?.get("e1")
        assertEquals(60_000L, episode?.resumePositionMs)
        assertEquals(NOW_MS, episode?.lastPlayedAt)
        val movie = store.snapshot.value?.moviesById?.get("m1")
        assertTrue(movie?.isPlayed == true)
        assertEquals(0L, movie?.resumePositionMs)
    }

    @Test
    fun `offline positions are laid over the synced library`() = runTest {
        pending =
            mapOf("m2" to OfflinePosition(positionMs = 5_000, played = false, watchedAtMs = 7))
        val store = store()

        store.start()

        assertEquals(5_000L, store.snapshot.value?.moviesById?.get("m2")?.resumePositionMs)
        assertEquals(0L, cache.stored?.second?.moviesById?.get("m2")?.resumePositionMs)
    }

    @Test
    fun `timings are recorded`() = runTest {
        val store = store()

        store.start()

        assertTrue(store.timings.value.cacheLoadMs != null)
        assertTrue(store.timings.value.lastSyncMs != null)
        store.clear()
        assertFalse(store.timings.value.lastSyncMs != null)
    }
}

private const val NOW_MS = 1_000L
