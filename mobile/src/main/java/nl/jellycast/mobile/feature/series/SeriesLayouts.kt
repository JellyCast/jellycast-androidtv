package nl.jellycast.mobile.feature.series

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.ui.episodeCode
import nl.jellycast.mobile.ui.chat.ChatTab
import nl.jellycast.mobile.ui.info.InfoSubject

private const val SPLIT_LEADING_FRACTION = 0.645f
private const val TAB_BAR_INDEX = 2
private val SummaryTop = 18.dp
private val TabBarTop = 28.dp
private val ColumnTabContentTop = 22.dp
private val SplitPadding = 24.dp
private val SplitTabContentTop = 10.dp
private val BottomPadding = 40.dp
private val EpisodeSpacing = 18.dp

// Room for the back button when a chosen tab scrolls the bar up to the top.
private val BackButtonClearance = 56.dp

/** Phones and tablets in portrait: one scrolling column. */
@Composable
internal fun SeriesColumnLayout(
    page: SeriesPage,
    tabs: List<SeriesTab>,
    tab: SeriesTab,
    onSelectTab: (SeriesTab) -> Unit,
    actions: SeriesActions,
    isAvailable: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayout.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tabBarScrollOffset = with(LocalDensity.current) {
        (
            TabBarTop - topInset -
                BackButtonClearance
            ).roundToPx()
    }
    LazyColumn(
        modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(bottom = BottomPadding),
    ) {
        item(key = "hero") { PageHero(page, actions, isAvailable) }
        item(key = "summary") {
            PageSummary(
                page,
                actions,
                Modifier
                    .padding(horizontal = layout.margin)
                    .padding(top = SummaryTop)
                    .widthIn(max = layout.textMaxWidth),
            )
        }
        item(key = "tabs") {
            SeriesTabBar(
                tabs = tabs,
                selected = tab,
                onSelect = {
                    onSelectTab(it)
                    scope.launch {
                        listState.animateScrollToItem(TAB_BAR_INDEX, tabBarScrollOffset)
                    }
                },
                modifier =
                    Modifier
                        .padding(horizontal = layout.margin)
                        .padding(top = TabBarTop)
                        .widthIn(max = layout.textMaxWidth),
            )
        }
        tabContent(
            page = page,
            tab = tab,
            margin = layout.margin,
            textMaxWidth = layout.textMaxWidth,
            contentTop = ColumnTabContentTop,
            isLarge = layout.isRegular,
            actions = actions,
            isAvailable = isAvailable,
        )
    }
}

/** Tablets in landscape: hero and summary on the left, the tabs scrolling on their own on the right. */
@Composable
internal fun SeriesSplitLayout(
    page: SeriesPage,
    tabs: List<SeriesTab>,
    tab: SeriesTab,
    onSelectTab: (SeriesTab) -> Unit,
    actions: SeriesActions,
    isAvailable: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    val layout = LocalLayout.current
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    Row(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth(SPLIT_LEADING_FRACTION).fillMaxHeight()) {
            PageHero(page, actions, isAvailable)
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                PageSummary(
                    page,
                    actions,
                    Modifier.padding(
                        horizontal = layout.margin,
                    ).padding(top = SummaryTop, bottom = BottomPadding),
                )
            }
        }
        Column(Modifier.weight(1f).fillMaxHeight()) {
            SeriesTabBar(
                tabs = tabs,
                selected = tab,
                onSelect = onSelectTab,
                modifier =
                    Modifier
                        .padding(horizontal = SplitPadding)
                        .padding(top = SummaryTop + topInset, bottom = 8.dp),
            )
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(bottom = BottomPadding),
            ) {
                tabContent(
                    page = page,
                    tab = tab,
                    margin = SplitPadding,
                    textMaxWidth = Dp.Infinity,
                    contentTop = SplitTabContentTop,
                    isLarge = false,
                    actions = actions,
                    isAvailable = isAvailable,
                )
            }
        }
    }
}

private fun LazyListScope.tabContent(
    page: SeriesPage,
    tab: SeriesTab,
    margin: Dp,
    textMaxWidth: Dp,
    contentTop: Dp,
    isLarge: Boolean,
    actions: SeriesActions,
    isAvailable: (String) -> Boolean,
) {
    when (tab) {
        SeriesTab.Episodes -> {
            item(key = "seasons") {
                SeasonChips(
                    seasons = page.seasons,
                    selected = page.selectedSeason.number,
                    margin = margin,
                    onSelect = actions.onSelectSeason,
                    onLongPress =
                        if (page.isOffline) {
                            null
                        } else {
                            { season ->
                                actions.onSeasonLongPress(page.series, season)
                            }
                        },
                    modifier = Modifier.padding(top = contentTop, bottom = EpisodeSpacing),
                )
            }
            items(page.selectedSeason.episodes, key = { it.id }) { episode ->
                EpisodeRow(
                    episode = episode,
                    series = page.series,
                    ratingSource = page.ratingSource,
                    isNext = episode.id == page.nextEpisodeId,
                    isLarge = isLarge,
                    isAvailable = isAvailable(episode.id),
                    onPlay = { actions.onPlay(episode) },
                    onLongPress = { actions.onEpisodeLongPress(episode) },
                    modifier = Modifier.padding(
                        horizontal = margin,
                    ).padding(bottom = EpisodeSpacing),
                )
            }
        }

        SeriesTab.Cast ->
            item(key = "cast") {
                SeriesCastTab(
                    page.series.id,
                    Modifier.padding(
                        horizontal = margin,
                    ).padding(top = contentTop).widthIn(max = textMaxWidth),
                )
            }

        SeriesTab.Chat ->
            item(key = "chat") {
                ChatTab(
                    InfoSubject.SeriesInfo(page.series.id),
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = margin)
                        .padding(top = contentTop)
                        .widthIn(max = textMaxWidth),
                )
            }
    }
}

@Composable
private fun PageHero(page: SeriesPage, actions: SeriesActions, isAvailable: (String) -> Boolean) {
    val next = page.next
    val artwork = page.series.artwork
    SeriesHero(
        backdropUrl = artwork.backdrop ?: artwork.poster,
        logoUrl = artwork.logo,
        title = page.series.title,
        caption = playCaption(page),
        progress = next.progress.takeIf { !next.isPlayed },
        isPlayable = isAvailable(next.id),
        onPlay = { actions.onPlay(next) },
    )
}

@Composable
private fun PageSummary(page: SeriesPage, actions: SeriesActions, modifier: Modifier = Modifier) {
    val onPlayOn = actions.onPlayOn
    SeriesSummary(
        page = page,
        onPlayOn = onPlayOn?.let { { it(page.next) } },
        onToggleWatchState = actions.onToggleWatched,
        modifier = modifier,
    )
}

/** "Continue · S2 · E3", "Watch again · S1 · E1" or "Play · S2 · E3". */
@Composable
private fun playCaption(page: SeriesPage): String {
    val code = episodeCode(page.next)
    return when (page.playKind) {
        PlayKind.Continue -> stringResource(R.string.series_continue, code)
        PlayKind.WatchAgain -> stringResource(R.string.series_watch_again, code)
        PlayKind.Play -> stringResource(R.string.series_play, code)
    }
}
