package nl.jellycast.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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

data class HomeContent(
    val continueWatching: List<PlayableItem>,
    val nextUp: List<Episode>,
    val newMovies: List<Movie>,
    val movies: List<Movie>,
    val series: List<Series>,
)

sealed interface HomeLoad {
    data object Loading : HomeLoad

    data object Error : HomeLoad

    data class Success(val content: HomeContent) : HomeLoad
}

data class HomeUiState(
    val tab: HomeTab = HomeTab.Home,
    val query: String = "",
    val load: HomeLoad = HomeLoad.Loading,
) {
    val movies: List<Movie>
        get() = (load as? HomeLoad.Success)?.content?.movies.orEmpty()
            .filter { matchesIgnoringCaseAndAccents(it.title, query) }

    val series: List<Series>
        get() = (load as? HomeLoad.Success)?.content?.series.orEmpty()
            .filter { matchesIgnoringCaseAndAccents(it.title, query) }
}

class HomeViewModel(private val library: LibraryRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onTabSelect(tab: HomeTab) {
        _uiState.update { it.copy(tab = tab, query = "") }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    /** Something may have been watched in the player or on another device. */
    fun onShown() {
        if (_uiState.value.load is HomeLoad.Success) load()
    }

    fun onRetry() {
        _uiState.update { it.copy(load = HomeLoad.Loading) }
        load()
    }

    private fun load() {
        loadJob?.cancel()
        loadJob =
            viewModelScope.launch {
                val resume = async { library.getResumeMovies(ROW_LIMIT) }
                val nextUp = async { library.getNextUp() }
                val latest = async { library.getLatestUnwatchedMovies(ROW_LIMIT) }
                val movies = async { library.getMovies() }
                val series = async { library.getSeries() }
                val content =
                    combine(
                        resume.await(),
                        nextUp.await(),
                        latest.await(),
                        movies.await(),
                        series.await(),
                    )
                // A failed refresh keeps what is already shown.
                _uiState.update {
                    when {
                        content != null -> it.copy(load = HomeLoad.Success(content))
                        it.load is HomeLoad.Success -> it
                        else -> it.copy(load = HomeLoad.Error)
                    }
                }
            }
    }

    private fun combine(
        resume: Result<List<Movie>>,
        nextUp: Result<List<Episode>>,
        latest: Result<List<Movie>>,
        movies: Result<List<Movie>>,
        series: Result<List<Series>>,
    ): HomeContent? {
        val episodes = nextUp.getOrNull() ?: return null
        val resumeMovies = resume.getOrNull() ?: return null
        return HomeContent(
            continueWatching = continueWatching(resumeMovies, episodes),
            nextUp = episodes.filter { it.resumePositionMs == 0L },
            newMovies = latest.getOrNull().orEmpty(),
            movies = movies.getOrNull() ?: return null,
            series = series.getOrNull() ?: return null,
        )
    }
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
