package nl.jellycast.mobile.feature.player

import android.os.SystemClock
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.RailCircleButton
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.ui.info.InfoSubject
import nl.jellycast.mobile.ui.info.ItemInfoPanel
import nl.jellycast.mobile.ui.remote.PlayOnRequest
import nl.jellycast.mobile.ui.remote.PlayOnSheet
import nl.jellycast.mobile.ui.remote.hasRemoteTargets

private val CloseDistance = 120.dp
private const val SWIPE_PREDICTION_S = 0.25f
private const val BACK_SWIPE_SHARE = 0.3f
private const val SLOW_HINT_SHOWN_MS = 10_000L
private const val BUFFER_SAMPLE_MS = 1_000L
private val ScrubAbove = 15.dp
private val ScrubBelow = 13.dp

/**
 * The black backdrop and, outside full screen, the info panel under (phone, tablet portrait) or
 * beside (tablet landscape, via the panel's header) the video. Fades and scales with the swipe.
 */
@Composable
fun FullPlayerChrome(
    item: PlayableItem,
    chrome: PlayerChromeState,
    fullScreen: Boolean,
    headerMode: Boolean,
    heightPx: Float,
    modifier: Modifier = Modifier,
) {
    val swipeLayer =
        Modifier.graphicsLayer {
            val swipe = SwipeTransform(chrome.swipeOffset.value, heightPx)
            scaleX = swipe.scale
            scaleY = swipe.scale
            translationY = swipe.offsetPx
            transformOrigin = TransformOrigin(0.5f, 0f)
        }
    val showsContinueOn = hasRemoteTargets()
    val continueLabel = stringResource(R.string.player_continue_on)
    val panelActions: @Composable RowScope.() -> Unit = {
        if (showsContinueOn) {
            RailCircleButton(
                R.drawable.ic_player_cast,
                continueLabel,
                onClick = { chrome.openSheet(PlayerSheetKind.ContinueOn) },
                size = 34.dp,
            )
        }
    }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 1f - SwipeTransform(chrome.swipeOffset.value, heightPx).progress
                }.background(Color.Black)
                .blockTouches(),
        )
        when {
            fullScreen -> Unit

            headerMode ->
                ItemInfoPanel(
                    subject = item.infoSubject(),
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .then(swipeLayer)
                            .background(JellyCastColors.Background)
                            .statusBarsPadding(),
                    playingItemId = item.id,
                    header = {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .aspectRatio(VIDEO_ASPECT)
                                .onGloballyPositioned { chrome.headerRect = it.boundsInRoot() },
                        )
                    },
                    actions = panelActions,
                )

            else ->
                Column(Modifier.fillMaxSize().then(swipeLayer)) {
                    Spacer(
                        Modifier
                            .fillMaxWidth()
                            .windowInsetsTopHeight(WindowInsets.statusBars)
                            .background(Color.Black),
                    )
                    Spacer(Modifier.fillMaxWidth().aspectRatio(VIDEO_ASPECT))
                    ItemInfoPanel(
                        subject = item.infoSubject(),
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .background(JellyCastColors.Background),
                        playingItemId = item.id,
                        actions = panelActions,
                    )
                }
        }
    }
}

private fun PlayableItem.infoSubject(): InfoSubject = when (this) {
    is PlayableItem.MovieItem -> InfoSubject.MovieInfo(movie.id)
    is PlayableItem.EpisodeItem -> InfoSubject.EpisodeInfo(episode.id, episode.seriesId)
}

/** Touches on the backdrop must not reach the app under the player. */
private fun Modifier.blockTouches(): Modifier = pointerInput(Unit) {
    awaitEachGesture { awaitFirstDown().consume() }
}

/** The controls, gestures, sheets and back handling of the expanded player. */
@Composable
fun FullPlayer(
    state: NowPlayingUiState,
    engine: PlayerEngineState,
    chrome: PlayerChromeState,
    actions: PlayerActions,
    fullScreen: Boolean,
    regular: Boolean,
    pip: PictureInPictureState,
    heightPx: Float,
    videoRect: () -> Rect,
    modifier: Modifier = Modifier,
) {
    val item = state.item ?: return
    val orientation = rememberOrientationLock()
    val closeDistancePx = with(LocalDensity.current) { CloseDistance.toPx() }
    val started = state.phase is PlayerPhase.Playing && engine.hasFirstFrame

    ControlsAutoHide(chrome, engine.isPlaying)
    LaunchedEffect(engine.hasFirstFrame) { if (engine.hasFirstFrame) chrome.showControls() }
    LaunchedEffect(engine.isPlaying) {
        if (engine.isPlaying && chrome.controlsVisible) chrome.interact()
    }
    SlowConnectionWatch(engine, chrome, state.quality)
    MinimizeOnBack(chrome, heightPx, actions::onMinimize)

    val seekBy: (Long) -> Unit = { deltaMs ->
        engine.player?.let { it.seekTo((it.currentPosition + deltaMs).coerceAtLeast(0)) }
    }
    val overlayActions =
        FullPlayerActions(
            controls = controlActions(engine, chrome, regular, fullScreen, orientation, pip),
            onTap = { side, time ->
                chrome.onVideoTap(side, time, engine.player?.currentPosition ?: 0) { run ->
                    engine.player?.let { it.seekTo(run.targetMs(it.duration)) }
                }
            },
            onSeekStep = { side -> seekBy(side.direction * SEEK_STEP_MS) },
            onSwipe = chrome::dragTo,
            onSwipeEnd = { velocity ->
                val offset = chrome.swipeOffset.value
                if (offset > closeDistancePx ||
                    offset + velocity * SWIPE_PREDICTION_S > heightPx / 2
                ) {
                    chrome.animateClose(heightPx, actions::onMinimize)
                } else {
                    chrome.springBack()
                }
            },
            onPill = { pill ->
                when (pill) {
                    is SegmentPill.SlowConnection -> chrome.slowHint = null
                    is SegmentPill.Skip -> engine.player?.seekTo(pill.endMs)
                    is SegmentPill.Next -> actions.onPlayNext()
                }
            },
            onRetry = actions::onRetry,
            onPlayViaServer = actions::onPlayViaServer,
        )
    val failed = state.phase as? PlayerPhase.Failed
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        FullPlayerOverlay(
            state = state,
            engine = engine,
            chrome = chrome,
            isFullScreen = fullScreen,
            title = playerTitle(item),
            autoLabel =
                state.quality
                    ?.takeIf { it.isAuto }
                    ?.let { stringResource(R.string.player_auto_level, levelShortName(it.level)) },
            buttons =
                ControlButtons(
                    pip = pip.isSupported,
                    continueOn = hasRemoteTargets(),
                    quality = state.load?.isLocal != true,
                ),
            errorMessage = failed?.let { stringResource(it.error.messageRes()) },
            viaServerLabel =
                failed?.viaServer?.let { stringResource(R.string.player_via_server, it.shortName) },
            actions = overlayActions,
            safePadding = safeDrawingPadding(),
            modifier = Modifier.placeAt(videoRect),
        )
        if (!fullScreen && started) {
            val above = with(density) { ScrubAbove.toPx() }
            val below = with(density) { ScrubBelow.toPx() }
            ScrubBar(
                played = { chrome.scrubFraction ?: engine.playedFraction() },
                buffered = { engine.bufferedFraction() },
                interactive = chrome.controlsVisible,
                label = stringResource(R.string.player_position),
                onScrub = overlayActions.controls.onScrub,
                onScrubEnd = overlayActions.controls.onScrubEnd,
                lineHeight = 3.dp,
                modifier =
                    Modifier.placeAt {
                        val video = videoRect()
                        Rect(video.left, video.bottom - above, video.right, video.bottom + below)
                    },
            )
        }
        PlayerSheets(state, engine, chrome, actions, item)
    }
}

@Composable
private fun controlActions(
    engine: PlayerEngineState,
    chrome: PlayerChromeState,
    regular: Boolean,
    fullScreen: Boolean,
    orientation: OrientationLock,
    pip: PictureInPictureState,
): ControlActions = ControlActions(
    onPip = {
        chrome.interact()
        pip.enter()
    },
    onContinueOn = { chrome.openSheet(PlayerSheetKind.ContinueOn) },
    onTracks = { chrome.openSheet(PlayerSheetKind.Tracks) },
    onQuality = { chrome.openSheet(PlayerSheetKind.Quality) },
    onPlayPause = {
        engine.togglePlayPause()
        chrome.interact()
    },
    onToggleFullScreen = {
        chrome.interact()
        if (regular) {
            chrome.videoOnly = !chrome.videoOnly
        } else {
            orientation.force(
                landscape = !fullScreen,
            )
        }
    },
    onScrub = { fraction ->
        chrome.scrubFraction = fraction
        chrome.interact()
    },
    onScrubEnd = {
        val fraction = chrome.scrubFraction
        val player = engine.player
        if (fraction != null && player != null) player.seekTo((fraction * player.duration).toLong())
        chrome.scrubFraction = null
    },
)

@Composable
private fun PlayerSheets(
    state: NowPlayingUiState,
    engine: PlayerEngineState,
    chrome: PlayerChromeState,
    actions: PlayerActions,
    item: PlayableItem,
) {
    val plan = state.load?.plan
    val position = { engine.player?.currentPosition ?: engine.positionMs }
    when (chrome.sheet) {
        PlayerSheetKind.Tracks ->
            if (plan != null) {
                TrackSheet(
                    plan = plan,
                    engine = engine,
                    onTranscodeStreams = { actions.onTranscodeStreamsChosen(it, position()) },
                    onDismiss = chrome::closeSheet,
                )
            }

        PlayerSheetKind.Quality ->
            state.quality?.let { quality ->
                QualitySheet(
                    quality = quality,
                    onChoose = { choice ->
                        chrome.closeSheet()
                        actions.onQualityChosen(choice, position(), plan?.let(engine::streams))
                    },
                    onDismiss = chrome::closeSheet,
                )
            }

        PlayerSheetKind.ContinueOn ->
            PlayOnSheet(
                request = PlayOnRequest(item, position(), plan?.let(engine::streams)),
                onDismiss = chrome::closeSheet,
                onSend = actions::onClose,
            )

        null -> Unit
    }
}

/** Predictive back pulls the player down like the swipe; completing it minimises. */
@Composable
private fun MinimizeOnBack(chrome: PlayerChromeState, heightPx: Float, onMinimize: () -> Unit) {
    PredictiveBackHandler { progress ->
        try {
            progress.collect { chrome.dragTo(it.progress * heightPx * BACK_SWIPE_SHARE) }
            chrome.animateClose(heightPx, onMinimize)
        } catch (e: CancellationException) {
            chrome.springBack()
            throw e
        }
    }
}

@Composable
private fun SlowConnectionWatch(
    engine: PlayerEngineState,
    chrome: PlayerChromeState,
    quality: QualityState?,
) {
    val watch = remember { BufferWatch() }
    val player = engine.player
    LaunchedEffect(player, quality) {
        val active = player ?: return@LaunchedEffect
        var seeks = engine.seekCount
        while (isActive) {
            delay(BUFFER_SAMPLE_MS)
            if (!active.isPlaying || engine.seekCount != seeks) {
                seeks = engine.seekCount
                watch.reset()
                continue
            }
            val position = active.currentPosition
            val fires =
                watch.sample(
                    nowMs = SystemClock.elapsedRealtime(),
                    bufferedAheadMs = active.bufferedPosition - position,
                    remainingMs = active.duration - position,
                )
            if (fires) chrome.slowHint = SegmentPill.SlowConnection(quality?.lowerLevel)
        }
    }
    LaunchedEffect(chrome.slowHint) {
        if (chrome.slowHint != null) {
            delay(SLOW_HINT_SHOWN_MS)
            chrome.slowHint = null
        }
    }
}

private fun PlayerError.messageRes(): Int = when (this) {
    PlayerError.NoSource -> R.string.player_error_no_source
    PlayerError.DoesNotPlay -> R.string.player_error_does_not_play
    PlayerError.StoppedMidPlay -> R.string.player_error_stopped
    PlayerError.Server -> R.string.player_error_no_answer
}
