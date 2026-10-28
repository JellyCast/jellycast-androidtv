package nl.jellycast.tv.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

/** Two consecutive frames within this interval count as steady (one 60 Hz frame is 16.7 ms). */
private const val STEADY_FRAME_NS = 25_000_000L
private const val STEADY_FRAMES_NEEDED = 2
private const val MAX_WAIT_FRAMES = 60
private const val DRAW_MS = 600
private const val WORDMARK_DELAY_MS = 500
private const val WORDMARK_FADE_MS = 250
private const val HOLD_MS = 250L
private const val GAP_DP = 28
private const val MARK_SIZE_DP = 132

/**
 * Build the heavy start screen only after [onDraw]: composing it at the same time makes the line
 * stutter. A plain Compose screen, because the SplashScreen API has no custom animation on TV.
 */
@Composable
fun BrandSplash(onDraw: () -> Unit, onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val draw = remember { Animatable(0f) }
    val wordmark = remember { Animatable(0f) }
    val currentOnDraw by rememberUpdatedState(onDraw)
    val currentOnFinish by rememberUpdatedState(onFinish)

    LaunchedEffect(Unit) {
        awaitSteadyFrames()
        draw.animateTo(1f, tween(DRAW_MS, easing = FastOutSlowInEasing))
        currentOnDraw()
        delay(HOLD_MS)
        currentOnFinish()
    }
    LaunchedEffect(Unit) {
        delay(WORDMARK_DELAY_MS.toLong())
        wordmark.animateTo(1f, tween(WORDMARK_FADE_MS, easing = LinearEasing))
    }

    Column(
        modifier = modifier.fillMaxSize().background(JellyCastColors.Background),
        verticalArrangement = Arrangement.spacedBy(GAP_DP.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(Modifier.size(MARK_SIZE_DP.dp), drawProgress = { draw.value })
        BrandWordmark(Modifier.graphicsLayer { alpha = wordmark.value })
    }
}

/**
 * The app's first frames on the Chromecast are long and irregular; an animation that starts
 * earlier skips over them in one jump.
 */
private suspend fun awaitSteadyFrames() {
    var previous = withFrameNanos { it }
    var steady = 0
    repeat(MAX_WAIT_FRAMES) {
        val now = withFrameNanos { it }
        steady = if (now - previous < STEADY_FRAME_NS) steady + 1 else 0
        if (steady == STEADY_FRAMES_NEEDED) return
        previous = now
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun BrandSplashPreview() {
    JellyCastTheme {
        BrandSplash(onDraw = {}, onFinish = {})
    }
}
