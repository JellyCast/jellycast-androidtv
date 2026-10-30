package nl.jellycast.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The usage choice, the install ID it allows, and the last placements from the service. */
class ServiceStore(private val dataStore: DataStore<Preferences>) {
    data class Consent(val choice: String?, val version: Int)

    val consent: Flow<Consent> =
        dataStore.data.map { Consent(it[CHOICE], it[CONSENT_VERSION] ?: 0) }

    data class StoredPlacements(val json: String, val receivedAtMs: Long)

    val placements: Flow<StoredPlacements?> =
        dataStore.data.map { prefs ->
            prefs[PLACEMENTS]?.let { StoredPlacements(it, prefs[PLACEMENTS_RECEIVED_AT] ?: 0) }
        }

    suspend fun installId(): String? = dataStore.data.first()[INSTALL_ID]

    suspend fun saveConsent(choice: String, version: Int, decidedAtMs: Long, installId: String?) {
        dataStore.edit {
            it[CHOICE] = choice
            it[CONSENT_VERSION] = version
            it[DECIDED_AT] = decidedAtMs
            if (installId == null) it.remove(INSTALL_ID) else it[INSTALL_ID] = installId
        }
    }

    suspend fun savePlacements(json: String, receivedAtMs: Long) {
        dataStore.edit {
            it[PLACEMENTS] = json
            it[PLACEMENTS_RECEIVED_AT] = receivedAtMs
        }
    }

    private companion object {
        val CHOICE = stringPreferencesKey("usage_choice")
        val CONSENT_VERSION = intPreferencesKey("usage_consent_version")
        val DECIDED_AT = longPreferencesKey("usage_decided_at")
        val INSTALL_ID = stringPreferencesKey("usage_install_id")
        val PLACEMENTS = stringPreferencesKey("ads_placements")
        val PLACEMENTS_RECEIVED_AT = longPreferencesKey("ads_placements_received_at")
    }
}
