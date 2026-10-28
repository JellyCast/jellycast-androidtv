package nl.jellycast.mobile.feature.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.ProgressBrush

private val TouchHeight = 28.dp
private val KnobDiameter = 13.dp

/**
 * iOS `ScrubBar`: track, buffered band, played part in the progress gradient and a knob while
 * [interactive]. Fractions are lambdas so the player clock only redraws, never recomposes.
 */
@Composable
fun ScrubBar(
    played: () -> Float,
    buffered: () -> Float,
    interactive: Boolean,
    label: String,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    modifier: Modifier = Modifier,
    lineHeight: Dp = 4.dp,
) {
    val currentOnScrub by rememberUpdatedState(onScrub)
    val currentOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val gestures =
        if (interactive) {
            Modifier.pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    down.consume()
                    currentOnScrub((down.position.x / size.width).coerceIn(0f, 1f))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        change.consume()
                        currentOnScrub((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                    currentOnScrubEnd()
                }
            }
        } else {
            Modifier
        }
    Box(
        modifier
            .fillMaxWidth()
            .height(TouchHeight)
            .semantics {
                contentDescription = label
                progressBarRangeInfo =
                    androidx.compose.ui.semantics.ProgressBarRangeInfo(played(), 0f..1f)
                if (interactive) {
                    setProgress { target ->
                        currentOnScrub(target)
                        currentOnScrubEnd()
                        true
                    }
                }
            }.then(gestures)
            .drawBehind {
                val line = lineHeight.toPx()
                val top = (size.height - line) / 2
                val radius = CornerRadius(line / 2)
                val playedWidth = size.width * played().coerceIn(0f, 1f)
                val bufferedWidth = size.width * maxOf(buffered(), played()).coerceIn(0f, 1f)
                drawRoundRect(
                    JellyCastColors.Text.copy(alpha = 0.30f),
                    Offset(0f, top),
                    Size(size.width, line),
                    radius,
                )
                drawRoundRect(
                    JellyCastColors.Text.copy(alpha = 0.35f),
                    Offset(0f, top),
                    Size(bufferedWidth, line),
                    radius,
                )
                drawRoundRect(ProgressBrush, Offset(0f, top), Size(playedWidth, line), radius)
                if (interactive) {
                    drawCircle(
                        Color.White,
                        KnobDiameter.toPx() / 2,
                        Offset(playedWidth, size.height / 2),
                    )
                }
            },
    )
}
