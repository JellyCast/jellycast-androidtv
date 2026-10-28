package nl.jellycast.mobile.feature.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.progress
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.LandscapeImage
import nl.jellycast.mobile.core.designsystem.component.PosterImage
import nl.jellycast.mobile.core.ui.episodeCode

@Composable
internal fun HomeRows(uiState: HomeUiState, actions: HomeActions, padding: PaddingValues) {
    val continueWatching = uiState.continueWatching
    val nextEpisodes = uiState.nextEpisodes
    val newMovies = uiState.newMovies.valueOrNull.orEmpty()
    val stillLoading =
        Loadable.Loading in listOf(uiState.resumeMovies, uiState.nextUp, uiState.newMovies)
    val empty = continueWatching.isEmpty() && nextEpisodes.isEmpty() && newMovies.isEmpty()
    if (empty && !stillLoading) {
        Text(
            stringResource(R.string.home_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(padding).padding(24.dp),
        )
        return
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = padding.calculateTopPadding() + 8.dp,
            bottom =
                padding.calculateBottomPadding() + 24.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                Row(R.string.row_continue_watching) {
                    items(continueWatching, key = { it.id }) { item ->
                        ContinueCard(item, onClick = { actions.onPlay(item.id, item.resumeMs) })
                    }
                }
            }
        }
        if (nextEpisodes.isNotEmpty()) {
            item(key = "nextUp") {
                Row(R.string.row_next_up) {
                    items(nextEpisodes, key = { it.id }) { episode ->
                        EpisodeCard(episode, onClick = { actions.onOpenSeries(episode.seriesId) })
                    }
                }
            }
        }
        if (newMovies.isNotEmpty()) {
            item(key = "new") {
                Row(R.string.row_new_movies) {
                    items(newMovies, key = { it.id }) { movie ->
                        PosterImage(
                            url = movie.artwork.poster,
                            title = movie.title,
                            modifier = Modifier.width(POSTER_WIDTH).clickable {
                                actions.onOpenMovie(movie.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Row(
    titleRes: Int,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = ContentPaddingHorizontal),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = ContentPaddingHorizontal),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
private fun ContinueCard(item: PlayableItem, onClick: () -> Unit) {
    when (item) {
        is PlayableItem.MovieItem -> LandscapeCard(
            url = item.movie.landscapeUrl,
            title = item.movie.title,
            subtitle = item.movie.year?.toString().orEmpty(),
            progress = item.movie.progress,
            onClick = onClick,
        )

        is PlayableItem.EpisodeItem -> EpisodeCard(item.episode, onClick)
    }
}

@Composable
private fun EpisodeCard(episode: Episode, onClick: () -> Unit) {
    LandscapeCard(
        url = episode.stillUrl ?: episode.artwork.landscape ?: episode.artwork.backdrop,
        title = episode.seriesTitle,
        subtitle = listOf(episodeCode(episode), episode.title).filter {
            it.isNotEmpty()
        }.joinToString(" · "),
        progress = episode.progress,
        onClick = onClick,
    )
}

@Composable
private fun LandscapeCard(
    url: String?,
    title: String,
    subtitle: String,
    progress: Float?,
    onClick: () -> Unit,
) {
    Column(Modifier.width(LANDSCAPE_WIDTH).clickable(onClick = onClick)) {
        LandscapeImage(url = url, title = title, progress = progress)
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val Movie.landscapeUrl: String?
    get() = artwork.landscape ?: artwork.backdrop

private val PlayableItem.resumeMs: Long
    get() = when (this) {
        is PlayableItem.MovieItem -> movie.resumePositionMs
        is PlayableItem.EpisodeItem -> episode.resumePositionMs
    }

private val POSTER_WIDTH = 120.dp
private val LANDSCAPE_WIDTH = 240.dp
