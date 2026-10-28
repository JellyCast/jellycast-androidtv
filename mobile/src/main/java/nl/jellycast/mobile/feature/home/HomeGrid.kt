package nl.jellycast.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import nl.jellycast.mobile.core.designsystem.component.LANDSCAPE_RATIO
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout

/** Columns and spacing of Home's grid for the window it is in. */
internal data class HomeGridSpec(
    val columns: Int,
    val margin: Dp,
    val columnGap: Dp,
    val rowGap: Dp,
    val topGap: Dp,
    /** The featured tile spans two columns and two rows, with the next two tiles beside it. */
    val hasSideStack: Boolean,
) {
    fun tileWidth(width: Dp): Dp = (width - margin * 2 - columnGap * (columns - 1)) / columns
}

internal fun homeGridSpec(isRegular: Boolean, width: Dp, height: Dp): HomeGridSpec = when {
    !isRegular -> HomeGridSpec(2, 20.dp, 12.dp, 20.dp, topGap = 8.dp, hasSideStack = false)
    width > height -> HomeGridSpec(3, 32.dp, 21.dp, 26.dp, topGap = 12.dp, hasSideStack = true)
    else -> HomeGridSpec(2, 32.dp, 20.dp, 26.dp, topGap = 12.dp, hasSideStack = false)
}

private const val IMAGE_WIDTH_STEP_PX = 160
private const val SIDE_STACK_SIZE = 2
private const val FEATURED_KEY_PREFIX = "featured:"

/** Rounded up to a step, so a small change in width still finds the image in the cache. */
@Composable
private fun imageWidthPx(width: Dp): Int {
    val px = with(LocalDensity.current) { width.toPx() }
    return (ceil(px / IMAGE_WIDTH_STEP_PX).toInt() * IMAGE_WIDTH_STEP_PX)
}

@Composable
internal fun HomeGrid(
    tiles: List<HomeTile>,
    unavailableIds: Set<String>,
    onTileClick: (HomeTile) -> Unit,
    onTileLongPress: (HomeTile) -> Unit,
    modifier: Modifier = Modifier,
    featuredOverlay: @Composable () -> Unit = {},
) {
    val isRegular = LocalLayout.current.isRegular
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BoxWithConstraints(modifier.fillMaxSize()) {
        val spec = homeGridSpec(isRegular, maxWidth, maxHeight)
        val tileWidth = spec.tileWidth(maxWidth)
        val smallStyle = tileStyle(isRegular, featured = false)
        val smallImagePx = imageWidthPx(tileWidth)
        val featured = tiles.firstOrNull()
        val stacked =
            if (spec.hasSideStack) tiles.drop(1).take(SIDE_STACK_SIZE) else emptyList()
        val rest = tiles.drop(1 + stacked.size)
        val tileCard: @Composable (HomeTile) -> Unit = { tile ->
            HomeTileCard(
                tile = tile,
                style = smallStyle,
                imageWidthPx = smallImagePx,
                unavailable = tile.itemId in unavailableIds,
                onClick = { onTileClick(tile) },
                onLongPress = { onTileLongPress(tile) },
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(spec.columns),
            contentPadding =
                PaddingValues(
                    start = spec.margin,
                    end = spec.margin,
                    top = statusBar + spec.topGap,
                    bottom = 24.dp,
                ),
            horizontalArrangement = Arrangement.spacedBy(spec.columnGap),
            verticalArrangement = Arrangement.spacedBy(spec.rowGap),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (featured != null) {
                item(
                    key = FEATURED_KEY_PREFIX + featured.contentId,
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "featured",
                ) {
                    FeaturedBlock(
                        featured = featured,
                        stacked = stacked,
                        spec = spec,
                        tileWidth = tileWidth,
                        unavailableIds = unavailableIds,
                        onTileClick = onTileClick,
                        onTileLongPress = onTileLongPress,
                        tileCard = tileCard,
                        overlay = featuredOverlay,
                    )
                }
            }
            items(rest, key = { it.contentId }, contentType = { "tile" }) { tileCard(it) }
        }
    }
}

/**
 * The first tile over the full width, or in landscape over two columns with [stacked] beside it;
 * its image then fills the height of the stack, so both end on the same line.
 */
@Composable
private fun FeaturedBlock(
    featured: HomeTile,
    stacked: List<HomeTile>,
    spec: HomeGridSpec,
    tileWidth: Dp,
    unavailableIds: Set<String>,
    onTileClick: (HomeTile) -> Unit,
    onTileLongPress: (HomeTile) -> Unit,
    tileCard: @Composable (HomeTile) -> Unit,
    overlay: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRegular = LocalLayout.current.isRegular
    val style = tileStyle(isRegular, featured = true)
    if (!spec.hasSideStack) {
        Box(modifier.fillMaxWidth()) {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                HomeTileCard(
                    tile = featured,
                    style = style,
                    imageWidthPx = imageWidthPx(maxWidth),
                    unavailable = featured.itemId in unavailableIds,
                    onClick = { onTileClick(featured) },
                    onLongPress = { onTileLongPress(featured) },
                )
            }
            Box(Modifier.align(Alignment.TopEnd)) { overlay() }
        }
        return
    }
    val featuredWidth = tileWidth * 2 + spec.columnGap
    val smallBlock = tileWidth / LANDSCAPE_RATIO + tileStyle(isRegular, false).textBlockHeight()
    val stackHeight = smallBlock * SIDE_STACK_SIZE + spec.rowGap * (SIDE_STACK_SIZE - 1)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spec.columnGap)) {
        HomeTileCard(
            tile = featured,
            style = style,
            imageWidthPx = imageWidthPx(featuredWidth),
            unavailable = featured.itemId in unavailableIds,
            onClick = { onTileClick(featured) },
            onLongPress = { onTileLongPress(featured) },
            imageHeight = stackHeight - style.textBlockHeight(),
            modifier = Modifier.width(featuredWidth),
        )
        Column(Modifier.width(tileWidth), verticalArrangement = Arrangement.spacedBy(spec.rowGap)) {
            stacked.forEach { tileCard(it) }
        }
    }
}
