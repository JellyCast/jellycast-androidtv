package nl.jellycast.mobile.feature.movie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.model.Movie
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore

sealed interface MovieUiState {
    /** The library hasn't loaded yet (first start). */
    data object Loading : MovieUiState

    data object Missing : MovieUiState

    data class Ready(val movie: Movie) : MovieUiState
}

class MovieViewModel(private val movieId: String, private val store: LibraryStore) : ViewModel() {
    val uiState: StateFlow<MovieUiState> =
        store.snapshot
            .map(::stateOf)
            .stateIn(
                viewModelScope,
                SharingStarted.WhileSubscribed(5_000),
                stateOf(store.snapshot.value),
            )

    private fun stateOf(snapshot: LibrarySnapshot?): MovieUiState {
        snapshot ?: return MovieUiState.Loading
        return snapshot.moviesById[movieId]?.let(MovieUiState::Ready) ?: MovieUiState.Missing
    }

    fun onToggleWatched() {
        val movie = (uiState.value as? MovieUiState.Ready)?.movie ?: return
        viewModelScope.launch { store.setPlayed(listOf(movie.id), !movie.isPlayed) }
    }

    fun onRetry() {
        viewModelScope.launch { store.refresh() }
    }
}
