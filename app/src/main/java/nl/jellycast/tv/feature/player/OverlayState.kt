package nl.jellycast.tv.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.focus.FocusRequester
import kotlinx.coroutines.delay

/** What is open on top of the video. */
internal enum class PlayerPanel { None, Audio, Subtitles, Info }

private const val OVERLAY_HIDE_MS = 4_000L

/** Overlay visibility, the open panel and the focus targets of the player screen. */
internal class OverlayState {
    var visible by mutableStateOf(true)
    var panel by mutableStateOf(PlayerPanel.None)

    /** Bumped on every key press, restarting the hide timer. */
    var interactions by mutableIntStateOf(0)
    var returnFocusTo by mutableStateOf<OverlayButton?>(null)
    var jumpHadFocus by mutableStateOf(false)
    val requesters = OverlayRequesters()

    /** Holds focus while the overlay is hidden, so every key reaches the player. */
    val idleRequester = FocusRequester()
    val jumpRequester = FocusRequester()

    fun show() {
        visible = true
        interactions++
    }

    fun hide() {
        panel = PlayerPanel.None
        visible = false
        idleRequester.requestFocus()
    }

    fun closePanel() {
        returnFocusTo =
            when (panel) {
                PlayerPanel.Audio -> OverlayButton.Audio
                PlayerPanel.Info -> OverlayButton.Info
                else -> OverlayButton.Subtitles
            }
        panel = PlayerPanel.None
        interactions++
    }

    fun toggleInfo() {
        if (panel == PlayerPanel.Info) {
            // Keep focus on the info button instead of moving it back to the timebar.
            returnFocusTo = OverlayButton.Info
            panel = PlayerPanel.None
        } else {
            panel = PlayerPanel.Info
        }
    }
}

@Composable
internal fun OverlayEffects(overlay: OverlayState, isPlaying: Boolean, jumpVisible: Boolean) {
    val visible = overlay.visible
    val panel = overlay.panel
    LaunchedEffect(visible, overlay.interactions, isPlaying, panel) {
        if (visible && isPlaying && panel == PlayerPanel.None) {
            delay(OVERLAY_HIDE_MS)
            overlay.hide()
        }
    }

    LaunchedEffect(visible, panel) {
        if (visible && panel == PlayerPanel.None) {
            withFrameNanos { }
            val target =
                overlay.returnFocusTo?.let { overlay.requesters.of(it) }
                    ?: overlay.requesters.timebar
            overlay.returnFocusTo = null
            target.requestFocus()
        }
    }

    LaunchedEffect(jumpVisible, visible) {
        if (jumpVisible && !visible) {
            withFrameNanos { }
            overlay.jumpRequester.requestFocus()
        }
    }

    LaunchedEffect(jumpVisible) {
        if (!jumpVisible && overlay.jumpHadFocus) {
            overlay.jumpHadFocus = false
            withFrameNanos { }
            if (overlay.visible) {
                overlay.requesters.timebar.requestFocus()
            } else {
                overlay.idleRequester.requestFocus()
            }
        }
    }
}
