package nl.jellycast.mobile.core.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import nl.jellycast.mobile.core.network.NetworkKind

/** Owner: the settings worker. */
class DataStoreAppPreferences(private val store: DataStore<Preferences>, scope: CoroutineScope) :
    AppPreferences {
    override val ratingSource: StateFlow<RatingSource> =
        store.data
            .map { prefs ->
                prefs[RatingKey]?.let { name ->
                    RatingSource.entries.firstOrNull { it.name == name }
                }
                    ?: RatingSource.Imdb
            }
            .stateIn(scope, SharingStarted.Eagerly, RatingSource.Imdb)

    override val quality: StateFlow<Map<NetworkKind, QualityPreference>> =
        store.data
            .map { prefs ->
                QualityNetworks.associateWith { network ->
                    QualityPreference.parse(prefs[qualityKey(network)])
                        ?: QualityPreference.defaultFor(network)
                }
            }
            .stateIn(
                scope,
                SharingStarted.Eagerly,
                QualityNetworks.associateWith(QualityPreference::defaultFor),
            )

    override suspend fun setRatingSource(source: RatingSource) {
        store.edit { it[RatingKey] = source.name }
    }

    override suspend fun setQuality(network: NetworkKind, preference: QualityPreference) {
        store.edit { it[qualityKey(network)] = preference.stored }
    }
}

private val RatingKey = stringPreferencesKey("rating_source")

private val QualityNetworks = listOf(NetworkKind.Local, NetworkKind.Wifi, NetworkKind.Cellular)

private fun qualityKey(network: NetworkKind) =
    stringPreferencesKey("quality_${network.name.lowercase()}")
