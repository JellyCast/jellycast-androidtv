package nl.jellycast.mobile.ui.info

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import nl.jellycast.core.model.Episode
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.episodeCode
import nl.jellycast.mobile.ui.downloads.DownloadButton

/** Title, meta line, tech badges, overview, then the rating link, rating and actions. */
@Composable
internal fun InfoSummary(
    state: ItemInfoUiState,
    showsTitle: Boolean,
    actions: @Composable RowScope.() -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = currentLocale()
    val title = summaryTitle(state)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (showsTitle) {
                Text(
                    titleText(state),
                    style = JellyCastText.Title2.copy(fontWeight = FontWeight.Black),
                    color = JellyCastColors.Text,
                )
            }
            Text(
                metaLine(state, locale),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.TextMuted,
            )
            if (state.subject !is InfoSubject.SeriesInfo) {
                val badges = state.media?.let(::techBadges).orEmpty()
                if (badges.isNotEmpty()) TechBadges(badges, Modifier.padding(top = 2.dp))
            }
        }
        overviewText(state)?.let { ExpandableText(it) }
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val url = title?.let { state.ratingSource.url(it) }
            if (url != null) ExternalLinkCapsule(state.ratingSource, url)
            title?.let { state.ratingSource.rating(it, locale) }?.let {
                Text(it, style = JellyCastText.Footnote, color = JellyCastColors.TextMuted)
            }
            actions()
            downloadItemId(state)?.let { DownloadButton(it) }
        }
    }
}

/** For an episode the series: the episode's own link and rating are in the episode list. */
private fun summaryTitle(state: ItemInfoUiState): ExternalTitle? {
    state.movie?.let { return ExternalTitle.of(it) }
    return state.series?.let { ExternalTitle.of(it) }
}

private fun titleText(state: ItemInfoUiState): String =
    state.movie?.title ?: state.episode?.seriesTitle ?: state.series?.title.orEmpty()

private fun overviewText(state: ItemInfoUiState): String? =
    (state.movie?.overview ?: state.series?.overview)?.takeIf { it.isNotBlank() }

private fun downloadItemId(state: ItemInfoUiState): String? = when (state.subject) {
    is InfoSubject.MovieInfo -> state.movie?.id
    is InfoSubject.EpisodeInfo -> state.episode?.id
    is InfoSubject.SeriesInfo -> null
}

@Composable
private fun metaLine(state: ItemInfoUiState, locale: Locale): String {
    val movie = state.movie
    val series = state.series
    val parts =
        when {
            movie != null ->
                listOf(
                    movie.year?.toString(),
                    movie.runtimeMs?.let { durationText(it) },
                    state.genres,
                    movie.ageRating?.label,
                )

            state.subject is InfoSubject.SeriesInfo -> {
                val seasons = state.progress?.seasons.orEmpty().count { it > 0 }
                listOf(
                    series?.year?.toString(),
                    pluralStringResource(R.plurals.info_seasons, seasons, seasons),
                    state.genres,
                    series?.ageRating?.label,
                )
            }

            else -> {
                val episode = state.episode
                listOf(
                    episode?.let { episodeCode(it) },
                    episode?.runtimeMs?.let { durationText(it) },
                    state.genres,
                    series?.ageRating?.label,
                    episode?.let { episodeRating(it, state, locale) },
                )
            }
        }
    return joined(parts)
}

private fun episodeRating(episode: Episode, state: ItemInfoUiState, locale: Locale): String? =
    state.ratingSource.rating(ExternalTitle.of(episode, state.series), locale)

internal fun joined(parts: List<String?>): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

@Composable
internal fun currentLocale(): Locale = LocalConfiguration.current.locales.get(0)

@Composable
private fun TechBadges(parts: List<String>, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        parts.forEach { part ->
            Box(
                Modifier
                    .height(24.dp)
                    .border(1.dp, JellyCastColors.Rail, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    part.uppercase(),
                    style =
                        JellyCastText.Caption.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.6.sp,
                        ),
                    color = JellyCastColors.Text,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExpandableText(text: String, modifier: Modifier = Modifier) {
    var expanded by rememberSaveable(text) { mutableStateOf(false) }
    Text(
        text,
        style = JellyCastText.Subheadline,
        color = JellyCastColors.TextMuted,
        maxLines = if (expanded) Int.MAX_VALUE else COLLAPSED_LINES,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.fillMaxWidth().pressable({ expanded = !expanded }),
    )
}

/** The site's capsule; Android opens the site's app when it handles the link. */
@Composable
private fun ExternalLinkCapsule(source: RatingSource, url: String, modifier: Modifier = Modifier) {
    val uriHandler = LocalUriHandler.current
    Row(
        modifier
            .height(34.dp)
            .clip(CircleShape)
            .background(JellyCastColors.Surface)
            .pressable({ uriHandler.openUri(url) })
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            source.siteName,
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
        Icon(
            painterResource(R.drawable.ic_info_north_east),
            null,
            Modifier.size(12.dp),
            tint = JellyCastColors.TextMuted,
        )
    }
}

/** "IMDb ↗" as small text, for rows where a capsule would be too heavy. */
@Composable
internal fun InlineExternalLink(
    source: RatingSource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.pressable(onClick),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            source.siteName,
            style = JellyCastText.Footnote.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
        Icon(
            painterResource(R.drawable.ic_info_north_east),
            null,
            Modifier.size(10.dp),
            tint = JellyCastColors.TextMuted,
        )
    }
}

private const val COLLAPSED_LINES = 3
