package nl.jellycast.tv.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.FocusState
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.FocusCard
import nl.jellycast.tv.core.designsystem.component.ProvideBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberColumnBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.ui.stopHorizontalExit

// Only GridEdge of the gap above the grid lies inside the list (room for the scaled focus
// border); the rest sits above it, otherwise titles of the previous line scroll visibly under
// the controls.
internal val GridTop = 18.dp
internal val GridEdge = 6.dp

private val TileWidth = 92.dp
private val TileHeight = 138.dp
private val TileGap = 18.dp
private val LineGap = 16.dp
private const val SKELETON_LINE_COUNT = 2

internal class TileCallbacks(
    val onFocus: (LibraryTile) -> Unit,
    val onClick: (LibraryTile) -> Unit,
    val onPendingFocus: () -> Unit,
)

@Composable
internal fun LibraryGrid(
    lines: List<List<LibraryTile>>,
    gridState: LazyListState,
    upTargetsOfFirstLine: List<FocusRequester>,
    pendingTileId: String?,
    callbacks: TileCallbacks,
    onFocusChanged: (FocusState) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProvideBringIntoViewSpec(rememberColumnBringIntoViewSpec(cardTop = GridEdge)) {
        LazyColumn(
            state = gridState,
            contentPadding = PaddingValues(top = GridEdge, bottom = Dimens.SafeVertical),
            verticalArrangement = Arrangement.spacedBy(LineGap),
            modifier = modifier.onFocusChanged(onFocusChanged),
        ) {
            itemsIndexed(
                items = lines,
                key = { index, _ -> index },
                contentType = { _, _ -> "line" },
            ) { index, line ->
                GridLine(
                    tiles = line,
                    upTargets = if (index == 0) upTargetsOfFirstLine else null,
                    pendingTileId = pendingTileId,
                    callbacks = callbacks,
                )
            }
        }
    }
}

// Within the grid ↑/↓ keep the column (default 2D search); entering from the controls above
// lands on tile 1.
@Composable
private fun GridLine(
    tiles: List<LibraryTile>,
    upTargets: List<FocusRequester>?,
    pendingTileId: String?,
    callbacks: TileCallbacks,
    modifier: Modifier = Modifier,
) {
    val isFirstLine = upTargets != null
    val requesters = remember(tiles.size) { List(tiles.size) { FocusRequester() } }
    Row(
        modifier =
            modifier
                .padding(horizontal = Dimens.SafeHorizontal)
                .focusProperties {
                    onEnter = {
                        if (isFirstLine && requestedFocusDirection == FocusDirection.Down) {
                            requesters.first().requestFocus()
                        }
                    }
                }.stopHorizontalExit(),
        horizontalArrangement = Arrangement.spacedBy(TileGap),
    ) {
        tiles.forEachIndexed { index, tile ->
            if (tile.id == pendingTileId) {
                LaunchedEffect(Unit) {
                    requesters[index].requestFocus()
                    callbacks.onPendingFocus()
                }
            }
            Tile(
                tile = tile,
                callbacks = callbacks,
                posterModifier =
                    Modifier
                        .focusRequester(requesters[index])
                        .then(
                            if (upTargets != null) {
                                Modifier.focusProperties { up = upTargets[index] }
                            } else {
                                Modifier
                            },
                        ),
            )
        }
    }
}

// The title color reads focus only in the draw phase (BasicText color lambda), so a focus
// change recomposes nothing.
@Composable
private fun Tile(
    tile: LibraryTile,
    callbacks: TileCallbacks,
    modifier: Modifier = Modifier,
    posterModifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val bright = MaterialTheme.colorScheme.onSurface
    Column(
        modifier = modifier.width(TileWidth).onFocusChanged { focused = it.hasFocus },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        FocusCard(
            onClick = { callbacks.onClick(tile) },
            onFocus = { callbacks.onFocus(tile) },
            modifier = posterModifier.size(TileWidth, TileHeight),
            containerColor = Color.Transparent,
        ) {
            ArtworkImage(
                url = tile.posterUrl,
                fallbackTitle = tile.title,
                modifier = Modifier.fillMaxSize(),
            )
        }
        BasicText(
            text = tile.title,
            style = MaterialTheme.typography.bodySmall,
            color = { if (focused) bright else muted },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun GridSkeleton(modifier: Modifier = Modifier) {
    val clock = rememberSkeletonClock()
    val color = MaterialTheme.colorScheme.surface
    Column(
        modifier = modifier.padding(start = Dimens.SafeHorizontal, top = GridTop),
        verticalArrangement = Arrangement.spacedBy(LineGap + 22.dp),
    ) {
        repeat(SKELETON_LINE_COUNT) {
            Row(horizontalArrangement = Arrangement.spacedBy(TileGap)) {
                repeat(LIBRARY_COLUMNS) { index ->
                    Box(Modifier.size(TileWidth, TileHeight).skeletonBlock(clock, index, color))
                }
            }
        }
    }
}
