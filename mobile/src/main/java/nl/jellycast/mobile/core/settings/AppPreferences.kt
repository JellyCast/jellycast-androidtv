package nl.jellycast.mobile.core.settings

import kotlinx.coroutines.flow.StateFlow
import nl.jellycast.mobile.core.network.NetworkKind

/** The phone app's own settings; the playback engine's settings stay in `PlaybackSettingsStore`. */
interface AppPreferences {
    val ratingSource: StateFlow<RatingSource>

    /** The streaming quality per network kind (Settings → Streaming quality). */
    val quality: StateFlow<Map<NetworkKind, QualityPreference>>

    suspend fun setRatingSource(source: RatingSource)

    suspend fun setQuality(network: NetworkKind, preference: QualityPreference)
}

fun Map<NetworkKind, QualityPreference>.forNetwork(network: NetworkKind): QualityPreference =
    this[network] ?: QualityPreference.defaultFor(network)
