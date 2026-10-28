package nl.jellycast.mobile.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ImageBadge
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.library.NewEntry
import nl.jellycast.mobile.core.library.NewItem

private const val POSTER_HEIGHT_PER_WIDTH = 1.5f
private val PosterShape = RoundedCornerShape(10.dp)

/** What continues after the hero; a tap plays it at once. */
@Composable
internal fun UpNextCard(
    entry: UpNextEntry,
    unavailable: Boolean,
    onPlay: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = entry.item
    Column(
        modifier
            .width(LocalLayout.current.upNextCardWidth)
            .unavailableWhen(unavailable)
            .pressable(onPlay, onLongPress, enabled = !unavailable),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            ContinueImage(item, Modifier.fillMaxWidth())
            if (entry.isNew) {
                ImageBadge(stringResource(R.string.home_new), Modifier.padding(8.dp))
            }
        }
        Text(
            item.displayTitle,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = JellyCastColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            upNextDetail(item),
            style = JellyCastText.Footnote,
            color = JellyCastColors.TextMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun upNextDetail(item: PlayableItem): String = when (item) {
    is PlayableItem.MovieItem -> joined(item.movie.year?.toString(), timeLabel(item))
    is PlayableItem.EpisodeItem -> joined(seasonEpisodeText(item.episode), timeLabel(item))
}

@Composable
internal fun NewEntryCard(
    entry: NewEntry,
    unavailable: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (val item = entry.item) {
        is NewItem.NewMovie ->
            PosterCard(
                posterUrl = item.movie.artwork.poster,
                caption = joined(stringResource(R.string.home_movie), item.movie.year?.toString()),
                unavailable = unavailable,
                onClick = onOpen,
                onLongPress = onLongPress,
                modifier = modifier,
            )

        is NewItem.NewSeries ->
            PosterCard(
                posterUrl = item.series.artwork.poster,
                caption =
                    if (item.isNewSeason) {
                        stringResource(R.string.home_series_new_season)
                    } else {
                        pluralStringResource(
                            R.plurals.home_series_new_count,
                            item.count,
                            item.count,
                        )
                    },
                unavailable = unavailable,
                onClick = onOpen,
                onLongPress = onLongPress,
                modifier = modifier,
            )
    }
}

@Composable
internal fun NotStartedCard(
    entry: NotStartedEntry,
    unavailable: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PosterCard(
        posterUrl = entry.series.artwork.poster,
        caption = pluralStringResource(
            R.plurals.home_seasons,
            entry.seasonCount,
            entry.seasonCount,
        ),
        unavailable = unavailable,
        onClick = onOpen,
        onLongPress = onLongPress,
        modifier = modifier,
    )
}

/** A 2:3 poster with a caption under it. */
@Composable
private fun PosterCard(
    posterUrl: String?,
    caption: String,
    unavailable: Boolean,
    onClick: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = LocalLayout.current.posterWidth
    Column(
        modifier
            .width(width)
            .unavailableWhen(unavailable)
            .pressable(onClick, onLongPress, enabled = !unavailable),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(width * POSTER_HEIGHT_PER_WIDTH)
                .clip(PosterShape)
                .background(JellyCastColors.Surface),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = posterUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            caption,
            style = JellyCastText.Footnote,
            color = JellyCastColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
