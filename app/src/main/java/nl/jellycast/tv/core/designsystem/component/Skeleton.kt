package nl.jellycast.tv.core.designsystem.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlinx.coroutines.delay

/** Data that arrives sooner shows directly, without a flash of skeleton. */
const val SKELETON_DELAY_MS = 100L

private const val WAVE_PERIOD_MS = 2_000f
private const val WAVE_STEP_MS = 120
private const val ALPHA_MIN = 0.5f
private const val ALPHA_MAX = 0.85f

@Composable
fun rememberDelayedVisible(delayMs: Long = SKELETON_DELAY_MS): Boolean {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(delayMs)
        visible = true
    }
    return visible
}

/**
 * One clock (ms since the skeleton appeared) shared by all blocks. Blocks read it only in the
 * draw phase, so the wave never recomposes.
 */
@Composable
fun rememberSkeletonClock(): MutableLongState {
    val clock = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) withFrameMillis { clock.longValue = it - start }
    }
    return clock
}

/**
 * Pulses with a wave delayed by [index]. Only the colour changes in `drawBehind`: an alpha layer
 * would need an offscreen buffer.
 */
fun Modifier.skeletonBlock(
    clock: MutableLongState,
    index: Int,
    color: Color,
    cornerRadius: Dp = 8.dp,
): Modifier = drawBehind {
    val t = (clock.longValue - index * WAVE_STEP_MS).coerceAtLeast(0)
    val phase = 0.5f - 0.5f * cos(2 * PI.toFloat() * t / WAVE_PERIOD_MS)
    val alpha = ALPHA_MIN + (ALPHA_MAX - ALPHA_MIN) * phase
    val radius = cornerRadius.toPx()
    drawRoundRect(color = color.copy(alpha = alpha), cornerRadius = CornerRadius(radius))
}
