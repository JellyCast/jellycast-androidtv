package nl.jellycast.mobile.feature.person

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.DetailRepository
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore

/** A movie or series in the person's filmography that is in the library. */
sealed interface PersonEntry {
    val id: String

    data class MovieEntry(val movie: Movie) : PersonEntry {
        override val id: String get() = movie.id
    }

    data class SeriesEntry(val series: Series) : PersonEntry {
        override val id: String get() = series.id
    }
}

sealed interface PersonUiState {
    data object Loading : PersonUiState

    data class Error(val message: String?) : PersonUiState

    /** Movies first, then series, both in library order (by sort name). */
    data class Ready(val entries: List<PersonEntry>) : PersonUiState
}

private sealed interface IdsLoad {
    data object Loading : IdsLoad

    data class Failed(val message: String?) : IdsLoad

    data class Loaded(val ids: Set<String>) : IdsLoad
}

class PersonViewModel(
    private val personId: String,
    store: LibraryStore,
    private val details: DetailRepository,
) : ViewModel() {
    private val load = MutableStateFlow<IdsLoad>(IdsLoad.Loading)

    val uiState: StateFlow<PersonUiState> =
        combine(store.snapshot, load, ::stateOf)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonUiState.Loading)

    init {
        onRetry()
    }

    fun onRetry() {
        load.value = IdsLoad.Loading
        viewModelScope.launch {
            load.value =
                details.getItemIdsWithPerson(personId).fold(
                    onSuccess = { IdsLoad.Loaded(it.toSet()) },
                    onFailure = { IdsLoad.Failed(it.message) },
                )
        }
    }

    private fun stateOf(snapshot: LibrarySnapshot?, load: IdsLoad): PersonUiState = when (load) {
        IdsLoad.Loading -> PersonUiState.Loading

        is IdsLoad.Failed -> PersonUiState.Error(load.message)

        is IdsLoad.Loaded -> {
            if (snapshot == null) {
                PersonUiState.Loading
            } else {
                val movies = snapshot.movies.filter {
                    it.id in load.ids
                }.map(PersonEntry::MovieEntry)
                val series = snapshot.series.filter {
                    it.id in load.ids
                }.map(PersonEntry::SeriesEntry)
                PersonUiState.Ready(movies + series)
            }
        }
    }
}
