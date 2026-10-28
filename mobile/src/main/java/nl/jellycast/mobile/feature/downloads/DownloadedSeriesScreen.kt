package nl.jellycast.mobile.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.LandscapeImage
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.downloads.Download
import nl.jellycast.mobile.core.downloads.DownloadKind
import nl.jellycast.mobile.core.downloads.DownloadStatus
import nl.jellycast.mobile.core.downloads.DownloadedMedia
import nl.jellycast.mobile.core.ui.episodeCode
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.PlayRequest
import nl.jellycast.mobile.navigation.SeriesRoute
import nl.jellycast.mobile.ui.downloads.DownloadButton
import nl.jellycast.mobile.ui.downloads.rememberDownloadText
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** The downloaded episodes of one series, in order; a tap plays one. */
@Composable
fun DownloadedSeriesScreen(
    seriesId: String,
    modifier: Modifier = Modifier,
    viewModel: DownloadedSeriesViewModel = koinViewModel(key = seriesId) { parametersOf(seriesId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    DownloadedSeriesContent(
        uiState = uiState,
        onBack = { navigation.goBack() },
        onSeriesPage = { navigation.open(SeriesRoute(seriesId)) },
        onPlay = { navigation.play(PlayRequest(PlayableItem.EpisodeItem(it.episode))) },
        modifier = modifier,
        trailing = { DownloadButton(it.download.id) },
        onRemove = { viewModel.onRemove(it.download.id) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadedSeriesContent(
    uiState: DownloadedSeriesUiState,
    onBack: () -> Unit,
    onSeriesPage: () -> Unit,
    onPlay: (DownloadedEpisode) -> Unit,
    modifier: Modifier = Modifier,
    onRemove: (DownloadedEpisode) -> Unit = {},
    trailing: @Composable (DownloadedEpisode) -> Unit = {},
) {
    val margin = LocalLayout.current.margin
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JellyCastColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(uiState.seriesName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.back),
                        )
                    }
                },
                actions = {
                    if (uiState.hasSeriesPage) {
                        IconButton(onClick = onSeriesPage) {
                            Icon(
                                painterResource(R.drawable.ic_downloads_info),
                                stringResource(R.string.downloads_series_page),
                            )
                        }
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = JellyCastColors.Background,
                        titleContentColor = JellyCastColors.Text,
                        navigationIconContentColor = JellyCastColors.Text,
                        actionIconContentColor = JellyCastColors.Text,
                    ),
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = 24.dp),
        ) {
            items(uiState.episodes, key = { it.download.id }) { item ->
                SwipeToRemove(onRemove = { onRemove(item) }) {
                    EpisodeRow(
                        item,
                        Modifier.pressable(onClick = { onPlay(item) }).padding(horizontal = margin),
                    ) { trailing(item) }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    item: DownloadedEpisode,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val episode = item.episode
    val text = rememberDownloadText()
    Row(
        modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LandscapeImage(
            url = episode.stillUrl ?: episode.artwork.landscape ?: episode.artwork.backdrop,
            title = episode.title,
            dimmed = episode.isPlayed,
            modifier = Modifier.size(width = StillWidth, height = StillHeight),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                episodeCode(episode),
                style = JellyCastText.Footnote,
                color = JellyCastColors.TextMuted,
            )
            Text(
                episode.title,
                style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
                color = if (episode.isPlayed) JellyCastColors.TextMuted else JellyCastColors.Text,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text.sizeLine(item.download),
                style = JellyCastText.Footnote,
                color = JellyCastColors.TextMuted,
            )
        }
        trailing()
    }
}

private val StillWidth = 136.dp
private val StillHeight = 76.5.dp

@Preview
@Composable
private fun DownloadedSeriesContentPreview() {
    val media =
        DownloadedMedia(
            id = "e1",
            title = "7:00 A.M.",
            seriesId = "s1",
            seriesTitle = "The Pitt",
            seasonNumber = 1,
            episodeNumber = 1,
        )
    val episode = (media.toPlayable() as PlayableItem.EpisodeItem).episode
    val download =
        Download(
            media,
            DownloadKind.Compact,
            DownloadStatus.Done,
            bytes = 1_100_000_000,
        )
    JellyCastTheme {
        DownloadedSeriesContent(
            uiState =
                DownloadedSeriesUiState(
                    seriesName = "The Pitt",
                    episodes = listOf(DownloadedEpisode(episode, download)),
                    hasSeriesPage = true,
                ),
            onBack = {},
            onSeriesPage = {},
            onPlay = {},
        )
    }
}
