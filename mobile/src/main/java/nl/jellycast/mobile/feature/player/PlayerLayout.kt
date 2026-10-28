package nl.jellycast.mobile.feature.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt

const val VIDEO_ASPECT = 16f / 9f

/** The swipe-down transform: scale 1 − 0.5 × progress around the top centre, moved down. */
data class SwipeTransform(val offsetPx: Float, val heightPx: Float) {
    val progress: Float get() = if (heightPx > 0) (offsetPx / heightPx).coerceIn(0f, 1f) else 0f
    val scale: Float get() = 1f - SWIPE_SCALE * progress

    fun apply(rect: Rect, widthPx: Float): Rect {
        val centre = widthPx / 2
        return Rect(
            left = centre + (rect.left - centre) * scale,
            top = rect.top * scale + offsetPx,
            right = centre + (rect.right - centre) * scale,
            bottom = rect.bottom * scale + offsetPx,
        )
    }

    companion object {
        const val SWIPE_SCALE = 0.5f
    }
}

enum class SwipeDownOutcome { SpringBack, LeaveFullScreen, Minimize }

/**
 * A swipe down that goes far or fast enough leaves full screen for the player panel, and from
 * the panel minimises to the mini player; anything shorter springs back.
 */
fun swipeDownOutcome(
    offsetPx: Float,
    velocityPxPerS: Float,
    closeDistancePx: Float,
    heightPx: Float,
    fullScreen: Boolean,
): SwipeDownOutcome {
    val predicted = offsetPx + velocityPxPerS * SWIPE_PREDICTION_S
    val far = offsetPx > closeDistancePx || predicted > heightPx / 2
    return when {
        !far -> SwipeDownOutcome.SpringBack
        fullScreen -> SwipeDownOutcome.LeaveFullScreen
        else -> SwipeDownOutcome.Minimize
    }
}

private const val SWIPE_PREDICTION_S = 0.25f

/** The 16:9 video under the status bar, full width (the portrait player). */
fun pinnedVideoRect(widthPx: Float, topPx: Float): Rect =
    Rect(0f, topPx, widthPx, topPx + widthPx / VIDEO_ASPECT)

/** The mini player at the bottom end, [scale] applied around that corner. */
fun miniPlayerRect(
    containerWidthPx: Float,
    containerHeightPx: Float,
    widthPx: Float,
    endMarginPx: Float,
    bottomOffsetPx: Float,
    scale: Float,
): Rect {
    val width = widthPx * scale
    val height = width / VIDEO_ASPECT
    val right = containerWidthPx - endMarginPx
    val bottom = containerHeightPx - bottomOffsetPx
    return Rect(right - width, bottom - height, right, bottom)
}

/**
 * Places and sizes the element at [rect] inside a parent that fills the host, read in the layout
 * phase only, so the player clock or a swipe never recomposes it.
 */
fun Modifier.placeAt(rect: () -> Rect): Modifier = layout { measurable, constraints ->
    val bounds = rect()
    val width = bounds.width.roundToInt().coerceAtLeast(0)
    val height = bounds.height.roundToInt().coerceAtLeast(0)
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(constraints.maxWidth, constraints.maxHeight) {
        placeable.place(IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()))
    }
}
