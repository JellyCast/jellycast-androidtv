package nl.jellycast.mobile.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import nl.jellycast.core.model.Episode
import nl.jellycast.mobile.R

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60L

@Composable
fun durationText(ms: Long): String {
    val minutes = (ms / MS_PER_MINUTE).coerceAtLeast(1)
    val hours = minutes / MINUTES_PER_HOUR
    val rest = minutes % MINUTES_PER_HOUR
    return when {
        hours == 0L -> stringResource(R.string.duration_minutes, rest)
        rest == 0L -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, rest)
    }
}

@Composable
fun remainingText(ms: Long): String = stringResource(R.string.remaining, durationText(ms))

/** "S3 · E4"; specials without numbers show only what is known. */
@Composable
fun episodeCode(episode: Episode): String {
    val season = episode.seasonNumber
    val number = episode.episodeNumber
    return when {
        season != null && number != null -> stringResource(R.string.episode_code, season, number)
        number != null -> stringResource(R.string.episode_number, number)
        else -> ""
    }
}
