package nl.jellycast.mobile.feature.movie

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ContentErrorView
import nl.jellycast.mobile.core.designsystem.component.GlassCircleButton
import nl.jellycast.mobile.core.designsystem.component.RailCircleButton
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.ui.info.DetailHero
import nl.jellycast.mobile.ui.info.HeroPlayButton
import nl.jellycast.mobile.ui.info.InfoSubject
import nl.jellycast.mobile.ui.info.ItemInfoPanel
import nl.jellycast.mobile.ui.remote.PlayOnRequest
import nl.jellycast.mobile.ui.remote.PlayOnSheet
import nl.jellycast.mobile.ui.remote.hasRemoteTargets
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The movie detail: the backdrop with the logo and a play button, then the info panel. */
@Composable
fun MovieScreen(
    movieId: String,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel = koinViewModel { parametersOf(movieId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    var playOn by remember { mutableStateOf<PlayOnRequest?>(null) }
    MovieContent(
        state = state,
        onBack = { navigation.goBack() },
        onRetry = viewModel::onRetry,
        modifier = modifier,
    ) { movie ->
        MovieDetail(
            movie = movie,
            onPlay = { navigation.play(PlayableItem.MovieItem(movie)) },
            onPlayOn = { playOn = PlayOnRequest(PlayableItem.MovieItem(movie)) },
            onToggleWatched = viewModel::onToggleWatched,
            onAddToList = { navigation.addToList(movie.title, listOf(movie.id)) },
        )
    }
    PlayOnSheet(playOn, onDismiss = { playOn = null })
}

@Composable
private fun MovieContent(
    state: MovieUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    detail: @Composable (Movie) -> Unit,
) {
    Box(modifier.fillMaxSize().background(JellyCastColors.Background)) {
        when (state) {
            MovieUiState.Loading ->
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center).size(28.dp),
                    color = JellyCastColors.TextMuted,
                )
            MovieUiState.Missing ->
                ContentErrorView(
                    message = stringResource(R.string.info_movie_missing),
                    retryTitle = stringResource(R.string.retry),
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            is MovieUiState.Ready -> detail(state.movie)
        }
        GlassCircleButton(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = onBack,
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 8.dp),
        )
    }
}

@Composable
private fun MovieDetail(
    movie: Movie,
    onPlay: () -> Unit,
    onPlayOn: () -> Unit,
    onToggleWatched: () -> Unit,
    onAddToList: () -> Unit,
) {
    val inProgress = !movie.isPlayed && movie.resumePositionMs > 0
    ItemInfoPanel(
        subject = InfoSubject.MovieInfo(movie.id),
        header = {
            DetailHero(
                backdropUrl = movie.artwork.backdrop ?: movie.artwork.poster,
                logoUrl = movie.artwork.logo,
                title = movie.title,
            ) {
                HeroPlayButton(
                    contentDescription =
                        stringResource(if (inProgress) R.string.info_continue else R.string.info_play),
                    onPlay = onPlay,
                    progress = if (inProgress) movie.progress else null,
                )
            }
        },
    ) {
        if (hasRemoteTargets()) {
            RailCircleButton(R.drawable.ic_tv, stringResource(R.string.info_play_on), onPlayOn, size = 34.dp)
        }
        RailCircleButton(
            icon = if (movie.isPlayed) R.drawable.ic_info_check_circle else R.drawable.ic_check,
            contentDescription =
                stringResource(
                    if (movie.isPlayed) R.string.info_mark_not_watched else R.string.info_mark_watched,
                ),
            onClick = onToggleWatched,
            size = 34.dp,
        )
        RailCircleButton(
            R.drawable.ic_info_playlist_add,
            stringResource(R.string.info_add_to_list),
            onAddToList,
            size = 34.dp,
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun MovieContentMissingPreview() {
    JellyCastTheme {
        MovieContent(MovieUiState.Missing, onBack = {}, onRetry = {}) {}
    }
}
