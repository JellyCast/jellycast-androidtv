package nl.jellycast.mobile.feature.player

import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.C
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
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.model.streamChoice
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlayerFactory
import nl.jellycast.core.playback.applyTrackChoice
import nl.jellycast.core.playback.chosenStreams
import nl.jellycast.core.playback.classifyPlaybackFailure
import nl.jellycast.core.playback.holdTracksUntilChosen
import nl.jellycast.core.playback.toMediaItem
import nl.jellycast.core.playback.videoWithoutDecoder
import timber.log.Timber

private val ProgressInterval = 10.seconds
private val StablePlaybackDuration = 30.seconds
private const val CLOCK_INTERVAL_MS = 250L

/** What the player UI reads from the engine; the clock fields tick every 250 ms. */
@Stable
class PlayerEngineState {
    var player by mutableStateOf<ExoPlayer?>(null)
        internal set
    var isPlaying by mutableStateOf(false)
        internal set
    var playWhenReady by mutableStateOf(false)
        internal set
    var isBuffering by mutableStateOf(false)
        internal set
    var hasFirstFrame by mutableStateOf(false)
        internal set
    var tracks by mutableStateOf(Tracks.EMPTY)
        internal set
    var positionMs by mutableLongStateOf(0L)
        internal set
    var bufferedMs by mutableLongStateOf(0L)
        internal set
    var durationMs by mutableLongStateOf(0L)
        internal set

    /** Counts landed seeks, so watchers can start over after one. */
    var seekCount by mutableIntStateOf(0)
        internal set

    /** Loading again after the start while it should play. */
    val isStalled: Boolean get() = hasFirstFrame && isBuffering && playWhenReady

    /** The streams playing now, as the server counts them (for reports and a hand-over). */
    fun streams(plan: PlaybackPlan): StreamChoice = if (plan.playMethod == PlayMethod.DirectPlay) {
        tracks.chosenStreams(plan) ?: plan.streamChoice()
    } else {
        plan.streamChoice()
    }

    fun togglePlayPause() {
        val active = player ?: return
        if (active.isPlaying || active.playWhenReady) active.pause() else active.play()
    }

    internal fun reset() {
        isPlaying = false
        playWhenReady = false
        isBuffering = false
        hasFirstFrame = false
        tracks = Tracks.EMPTY
        positionMs = 0
        bufferedMs = 0
        durationMs = 0
    }
}

/**
 * One ExoPlayer per [PlaybackLoad], with its MediaSession. It keeps playing while the app shows
 * anything (full player, mini player, system PiP) and pauses when the Activity stops.
 */
@OptIn(UnstableApi::class)
@Composable
fun rememberPlayerEngine(
    load: PlaybackLoad?,
    viewModel: NowPlayingViewModel,
    playerFactory: PlayerFactory,
): PlayerEngineState {
    val state = remember { PlayerEngineState() }
    val context = LocalContext.current
    val view = LocalView.current

    DisposableEffect(load) {
        if (load == null) return@DisposableEffect onDispose { }
        val plan = load.plan
        val exoPlayer = playerFactory.create(plan)
        val session = MediaSession.Builder(context, exoPlayer).setId("play-${load.id}").build()
        state.reset()
        var failed = false

        fun fail(failure: PlaybackFailure) {
            if (failed) return
            failed = true
            viewModel.onPlaybackError(
                load,
                failure,
                exoPlayer.currentPosition,
                beforeFirstFrame = !state.hasFirstFrame,
            )
        }
        val listener =
            object : Player.Listener {
                private var tracksApplied = false

                override fun onTracksChanged(tracks: Tracks) {
                    state.tracks = tracks
                    if (tracks.isEmpty) return
                    if (!tracksApplied) {
                        tracksApplied = true
                        exoPlayer.applyTrackChoice(plan, tracks)
                        exoPlayer.playWhenReady = true
                    } else if (tracks.videoWithoutDecoder(exoPlayer)) {
                        Timber.w("No decoder for the video track")
                        fail(PlaybackFailure.Video)
                    }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    state.isPlaying = isPlaying
                    view.keepScreenOn = isPlaying
                    viewModel.onProgress(
                        load,
                        exoPlayer.currentPosition,
                        isPaused = !isPlaying,
                        streams = state.streams(plan),
                    )
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    state.playWhenReady = playWhenReady
                }

                override fun onRenderedFirstFrame() {
                    state.hasFirstFrame = true
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    state.isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_ENDED) viewModel.onEnded(load)
                }

                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int,
                ) {
                    if (reason != Player.DISCONTINUITY_REASON_SEEK) return
                    state.seekCount++
                    viewModel.onProgress(
                        load,
                        newPosition.positionMs,
                        isPaused = !exoPlayer.isPlaying,
                        streams = state.streams(plan),
                    )
                }

                override fun onPlayerError(error: PlaybackException) {
                    Timber.e(error, "Playback error (%s)", error.errorCodeName)
                    val exo = error as? ExoPlaybackException
                    fail(
                        classifyPlaybackFailure(
                            errorCode = error.errorCode,
                            isSource = exo?.type == ExoPlaybackException.TYPE_SOURCE,
                            isRenderer = exo?.type == ExoPlaybackException.TYPE_RENDERER,
                            rendererMimeType = exo?.rendererFormat?.sampleMimeType,
                        ),
                    )
                }
            }
        val holdUntilChosen = exoPlayer.holdTracksUntilChosen(plan)
        exoPlayer.addListener(listener)
        exoPlayer.setMediaItem(plan.toMediaItem(), plan.startPositionMs)
        exoPlayer.prepare()
        // Direct play starts once the app has chosen the audio and subtitle tracks.
        exoPlayer.playWhenReady = !holdUntilChosen
        state.player = exoPlayer
        viewModel.onEngineStarted(load)

        onDispose {
            val duration = exoPlayer.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            viewModel.onEngineStopped(
                load,
                positionMs = exoPlayer.currentPosition,
                durationMs = duration,
                ended = exoPlayer.playbackState == Player.STATE_ENDED,
                failed = failed,
            )
            exoPlayer.removeListener(listener)
            session.release()
            exoPlayer.release()
            state.player = null
            view.keepScreenOn = false
        }
    }

    EngineLoops(state, load, viewModel)
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { state.player?.pause() }
    return state
}

@Composable
private fun EngineLoops(state: PlayerEngineState, load: PlaybackLoad?, viewModel: NowPlayingViewModel) {
    val player = state.player
    LaunchedEffect(player) {
        val active = player ?: return@LaunchedEffect
        while (isActive) {
            state.positionMs = active.currentPosition
            state.bufferedMs = active.bufferedPosition
            state.durationMs = active.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(CLOCK_INTERVAL_MS)
        }
    }
    LaunchedEffect(player, load) {
        val active = player ?: return@LaunchedEffect
        val current = load ?: return@LaunchedEffect
        while (isActive) {
            delay(ProgressInterval)
            viewModel.onProgress(
                current,
                active.currentPosition,
                isPaused = !active.isPlaying,
                streams = state.streams(current.plan),
            )
        }
    }
    LaunchedEffect(player, state.isPlaying) {
        if (player != null && state.isPlaying) {
            delay(StablePlaybackDuration)
            viewModel.onPlaybackStable()
        }
    }
}
