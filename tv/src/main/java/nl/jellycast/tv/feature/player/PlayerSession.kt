package nl.jellycast.tv.feature.player

import android.os.SystemClock
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlaybackPlan
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.core.model.isFinishedAt
import nl.jellycast.core.model.streamChoice
import nl.jellycast.core.playback.FrameStats
import nl.jellycast.core.playback.PlaybackDiagnostics
import nl.jellycast.core.playback.PlaybackFailure
import nl.jellycast.core.playback.PlaybackReporter
import nl.jellycast.core.playback.PlaybackSettings
import nl.jellycast.core.playback.PlaybackSettingsStore
import nl.jellycast.core.playback.PlaybackStartLog
import nl.jellycast.core.playback.PlayerFactory
import nl.jellycast.core.playback.PlayerSnapshot
import nl.jellycast.core.playback.StallAction
import nl.jellycast.core.playback.StallDetector
import nl.jellycast.core.playback.StallKind
import nl.jellycast.core.playback.StartLogListener
import nl.jellycast.core.playback.applyTrackChoice
import nl.jellycast.core.playback.classifyPlaybackFailure
import nl.jellycast.core.playback.holdTracksUntilChosen
import nl.jellycast.core.playback.setVideoHeld
import nl.jellycast.core.playback.shouldTunnel
import nl.jellycast.core.playback.toFailure
import nl.jellycast.core.playback.toMediaItem
import nl.jellycast.core.playback.videoWithoutDecoder
import timber.log.Timber

private val ProgressInterval = 10.seconds

/** After this long without errors, the next error may retry the same plan once more. */
private val StablePlaybackDuration = 30.seconds

private const val STALL_CHECK_INTERVAL_MS = 1_000L

internal typealias PlaybackErrorHandler = (
    message: String,
    positionMs: Long,
    failure: PlaybackFailure,
    afterDisplaySwitch: Boolean,
    tunneled: Boolean,
) -> Unit

internal class PlayerSessionState {
    var player by mutableStateOf<ExoPlayer?>(null)
    var isPlaying by mutableStateOf(false)
    var tracks by mutableStateOf(Tracks.EMPTY)

    /** The chosen audio track is applied (for direct play only after `applyTrackChoice`). */
    var tracksChosen by mutableStateOf(false)

    /** The player whose video track is enabled. */
    var videoPlayer by mutableStateOf<ExoPlayer?>(null)
    var startedPlayer by mutableStateOf<ExoPlayer?>(null)

    private var chosenStreams: Pair<PlaybackPlan, StreamChoice>? = null

    fun streamsFor(plan: PlaybackPlan): StreamChoice =
        chosenStreams?.takeIf { it.first === plan }?.second ?: plan.streamChoice()

    fun chooseStreams(plan: PlaybackPlan, streams: StreamChoice) {
        chosenStreams = plan to streams
    }
}

/**
 * Creates the ExoPlayer for [plan], switches the display mode first and only starts playback once
 * the tracks are chosen and HDMI has settled. Also reports progress and watches for stalls.
 */
@OptIn(UnstableApi::class)
@Composable
internal fun rememberPlayerSession(
    plan: PlaybackPlan,
    info: PlayerInfo,
    settings: PlaybackSettings,
    diagnostics: PlaybackDiagnostics,
    nextAvailable: Boolean,
    onStop: (positionMs: Long) -> Unit,
    onError: PlaybackErrorHandler,
    onStable: () -> Unit,
    onPlayNext: () -> Unit,
    onExit: () -> Unit,
    playerFactory: PlayerFactory,
    reporter: PlaybackReporter,
    settingsStore: PlaybackSettingsStore,
    startLog: PlaybackStartLog,
): PlayerSessionState {
    val currentOnStop by rememberUpdatedState(onStop)
    val currentOnError by rememberUpdatedState(onError)
    val currentOnPlayNext by rememberUpdatedState(onPlayNext)
    val currentOnExit by rememberUpdatedState(onExit)
    val currentNextAvailable by rememberUpdatedState(nextAvailable)
    // Segments of this plan, even after info already moved on to the next episode: on stop they
    // decide whether the item counts as watched.
    val planSegments = remember(plan) { mutableStateOf(emptyList<MediaSegment>()) }
    if (info.item?.id == plan.itemId && info.segments.isNotEmpty()) {
        planSegments.value = info.segments
    }
    val state = remember { PlayerSessionState() }
    val view = LocalView.current
    val context = LocalContext.current

    // Before the display mode, so the start log stays in order.
    DisposableEffect(plan) {
        startLog.begin(plan.startLogDescription())
        onDispose { }
    }

    // Display mode first, then audio: after a mode switch HDMI rebuilds the link (picture, then
    // HDCP), and audio that starts during that stutters on the TV and the soundbar.
    val frameRate =
        rememberFrameRateMatching(
            videoFrameRate = plan.video?.displayFrameRate,
            videoWidth = plan.video?.width,
            videoHeight = plan.video?.height,
            frameRateMode = settings.frameRateMode,
            startDelayMs = settings.startDelayMs.toLong(),
            log = startLog::log,
        )
    val currentSwitchedAt by rememberUpdatedState(frameRate.switchedAtMs)
    val currentModeReady by rememberUpdatedState(frameRate.modeReady)

    // The player lives from onStart to onStop, so the hardware decoder is freed when the app is
    // gone. A new plan gets a new player: the decoder choice (Dolby Vision via HEVC) is fixed then.
    val tunneled = remember(plan) { shouldTunnel(plan, settingsStore.settings.value.tunneling) }
    LifecycleStartEffect(plan) {
        if (tunneled) startLog.log("Tunneling on")
        val frameStats = FrameStats()
        val exoPlayer = playerFactory.create(plan, tunneled, frameStats)
        // System and Assistant ("pause") control the player through the session; no service,
        // because there is no background playback.
        val session = MediaSession.Builder(context, exoPlayer).build()
        exoPlayer.addAnalyticsListener(diagnostics)
        exoPlayer.addAnalyticsListener(StartLogListener(startLog))
        exoPlayer.addListener(
            SessionListener(
                exoPlayer = exoPlayer,
                plan = plan,
                state = state,
                view = view,
                reporter = reporter,
                startLog = startLog,
                reportError = { message, positionMs, failure, afterSwitch ->
                    currentOnError(message, positionMs, failure, afterSwitch, tunneled)
                },
                switchedAtMs = { currentSwitchedAt },
                onEnded = { if (currentNextAvailable) currentOnPlayNext() else currentOnExit() },
            ),
        )
        // Preparing (opening the file, buffering) may start right away; the video decoder waits
        // for the mode switch, and playback also for the track choice and a stable HDMI link.
        state.tracksChosen = !exoPlayer.holdTracksUntilChosen(plan)
        if (currentModeReady) {
            state.videoPlayer = exoPlayer
        } else {
            exoPlayer.setVideoHeld(true)
        }
        startLog.log("Preparing from ${plan.startPositionMs} ms")
        exoPlayer.setMediaItem(plan.toMediaItem(), plan.startPositionMs)
        exoPlayer.prepare()
        reporter.start(plan, plan.startPositionMs)
        state.player = exoPlayer

        onStopOrDispose {
            val positionMs = exoPlayer.currentPosition
            val finished = planSegments.value.isFinishedAt(positionMs, exoPlayer.duration)
            reporter.stop(plan, positionMs, finished = finished)
            if (finished) startLog.log("Stopped in the credits: watched")
            frameStats.summary()?.let { startLog.log("Video: $it") }
            session.release()
            exoPlayer.release()
            state.player = null
            view.keepScreenOn = false
            // Prepare again only when going to the background, not on Back.
            if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                currentOnStop(positionMs)
            }
        }
    }

    // Enable the video track (and so the decoder) only once the new display mode is active: a
    // decoder that already outputs during the HDMI resync sometimes showed green blocks.
    LaunchedEffect(state.player, frameRate.modeReady) {
        val activePlayer = state.player ?: return@LaunchedEffect
        if (frameRate.modeReady && state.videoPlayer !== activePlayer) {
            state.videoPlayer = activePlayer
            startLog.log("Video track on")
            activePlayer.setVideoHeld(false)
        }
    }

    // Start once per player: a later mode switch must not undo a pause by the user.
    val readyToStart =
        state.tracksChosen && frameRate.settled && state.videoPlayer === state.player
    LaunchedEffect(state.player, readyToStart) {
        val activePlayer = state.player ?: return@LaunchedEffect
        if (readyToStart && state.startedPlayer !== activePlayer) {
            state.startedPlayer = activePlayer
            activePlayer.playWhenReady = true
        }
    }

    PlaybackWatchers(
        player = state.player,
        isPlaying = state.isPlaying,
        plan = plan,
        streams = { state.streamsFor(plan) },
        tunneled = tunneled,
        reporter = reporter,
        startLog = startLog,
        onStable = onStable,
        onError = onError,
    )
    return state
}

@Composable
private fun PlaybackWatchers(
    player: ExoPlayer?,
    isPlaying: Boolean,
    plan: PlaybackPlan,
    streams: () -> StreamChoice,
    tunneled: Boolean,
    reporter: PlaybackReporter,
    startLog: PlaybackStartLog,
    onStable: () -> Unit,
    onError: PlaybackErrorHandler,
) {
    val currentOnStable by rememberUpdatedState(onStable)
    val currentOnError by rememberUpdatedState(onError)
    val currentStreams by rememberUpdatedState(streams)

    LaunchedEffect(player, isPlaying) {
        if (player != null && isPlaying) {
            delay(StablePlaybackDuration)
            currentOnStable()
        }
    }

    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        while (isActive) {
            delay(ProgressInterval)
            reporter.progress(
                plan,
                activePlayer.currentPosition,
                isPaused = !activePlayer.isPlaying,
                streams = currentStreams(),
            )
        }
    }

    // A player can hang without an error: seek to the same spot first, fall back if it recurs.
    LaunchedEffect(player) {
        val activePlayer = player ?: return@LaunchedEffect
        val detector = StallDetector()
        val seekListener = SeekListener(detector::onSeek)
        activePlayer.addListener(seekListener)
        try {
            while (isActive) {
                delay(STALL_CHECK_INTERVAL_MS)
                val snapshot = activePlayer.snapshot()
                when (val action = detector.check(SystemClock.elapsedRealtime(), snapshot)) {
                    StallAction.None -> Unit

                    is StallAction.Reseek -> {
                        startLog.log(
                            "Stalled (${action.kind}) at ${snapshot.positionMs} ms; retrying",
                        )
                        activePlayer.seekTo(snapshot.positionMs)
                    }

                    is StallAction.Escalate -> {
                        startLog.log(
                            if (action.kind == StallKind.TooSlow) {
                                "Loading too slowly; lower quality"
                            } else {
                                "Stalled again (${action.kind}); fallback"
                            },
                        )
                        reporter.stop(plan, snapshot.positionMs, failed = true)
                        // Shown on the error screen when no fallback is left.
                        currentOnError(
                            "Stalled: ${action.kind}",
                            snapshot.positionMs,
                            action.kind.toFailure(),
                            false,
                            tunneled,
                        )
                    }
                }
            }
        } finally {
            activePlayer.removeListener(seekListener)
        }
    }
}

private class SeekListener(private val onSeek: () -> Unit) : Player.Listener {
    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int,
    ) {
        if (reason == Player.DISCONTINUITY_REASON_SEEK) onSeek()
    }
}

private fun Player.snapshot() = PlayerSnapshot(
    playWhenReady = playWhenReady,
    buffering = playbackState == Player.STATE_BUFFERING,
    playing = isPlaying,
    positionMs = currentPosition,
    bufferedPositionMs = bufferedPosition,
)

/** Header line of the start log: what plays and how the server decided. No URL or token. */
private fun PlaybackPlan.startLogDescription(): String {
    val video = video?.let {
        "${it.codec} ${it.width}x${it.height} ${it.frameRate ?: "?"}fps ${it.rangeType}"
    }
    return listOfNotNull(
        title,
        "($itemId)",
        playMethod.name,
        transcodeReasons.takeIf { it.isNotEmpty() }?.joinToString(","),
        bitrateLimit?.let { "max ${it.bitsPerSecond / 1_000_000} Mbps (${it.cause})" },
        container,
        video,
        "start $startPositionMs ms",
    ).joinToString(" · ")
}
