package nl.jellycast.tv.feature.movies

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type

internal fun Modifier.bookcaseKeys(
    state: BookcaseState,
    lastShelfIndex: Int,
    onShelfChange: (Int) -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
    when (event.key) {
        Key.DirectionLeft -> state.queueStepWhileSwitching(-1)

        Key.DirectionRight -> state.queueStepWhileSwitching(1)

        Key.DirectionDown -> {
            state.moveDown(lastShelfIndex, onShelfChange)
            true
        }

        Key.DirectionUp -> state.moveUp(onShelfChange)

        else -> false
    }
}
