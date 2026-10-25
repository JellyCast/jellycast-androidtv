package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp

/**
 * Keeps the focused item at a fixed spot: the list moves, the focus does not. [fraction] is that
 * spot measured from the start of the list (0.3 = 30% from the left).
 */
class PivotBringIntoViewSpec(private val fraction: Float, private val offsetPx: Float = 0f) :
    BringIntoViewSpec {
    override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float =
        offset - (containerSize * fraction + offsetPx)
}

val RowBringIntoViewSpec = PivotBringIntoViewSpec(fraction = 0.3f)

/** For lists that position themselves, such as the bookcase. */
val NoScrollBringIntoViewSpec =
    object : BringIntoViewSpec {
        override fun calculateScrollDistance(
            offset: Float,
            size: Float,
            containerSize: Float,
        ): Float = 0f
    }

/** Puts the focused card [cardTop] below the top, so the row header above it stays visible. */
@Composable
fun rememberColumnBringIntoViewSpec(cardTop: Dp): BringIntoViewSpec {
    val density = LocalDensity.current
    return remember(density, cardTop) {
        PivotBringIntoViewSpec(fraction = 0f, offsetPx = with(density) { cardTop.toPx() })
    }
}

/** Keeps the opt-in for the experimental `LocalBringIntoViewSpec` in one place. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ProvideBringIntoViewSpec(spec: BringIntoViewSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBringIntoViewSpec provides spec, content = content)
}

/**
 * Keeps [ahead] and [behind] composed and measured off screen, so during key repeat an item that
 * scrolls into view does not have to be built in that same frame.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun rememberCachedLazyListState(ahead: Dp, behind: Dp = ahead): LazyListState =
    rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(ahead = ahead, behind = behind))
