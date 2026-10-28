package nl.jellycast.mobile.core.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * What lives on this phone only: the paired TVs, and the names the user gave devices ("Living
 * room"); the TV apps only know names like "chromecast" or "LG webOS TV".
 */
data class SavedRemoteDevices(
    val paired: List<PairedTv> = emptyList(),
    val names: Map<String, String> = emptyMap(),
)

interface RemoteDeviceStore {
    suspend fun load(): SavedRemoteDevices

    suspend fun save(devices: SavedRemoteDevices)
}

class DataStoreRemoteDeviceStore(private val dataStore: DataStore<Preferences>) :
    RemoteDeviceStore {
    override suspend fun load(): SavedRemoteDevices {
        val preferences = dataStore.data.first()
        return SavedRemoteDevices(
            paired = preferences[PairedKey]?.decodeOr(emptyList()) ?: emptyList(),
            names = preferences[NamesKey]?.decodeOr(emptyMap()) ?: emptyMap(),
        )
    }

    override suspend fun save(devices: SavedRemoteDevices) {
        dataStore.edit {
            it[PairedKey] = Json.encodeToString(devices.paired)
            it[NamesKey] = Json.encodeToString(devices.names)
        }
    }

    private inline fun <reified T> String.decodeOr(fallback: T): T = try {
        Json.decodeFromString<T>(this)
    } catch (_: SerializationException) {
        fallback
    } catch (_: IllegalArgumentException) {
        fallback
    }

    private companion object {
        val PairedKey = stringPreferencesKey("pairedAndroidTvs")
        val NamesKey = stringPreferencesKey("deviceNames")
    }
}
