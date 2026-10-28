package nl.jellycast.mobile.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ContentErrorView
import nl.jellycast.mobile.core.designsystem.component.GlassCircleButton
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.PlayRequest
import nl.jellycast.mobile.ui.actions.ActionTarget
import nl.jellycast.mobile.ui.actions.MediaActionDrawer
import nl.jellycast.mobile.ui.chat.isChatAvailable
import nl.jellycast.mobile.ui.downloads.OfflineAvailability
import nl.jellycast.mobile.ui.remote.PlayOnRequest
import nl.jellycast.mobile.ui.remote.PlayOnSheet
import nl.jellycast.mobile.ui.remote.hasRemoteTargets
import org.koin.compose.getKoin
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The series page: hero with the next episode, summary, and the Episodes, Cast and Chat tabs. */
@Composable
fun SeriesScreen(
    seriesId: String,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = koinViewModel { parametersOf(seriesId) },
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    val koin = getKoin()
    // The downloads area binds this; until then nothing counts as on the device.
    val offline = remember(koin) {
        koin.getOrNull<OfflineAvailability>()
            ?: OfflineAvailability { false }
    }
    var drawerTarget by remember { mutableStateOf<ActionTarget?>(null) }
    var playOn by remember { mutableStateOf<PlayOnRequest?>(null) }
    val canPlayOn = hasRemoteTargets()
    val tabs =
        if (isChatAvailable()) {
            SeriesTab.entries
        } else {
            listOf(SeriesTab.Episodes, SeriesTab.Cast)
        }

    SeriesContent(
        state = state,
        tabs = tabs,
        actions =
            SeriesActions(
                onBack = { navigation.goBack() },
                onPlay = { navigation.play(PlayRequest(PlayableItem.EpisodeItem(it))) },
                onPlayOn = if (canPlayOn) {
                    { playOn = PlayOnRequest(PlayableItem.EpisodeItem(it)) }
                } else {
                    null
                },
                onEpisodeLongPress = { drawerTarget = ActionTarget.EpisodeTarget(it) },
                onSeasonLongPress = { series, season ->
                    drawerTarget = ActionTarget.SeasonTarget(series, season.number, season.episodes)
                },
                onSelectSeason = viewModel::onSelectSeason,
                onToggleWatched = viewModel::onToggleSeriesWatched,
                onRetry = viewModel::onRetry,
            ),
        isAvailableOffline = offline::isAvailableOffline,
        modifier = modifier,
    )
    MediaActionDrawer(target = drawerTarget, onDismiss = { drawerTarget = null })
    PlayOnSheet(request = playOn, onDismiss = { playOn = null })
}

internal data class SeriesActions(
    val onBack: () -> Unit,
    val onPlay: (Episode) -> Unit,
    val onPlayOn: ((Episode) -> Unit)?,
    val onEpisodeLongPress: (Episode) -> Unit,
    val onSeasonLongPress: (Series, SeasonSummary) -> Unit,
    val onSelectSeason: (Int) -> Unit,
    val onToggleWatched: () -> Unit,
    val onRetry: () -> Unit,
)

@Composable
internal fun SeriesContent(
    state: SeriesUiState,
    tabs: List<SeriesTab>,
    actions: SeriesActions,
    isAvailableOffline: (String) -> Boolean,
    modifier: Modifier = Modifier,
) {
    var selectedTab by rememberSaveable { mutableStateOf(SeriesTab.Episodes) }
    val tab = selectedTab.takeIf { it in tabs } ?: tabs.first()
    Box(modifier.fillMaxSize().background(JellyCastColors.Background)) {
        when (state) {
            SeriesUiState.Loading ->
                CircularProgressIndicator(
                    Modifier.align(Alignment.Center),
                    color = JellyCastColors.TextMuted,
                )

            SeriesUiState.Missing ->
                ContentErrorView(
                    message = stringResource(R.string.series_missing),
                    retryTitle = stringResource(R.string.retry),
                    onRetry = actions.onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )

            is SeriesUiState.Ready ->
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val page = state.page
                    val available = { id: String -> !page.isOffline || isAvailableOffline(id) }
                    if (LocalLayout.current.isRegular && maxWidth > maxHeight) {
                        SeriesSplitLayout(page, tabs, tab, { selectedTab = it }, actions, available)
                    } else {
                        SeriesColumnLayout(page, tabs, tab, {
                            selectedTab = it
                        }, actions, available)
                    }
                }
        }
        GlassCircleButton(
            icon = R.drawable.ic_arrow_back,
            contentDescription = stringResource(R.string.back),
            onClick = actions.onBack,
            modifier = Modifier.statusBarsPadding().padding(start = 12.dp, top = 6.dp),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun SeriesContentPreview() {
    val series =
        Series(
            id = "s1",
            title = "Severance",
            year = 2022,
            overview = "Mark leads a team of office workers whose memories have been surgically divided.",
            genres = listOf("Drama", "Mystery"),
            ageRating = null,
            seasonCount = 2,
            lastEpisodeAddedAt = null,
            artwork = Artwork(),
            communityRating = 8.7f,
        )
    val episodes =
        (1..3).map {
            Episode(
                id = "e$it",
                seriesId = "s1",
                seriesTitle = "Severance",
                seasonNumber = 1,
                episodeNumber = it,
                title = "Episode $it",
                overview = "What happens in episode $it.",
                runtimeMs = 3_300_000,
                resumePositionMs = if (it == 2) 1_200_000 else 0,
                artwork = Artwork(),
                isPlayed = it == 1,
            )
        }
    val season = SeasonSummary(1, episodes)
    val page =
        SeriesPage(
            series = series,
            next = episodes[1],
            playKind = PlayKind.Continue,
            nextEpisodeId = "e2",
            isWatched = false,
            seasonCount = 1,
            seasons = listOf(season),
            selectedSeason = season,
            isOffline = false,
            ratingSource = RatingSource.Imdb,
        )
    JellyCastTheme {
        SeriesContent(
            state = SeriesUiState.Ready(page),
            tabs = listOf(SeriesTab.Episodes, SeriesTab.Cast),
            actions = SeriesActions({}, {}, null, {}, { _, _ -> }, {}, {}, {}),
            isAvailableOffline = { false },
        )
    }
}
