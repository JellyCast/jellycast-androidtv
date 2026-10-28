package nl.jellycast.core.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import nl.jellycast.core.data.repository.UiPreferencesRepository
import nl.jellycast.core.model.LibraryKind
import nl.jellycast.core.model.LibrarySort
import nl.jellycast.core.model.MovieView
import nl.jellycast.core.model.UiPreferences

class FakeUiPreferencesRepository(initial: UiPreferences = UiPreferences()) :
    UiPreferencesRepository {
    val preferences = MutableStateFlow(initial)

    override fun getPreferencesStream(): Flow<UiPreferences> = preferences

    override suspend fun setMovieView(view: MovieView) {
        preferences.update { it.copy(movieView = view) }
    }

    override suspend fun setLibraryKind(kind: LibraryKind) {
        preferences.update { it.copy(libraryKind = kind) }
    }

    override suspend fun setLibrarySort(sort: LibrarySort) {
        preferences.update { it.copy(librarySort = sort) }
    }
}
