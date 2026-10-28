package nl.jellycast.mobile.feature.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlaybackException
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.isFinishedAt
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackReporter
import nl.jellycast.core.playback.PlayerFactory
import nl.jellycast.core.playback.applyTrackChoice
import nl.jellycast.core.playback.classifyPlaybackFailure
import nl.jellycast.core.playback.holdTracksUntilChosen
import nl.jellycast.core.playback.toMediaItem
import timber.log.Timber

private val ProgressInterval = 10.seconds

/** After this long without errors, the next error may retry the same plan once more. */
private val StablePlaybackDuration = 30.seconds

class SessionCallbacks(
    val onStop: (positionMs: Long) -> Unit,
    val onError: (message: String, positionMs: Long, failure: PlaybackFailure) -> Unit,
    val onStable: () -> Unit,
    val onPlayNext: () -> Unit,
    val onExit: () -> Unit,
)

class PlayerSessionState {
    var player by mutableStateOf<ExoPlayer?>(null)
    var isPlaying by mutableStateOf(false)
}

/**
 * One ExoPlayer per plan, alive from onStart to onStop so the decoder is freed in the background.
 * Reports start, progress and stop to the server.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerSession(
    plan: PlaybackPlan,
    segments: List<MediaSegment>,
    nextAvailable: Boolean,
    playerFactory: PlayerFactory,
    reporter: PlaybackReporter,
    callbacks: SessionCallbacks,
): PlayerSessionState {
    val currentCallbacks by rememberUpdatedState(callbacks)
    val currentNextAvailable by rememberUpdatedState(nextAvailable)
    val currentSegments by rememberUpdatedState(segments)
    val state = remember { PlayerSessionState() }
    val context = LocalContext.current
    val view = LocalView.current

    LifecycleStartEffect(plan) {
        val exoPlayer = playerFactory.create(plan)
        val session = MediaSession.Builder(context, exoPlayer).build()
        val holdUntilChosen = exoPlayer.holdTracksUntilChosen(plan)
        exoPlayer.addListener(
            object : Player.Listener {
                private var tracksApplied = false

                override fun onTracksChanged(tracks: Tracks) {
                    if (tracksApplied || tracks.isEmpty) return
                    tracksApplied = true
                    exoPlayer.applyTrackChoice(plan, tracks)
                    exoPlayer.playWhenReady = true
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state.isPlaying = isPlaying
                    view.keepScreenOn = isPlaying
                    reporter.progress(plan, exoPlayer.currentPosition, isPaused = !isPlaying)
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState != Player.STATE_ENDED) return
                    if (currentNextAvailable) {
                        currentCallbacks.onPlayNext()
                    } else {
                        currentCallbacks.onExit()
                    }
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
                    currentCallbacks.onError(
                        "${error.errorCodeName}: ${error.message}",
                        exoPlayer.currentPosition,
                        failure,
                    )
                }
            },
        )
        exoPlayer.setMediaItem(plan.toMediaItem(), plan.startPositionMs)
        exoPlayer.prepare()
        // Direct play starts once the app has chosen the audio and subtitle tracks.
        exoPlayer.playWhenReady = !holdUntilChosen
        reporter.start(plan, plan.startPositionMs)
        state.player = exoPlayer

        onStopOrDispose {
            val positionMs = exoPlayer.currentPosition
            val finished = currentSegments.isFinishedAt(positionMs, exoPlayer.duration)
            reporter.stop(plan, positionMs, finished = finished)
            session.release()
            exoPlayer.release()
            state.player = null
            view.keepScreenOn = false
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                currentCallbacks.onStop(positionMs)
            }
        }
    }

    LaunchedEffect(state.player, state.isPlaying) {
        if (state.player != null && state.isPlaying) {
            delay(StablePlaybackDuration)
            currentCallbacks.onStable()
        }
    }

    LaunchedEffect(state.player) {
        val activePlayer = state.player ?: return@LaunchedEffect
        while (isActive) {
            delay(ProgressInterval)
            reporter.progress(
                plan,
                activePlayer.currentPosition,
                isPaused = !activePlayer.isPlaying,
            )
        }
    }
    return state
}
