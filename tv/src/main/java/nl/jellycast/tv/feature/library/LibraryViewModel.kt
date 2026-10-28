package nl.jellycast.tv.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.jellycast.core.common.LOAD_TIMEOUT_MS
import nl.jellycast.core.common.matchesIgnoringCaseAndAccents
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.data.repository.UiPreferencesRepository
import nl.jellycast.core.model.LibraryKind
import nl.jellycast.core.model.LibrarySort
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series

const val LIBRARY_COLUMNS = 8

data class LibraryTile(val id: String, val title: String, val posterUrl: String?)

sealed interface LibraryContent {
    data object Loading : LibraryContent

    data object Error : LibraryContent

    data class Loaded(val lines: List<List<LibraryTile>>, val total: Int) : LibraryContent
}

data class LibraryUiState(
    val kind: LibraryKind = LibraryKind.Movies,
    val sort: LibrarySort = LibrarySort.Title,
    val query: String = "",
    val content: LibraryContent = LibraryContent.Loading,
)

class LibraryViewModel(
    private val library: LibraryRepository,
    private val preferences: UiPreferencesRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var movies: List<Movie>? = null
    private var series: List<Series>? = null
    private var loadJob: Job? = null
    private var preferencesRead = false

    init {
        viewModelScope.launch {
            preferences.getPreferencesStream().collect { prefs ->
                val first = !preferencesRead
                preferencesRead = true
                _uiState.update { it.copy(kind = prefs.libraryKind, sort = prefs.librarySort) }
                if (first) load() else rebuild()
            }
        }
    }

    // Refreshes in case something was added; the current list stays until the new one loads.
    fun onShown() {
        if (preferencesRead) load()
    }

    fun onRetry() {
        _uiState.update { it.copy(content = LibraryContent.Loading) }
        load()
    }

    fun onKindSelect(kind: LibraryKind) {
        if (kind == _uiState.value.kind) return
        _uiState.update { it.copy(kind = kind) }
        viewModelScope.launch { preferences.setLibraryKind(kind) }
        if (isLoaded(kind)) {
            rebuild()
        } else {
            _uiState.update { it.copy(content = LibraryContent.Loading) }
            load()
        }
    }

    fun onSortClick() {
        val sorts = LibrarySort.entries
        val next = sorts[(_uiState.value.sort.ordinal + 1) % sorts.size]
        _uiState.update { it.copy(sort = next) }
        viewModelScope.launch { preferences.setLibrarySort(next) }
        rebuild()
    }

    fun onQueryChange(query: String) {
        if (query == _uiState.value.query) return
        _uiState.update { it.copy(query = query) }
        rebuild()
    }

    private fun isLoaded(kind: LibraryKind): Boolean = when (kind) {
        LibraryKind.Movies -> movies != null
        LibraryKind.Series -> series != null
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        val kind = _uiState.value.kind
        loadJob =
            viewModelScope.launch {
                val loaded =
                    withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                        when (kind) {
                            LibraryKind.Movies ->
                                library.getMovies().getOrNull()?.also { movies = it }

                            LibraryKind.Series ->
                                library.getSeries().getOrNull()?.also { series = it }
                        }
                    } != null
                val currentKind = _uiState.value.kind
                when {
                    currentKind != kind ->
                        if (isLoaded(currentKind)) rebuild() else relaunchLoad()

                    loaded -> rebuild()

                    else ->
                        _uiState.update {
                            if (it.content is LibraryContent.Loaded) {
                                it
                            } else {
                                it.copy(content = LibraryContent.Error)
                            }
                        }
                }
            }
    }

    private fun relaunchLoad() {
        viewModelScope.launch { load() }
    }

    private fun rebuild() {
        val state = _uiState.value
        val tiles =
            when (state.kind) {
                LibraryKind.Movies -> movies?.let { libraryMovies(it, state.sort, state.query) }
                LibraryKind.Series -> series?.let { librarySeries(it, state.sort, state.query) }
            } ?: return
        _uiState.update {
            it.copy(
                content =
                    LibraryContent.Loaded(
                        lines = tiles.chunked(LIBRARY_COLUMNS),
                        total = tiles.size,
                    ),
            )
        }
    }
}

// The server already sorts by SortName; the other sorts are stable, so ties stay A–Z.
internal fun libraryMovies(
    movies: List<Movie>,
    sort: LibrarySort,
    query: String,
): List<LibraryTile> {
    val found = movies.filter { matchesIgnoringCaseAndAccents(it.title, query) }
    val sorted =
        when (sort) {
            LibrarySort.Title -> found
            LibrarySort.Added -> found.sortedByDescending { it.addedAt ?: Long.MIN_VALUE }
            LibrarySort.Year -> found.sortedByDescending { it.year ?: Int.MIN_VALUE }
            LibrarySort.Rating -> found.sortedByDescending { it.communityRating ?: -1f }
        }
    return sorted.map { LibraryTile(it.id, it.title, it.artwork.poster) }
}

internal fun librarySeries(
    series: List<Series>,
    sort: LibrarySort,
    query: String,
): List<LibraryTile> {
    val found = series.filter { matchesIgnoringCaseAndAccents(it.title, query) }
    val sorted =
        when (sort) {
            LibrarySort.Title -> found

            LibrarySort.Added ->
                found.sortedByDescending { it.lastEpisodeAddedAt ?: Long.MIN_VALUE }

            LibrarySort.Year -> found.sortedByDescending { it.year ?: Int.MIN_VALUE }

            LibrarySort.Rating -> found.sortedByDescending { it.communityRating ?: -1f }
        }
    return sorted.map { LibraryTile(it.id, it.title, it.artwork.poster) }
}
