package nl.jellycast.mobile.testing

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.core.data.repository.MediaSourceRepository
import nl.jellycast.core.data.repository.MediaSourceSummary
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.AppPreferences
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.feature.player.PlaybackReports

class PlayerTestPreferences(quality: Map<NetworkKind, QualityPreference> = emptyMap()) :
    AppPreferences {
    override val ratingSource = MutableStateFlow(RatingSource.entries.first())
    override val quality = MutableStateFlow(quality)

    override suspend fun setRatingSource(source: RatingSource) {
        ratingSource.value = source
    }

    override suspend fun setQuality(network: NetworkKind, preference: QualityPreference) {
        quality.value += network to preference
    }
}

class PlayerTestConnectivity(kind: NetworkKind = NetworkKind.Wifi) : Connectivity {
    override val networkKind = MutableStateFlow(kind)
    override val isServerReachable = MutableStateFlow(true)
    override val isOffline = MutableStateFlow(false)

    override suspend fun checkServer(): Boolean = true
}

class PlayerTestSources(var summary: MediaSourceSummary? = null) : MediaSourceRepository {
    override suspend fun getSourceSummary(itemId: String): MediaSourceSummary? = summary
}

class RecordingPlaybackReports : PlaybackReports {
    val started = mutableListOf<String>()
    val stops = mutableListOf<Stop>()

    data class Stop(
        val itemId: String,
        val positionMs: Long,
        val failed: Boolean,
        val finished: Boolean,
    )

    override fun start(plan: PlaybackPlan, positionMs: Long) {
        started += plan.itemId
    }

    override fun progress(
        plan: PlaybackPlan,
        positionMs: Long,
        isPaused: Boolean,
        streams: StreamChoice?,
    ) = Unit

    override fun stop(plan: PlaybackPlan, positionMs: Long, failed: Boolean, finished: Boolean) {
        stops += Stop(plan.itemId, positionMs, failed, finished)
    }
}
