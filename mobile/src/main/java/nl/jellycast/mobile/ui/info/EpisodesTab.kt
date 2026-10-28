package nl.jellycast.mobile.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.progress
import nl.jellycast.core.model.remainingMs
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ArtworkImage
import nl.jellycast.mobile.core.designsystem.component.CardShape
import nl.jellycast.mobile.core.designsystem.component.WatchProgressBar
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.library.SeriesProgress
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.remainingText
import nl.jellycast.mobile.ui.downloads.DownloadButton

/** How episode rows look and react; shared by the player window and the series page. */
internal data class EpisodeRowStyle(
    val series: Series?,
    val ratingSource: RatingSource,
    val isLarge: Boolean,
    val playingId: String? = null,
    val nextId: String? = null,
    val playingOn: String? = null,
    /** `false` dims the row to 35% and ignores taps (offline, not downloaded). */
    val isAvailable: (Episode) -> Boolean = { true },
)

@Composable
internal fun EpisodeInfoList(
    episodes: List<Episode>,
    style: EpisodeRowStyle,
    onPlay: (Episode) -> Unit,
    onLongPress: (Episode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        episodes.forEach { episode ->
            val available = style.isAvailable(episode)
            EpisodeInfoRow(
                episode = episode,
                style = style,
                onPlay = { if (available && episode.id != style.playingId) onPlay(episode) },
                onLongPress = { onLongPress(episode) },
                modifier = Modifier.alpha(if (available) 1f else OFFLINE_ALPHA),
            )
        }
    }
}

@Composable
private fun EpisodeInfoRow(
    episode: Episode,
    style: EpisodeRowStyle,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPlaying = episode.id == style.playingId
    val large = style.isLarge
    Column(
        modifier.fillMaxWidth().pressable(onPlay, onLongClick = onLongPress),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            verticalAlignment = if (large) Alignment.Top else Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (large) 16.dp else 12.dp),
        ) {
            EpisodeStill(
                episode,
                width = if (large) 192.dp else 136.dp,
                isPlaying = isPlaying,
                isOutlined = isPlaying || episode.id == style.nextId,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                EpisodeTitleLines(episode, style, isPlaying)
                if (large) EpisodeOverview(episode, isPlaying, Modifier.padding(top = 3.dp))
            }
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (episode.isPlayed && !isPlaying) {
                    Icon(
                        painterResource(R.drawable.ic_check),
                        null,
                        Modifier.size(15.dp),
                        tint = JellyCastColors.TextMuted,
                    )
                }
            }
            DownloadButton(episode.id, compact = true)
        }
        if (!large) EpisodeOverview(episode, isPlaying)
    }
}

@Composable
private fun EpisodeStill(episode: Episode, width: Dp, isPlaying: Boolean, isOutlined: Boolean) {
    val watched = episode.isPlayed && !isPlaying
    Box(
        Modifier
            .width(width)
            .aspectRatio(STILL_RATIO)
            .clip(CardShape)
            .then(
                if (isOutlined) Modifier.border(2.dp, JellyCastColors.Accent, CardShape) else Modifier,
            ),
    ) {
        ArtworkImage(
            url = episode.stillUrl ?: episode.artwork.backdrop,
            title = episode.title,
            modifier = Modifier.fillMaxSize().alpha(if (watched) WATCHED_IMAGE_ALPHA else 1f),
        )
        episode.progress?.let {
            WatchProgressBar(
                it,
                Modifier.align(Alignment.BottomCenter).padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
                onImage = true,
            )
        }
    }
}

@Composable
private fun EpisodeTitleLines(episode: Episode, style: EpisodeRowStyle, isPlaying: Boolean) {
    val watched = episode.isPlayed && !isPlaying
    Text(
        episode.episodeNumber?.let { "$it. ${episode.title}" } ?: episode.title,
        style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
        color = if (watched) JellyCastColors.TextMuted else JellyCastColors.Text,
        maxLines = 2,
    )
    val status = statusLine(episode, style, isPlaying)
    if (status.isNotEmpty()) {
        val accent = isPlaying || (!episode.isPlayed && episode.resumePositionMs > 0)
        Text(
            status,
            style =
                JellyCastText.Footnote.copy(
                    fontWeight = if (isPlaying) FontWeight.SemiBold else FontWeight.Normal,
                ),
            color = if (accent) JellyCastColors.Accent else JellyCastColors.TextMuted,
            maxLines = 1,
        )
    }
    val title = ExternalTitle.of(episode, style.series)
    val url = style.ratingSource.url(title)
    val rating = style.ratingSource.rating(title, currentLocale())
    if (url != null || rating != null) {
        val uriHandler = LocalUriHandler.current
        Row(
            Modifier.padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (url != null) InlineExternalLink(style.ratingSource, { uriHandler.openUri(url) })
            if (rating != null) {
                Text(rating, style = JellyCastText.Footnote, color = JellyCastColors.TextMuted)
            }
        }
    }
}

@Composable
private fun statusLine(episode: Episode, style: EpisodeRowStyle, isPlaying: Boolean): String {
    val runtime = episode.runtimeMs?.let { durationText(it) }
    val remaining = remainingMs(episode.resumePositionMs, episode.runtimeMs)?.let { remainingText(it) }
    return when {
        isPlaying && style.playingOn != null -> stringResource(R.string.info_playing_on, style.playingOn)
        isPlaying -> joined(listOf(stringResource(R.string.info_playing_now), remaining))
        episode.isPlayed -> joined(listOf(stringResource(R.string.info_watched), runtime))
        episode.resumePositionMs > 0 -> remaining.orEmpty()
        else -> runtime.orEmpty()
    }
}

@Composable
private fun EpisodeOverview(episode: Episode, isPlaying: Boolean, modifier: Modifier = Modifier) {
    val overview = episode.overview?.takeIf { it.isNotBlank() } ?: return
    Text(
        overview,
        style = JellyCastText.Footnote,
        color = if (isPlaying) JellyCastColors.Text else JellyCastColors.TextMuted,
        maxLines = OVERVIEW_LINES,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

/** Every season as chips, with the chosen season's episodes under them. */
@Composable
internal fun SeriesEpisodesTab(
    progress: SeriesProgress,
    style: EpisodeRowStyle,
    margin: Dp,
    onPlay: (Episode) -> Unit,
    onLongPress: (Episode) -> Unit,
    onSeasonLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val defaultSeason = progress.next?.seasonNumber ?: progress.seasons.firstOrNull() ?: 1
    var selected by rememberSaveable(progress.series.id) { mutableStateOf<Int?>(null) }
    val season = selected ?: defaultSeason
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SeasonChips(
            progress = progress,
            selection = season,
            margin = margin,
            onSelect = { selected = it },
            onLongPress = onSeasonLongPress,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        EpisodeInfoList(
            episodes = progress.episodes.filter { (it.seasonNumber ?: 0) == season },
            style = style,
            onPlay = onPlay,
            onLongPress = onLongPress,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun SeasonChips(
    progress: SeriesProgress,
    selection: Int,
    margin: Dp,
    onSelect: (Int) -> Unit,
    onLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val seasons = progress.seasons
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = (seasons.indexOf(selection) - 1).coerceAtLeast(0),
        )
    // The chips run edge to edge: the parent's margin is undone here and given back as padding.
    LazyRow(
        modifier = modifier.layoutWiderBy(margin),
        state = listState,
        contentPadding = PaddingValues(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { it }) { season ->
            val episodes = progress.episodes.filter { (it.seasonNumber ?: 0) == season }
            SeasonChip(
                season = season,
                watched = episodes.count { it.isPlayed },
                total = episodes.size,
                isSelected = season == selection,
                onClick = { onSelect(season) },
                onLongClick = { onLongPress(season) },
            )
        }
    }
}

@Composable
private fun SeasonChip(
    season: Int,
    watched: Int,
    total: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val isWatched = total > 0 && watched == total
    val color =
        when {
            isSelected -> JellyCastColors.Background
            isWatched -> JellyCastColors.TextMuted
            else -> JellyCastColors.Text
        }
    Row(
        Modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(if (isSelected) JellyCastColors.Text else JellyCastColors.Rail)
            .pressable(onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val style = JellyCastText.Subheadline
        if (isSelected) {
            Text(seasonTitle(season), style = style.copy(fontWeight = FontWeight.Bold), color = color)
            Text("$watched/$total", style = style, color = color.copy(alpha = 0.6f))
        } else {
            val label =
                if (season == 0) {
                    stringResource(R.string.info_specials)
                } else {
                    stringResource(R.string.info_season_short, season)
                }
            Text(label, style = style.copy(fontWeight = FontWeight.Medium), color = color)
            if (isWatched) {
                Icon(painterResource(R.drawable.ic_check), null, Modifier.size(12.dp), tint = color)
            }
        }
    }
}

@Composable
internal fun seasonTitle(season: Int): String = if (season == 0) {
    stringResource(R.string.info_specials)
} else {
    stringResource(R.string.info_season, season)
}

private fun Modifier.layoutWiderBy(extra: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val extraPx = extra.roundToPx()
    val width = constraints.maxWidth + extraPx * 2
    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extraPx, 0) }
}

private const val STILL_RATIO = 16f / 9f
private const val WATCHED_IMAGE_ALPHA = 0.55f
private const val OFFLINE_ALPHA = 0.35f
private const val OVERVIEW_LINES = 3
