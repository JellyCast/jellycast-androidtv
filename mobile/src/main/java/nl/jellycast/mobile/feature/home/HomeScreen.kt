package nl.jellycast.mobile.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onOpenMovie: (String) -> Unit,
    onOpenSeries: (String) -> Unit,
    onPlay: (itemId: String, startPositionMs: Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val firstResume = remember { booleanArrayOf(true) }
    LifecycleResumeEffect(Unit) {
        if (!firstResume[0]) viewModel.onShown()
        firstResume[0] = false
        onPauseOrDispose { }
    }
    HomeContent(
        uiState = uiState,
        actions =
            HomeActions(
                onTabSelect = viewModel::onTabSelect,
                onQueryChange = viewModel::onQueryChange,
                onRetry = viewModel::onRetry,
                onOpenMovie = onOpenMovie,
                onOpenSeries = onOpenSeries,
                onPlay = onPlay,
                onOpenSettings = onOpenSettings,
            ),
    )
}

class HomeActions(
    val onTabSelect: (HomeTab) -> Unit,
    val onQueryChange: (String) -> Unit,
    val onRetry: () -> Unit,
    val onOpenMovie: (String) -> Unit,
    val onOpenSeries: (String) -> Unit,
    val onPlay: (String, Long) -> Unit,
    val onOpenSettings: () -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(uiState: HomeUiState, actions: HomeActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= WIDE_LAYOUT_MIN
        Row(Modifier.fillMaxSize()) {
            if (wide) TabRail(uiState.tab, actions.onTabSelect)
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(stringResource(uiState.tab.titleRes)) },
                        actions = {
                            IconButton(onClick = actions.onOpenSettings) {
                                Icon(
                                    painterResource(R.drawable.ic_settings),
                                    contentDescription = stringResource(R.string.settings_title),
                                )
                            }
                        },
                    )
                },
                bottomBar = { if (!wide) TabBar(uiState.tab, actions.onTabSelect) },
                modifier = Modifier.weight(1f),
            ) { padding ->
                TabBody(uiState, actions, padding)
            }
        }
    }
}

@Composable
private fun TabBody(uiState: HomeUiState, actions: HomeActions, padding: PaddingValues) {
    when (val load = uiState.load) {
        HomeLoad.Loading ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

        HomeLoad.Error -> ErrorMessage(actions.onRetry, Modifier.padding(padding))

        is HomeLoad.Success ->
            when (uiState.tab) {
                HomeTab.Home -> HomeRows(load.content, actions, padding)

                HomeTab.Movies ->
                    PosterGrid(
                        tiles = uiState.movies.map {
                            PosterTile(it.id, it.title, it.artwork.poster)
                        },
                        query = uiState.query,
                        onQueryChange = actions.onQueryChange,
                        onClick = actions.onOpenMovie,
                        contentPadding = padding,
                    )

                HomeTab.Series ->
                    PosterGrid(
                        tiles = uiState.series.map {
                            PosterTile(it.id, it.title, it.artwork.poster)
                        },
                        query = uiState.query,
                        onQueryChange = actions.onQueryChange,
                        onClick = actions.onOpenSeries,
                        contentPadding = padding,
                    )
            }
    }
}

@Composable
fun ErrorMessage(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.load_error), style = MaterialTheme.typography.bodyLarge)
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun TabBar(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationBar {
        HomeTab.entries.forEach { tab ->
            NavigationBarItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.iconRes), contentDescription = null) },
                label = { Text(stringResource(tab.titleRes)) },
            )
        }
    }
}

@Composable
private fun TabRail(selected: HomeTab, onSelect: (HomeTab) -> Unit) {
    NavigationRail(Modifier.padding(top = 8.dp)) {
        HomeTab.entries.forEach { tab ->
            NavigationRailItem(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                icon = { Icon(painterResource(tab.iconRes), contentDescription = null) },
                label = { Text(stringResource(tab.titleRes)) },
            )
        }
    }
}

private val HomeTab.titleRes: Int
    get() = when (this) {
        HomeTab.Home -> R.string.tab_home
        HomeTab.Movies -> R.string.tab_movies
        HomeTab.Series -> R.string.tab_series
    }

private val HomeTab.iconRes: Int
    get() = when (this) {
        HomeTab.Home -> R.drawable.ic_home
        HomeTab.Movies -> R.drawable.ic_movie
        HomeTab.Series -> R.drawable.ic_tv
    }

private val WIDE_LAYOUT_MIN = 600.dp

internal val ContentPaddingHorizontal = 16.dp
