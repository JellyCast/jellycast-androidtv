package nl.jellycast.mobile.feature.player

import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.mobile.core.settings.QualityLevel
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.navigation.PlayQueue

/** One plan given to the engine; [id] differs per load, so a retry of an equal plan restarts. */
data class PlaybackLoad(val id: Int, val plan: PlaybackPlan)

enum class PlayerError {
    /** The item has no media source. */
    NoSource,

    /** The engine or decoder refused the file before it played. */
    DoesNotPlay,

    /** An engine error after the start. */
    StoppedMidPlay,

    /** The server did not answer. */
    Server,
}

sealed interface PlayerPhase {
    data object Preparing : PlayerPhase

    data class Playing(val load: PlaybackLoad) : PlayerPhase

    /** [viaServer] is the level "Play via the server" offers, when the original failed. */
    data class Failed(val error: PlayerError, val viaServer: QualityLevel?) : PlayerPhase
}

data class QualityState(
    /** What this play uses: the network's setting until one is picked in the player. */
    val preference: QualityPreference,
    /** What the preference resolved to for this item. */
    val level: QualityLevel,
    val offered: List<QualityLevel>,
    val sourceBitrate: Long?,
) {
    val isAuto: Boolean get() = preference is QualityPreference.Auto

    /** The highest offered level below the one playing, for "Play via the server" and hints. */
    val lowerLevel: QualityLevel?
        get() = offered.firstOrNull { it.raw < level.raw }
}

data class NowPlayingUiState(
    /** `null` when nothing plays: no mini player, no full player. */
    val item: PlayableItem? = null,
    val queue: PlayQueue? = null,
    val phase: PlayerPhase = PlayerPhase.Preparing,
    val isExpanded: Boolean = false,
    val isMiniPlayerLarge: Boolean = false,
    val segments: List<MediaSegment> = emptyList(),
    val nextItem: PlayableItem? = null,
    val quality: QualityState? = null,
) {
    val load: PlaybackLoad? get() = (phase as? PlayerPhase.Playing)?.load
}
