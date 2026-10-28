package nl.jellycast.tv.feature.movies

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.MovieView
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.ServerGenreNames
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun MoviesSection(
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onOpenMovie: (movieId: String) -> Unit,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
    viewModel: MoviesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    val playRequest = (uiState as? MoviesUiState.Success)?.playRequest
    val currentOnPlay by rememberUpdatedState(onPlay)
    if (playRequest != null) {
        LaunchedEffect(playRequest) {
            currentOnPlay(playRequest)
            viewModel.onPlayHandled()
        }
    }
    val browseActions =
        remember(viewModel) {
            MovieBrowseActions(
                onShelfChange = viewModel::onShelfChange,
                onPlayClick = viewModel::onPlayClick,
                onPlayFromStart = viewModel::onPlayFromStart,
                onSetWatched = viewModel::onSetWatched,
                onMovieSettled = viewModel::onMovieSettled,
            )
        }
    MoviesContent(
        uiState = uiState,
        takeFocus = takeFocus,
        onTakeFocusComplete = onTakeFocusComplete,
        onBackToBar = onBackToBar,
        onShelfChange = viewModel::onShelfChange,
        onPlayClick = viewModel::onPlayClick,
        onDetailsClick = { onOpenMovie(it.id) },
        onRetry = viewModel::onRetry,
        browseActions = browseActions,
        modifier = modifier,
    )
}

@Composable
fun MoviesContent(
    uiState: MoviesUiState,
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onShelfChange: (Int) -> Unit,
    onPlayClick: (Movie) -> Unit,
    onDetailsClick: (Movie) -> Unit,
    onRetry: () -> Unit,
    browseActions: MovieBrowseActions,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
) {
    // The detail view runs edge to edge, under the section bar; the bookcase starts below it.
    when {
        uiState is MoviesUiState.Success && uiState.view == MovieView.Detail -> {
            MovieBrowse(
                uiState = uiState,
                takeFocus = takeFocus,
                onTakeFocusComplete = onTakeFocusComplete,
                onBackToBar = onBackToBar,
                actions = browseActions,
                modifier = modifier,
            )
            return
        }

        uiState is MoviesUiState.Loading && uiState.view == MovieView.Detail -> {
            if (rememberDelayedVisible()) MovieBrowseSkeleton(modifier)
            return
        }
    }
    Column(modifier.fillMaxSize()) {
        Spacer(Modifier.height(Dimens.SectionBarHeight + BookcaseDimens.TopGap))
        when (uiState) {
            is MoviesUiState.Loading -> if (rememberDelayedVisible()) BookcaseSkeleton()

            MoviesUiState.Error -> {
                val onFocusComplete by rememberUpdatedState(onTakeFocusComplete)
                if (takeFocus) LaunchedEffect(Unit) { onFocusComplete() }
                ErrorState(
                    message = stringResource(R.string.library_error),
                    onRetry = onRetry,
                    takeFocus = takeFocus,
                    modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 44.dp),
                )
            }

            is MoviesUiState.Success ->
                Bookcase(
                    uiState = uiState,
                    takeFocus = takeFocus,
                    onTakeFocusComplete = onTakeFocusComplete,
                    onBackToBar = onBackToBar,
                    onShelfChange = onShelfChange,
                    onPlayClick = onPlayClick,
                    onDetailsClick = onDetailsClick,
                )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun MoviesContentPreview() {
    JellyCastTheme {
        MoviesContent(
            uiState =
                MoviesUiState.Success(
                    shelves = buildMovieShelves(
                        List(12) { PreviewMovie.copy(id = "m$it") },
                        ServerGenreNames,
                    ),
                    view = MovieView.Bookcase,
                ),
            takeFocus = false,
            onTakeFocusComplete = {},
            onShelfChange = {},
            onPlayClick = {},
            onDetailsClick = {},
            onRetry = {},
            browseActions = MovieBrowseActions({}, {}, {}, { _, _ -> }, {}),
        )
    }
}
