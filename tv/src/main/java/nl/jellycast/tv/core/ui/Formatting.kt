package nl.jellycast.tv.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import nl.jellycast.core.common.genreStringRes
import nl.jellycast.core.model.DynamicRange
import nl.jellycast.core.model.Resolution
import nl.jellycast.tv.R

private const val MS_PER_MINUTE = 60_000L
private const val MINUTES_PER_HOUR = 60

/** "S3 · E4", or empty without numbers. */
@Composable
fun episodeCode(season: Int?, episode: Int?): String = if (season != null && episode != null) {
    stringResource(R.string.episode_code, season, episode)
} else {
    ""
}

/** "2 h 49 min" or "52 min". */
@Composable
fun runtimeText(ms: Long): String {
    val totalMinutes = ((ms + MS_PER_MINUTE / 2) / MS_PER_MINUTE).toInt()
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return if (hours > 0) {
        stringResource(R.string.runtime_hours_minutes, hours, minutes)
    } else {
        stringResource(R.string.runtime_minutes, minutes)
    }
}

@Composable
fun remainingText(ms: Long): String = stringResource(R.string.runtime_remaining, runtimeText(ms))

@Composable
fun seasonCountText(count: Int): String = pluralStringResource(R.plurals.season_count, count, count)

@Composable
fun resolutionLabel(resolution: Resolution): String = stringResource(
    when (resolution) {
        Resolution.UHD -> R.string.tech_4k
        Resolution.HD -> R.string.tech_hd
        Resolution.SD -> R.string.tech_sd
    },
)

/** "DV" or "HDR", for a spine. */
@Composable
fun dynamicRangeShortLabel(range: DynamicRange): String = stringResource(
    when (range) {
        DynamicRange.DolbyVision -> R.string.tech_dv_short
        DynamicRange.Hdr10Plus, DynamicRange.Hdr10, DynamicRange.Hlg -> R.string.tech_hdr_short
    },
)

@Composable
fun dynamicRangeLabel(range: DynamicRange): String = stringResource(
    when (range) {
        DynamicRange.DolbyVision -> R.string.tech_dolby_vision
        DynamicRange.Hdr10Plus -> R.string.tech_hdr10_plus
        DynamicRange.Hdr10 -> R.string.tech_hdr10
        DynamicRange.Hlg -> R.string.tech_hlg
    },
)

/** Server genre in the UI language; an unknown genre stays as the server names it. */
@Composable
fun genreName(serverName: String): String =
    genreStringRes(serverName)?.let { stringResource(it) } ?: serverName
