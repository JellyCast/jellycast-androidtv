package nl.jellycast.tv.feature.player

import android.os.SystemClock
import android.view.View
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackReporter
import nl.jellycast.core.playback.PlaybackStartLog
import nl.jellycast.core.playback.applyTrackChoice
import nl.jellycast.core.playback.chosenStreams
import nl.jellycast.core.playback.classifyPlaybackFailure
import nl.jellycast.core.playback.videoWithoutDecoder
import timber.log.Timber

@OptIn(UnstableApi::class)
internal class SessionListener(
    private val exoPlayer: ExoPlayer,
    private val plan: PlaybackPlan,
    private val state: PlayerSessionState,
    private val view: View,
    private val reporter: PlaybackReporter,
    private val startLog: PlaybackStartLog,
    private val reportError: (
        message: String,
        positionMs: Long,
        failure: PlaybackFailure,
        afterSwitch: Boolean,
    ) -> Unit,
    private val switchedAtMs: () -> Long?,
    private val onEnded: () -> Unit,
) : Player.Listener {
    private var tracksApplied = false
    private var playableChecked = false

    /**
     * Runs once when playback really starts. Media3 plays audio only, without an error, when the
     * video track has no decoder (Moonfin mf#1510). An unseekable direct-play file (MKV with the
     * cues in a second SeekHead; jellyfin-androidtv #5221) breaks seeking and resume; HLS from a
     * server remux is seekable.
     */
    private fun checkPlayable() {
        if (playableChecked) return
        playableChecked = true
        val position = exoPlayer.currentPosition
        // The messages passed to reportError are shown on the error screen when no fallback is left.
        when {
            exoPlayer.currentTracks.videoWithoutDecoder(exoPlayer) -> {
                startLog.log("No decoder for the video track")
                reportError("No video decoder", position, PlaybackFailure.Video, false)
            }

            plan.playMethod == PlayMethod.DirectPlay && !exoPlayer.isCurrentMediaItemSeekable -> {
                startLog.log("Not seekable; remux")
                reportError(
                    "Not seekable",
                    maxOf(position, plan.startPositionMs),
                    PlaybackFailure.Source,
                    false,
                )
            }
        }
    }

    override fun onTracksChanged(tracks: Tracks) {
        if (!tracksApplied && !tracks.isEmpty) {
            tracksApplied = true
            exoPlayer.applyTrackChoice(plan, tracks)
            state.tracksChosen = true
        } else if (tracksApplied) {
            tracks.chosenStreams(plan)?.let { state.chooseStreams(plan, it) }
        }
        state.tracks = tracks
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        state.isPlaying = isPlaying
        if (isPlaying) checkPlayable()
        view.keepScreenOn = isPlaying
        reporter.progress(
            plan,
            exoPlayer.currentPosition,
            isPaused = !isPlaying,
            streams = state.streamsFor(plan),
        )
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) onEnded()
    }

    override fun onPlayerError(error: PlaybackException) {
        Timber.e(error, "Playback error (%s)", error.errorCodeName)
        reporter.stop(plan, exoPlayer.currentPosition, failed = true)
        val exo = error as? ExoPlaybackException
        val failure =
            classifyPlaybackFailure(
                errorCode = error.errorCode,
                isSource = exo?.type == ExoPlaybackException.TYPE_SOURCE,
                isRenderer = exo?.type == ExoPlaybackException.TYPE_RENDERER,
                rendererMimeType = exo?.rendererFormat?.sampleMimeType,
            )
        val afterSwitch =
            switchedAtMs()?.let {
                SystemClock.elapsedRealtime() - it < AFTER_SWITCH_WINDOW_MS
            } == true
        reportError(
            "${error.errorCodeName}: ${error.message}",
            exoPlayer.currentPosition,
            failure,
            afterSwitch,
        )
    }
}
