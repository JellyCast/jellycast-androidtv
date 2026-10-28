package nl.jellycast.mobile.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Spine
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ContentErrorView
import nl.jellycast.mobile.core.designsystem.component.POSTER_RATIO
import nl.jellycast.mobile.core.designsystem.component.SurfaceBlock
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.navigation.ListsRoute
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.MovieRoute
import nl.jellycast.mobile.navigation.PersonRoute
import nl.jellycast.mobile.navigation.SeriesRoute
import nl.jellycast.mobile.ui.actions.ActionTarget
import nl.jellycast.mobile.ui.actions.MediaActionDrawer
import nl.jellycast.mobile.ui.downloads.OfflineAvailability
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.getKoin

/**
 * Library: everything as a grid of posters, filtered by kind, genre and watch status, sorted,
 * and searchable by title, person and episode from the bar above the tab bar.
 */
@Composable
fun LibraryScreen(modifier: Modifier = Modifier, viewModel: LibraryViewModel = koinViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    val koin = getKoin()
    val availability = remember(koin) { koin.getOrNull<OfflineAvailability>() }
    var query by rememberSaveable { mutableStateOf("") }
    LaunchedEffect(viewModel) { if (query.isNotEmpty()) viewModel.onQueryChange(query) }
    val isOffline by rememberUpdatedState(state.isOffline)
    var target by remember { mutableStateOf<ActionTarget?>(null) }
    val haptics = LocalHapticFeedback.current
    fun hold(next: ActionTarget) {
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        target = next
    }
    val actions =
        remember(viewModel, navigation, availability) {
            LibraryGridActions(
                onOpenEntry = { entry ->
                    viewModel.onRemember()
                    navigation.open(
                        when (entry) {
                            is LibraryEntry.MovieEntry -> MovieRoute(entry.id)
                            is LibraryEntry.SeriesEntry -> SeriesRoute(entry.id)
                        },
                    )
                },
                onHoldEntry = { entry ->
                    hold(
                        when (entry) {
                            is LibraryEntry.MovieEntry -> ActionTarget.MovieTarget(entry.movie)
                            is LibraryEntry.SeriesEntry -> ActionTarget.SeriesTarget(entry.series)
                        },
                    )
                },
                onOpenPerson = { person ->
                    viewModel.onRemember()
                    navigation.open(PersonRoute(person.id, person.name))
                },
                onOpenEpisode = { episode ->
                    viewModel.onRemember()
                    navigation.open(SeriesRoute(episode.seriesId))
                },
                onHoldEpisode = { hold(ActionTarget.EpisodeTarget(it)) },
                onFiltersChange = viewModel::onFiltersChange,
                onSelectRecent = { term ->
                    query = term
                    viewModel.onQueryChange(term)
                },
                onForgetRecent = viewModel::onForget,
                isDimmed = { id -> isOffline && availability?.isAvailableOffline(id) != true },
            )
        }
    LibraryContent(
        state = state,
        query = query,
        onQueryChange = {
            query = it
            viewModel.onQueryChange(it)
        },
        onSubmit = viewModel::onRemember,
        onRefresh = viewModel::onRefresh,
        onOpenLists = { navigation.open(ListsRoute) },
        actions = actions,
        modifier = modifier,
    )
    MediaActionDrawer(target, onDismiss = { target = null })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryContent(
    state: LibraryUiState,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onRefresh: () -> Unit,
    onOpenLists: () -> Unit,
    actions: LibraryGridActions,
    modifier: Modifier = Modifier,
) {
    var isSearchFocused by remember { mutableStateOf(false) }
    var showsFilters by remember { mutableStateOf(false) }
    var pulled by remember { mutableStateOf(false) }
    LaunchedEffect(state.isSyncing) { if (!state.isSyncing) pulled = false }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box(modifier.fillMaxSize()) {
        when (val content = state.content) {
            LibraryBody.Loading -> LibrarySkeleton(Modifier.statusBarsPadding())

            is LibraryBody.Failed ->
                ContentErrorView(
                    message = content.message ?: stringResource(R.string.library_sync_failed),
                    retryTitle = stringResource(R.string.retry),
                    onRetry = onRefresh,
                    modifier = Modifier.align(Alignment.Center),
                )

            is LibraryBody.Ready ->
                PullToRefreshBox(
                    isRefreshing = pulled && state.isSyncing,
                    onRefresh = {
                        pulled = true
                        onRefresh()
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LibraryGrid(
                        state = state,
                        content = content,
                        showsRecent = isSearchFocused,
                        contentPadding =
                            PaddingValues(top = statusBarTop + 8.dp, bottom = GridBottomPadding),
                        actions = actions,
                        onScroll = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    )
                }
        }
        state.syncProblem?.let { problem ->
            SyncProblemBanner(
                problem,
                isRetrying = state.isSyncing,
                onRetry = onRefresh,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        LibrarySearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSubmit = onSubmit,
            onFocusChange = { isSearchFocused = it },
            isNarrowed = state.filters.isNarrowed,
            onOpenFilters = {
                keyboard?.hide()
                showsFilters = true
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
    val ready = state.content as? LibraryBody.Ready
    if (showsFilters) {
        LibraryFilterSheet(
            filters = state.filters,
            genres = ready?.genres.orEmpty(),
            onFiltersChange = actions.onFiltersChange,
            onOpenLists = {
                showsFilters = false
                onOpenLists()
            },
            onDismiss = { showsFilters = false },
        )
    }
}

private val GridBottomPadding = 48.dp + 8.dp + 24.dp + 8.dp

private const val SKELETON_ROWS = 4
private const val SKELETON_COLUMNS = 3

@Composable
private fun LibrarySkeleton(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        SurfaceBlock(Modifier.size(width = 120.dp, height = 14.dp), radius = 4.dp)
        repeat(SKELETON_ROWS) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repeat(SKELETON_COLUMNS) {
                    SurfaceBlock(Modifier.weight(1f).aspectRatio(POSTER_RATIO), radius = 8.dp)
                }
            }
        }
    }
}

@Preview
@Composable
private fun LibraryContentPreview() {
    val entries =
        listOf(
            LibraryEntry.MovieEntry(previewMovie("1", "Interstellar"), WatchStatus.NotStarted),
            LibraryEntry.MovieEntry(previewMovie("2", "Dune"), WatchStatus.Watched),
        )
    JellyCastTheme {
        LibraryContent(
            state =
                LibraryUiState(
                    content =
                        LibraryBody.Ready(
                            sections = listOf(LibrarySection(SectionKey.AddedThisWeek, entries)),
                            genres = emptyList(),
                            people = emptyList(),
                            episodes = emptyList(),
                            statusCount = 1,
                        ),
                ),
            query = "",
            onQueryChange = {},
            onSubmit = {},
            onRefresh = {},
            onOpenLists = {},
            actions =
                LibraryGridActions(
                    onOpenEntry = {},
                    onHoldEntry = {},
                    onOpenPerson = {},
                    onOpenEpisode = {},
                    onHoldEpisode = {},
                    onFiltersChange = {},
                    onSelectRecent = {},
                    onForgetRecent = {},
                    isDimmed = { false },
                ),
        )
    }
}

private fun previewMovie(id: String, title: String) = Movie(
    id = id,
    title = title,
    year = 2014,
    runtimeMs = 10_140_000,
    overview = null,
    genres = emptyList(),
    ageRating = null,
    resumePositionMs = 0,
    lastPlayedAt = null,
    addedAt = null,
    artwork = Artwork(),
    spine = Spine(posterUrl = null, logoUrl = null, color = 0, isLight = false),
    tech = MediaTech(),
)
