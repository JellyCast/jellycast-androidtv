package nl.jellycast.mobile.feature.player

import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.playback.PlaybackReporter

/** The server reports of a play, behind an interface so the ViewModel tests can record them. */
interface PlaybackReports {
    fun start(plan: PlaybackPlan, positionMs: Long)

    fun progress(plan: PlaybackPlan, positionMs: Long, isPaused: Boolean, streams: StreamChoice?)

    fun stop(plan: PlaybackPlan, positionMs: Long, failed: Boolean, finished: Boolean)
}

class ServerPlaybackReports(private val reporter: PlaybackReporter) : PlaybackReports {
    override fun start(plan: PlaybackPlan, positionMs: Long) = reporter.start(plan, positionMs)

    override fun progress(
        plan: PlaybackPlan,
        positionMs: Long,
        isPaused: Boolean,
        streams: StreamChoice?,
    ) {
        if (streams == null) {
            reporter.progress(plan, positionMs, isPaused)
        } else {
            reporter.progress(plan, positionMs, isPaused, streams)
        }
    }

    override fun stop(plan: PlaybackPlan, positionMs: Long, failed: Boolean, finished: Boolean) =
        reporter.stop(plan, positionMs, failed = failed, finished = finished)
}
