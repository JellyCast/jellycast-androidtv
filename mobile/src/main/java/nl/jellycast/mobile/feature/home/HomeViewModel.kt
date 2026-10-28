package nl.jellycast.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.common.matchesIgnoringCaseAndAccents
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series

enum class HomeTab { Home, Movies, Series }

sealed interface Loadable<out T> {
    data object Loading : Loadable<Nothing>

    data object Failed : Loadable<Nothing>

    data class Ready<T>(val value: T) : Loadable<T>
}

val <T> Loadable<T>.valueOrNull: T?
    get() = (this as? Loadable.Ready)?.value

/**
 * Every part loads on its own, so the small rows show before the whole library has arrived (on a
 * phone network the full lists can take seconds).
 */
data class HomeUiState(
    val tab: HomeTab = HomeTab.Home,
    val query: String = "",
    val resumeMovies: Loadable<List<Movie>> = Loadable.Loading,
    val nextUp: Loadable<List<Episode>> = Loadable.Loading,
    val newMovies: Loadable<List<Movie>> = Loadable.Loading,
    val allMovies: Loadable<List<Movie>> = Loadable.Loading,
    val allSeries: Loadable<List<Series>> = Loadable.Loading,
) {
    private val rowParts: List<Loadable<*>>
        get() = listOf(resumeMovies, nextUp, newMovies)

    val rowsLoading: Boolean
        get() = rowParts.all { it == Loadable.Loading }

    val rowsFailed: Boolean
        get() = rowParts.all { it == Loadable.Failed }

    val continueWatching: List<PlayableItem>
        get() = continueWatching(resumeMovies.valueOrNull.orEmpty(), nextUp.valueOrNull.orEmpty())

    val nextEpisodes: List<Episode>
        get() = nextUp.valueOrNull.orEmpty().filter { it.resumePositionMs == 0L }

    val movies: Loadable<List<Movie>>
        get() = allMovies.filtered { matchesIgnoringCaseAndAccents(it.title, query) }

    val series: Loadable<List<Series>>
        get() = allSeries.filtered { matchesIgnoringCaseAndAccents(it.title, query) }
}

private fun <T> Loadable<List<T>>.filtered(keep: (T) -> Boolean): Loadable<List<T>> =
    if (this is Loadable.Ready) Loadable.Ready(value.filter(keep)) else this

class HomeViewModel(private val library: LibraryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadAll()
    }

    fun onTabSelect(tab: HomeTab) {
        _uiState.update { it.copy(tab = tab, query = "") }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Something may have been watched in the player or on another device. */
    fun onShown() {
        loadAll()
    }

    fun onRetry() {
        _uiState.update {
            it.copy(
                resumeMovies = it.resumeMovies.retrying(),
                nextUp = it.nextUp.retrying(),
                newMovies = it.newMovies.retrying(),
                allMovies = it.allMovies.retrying(),
                allSeries = it.allSeries.retrying(),
            )
        }
        loadAll()
    }

    private fun loadAll() {
        load({
            library.getResumeMovies(ROW_LIMIT)
        }) { state, part -> state.copy(resumeMovies = part(state.resumeMovies)) }
        load({
            library.getLatestUnwatchedMovies(ROW_LIMIT)
        }) { state, part -> state.copy(newMovies = part(state.newMovies)) }
        load({ library.getNextUp() }) { state, part -> state.copy(nextUp = part(state.nextUp)) }
        load({
            library.getMovies()
        }) { state, part -> state.copy(allMovies = part(state.allMovies)) }
        load({
            library.getSeries()
        }) { state, part -> state.copy(allSeries = part(state.allSeries)) }
    }

    /** A failed refresh keeps what is already shown. */
    private fun <T> load(
        fetch: suspend () -> Result<T>,
        apply: (HomeUiState, (Loadable<T>) -> Loadable<T>) -> HomeUiState,
    ) {
        viewModelScope.launch {
            val result = fetch()
            _uiState.update { state ->
                apply(state) { current ->
                    result.fold(
                        onSuccess = { Loadable.Ready(it) },
                        onFailure = { if (current is Loadable.Ready) current else Loadable.Failed },
                    )
                }
            }
        }
    }
}

private fun <T> Loadable<T>.retrying(): Loadable<T> = if (this ==
    Loadable.Failed
) {
    Loadable.Loading
} else {
    this
}

/** Half-watched movies and episodes, most recently touched first. */
internal fun continueWatching(movies: List<Movie>, nextUp: List<Episode>): List<PlayableItem> {
    val episodes = nextUp.filter { it.resumePositionMs > 0 }
    return (
        movies.map { PlayableItem.MovieItem(it) to (it.lastPlayedAt ?: 0L) } +
            episodes.map { PlayableItem.EpisodeItem(it) to (it.touchedAt ?: it.lastPlayedAt ?: 0L) }
        )
        .sortedByDescending { it.second }
        .map { it.first }
}

private const val ROW_LIMIT = 20
