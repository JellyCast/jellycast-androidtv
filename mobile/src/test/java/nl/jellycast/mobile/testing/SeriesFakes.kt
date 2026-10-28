package nl.jellycast.mobile.testing

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.RatingSource

/** Connectivity for the series page tests: set [isOffline] directly. */
class FakeSeriesConnectivity(offline: Boolean = false) : Connectivity {
    override val networkKind = MutableStateFlow(if (offline) NetworkKind.None else NetworkKind.Wifi)
    override val isServerReachable = MutableStateFlow(!offline)
    override val isOffline = MutableStateFlow(offline)

    override suspend fun checkServer(): Boolean = isServerReachable.value
}

class FakeSeriesPreferences(source: RatingSource = RatingSource.Imdb) : AppPreferences {
    override val ratingSource = MutableStateFlow(source)
    override val quality = MutableStateFlow<Map<NetworkKind, QualityPreference>>(emptyMap())

    override suspend fun setRatingSource(source: RatingSource) {
        ratingSource.value = source
    }

    override suspend fun setQuality(network: NetworkKind, preference: QualityPreference) {
        quality.value += network to preference
    }
}
