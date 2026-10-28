package nl.jellycast.mobile.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R

private val SwipeSlop = 16.dp

/** Taps (controls, double-tap seek) and the vertical swipe that minimises, on one handler. */
fun Modifier.videoGestures(
    tapsEnabled: Boolean,
    onTap: (TapSide, Long) -> Unit,
    onSwipe: (Float) -> Unit,
    onSwipeEnd: (Float) -> Unit,
): Modifier = pointerInput(tapsEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown()
        val slop = SwipeSlop.toPx()
        val velocity = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
        var dx = 0f
        var dy = 0f
        var swiping = false
        var moved = false
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            // A control above the video took this touch (its press or its release).
            if (change.isConsumed) return@awaitEachGesture
            if (!change.pressed) break
            val delta = change.positionChange()
            dx += delta.x
            dy += delta.y
            velocity.addPosition(change.uptimeMillis, change.position)
            if (!swiping && !moved && (abs(dx) > slop || abs(dy) > slop)) {
                if (abs(dy) > abs(dx)) swiping = true else moved = true
            }
            if (swiping) {
                change.consume()
                onSwipe(maxOf(0f, dy))
            }
        }
        if (swiping) {
            onSwipeEnd(velocity.calculateVelocity().y)
        } else if (!moved && tapsEnabled) {
            val side = if (down.position.x < size.width / 2) TapSide.Back else TapSide.Forward
            onTap(side, down.uptimeMillis)
        }
    }
}

class FullPlayerActions(
    val controls: ControlActions,
    val onTap: (TapSide, Long) -> Unit,
    val onSeekStep: (TapSide) -> Unit,
    val onSwipe: (Float) -> Unit,
    val onSwipeEnd: (Float) -> Unit,
    val onPill: (SegmentPill) -> Unit,
    val onRetry: () -> Unit,
    val onPlayViaServer: () -> Unit,
)

/** Everything over the video in the full player, bottom to top as iOS `PlayerScreen`. */
@Composable
fun FullPlayerOverlay(
    state: NowPlayingUiState,
    engine: PlayerEngineState,
    chrome: PlayerChromeState,
    isFullScreen: Boolean,
    title: PlayerTitle?,
    autoLabel: String?,
    buttons: ControlButtons,
    errorMessage: String?,
    viaServerLabel: String?,
    actions: FullPlayerActions,
    safePadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val started = state.phase is PlayerPhase.Playing && engine.hasFirstFrame
    val videoLabel = stringResource(R.string.player_video)
    val backLabel = stringResource(R.string.player_back_10)
    val forwardLabel = stringResource(R.string.player_forward_10)
    val currentActions by rememberUpdatedState(actions)
    Box(
        modifier
            .semantics {
                contentDescription = videoLabel
                customActions =
                    listOf(
                        CustomAccessibilityAction(backLabel) {
                            currentActions.onSeekStep(TapSide.Back)
                            true
                        },
                        CustomAccessibilityAction(forwardLabel) {
                            currentActions.onSeekStep(TapSide.Forward)
                            true
                        },
                    )
            }.videoGestures(
                tapsEnabled = started,
                onTap = { side, time -> currentActions.onTap(side, time) },
                onSwipe = { currentActions.onSwipe(it) },
                onSwipeEnd = { currentActions.onSwipeEnd(it) },
            ),
    ) {
        SeekBubble(chrome.seekRun, chrome.lastSeekSide)
        if (started) {
            PlayerControlsOverlay(
                visible = chrome.controlsVisible,
                isFullScreen = isFullScreen,
                title = title,
                engine = engine,
                scrubFraction = chrome.scrubFraction,
                autoLabel = autoLabel,
                buttons = buttons,
                actions = actions.controls,
                safePadding = safePadding,
                modifier = Modifier.fillMaxSize(),
            )
            Pills(state, engine, chrome, isFullScreen, safePadding, actions.onPill)
            if (engine.isStalled && !chrome.controlsVisible) {
                StallIndicator(Modifier.align(Alignment.Center))
            }
        }
        when {
            errorMessage != null ->
                PlayerErrorView(
                    errorMessage,
                    viaServerLabel,
                    actions.onRetry,
                    actions.onPlayViaServer,
                )

            !started -> StartOverlay()
        }
    }
}

@Composable
private fun BoxScope.Pills(
    state: NowPlayingUiState,
    engine: PlayerEngineState,
    chrome: PlayerChromeState,
    isFullScreen: Boolean,
    safePadding: PaddingValues,
    onPill: (SegmentPill) -> Unit,
) {
    val pill =
        segmentPill(
            positionMs = engine.positionMs,
            durationMs = engine.durationMs,
            segments = state.segments,
            nextItem = state.nextItem,
            fromList = state.queue != null,
            slowHint = chrome.slowHint,
        ) ?: return
    val bottom =
        when {
            isFullScreen && chrome.controlsVisible -> 110.dp
            isFullScreen -> maxOf(safePadding.calculateBottomPadding(), 24.dp)
            chrome.controlsVisible -> 56.dp
            else -> 14.dp
        }
    val end = maxOf(safePadding.calculateRightPadding(LayoutDirection.Ltr), 12.dp)
    SegmentPillView(
        pill,
        onClick = { onPill(pill) },
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = end, bottom = bottom),
    )
}

/** Movie: name and year. Episode: series name and "S1 · E3 · Episode name". */
@Composable
fun playerTitle(item: PlayableItem): PlayerTitle = when (item) {
    is PlayableItem.MovieItem -> PlayerTitle(item.movie.title, item.movie.year?.toString())

    is PlayableItem.EpisodeItem ->
        PlayerTitle(
            item.episode.seriesTitle,
            "${episodeCode(
                item.episode.seasonNumber,
                item.episode.episodeNumber,
            )} · ${item.episode.title}",
        )
}
