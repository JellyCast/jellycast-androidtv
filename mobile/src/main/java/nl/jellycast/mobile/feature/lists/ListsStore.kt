package nl.jellycast.mobile.feature.lists

import java.text.Collator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.PlaylistRepository
import nl.jellycast.core.model.Playlist

sealed interface ListsLoad {
    data object Idle : ListsLoad

    data object Loading : ListsLoad

    data object Loaded : ListsLoad

    data class Failed(val message: String?) : ListsLoad
}

data class ListsState(
    val lists: List<Playlist> = emptyList(),
    val load: ListsLoad = ListsLoad.Idle,
    /** Set when a change could not be saved; shown once. */
    val changeError: String? = null,
) {
    fun list(id: String): Playlist? = lists.firstOrNull { it.id == id }
}

/**
 * The user's lists, shared by the lists screens and the "Add to list" sheet. Changes show at once
 * and go to the server after, in [scope] so leaving a screen doesn't cancel them; when the server
 * refuses one, the lists are loaded again.
 */
class ListsStore(private val repository: PlaylistRepository, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow(ListsState())
    val state: StateFlow<ListsState> = _state.asStateFlow()

    /** Shows what was loaded before right away; another device may have changed the lists. */
    suspend fun load() {
        if (_state.value.lists.isEmpty()) _state.update { it.copy(load = ListsLoad.Loading) }
        repository
            .getPlaylists()
            .onSuccess { lists ->
                _state.update { it.copy(lists = lists, load = ListsLoad.Loaded) }
            }
            .onFailure { error ->
                _state.update {
                    if (it.lists.isEmpty()) it.copy(load = ListsLoad.Failed(error.message)) else it
                }
            }
    }

    /** Returns the new list's id, or `null` when the server refused it. */
    suspend fun create(name: String, itemIds: List<String>): String? {
        val id = repository.create(name, itemIds).onFailure(::fail).getOrNull() ?: return null
        val entries = repository.getEntries(id).getOrDefault(emptyList())
        val collator = Collator.getInstance()
        _state.update { state ->
            state.copy(
                lists =
                    (state.lists + Playlist(id, name, entries))
                        .sortedWith { a, b -> collator.compare(a.name, b.name) },
            )
        }
        return id
    }

    suspend fun add(itemIds: List<String>, listId: String) {
        repository
            .add(listId, itemIds)
            .onFailure(::fail)
            .onSuccess {
                repository.getEntries(listId).onSuccess { entries ->
                    update(listId) { it.copy(entries = entries) }
                }
            }
    }

    fun remove(entryId: String, listId: String) {
        update(listId) { list ->
            list.copy(entries = list.entries.filterNot { it.entryId == entryId })
        }
        save { repository.remove(listId, listOf(entryId)) }
    }

    /** Moves the entry at [from] so it ends up at [to]. */
    fun move(listId: String, from: Int, to: Int) {
        val list = _state.value.list(listId) ?: return
        if (from == to || from !in list.entries.indices || to !in list.entries.indices) return
        val moved = list.entries[from]
        val entries = list.entries.toMutableList().apply { add(to, removeAt(from)) }
        update(listId) { it.copy(entries = entries) }
        save { repository.move(listId, moved.entryId, to) }
    }

    fun rename(listId: String, name: String) {
        update(listId) { it.copy(name = name) }
        save { repository.rename(listId, name) }
    }

    fun delete(listId: String) {
        _state.update { state -> state.copy(lists = state.lists.filterNot { it.id == listId }) }
        save { repository.delete(listId) }
    }

    fun onChangeErrorShown() {
        _state.update { it.copy(changeError = null) }
    }

    private fun update(listId: String, change: (Playlist) -> Playlist) {
        _state.update { state ->
            state.copy(lists = state.lists.map { if (it.id == listId) change(it) else it })
        }
    }

    private fun save(change: suspend () -> Result<Unit>) {
        scope.launch {
            change().onFailure {
                fail(it)
                load()
            }
        }
    }

    private fun fail(error: Throwable) {
        _state.update { it.copy(changeError = error.message.orEmpty()) }
    }
}
