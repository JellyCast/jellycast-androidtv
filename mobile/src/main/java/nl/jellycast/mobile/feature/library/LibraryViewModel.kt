package nl.jellycast.mobile.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.common.Clock
import nl.jellycast.core.common.matchesIgnoringCaseAndAccents
import nl.jellycast.core.data.repository.PeopleSearchRepository
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PersonMatch
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.core.network.Connectivity

sealed interface LibraryBody {
    data object Loading : LibraryBody

    data class Failed(val message: String?) : LibraryBody

    data class Ready(
        val sections: List<LibrarySection>,
        val genres: List<GenreCount>,
        val people: List<PersonMatch>,
        val episodes: List<Episode>,
        /** Titles matching the search and other filters that have the chosen status. */
        val statusCount: Int,
    ) : LibraryBody {
        val isEmpty: Boolean get() = sections.isEmpty() && people.isEmpty() && episodes.isEmpty()
    }
}

/** The library was shown from the cache because the last sync failed. */
data class SyncProblem(val message: String?, val syncedAtMs: Long)

data class LibraryUiState(
    val query: String = "",
    val filters: LibraryFilters = LibraryFilters(),
    val content: LibraryBody = LibraryBody.Loading,
    val recentSearches: List<String> = emptyList(),
    val syncProblem: SyncProblem? = null,
    val isSyncing: Boolean = false,
    val isOffline: Boolean = false,
) {
    val term: String get() = query.trim()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val store: LibraryStore,
    private val people: PeopleSearchRepository,
    private val recent: RecentSearchStore,
    private val clock: Clock,
    connectivity: Connectivity,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filters = MutableStateFlow(LibraryFilters())

    private val foundPeople =
        query
            .map { it.trim() }
            .distinctUntilChanged()
            .mapLatest { term ->
                if (term.isEmpty()) {
                    emptyList()
                } else {
                    delay(PEOPLE_DELAY_MS)
                    people.search(term).getOrDefault(emptyList())
                }
            }

    private val input =
        combine(query, filters, foundPeople, recent.terms) { query, filters, people, recent ->
            Input(query, filters, people, recent)
        }

    val uiState: StateFlow<LibraryUiState> =
        combine(
            input,
            store.snapshot,
            store.syncState,
            connectivity.isOffline,
        ) { input, snapshot, sync, offline ->
            LibraryUiState(
                query = input.query,
                filters = input.filters,
                content = content(input, snapshot, sync),
                recentSearches = input.recent,
                syncProblem =
                    if (snapshot != null && sync is SyncState.Failed) {
                        SyncProblem(sync.message, snapshot.syncedAtMs)
                    } else {
                        null
                    },
                isSyncing = sync == SyncState.Syncing,
                isOffline = offline,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), LibraryUiState())

    fun onQueryChange(value: String) {
        val wasEmpty = query.value.isBlank()
        query.value = value
        // A search looks in everything; the status filter would hide what was already watched.
        if (wasEmpty && value.isNotBlank()) filters.update { it.copy(status = null) }
    }

    fun onFiltersChange(value: LibraryFilters) {
        filters.value = value
    }

    fun onShowNewest() {
        filters.update { it.copy(sort = LibrarySort.Newest, status = null) }
    }

    fun onRemember() {
        val term = query.value.trim()
        if (term.isEmpty()) return
        viewModelScope.launch { recent.save(remembered(recent.terms.first(), term)) }
    }

    fun onForget(term: String) {
        viewModelScope.launch { recent.save(recent.terms.first() - term) }
    }

    fun onRefresh() {
        viewModelScope.launch { store.refresh() }
    }

    private fun content(input: Input, snapshot: LibrarySnapshot?, sync: SyncState) = when {
        snapshot != null -> ready(input, LibraryIndex(snapshot, clock.nowMs()))
        sync is SyncState.Failed -> LibraryBody.Failed(sync.message)
        else -> LibraryBody.Loading
    }

    private fun ready(input: Input, index: LibraryIndex): LibraryBody.Ready {
        val term = input.query.trim()
        val filters = input.filters
        val entries = LibraryListing.entries(index, filters.kind)
        val matching = LibraryListing.filtered(entries, filters.genre, term)
        val episodes =
            if (term.isEmpty() || filters.kind == LibraryKind.Movies || filters.genre != null) {
                emptyList()
            } else {
                index.snapshot.episodes
                    .asSequence()
                    .filter { matchesIgnoringCaseAndAccents(it.title, term) }
                    .take(EPISODE_LIMIT)
                    .toList()
            }
        return LibraryBody.Ready(
            sections = LibraryListing.sections(matching, filters.status, filters.sort, clock.nowMs()),
            genres = LibraryListing.genreCounts(entries),
            people = if (term.isEmpty()) emptyList() else input.people,
            episodes = episodes,
            statusCount = filters.status?.let { status -> matching.count { it.status == status } } ?: 0,
        )
    }

    private data class Input(
        val query: String,
        val filters: LibraryFilters,
        val people: List<PersonMatch>,
        val recent: List<String>,
    )
}

private const val PEOPLE_DELAY_MS = 300L
private const val EPISODE_LIMIT = 10
private const val STOP_TIMEOUT_MS = 5_000L
