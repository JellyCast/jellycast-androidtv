package nl.jellycast.mobile.feature.player

import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.playback.PlaybackFailure

/** What the engine reports about a load; every server report starts here. */
interface EngineCallbacks {
    fun onEngineStarted(load: PlaybackLoad)

    fun onProgress(load: PlaybackLoad, positionMs: Long, isPaused: Boolean, streams: StreamChoice?)

    /** Called once per load when its engine is released, whatever the reason. */
    fun onEngineStopped(
        load: PlaybackLoad,
        positionMs: Long,
        durationMs: Long,
        ended: Boolean,
        failed: Boolean,
    )

    fun onEnded(load: PlaybackLoad)

    fun onPlaybackStable()

    /** [beforeFirstFrame]: the file never showed a picture, so it "doesn't play here". */
    fun onPlaybackError(
        load: PlaybackLoad,
        failure: PlaybackFailure,
        positionMs: Long,
        beforeFirstFrame: Boolean,
    )
}

/** What the player UI asks of the play. */
interface PlayerActions {
    fun onMinimize()

    fun onExpand()

    fun onClose()

    fun onMiniPlayerSizeChange(large: Boolean)

    fun onPlayNext()

    fun onRetry()

    fun onPlayViaServer()

    fun onQualityChosen(choice: QualityChoice, positionMs: Long, streams: StreamChoice?)

    fun onTranscodeStreamsChosen(streams: StreamChoice, positionMs: Long)
}
