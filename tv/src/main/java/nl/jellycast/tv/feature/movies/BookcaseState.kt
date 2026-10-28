package nl.jellycast.tv.feature.movies

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Shelf

// The first visible item and its offset in px that put the panel of `index` in place.
internal fun bookcaseScrollPosition(index: Int, stepPx: Int, panelOffsetPx: Int): Pair<Int, Int> {
    val scroll = (index * stepPx - panelOffsetPx).coerceAtLeast(0)
    return scroll / stepPx to scroll % stepPx
}

@Composable
internal fun rememberBookcaseState(
    listState: LazyListState,
    initialShelfIndex: Int,
): BookcaseState {
    val density = LocalDensity.current
    val spineStepPx =
        with(density) { (BookcaseDimens.SpineWidth + BookcaseDimens.SpineGap).roundToPx() }
    val panelOffsetPx = with(density) { BookcaseDimens.PanelOffsetFromMargin.roundToPx() }
    val focusedMovieId = rememberSaveable { mutableStateOf<String?>(null) }
    // Saveable so Back from the movie detail lands on Details again.
    val panelButton = rememberSaveable { mutableStateOf(PanelButton.Play) }
    return remember {
        BookcaseState(
            listState = listState,
            spineStepPx = spineStepPx,
            panelOffsetPx = panelOffsetPx,
            focusedMovieIdState = focusedMovieId,
            panelButtonState = panelButton,
            initialShelfIndex = initialShelfIndex,
        )
    }
}

internal class BookcaseState(
    private val listState: LazyListState,
    val spineStepPx: Int,
    val panelOffsetPx: Int,
    focusedMovieIdState: MutableState<String?>,
    panelButtonState: MutableState<PanelButton>,
    initialShelfIndex: Int,
) {
    var focusedMovieId by focusedMovieIdState
        private set

    // Not real focus: focus stays on the movie so ←/→ remains one press per movie.
    var panelButton by panelButtonState
        private set

    var pendingFocusMovieId by mutableStateOf<String?>(null)
        private set

    var hasFocus by mutableStateOf(false)

    // Updated on every key press, so a second ↓ that arrives before recomposition still counts.
    private var requestedShelf by mutableIntStateOf(initialShelfIndex)
    private var focusFirstOnShelfChange by mutableStateOf(false)

    // While a shelf switch removes the focused spine, the anchor keeps focus inside the bookcase
    // and ←/→ are queued, then replayed in order once the new spine has focus.
    val focusAnchor = FocusRequester()
    var isAnchorHoldingFocus by mutableStateOf(false)
        private set
    private var queuedSteps by mutableIntStateOf(0)

    // Own bookkeeping: under key repeat the next step can arrive before the list is measured.
    private var scrollPx =
        listState.firstVisibleItemIndex * spineStepPx + listState.firstVisibleItemScrollOffset

    fun takeFocus(shelf: Shelf<Movie>, openIndex: Int) {
        val target = shelf.items.getOrNull(openIndex) ?: shelf.items.first()
        jumpPanelTo(shelf.items.indexOf(target))
        pendingFocusMovieId = target.id
    }

    fun onPendingFocusTaken(shelf: Shelf<Movie>, openIndex: Int, onTakeFocusComplete: () -> Unit) {
        pendingFocusMovieId = null
        focusFirstOnShelfChange = false
        if (queuedSteps != 0) {
            val target = (openIndex + queuedSteps).coerceIn(0, shelf.items.lastIndex)
            queuedSteps = 0
            moveFocusTo(target, shelf)
        } else {
            isAnchorHoldingFocus = false
            onTakeFocusComplete()
        }
    }

    fun onMovieFocused(index: Int, movie: Movie) {
        // The same movie again (back from the detail) keeps the chosen button.
        if (movie.id != focusedMovieId) panelButton = PanelButton.Play
        focusedMovieId = movie.id
        scrollPanelTo(index)
    }

    fun onShelfIndexChanged(shelfIndex: Int, shelf: Shelf<Movie>) {
        val isWaitingForRequestedShelf = focusFirstOnShelfChange
        when {
            isWaitingForRequestedShelf && shelfIndex == requestedShelf -> moveFocusTo(0, shelf)

            // Changed without a key press, e.g. Continue watching became empty after a refresh.
            !isWaitingForRequestedShelf -> requestedShelf = shelfIndex
        }
    }

    fun resetToStart(shelfIndex: Int, firstMovieId: String, onShelfChange: (Int) -> Unit) {
        panelButton = PanelButton.Play
        if (shelfIndex != 0) {
            focusFirstOnShelfChange = false
            requestedShelf = 0
            onShelfChange(0)
        }
        jumpPanelTo(0)
        focusedMovieId = firstMovieId
    }

    fun queueStepWhileSwitching(step: Int): Boolean {
        if (!isAnchorHoldingFocus) return false
        queuedSteps += step
        return true
    }

    fun moveDown(lastShelfIndex: Int, onShelfChange: (Int) -> Unit) {
        when {
            !isAnchorHoldingFocus && panelButton == PanelButton.Play ->
                panelButton = PanelButton.Details

            requestedShelf < lastShelfIndex -> changeShelf(requestedShelf + 1, onShelfChange)
        }
    }

    fun moveUp(onShelfChange: (Int) -> Unit): Boolean = when {
        !isAnchorHoldingFocus && panelButton == PanelButton.Details -> {
            panelButton = PanelButton.Play
            true
        }

        requestedShelf > 0 -> {
            changeShelf(requestedShelf - 1, onShelfChange)
            true
        }

        // Still switching: ↑ must not escape to the section bar.
        else -> isAnchorHoldingFocus
    }

    private fun changeShelf(index: Int, onShelfChange: (Int) -> Unit) {
        panelButton = PanelButton.Play
        holdFocusOnAnchor()
        focusFirstOnShelfChange = true
        requestedShelf = index
        onShelfChange(index)
    }

    private fun moveFocusTo(index: Int, shelf: Shelf<Movie>) {
        panelButton = PanelButton.Play
        holdFocusOnAnchor()
        jumpPanelTo(index)
        focusedMovieId = shelf.items[index].id
        pendingFocusMovieId = shelf.items[index].id
    }

    private fun holdFocusOnAnchor() {
        isAnchorHoldingFocus = true
        focusAnchor.requestFocus()
    }

    private fun jumpPanelTo(index: Int) {
        val (item, offset) = bookcaseScrollPosition(index, spineStepPx, panelOffsetPx)
        listState.requestScrollToItem(item, offset)
        scrollPx = item * spineStepPx + offset
    }

    // Scrolls instead of jumping, so the list prefetches the next spines between key repeats.
    private fun scrollPanelTo(index: Int) {
        val (item, offset) = bookcaseScrollPosition(index, spineStepPx, panelOffsetPx)
        val target = item * spineStepPx + offset
        val delta = target - scrollPx
        scrollPx = target
        if (delta != 0) listState.dispatchRawDelta(delta.toFloat())
    }
}
