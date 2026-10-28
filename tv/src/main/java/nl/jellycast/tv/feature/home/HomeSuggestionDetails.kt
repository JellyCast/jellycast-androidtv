package nl.jellycast.tv.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.remainingMs
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.ArtworkImage
import nl.jellycast.tv.core.designsystem.component.ErrorState
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.ui.dynamicRangeLabel
import nl.jellycast.tv.core.ui.episodeCode
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.remainingText
import nl.jellycast.tv.core.ui.resolutionLabel
import nl.jellycast.tv.core.ui.runtimeText
import nl.jellycast.tv.core.ui.seasonCountText

private val ReasonAreaHeight = 28.dp
private val ReasonLabelHeight = 18.dp
private val LogoHeight = 95.dp
private val MetaHeight = 24.dp
private val OverviewHeight = 63.dp
private val DetailsGap = 10.dp

/** Fixed height, so the Play button does not move while loading. */
internal val SuggestionHeight =
    ReasonAreaHeight + LogoHeight + DetailsGap + MetaHeight + DetailsGap + OverviewHeight

@Composable
internal fun SuggestionDetails(suggestion: Suggestion, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(464.dp).height(SuggestionHeight),
        verticalArrangement = Arrangement.spacedBy(DetailsGap),
    ) {
        ReasonLabel(suggestion.reason, Modifier.height(ReasonLabelHeight))
        Box(
            Modifier.size(width = 280.dp, height = LogoHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (suggestion.logoUrl() != null) {
                ArtworkImage(
                    url = suggestion.logoUrl(),
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    showPlaceholder = false,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = suggestion.title(),
                    style = MaterialTheme.typography.displaySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (suggestion) {
                is Suggestion.MovieSuggestion -> MovieMeta(suggestion.movie)
                is Suggestion.SeriesSuggestion -> SeriesMeta(suggestion)
            }
        }
        Text(
            text = suggestion.overview().orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(400.dp).height(OverviewHeight),
        )
    }
}

@Composable
private fun ReasonLabel(reason: SuggestionReason, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(JellyCastColors.Accent, CircleShape))
        Text(
            text = stringResource(reason.labelRes()).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.1.em),
            color = JellyCastColors.Accent,
            maxLines = 1,
        )
    }
}

private fun SuggestionReason.labelRes(): Int = when (this) {
    SuggestionReason.ContinueWatching -> R.string.home_reason_continue
    SuggestionReason.NewEpisode -> R.string.home_reason_new_episode
    SuggestionReason.NextEpisode -> R.string.home_reason_next_episode
    SuggestionReason.RecentlyAdded -> R.string.home_reason_recently_added
    SuggestionReason.ForTonight -> R.string.home_reason_for_tonight
}

@Composable
private fun RowScope.MovieMeta(movie: Movie) {
    movie.year?.let { Text(text = it.toString(), style = MaterialTheme.typography.bodyMedium) }
    val parts =
        buildList {
            movie.runtimeMs?.let { add(runtimeText(it)) }
            movie.genres.take(2).takeIf { it.isNotEmpty() }?.let { genres ->
                add(genres.map { genreName(it) }.joinToString(", "))
            }
            movie.ageRating?.let { add(it.label) }
        }
    MutedText((if (movie.year != null) "·  " else "") + parts.joinToString("  ·  "))
}

@Composable
private fun RowScope.SeriesMeta(suggestion: Suggestion.SeriesSuggestion) {
    val episode = suggestion.episode
    Text(
        text = episodeCode(episode.seasonNumber, episode.episodeNumber),
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
    )
    Text(text = episode.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
    val parts =
        buildList {
            val runtimeMs = episode.runtimeMs
            val remaining = remainingMs(episode.resumePositionMs, runtimeMs)
            when {
                remaining != null -> add(remainingText(remaining))
                runtimeMs != null -> add(runtimeText(runtimeMs))
            }
            suggestion.series.seasonCount?.takeIf { it > 0 }?.let { add(seasonCountText(it)) }
        }
    if (parts.isNotEmpty()) MutedText("·  " + parts.joinToString("  ·  "))
}

@Composable
private fun MutedText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun TechLine(suggestion: Suggestion, modifier: Modifier = Modifier) {
    val tech = (suggestion as? Suggestion.MovieSuggestion)?.movie?.tech ?: return
    val parts =
        listOfNotNull(
            tech.resolution?.let { resolutionLabel(it) },
            tech.dynamicRange?.let { dynamicRangeLabel(it) },
            if (tech.hasAtmos) stringResource(R.string.tech_atmos) else null,
        )
    if (parts.isEmpty()) return
    Text(
        text = parts.joinToString("  ·  ").uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.06.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun SuggestionMessage(text: String, modifier: Modifier = Modifier) {
    Box(modifier.height(SuggestionHeight).padding(top = ReasonAreaHeight)) {
        MutedText(text)
    }
}

@Composable
internal fun SuggestionError(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.height(SuggestionHeight).padding(top = ReasonAreaHeight)) {
        ErrorState(message = stringResource(R.string.library_error), onRetry = onRetry)
    }
}

@Composable
internal fun SuggestionSkeleton(modifier: Modifier = Modifier) {
    val clock = rememberSkeletonClock()
    val color = MaterialTheme.colorScheme.surface
    Column(
        modifier.padding(top = ReasonAreaHeight).height(SuggestionHeight - ReasonAreaHeight),
    ) {
        Box(Modifier.size(180.dp, LogoHeight).skeletonBlock(clock, 0, color))
        Box(Modifier.padding(top = 16.dp).size(260.dp, 18.dp).skeletonBlock(clock, 1, color, 4.dp))
        listOf(400, 380, 240).forEach { width ->
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(width.dp, 15.dp)
                    .skeletonBlock(clock, 2, color, 4.dp),
            )
        }
    }
}

internal fun Suggestion.backdropUrl(): String? = when (this) {
    is Suggestion.MovieSuggestion -> movie.artwork.backdrop
    is Suggestion.SeriesSuggestion -> series.artwork.backdrop
}

private fun Suggestion.logoUrl(): String? = when (this) {
    is Suggestion.MovieSuggestion -> movie.artwork.logo
    is Suggestion.SeriesSuggestion -> series.artwork.logo
}

internal fun Suggestion.title(): String = when (this) {
    is Suggestion.MovieSuggestion -> movie.title
    is Suggestion.SeriesSuggestion -> series.title
}

private fun Suggestion.overview(): String? = when (this) {
    is Suggestion.MovieSuggestion -> movie.overview
    is Suggestion.SeriesSuggestion -> series.overview
}

internal fun Suggestion.resumePositionMs(): Long = when (this) {
    is Suggestion.MovieSuggestion -> movie.resumePositionMs
    is Suggestion.SeriesSuggestion -> episode.resumePositionMs
}
