package nl.jellycast.tv.feature.movies

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type

internal val KeyEvent.isHorizontalArrow: Boolean
    get() = key == Key.DirectionLeft || key == Key.DirectionRight

internal val KeyEvent.isRepeatedKeyDown: Boolean
    get() = type == KeyEventType.KeyDown && nativeKeyEvent.repeatCount > 0

// No wrap-around: ←/→ on the first and last movie do nothing. ↑ on the first shelf is not
// consumed, so it moves focus to the section bar.
internal fun handleBrowseArrow(
    key: Key,
    movieIndex: Int,
    lastMovieIndex: Int,
    requestedShelf: Int,
    lastShelfIndex: Int,
    showMovie: (Int) -> Unit,
    changeShelf: (Int) -> Unit,
): Boolean = when (key) {
    Key.DirectionLeft -> {
        if (movieIndex > 0) showMovie(movieIndex - 1)
        true
    }

    Key.DirectionRight -> {
        if (movieIndex < lastMovieIndex) showMovie(movieIndex + 1)
        true
    }

    Key.DirectionDown -> {
        if (requestedShelf < lastShelfIndex) changeShelf(requestedShelf + 1)
        true
    }

    Key.DirectionUp ->
        if (requestedShelf > 0) {
            changeShelf(requestedShelf - 1)
            true
        } else {
            false
        }

    else -> false
}

// Short OK fires on release; holding fires the long press on the first repeat (after ~0.5 s).
// Acting on release keeps a long press from starting playback first.
internal class OkPress {
    private var isDown = false
    private var longPressFired = false

    fun handle(
        event: KeyEvent,
        enabled: Boolean,
        onClick: () -> Unit,
        onLongPress: () -> Unit,
    ): Boolean {
        when (event.type) {
            KeyEventType.KeyDown -> {
                if (event.nativeKeyEvent.repeatCount == 0) {
                    if (!enabled) return false
                    isDown = true
                    longPressFired = false
                } else if (isDown && !longPressFired) {
                    longPressFired = true
                    onLongPress()
                }
                return isDown
            }

            KeyEventType.KeyUp -> {
                if (!isDown) return false
                isDown = false
                if (!longPressFired) onClick()
                return true
            }

            else -> return false
        }
    }
}
