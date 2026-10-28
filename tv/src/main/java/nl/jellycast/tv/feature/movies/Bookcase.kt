package nl.jellycast.tv.feature.movies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Shelf
import nl.jellycast.core.model.ShelfKind
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.NoScrollBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.ProvideBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberCachedLazyListState
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.ui.stopHorizontalExit

// About five spines on both sides, so a spine that slides into view is already composed.
private val SpinePrefetchDistance = 240.dp

// A spine that was just composed can be one frame too early for focus: retry on the next frame.
private const val FOCUS_REQUEST_FRAME_ATTEMPTS = 3

private const val SKELETON_SPINE_COUNT = 9

@Composable
internal fun Bookcase(
    uiState: MoviesUiState.Success,
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onShelfChange: (Int) -> Unit,
    onPlayClick: (Movie) -> Unit,
    onDetailsClick: (Movie) -> Unit,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
) {
    val shelf = uiState.shelf
    val showProgress = shelf.kind == ShelfKind.ContinueWatching
    val listState = rememberCachedLazyListState(ahead = SpinePrefetchDistance)
    val state = rememberBookcaseState(listState, uiState.shelfIndex)
    // A State, so key repeat recomposes only the panel and the two affected spines.
    val openIndexState =
        remember(shelf) {
            derivedStateOf {
                shelf.items.indexOfFirst { it.id == state.focusedMovieId }.coerceAtLeast(0)
            }
        }

    if (takeFocus) {
        LaunchedEffect(Unit) { state.takeFocus(shelf, openIndexState.value) }
    }
    LaunchedEffect(uiState.shelfIndex) { state.onShelfIndexChanged(uiState.shelfIndex, shelf) }
    BackHandler(enabled = state.hasFocus) {
        state.resetToStart(uiState.shelfIndex, shelf.items.first().id, onShelfChange)
        onBackToBar()
    }

    Box(
        modifier =
            modifier
                .height(BookcaseDimens.Height)
                // Own layer: scrolling the bookcase leaves the rest of Home untouched.
                .graphicsLayer()
                .onFocusChanged { state.hasFocus = it.hasFocus }
                .bookcaseKeys(state, uiState.shelves.lastIndex, onShelfChange),
    ) {
        BookcaseFocusAnchor(state)
        val callbacks =
            rememberBookcaseCallbacks(
                state = state,
                shelf = shelf,
                openIndex = { openIndexState.value },
                onTakeFocusComplete = onTakeFocusComplete,
                onPlayClick = onPlayClick,
                onDetailsClick = onDetailsClick,
            )
        BookcaseRow(
            uiState = uiState,
            listState = listState,
            pendingFocusMovieId = { state.pendingFocusMovieId },
            openIndex = { openIndexState.value },
            showProgress = showProgress,
            callbacks = callbacks,
        )
        BookcasePanel(
            shelf = shelf,
            openIndex = { openIndexState.value },
            panelButton = state.panelButton,
            showProgress = showProgress,
            spineStepPx = state.spineStepPx,
            panelOffsetPx = state.panelOffsetPx,
        )
        BookcaseEdges(
            listState = listState,
            itemCount = shelf.items.size,
            spineStepPx = state.spineStepPx,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun BookcaseFocusAnchor(state: BookcaseState) {
    Box(
        Modifier
            .padding(start = Dimens.SafeHorizontal)
            .size(1.dp)
            .focusProperties { canFocus = state.isAnchorHoldingFocus }
            .focusRequester(state.focusAnchor)
            .focusable(),
    )
}

private class BookcaseCallbacks(
    val onFocus: (Int, Movie) -> Unit,
    val onPendingFocus: () -> Unit,
    val onClick: (Movie) -> Unit,
)

// Stable callbacks: a new lambda per step would recompose every spine.
@Composable
private fun rememberBookcaseCallbacks(
    state: BookcaseState,
    shelf: Shelf<Movie>,
    openIndex: () -> Int,
    onTakeFocusComplete: () -> Unit,
    onPlayClick: (Movie) -> Unit,
    onDetailsClick: (Movie) -> Unit,
): BookcaseCallbacks {
    val latestOnPendingFocus by rememberUpdatedState {
        state.onPendingFocusTaken(shelf, openIndex(), onTakeFocusComplete)
    }
    val latestOnMovieClick by rememberUpdatedState { movie: Movie ->
        when (state.panelButton) {
            PanelButton.Play -> onPlayClick(movie)
            PanelButton.Details -> onDetailsClick(movie)
        }
    }
    return remember {
        BookcaseCallbacks(
            onFocus = { index, movie -> state.onMovieFocused(index, movie) },
            onPendingFocus = { latestOnPendingFocus() },
            onClick = { movie -> latestOnMovieClick(movie) },
        )
    }
}

@Composable
private fun BookcasePanel(
    shelf: Shelf<Movie>,
    openIndex: () -> Int,
    panelButton: PanelButton,
    showProgress: Boolean,
    spineStepPx: Int,
    panelOffsetPx: Int,
    modifier: Modifier = Modifier,
) {
    val index = openIndex()
    val startPx = with(LocalDensity.current) { Dimens.SafeHorizontal.roundToPx() }
    val panelX = startPx + minOf(index * spineStepPx, panelOffsetPx)
    MoviePanel(
        movie = shelf.items[index],
        panelButton = panelButton,
        showProgress = showProgress,
        modifier = modifier.offset { IntOffset(panelX, 0) }.graphicsLayer(),
    )
}

@Composable
private fun BookcaseRow(
    uiState: MoviesUiState.Success,
    listState: LazyListState,
    pendingFocusMovieId: () -> String?,
    openIndex: () -> Int,
    showProgress: Boolean,
    callbacks: BookcaseCallbacks,
    modifier: Modifier = Modifier,
) {
    val shelf = uiState.shelf
    ProvideBringIntoViewSpec(NoScrollBringIntoViewSpec) {
        LazyRow(
            state = listState,
            userScrollEnabled = false,
            contentPadding =
                PaddingValues(start = Dimens.SafeHorizontal, end = BookcaseDimens.EndPadding),
            horizontalArrangement = Arrangement.spacedBy(BookcaseDimens.SpineGap),
            modifier = modifier.height(BookcaseDimens.Height).stopHorizontalExit(),
        ) {
            itemsIndexed(
                items = shelf.items,
                key = { _, movie -> movie.id },
            ) { index, movie ->
                val requester = remember { FocusRequester() }
                val isOpen by remember(index) { derivedStateOf { openIndex() == index } }
                val isPendingFocus by remember(movie.id) {
                    derivedStateOf { pendingFocusMovieId() == movie.id }
                }
                if (isPendingFocus) {
                    LaunchedEffect(Unit) {
                        repeat(FOCUS_REQUEST_FRAME_ATTEMPTS) {
                            if (requester.requestFocus(FocusDirection.Enter)) {
                                callbacks.onPendingFocus()
                                return@LaunchedEffect
                            }
                            withFrameNanos { }
                        }
                    }
                }
                BookcaseItem(
                    movie = movie,
                    isOpen = isOpen,
                    showProgress = showProgress,
                    onClick = { callbacks.onClick(movie) },
                    onFocus = { callbacks.onFocus(index, movie) },
                    modifier = Modifier.focusRequester(requester),
                )
            }
            uiState.shelves.getOrNull(uiState.shelfIndex + 1)?.let { next ->
                item(key = "next-shelf", contentType = "hint") {
                    NextShelfHint(name = shelfTitle(next.kind))
                }
            }
        }
    }
}

// An open item only makes room for the one panel overlay, so no spine is rebuilt per step. No
// tv-material Surface: its focus scale, border and animations would only cost frames here.
@Composable
private fun BookcaseItem(
    movie: Movie,
    isOpen: Boolean,
    showProgress: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .width(if (isOpen) BookcaseDimens.PanelWidth else BookcaseDimens.SpineWidth)
                .onFocusChanged { if (it.isFocused) onFocus() }
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onClick,
                ),
    ) {
        MovieSpine(movie = movie, showProgress = showProgress)
    }
}

@Composable
private fun NextShelfHint(name: String, modifier: Modifier = Modifier) {
    Box(modifier.padding(start = 40.dp).width(220.dp).height(BookcaseDimens.Height)) {
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(bottom = 60.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.movies_next_shelf).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_down),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
internal fun BookcaseSkeleton(modifier: Modifier = Modifier) {
    val clock = rememberSkeletonClock()
    val color = MaterialTheme.colorScheme.surface
    Row(
        modifier = modifier.padding(start = Dimens.SafeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(BookcaseDimens.SpineGap),
    ) {
        Box(
            Modifier
                .size(BookcaseDimens.PanelWidth, BookcaseDimens.Height)
                .skeletonBlock(clock, 0, color, 0.dp),
        )
        repeat(SKELETON_SPINE_COUNT) { index ->
            Box(
                Modifier
                    .size(BookcaseDimens.SpineWidth, BookcaseDimens.Height)
                    .skeletonBlock(clock, index + 1, color, 0.dp),
            )
        }
    }
}
