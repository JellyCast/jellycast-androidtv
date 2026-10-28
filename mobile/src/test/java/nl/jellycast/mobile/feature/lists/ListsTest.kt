package nl.jellycast.mobile.feature.lists

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Playlist
import nl.jellycast.core.model.PlaylistEntry
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.FakePlaylistRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ListsTest {
    @get:Rule val mainRule = MainDispatcherRule()

    private val seen = testMovie("m1", isPlayed = true)
    private val busy = testMovie("m2", resumePositionMs = 600_000, runtimeMs = 3_600_000)
    private val episode = testEpisode("e1", seriesId = "s1")
    private val library =
        FakeLibraryStore(listOf(seen, busy), listOf(testSeries("s1")), listOf(episode))
    private val nolan =
        Playlist(
            "l1",
            "Nolan",
            listOf(
                PlaylistEntry("a", "m1"),
                PlaylistEntry("b", "m2"),
                PlaylistEntry("gone", "unknown"),
                PlaylistEntry("c", "e1"),
            ),
        )
    private val repository =
        FakePlaylistRepository(listOf(nolan, Playlist("l2", "Christmas", emptyList())))

    private fun TestScope.store() = ListsStore(repository, backgroundScope)

    private fun <T> TestScope.collect(flow: Flow<T>) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { flow.collect {} }
    }

    @Test
    fun `progress skips items the library doesn't have and continues at the first unwatched`() {
        val progress = ListProgress.of(nolan, library.snapshot.value)

        assertEquals(listOf("a", "b", "c"), progress.items.map { it.entryId })
        assertEquals(1, progress.watchedCount)
        assertEquals("b", progress.next?.entryId)
        assertTrue(progress.isStarted)
        assertEquals(3_000_000L + 3_000_000L, progress.remainingMs)
    }

    @Test
    fun `continue plays a queue from the next item, or from the start once all is watched`() {
        val progress = ListProgress.of(nolan, library.snapshot.value)

        val request = progress.continueRequest("l1", "Nolan")
        assertEquals(PlayableItem.MovieItem(busy), request?.item)
        assertEquals(1, request?.queue?.position)
        assertEquals(3, request?.queue?.items?.size)

        val allSeen = ListProgress(listOf(ListItem("a", PlayableItem.MovieItem(seen))))
        val again = allSeen.continueRequest("l1", "Nolan")
        assertEquals(0, again?.queue?.position)
        assertTrue(again?.fromStart == true)
    }

    @Test
    fun `the lists screen shows each list's count and status`() = runTest {
        val viewModel = ListsViewModel(store(), library)
        collect(viewModel.uiState)

        val lists = viewModel.uiState.value.lists
        assertEquals(listOf("Christmas", "Nolan"), lists.map { it.name })
        assertEquals(ListStatus.Empty, lists[0].status)
        assertEquals(ListStatus.ContinueWith(PlayableItem.MovieItem(busy)), lists[1].status)
        assertEquals(3, lists[1].count)
    }

    @Test
    fun `creating a list opens it`() = runTest {
        val viewModel = ListsViewModel(store(), library)
        collect(viewModel.uiState)

        viewModel.onCreate("Horror")

        val id = viewModel.uiState.value.createdListId
        assertTrue(viewModel.uiState.value.lists.any { it.id == id && it.name == "Horror" })
        viewModel.onNavigated()
        assertNull(viewModel.uiState.value.createdListId)
    }

    @Test
    fun `moving a row moves its entry, counting items the app can't show`() = runTest {
        val store = store()
        val viewModel = ListViewModel("l1", store, library)
        collect(viewModel.uiState)

        viewModel.onMove(from = 0, to = 2)
        runCurrent()

        assertEquals(
            listOf("b", "gone", "c", "a"),
            store.state.value.list("l1")?.entries?.map { it.entryId },
        )
        assertEquals("move l1 a 3", repository.calls.last())
    }

    @Test
    fun `a refused change comes back after reloading and says so`() = runTest {
        val store = store()
        val viewModel = ListViewModel("l1", store, library)
        collect(viewModel.uiState)
        repository.failNext = true

        viewModel.onRemove("a")
        runCurrent()

        assertEquals("a", store.state.value.list("l1")?.entries?.first()?.entryId)
        assertEquals("refused", store.state.value.changeError)
    }

    @Test
    fun `a deleted list is gone`() = runTest {
        val store = store()
        val viewModel = ListViewModel("l1", store, library)
        collect(viewModel.uiState)

        viewModel.onDelete()
        runCurrent()

        assertEquals(ListUiState.Gone, viewModel.uiState.value)
        assertEquals("delete l1", repository.calls.last())
    }

    @Test
    fun `adding to a list puts the item at the bottom and closes`() = runTest {
        val store = store()
        val viewModel = AddToListViewModel(store)
        collect(viewModel.uiState)
        viewModel.onOpen()

        viewModel.onAdd("l2", listOf("m1"))

        assertTrue(viewModel.uiState.value.isDone)
        assertEquals(listOf("m1"), store.state.value.list("l2")?.entries?.map { it.itemId })
    }

    @Test
    fun `the add sheet shows where an item already is`() {
        val targets = addTargets(listOf(nolan), listOf("m2"))

        assertEquals(2, targets.single().position)
        assertNull(addTargets(listOf(nolan), listOf("m2", "x")).single().position)
    }
}
