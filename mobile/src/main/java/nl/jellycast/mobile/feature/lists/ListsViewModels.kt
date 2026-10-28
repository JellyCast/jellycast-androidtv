package nl.jellycast.mobile.feature.lists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Playlist
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore

sealed interface ListStatus {
    data object Empty : ListStatus

    data class ContinueWith(val next: PlayableItem) : ListStatus

    data object NotStarted : ListStatus

    data object AllWatched : ListStatus
}

data class ListSummary(
    val id: String,
    val name: String,
    val posterUrls: List<String?>,
    val count: Int,
    val watchedCount: Int,
    val status: ListStatus,
)

data class ListsUiState(
    val load: ListsLoad = ListsLoad.Idle,
    val lists: List<ListSummary> = emptyList(),
    /** A list just made here; the screen opens it and calls `onNavigated`. */
    val createdListId: String? = null,
)

class ListsViewModel(private val lists: ListsStore, library: LibraryStore) : ViewModel() {
    private val createdListId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ListsUiState> =
        combine(lists.state, library.snapshot, createdListId) { state, snapshot, created ->
            ListsUiState(
                load = state.load,
                lists = state.lists.map { summaryOf(it, snapshot) },
                createdListId = created,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ListsUiState())

    init {
        onRefresh()
    }

    fun onRefresh() {
        viewModelScope.launch { lists.load() }
    }

    fun onCreate(name: String) {
        viewModelScope.launch { createdListId.value = lists.create(name, emptyList()) }
    }

    fun onNavigated() {
        createdListId.value = null
    }
}

internal fun summaryOf(list: Playlist, snapshot: LibrarySnapshot?): ListSummary {
    val progress = ListProgress.of(list, snapshot)
    val next = progress.next
    return ListSummary(
        id = list.id,
        name = list.name,
        posterUrls = progress.items.take(STACK_SIZE).map { it.playable.listPosterUrl(snapshot) },
        count = progress.items.size,
        watchedCount = progress.watchedCount,
        status =
            when {
                progress.items.isEmpty() -> ListStatus.Empty
                next == null -> ListStatus.AllWatched
                progress.isStarted -> ListStatus.ContinueWith(next.playable)
                else -> ListStatus.NotStarted
            },
    )
}

data class ListRowItem(
    val entryId: String,
    val playable: PlayableItem,
    val posterUrl: String?,
    val isNext: Boolean,
)

sealed interface ListUiState {
    data object Loading : ListUiState

    data object Gone : ListUiState

    data class Ready(
        val listId: String,
        val name: String,
        val progress: ListProgress,
        val rows: List<ListRowItem>,
    ) : ListUiState
}

class ListViewModel(
    private val listId: String,
    private val lists: ListsStore,
    library: LibraryStore,
) : ViewModel() {
    val uiState: StateFlow<ListUiState> =
        combine(lists.state, library.snapshot) { state, snapshot ->
            val list = state.list(listId)
            when {
                list != null -> ready(list, snapshot)
                state.load == ListsLoad.Loaded -> ListUiState.Gone
                else -> ListUiState.Loading
            }
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            ListUiState.Loading,
        )

    init {
        if (lists.state.value.list(listId) == null) onRefresh()
    }

    fun onRefresh() {
        viewModelScope.launch { lists.load() }
    }

    fun onMove(from: Int, to: Int) {
        // Rows hold only playable items; the list itself may also hold items this app can't show.
        val rows = (uiState.value as? ListUiState.Ready)?.rows ?: return
        val entries = lists.state.value.list(listId)?.entries ?: return
        val fromIndex = entries.indexOfFirst { it.entryId == rows.getOrNull(from)?.entryId }
        val toIndex = entries.indexOfFirst { it.entryId == rows.getOrNull(to)?.entryId }
        if (fromIndex >= 0 && toIndex >= 0) lists.move(listId, fromIndex, toIndex)
    }

    fun onRemove(entryId: String) = lists.remove(entryId, listId)

    fun onRename(name: String) = lists.rename(listId, name)

    fun onDelete() = lists.delete(listId)

    private fun ready(list: Playlist, snapshot: LibrarySnapshot?): ListUiState.Ready {
        val progress = ListProgress.of(list, snapshot)
        val next = progress.next?.entryId
        return ListUiState.Ready(
            listId = list.id,
            name = list.name,
            progress = progress,
            rows =
                progress.items.map {
                    ListRowItem(
                        it.entryId,
                        it.playable,
                        it.playable.listPosterUrl(snapshot),
                        it.entryId == next,
                    )
                },
        )
    }
}

/** One list in the "Add to list" sheet; [position] (from 1) when everything is already in it. */
data class AddTarget(val list: Playlist, val position: Int?)

data class AddToListUiState(
    val load: ListsLoad = ListsLoad.Idle,
    val lists: List<Playlist> = emptyList(),
    val addingTo: String? = null,
    /** The add or create finished; the sheet closes and calls `onClosed`. */
    val isDone: Boolean = false,
    val changeError: String? = null,
)

class AddToListViewModel(private val lists: ListsStore) : ViewModel() {
    private val progress = MutableStateFlow(AddProgress())

    val uiState: StateFlow<AddToListUiState> =
        combine(lists.state, progress) { state, progress ->
            AddToListUiState(
                load = state.load,
                lists = state.lists,
                addingTo = progress.addingTo,
                isDone = progress.isDone,
                changeError = state.changeError,
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            AddToListUiState(),
        )

    fun onOpen() {
        viewModelScope.launch { lists.load() }
    }

    fun onAdd(listId: String, itemIds: List<String>) {
        if (progress.value.addingTo != null) return
        progress.value = AddProgress(addingTo = listId)
        viewModelScope.launch {
            lists.add(itemIds, listId)
            progress.value = AddProgress(isDone = true)
        }
    }

    fun onCreate(name: String, itemIds: List<String>) {
        viewModelScope.launch {
            if (lists.create(name, itemIds) != null) progress.value = AddProgress(isDone = true)
        }
    }

    fun onClosed() {
        progress.value = AddProgress()
    }

    fun onChangeErrorShown() = lists.onChangeErrorShown()

    private data class AddProgress(val addingTo: String? = null, val isDone: Boolean = false)
}

fun addTargets(lists: List<Playlist>, itemIds: List<String>): List<AddTarget> = lists.map { list ->
    val inList = list.entries.map { it.itemId }.toSet()
    val first = list.entries.indexOfFirst { it.itemId == itemIds.firstOrNull() }
    val complete = itemIds.isNotEmpty() && itemIds.all { it in inList } && first >= 0
    AddTarget(list, if (complete) first + 1 else null)
}

private const val STACK_SIZE = 3
private const val STOP_TIMEOUT_MS = 5_000L
