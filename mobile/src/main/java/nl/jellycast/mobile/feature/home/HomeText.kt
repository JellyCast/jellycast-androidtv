package nl.jellycast.mobile.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.remainingMs
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.episodeCode
import nl.jellycast.mobile.core.ui.remainingText
import nl.jellycast.mobile.ui.actions.ActionTarget

private const val UNAVAILABLE_ALPHA = 0.35f

internal fun joined(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

/** "S3 · E4", "S3" without an episode number, "Special" outside the regular seasons. */
@Composable
internal fun seasonEpisodeText(episode: Episode): String {
    val season = episode.seasonNumber ?: 0
    return when {
        season == 0 -> stringResource(R.string.home_special)
        episode.episodeNumber == null -> stringResource(R.string.home_season_code, season)
        else -> episodeCode(episode)
    }
}

/** The time left while in progress, otherwise the runtime. */
@Composable
internal fun timeLabel(item: PlayableItem): String? {
    val (position, runtime) =
        when (item) {
            is PlayableItem.MovieItem -> item.movie.resumePositionMs to item.movie.runtimeMs
            is PlayableItem.EpisodeItem -> item.episode.resumePositionMs to item.episode.runtimeMs
        }
    runtime ?: return null
    val remaining = remainingMs(position, runtime)
    return if (item.isInProgress && remaining != null) {
        remainingText(remaining)
    } else {
        durationText(runtime)
    }
}

internal val PlayableItem.displayTitle: String
    get() = when (this) {
        is PlayableItem.MovieItem -> movie.title
        is PlayableItem.EpisodeItem -> episode.seriesTitle
    }

internal val PlayableItem.year: Int?
    get() = (this as? PlayableItem.MovieItem)?.movie?.year

internal val PlayableItem.actionTarget: ActionTarget
    get() = when (this) {
        is PlayableItem.MovieItem -> ActionTarget.MovieTarget(movie)
        is PlayableItem.EpisodeItem -> ActionTarget.EpisodeTarget(episode)
    }

/** Offline and not downloaded: dimmed, and the caller disables its taps. */
@Composable
internal fun Modifier.unavailableWhen(unavailable: Boolean): Modifier {
    if (!unavailable) return this
    val hint = stringResource(R.string.home_not_downloaded)
    return alpha(UNAVAILABLE_ALPHA).semantics { stateDescription = hint }
}
