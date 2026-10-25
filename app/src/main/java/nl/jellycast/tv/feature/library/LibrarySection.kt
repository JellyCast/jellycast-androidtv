package nl.jellycast.tv.feature.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.rememberCachedLazyListState
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.LibraryKind
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LibrarySection(
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onOpenMovie: (movieId: String) -> Unit,
    onOpenSeries: (seriesId: String) -> Unit,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    val kind = uiState.kind
    LibraryContent(
        uiState = uiState,
        takeFocus = takeFocus,
        onTakeFocusComplete = onTakeFocusComplete,
        onBackToBar = onBackToBar,
        onKindSelect = viewModel::onKindSelect,
        onQueryChange = viewModel::onQueryChange,
        onSortClick = viewModel::onSortClick,
        onTileClick = { tile ->
            when (kind) {
                LibraryKind.Movies -> onOpenMovie(tile.id)
                LibraryKind.Series -> onOpenSeries(tile.id)
            }
        },
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun LibraryContent(
    uiState: LibraryUiState,
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onKindSelect: (LibraryKind) -> Unit,
    onQueryChange: (String) -> Unit,
    onSortClick: () -> Unit,
    onTileClick: (LibraryTile) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
) {
    // Saveable, so Back from the detail lands on the same tile.
    var lastTileId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingTileId by remember { mutableStateOf<String?>(null) }
    var gridHasFocus by remember { mutableStateOf(false) }
    val gridState = rememberCachedLazyListState(ahead = 200.dp)
    val scope = rememberCoroutineScope()
    val kindRequesters = remember { LibraryKind.entries.associateWith { FocusRequester() } }
    val searchRequester = remember { FocusRequester() }
    val sortRequester = remember { FocusRequester() }
    // The default 2D search jumped from column 1 over the controls to the section bar.
    val upTargetsOfFirstLine =
        remember {
            List(LIBRARY_COLUMNS) { column ->
                when (column) {
                    0 -> kindRequesters.getValue(LibraryKind.Movies)
                    1 -> kindRequesters.getValue(LibraryKind.Series)
                    2, 3 -> searchRequester
                    else -> sortRequester
                }
            }
        }
    val content = uiState.content
    val lines = (content as? LibraryContent.Loaded)?.lines.orEmpty()
    val firstTileId = lines.firstOrNull()?.firstOrNull()?.id

    val latestOnTakeFocusComplete by rememberUpdatedState(onTakeFocusComplete)
    if (takeFocus) {
        LaunchedEffect(content is LibraryContent.Loaded) {
            when (content) {
                is LibraryContent.Loaded ->
                    if (firstTileId == null) {
                        kindRequesters.getValue(uiState.kind).requestFocus()
                        latestOnTakeFocusComplete()
                    } else {
                        val restoredLine =
                            lastTileId?.let { lines.lineIndexOf(it) }?.takeIf { it >= 0 }
                        if (restoredLine != null && restoredLine > 0) {
                            gridState.scrollToItem(restoredLine)
                        }
                        pendingTileId = if (restoredLine != null) lastTileId else firstTileId
                    }

                LibraryContent.Error -> latestOnTakeFocusComplete()

                LibraryContent.Loading -> Unit
            }
        }
    }
    LaunchedEffect(uiState.kind, uiState.sort, uiState.query) {
        if (gridState.firstVisibleItemIndex > 0) gridState.scrollToItem(0)
    }

    BackHandler(enabled = gridHasFocus && firstTileId != null) {
        scope.launch { gridState.scrollToItem(0) }
        lastTileId = firstTileId
        onBackToBar()
    }

    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(Dimens.SectionBarHeight))
        LibraryControls(
            uiState = uiState,
            kindRequesters = kindRequesters,
            searchRequester = searchRequester,
            sortRequester = sortRequester,
            onKindSelect = onKindSelect,
            onQueryChange = onQueryChange,
            onSortClick = onSortClick,
        )
        when (content) {
            LibraryContent.Loading -> if (rememberDelayedVisible()) GridSkeleton()

            LibraryContent.Error ->
                ErrorState(
                    message = stringResource(R.string.library_error),
                    onRetry = onRetry,
                    modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 32.dp),
                )

            is LibraryContent.Loaded ->
                if (content.lines.isEmpty()) {
                    Text(
                        text = stringResource(R.string.library_nothing_found, uiState.query),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 32.dp),
                    )
                } else {
                    val latestOnTileClick by rememberUpdatedState(onTileClick)
                    // Stable callbacks: a new lambda per key press would recompose every tile.
                    val callbacks =
                        remember {
                            TileCallbacks(
                                onFocus = { lastTileId = it.id },
                                onClick = { latestOnTileClick(it) },
                                onPendingFocus = { pendingTileId = null },
                            )
                        }
                    Spacer(Modifier.height(GridTop - GridEdge))
                    LibraryGrid(
                        lines = content.lines,
                        gridState = gridState,
                        upTargetsOfFirstLine = upTargetsOfFirstLine,
                        pendingTileId = pendingTileId,
                        callbacks = callbacks,
                        onFocusChanged = {
                            gridHasFocus = it.hasFocus
                            if (it.hasFocus) latestOnTakeFocusComplete()
                        },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    )
                }
        }
    }
}

private fun List<List<LibraryTile>>.lineIndexOf(tileId: String): Int =
    indexOfFirst { line -> line.any { it.id == tileId } }

@Preview(device = Devices.TV_1080p)
@Composable
private fun LibraryContentPreview() {
    JellyCastTheme {
        val tiles = List(20) { LibraryTile("s$it", "Series $it", posterUrl = null) }
        LibraryContent(
            uiState =
                LibraryUiState(
                    kind = LibraryKind.Series,
                    content = LibraryContent.Loaded(tiles.chunked(LIBRARY_COLUMNS), tiles.size),
                ),
            takeFocus = false,
            onTakeFocusComplete = {},
            onKindSelect = {},
            onQueryChange = {},
            onSortClick = {},
            onTileClick = {},
            onRetry = {},
        )
    }
}
