package nl.jellycast.tv.feature.movies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.jellycast.tv.core.common.HomeSectionLeaves
import nl.jellycast.tv.core.common.LOAD_TIMEOUT_MS
import nl.jellycast.tv.core.data.repository.LibraryRepository
import nl.jellycast.tv.core.data.repository.UiPreferencesRepository
import nl.jellycast.tv.core.model.GenreNames
import nl.jellycast.tv.core.model.HomeSection
import nl.jellycast.tv.core.model.Movie
import nl.jellycast.tv.core.model.MovieView
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.PlaybackTech
import nl.jellycast.tv.core.model.Shelf
import nl.jellycast.tv.core.model.buildShelves
import nl.jellycast.tv.core.playback.PlaybackTechProvider

sealed interface MoviesUiState {
    data class Loading(val view: MovieView = MovieView.Detail) : MoviesUiState

    data object Error : MoviesUiState

    data class Success(
        val shelves: List<Shelf<Movie>>,
        val shelfIndex: Int = 0,
        val playRequest: PlayRequest? = null,
        val view: MovieView = MovieView.Detail,
        val tech: Map<String, PlaybackTech> = emptyMap(),
    ) : MoviesUiState {
        val shelf: Shelf<Movie> get() = shelves[shelfIndex]
    }
}

class MoviesViewModel(
    private val library: LibraryRepository,
    uiPreferences: UiPreferencesRepository,
    private val techProvider: PlaybackTechProvider,
    private val genreNames: GenreNames,
    sectionLeaves: HomeSectionLeaves,
) : ViewModel() {
    private val _uiState = MutableStateFlow<MoviesUiState>(MoviesUiState.Loading())
    val uiState: StateFlow<MoviesUiState> = _uiState.asStateFlow()

    private var loadJob: Job? = null
    private var movies: List<Movie> = emptyList()
    private var view = MovieView.Detail
    private val techRequested = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            uiPreferences.getPreferencesStream().collect { preferences ->
                view = preferences.movieView
                _uiState.update {
                    when (it) {
                        is MoviesUiState.Loading -> MoviesUiState.Loading(view)
                        is MoviesUiState.Success -> it.copy(view = view)
                        MoviesUiState.Error -> it
                    }
                }
            }
        }
        viewModelScope.launch {
            sectionLeaves.leavesOf(HomeSection.Movies).collect {
                library.advanceBackdrops(movies.map { it.id })
            }
        }
        viewModelScope.launch {
            library.backdropChanges.drop(1).collect {
                if (movies.isEmpty()) return@collect
                movies = movies.map(library::withBackdrop)
                showShelves(buildMovieShelves(movies, genreNames))
            }
        }
    }

    // Progress may have changed while watching; the current shelves stay until the new ones load.
    fun onShown() {
        load()
    }

    fun onRetry() {
        _uiState.value = MoviesUiState.Loading(view)
        load()
    }

    fun onShelfChange(index: Int) {
        updateSuccess { it.copy(shelfIndex = index.coerceIn(0, it.shelves.lastIndex)) }
    }

    fun onPlayClick(movie: Movie) {
        updateSuccess { it.copy(playRequest = PlayRequest(movie.id, movie.resumePositionMs)) }
    }

    fun onPlayFromStart(movie: Movie) {
        updateSuccess { it.copy(playRequest = PlayRequest(movie.id, 0)) }
    }

    fun onToggleWatched(movie: Movie) {
        onSetWatched(movie, played = !movie.isPlayed)
    }

    fun onSetWatched(movie: Movie, played: Boolean) {
        replaceMovie(movie.copy(isPlayed = played, resumePositionMs = 0))
        viewModelScope.launch {
            if (library.setPlayed(movie.id, played).isFailure) replaceMovie(movie)
        }
    }

    private fun replaceMovie(movie: Movie) {
        movies = movies.map { if (it.id == movie.id) movie else it }
        showShelves(buildMovieShelves(movies, genreNames))
    }

    fun onMovieSettled(movie: Movie) {
        // Play would start this movie: spin up the server's disk ahead of time.
        viewModelScope.launch { library.warmUpMedia(movie.id) }
        if (!techRequested.add(movie.id)) return
        viewModelScope.launch {
            val tech = techProvider.techFor(movie.id)
            if (tech == null) {
                techRequested.remove(movie.id)
                return@launch
            }
            updateSuccess { it.copy(tech = it.tech + (movie.id to tech)) }
        }
    }

    fun onPlayHandled() {
        updateSuccess { it.copy(playRequest = null) }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        loadJob =
            viewModelScope.launch {
                val loaded = withTimeoutOrNull(LOAD_TIMEOUT_MS) { library.getMovies().getOrNull() }
                if (loaded == null) {
                    _uiState.update { if (it is MoviesUiState.Success) it else MoviesUiState.Error }
                    return@launch
                }
                movies = loaded
                showShelves(buildMovieShelves(movies, genreNames))
            }
    }

    private fun showShelves(shelves: List<Shelf<Movie>>) {
        _uiState.update { current ->
            val old = current as? MoviesUiState.Success
            if (shelves.isEmpty()) return@update MoviesUiState.Error
            MoviesUiState.Success(
                shelves = shelves,
                shelfIndex = old?.let { sameOrReplacingShelfIndex(it, shelves) } ?: 0,
                playRequest = old?.playRequest,
                view = view,
                tech = old?.tech.orEmpty(),
            )
        }
    }

    private fun sameOrReplacingShelfIndex(
        old: MoviesUiState.Success,
        shelves: List<Shelf<Movie>>,
    ): Int = shelves.indexOfFirst { it.kind == old.shelf.kind }.takeIf { it >= 0 }
        ?: old.shelfIndex.coerceAtMost(shelves.lastIndex)

    private fun updateSuccess(transform: (MoviesUiState.Success) -> MoviesUiState.Success) {
        _uiState.update { if (it is MoviesUiState.Success) transform(it) else it }
    }
}

internal fun buildMovieShelves(movies: List<Movie>, genreNames: GenreNames): List<Shelf<Movie>> =
    buildShelves(
        continueWatching =
            movies.filter { it.resumePositionMs > 0 }.sortedByDescending { it.lastPlayedAt ?: 0 },
        new = movies.filter { !it.isPlayed }.sortedByDescending { it.addedAt ?: Long.MIN_VALUE },
        all = movies,
        genreNames = genreNames,
        genresOf = { it.genres },
    )
