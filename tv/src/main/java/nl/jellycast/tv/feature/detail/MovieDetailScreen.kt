package nl.jellycast.tv.feature.detail

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.core.model.AgeRating
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.AudioTrack
import nl.jellycast.core.model.DynamicRange
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.PlaybackTech
import nl.jellycast.core.model.Resolution
import nl.jellycast.core.model.Spine
import nl.jellycast.core.model.remainingMs
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.FullBackdrop
import nl.jellycast.tv.core.designsystem.component.MovieInfo
import nl.jellycast.tv.core.designsystem.component.MovieInfoHeight
import nl.jellycast.tv.core.designsystem.component.MovieInfoSkeleton
import nl.jellycast.tv.core.designsystem.component.MovieTechLine
import nl.jellycast.tv.core.designsystem.component.PillButton
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.stopHorizontalExit
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun MovieDetailScreen(
    movieId: String,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovieDetailViewModel = koinViewModel { parametersOf(movieId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    val currentOnPlay by rememberUpdatedState(onPlay)
    val playRequest = (uiState as? MovieDetailUiState.Success)?.playRequest
    if (playRequest != null) {
        LaunchedEffect(playRequest) {
            currentOnPlay(playRequest)
            viewModel.onPlayHandled()
        }
    }
    MovieDetailContent(
        uiState = uiState,
        onPlay = viewModel::onPlay,
        onPlayFromStart = viewModel::onPlayFromStart,
        onWatchToggle = viewModel::onToggleWatched,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun MovieDetailContent(
    uiState: MovieDetailUiState,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onWatchToggle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val success = uiState as? MovieDetailUiState.Success
    val movie = success?.movie
    // Saveable, so Back from the player lands on the same button.
    var lastFocusedButton by rememberSaveable { mutableIntStateOf(PLAY_BUTTON) }
    val requesters = remember { List(BUTTON_COUNT) { FocusRequester() } }
    val remaining = movie?.let { remainingMs(it.resumePositionMs, it.runtimeMs) }
    val buttonExists = listOf(true, remaining != null, movie != null)
    LaunchedEffect(Unit) {
        requesters[lastFocusedButton.takeIf { buttonExists[it] } ?: PLAY_BUTTON].requestFocus()
    }
    BackHandler(enabled = lastFocusedButton != PLAY_BUTTON) {
        requesters[PLAY_BUTTON].requestFocus()
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(modifier.fillMaxSize()) {
            FullBackdrop(url = movie?.artwork?.backdrop)
            Column(
                modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 120.dp),
            ) {
                when {
                    movie != null -> MovieInfo(movie, overviewMaxLines = OVERVIEW_MAX_LINES)

                    uiState is MovieDetailUiState.Error ->
                        Box(Modifier.height(MovieInfoHeight)) {
                            ErrorState(
                                message = stringResource(R.string.library_error),
                                onRetry = onRetry,
                            )
                        }

                    rememberDelayedVisible() -> MovieInfoSkeleton()

                    else -> Box(Modifier.height(MovieInfoHeight))
                }
                DetailButtons(
                    movie = movie,
                    remainingMs = remaining,
                    requesters = requesters,
                    onButtonFocus = { lastFocusedButton = it },
                    onPlay = onPlay,
                    onPlayFromStart = onPlayFromStart,
                    onWatchToggle = onWatchToggle,
                )
                if (movie != null) {
                    MovieTechLine(
                        movie = movie,
                        tech = success.tech,
                        modifier = Modifier.padding(top = 22.dp),
                    )
                }
            }
        }
    }
}

// The play button is there even without a movie (skeleton), so it can take focus at once.
@Composable
private fun DetailButtons(
    movie: Movie?,
    remainingMs: Long?,
    requesters: List<FocusRequester>,
    onButtonFocus: (Int) -> Unit,
    onPlay: () -> Unit,
    onPlayFromStart: () -> Unit,
    onWatchToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(top = 18.dp).stopHorizontalExit(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PillButton(
            text =
                stringResource(
                    if (remainingMs != null) R.string.detail_resume else R.string.detail_play,
                ),
            trailing = remainingMs?.let { remainingText(it) },
            onClick = onPlay,
            icon = painterResource(R.drawable.ic_play),
            isPrimary = true,
            enabled = movie != null,
            modifier =
                Modifier.trackFocus(requesters[PLAY_BUTTON]) { onButtonFocus(PLAY_BUTTON) },
        )
        if (remainingMs != null) {
            PillButton(
                text = stringResource(R.string.detail_from_start),
                onClick = onPlayFromStart,
                icon = painterResource(R.drawable.ic_restart),
                modifier =
                    Modifier.trackFocus(requesters[FROM_START_BUTTON]) {
                        onButtonFocus(FROM_START_BUTTON)
                    },
            )
        }
        if (movie != null) {
            PillButton(
                text =
                    stringResource(
                        if (movie.isPlayed) {
                            R.string.detail_watched
                        } else {
                            R.string.detail_mark_watched
                        },
                    ),
                onClick = onWatchToggle,
                icon = if (movie.isPlayed) painterResource(R.drawable.ic_check) else null,
                modifier =
                    Modifier.trackFocus(requesters[WATCHED_BUTTON]) {
                        onButtonFocus(WATCHED_BUTTON)
                    },
            )
        }
    }
}

private const val PLAY_BUTTON = 0
private const val FROM_START_BUTTON = 1
private const val WATCHED_BUTTON = 2
private const val BUTTON_COUNT = 3

// The full overview, capped so the buttons stay on screen.
private const val OVERVIEW_MAX_LINES = 8

private fun Modifier.trackFocus(requester: FocusRequester, onFocus: () -> Unit): Modifier =
    focusRequester(requester).onFocusChanged { if (it.isFocused) onFocus() }

@Preview(device = Devices.TV_1080p)
@Composable
private fun MovieDetailContentPreview() {
    JellyCastTheme {
        MovieDetailContent(
            uiState =
                MovieDetailUiState.Success(
                    movie =
                        Movie(
                            id = "m1",
                            title = "Interstellar",
                            year = 2014,
                            runtimeMs = 10_144_000,
                            overview = "The adventures of a group of explorers…",
                            genres = listOf("Adventure", "Science Fiction"),
                            ageRating = AgeRating("12"),
                            resumePositionMs = 5_800_000,
                            lastPlayedAt = null,
                            addedAt = null,
                            artwork = Artwork(),
                            spine = Spine(null, null, 0xFF20282B, isLight = false),
                            tech = MediaTech(
                                Resolution.UHD,
                                DynamicRange.DolbyVision,
                                hasAtmos = true,
                            ),
                        ),
                    tech =
                        PlaybackTech(
                            playMethod = PlayMethod.DirectPlay,
                            audio = AudioTrack(2, "eng", "eac3", 8, null, isDefault = false),
                            replacedAudio = AudioTrack(
                                1,
                                "eng",
                                "truehd",
                                8,
                                null,
                                isDefault = true,
                            ),
                        ),
                ),
            onPlay = {},
            onPlayFromStart = {},
            onWatchToggle = {},
            onRetry = {},
        )
    }
}
