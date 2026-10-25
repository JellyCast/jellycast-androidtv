package nl.jellycast.tv.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import nl.jellycast.tv.core.model.LibraryKind
import nl.jellycast.tv.core.model.LibrarySort
import nl.jellycast.tv.core.model.MovieView
import nl.jellycast.tv.core.model.UiPreferences

class UiPreferencesStore(private val dataStore: DataStore<Preferences>) {
    val preferences: Flow<UiPreferences> =
        dataStore.data.map { prefs ->
            UiPreferences(
                movieView = prefs[MOVIE_VIEW].migrateMovieView().toEnum(MovieView.Detail),
                libraryKind = prefs[LIBRARY_KIND].toEnum(LibraryKind.Movies),
                librarySort = prefs[LIBRARY_SORT].toEnum(LibrarySort.Title),
            )
        }

    suspend fun saveMovieView(view: MovieView) {
        dataStore.edit { it[MOVIE_VIEW] = view.name }
    }

    suspend fun saveLibraryKind(kind: LibraryKind) {
        dataStore.edit { it[LIBRARY_KIND] = kind.name }
    }

    suspend fun saveLibrarySort(sort: LibrarySort) {
        dataStore.edit { it[LIBRARY_SORT] = sort.name }
    }

    private companion object {
        val MOVIE_VIEW = stringPreferencesKey("movie_view")
        val LIBRARY_KIND = stringPreferencesKey("library_kind")
        val LIBRARY_SORT = stringPreferencesKey("library_sort")
    }
}

private inline fun <reified T : Enum<T>> String?.toEnum(default: T): T =
    enumValues<T>().firstOrNull { it.name == this } ?: default

private const val LEGACY_BOOKCASE_NAME = "Shelf"

private fun String?.migrateMovieView(): String? =
    if (this == LEGACY_BOOKCASE_NAME) MovieView.Bookcase.name else this
