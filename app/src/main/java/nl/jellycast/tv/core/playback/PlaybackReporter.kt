package nl.jellycast.tv.core.playback

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.model.PlayMethod
import nl.jellycast.tv.core.model.PlaybackPlan
import nl.jellycast.tv.core.model.StreamChoice
import nl.jellycast.tv.core.model.msToTicks
import nl.jellycast.tv.core.model.streamChoice
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.playStateApi
import org.jellyfin.sdk.model.api.PlayMethod as ApiPlayMethod
import org.jellyfin.sdk.model.api.PlaybackOrder
import org.jellyfin.sdk.model.api.PlaybackProgressInfo
import org.jellyfin.sdk.model.api.PlaybackStartInfo
import org.jellyfin.sdk.model.api.PlaybackStopInfo
import org.jellyfin.sdk.model.api.RepeatMode
import org.jellyfin.sdk.model.serializer.toUUID
import timber.log.Timber

/**
 * Reports go through one ordered queue on the app scope, so a stop is still sent after the screen
 * closes and never arrives before the last progress report. Failures are only logged.
 */
class PlaybackReporter(private val api: ApiClient, appScope: CoroutineScope) {
    private val queue = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val sessions = StoppedSessions()

    init {
        appScope.launch {
            for (report in queue) {
                try {
                    report()
                } catch (e: ApiClientException) {
                    Timber.w(e, "Playback report failed")
                }
            }
        }
    }

    fun start(plan: PlaybackPlan, positionMs: Long) = enqueue {
        api.playStateApi.reportPlaybackStart(
            PlaybackStartInfo(
                canSeek = true,
                itemId = plan.itemId.toUUID(),
                mediaSourceId = plan.mediaSourceId,
                audioStreamIndex = plan.audioStreamIndex,
                subtitleStreamIndex = plan.subtitleStreamIndex,
                isPaused = false,
                isMuted = false,
                positionTicks = positionMs.msToTicks(),
                playMethod = plan.playMethod.toApi(),
                playSessionId = plan.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    fun progress(
        plan: PlaybackPlan,
        positionMs: Long,
        isPaused: Boolean,
        streams: StreamChoice = plan.streamChoice(),
    ) {
        if (sessions.isStopped(plan)) return
        enqueue { reportProgress(plan, positionMs, isPaused, streams) }
    }

    private suspend fun reportProgress(
        plan: PlaybackPlan,
        positionMs: Long,
        isPaused: Boolean,
        streams: StreamChoice,
    ) {
        api.playStateApi.reportPlaybackProgress(
            PlaybackProgressInfo(
                canSeek = true,
                itemId = plan.itemId.toUUID(),
                mediaSourceId = plan.mediaSourceId,
                audioStreamIndex = streams.audioStreamIndex,
                subtitleStreamIndex = streams.subtitleStreamIndex,
                isPaused = isPaused,
                isMuted = false,
                positionTicks = positionMs.msToTicks(),
                playMethod = plan.playMethod.toApi(),
                playSessionId = plan.playSessionId,
                repeatMode = RepeatMode.REPEAT_NONE,
                playbackOrder = PlaybackOrder.DEFAULT,
            ),
        )
    }

    /**
     * [finished]: stopped in the credits, so the item is marked as played. That must come after the
     * stop report, which would otherwise restore the position.
     */
    fun stop(
        plan: PlaybackPlan,
        positionMs: Long,
        failed: Boolean = false,
        finished: Boolean = false,
    ) {
        sessions.stop(plan)
        enqueue { reportStop(plan, positionMs, failed) }
        if (finished && !failed) {
            enqueue { api.playStateApi.markPlayedItem(itemId = plan.itemId.toUUID()) }
        }
    }

    private suspend fun reportStop(plan: PlaybackPlan, positionMs: Long, failed: Boolean) {
        api.playStateApi.reportPlaybackStopped(
            PlaybackStopInfo(
                itemId = plan.itemId.toUUID(),
                mediaSourceId = plan.mediaSourceId,
                positionTicks = positionMs.msToTicks(),
                playSessionId = plan.playSessionId,
                failed = failed,
            ),
        )
    }

    private fun enqueue(report: suspend () -> Unit) {
        queue.trySend(report)
    }
}

/**
 * The player still reports a pause when released; progress after the stop makes the server think
 * something is playing and delays saving the position for about a minute. Compared by plan
 * identity, because `playSessionId` can be missing. Main thread only, like the player.
 */
internal class StoppedSessions {
    private val stopped = ArrayDeque<PlaybackPlan>()

    fun stop(plan: PlaybackPlan) {
        if (isStopped(plan)) return
        stopped.addLast(plan)
        if (stopped.size > REMEMBERED_STOPS) stopped.removeFirst()
    }

    fun isStopped(plan: PlaybackPlan): Boolean = stopped.any { it === plan }
}

private const val REMEMBERED_STOPS = 4

private fun PlayMethod.toApi(): ApiPlayMethod = when (this) {
    PlayMethod.DirectPlay -> ApiPlayMethod.DIRECT_PLAY
    PlayMethod.DirectStream -> ApiPlayMethod.DIRECT_STREAM
    PlayMethod.Transcode -> ApiPlayMethod.TRANSCODE
}
