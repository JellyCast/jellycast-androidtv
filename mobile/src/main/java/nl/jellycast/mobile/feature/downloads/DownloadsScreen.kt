package nl.jellycast.mobile.feature.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.downloads.Download
import nl.jellycast.mobile.core.downloads.DownloadKind
import nl.jellycast.mobile.core.downloads.DownloadStatus
import nl.jellycast.mobile.core.downloads.DownloadedMedia
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.remainingText
import nl.jellycast.mobile.navigation.DownloadedSeriesRoute
import nl.jellycast.mobile.navigation.LocalAppNavigation
import nl.jellycast.mobile.navigation.MovieRoute
import nl.jellycast.mobile.navigation.PlayRequest
import nl.jellycast.mobile.ui.downloads.DownloadButton
import nl.jellycast.mobile.ui.downloads.DownloadText
import nl.jellycast.mobile.ui.downloads.rememberDownloadText
import org.koin.compose.viewmodel.koinViewModel

/**
 * What is on this device and what is on its way: the storage line, the running download and
 * the queue, then every movie and every series with downloaded episodes.
 */
@Composable
fun DownloadsScreen(
    modifier: Modifier = Modifier,
    viewModel: DownloadsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val navigation = LocalAppNavigation.current
    DownloadsContent(
        uiState = uiState,
        onPauseAll = viewModel::onPauseAll,
        onRemove = viewModel::onRemove,
        onRemoveGroup = viewModel::onRemoveGroup,
        onOpenGroup = { group ->
            when (group) {
                // Offline the movie page can't load, so the download plays straight away.
                is DownloadGroup.MovieGroup ->
                    if (uiState.isOffline) {
                        navigation.play(PlayRequest(group.item))
                    } else {
                        navigation.open(MovieRoute(group.id))
                    }

                is DownloadGroup.SeriesGroup -> navigation.open(DownloadedSeriesRoute(group.id))
            }
        },
        modifier = modifier,
    )
}

@Composable
fun DownloadsContent(
    uiState: DownloadsUiState,
    onPauseAll: () -> Unit,
    onRemove: (Download) -> Unit,
    onRemoveGroup: (DownloadGroup) -> Unit,
    onOpenGroup: (DownloadGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    val margin = LocalLayout.current.margin
    val text = rememberDownloadText()
    Box(modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "title", contentType = "title") {
                Text(
                    stringResource(R.string.downloads_title),
                    style = JellyCastText.LargeTitle,
                    color = JellyCastColors.Text,
                    modifier =
                        Modifier.statusBarsPadding().padding(
                            horizontal = margin,
                        ).padding(top = 12.dp, bottom = 8.dp),
                )
            }
            item(key = "storage", contentType = "storage") {
                StorageSummaryView(uiState.storage, text, Modifier.padding(horizontal = margin))
            }
            if (uiState.active.isNotEmpty()) {
                item(key = "activeHeader", contentType = "header") {
                    ActiveHeader(
                        uiState.canPauseAll,
                        onPauseAll,
                        Modifier.padding(horizontal = margin),
                    )
                }
                items(uiState.active, key = {
                    "active-${it.download.id}"
                }, contentType = { "active" }) {
                    SwipeToRemove(onRemove = { onRemove(it.download) }) {
                        ActiveDownloadRow(it, text, Modifier.padding(horizontal = margin))
                    }
                }
            }
            if (uiState.groups.isNotEmpty()) {
                item(key = "finishedHeader", contentType = "header") {
                    ListSectionLabel(
                        text.onThisDevice(),
                        Modifier.padding(horizontal = margin).padding(top = 20.dp, bottom = 4.dp),
                    )
                }
                items(uiState.groups, key = {
                    "group-${it.id}"
                }, contentType = { "group" }) { group ->
                    SwipeToRemove(onRemove = { onRemoveGroup(group) }) {
                        FinishedGroupRow(
                            group,
                            text,
                            Modifier.pressable(onClick = {
                                onOpenGroup(group)
                            }).padding(horizontal = margin),
                        )
                    }
                }
            }
        }
        if (uiState.isEmpty) EmptyDownloads(Modifier.align(Alignment.Center).padding(32.dp))
    }
}

@Composable
private fun ActiveHeader(
    canPauseAll: Boolean,
    onPauseAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(top = 20.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        ListSectionLabel(stringResource(R.string.downloads_in_progress), Modifier.weight(1f))
        if (canPauseAll) {
            Text(
                stringResource(R.string.downloads_pause_all),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.Accent,
                modifier = Modifier.pressable(onPauseAll),
            )
        }
    }
}

@Composable
private fun ActiveDownloadRow(row: ActiveRow, text: DownloadText, modifier: Modifier = Modifier) {
    val download = row.download
    val isMoving =
        download.status == DownloadStatus.Running ||
            download.status == DownloadStatus.WaitingForNetwork
    DownloadRowLayout(
        media = download.media,
        title = text.title(download.media),
        detail = text.sizeLine(download),
        status = text.statusLine(download, row.secondsLeft),
        statusColor = if (isMoving) JellyCastColors.Accent else JellyCastColors.TextMuted,
        modifier = modifier,
    ) {
        DownloadButton(download.id)
    }
}

@Composable
private fun FinishedGroupRow(
    group: DownloadGroup,
    text: DownloadText,
    modifier: Modifier = Modifier,
) {
    val first = group.downloads.first()
    val size = text.bytes(group.bytes)
    val (title, detail, status) =
        when (group) {
            is DownloadGroup.MovieGroup -> {
                val watch =
                    when (val watch = group.watch) {
                        MovieWatch.Watched -> stringResource(R.string.downloads_watched)
                        is MovieWatch.InProgress -> remainingText(watch.remainingMs)
                        MovieWatch.NotWatched -> stringResource(R.string.downloads_not_watched)
                    }
                Triple(
                    first.media.title,
                    DownloadText.joined(
                        stringResource(R.string.downloads_movie),
                        first.media.runtimeMs?.let { durationText(it) },
                        size,
                    ),
                    DownloadText.joined(text.kind(first.kind), watch),
                )
            }

            is DownloadGroup.SeriesGroup ->
                Triple(
                    group.name,
                    DownloadText.joined(
                        pluralStringResource(
                            R.plurals.downloads_episodes,
                            group.downloads.size,
                            group.downloads.size,
                        ),
                        episodeRange(group.downloads.map { it.media }, text),
                        size,
                    ),
                    if (group.unwatchedCount == 0) {
                        stringResource(R.string.downloads_all_watched)
                    } else {
                        pluralStringResource(
                            R.plurals.downloads_unwatched_count,
                            group.unwatchedCount,
                            group.unwatchedCount,
                        )
                    },
                )
        }
    DownloadRowLayout(
        media = first.media,
        title = title,
        detail = detail,
        status = status,
        statusColor = JellyCastColors.TextMuted,
        modifier = modifier,
    ) {
        Icon(
            painterResource(R.drawable.ic_downloads_chevron_right),
            contentDescription = null,
            tint = JellyCastColors.TextMuted,
            modifier = Modifier.width(34.dp).size(20.dp),
        )
    }
}

/** "S3 E4–E6", "S1 E9 – S2 E2", or "S3 E4" for one. */
private fun episodeRange(episodes: List<DownloadedMedia>, text: DownloadText): String? {
    val first = episodes.firstOrNull() ?: return null
    val last = episodes.last()
    val short = { media: DownloadedMedia -> text.episodeCode(media)?.replace(" · ", " ") }
    return when {
        first.id == last.id -> short(first)

        first.seasonNumber == last.seasonNumber && last.episodeNumber != null ->
            "${short(first)}–E${last.episodeNumber}"

        else -> "${short(first)} – ${short(last)}"
    }
}

@Composable
private fun EmptyDownloads(modifier: Modifier = Modifier) {
    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_download),
            contentDescription = null,
            tint = JellyCastColors.TextMuted,
            modifier = Modifier.size(44.dp),
        )
        Text(
            stringResource(R.string.downloads_empty_title),
            style = JellyCastText.Title3,
            color = JellyCastColors.Text,
        )
        Text(
            stringResource(R.string.downloads_empty_message),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview
@Composable
private fun DownloadsContentPreview() {
    val movie = DownloadedMedia(id = "m1", title = "Runner", runtimeMs = 6_600_000)
    val done = Download(movie, DownloadKind.Compact, DownloadStatus.Done, bytes = 3_480_000_000)
    JellyCastTheme {
        DownloadsContent(
            uiState =
                DownloadsUiState(
                    groups =
                        listOf(
                            DownloadGroup.MovieGroup(
                                done,
                                movie.toPlayable(),
                                MovieWatch.NotWatched,
                            ),
                        ),
                    storage = StorageSummary(1, 0, 3_880_000_000, null),
                    isEmpty = false,
                    canPauseAll = false,
                ),
            onPauseAll = {},
            onRemove = {},
            onRemoveGroup = {},
            onOpenGroup = {},
        )
    }
}
