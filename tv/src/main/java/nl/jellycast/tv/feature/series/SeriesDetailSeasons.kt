package nl.jellycast.tv.feature.series

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayRequest
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.progress
import nl.jellycast.core.model.remainingMs
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.CornerBackdrop
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.FocusCard
import nl.jellycast.tv.core.designsystem.component.PillButton
import nl.jellycast.tv.core.designsystem.component.ProvideBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.TvRow
import nl.jellycast.tv.core.designsystem.component.progressBar
import nl.jellycast.tv.core.designsystem.component.rememberCachedLazyListState
import nl.jellycast.tv.core.designsystem.component.rememberColumnBringIntoViewSpec
import nl.jellycast.tv.core.designsystem.component.rememberDelayedVisible
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.ui.episodeCode
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.runtimeText
import nl.jellycast.tv.core.ui.seasonCountText
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

// Season header (20 dp) + gap (12 dp) + list top padding (24 dp).
private val SeasonCardTop = 56.dp

@Composable
internal fun Seasons(
    seasons: List<Season>,
    columnState: LazyListState,
    pending: EpisodeTarget?,
    onPendingFocus: () -> Unit,
    onFocusEpisode: (Season, Episode) -> Unit,
    onEpisodeClick: (Episode) -> Unit,
    onFocusSeasonCard: () -> Unit,
    onSeasonWatchedToggle: (seasonKey: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    ProvideBringIntoViewSpec(rememberColumnBringIntoViewSpec(cardTop = SeasonCardTop)) {
        LazyColumn(
            state = columnState,
            contentPadding = PaddingValues(top = 24.dp, bottom = Dimens.SafeVertical),
            verticalArrangement = Arrangement.spacedBy(Dimens.RowGap),
            modifier = modifier.fillMaxWidth(),
        ) {
            items(seasons, key = { it.key }, contentType = { "season" }) { season ->
                TvRow(
                    items = season.rowItems,
                    key = { it.key },
                    pendingKey = pending?.takeIf { it.rowKey == season.key }?.episodeId,
                    onPendingFocus = onPendingFocus,
                    header = { SeasonHeader(season) },
                    contentType = { it::class },
                ) { item, itemModifier ->
                    when (item) {
                        is SeasonRowItem.EpisodeItem ->
                            EpisodeCard(
                                episode = item.episode,
                                onClick = { onEpisodeClick(item.episode) },
                                onFocus = { onFocusEpisode(season, item.episode) },
                                modifier = itemModifier,
                            )

                        is SeasonRowItem.MarkSeason ->
                            MarkSeasonCard(
                                isWatched = season.isWatched,
                                onClick = { onSeasonWatchedToggle(item.seasonKey) },
                                onFocus = onFocusSeasonCard,
                                modifier = itemModifier,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun SeasonHeader(season: Season, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(start = Dimens.SafeHorizontal),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Text(
            text =
                if (season.number == 0) {
                    stringResource(R.string.series_specials)
                } else {
                    stringResource(R.string.series_season, season.number)
                },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
        )
        Text(
            text =
                if (season.watchedCount > 0) {
                    stringResource(
                        R.string.series_season_watched,
                        season.watchedCount,
                        season.episodes.size,
                    )
                } else {
                    pluralStringResource(
                        R.plurals.series_episode_count,
                        season.episodes.size,
                        season.episodes.size,
                    )
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 1.dp),
        )
    }
}

// A color filter instead of alpha: alpha < 1 would need an offscreen buffer per card.
private val WatchedEpisodeDim = ColorFilter.tint(Color(0x8C0E0E0F), BlendMode.SrcAtop)

@Composable
private fun EpisodeCard(
    episode: Episode,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.width(Dimens.LandscapeWidth)) {
        FocusCard(
            onClick = onClick,
            onFocus = onFocus,
            containerColor = Color.Transparent,
            modifier = Modifier.size(Dimens.LandscapeWidth, Dimens.LandscapeHeight),
        ) {
            ArtworkImage(
                url = episode.stillUrl,
                fallbackTitle = episode.title,
                colorFilter = if (episode.isPlayed) WatchedEpisodeDim else null,
                modifier = Modifier.fillMaxSize(),
            )
            if (episode.isPlayed) {
                WatchedBadge()
            } else {
                episode.progress?.let { EpisodeProgress(it) }
            }
        }
        Text(
            text =
                listOfNotNull(episode.episodeNumber?.toString(), episode.title)
                    .joinToString("  ·  "),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun BoxScope.WatchedBadge() {
    Box(
        Modifier
            .align(Alignment.TopEnd)
            .padding(8.dp)
            .size(20.dp)
            .background(JellyCastColors.Text.copy(alpha = 0.92f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_check),
            contentDescription = null,
            tint = JellyCastColors.Background,
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun BoxScope.EpisodeProgress(progress: Float) {
    Box(
        Modifier
            .align(Alignment.BottomCenter)
            .padding(start = 10.dp, end = 10.dp, bottom = 8.dp)
            .fillMaxWidth()
            .height(3.dp)
            .progressBar(progress, track = JellyCastColors.Background.copy(alpha = 0.7f)),
    )
}

@Composable
private fun MarkSeasonCard(
    isWatched: Boolean,
    onClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FocusCard(
        onClick = onClick,
        onFocus = onFocus,
        containerColor = JellyCastColors.Rail,
        modifier = modifier.size(Dimens.LandscapeWidth, Dimens.LandscapeHeight),
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (isWatched) {
                Icon(
                    painter = painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text =
                    stringResource(
                        if (isWatched) {
                            R.string.series_season_all_watched
                        } else {
                            R.string.series_mark_season
                        },
                    ),
                style = MaterialTheme.typography.labelLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
internal fun SeasonsSkeleton(modifier: Modifier = Modifier) {
    val clock = rememberSkeletonClock()
    val color = MaterialTheme.colorScheme.surface
    Column(modifier.padding(start = Dimens.SafeHorizontal, top = 24.dp)) {
        repeat(2) { row ->
            if (row > 0) Spacer(Modifier.height(Dimens.RowGap))
            Box(Modifier.size(120.dp, 20.dp).skeletonBlock(clock, 0, color, 4.dp))
            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(Dimens.CardGap),
            ) {
                repeat(5) { index ->
                    Column {
                        Box(
                            Modifier
                                .size(Dimens.LandscapeWidth, Dimens.LandscapeHeight)
                                .skeletonBlock(clock, index, color),
                        )
                        Box(
                            Modifier
                                .padding(top = 12.dp)
                                .size(110.dp, 12.dp)
                                .skeletonBlock(clock, index, color, 4.dp),
                        )
                    }
                }
            }
        }
    }
}
