package nl.jellycast.mobile.feature.series

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.DetailHeader
import nl.jellycast.mobile.core.designsystem.component.LandscapeImage
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.episodeCode
import nl.jellycast.mobile.feature.home.ErrorMessage
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun SeriesDetailScreen(
    seriesId: String,
    onPlay: (itemId: String, startPositionMs: Long) -> Unit,
    onBack: () -> Unit,
    viewModel: SeriesDetailViewModel = koinViewModel(key = seriesId) { parametersOf(seriesId) },
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val firstResume = remember { booleanArrayOf(true) }
    LifecycleResumeEffect(Unit) {
        if (!firstResume[0]) viewModel.onShown()
        firstResume[0] = false
        onPauseOrDispose { }
    }
    SeriesDetailContent(
        uiState = uiState,
        onPlay = onPlay,
        onSeasonSelect = viewModel::onSeasonSelect,
        onRetry = viewModel::onRetry,
        onBack = onBack,
    )
}

@Composable
fun SeriesDetailContent(
    uiState: SeriesDetailUiState,
    onPlay: (String, Long) -> Unit,
    onSeasonSelect: (Int) -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val series = uiState.series
    when {
        series != null -> SeriesBody(series, uiState, onPlay, onSeasonSelect, onBack, modifier)

        uiState.failed -> ErrorMessage(onRetry, modifier)

        else ->
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
    }
}

@Composable
private fun SeriesBody(
    series: Series,
    uiState: SeriesDetailUiState,
    onPlay: (String, Long) -> Unit,
    onSeasonSelect: (Int) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "header") {
            DetailHeader(
                backdropUrl = series.artwork.backdrop,
                logoUrl = series.artwork.logo,
                title = series.title,
                onBack = onBack,
            )
        }
        item(key = "info") {
            SeriesInfo(series, uiState, onPlay)
        }
        if (uiState.seasons.size > 1) {
            item(key = "seasons") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp),
                ) {
                    items(uiState.seasons, key = { it }) { season ->
                        FilterChip(
                            selected = season == uiState.shownSeason,
                            onClick = { onSeasonSelect(season) },
                            label = { Text(seasonLabel(season)) },
                        )
                    }
                }
            }
        }
        items(uiState.seasonEpisodes, key = { it.id }) { episode ->
            EpisodeRow(episode, onClick = { onPlay(episode.id, episode.startPositionMs) })
        }
        item(key = "bottom") { Box(Modifier.navigationBarsPadding()) }
    }
}

@Composable
private fun SeriesInfo(
    series: Series,
    uiState: SeriesDetailUiState,
    onPlay: (String, Long) -> Unit,
) {
    Column(
        Modifier.widthIn(max = 720.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            listOfNotNull(
                series.year?.toString(),
                series.ageRating?.label,
                series.genres.take(3).takeIf { it.isNotEmpty() }?.joinToString(", "),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val next = uiState.continueWith ?: uiState.firstEpisode
        if (next != null) {
            Button(onClick = { onPlay(next.id, next.startPositionMs) }, Modifier.fillMaxWidth()) {
                Icon(
                    painterResource(R.drawable.ic_play),
                    contentDescription = null,
                    Modifier.size(18.dp),
                )
                val label =
                    if (uiState.continueWith != null) {
                        stringResource(R.string.series_continue, episodeCode(next))
                    } else {
                        stringResource(R.string.series_play_first, episodeCode(next))
                    }
                Text(label, Modifier.padding(start = 8.dp))
            }
        }
        series.overview?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun EpisodeRow(episode: Episode, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LandscapeImage(
            url = episode.stillUrl ?: episode.artwork.landscape,
            title = episode.title,
            progress = episode.progress?.takeIf { !episode.isPlayed },
            dimmed = episode.isPlayed,
            modifier = Modifier.width(EPISODE_IMAGE_WIDTH),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                listOf(episode.episodeNumber?.toString(), episode.title)
                    .filterNot { it.isNullOrEmpty() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    episode.runtimeMs?.let { durationText(it) },
                    if (episode.isPlayed) stringResource(R.string.episode_watched) else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            episode.overview?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun seasonLabel(season: Int): String = if (season ==
    0
) {
    stringResource(R.string.season_specials)
} else {
    stringResource(R.string.season_number, season)
}

private val Episode.startPositionMs: Long
    get() = if (isPlayed) 0 else resumePositionMs

private val EPISODE_IMAGE_WIDTH = 150.dp
