package nl.jellycast.mobile.feature.person

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ContentErrorView
import nl.jellycast.mobile.core.designsystem.component.PosterImage
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.MovieRoute
import nl.jellycast.mobile.navigation.SeriesRoute
import nl.jellycast.mobile.ui.actions.ActionTarget
import nl.jellycast.mobile.ui.actions.MediaActionDrawer
import nl.jellycast.mobile.ui.info.rememberOfflineAvailability
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/** The movies and series in the library that one person appears in. */
@Composable
fun PersonScreen(
    personId: String,
    name: String,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = koinViewModel { parametersOf(personId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    var drawerTarget by remember { mutableStateOf<ActionTarget?>(null) }
    PersonContent(
        name = name,
        state = state,
        isAvailable = rememberOfflineAvailability(),
        onBack = { navigation.goBack() },
        onRetry = viewModel::onRetry,
        onOpen = { entry ->
            navigation.open(
                when (entry) {
                    is PersonEntry.MovieEntry -> MovieRoute(entry.id)
                    is PersonEntry.SeriesEntry -> SeriesRoute(entry.id)
                },
            )
        },
        onLongPress = { entry ->
            drawerTarget =
                when (entry) {
                    is PersonEntry.MovieEntry -> ActionTarget.MovieTarget(entry.movie)
                    is PersonEntry.SeriesEntry -> ActionTarget.SeriesTarget(entry.series)
                }
        },
        modifier = modifier,
    )
    MediaActionDrawer(drawerTarget, onDismiss = { drawerTarget = null })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonContent(
    name: String,
    state: PersonUiState,
    isAvailable: (String) -> Boolean,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onOpen: (PersonEntry) -> Unit,
    onLongPress: (PersonEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = JellyCastColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = JellyCastColors.Background,
                        titleContentColor = JellyCastColors.Text,
                        navigationIconContentColor = JellyCastColors.Text,
                    ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                PersonUiState.Loading ->
                    CircularProgressIndicator(
                        Modifier.align(Alignment.Center).size(28.dp),
                        color = JellyCastColors.TextMuted,
                    )

                is PersonUiState.Error ->
                    ContentErrorView(
                        message = state.message ?: stringResource(R.string.retry),
                        retryTitle = stringResource(R.string.retry),
                        onRetry = onRetry,
                        modifier = Modifier.align(Alignment.Center),
                    )

                is PersonUiState.Ready ->
                    if (state.entries.isEmpty()) {
                        Text(
                            stringResource(R.string.info_nothing_here),
                            color = JellyCastColors.TextMuted,
                            modifier = Modifier.align(Alignment.TopCenter).padding(top = 48.dp),
                        )
                    } else {
                        PersonGrid(state.entries, isAvailable, onOpen, onLongPress)
                    }
            }
        }
    }
}

@Composable
private fun PersonGrid(
    entries: List<PersonEntry>,
    isAvailable: (String) -> Boolean,
    onOpen: (PersonEntry) -> Unit,
    onLongPress: (PersonEntry) -> Unit,
) {
    val layout = LocalLayout.current
    val minWidth = if (layout.isRegular) 150.dp else 100.dp
    val columnSpacing = if (layout.isRegular) 20.dp else 12.dp
    val rowSpacing = if (layout.isRegular) 24.dp else 18.dp
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth - layout.margin * 2
        val columns =
            ((width + columnSpacing) / (minWidth + columnSpacing)).toInt().coerceAtLeast(
                MIN_COLUMNS,
            )
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(
                start = layout.margin,
                end = layout.margin,
                top = 12.dp,
                bottom = 24.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(columnSpacing),
            verticalArrangement = Arrangement.spacedBy(rowSpacing),
        ) {
            items(entries, key = { it.id }) { entry ->
                val available = isAvailable(entry.id)
                PersonTile(
                    entry,
                    onClick = { if (available) onOpen(entry) },
                    onLongClick = { onLongPress(entry) },
                    modifier = Modifier.alpha(if (available) 1f else OFFLINE_ALPHA),
                )
            }
        }
    }
}

@Composable
private fun PersonTile(
    entry: PersonEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tile =
        when (entry) {
            is PersonEntry.MovieEntry ->
                TileData(
                    entry.movie.title,
                    entry.movie.year,
                    entry.movie.artwork,
                    entry.movie.isPlayed,
                    entry.movie.progress,
                )

            is PersonEntry.SeriesEntry ->
                TileData(
                    entry.series.title,
                    entry.series.year,
                    entry.series.artwork,
                    entry.series.isPlayed,
                    null,
                )
        }
    Column(
        modifier.pressable(onClick, onLongClick),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.padding(bottom = 2.dp)) {
            PosterImage(
                tile.artwork.poster,
                tile.title,
                Modifier.fillMaxWidth(),
                progress = tile.progress.takeIf { !tile.isPlayed },
            )
            if (tile.isPlayed) WatchedBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            tile.title,
            style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        tile.year?.let {
            Text(it.toString(), style = JellyCastText.Caption, color = JellyCastColors.TextMuted)
        }
    }
}

private data class TileData(
    val title: String,
    val year: Int?,
    val artwork: Artwork,
    val isPlayed: Boolean,
    val progress: Float?,
)

@Composable
private fun WatchedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(JellyCastColors.Background.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(R.drawable.ic_check),
            null,
            Modifier.size(12.dp),
            tint = JellyCastColors.Text,
        )
    }
}

private const val MIN_COLUMNS = 3
private const val OFFLINE_ALPHA = 0.35f

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun PersonContentEmptyPreview() {
    JellyCastTheme {
        PersonContent(
            name = "Matthew McConaughey",
            state = PersonUiState.Ready(emptyList()),
            isAvailable = { true },
            onBack = {},
            onRetry = {},
            onOpen = {},
            onLongPress = {},
        )
    }
}
