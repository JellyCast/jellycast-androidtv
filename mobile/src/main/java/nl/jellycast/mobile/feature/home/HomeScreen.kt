package nl.jellycast.mobile.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ContentErrorView
import nl.jellycast.mobile.core.designsystem.component.SectionHeader
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.library.NewItem
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.MovieRoute
import nl.jellycast.mobile.navigation.Route
import nl.jellycast.mobile.navigation.SeriesRoute
import nl.jellycast.mobile.ui.actions.ActionTarget
import nl.jellycast.mobile.ui.actions.MediaActionDrawer
import org.koin.androidx.compose.koinViewModel

private val SectionSpacing = 26.dp
private val HeroBottomSpacing = 18.dp
private val TopBarHeight = 56.dp

/** Home: what to watch now. Built from the cached library, so it shows without the server. */
@Composable
fun HomeScreen(modifier: Modifier = Modifier) {
    val viewModel: HomeViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    var actionTarget by remember { mutableStateOf<ActionTarget?>(null) }
    HomeContent(
        uiState = uiState,
        onPlay = { navigation.play(it) },
        onOpen = navigation::open,
        onShowActions = { actionTarget = it },
        onShowAllNew = navigation::showNewInLibrary,
        onOpenSettings = { navigation.showsSettings = true },
        onRefresh = viewModel::onRefresh,
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
    MediaActionDrawer(actionTarget, onDismiss = { actionTarget = null })
}

@Composable
internal fun HomeContent(
    uiState: HomeUiState,
    onPlay: (PlayableItem) -> Unit,
    onOpen: (Route) -> Unit,
    onShowActions: (ActionTarget) -> Unit,
    onShowAllNew: () -> Unit,
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxSize().background(JellyCastColors.Background)) {
        when (val content = uiState.content) {
            HomeContent.Loading -> HomeSkeleton()

            is HomeContent.Failed ->
                ContentErrorView(
                    message = syncMessage(content.message, content.isOffline),
                    retryTitle = stringResource(R.string.retry),
                    onRetry = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

            is HomeContent.Ready ->
                RefreshableRows(uiState.isRefreshing, onRefresh) {
                    HomeRowsColumn(
                        content.rows,
                        uiState.unavailableIds,
                        onPlay,
                        onOpen,
                        onShowActions,
                        onShowAllNew,
                    )
                }
        }
        HomeTopBar(uiState.syncProblem, onOpenSettings, onRetry)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableRows(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    content: @Composable () -> Unit,
) {
    val state = rememberPullToRefreshState()
    val label = stringResource(R.string.home_refresh)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = Modifier.fillMaxSize(),
        // The rows run under the status bar, so the indicator comes down below it.
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = isRefreshing,
                containerColor = JellyCastColors.Surface,
                color = JellyCastColors.Accent,
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .semantics { contentDescription = label },
            )
        },
    ) {
        content()
    }
}

@Composable
private fun HomeRowsColumn(
    rows: HomeRows,
    unavailableIds: Set<String>,
    onPlay: (PlayableItem) -> Unit,
    onOpen: (Route) -> Unit,
    onShowActions: (ActionTarget) -> Unit,
    onShowAllNew: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(SectionSpacing),
    ) {
        rows.hero?.let { hero ->
            HomeHeroCard(
                hero = hero,
                unavailable = hero.item.id in unavailableIds,
                onOpen = { onOpen(hero.item.route) },
                onPlay = { onPlay(hero.item) },
                onLongPress = { onShowActions(hero.item.actionTarget) },
                modifier = Modifier.padding(bottom = HeroBottomSpacing),
            )
        }
        if (rows.upNext.isNotEmpty()) {
            HomeSection(
                stringResource(R.string.home_up_next),
                stringResource(R.string.home_up_next_subtitle),
            ) {
                items(rows.upNext, key = { it.item.id }) { entry ->
                    UpNextCard(
                        entry = entry,
                        unavailable = entry.item.id in unavailableIds,
                        onPlay = { onPlay(entry.item) },
                        onLongPress = { onShowActions(entry.item.actionTarget) },
                    )
                }
            }
        }
        if (rows.newEntries.isNotEmpty()) {
            HomeSection(
                stringResource(R.string.home_new),
                stringResource(R.string.home_new_subtitle),
                linkTitle = stringResource(R.string.home_all),
                onLink = onShowAllNew,
            ) {
                items(rows.newEntries, key = { it.itemId }) { entry ->
                    NewEntryCard(
                        entry = entry,
                        unavailable = entry.itemId in unavailableIds,
                        onOpen = { onOpen(entry.item.route) },
                        onLongPress = { onShowActions(entry.item.actionTarget) },
                    )
                }
            }
        }
        if (rows.notStarted.isNotEmpty()) {
            HomeSection(
                stringResource(R.string.home_not_started),
                stringResource(R.string.home_not_started_subtitle),
            ) {
                items(rows.notStarted, key = { it.series.id }) { entry ->
                    NotStartedCard(
                        entry = entry,
                        unavailable = entry.series.id in unavailableIds,
                        onOpen = { onOpen(SeriesRoute(entry.series.id)) },
                        onLongPress = { onShowActions(ActionTarget.SeriesTarget(entry.series)) },
                    )
                }
            }
        }
        // Tablets have no bottom bar, so the rows end above the system navigation.
        if (LocalLayout.current.isRegular) Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun HomeSection(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    linkTitle: String? = null,
    onLink: (() -> Unit)? = null,
    cards: LazyListScope.() -> Unit,
) {
    val layout = LocalLayout.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader(
            title,
            Modifier.padding(horizontal = layout.margin),
            subtitle = subtitle,
            linkTitle = linkTitle,
            onLink = onLink,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = layout.margin),
            horizontalArrangement = Arrangement.spacedBy(layout.cardSpacing),
            verticalAlignment = Alignment.Top,
            content = cards,
        )
    }
}

/** The settings button at the top end, and under it the sync problem when there is one. */
@Composable
private fun HomeTopBar(problem: SyncProblem?, onOpenSettings: () -> Unit, onRetry: () -> Unit) {
    var shownProblem by remember { mutableStateOf(problem) }
    if (problem != null) shownProblem = problem
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onOpenSettings,
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = JellyCastColors.Background.copy(alpha = 0.45f),
                        contentColor = JellyCastColors.Text,
                    ),
            ) {
                Icon(painterResource(R.drawable.ic_person), stringResource(R.string.home_settings))
            }
        }
        AnimatedVisibility(
            visible = problem != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            shownProblem?.let {
                SyncProblemBanner(
                    it,
                    onRetry,
                    Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private val PlayableItem.route: Route
    get() = when (this) {
        is PlayableItem.MovieItem -> MovieRoute(movie.id)
        is PlayableItem.EpisodeItem -> SeriesRoute(episode.seriesId)
    }

private val NewItem.route: Route
    get() = when (this) {
        is NewItem.NewMovie -> MovieRoute(movie.id)
        is NewItem.NewSeries -> SeriesRoute(series.id)
    }

private val NewItem.actionTarget: ActionTarget
    get() = when (this) {
        is NewItem.NewMovie -> ActionTarget.MovieTarget(movie)
        is NewItem.NewSeries -> ActionTarget.SeriesTarget(series)
    }
