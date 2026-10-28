package nl.jellycast.mobile.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import nl.jellycast.core.model.AuthState
import nl.jellycast.mobile.feature.detail.MovieDetailScreen
import nl.jellycast.mobile.feature.home.HomeScreen
import nl.jellycast.mobile.feature.login.LoginScreen
import nl.jellycast.mobile.feature.player.PlayerScreen
import nl.jellycast.mobile.feature.series.SeriesDetailScreen
import nl.jellycast.mobile.feature.server.ServerScreen
import nl.jellycast.mobile.feature.settings.SettingsScreen

@Composable
fun JellyCastNavHost(
    startDestination: NavKey,
    authState: AuthState?,
    modifier: Modifier = Modifier,
) {
    val backStack = rememberNavBackStack(startDestination)
    LaunchedEffect(authState) { backStack.followAuthState(authState) }
    val play: (String, Long) -> Unit = { id, startMs -> backStack.add(Player(id, startMs)) }

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        transitionSpec = { screenFade() },
        popTransitionSpec = { screenFade() },
        predictivePopTransitionSpec = { screenFade() },
        entryDecorators =
            listOf(
                rememberSaveableStateHolderNavEntryDecorator(),
                rememberViewModelStoreNavEntryDecorator(),
            ),
        entryProvider =
            entryProvider {
                entry<ServerSetup> {
                    ServerScreen(onNavigateToLogin = { backStack.resetTo(Login) })
                }
                entry<Login> {
                    LoginScreen(onChangeServer = { backStack.add(ServerSetup) })
                }
                entry<Home> {
                    HomeScreen(
                        onOpenMovie = { backStack.add(MovieDetail(it)) },
                        onOpenSeries = { backStack.add(SeriesDetail(it)) },
                        onPlay = play,
                        onOpenSettings = { backStack.add(Settings) },
                    )
                }
                entry<MovieDetail> { key ->
                    MovieDetailScreen(
                        movieId = key.movieId,
                        onPlay = play,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<SeriesDetail> { key ->
                    SeriesDetailScreen(
                        seriesId = key.seriesId,
                        onPlay = play,
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
                entry<Player> { key ->
                    PlayerScreen(
                        itemId = key.itemId,
                        startPositionMs = key.startPositionMs,
                        onExit = {
                            if (backStack.lastOrNull() is Player) backStack.removeLastOrNull()
                        },
                    )
                }
                entry<Settings> {
                    SettingsScreen(
                        onChangeServer = { backStack.add(ServerSetup) },
                        onBack = { backStack.removeLastOrNull() },
                    )
                }
            },
    )
}

private fun NavBackStack<NavKey>.followAuthState(authState: AuthState?) {
    val top = lastOrNull()
    when {
        authState is AuthState.LoggedIn && top == Login -> resetTo(Home)
        authState is AuthState.LoggedOut && top.needsLogin() -> resetTo(Login)
        authState is AuthState.NoServer && top.needsLogin() -> resetTo(ServerSetup)
    }
}

private fun NavKey?.needsLogin(): Boolean = this != null && this != Login && this != ServerSetup

private fun NavBackStack<NavKey>.resetTo(key: NavKey) {
    clear()
    add(key)
}

private const val SCREEN_FADE_MS = 200

private fun screenFade(): ContentTransform =
    fadeIn(tween(SCREEN_FADE_MS)) togetherWith fadeOut(tween(SCREEN_FADE_MS))
