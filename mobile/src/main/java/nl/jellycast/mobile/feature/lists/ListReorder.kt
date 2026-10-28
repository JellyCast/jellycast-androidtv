package nl.jellycast.mobile.feature.lists

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex

/**
 * Drag a row by its handle to reorder: the rows swap locally while dragging, and [onMove] gets
 * the start and end position once the finger lifts.
 */
class ReorderState<T>(private val listState: LazyListState, private val keyOf: (T) -> String) {
    var items by mutableStateOf<List<T>>(emptyList())
        private set

    var draggedKey by mutableStateOf<String?>(null)
        private set

    var offsetPx by mutableFloatStateOf(0f)
        private set

    private var startIndex = -1
    private var latest: List<T> = emptyList()

    /** Takes the latest rows unless a drag is under way. */
    fun sync(rows: List<T>) {
        latest = rows
        if (draggedKey == null) items = rows
    }

    fun cancel() {
        draggedKey = null
        offsetPx = 0f
        items = latest
    }

    fun start(key: String) {
        startIndex = items.indexOfFirst { keyOf(it) == key }
        if (startIndex >= 0) {
            draggedKey = key
            offsetPx = 0f
        }
    }

    fun drag(deltaPx: Float) {
        val key = draggedKey ?: return
        offsetPx += deltaPx
        val index = items.indexOfFirst { keyOf(it) == key }
        val visible = listState.layoutInfo.visibleItemsInfo
        val current = visible.firstOrNull { it.key == key } ?: return
        val neighbourIndex = if (offsetPx > 0) index + 1 else index - 1
        val neighbour = items.getOrNull(neighbourIndex) ?: return
        val neighbourInfo = visible.firstOrNull { it.key == keyOf(neighbour) } ?: return
        val crossed =
            if (offsetPx > 0) {
                current.offset + current.size + offsetPx >
                    neighbourInfo.offset + neighbourInfo.size / 2
            } else {
                current.offset + offsetPx < neighbourInfo.offset + neighbourInfo.size / 2
            }
        if (crossed) {
            items = items.toMutableList().apply { add(neighbourIndex, removeAt(index)) }
            offsetPx += if (offsetPx > 0) -neighbourInfo.size else neighbourInfo.size
        }
    }

    /** Returns the start and end position, or `null` when the row ended where it began. */
    fun end(): Pair<Int, Int>? {
        val key = draggedKey ?: return null
        val endIndex = items.indexOfFirst { keyOf(it) == key }
        draggedKey = null
        offsetPx = 0f
        return (startIndex to endIndex).takeIf { startIndex >= 0 && startIndex != endIndex }
    }
}

@Composable
fun <T> rememberReorderState(listState: LazyListState, keyOf: (T) -> String): ReorderState<T> =
    remember(listState) { ReorderState(listState, keyOf) }

/** Lifts the dragged row above the others and moves it with the finger. */
fun <T> Modifier.reorderable(state: ReorderState<T>, key: String): Modifier = this
    .zIndex(if (state.draggedKey == key) 1f else 0f)
    .graphicsLayer {
        translationY = if (state.draggedKey == key) state.offsetPx else 0f
    }

/** The drag handle of the row with [key]. */
@Composable
fun <T> Modifier.reorderHandle(
    state: ReorderState<T>,
    key: String,
    onMove: (from: Int, to: Int) -> Unit,
): Modifier {
    val currentOnMove by rememberUpdatedState(onMove)
    return pointerInput(state, key) {
        detectDragGestures(
            onDragStart = { state.start(key) },
            onDrag = { change, amount ->
                change.consume()
                state.drag(amount.y)
            },
            onDragEnd = { state.end()?.let { (from, to) -> currentOnMove(from, to) } },
            onDragCancel = { state.cancel() },
        )
    }
}
