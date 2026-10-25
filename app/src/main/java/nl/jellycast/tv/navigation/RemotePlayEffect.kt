package nl.jellycast.tv.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import nl.jellycast.tv.core.data.repository.RemoteControlRepository
import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.core.model.RemoteCommand
import org.koin.compose.koinInject

private val PresenceInterval = 60.seconds

/**
 * While the app is in front, the iOS app can start something from any screen. In the player the
 * player's own listener handles every command, including play.
 */
@Composable
internal fun RemotePlayEffect(
    authState: AuthState?,
    backStack: NavBackStack<NavKey>,
    repository: RemoteControlRepository = koinInject(),
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(authState is AuthState.LoggedIn) {
        if (authState !is AuthState.LoggedIn) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            launch {
                while (isActive) {
                    delay(PresenceInterval)
                    repository.reportPresence()
                }
            }
            repository.getCommandStream().collect { command ->
                if (command is RemoteCommand.Play && backStack.lastOrNull() !is Player) {
                    backStack.add(
                        Player(
                            command.itemId,
                            command.startPositionMs,
                            command.streams.audioStreamIndex,
                            command.streams.subtitleStreamIndex,
                        ),
                    )
                }
            }
        }
    }
}
