package nl.jellycast.tv.feature.movies

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.Dimens

private val EdgeFadeWidth = 120.dp

// Fixed narrow overlays at the screen edges; they only change when the overflow flips,
// not on every step.
@Composable
internal fun BookcaseEdges(
    listState: LazyListState,
    itemCount: Int,
    spineStepPx: Int,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val startPx = with(density) { Dimens.SafeHorizontal.roundToPx() }
    val extraPanelPx =
        with(density) { (BookcaseDimens.PanelWidth - BookcaseDimens.SpineWidth).roundToPx() }
    val gapPx = with(density) { BookcaseDimens.SpineGap.roundToPx() }
    val screenPx = with(density) { BookcaseDimens.ScreenWidth.roundToPx() }
    // Every item before the first visible one is a spine of equal width.
    val scrolledPx by remember(spineStepPx) {
        derivedStateOf {
            listState.firstVisibleItemIndex * spineStepPx + listState.firstVisibleItemScrollOffset
        }
    }
    val hasMoviesLeft by remember { derivedStateOf { scrolledPx > startPx } }
    val hasMoviesRight by remember(itemCount) {
        derivedStateOf {
            val contentEnd =
                startPx - scrolledPx + itemCount * spineStepPx - gapPx + extraPanelPx
            contentEnd > screenPx
        }
    }
    Box(modifier) {
        if (hasMoviesLeft) {
            EdgeFade(isStart = true, modifier = Modifier.align(Alignment.CenterStart))
        }
        if (hasMoviesRight) {
            EdgeFade(isStart = false, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun EdgeFade(isStart: Boolean, modifier: Modifier = Modifier) {
    val background = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                // Own layer, so scrolling the bookcase underneath does not re-record the fade.
                .graphicsLayer()
                .width(EdgeFadeWidth)
                .fillMaxHeight()
                .drawWithCache {
                    val opaqueAtScreenEdge =
                        arrayOf(
                            0f to background,
                            0.45f to background.copy(alpha = 0.85f),
                            1f to background.copy(alpha = 0f),
                        )
                    val brush =
                        if (isStart) {
                            Brush.horizontalGradient(*opaqueAtScreenEdge)
                        } else {
                            Brush.horizontalGradient(
                                *opaqueAtScreenEdge,
                                startX = size.width,
                                endX = 0f,
                            )
                        }
                    onDrawBehind { drawRect(brush) }
                },
        contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Icon(
            painter =
                painterResource(
                    if (isStart) R.drawable.ic_chevron_left else R.drawable.ic_chevron_right,
                ),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp).size(24.dp),
        )
    }
}
