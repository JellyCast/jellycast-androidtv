package nl.jellycast.mobile.feature.library

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** The last few terms a search led somewhere with, most recent first. */
interface RecentSearchStore {
    val terms: Flow<List<String>>

    suspend fun save(terms: List<String>)
}

class DataStoreRecentSearches(private val dataStore: DataStore<Preferences>) : RecentSearchStore {
    override val terms: Flow<List<String>> =
        dataStore.data.map { prefs ->
            prefs[TERMS].orEmpty().split(SEPARATOR).filter { it.isNotEmpty() }
        }

    override suspend fun save(terms: List<String>) {
        dataStore.edit { it[TERMS] = terms.joinToString(SEPARATOR) }
    }
}

/** [term] first, without an earlier copy in other case, at most [RECENT_LIMIT]. */
fun remembered(terms: List<String>, term: String): List<String> =
    (listOf(term) + terms.filterNot { it.equals(term, ignoreCase = true) }).take(RECENT_LIMIT)

private val TERMS = stringPreferencesKey("recent_searches")

// A search term comes from a one-line field, so it never holds a newline.
private const val SEPARATOR = "\n"
private const val RECENT_LIMIT = 5
