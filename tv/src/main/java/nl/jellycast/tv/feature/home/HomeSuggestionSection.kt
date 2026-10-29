package nl.jellycast.tv.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import nl.jellycast.core.model.AgeRating
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.Resolution
import nl.jellycast.core.model.Spine
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.FullBackdrop
import nl.jellycast.tv.core.designsystem.component.PillButton
import nl.jellycast.tv.core.designsystem.component.StackNeighbours
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.ui.stopHorizontalExit
import org.koin.compose.viewmodel.koinViewModel

private const val BACKDROP_SETTLE_MS = 300L
private val SuggestionTopGap = 40.dp

@Composable
fun HomeSuggestionSection(
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onBackToBar: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeSuggestionViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Home is recomposed from scratch when returning from the player, so this also fires then.
    DisposableEffect(viewModel) {
        viewModel.onVisible()
        onDispose { viewModel.onHidden() }
    }
    val playRequest = (uiState as? HomeSuggestionUiState.Success)?.playRequest
    val currentOnPlay by rememberUpdatedState(onPlay)
    if (playRequest != null) {
        LaunchedEffect(playRequest) {
            currentOnPlay(playRequest)
            viewModel.onPlayHandled()
        }
    }
    HomeSuggestionContent(
        uiState = uiState,
        takeFocus = takeFocus,
        onTakeFocusComplete = onTakeFocusComplete,
        onPlay = viewModel::onPlay,
        onRetry = viewModel::onRetry,
        onNext = viewModel::onNext,
        onPrevious = viewModel::onPrevious,
        onBackToStart = {
            viewModel.onBackToStart()
            onBackToBar()
        },
        modifier = modifier,
    )
}

@Composable
fun HomeSuggestionContent(
    uiState: HomeSuggestionUiState,
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    onBackToStart: () -> Unit = {},
) {
    val playRequester = remember { FocusRequester() }
    val success = uiState as? HomeSuggestionUiState.Success
    var hasFocus by remember { mutableStateOf(false) }
    // On the first suggestion Home itself handles Back.
    BackHandler(enabled = hasFocus && (success?.index ?: 0) > 0) { onBackToStart() }

    val shownId = success?.suggestion?.id
    var settledId by remember { mutableStateOf(shownId) }
    var showsAdReason by remember(shownId) { mutableStateOf(false) }
    var browsed by remember { mutableStateOf(false) }
    LaunchedEffect(shownId) {
        if (browsed) delay(BACKDROP_SETTLE_MS)
        browsed = false
        settledId = shownId
    }

    val onFocusComplete by rememberUpdatedState(onTakeFocusComplete)
    if (takeFocus) {
        LaunchedEffect(Unit) {
            playRequester.requestFocus()
            onFocusComplete()
        }
    }
    // Play keeps focus; left/right is handled here so each suggestion is exactly one press.
    Box(
        modifier
            .fillMaxSize()
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (success == null || event.type != KeyEventType.KeyDown) {
                    return@onPreviewKeyEvent false
                }
                when (event.key) {
                    Key.DirectionLeft -> {
                        browsed = true
                        onPrevious()
                        true
                    }

                    Key.DirectionRight -> {
                        browsed = true
                        onNext()
                        true
                    }

                    else -> false
                }
            },
    ) {
        FullBackdrop(
            url = success?.suggestion?.takeIf { it.id == settledId }?.backdropUrl(),
        )
        SuggestionColumn(
            uiState = uiState,
            showsAdReason = showsAdReason,
            onPlay = {
                if (success?.suggestion is Suggestion.AdSuggestion) {
                    showsAdReason = !showsAdReason
                } else {
                    onPlay()
                }
            },
            onRetry = onRetry,
            playRequester = playRequester,
        )
        if (success != null && success.stack.size > 1) StackPosition(success)
    }
}

@Composable
private fun SuggestionColumn(
    uiState: HomeSuggestionUiState,
    showsAdReason: Boolean,
    onPlay: () -> Unit,
    onRetry: () -> Unit,
    playRequester: FocusRequester,
) {
    val success = uiState as? HomeSuggestionUiState.Success
    Column(Modifier.padding(start = Dimens.SafeHorizontal)) {
        Spacer(Modifier.height(Dimens.SectionBarHeight + SuggestionTopGap))
        when (uiState) {
            is HomeSuggestionUiState.Success ->
                SuggestionDetails(uiState.suggestion, showsAdReason = showsAdReason)

            HomeSuggestionUiState.Loading ->
                if (rememberDelayedVisible()) {
                    SuggestionSkeleton()
                } else {
                    Spacer(Modifier.height(SuggestionHeight))
                }

            HomeSuggestionUiState.Empty -> SuggestionMessage(stringResource(R.string.home_empty))

            HomeSuggestionUiState.Error -> SuggestionError(onRetry)
        }
        Row(
            modifier = Modifier.padding(top = 18.dp).stopHorizontalExit(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val isAd = success?.suggestion is Suggestion.AdSuggestion
            val resumes = success?.suggestion?.resumePositionMs()?.let { it > 0 } == true
            PillButton(
                text =
                    stringResource(
                        when {
                            isAd -> R.string.ad_why_title
                            resumes -> R.string.home_resume
                            else -> R.string.home_play
                        },
                    ),
                onClick = onPlay,
                icon = if (isAd) null else painterResource(R.drawable.ic_play),
                isPrimary = true,
                enabled = success != null,
                modifier = Modifier.focusRequester(playRequester),
            )
        }
        success?.suggestion?.let { TechLine(it, Modifier.padding(top = 22.dp)) }
    }
}

@Composable
private fun BoxScope.StackPosition(success: HomeSuggestionUiState.Success) {
    StackNeighbours(
        previous = success.stack.getOrNull(success.index - 1)?.title(),
        next = success.stack.getOrNull(success.index + 1)?.title(),
        position = success.index + 1,
        count = success.stack.size,
        modifier =
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = Dimens.SafeHorizontal, bottom = Dimens.SafeVertical),
    )
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun HomeSuggestionContentPreview() {
    JellyCastTheme {
        HomeSuggestionContent(
            uiState =
                HomeSuggestionUiState.Success(
                    stack =
                        listOf(
                            Suggestion.MovieSuggestion(
                                reason = SuggestionReason.RecentlyAdded,
                                movie = Movie(
                                    id = "m1",
                                    title = "The Usual Suspects",
                                    year = 1995,
                                    runtimeMs = 6_360_000,
                                    overview = "Held in an L.A. interrogation room, Verbal Kint…",
                                    genres = listOf("Crime", "Thriller"),
                                    ageRating = AgeRating("12"),
                                    resumePositionMs = 0,
                                    lastPlayedAt = null,
                                    addedAt = null,
                                    artwork = Artwork(),
                                    spine = Spine(null, null, 0xFF20282B, isLight = false),
                                    tech = MediaTech(resolution = Resolution.HD),
                                ),
                            ),
                        ),
                ),
            takeFocus = false,
            onTakeFocusComplete = {},
            onPlay = {},
            onRetry = {},
        )
    }
}
