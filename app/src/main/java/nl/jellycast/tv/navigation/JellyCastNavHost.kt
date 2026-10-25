package nl.jellycast.tv.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.core.model.HomeSection
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.StreamChoice
import nl.jellycast.tv.feature.detail.MovieDetailScreen
import nl.jellycast.tv.feature.home.HomeScreen
import nl.jellycast.tv.feature.home.HomeSuggestionSection
import nl.jellycast.tv.feature.library.LibrarySection
import nl.jellycast.tv.feature.login.LoginScreen
import nl.jellycast.tv.feature.movies.MoviesSection
import nl.jellycast.tv.feature.movies.MoviesShelfIndicator
import nl.jellycast.tv.feature.player.PlayerScreen
import nl.jellycast.tv.feature.series.SeriesDetailScreen
import nl.jellycast.tv.feature.series.SeriesSection
import nl.jellycast.tv.feature.server.ServerScreen
import nl.jellycast.tv.feature.settings.PlaybackSettingsScreen
import nl.jellycast.tv.feature.settings.SettingsScreen

@Composable
fun JellyCastNavHost(
    startDestination: NavKey,
    authState: AuthState?,
    modifier: Modifier = Modifier,
    debugPlayItemId: String? = null,
    debugStartMs: Long = 0,
    linkRequest: PlayRequest? = null,
    onLinkHandle: () -> Unit = {},
) {
    val backStack = rememberNavBackStack(startDestination)
    val currentOnLinkHandle by rememberUpdatedState(onLinkHandle)

    LaunchedEffect(linkRequest, authState is AuthState.LoggedIn) {
        if (linkRequest == null || authState !is AuthState.LoggedIn) return@LaunchedEffect
        if (backStack.lastOrNull() is Player) backStack.removeLastOrNull()
        backStack.add(
            Player(
                linkRequest.itemId,
                linkRequest.startPositionMs,
                linkRequest.streams.audioStreamIndex,
                linkRequest.streams.subtitleStreamIndex,
            ),
        )
        currentOnLinkHandle()
    }

    LaunchedEffect(debugPlayItemId) {
        if (debugPlayItemId != null && authState is AuthState.LoggedIn) {
            backStack.add(Player(debugPlayItemId, startPositionMs = debugStartMs))
        }
    }

    LaunchedEffect(authState) { backStack.followAuthState(authState) }
    RemotePlayEffect(authState, backStack)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        modifier = modifier,
        // Navigation 3 defaults to 700 ms.
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
                entry<Home> { HomeDestination(backStack) }
                entry<MovieDetail> { key ->
                    MovieDetailScreen(
                        movieId = key.movieId,
                        onPlay = { backStack.add(Player(it.itemId, it.startPositionMs)) },
                    )
                }
                entry<SeriesDetail> { key ->
                    SeriesDetailScreen(
                        seriesId = key.seriesId,
                        focusEpisodeId = key.episodeId,
                        onPlay = { backStack.add(Player(it.itemId, it.startPositionMs)) },
                    )
                }
                // The video SurfaceView ignores alpha, so a fade would leave the video plane over
                // the previous screen.
                entry<Player>(metadata = NoTransition) { key ->
                    PlayerScreen(
                        itemId = key.itemId,
                        startPositionMs = key.startPositionMs,
                        requestedStreams =
                            StreamChoice(key.audioStreamIndex, key.subtitleStreamIndex),
                        // A fast double Back would otherwise also pop Home and close the app.
                        onExit = {
                            if (backStack.lastOrNull() is Player) backStack.removeLastOrNull()
                        },
                    )
                }
                entry<PlaybackSettings> { PlaybackSettingsScreen() }
                entry<Settings> {
                    SettingsScreen(
                        // Stay logged in until the new server is saved (which clears the login).
                        onChangeServer = { backStack.add(ServerSetup) },
                        onOpenPlayback = { backStack.add(PlaybackSettings) },
                    )
                }
            },
    )
}

// Home must not know the sections: features never import each other.
@Composable
private fun HomeDestination(backStack: NavBackStack<NavKey>) {
    val play: (PlayRequest) -> Unit = { backStack.add(Player(it.itemId, it.startPositionMs)) }
    HomeScreen(
        onOpenSettings = { backStack.add(Settings) },
        sectionContent = { section, takeFocus, onTakeFocusComplete, onBackToBar ->
            when (section) {
                HomeSection.Home ->
                    HomeSuggestionSection(
                        takeFocus = takeFocus,
                        onTakeFocusComplete = onTakeFocusComplete,
                        onPlay = play,
                        onBackToBar = onBackToBar,
                    )

                HomeSection.Series ->
                    SeriesSection(
                        takeFocus = takeFocus,
                        onTakeFocusComplete = onTakeFocusComplete,
                        onBackToBar = onBackToBar,
                        onPlay = play,
                        onOpenSeries = { seriesId, episodeId ->
                            backStack.add(SeriesDetail(seriesId, episodeId))
                        },
                    )

                HomeSection.Movies ->
                    MoviesSection(
                        takeFocus = takeFocus,
                        onTakeFocusComplete = onTakeFocusComplete,
                        onBackToBar = onBackToBar,
                        onPlay = play,
                        onOpenMovie = { backStack.add(MovieDetail(it)) },
                    )

                HomeSection.Library ->
                    LibrarySection(
                        takeFocus = takeFocus,
                        onTakeFocusComplete = onTakeFocusComplete,
                        onBackToBar = onBackToBar,
                        onOpenMovie = { backStack.add(MovieDetail(it)) },
                        onOpenSeries = { backStack.add(SeriesDetail(it)) },
                    )
            }
        },
        barAccessory = { section ->
            if (section == HomeSection.Movies) MoviesShelfIndicator()
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

private const val SCREEN_FADE_MS = 200

private fun screenFade(): ContentTransform =
    fadeIn(tween(SCREEN_FADE_MS)) togetherWith fadeOut(tween(SCREEN_FADE_MS))

private val NoTransition =
    NavDisplay.transitionSpec { EnterTransition.None togetherWith ExitTransition.None } +
        NavDisplay.popTransitionSpec { EnterTransition.None togetherWith ExitTransition.None }

private fun NavBackStack<NavKey>.resetTo(key: NavKey) {
    clear()
    add(key)
}
