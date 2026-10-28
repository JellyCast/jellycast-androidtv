package nl.jellycast.mobile.feature.series

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import nl.jellycast.core.common.genreStringRes
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.RailCircleButton
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText

private const val META_GENRES = 2
private const val COLLAPSED_OVERVIEW_LINES = 3
private val ActionButtonSize = 34.dp

/** Meta line, overview, then the link, rating and action buttons in one row. */
@Composable
internal fun SeriesSummary(
    page: SeriesPage,
    onPlayOn: (() -> Unit)?,
    onToggleWatchState: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val series = page.series
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(seriesMeta(page), style = JellyCastText.Subheadline, color = JellyCastColors.TextMuted)
        series.overview?.takeIf { it.isNotBlank() }?.let { ExpandableText(it) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            seriesPageUrl(page.ratingSource, series)?.let { url ->
                ExternalLinkCapsule(page.ratingSource.siteName, url)
            }
            seriesRating(page.ratingSource, series)?.let {
                Text(it, style = JellyCastText.Footnote, color = JellyCastColors.TextMuted)
            }
            if (onPlayOn != null) {
                RailCircleButton(
                    icon = R.drawable.ic_tv,
                    contentDescription = stringResource(R.string.series_play_on),
                    onClick = onPlayOn,
                    size = ActionButtonSize,
                )
            }
            val watchedIcon =
                if (page.isWatched) R.drawable.ic_series_check_circle else R.drawable.ic_check
            val watchedLabel =
                if (page.isWatched) {
                    R.string.series_mark_not_watched
                } else {
                    R.string.series_mark_watched
                }
            RailCircleButton(
                icon = watchedIcon,
                contentDescription = stringResource(watchedLabel),
                onClick = onToggleWatchState,
                size = ActionButtonSize,
            )
        }
    }
}

/** "2019 · 3 seasons · Drama, Crime · TV-MA". */
@Composable
private fun seriesMeta(page: SeriesPage): String {
    val series = page.series
    val genres =
        series.genres.take(META_GENRES).map { name ->
            genreStringRes(name)?.let { stringResource(it) } ?: name
        }
    return listOf(
        series.year?.toString(),
        pluralStringResource(R.plurals.series_seasons, page.seasonCount, page.seasonCount),
        genres.joinToString(", "),
        series.ageRating?.label,
    ).filterNot { it.isNullOrEmpty() }.joinToString(" · ")
}

@Composable
private fun ExpandableText(text: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Text(
        text,
        style = JellyCastText.Subheadline,
        color = JellyCastColors.TextMuted,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_OVERVIEW_LINES,
        modifier = modifier.fillMaxWidth().pressable({ expanded = !expanded }),
    )
}

@Composable
private fun ExternalLinkCapsule(siteName: String, url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier
            .height(ActionButtonSize)
            .clip(CircleShape)
            .background(JellyCastColors.Surface)
            .pressable({ uriHandler.openUri(url) })
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            siteName,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
        Icon(
            painterResource(R.drawable.ic_series_arrow_outward),
            contentDescription = null,
            tint = JellyCastColors.TextMuted,
            modifier = Modifier.size(12.dp),
        )
    }
}

/** "IMDb ↗" as small text, for rows where a capsule would be too heavy. */
@Composable
internal fun InlineExternalLink(siteName: String, url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier.pressable({ uriHandler.openUri(url) }),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            siteName,
            style = JellyCastText.Footnote.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
        Icon(
            painterResource(R.drawable.ic_series_arrow_outward),
            contentDescription = null,
            tint = JellyCastColors.TextMuted,
            modifier = Modifier.size(10.dp),
        )
    }
}
