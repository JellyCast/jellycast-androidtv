package nl.jellycast.tv.feature.movies

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.MovieView
import nl.jellycast.core.model.PlaybackTech
import nl.jellycast.core.model.ServerGenreNames
import nl.jellycast.core.model.Shelf
import nl.jellycast.core.model.ShelfKind
import nl.jellycast.core.model.remainingMs
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.FullBackdrop
import nl.jellycast.tv.core.designsystem.component.HoldOkHint
import nl.jellycast.tv.core.designsystem.component.MovieInfo
import nl.jellycast.tv.core.designsystem.component.MovieInfoSkeleton
import nl.jellycast.tv.core.designsystem.component.MovieTechLine
import nl.jellycast.tv.core.designsystem.component.OVERVIEW_SHORT_LINES
import nl.jellycast.tv.core.designsystem.component.PillButton
import nl.jellycast.tv.core.designsystem.component.StackNeighbours
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.ui.isOk
import nl.jellycast.tv.core.ui.remainingText

private const val SETTLE_MS = 300L

private val TechLineHeight = 16.dp

// More lines would push the button off screen.
private const val FULL_OVERVIEW_LINES = 8

private val InfoTop = 128.dp

class MovieBrowseActions(
    val onShelfChange: (Int) -> Unit,
    val onPlayClick: (Movie) -> Unit,
    val onPlayFromStart: (Movie) -> Unit,
    val onSetWatched: (Movie, played: Boolean) -> Unit,
    val onMovieSettled: (Movie) -> Unit,
)

// The screen handles the keys itself, so every movie and every shelf is exactly one press away
// and focus never jumps; focus stays on the play button.
@Composable
internal fun MovieBrowse(
    uiState: MoviesUiState.Success,
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    actions: MovieBrowseActions,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
) {
    val shelf = uiState.shelf
    var focusedMovieId by rememberSaveable { mutableStateOf<String?>(null) }
    val lastShown = remember { LastShownPosition() }
    val index = lastShown.indexOf(focusedMovieId, shelf)
    val movie = shelf.items[index]
    // Updated on every key press, so a second ↓ before the next composition still counts.
    var requestedShelf by remember { mutableIntStateOf(uiState.shelfIndex) }
    val shelfKinds = remember(uiState.shelves) { uiState.shelves.map { it.kind } }
    LaunchedEffect(shelfKinds) { requestedShelf = uiState.shelfIndex }

    // Saveable, so the backdrop is there at once when coming back from the player.
    var settledId by rememberSaveable { mutableStateOf<String?>(null) }
    val latestOnSettled by rememberUpdatedState(actions.onMovieSettled)
    LaunchedEffect(movie.id) {
        // After a removed movie its neighbour becomes the focused one.
        focusedMovieId = movie.id
        if (settledId != movie.id) {
            delay(SETTLE_MS)
            settledId = movie.id
        }
        latestOnSettled(movie)
    }

    var optionsOpen by rememberSaveable { mutableStateOf(false) }
    var fullOverviewId by rememberSaveable { mutableStateOf<String?>(null) }
    var hasFocus by remember { mutableStateOf(false) }
    var isFlipping by remember { mutableStateOf(false) }
    val buttonRequester = remember { FocusRequester() }
    val okPress = remember { OkPress() }

    val latestOnTakeFocusComplete by rememberUpdatedState(onTakeFocusComplete)
    if (takeFocus) {
        LaunchedEffect(Unit) {
            buttonRequester.requestFocus()
            latestOnTakeFocusComplete()
        }
    }

    fun showMovie(target: Int) {
        focusedMovieId = shelf.items[target].id
    }

    fun changeShelf(target: Int) {
        requestedShelf = target
        focusedMovieId = uiState.shelves[target].items.first().id
        actions.onShelfChange(target)
    }

    fun closeOptions() {
        optionsOpen = false
        buttonRequester.requestFocus()
    }

    BackHandler(enabled = optionsOpen) { closeOptions() }
    BackHandler(enabled = hasFocus && !optionsOpen) {
        if (requestedShelf != 0) changeShelf(0) else showMovie(0)
        onBackToBar()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onFocusChanged { hasFocus = it.hasFocus }
                .onPreviewKeyEvent { event ->
                    // One OK press belongs to one action: once the options are open, its repeats
                    // and its release must not pick anything there.
                    if (event.isOk) {
                        return@onPreviewKeyEvent okPress.handle(
                            event = event,
                            enabled = !optionsOpen,
                            onClick = { actions.onPlayClick(movie) },
                            onLongPress = { optionsOpen = true },
                        )
                    }
                    if (event.isHorizontalArrow) isFlipping = event.isRepeatedKeyDown
                    if (optionsOpen || event.type != KeyEventType.KeyDown) {
                        return@onPreviewKeyEvent false
                    }
                    handleBrowseArrow(
                        key = event.key,
                        movieIndex = index,
                        lastMovieIndex = shelf.items.lastIndex,
                        requestedShelf = requestedShelf,
                        lastShelfIndex = uiState.shelves.lastIndex,
                        showMovie = ::showMovie,
                        changeShelf = ::changeShelf,
                    )
                },
    ) {
        BrowseBackdrop(movie = movie, isSettled = settledId == movie.id)
        BrowseInfo(
            movie = movie,
            tech = uiState.tech[movie.id],
            showFullOverview = fullOverviewId == movie.id,
            isFlipping = isFlipping,
            buttonRequester = buttonRequester,
        )
        StackNeighbours(
            previous = shelf.items.getOrNull(index - 1)?.title,
            next = shelf.items.getOrNull(index + 1)?.title,
            position = index + 1,
            count = shelf.items.size,
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Dimens.SafeHorizontal, bottom = Dimens.SafeVertical),
        )
        if (optionsOpen) {
            MovieOptions(
                movie = movie,
                onPlayFromStart = {
                    closeOptions()
                    actions.onPlayFromStart(movie)
                },
                onWatchedChange = { watched ->
                    closeOptions()
                    actions.onSetWatched(movie, watched)
                },
                onFullOverview = {
                    closeOptions()
                    fullOverviewId = movie.id
                },
                // The section bar lies on top of the content.
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(top = Dimens.SectionBarHeight),
            )
        }
    }
}

@Composable
private fun BrowseBackdrop(movie: Movie, isSettled: Boolean) {
    if (isSettled) {
        FullBackdrop(url = movie.artwork.backdrop, baseColor = Color(movie.spine.darkColor))
    } else {
        // A plain fill while flipping: re-recording the offscreen backdrop would cost an extra
        // full-screen pass per step.
        Box(Modifier.fillMaxSize().background(Color(movie.spine.darkColor)))
    }
}

// While flipping (←/→ held) only logo, metadata and button are drawn, so each step fits in one
// frame; releasing the key shows the rest at once.
@Composable
private fun BrowseInfo(
    movie: Movie,
    tech: PlaybackTech?,
    showFullOverview: Boolean,
    isFlipping: Boolean,
    buttonRequester: FocusRequester,
) {
    Column(Modifier.padding(start = Dimens.SafeHorizontal, top = InfoTop)) {
        MovieInfo(
            movie = movie,
            overviewMaxLines = if (showFullOverview) FULL_OVERVIEW_LINES else OVERVIEW_SHORT_LINES,
            fixedLines = true,
            showOverview = !isFlipping,
        )
        Row(
            modifier = Modifier.padding(top = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val resume = remainingMs(movie.resumePositionMs, movie.runtimeMs)
            PillButton(
                text =
                    stringResource(
                        if (resume != null) R.string.movies_resume else R.string.movies_play,
                    ),
                trailing = resume?.let { remainingText(it) },
                // OK is handled by the screen's key handler (short = play, long = options).
                onClick = {},
                icon = painterResource(R.drawable.ic_play),
                isPrimary = true,
                modifier = Modifier.focusRequester(buttonRequester),
            )
            HoldOkHint()
        }
        if (isFlipping) {
            Spacer(Modifier.padding(top = 22.dp).height(TechLineHeight))
        } else {
            MovieTechLine(
                movie = movie,
                tech = tech,
                modifier = Modifier.padding(top = 22.dp).height(TechLineHeight),
            )
        }
    }
}

@Composable
internal fun MovieBrowseSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(start = Dimens.SafeHorizontal, top = InfoTop)) {
        MovieInfoSkeleton()
        PillButton(
            text = stringResource(R.string.movies_play),
            onClick = {},
            icon = painterResource(R.drawable.ic_play),
            isPrimary = true,
            enabled = false,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

// Plain fields, not State: only read to pick a neighbour when the shown movie leaves its shelf.
private class LastShownPosition {
    private var kind: ShelfKind? = null
    private var index = 0

    fun indexOf(movieId: String?, shelf: Shelf<Movie>): Int {
        val found = shelf.items.indexOfFirst { it.id == movieId }
        index =
            when {
                found >= 0 -> found
                kind == shelf.kind -> index.coerceAtMost(shelf.items.lastIndex)
                else -> 0
            }
        kind = shelf.kind
        return index
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun MovieBrowsePreview() {
    JellyCastTheme {
        MovieBrowse(
            uiState =
                MoviesUiState.Success(
                    shelves = buildMovieShelves(
                        List(5) { PreviewMovie.copy(id = "m$it") },
                        ServerGenreNames,
                    ),
                    view = MovieView.Detail,
                ),
            takeFocus = false,
            onTakeFocusComplete = {},
            actions = MovieBrowseActions({}, {}, {}, { _, _ -> }, {}),
        )
    }
}
