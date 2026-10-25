package nl.jellycast.tv.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.CornerBackdrop
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.FocusCard
import nl.jellycast.tv.core.designsystem.component.PillButton
import nl.jellycast.tv.core.designsystem.component.ProvideBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.TvRow
import nl.jellycast.tv.core.designsystem.component.progressBar
import nl.jellycast.tv.core.designsystem.component.rememberCachedLazyListState
import nl.jellycast.tv.core.designsystem.component.rememberColumnBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.Artwork
import nl.jellycast.tv.core.model.Episode
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.Series
import nl.jellycast.tv.core.model.progress
import nl.jellycast.tv.core.model.remainingMs
import nl.jellycast.tv.core.ui.episodeCode
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.runtimeText
import nl.jellycast.tv.core.ui.seasonCountText
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onPlay: (PlayRequest) -> Unit,
    modifier: Modifier = Modifier,
    focusEpisodeId: String? = null,
    viewModel: SeriesDetailViewModel = koinViewModel { parametersOf(seriesId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    val currentOnPlay by rememberUpdatedState(onPlay)
    val playRequest = uiState.playRequest
    if (playRequest != null) {
        LaunchedEffect(playRequest) {
            currentOnPlay(playRequest)
            viewModel.onPlayHandled()
        }
    }
    SeriesDetailContent(
        uiState = uiState,
        onContinueClick = viewModel::onContinueClick,
        onFocusContinue = viewModel::onFocusContinue,
        onEpisodeClick = viewModel::onEpisodeClick,
        onFocusEpisode = viewModel::onFocusEpisode,
        onMarkNextClick = viewModel::onMarkNextClick,
        onSeriesWatchedToggle = viewModel::onSeriesWatchedToggle,
        onSeasonWatchedToggle = viewModel::onSeasonWatchedToggle,
        onRetry = viewModel::onRetry,
        modifier = modifier,
        focusEpisodeId = focusEpisodeId,
    )
}

internal data class EpisodeTarget(val rowKey: String, val episodeId: String)

@Composable
fun SeriesDetailContent(
    uiState: SeriesDetailUiState,
    onContinueClick: () -> Unit,
    onFocusContinue: () -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFocusEpisode: (Episode) -> Unit,
    onMarkNextClick: () -> Unit,
    onSeriesWatchedToggle: () -> Unit,
    onSeasonWatchedToggle: (seasonKey: String) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    focusEpisodeId: String? = null,
) {
    val continueRequester = remember { FocusRequester() }
    val seriesWatchedRequester = remember { FocusRequester() }
    var continueFocused by remember { mutableStateOf(false) }
    var lastRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastEpisodeId by rememberSaveable { mutableStateOf(focusEpisodeId) }
    var pending by remember { mutableStateOf<EpisodeTarget?>(null) }
    var restoreDone by remember { mutableStateOf(false) }
    val columnState = rememberCachedLazyListState(ahead = 240.dp)
    val scope = rememberCoroutineScope()
    val seasons = uiState.seasons

    // Continue exists in the skeleton too; restoring an episode has to wait for the seasons.
    LaunchedEffect(seasons != null) {
        if (restoreDone) return@LaunchedEffect
        val id = lastEpisodeId
        if (id == null) {
            continueRequester.requestFocus()
            restoreDone = true
        } else if (seasons != null) {
            // Opened on an episode, only its id is known.
            val rowIndex =
                seasons.indexOfFirst { season ->
                    (lastRowKey == null || season.key == lastRowKey) &&
                        season.episodes.any { it.id == id }
                }
            if (rowIndex >= 0) {
                val visible = columnState.layoutInfo.visibleItemsInfo.any { it.index == rowIndex }
                if (!visible) columnState.scrollToItem(rowIndex)
                pending = EpisodeTarget(seasons[rowIndex].key, id)
            } else {
                continueRequester.requestFocus()
            }
            restoreDone = true
        }
    }

    val onTopButtonFocus = {
        // Android focuses Continue by itself while the seasons load; that must not cancel the
        // episode still to be restored.
        if (restoreDone) {
            lastRowKey = null
            lastEpisodeId = null
        }
        onFocusContinue()
    }

    BackHandler(enabled = !continueFocused && restoreDone) {
        lastRowKey = null
        lastEpisodeId = null
        scope.launch { columnState.scrollToItem(0) }
        continueRequester.requestFocus()
    }

    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        Box(modifier.fillMaxSize()) {
            CornerBackdrop(
                url = uiState.series?.artwork?.backdrop,
                modifier = Modifier.align(Alignment.TopEnd),
            )
            Column(Modifier.fillMaxSize()) {
                TopBlock(
                    series = uiState.series,
                    episode = uiState.focusedEpisode,
                    modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 44.dp),
                )
                TopButtons(
                    uiState = uiState,
                    continueFocused = continueFocused,
                    continueRequester = continueRequester,
                    seriesWatchedRequester = seriesWatchedRequester,
                    onContinueFocusChange = { continueFocused = it },
                    onTopButtonFocus = onTopButtonFocus,
                    onContinueClick = onContinueClick,
                    onMarkNextClick = onMarkNextClick,
                    onSeriesWatchedToggle = onSeriesWatchedToggle,
                )
                when {
                    seasons != null ->
                        Seasons(
                            seasons = seasons,
                            columnState = columnState,
                            pending = pending,
                            onPendingFocus = { pending = null },
                            onFocusEpisode = { season, episode ->
                                lastRowKey = season.key
                                lastEpisodeId = episode.id
                                onFocusEpisode(episode)
                            },
                            onEpisodeClick = onEpisodeClick,
                            onFocusSeasonCard = onFocusContinue,
                            onSeasonWatchedToggle = onSeasonWatchedToggle,
                            modifier = Modifier.weight(1f),
                        )

                    uiState.isError ->
                        ErrorState(
                            message = stringResource(R.string.library_error),
                            onRetry = onRetry,
                            modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 36.dp),
                        )

                    rememberDelayedVisible() -> SeasonsSkeleton()
                }
            }
        }
    }
}

/** Mark buttons sit after Continue, so season n stays n presses down from Continue. */
@Composable
private fun TopButtons(
    uiState: SeriesDetailUiState,
    continueFocused: Boolean,
    continueRequester: FocusRequester,
    seriesWatchedRequester: FocusRequester,
    onContinueFocusChange: (Boolean) -> Unit,
    onTopButtonFocus: () -> Unit,
    onContinueClick: () -> Unit,
    onMarkNextClick: () -> Unit,
    onSeriesWatchedToggle: () -> Unit,
) {
    val markableNext = uiState.markableNext
    Row(
        modifier = Modifier.padding(start = Dimens.SafeHorizontal, top = 18.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ContinueButton(
            next = uiState.next,
            isUnstarted = uiState.isUnstarted,
            isFocused = continueFocused,
            onClick = onContinueClick,
            modifier =
                Modifier
                    .focusRequester(continueRequester)
                    .onFocusChanged {
                        onContinueFocusChange(it.isFocused)
                        if (it.isFocused) onTopButtonFocus()
                    },
        )
        if (markableNext != null) {
            PillButton(
                text =
                    stringResource(
                        R.string.series_mark_episode,
                        episodeCode(markableNext.seasonNumber, markableNext.episodeNumber),
                    ),
                onClick = {
                    // Move focus before the button disappears, or Android picks Continue itself.
                    if (uiState.markingNextHidesButton) seriesWatchedRequester.requestFocus()
                    onMarkNextClick()
                },
                modifier = Modifier.onFocusChanged { if (it.isFocused) onTopButtonFocus() },
            )
        }
        if (uiState.seasons != null) {
            PillButton(
                text =
                    stringResource(
                        if (uiState.isWatched) {
                            R.string.series_watched
                        } else {
                            R.string.series_mark_series
                        },
                    ),
                icon = if (uiState.isWatched) painterResource(R.drawable.ic_check) else null,
                onClick = onSeriesWatchedToggle,
                modifier =
                    Modifier
                        .focusRequester(seriesWatchedRequester)
                        .onFocusChanged { if (it.isFocused) onTopButtonFocus() },
            )
        }
    }
}

private val PreviewEpisodes =
    List(16) { index ->
        Episode(
            id = "e$index",
            seriesId = "s1",
            seriesTitle = "The Bear",
            seasonNumber = index / 8 + 1,
            episodeNumber = index % 8 + 1,
            title = "Episode ${index + 1}",
            overview = "Sydney gets a new apartment.",
            runtimeMs = 1_800_000,
            resumePositionMs = if (index == 9) 600_000 else 0,
            artwork = Artwork(),
            isPlayed = index < 9,
        )
    }

@Preview(device = Devices.TV_1080p)
@Composable
private fun SeriesDetailContentPreview() {
    JellyCastTheme {
        SeriesDetailContent(
            uiState =
                SeriesDetailUiState(
                    series =
                        Series(
                            id = "s1",
                            title = "The Bear",
                            year = 2022,
                            overview = "Carmy, a young fine-dining chef, comes home to Chicago…",
                            genres = listOf("Drama", "Comedy"),
                            ageRating = null,
                            seasonCount = 2,
                            lastEpisodeAddedAt = null,
                            artwork = Artwork(),
                        ),
                    seasons = groupSeasons(PreviewEpisodes),
                    next = PreviewEpisodes[9],
                ),
            onContinueClick = {},
            onFocusContinue = {},
            onEpisodeClick = {},
            onFocusEpisode = {},
            onMarkNextClick = {},
            onSeriesWatchedToggle = {},
            onSeasonWatchedToggle = {},
            onRetry = {},
        )
    }
}
