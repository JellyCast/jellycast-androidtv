package nl.jellycast.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Only `core/data` uses this, so the token never reaches the UI. */
data class StoredSession(
    val serverUrl: String? = null,
    val serverName: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val accessToken: String? = null,
    /** The user's subtitle language first, then the audio language; codes as the server has them. */
    val preferredLanguages: List<String> = emptyList(),
)

class SessionStore(private val dataStore: DataStore<Preferences>) {
    val session: Flow<StoredSession> =
        dataStore.data.map { prefs ->
            StoredSession(
                serverUrl = prefs[SERVER_URL],
                serverName = prefs[SERVER_NAME],
                userId = prefs[USER_ID],
                userName = prefs[USER_NAME],
                accessToken = prefs[ACCESS_TOKEN],
                preferredLanguages =
                    prefs[PREFERRED_LANGUAGES]?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            )
        }

    // A token belongs to one server, so switching servers clears the login.
    suspend fun saveServer(url: String, name: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = url
            prefs[SERVER_NAME] = name
            prefs.removeLogin()
        }
    }

    suspend fun saveLogin(
        userId: String,
        userName: String,
        accessToken: String,
        preferredLanguages: List<String>,
    ) {
        dataStore.edit { prefs ->
            prefs[USER_ID] = userId
            prefs[USER_NAME] = userName
            prefs[ACCESS_TOKEN] = accessToken
            prefs[PREFERRED_LANGUAGES] = preferredLanguages.joinToString(",")
        }
    }

    suspend fun saveUser(userName: String, preferredLanguages: List<String>) {
        dataStore.edit { prefs ->
            prefs[USER_NAME] = userName
            prefs[PREFERRED_LANGUAGES] = preferredLanguages.joinToString(",")
        }
    }

    suspend fun clearLogin() {
        dataStore.edit { prefs -> prefs.removeLogin() }
    }

    private fun MutablePreferences.removeLogin() {
        remove(USER_ID)
        remove(USER_NAME)
        remove(ACCESS_TOKEN)
        remove(PREFERRED_LANGUAGES)
    }

    private companion object {
        val SERVER_URL = stringPreferencesKey("server_url")
        val SERVER_NAME = stringPreferencesKey("server_name")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val PREFERRED_LANGUAGES = stringPreferencesKey("preferred_languages")
    }
}
