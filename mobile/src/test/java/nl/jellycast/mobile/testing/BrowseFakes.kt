package nl.jellycast.mobile.testing

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.core.data.repository.PeopleSearchRepository
import nl.jellycast.core.data.repository.PlaylistRepository
import nl.jellycast.core.model.PersonMatch
import nl.jellycast.core.model.Playlist
import nl.jellycast.core.model.PlaylistEntry
import nl.jellycast.mobile.feature.library.RecentSearchStore

class FakePlaylistRepository(lists: List<Playlist> = emptyList()) : PlaylistRepository {
    val lists = lists.toMutableList()
    val calls = mutableListOf<String>()
    var failNext = false
    private var nextId = 1
    private var nextEntry = 1

    override suspend fun getPlaylists(): Result<List<Playlist>> =
        result("get") { lists.sortedBy { it.name } }

    override suspend fun getEntries(playlistId: String): Result<List<PlaylistEntry>> =
        result("entries $playlistId") { lists.first { it.id == playlistId }.entries }

    override suspend fun create(name: String, itemIds: List<String>): Result<String> =
        result("create $name") {
            val id = "l${nextId++}"
            lists += Playlist(id, name, itemIds.map(::entry))
            id
        }

    override suspend fun add(playlistId: String, itemIds: List<String>): Result<Unit> =
        result("add $playlistId $itemIds") {
            change(playlistId) { it.copy(entries = it.entries + itemIds.map(::entry)) }
        }

    override suspend fun remove(playlistId: String, entryIds: List<String>): Result<Unit> =
        result("remove $playlistId $entryIds") {
            change(playlistId) { list ->
                list.copy(entries = list.entries.filterNot { it.entryId in entryIds })
            }
        }

    override suspend fun move(playlistId: String, entryId: String, newIndex: Int): Result<Unit> =
        result("move $playlistId $entryId $newIndex") {
            change(playlistId) { list ->
                val entries = list.entries.toMutableList()
                val entry = entries.first { it.entryId == entryId }
                entries.remove(entry)
                entries.add(newIndex, entry)
                list.copy(entries = entries)
            }
        }

    override suspend fun rename(playlistId: String, name: String): Result<Unit> =
        result("rename $playlistId $name") { change(playlistId) { it.copy(name = name) } }

    override suspend fun delete(playlistId: String): Result<Unit> = result("delete $playlistId") {
        lists.removeAll { it.id == playlistId }
        Unit
    }

    private fun entry(itemId: String) = PlaylistEntry("e${nextEntry++}", itemId)

    private fun change(id: String, block: (Playlist) -> Playlist) {
        val index = lists.indexOfFirst { it.id == id }
        lists[index] = block(lists[index])
    }

    private fun <T> result(call: String, block: () -> T): Result<T> {
        calls += call
        if (failNext) {
            failNext = false
            return Result.failure(IllegalStateException("refused"))
        }
        return Result.success(block())
    }
}

class FakePeopleSearchRepository(private val people: List<PersonMatch> = emptyList()) :
    PeopleSearchRepository {
    val terms = mutableListOf<String>()

    override suspend fun search(term: String): Result<List<PersonMatch>> {
        terms += term
        return Result.success(people.filter { it.name.contains(term, ignoreCase = true) })
    }
}

class FakeRecentSearchStore(terms: List<String> = emptyList()) : RecentSearchStore {
    override val terms = MutableStateFlow(terms)

    override suspend fun save(terms: List<String>) {
        this.terms.value = terms
    }
}
