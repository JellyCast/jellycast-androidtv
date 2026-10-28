package nl.jellycast.mobile.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Series
import nl.jellycast.core.model.progress
import nl.jellycast.core.model.remainingMs
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.CardShape
import nl.jellycast.mobile.core.designsystem.component.LANDSCAPE_RATIO
import nl.jellycast.mobile.core.designsystem.component.LandscapeImage
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.remainingText
import nl.jellycast.mobile.ui.downloads.DownloadButton

private val ChipHeight = 34.dp
private const val WATCHED_STILL_ALPHA = 0.55f
private const val OVERVIEW_LINES = 3

@Composable
internal fun seasonTitle(season: SeasonSummary): String = if (season.isSpecials) {
    stringResource(R.string.series_specials)
} else {
    stringResource(R.string.series_season, season.number)
}

/** Every season as a chip, scrolled so the chosen one starts in the middle. */
@Composable
internal fun SeasonChips(
    seasons: List<SeasonSummary>,
    selected: Int,
    margin: Dp,
    onSelect: (Int) -> Unit,
    onLongPress: ((SeasonSummary) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = seasons.indexOfFirst { it.number == selected }
        if (index <= 0) return@LaunchedEffect
        state.scrollToItem(index)
        val layout = state.layoutInfo
        val item =
            layout.visibleItemsInfo.firstOrNull { it.index == index } ?: return@LaunchedEffect
        val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
        state.scrollBy((item.offset + item.size / 2 - viewportCenter).toFloat())
    }
    LazyRow(
        modifier.fillMaxWidth(),
        state = state,
        contentPadding = PaddingValues(horizontal = margin),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(seasons, key = { it.number }) { season ->
            SeasonChip(
                season = season,
                isSelected = season.number == selected,
                onClick = { onSelect(season.number) },
                onLongClick = onLongPress?.let { { it(season) } },
            )
        }
    }
}

@Composable
private fun SeasonChip(
    season: SeasonSummary,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val color =
        when {
            isSelected -> JellyCastColors.Background
            season.isWatched -> JellyCastColors.TextMuted
            else -> JellyCastColors.Text
        }
    Row(
        modifier
            .height(ChipHeight)
            .clip(CircleShape)
            .background(if (isSelected) JellyCastColors.Text else JellyCastColors.Rail)
            .pressable(onClick, onLongClick)
            .semantics { selected = isSelected }
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelected) {
            Text(
                seasonTitle(season),
                style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.Bold),
                color = color,
            )
            Text(
                "${season.watchedCount}/${season.episodeCount}",
                style = JellyCastText.Subheadline,
                color = color.copy(alpha = 0.6f),
            )
        } else {
            Text(
                if (season.isSpecials) {
                    stringResource(R.string.series_specials)
                } else {
                    stringResource(R.string.series_season_short, season.number)
                },
                style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.Medium),
                color = color,
            )
            if (season.isWatched) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(11.dp),
                )
            }
        }
    }
}

/** One episode: still, title, status line and link, the watched check and the download button. */
@Composable
internal fun EpisodeRow(
    episode: Episode,
    series: Series,
    ratingSource: RatingSource,
    isNext: Boolean,
    isLarge: Boolean,
    isAvailable: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val stillWidth = if (isLarge) 192.dp else 136.dp
    val overview = episode.overview?.takeIf { it.isNotBlank() }
    Box(modifier.alpha(if (isAvailable) 1f else UNAVAILABLE_ALPHA)) {
        Column(
            Modifier.fillMaxWidth().pressable(onPlay, onLongPress),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = if (isLarge) Alignment.Top else Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(if (isLarge) 16.dp else 12.dp),
            ) {
                EpisodeStill(
                    episode,
                    isNext,
                    Modifier.width(stillWidth).height(stillWidth / LANDSCAPE_RATIO),
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    EpisodeTitleLines(episode, series, ratingSource)
                    if (isLarge && overview != null) EpisodeOverview(overview)
                }
                Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                    if (episode.isPlayed) {
                        Icon(
                            painterResource(R.drawable.ic_check),
                            contentDescription = stringResource(R.string.series_watched),
                            tint = JellyCastColors.TextMuted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
                DownloadButton(episode.id, compact = true)
            }
            if (!isLarge && overview != null) EpisodeOverview(overview)
        }
        if (!isAvailable) {
            // Swallows every touch, so nothing in the row reacts while it can't play.
            Box(
                Modifier.matchParentSize().pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) awaitPointerEvent().changes.forEach { it.consume() }
                    }
                },
            )
        }
    }
}

@Composable
private fun EpisodeStill(episode: Episode, isNext: Boolean, modifier: Modifier = Modifier) {
    val url = episode.stillUrl ?: episode.artwork.landscape ?: episode.artwork.backdrop
    val outline =
        if (isNext) Modifier.border(2.dp, JellyCastColors.Accent, CardShape) else Modifier
    Box(modifier.then(outline)) {
        LandscapeImage(
            url = url,
            title = episode.title,
            progress = episode.progress.takeIf { !episode.isPlayed },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .alpha(if (episode.isPlayed) WATCHED_STILL_ALPHA else 1f),
        )
    }
}

@Composable
private fun EpisodeTitleLines(
    episode: Episode,
    series: Series,
    ratingSource: RatingSource,
    modifier: Modifier = Modifier,
) {
    val number = episode.episodeNumber
    val title =
        if (number != null) {
            stringResource(R.string.series_episode_title, number, episode.title)
        } else {
            episode.title
        }
    val inProgress = !episode.isPlayed && episode.resumePositionMs > 0
    val status = statusLine(episode, inProgress)
    val url = episodePageUrl(ratingSource, episode, series)
    val rating = episodeRating(ratingSource, episode)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            title,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = if (episode.isPlayed) JellyCastColors.TextMuted else JellyCastColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (status.isNotEmpty()) {
            Text(
                status,
                style = JellyCastText.Footnote,
                color = if (inProgress) JellyCastColors.Accent else JellyCastColors.TextMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (url != null || rating != null) {
            Row(
                Modifier.padding(top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (url != null) InlineExternalLink(ratingSource.siteName, url)
                if (rating != null) {
                    Text(rating, style = JellyCastText.Footnote, color = JellyCastColors.TextMuted)
                }
            }
        }
    }
}

/** "Watched · 41 min", "18 min left" or the runtime. */
@Composable
private fun statusLine(episode: Episode, inProgress: Boolean): String {
    val runtime = episode.runtimeMs?.let { durationText(it) }
    return when {
        episode.isPlayed ->
            listOfNotNull(stringResource(R.string.series_watched), runtime).joinToString(" · ")

        inProgress ->
            remainingMs(episode.resumePositionMs, episode.runtimeMs)?.let {
                remainingText(it)
            }.orEmpty()

        else -> runtime.orEmpty()
    }
}

@Composable
private fun EpisodeOverview(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = JellyCastText.Footnote,
        color = JellyCastColors.TextMuted,
        maxLines = OVERVIEW_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}
