package nl.jellycast.mobile.feature.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.jellycast.mobile.core.designsystem.component.BrandMark
import nl.jellycast.mobile.core.designsystem.component.BrandWordmark
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme

private const val DRAW_MS = 600
private const val WORDMARK_DELAY_MS = 500L
private const val WORDMARK_FADE_MS = 250
private const val HOLD_MS = 250L
private val DrawEasing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

/**
 * Shown once per launch on top of the start screen: the brand mark draws itself in, the
 * wordmark fades in, and [onFinish] follows after a short hold. A tap anywhere skips it. The
 * window background is the same color, so the hand-over from the system splash is invisible.
 */
@Composable
fun BrandSplash(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val currentOnFinish by rememberUpdatedState(onFinish)
    val draw = remember { Animatable(0f) }
    val wordmark = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        coroutineScope {
            launch { draw.animateTo(1f, tween(DRAW_MS, easing = DrawEasing)) }
            delay(WORDMARK_DELAY_MS)
            launch { wordmark.animateTo(1f, tween(WORDMARK_FADE_MS, easing = LinearEasing)) }
            delay(DRAW_MS - WORDMARK_DELAY_MS + HOLD_MS)
        }
        currentOnFinish()
    }
    Column(
        modifier
            .fillMaxSize()
            .background(JellyCastColors.Background)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { currentOnFinish() },
            ),
        verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandMark(Modifier.size(132.dp), drawProgress = { draw.value })
        BrandWordmark(Modifier.graphicsLayer { alpha = wordmark.value })
    }
}

@Preview
@Composable
private fun BrandSplashPreview() {
    JellyCastTheme { BrandSplash(onFinish = {}) }
}
