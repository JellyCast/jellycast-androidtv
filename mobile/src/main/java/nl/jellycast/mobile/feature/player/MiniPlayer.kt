package nl.jellycast.mobile.feature.player

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors

const val PINCH_LARGER = 1.15f
const val PINCH_SMALLER = 0.85f

/** The size a pinch ends in: larger above 1.15, smaller below 0.85, otherwise unchanged. */
fun pinchResult(large: Boolean, magnification: Float): Boolean = when {
    magnification > PINCH_LARGER -> true
    magnification < PINCH_SMALLER -> false
    else -> large
}

/**
 * The controls drawn over the live video in the mini player: play/pause and close, a spinner
 * while starting or stalled, a triangle on error. Tap expands; pinch changes the size.
 */
@Composable
fun MiniPlayerOverlay(
    large: Boolean,
    engine: PlayerEngineState,
    phase: PlayerPhase,
    onExpand: () -> Unit,
    onClose: () -> Unit,
    onPinch: (Float) -> Unit,
    onPinchEnd: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnPinch by rememberUpdatedState(onPinch)
    val currentOnPinchEnd by rememberUpdatedState(onPinchEnd)
    val iconSize = if (large) 19.dp else 15.dp
    val failed = phase is PlayerPhase.Failed
    val loading = !failed && (!engine.hasFirstFrame || engine.isStalled)
    Box(
        modifier
            .border(1.dp, JellyCastColors.Rail)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var zoom = 1f
                    var pinched = false
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.count { it.pressed } >= 2) {
                            pinched = true
                            zoom *= event.calculateZoom()
                            currentOnPinch(zoom)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (pinched) currentOnPinchEnd(zoom)
                }
            }.pressable(onExpand),
    ) {
        if (failed) {
            val label = stringResource(R.string.player_mini_error)
            Icon(
                painterResource(R.drawable.ic_player_warning),
                null,
                Modifier
                    .align(Alignment.Center)
                    .size(28.dp)
                    .semantics { contentDescription = label },
                tint = JellyCastColors.TextMuted,
            )
        } else if (loading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(if (large) 36.dp else 24.dp),
                color = Color.White,
                strokeWidth = 2.dp,
            )
        }
        Row(Modifier.fillMaxWidth().align(Alignment.TopStart)) {
            if (!failed) {
                PlayerIconButton(
                    if (engine.playWhenReady) R.drawable.ic_player_pause else R.drawable.ic_play,
                    stringResource(R.string.player_play_pause),
                    engine::togglePlayPause,
                    iconSize = iconSize,
                )
            }
            Spacer(Modifier.weight(1f))
            PlayerIconButton(
                R.drawable.ic_player_close,
                stringResource(R.string.player_close),
                onClose,
                iconSize = iconSize,
            )
        }
    }
}
