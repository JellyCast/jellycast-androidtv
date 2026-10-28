package nl.jellycast.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.datastore.UiPreferencesStore
import nl.jellycast.core.model.LibraryKind
import nl.jellycast.core.model.LibrarySort
import nl.jellycast.core.model.MovieView
import nl.jellycast.core.model.UiPreferences

interface UiPreferencesRepository {
    fun getPreferencesStream(): Flow<UiPreferences>

    suspend fun setMovieView(view: MovieView)

    suspend fun setLibraryKind(kind: LibraryKind)

    suspend fun setLibrarySort(sort: LibrarySort)
}

class DefaultUiPreferencesRepository(
    private val store: UiPreferencesStore,
    private val dispatchers: AppDispatchers,
) : UiPreferencesRepository {
    override fun getPreferencesStream(): Flow<UiPreferences> = store.preferences

    override suspend fun setMovieView(view: MovieView) =
        withContext(dispatchers.io) { store.saveMovieView(view) }

    override suspend fun setLibraryKind(kind: LibraryKind) =
        withContext(dispatchers.io) { store.saveLibraryKind(kind) }

    override suspend fun setLibrarySort(sort: LibrarySort) =
        withContext(dispatchers.io) { store.saveLibrarySort(sort) }
}
