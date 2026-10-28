package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.ui.isArrowKey
import nl.jellycast.tv.core.ui.stopHorizontalExit

/** Keeps two cards composed ahead for key repeat. */
private val PrefetchAhead = 280.dp
private val HeaderGap = 12.dp

/**
 * Entering the row with up/down always focuses item 1 and scrolls back to the start; there is no
 * wrap-around. [pendingKey] requests focus as soon as that item is composed (opening, returning)
 * and reports it with [onPendingFocus].
 */
@Composable
fun <T> TvRow(
    items: List<T>,
    key: (T) -> Any,
    pendingKey: Any?,
    onPendingFocus: () -> Unit,
    header: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    contentType: (T) -> Any? = { null },
    itemContent: @Composable (item: T, modifier: Modifier) -> Unit,
) {
    val rowState = rememberCachedLazyListState(ahead = PrefetchAhead)
    val firstItem = remember { FocusRequester() }
    val scope = rememberCoroutineScope()

    val currentKey by rememberUpdatedState(key)
    val focused = remember { FocusedKey<T>() }
    var refocusKey by remember { mutableStateOf<Any?>(null) }
    focused.followNewItems(items, key, rowState)?.let { refocusKey = it }
    if (pendingKey != null) {
        LaunchedEffect(pendingKey) {
            val index = items.indexOfFirst { currentKey(it) == pendingKey }
            val visible = rowState.layoutInfo.visibleItemsInfo.any { it.index == index }
            if (index >= 0 && !visible) rowState.scrollToItem(index)
        }
    }

    Column(
        modifier =
            modifier
                .focusProperties {
                    onEnter = {
                        if (!isArrowKey) {
                            // A focus request from code (returning) is never redirected.
                        } else if (rowState.firstVisibleItemIndex == 0) {
                            firstItem.requestFocus()
                        } else {
                            // Item 1 is not composed: scroll back to the start first.
                            cancelFocusChange()
                            scope.launch {
                                rowState.scrollToItem(0)
                                firstItem.requestFocus()
                            }
                        }
                    }
                }.focusGroup(),
        verticalArrangement = Arrangement.spacedBy(HeaderGap),
    ) {
        header()
        ProvideBringIntoViewSpec(RowBringIntoViewSpec) {
            LazyRow(
                state = rowState,
                modifier = Modifier.stopHorizontalExit(),
                contentPadding = PaddingValues(horizontal = Dimens.SafeHorizontal),
                horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                itemsIndexed(
                    items = items,
                    key = { _, item -> key(item) },
                    contentType = { _, item -> contentType(item) },
                ) { index, item ->
                    val requester = if (index == 0) firstItem else remember { FocusRequester() }
                    if (key(item) == pendingKey) {
                        val onFocused by rememberUpdatedState(onPendingFocus)
                        LaunchedEffect(Unit) {
                            requester.requestFocus()
                            onFocused()
                        }
                    }
                    if (key(item) == refocusKey) {
                        LaunchedEffect(Unit) {
                            requester.requestFocus()
                            refocusKey = null
                        }
                    }
                    val itemKey = key(item)
                    itemContent(
                        item,
                        Modifier
                            .onFocusChanged {
                                if (it.hasFocus) {
                                    focused.key = itemKey
                                } else if (focused.key == itemKey) {
                                    focused.key = null
                                }
                            }.focusRequester(requester),
                    )
                }
            }
        }
    }
}

/** Plain fields, not state: only read when the items change. */
private class FocusedKey<T> {
    var key: Any? = null
    var items: List<T>? = null
}

/**
 * When new items arrive while a card has focus, scrolls the row in the same frame so that card
 * stays in place; otherwise it would slide out of view and focus would be lost. Returns the key
 * of the card that should take over focus when the focused card disappeared (the one sliding into
 * its place, or the previous one if it was the last), or `null`.
 */
private fun <T> FocusedKey<T>.followNewItems(
    items: List<T>,
    key: (T) -> Any,
    rowState: LazyListState,
): Any? {
    if (this.items === items) return null
    val previous = this.items
    this.items = items
    val focusedKey = this.key
    if (previous == null || focusedKey == null) return null
    val newIndex = items.indexOfFirst { key(it) == focusedKey }
    val old = rowState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == focusedKey }
    if (newIndex >= 0) {
        if (old != null && old.index != newIndex) {
            rowState.requestScrollToItem(newIndex, -old.offset)
        }
        return null
    }
    if (items.isEmpty()) return null
    val oldIndex = previous.indexOfFirst { key(it) == focusedKey }
    if (oldIndex < 0) return null
    val neighbour = oldIndex.coerceAtMost(items.lastIndex)
    if (old != null) rowState.requestScrollToItem(neighbour, -old.offset)
    return key(items[neighbour])
}
