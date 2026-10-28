package nl.jellycast.mobile.ui.remote

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.StreamChoice
import nl.jellycast.mobile.core.remote.RemoteSessions
import nl.jellycast.mobile.feature.remote.PlayOnSheetHost
import nl.jellycast.mobile.feature.remote.PlayOnTarget
import nl.jellycast.mobile.feature.remote.RemoteNowPlayingOverlay
import nl.jellycast.mobile.feature.remote.TvBarHost
import nl.jellycast.mobile.feature.remote.TvBarViewModel
import nl.jellycast.mobile.navigation.LocalAppNavigation
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * The bar above the tab bar while a TV plays something (TV bar wins over the download bar).
 * Draws nothing when no TV plays. Returns whether it drew; tap opens the now-playing screen.
 * It also keeps the sessions current while the app is in the foreground.
 */
@Composable
fun TvBar(modifier: Modifier = Modifier): Boolean {
    val viewModel: TvBarViewModel = koinViewModel()
    return TvBarHost(viewModel, modifier)
}

/** The full-screen "playing on the TV" remote screen, shown over the app when opened. */
@Composable
fun RemoteNowPlayingHost() = RemoteNowPlayingOverlay()

/** A "Play on…" / "Continue on…" request. [handOffPositionMs] set means continue from there. */
data class PlayOnRequest(
    val item: PlayableItem,
    val handOffPositionMs: Long? = null,
    val handOffStreams: StreamChoice? = null,
)

/**
 * The bottom sheet listing the TVs. [onSend] runs after a TV took it (the player then closes).
 * Without a hand-off position it also offers "This phone", which plays the item here.
 */
@Composable
fun PlayOnSheet(request: PlayOnRequest?, onDismiss: () -> Unit, onSend: () -> Unit = {}) {
    if (request == null) return
    val navigation = LocalAppNavigation.current
    PlayOnSheetHost(
        target = PlayOnTarget(request.item, request.handOffPositionMs, request.handOffStreams),
        onDismiss = onDismiss,
        onPlayHere =
            if (request.handOffPositionMs == null) {
                { navigation.play(request.item) }
            } else {
                null
            },
        onHandOff = onSend,
    )
}

/** Whether a TV can be offered, so "Play on…" and "Continue on…" buttons show. */
@Composable
fun hasRemoteTargets(): Boolean {
    val remote: RemoteSessions = koinInject()
    val state by remote.state.collectAsStateWithLifecycle()
    return state.hasTargets
}
