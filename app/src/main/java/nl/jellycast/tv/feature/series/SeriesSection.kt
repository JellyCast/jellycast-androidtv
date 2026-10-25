package nl.jellycast.tv.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.CornerBackdrop
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.HoldOkHint
import nl.jellycast.tv.core.designsystem.component.LandscapeCard
import nl.jellycast.tv.core.designsystem.component.PosterCard
import nl.jellycast.tv.core.designsystem.component.ProvideBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.RowBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.TvRow
import nl.jellycast.tv.core.designsystem.component.rememberCachedLazyListState
import nl.jellycast.tv.core.designsystem.component.rememberColumnBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.Artwork
import nl.jellycast.tv.core.model.Episode
import nl.jellycast.tv.core.model.PlayRequest
import nl.jellycast.tv.core.model.Series
import nl.jellycast.tv.core.model.ServerGenreNames
import nl.jellycast.tv.core.model.ShelfKind
import nl.jellycast.tv.core.model.progress
import nl.jellycast.tv.core.model.remainingMs
import nl.jellycast.tv.core.ui.episodeCode
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.isArrowKey
import nl.jellycast.tv.core.ui.isOk
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.runtimeText
import nl.jellycast.tv.core.ui.seasonCountText
import nl.jellycast.tv.core.ui.stopHorizontalExit
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun SeriesSection(
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onPlay: (PlayRequest) -> Unit,
    onOpenSeries: (seriesId: String, episodeId: String?) -> Unit,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
    viewModel: SeriesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.onShown() }
    val playRequest = (uiState as? SeriesUiState.Success)?.playRequest
    val currentOnPlay by rememberUpdatedState(onPlay)
    if (playRequest != null) {
        LaunchedEffect(playRequest) {
            currentOnPlay(playRequest)
            viewModel.onPlayHandled()
        }
    }
    SeriesContent(
        uiState = uiState,
        takeFocus = takeFocus,
        onTakeFocusComplete = onTakeFocusComplete,
        onBackToBar = onBackToBar,
        onFocusItem = viewModel::onFocusItem,
        onItemClick = { item ->
            when (item) {
                is SeriesItem.NextUp -> viewModel.onEpisodeClick(item.episode)
                is SeriesItem.Show -> onOpenSeries(item.series.id, null)
            }
        },
        episodeActions =
            EpisodeActions(
                onWatchedChange = viewModel::onSetEpisodePlayed,
                onOpenSeries = { onOpenSeries(it.seriesId, it.id) },
            ),
        onRetry = viewModel::onRetry,
        modifier = modifier,
    )
}

@Composable
fun SeriesContent(
    uiState: SeriesUiState,
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onFocusItem: (SeriesItem) -> Unit,
    onItemClick: (SeriesItem) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
    episodeActions: EpisodeActions = EpisodeActions(),
) {
    when (uiState) {
        SeriesUiState.Loading -> if (rememberDelayedVisible()) SeriesSkeleton(modifier)

        SeriesUiState.Error -> {
            val onFocusComplete by rememberUpdatedState(onTakeFocusComplete)
            if (takeFocus) LaunchedEffect(Unit) { onFocusComplete() }
            ErrorState(
                message = stringResource(R.string.library_error),
                onRetry = onRetry,
                takeFocus = takeFocus,
                modifier = modifier.padding(start = Dimens.SafeHorizontal, top = 120.dp),
            )
        }

        is SeriesUiState.Success ->
            SeriesLoaded(
                uiState = uiState,
                takeFocus = takeFocus,
                onTakeFocusComplete = onTakeFocusComplete,
                onBackToBar = onBackToBar,
                onFocusItem = onFocusItem,
                onItemClick = onItemClick,
                episodeActions = episodeActions,
                modifier = modifier,
            )
    }
}

private data class FocusTarget(val rowKey: String, val itemId: String)

@Composable
private fun SeriesLoaded(
    uiState: SeriesUiState.Success,
    takeFocus: Boolean,
    onTakeFocusComplete: () -> Unit,
    onFocusItem: (SeriesItem) -> Unit,
    onItemClick: (SeriesItem) -> Unit,
    episodeActions: EpisodeActions,
    modifier: Modifier = Modifier,
    onBackToBar: () -> Unit = {},
) {
    val rows = uiState.rows
    val start = rows.firstOrNull()?.let { row -> FocusTarget(row.key, row.items.first().id) }
    var lastRowKey by rememberSaveable { mutableStateOf<String?>(null) }
    var lastItemId by rememberSaveable { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<FocusTarget?>(null) }
    var hasFocus by remember { mutableStateOf(false) }
    // Keeps a row above and below composed, so up/down always lands on an existing row.
    val columnState = rememberCachedLazyListState(ahead = 240.dp)
    val scope = rememberCoroutineScope()

    val last =
        lastRowKey?.let { key ->
            lastItemId?.let { id -> FocusTarget(key, id).takeIf { rows.hasItem(it) } }
        }

    if (takeFocus) {
        LaunchedEffect(Unit) { pending = last ?: start }
    }
    // Scroll the row into view; the item requests focus itself once it is composed.
    LaunchedEffect(pending) {
        val target = pending ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == target.rowKey }
        val visible = columnState.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (index >= 0 && !visible) columnState.scrollToItem(index)
    }

    BackHandler(enabled = hasFocus && start != null) {
        scope.launch { columnState.scrollToItem(0) }
        lastRowKey = start?.rowKey
        lastItemId = start?.itemId
        rows.first().items.first().let(onFocusItem)
        onBackToBar()
    }

    var optionsItemId by rememberSaveable { mutableStateOf<String?>(null) }
    val optionsEpisode =
        optionsItemId?.let { id -> (rows.findItem(id) as? SeriesItem.NextUp)?.episode }
    // The OK press that opened the panel keeps repeating until release; none of it may pick an
    // option.
    var swallowOk by remember { mutableStateOf(false) }

    fun closeOptions() {
        optionsItemId = null
        pending = last
    }

    BackHandler(enabled = optionsEpisode != null) { closeOptions() }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (!swallowOk || !event.isOk) return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyUp) swallowOk = false
                    true
                },
    ) {
        CornerBackdrop(
            url = uiState.hero?.backdropUrl(),
            modifier = Modifier.align(Alignment.TopEnd),
        )
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.height(Dimens.SectionBarHeight))
            SeriesHero(item = uiState.hero)
            SeriesRowList(
                rows = rows,
                columnState = columnState,
                pendingTarget = { pending },
                onPendingFocus = {
                    pending = null
                    onTakeFocusComplete()
                },
                onItemFocus = { rowKey, item ->
                    lastRowKey = rowKey
                    lastItemId = item.id
                    onFocusItem(item)
                },
                onItemClick = onItemClick,
                onItemLongClick = { item ->
                    optionsItemId = item.id
                    swallowOk = true
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .onFocusChanged { hasFocus = it.hasFocus },
            )
        }
        if (optionsEpisode != null) {
            EpisodeOptions(
                episode = optionsEpisode,
                onWatchedChange = { watched ->
                    closeOptions()
                    episodeActions.onWatchedChange(optionsEpisode, watched)
                },
                onOpenSeries = {
                    closeOptions()
                    episodeActions.onOpenSeries(optionsEpisode)
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
private fun SeriesRowList(
    rows: List<SeriesRow>,
    columnState: LazyListState,
    pendingTarget: () -> FocusTarget?,
    onPendingFocus: () -> Unit,
    onItemFocus: (rowKey: String, item: SeriesItem) -> Unit,
    onItemClick: (SeriesItem) -> Unit,
    onItemLongClick: (SeriesItem.NextUp) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProvideBringIntoViewSpec(rememberColumnBringIntoViewSpec(cardTop = RowCardTop)) {
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 24.dp, bottom = Dimens.SafeVertical),
            verticalArrangement = Arrangement.spacedBy(Dimens.RowGap),
            modifier = modifier,
        ) {
            rows.forEach { row ->
                item(key = row.key, contentType = row.contentType()) {
                    val rowPending = pendingTarget()?.takeIf { it.rowKey == row.key }
                    ShelfRow(
                        row = row,
                        pendingItemId = rowPending?.itemId,
                        onPendingFocus = onPendingFocus,
                        onFocus = { item -> onItemFocus(row.key, item) },
                        onItemClick = onItemClick,
                        onItemLongClick = onItemLongClick,
                    )
                }
            }
        }
    }
}

// Row header (20 dp) + gap (12 dp) + list top padding (24 dp) + 18 dp of the row above.
private val RowCardTop = 74.dp

private fun List<SeriesRow>.hasItem(target: FocusTarget): Boolean =
    any { row -> row.key == target.rowKey && row.items.any { it.id == target.itemId } }

private fun SeriesRow.contentType(): String =
    if (items.firstOrNull() is SeriesItem.NextUp) "landscape" else "poster"

private fun SeriesItem.backdropUrl(): String? = when (this) {
    is SeriesItem.NextUp -> episode.artwork.backdrop
    is SeriesItem.Show -> series.artwork.backdrop
}

@Composable
private fun ShelfRow(
    row: SeriesRow,
    pendingItemId: String?,
    onPendingFocus: () -> Unit,
    onFocus: (SeriesItem) -> Unit,
    onItemClick: (SeriesItem) -> Unit,
    onItemLongClick: (SeriesItem.NextUp) -> Unit,
    modifier: Modifier = Modifier,
) {
    TvRow(
        items = row.items,
        key = { it.id },
        pendingKey = pendingItemId,
        onPendingFocus = onPendingFocus,
        header = {
            RowTitle(
                text = shelfTitle(row.kind),
                showHoldHint = row.kind == ShelfKind.ContinueWatching,
            )
        },
        contentType = { it::class },
        modifier = modifier,
    ) { item, itemModifier ->
        SeriesCard(
            item = item,
            onClick = { onItemClick(item) },
            onLongClick = { if (item is SeriesItem.NextUp) onItemLongClick(item) },
            onFocus = { onFocus(item) },
            modifier = itemModifier,
        )
    }
}

@Composable
private fun SeriesCard(
    item: SeriesItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (item) {
        is SeriesItem.NextUp -> {
            val episode = item.episode
            val code = episodeCode(episode.seasonNumber, episode.episodeNumber)
            LandscapeCard(
                imageUrl = episode.artwork.landscape,
                title = episode.seriesTitle,
                label =
                    if (episode.resumePositionMs > 0) {
                        code
                    } else {
                        stringResource(R.string.series_card_new, code)
                    },
                progress = episode.progress,
                onClick = onClick,
                onLongClick = onLongClick,
                onFocus = onFocus,
                modifier = modifier,
            )
        }

        is SeriesItem.Show ->
            PosterCard(
                imageUrl = item.series.artwork.poster,
                title = item.series.title,
                onClick = onClick,
                onFocus = onFocus,
                modifier = modifier,
            )
    }
}

@Composable
private fun RowTitle(text: String, showHoldHint: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = Dimens.SafeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (showHoldHint) HoldOkHint()
    }
}

@Composable
private fun shelfTitle(kind: ShelfKind): String = when (kind) {
    ShelfKind.ContinueWatching -> stringResource(R.string.shelf_continue)
    ShelfKind.New -> stringResource(R.string.series_shelf_new)
    ShelfKind.NotStarted -> stringResource(R.string.series_shelf_not_started)
    is ShelfKind.Genre -> kind.name
}

private val PreviewEpisode =
    Episode(
        id = "e1",
        seriesId = "s1",
        seriesTitle = "The Bear",
        seasonNumber = 3,
        episodeNumber = 4,
        title = "Violet",
        overview = "Sydney gets a new apartment. Marcus finds inspiration.",
        runtimeMs = 1_800_000,
        resumePositionMs = 720_000,
        artwork = Artwork(),
    )

private val PreviewSeries =
    Series(
        id = "s2",
        title = "The Last of Us",
        year = 2023,
        overview = "Twenty years after modern civilization has been destroyed…",
        genres = listOf("Drama"),
        ageRating = null,
        seasonCount = 2,
        lastEpisodeAddedAt = null,
        artwork = Artwork(),
    )

@Preview(device = Devices.TV_1080p)
@Composable
private fun SeriesContentPreview() {
    JellyCastTheme {
        val rows =
            buildSeriesRows(
                series = List(8) { PreviewSeries.copy(id = "s$it") },
                nextUp = List(5) { PreviewEpisode.copy(id = "e$it") },
                genreNames = ServerGenreNames,
                nowMs = 0,
            )
        SeriesContent(
            uiState = SeriesUiState.Success(rows = rows, hero = rows.firstItem()),
            takeFocus = false,
            onTakeFocusComplete = {},
            onFocusItem = {},
            onItemClick = {},
            onRetry = {},
        )
    }
}
