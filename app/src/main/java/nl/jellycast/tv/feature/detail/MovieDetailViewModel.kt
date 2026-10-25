package nl.jellycast.tv.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import nl.jellycast.tv.core.common.LOAD_TIMEOUT_MS
import nl.jellycast.tv.core.data.repository.LibraryRepository
import nl.jellycast.tv.core.model.Movie
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.PlaybackTech
import nl.jellycast.tv.core.playback.PlaybackTechProvider

sealed interface MovieDetailUiState {
    data object Loading : MovieDetailUiState

    data object Error : MovieDetailUiState

    data class Success(
        val movie: Movie,
        val tech: PlaybackTech? = null,
        val playRequest: PlayRequest? = null,
    ) : MovieDetailUiState
}

class MovieDetailViewModel(
    private val movieId: String,
    private val library: LibraryRepository,
    private val techProvider: PlaybackTechProvider,
) : ViewModel() {
    private val _uiState =
        MutableStateFlow(
            library.peekMovie(movieId)?.let { MovieDetailUiState.Success(it) }
                ?: MovieDetailUiState.Loading,
        )
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        // Play is one press away: spin up the server's disk ahead of time.
        viewModelScope.launch { library.warmUpMedia(movieId) }
    }

    private var loadJob: Job? = null
    private var techLoaded = false

    // Also called when coming back from the player, so progress is refreshed.
    fun onShown() {
        load()
    }

    fun onRetry() {
        _uiState.value = MovieDetailUiState.Loading
        load()
    }

    fun onPlay() {
        updateSuccess { it.copy(playRequest = PlayRequest(movieId, it.movie.resumePositionMs)) }
    }

    fun onPlayFromStart() {
        updateSuccess { it.copy(playRequest = PlayRequest(movieId, 0)) }
    }

    fun onPlayHandled() {
        updateSuccess { it.copy(playRequest = null) }
    }

    fun onToggleWatched() {
        val current = (_uiState.value as? MovieDetailUiState.Success)?.movie ?: return
        val played = !current.isPlayed
        updateSuccess { it.copy(movie = it.movie.copy(isPlayed = played, resumePositionMs = 0)) }
        viewModelScope.launch {
            if (library.setPlayed(movieId, played).isFailure) {
                updateSuccess { it.copy(movie = current) }
            }
        }
    }

    private fun load() {
        if (loadJob?.isActive == true) return
        loadJob =
            viewModelScope.launch {
                val movie =
                    withTimeoutOrNull(LOAD_TIMEOUT_MS) { library.getMovie(movieId).getOrNull() }
                _uiState.update { current ->
                    when {
                        movie != null ->
                            (current as? MovieDetailUiState.Success)?.copy(movie = movie)
                                ?: MovieDetailUiState.Success(movie)

                        current is MovieDetailUiState.Success -> current

                        else -> MovieDetailUiState.Error
                    }
                }
                if (movie != null && !techLoaded) {
                    techLoaded = true
                    val tech = techProvider.techFor(movieId)
                    updateSuccess { it.copy(tech = tech) }
                }
            }
    }

    private fun updateSuccess(
        transform: (MovieDetailUiState.Success) -> MovieDetailUiState.Success,
    ) {
        _uiState.update { if (it is MovieDetailUiState.Success) transform(it) else it }
    }
}
