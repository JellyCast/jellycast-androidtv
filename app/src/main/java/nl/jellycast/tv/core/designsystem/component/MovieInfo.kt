package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.model.Movie
import nl.jellycast.tv.core.model.PlaybackTech
import nl.jellycast.tv.core.ui.audioOutcomeShort
import nl.jellycast.tv.core.ui.dynamicRangeLabel
import nl.jellycast.tv.core.ui.genreName
import nl.jellycast.tv.core.ui.playMethodLabel
import nl.jellycast.tv.core.ui.resolutionLabel
import nl.jellycast.tv.core.ui.runtimeText
import nl.jellycast.tv.core.ui.videoOutcomeShort

private val LogoWidth = 300.dp
private val LogoHeight = 74.dp
private val InfoGap = 10.dp
private val MetaRowHeight = 24.dp
private val ThreeOverviewLinesHeight = 63.dp

/** Stays inside the dark part of the backdrop gradient. */
private val TextColumnWidth = 464.dp
private val OverviewWidth = 440.dp

/** Height of the skeleton and error state, and the minimum height with content. */
val MovieInfoHeight = LogoHeight + InfoGap + MetaRowHeight + InfoGap + ThreeOverviewLinesHeight

const val OVERVIEW_SHORT_LINES = 3

/**
 * [fixedLines] always reserves [overviewMaxLines] lines, so what is below does not jump when the
 * movie changes. With [showOverview] off the space stays but the text is not laid out (cheaper
 * while browsing with key repeat).
 */
@Composable
fun MovieInfo(
    movie: Movie,
    overviewMaxLines: Int,
    modifier: Modifier = Modifier,
    fixedLines: Boolean = false,
    showOverview: Boolean = true,
) {
    Column(
        modifier = modifier.width(TextColumnWidth).heightIn(min = MovieInfoHeight),
        verticalArrangement = Arrangement.spacedBy(InfoGap),
    ) {
        Box(
            Modifier.size(width = LogoWidth, height = LogoHeight),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (movie.artwork.logo != null) {
                ArtworkImage(
                    url = movie.artwork.logo,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    showPlaceholder = false,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = movie.title,
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
            MovieMeta(movie)
        }
        Text(
            text = if (showOverview) movie.overview.orEmpty() else "",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            minLines = if (fixedLines) overviewMaxLines else 1,
            maxLines = overviewMaxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(OverviewWidth),
        )
    }
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
    Text(
        text = (if (movie.year != null) "·  " else "") + parts.joinToString("  ·  "),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
    )
}

/**
 * E.g. "4K · DV P7 → HDR10 · TRUEHD ATMOS → PCM 7.1 · DIRECT PLAY". Shows what the library knows
 * until the playback plan ([tech]) arrives. Always one line high, even when empty, so nothing
 * jumps when it fills in.
 */
@Composable
fun MovieTechLine(movie: Movie, tech: PlaybackTech?, modifier: Modifier = Modifier) {
    val outcome = tech?.outcome
    val parts =
        buildList {
            movie.tech.resolution?.let { add(resolutionLabel(it)) }
            val video = outcome?.video
            if (video != null) {
                videoOutcomeShort(video)?.let { add(it) }
            } else {
                movie.tech.dynamicRange?.let { add(dynamicRangeLabel(it)) }
            }
            val audio = outcome?.audio
            if (audio != null) {
                add(audioOutcomeShort(audio))
            } else if (movie.tech.hasAtmos) {
                add(stringResource(R.string.tech_atmos))
            }
            if (tech != null) add(playMethodLabel(outcome, tech.playMethod))
        }
    Text(
        text = parts.joinToString("  ·  ").uppercase(),
        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.06.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
fun MovieInfoSkeleton(modifier: Modifier = Modifier) {
    val clock = rememberSkeletonClock()
    val color = MaterialTheme.colorScheme.surface
    Column(modifier.height(MovieInfoHeight)) {
        Box(Modifier.size(LogoWidth, LogoHeight).skeletonBlock(clock, 0, color))
        Box(Modifier.padding(top = 16.dp).size(260.dp, 18.dp).skeletonBlock(clock, 1, color, 4.dp))
        listOf(440, 418, 264).forEach { width ->
            Box(
                Modifier
                    .padding(top = 6.dp)
                    .size(width.dp, 15.dp)
                    .skeletonBlock(clock, 2, color, 4.dp),
            )
        }
    }
}
