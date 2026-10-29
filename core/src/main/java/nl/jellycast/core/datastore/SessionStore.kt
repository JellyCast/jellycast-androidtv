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
    val serverId: String? = null,
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
                serverId = prefs[SERVER_ID],
                userId = prefs[USER_ID],
                userName = prefs[USER_NAME],
                accessToken = prefs[ACCESS_TOKEN],
                preferredLanguages =
                    prefs[PREFERRED_LANGUAGES]?.split(',')?.filter { it.isNotBlank() }.orEmpty(),
            )
        }

    // A token belongs to one server, so switching servers clears the login.
    suspend fun saveServer(url: String, name: String, id: String) {
        dataStore.edit { prefs ->
            prefs[SERVER_URL] = url
            prefs[SERVER_NAME] = name
            if (id.isEmpty()) prefs.remove(SERVER_ID) else prefs[SERVER_ID] = id
            prefs.removeLogin()
        }
    }

    /** Another address of the same server; the sign-in stays, a token belongs to the server. */
    suspend fun saveServerAddress(url: String) {
        dataStore.edit { prefs -> prefs[SERVER_URL] = url }
    }

    suspend fun saveServerId(id: String) {
        dataStore.edit { prefs -> prefs[SERVER_ID] = id }
    }

    /**
     * The addresses a server answers on, in the order the user prefers them. Kept per server id,
     * so they outlive a sign-out.
     */
    fun addresses(serverId: String): Flow<List<String>> = dataStore.data.map { prefs ->
        prefs[addressesKey(serverId)]?.split('\n')?.filter { it.isNotBlank() }.orEmpty()
    }

    suspend fun saveAddresses(serverId: String, addresses: List<String>) {
        dataStore.edit { prefs -> prefs[addressesKey(serverId)] = addresses.joinToString("\n") }
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
        val SERVER_ID = stringPreferencesKey("server_id")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_NAME = stringPreferencesKey("user_name")
        val ACCESS_TOKEN = stringPreferencesKey("access_token")
        val PREFERRED_LANGUAGES = stringPreferencesKey("preferred_languages")

        fun addressesKey(serverId: String) = stringPreferencesKey("server_addresses_$serverId")
    }
}
