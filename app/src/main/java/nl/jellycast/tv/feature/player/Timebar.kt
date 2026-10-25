package nl.jellycast.tv.feature.player

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import nl.jellycast.tv.core.designsystem.component.Flicker
import nl.jellycast.tv.core.designsystem.component.HOT_TAIL_DP
import nl.jellycast.tv.core.designsystem.component.HotTail
import nl.jellycast.tv.core.designsystem.component.drawWeldPoint
import nl.jellycast.tv.core.designsystem.component.progressBrush
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors

private const val SEEK_STEP_MS = 10_000L
private const val ACCELERATE_AFTER_MS = 1_000L
private const val FAST_SEEK_STEP_MS = 30_000L

/** OK within this time after the last seek step confirms the jump instead of pausing. */
private const val CONFIRM_SEEK_WINDOW_MS = 3_000L

/** How long the weld spark stays large and flickering after a seek step. */
private const val SEEK_GLOW_NS = 400_000_000L

private const val WELD_SPARK_SEEKING = 1.25f
private const val WELD_SPARK_FOCUSED = 0.85f
private const val WELD_SPARK_IDLE = 0.6f
private const val WELD_SPARK_IDLE_INTENSITY = 0.8f

/** [id] increases with every seek step, so each step lights the weld spark again. */
internal data class SeekPulse(val id: Int = 0)

/** The scrub target while ←/→ is pressed; the real seek follows once the key is released. */
internal class ScrubState {
    var targetMs by mutableStateOf<Long?>(null)
    var pulse by mutableStateOf(SeekPulse())
    private var holdStartMs = 0L
    private var lastStepAtMs: Long? = null

    /**
     * ←/→ scrubs, in larger steps after holding the key. OK right after scrubbing confirms the
     * jump and plays; otherwise OK calls [onTogglePlay].
     */
    fun onTimebarKey(
        event: KeyEvent,
        player: Player,
        durationMs: Long,
        onTogglePlay: () -> Unit,
    ): Boolean {
        if (event.type != KeyEventType.KeyDown) return false
        return when (event.key) {
            Key.DirectionLeft, Key.DirectionRight -> {
                val native = event.nativeKeyEvent
                if (native.repeatCount == 0) holdStartMs = native.eventTime
                val heldMs = native.eventTime - holdStartMs
                val step = if (heldMs > ACCELERATE_AFTER_MS) FAST_SEEK_STEP_MS else SEEK_STEP_MS
                val sign = if (event.key == Key.DirectionRight) 1 else -1
                val base = targetMs ?: player.currentPosition
                targetMs = (base + sign * step).coerceIn(0, maxOf(durationMs, 0))
                pulse = SeekPulse(pulse.id + 1)
                lastStepAtMs = native.eventTime
                true
            }

            Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                if (confirmsSeek(
                        event.nativeKeyEvent.eventTime,
                    )
                ) {
                    confirmSeek(player)
                } else {
                    onTogglePlay()
                }
                true
            }

            else -> false
        }
    }

    // Netflix and the official Jellyfin app pause while scrubbing, so OK after a jump means "play".
    private fun confirmsSeek(nowMs: Long): Boolean {
        val stepAt = lastStepAtMs ?: return false
        return nowMs - stepAt < CONFIRM_SEEK_WINDOW_MS
    }

    private fun confirmSeek(player: Player) {
        targetMs?.let { player.seekTo(it) }
        targetMs = null
        lastStepAtMs = null
        player.play()
    }
}

/** Plain fields, read only in the draw phase. */
private class TimebarEffects {
    val flicker = Flicker()
    var seekUntilNs = 0L
}

/** Frames only run during and just after seeking; otherwise it redraws on a new position. */
@Composable
internal fun Timebar(
    positionMs: Long,
    durationMs: Long,
    seek: SeekPulse,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val effects = remember { TimebarEffects() }
    var frameNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(seek) {
        if (seek.id == 0) return@LaunchedEffect
        val start = withFrameNanos { it }
        effects.seekUntilNs = start + SEEK_GLOW_NS
        do {
            frameNs = withFrameNanos { it }
        } while (frameNs < effects.seekUntilNs)
    }
    Box(
        modifier
            .fillMaxWidth()
            .height(24.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .drawBehind {
                val now = frameNs
                val fraction = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
                val y = size.height / 2
                val track = 2.dp.toPx()
                val headX = size.width * fraction
                drawRect(
                    color = JellyCastColors.Text.copy(alpha = 0.22f),
                    topLeft = Offset(0f, y - track / 2),
                    size = Size(size.width, track),
                )
                drawRect(
                    brush = progressBrush(size.width),
                    topLeft = Offset(0f, y - track / 2),
                    size = Size(headX, track),
                )
                val hotLength = minOf(HOT_TAIL_DP.dp.toPx(), headX)
                if (hotLength > 0f) {
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                HotTail,
                                startX = headX - hotLength,
                                endX = headX,
                            ),
                        topLeft = Offset(headX - hotLength, y - 1.3.dp.toPx()),
                        size = Size(hotLength, 2.6.dp.toPx()),
                    )
                }
                val seeking = now != 0L && now < effects.seekUntilNs
                drawWeldPoint(
                    position = Offset(headX, y),
                    angle = 0f,
                    size =
                        when {
                            seeking -> WELD_SPARK_SEEKING
                            focused -> WELD_SPARK_FOCUSED
                            else -> WELD_SPARK_IDLE
                        },
                    intensity =
                        if (seeking) effects.flicker.next(now) else WELD_SPARK_IDLE_INTENSITY,
                )
            },
    )
}
