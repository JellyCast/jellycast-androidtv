package nl.jellycast.mobile.feature.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.model.Movie

data class MovieDetailUiState(val movie: Movie? = null, val failed: Boolean = false)

class MovieDetailViewModel(private val movieId: String, private val library: LibraryRepository) :
    ViewModel() {
    private val _uiState = MutableStateFlow(MovieDetailUiState(movie = library.peekMovie(movieId)))
    val uiState: StateFlow<MovieDetailUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun onShown() {
        load()
    }

    fun onRetry() {
        _uiState.update { it.copy(failed = false) }
        load()
    }

    fun onWatchedClick() {
        val movie = _uiState.value.movie ?: return
        val played = !movie.isPlayed
        _uiState.update {
            it.copy(movie = movie.copy(isPlayed = played, resumePositionMs = 0))
        }
        viewModelScope.launch {
            library.setPlayed(movie.id, played).onFailure {
                _uiState.update { it.copy(movie = movie) }
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            library.getMovie(movieId)
                .onSuccess { movie -> _uiState.update { it.copy(movie = movie, failed = false) } }
                .onFailure { _uiState.update { it.copy(failed = it.movie == null) } }
        }
    }
}
