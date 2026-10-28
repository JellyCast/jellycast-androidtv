package nl.jellycast.tv.feature.player

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.media3.common.Player
import nl.jellycast.core.model.MediaSegment

private val OkKeys = setOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter)

/** Preview key handler of the player screen; `true` consumes the key. */
internal fun OverlayState.onPlayerKey(
    event: KeyEvent,
    player: Player?,
    jumpVisible: Boolean,
): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    interactions++
    return when {
        event.key == Key.MediaPlayPause -> {
            player?.let { if (it.isPlaying) it.pause() else it.play() }
            show()
            true
        }

        // The focused jump pill handles OK itself.
        jumpVisible && !visible && event.key in OkKeys -> false

        // With the overlay hidden the first key only shows it; Back goes to the BackHandler.
        !visible && event.key != Key.Back -> {
            show()
            true
        }

        else -> false
    }
}

/** Back in steps: close the menu, hide the overlay, dismiss the jump pill, then stop. */
@Composable
internal fun PlayerBackHandler(
    overlay: OverlayState,
    jump: JumpState,
    showNextPill: Boolean,
    skip: MediaSegment?,
    onExit: () -> Unit,
) {
    BackHandler {
        when {
            overlay.panel != PlayerPanel.None -> overlay.closePanel()
            overlay.visible -> overlay.hide()
            showNextPill -> jump.nextDismissed = true
            skip != null -> jump.dismissSkip(skip)
            else -> onExit()
        }
    }
}
