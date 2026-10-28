package nl.jellycast.mobile.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.StreamChoice

/**
 * The bar above the tab bar while a TV plays something (TV bar wins over the download bar).
 * Draws nothing when no TV plays. Returns whether it drew; tap opens the now-playing screen.
 *
 * Owner: the remote worker. STUB.
 */
@Composable
fun TvBar(modifier: Modifier = Modifier): Boolean = false

/** The full-screen "playing on the TV" remote screen, shown over the app when opened. STUB. */
@Composable
fun RemoteNowPlayingHost() = Unit

/** A "Play on…" / "Continue on…" request. [handOffPositionMs] set means continue from there. */
data class PlayOnRequest(
    val item: PlayableItem,
    val handOffPositionMs: Long? = null,
    val handOffStreams: StreamChoice? = null,
)

/**
 * The bottom sheet listing the TVs. [onSend] runs after a TV took it (the player then closes).
 *
 * Owner: the remote worker. STUB.
 */
@Composable
fun PlayOnSheet(request: PlayOnRequest?, onDismiss: () -> Unit, onSend: () -> Unit = {}) = Unit

/** Whether any TV can be reached, so "Continue on…" buttons show. STUB: always false. */
@Composable
fun hasRemoteTargets(): Boolean = false
