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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.Route
import nl.jellycast.mobile.ui.actions.ActionTarget
import nl.jellycast.mobile.ui.actions.MediaActionDrawer
import org.koin.androidx.compose.koinViewModel

private val FeaturedButtonInset = 6.dp

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
    onOpenSettings: () -> Unit,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRegular = LocalLayout.current.isRegular
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
                RefreshableGrid(uiState.isRefreshing, onRefresh) {
                    HomeGrid(
                        tiles = content.tiles,
                        unavailableIds = uiState.unavailableIds,
                        onTileClick = { tile ->
                            when (tile) {
                                is HomeTile.Continue -> onPlay(tile.item)
                                else -> onOpen(tile.route)
                            }
                        },
                        onTileLongPress = { onShowActions(it.actionTarget) },
                        featuredOverlay = {
                            SettingsButton(onOpenSettings, Modifier.padding(FeaturedButtonInset))
                        },
                    )
                }
        }
        StatusBarScrim()
        SyncProblemBar(uiState.syncProblem, onRetry)
    }
}

/** Tiles scroll under the status bar; a flat strip keeps its icons readable. */
@Composable
private fun StatusBarScrim(modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .background(JellyCastColors.Background)
            .statusBarsPadding(),
    )
}

@Composable
private fun SettingsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier,
        colors =
            IconButtonDefaults.iconButtonColors(
                containerColor = JellyCastColors.Background.copy(alpha = 0.45f),
                contentColor = JellyCastColors.Text,
            ),
    ) {
        Icon(painterResource(R.drawable.ic_person), stringResource(R.string.home_settings))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableGrid(
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
        // The grid runs under the status bar, so the indicator comes down below it.
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
private fun SyncProblemBar(problem: SyncProblem?, onRetry: () -> Unit) {
    var shownProblem by remember { mutableStateOf(problem) }
    if (problem != null) shownProblem = problem
    Column(Modifier.fillMaxWidth().statusBarsPadding()) {
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
