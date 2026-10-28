package nl.jellycast.mobile.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.progress
import nl.jellycast.core.model.remainingMs
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.library.NewItem
import nl.jellycast.mobile.core.ui.durationText
import nl.jellycast.mobile.core.ui.episodeCode
import nl.jellycast.mobile.core.ui.remainingText
import nl.jellycast.mobile.navigation.MovieRoute
import nl.jellycast.mobile.navigation.Route
import nl.jellycast.mobile.navigation.SeriesRoute
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

internal val HomeTile.title: String
    get() = when (this) {
        is HomeTile.Continue -> when (item) {
            is PlayableItem.MovieItem -> item.movie.title
            is PlayableItem.EpisodeItem -> item.episode.seriesTitle
        }

        is HomeTile.New -> when (item) {
            is NewItem.NewMovie -> item.movie.title
            is NewItem.NewSeries -> item.series.title
        }

        is HomeTile.NotStarted -> series.title
    }

/** "S2 · E7 · Episode name", "S4 · E1 · new episode", "Movie · 2001", "Series · 2 seasons". */
@Composable
internal fun HomeTile.meta(): String = when (this) {
    is HomeTile.Continue -> when (item) {
        is PlayableItem.MovieItem -> movieMeta(item.movie)
        is PlayableItem.EpisodeItem -> joined(seasonEpisodeText(item.episode), item.episode.title)
    }

    is HomeTile.New -> when (item) {
        is NewItem.NewMovie -> movieMeta(item.movie)

        is NewItem.NewSeries ->
            joined(
                seasonEpisodeText(item.firstAdded),
                if (item.count == 1) {
                    stringResource(R.string.home_new_episode)
                } else {
                    pluralStringResource(R.plurals.home_new_episodes, item.count, item.count)
                },
            )
    }

    is HomeTile.NotStarted ->
        joined(
            stringResource(R.string.home_series),
            pluralStringResource(R.plurals.home_seasons, seasonCount, seasonCount),
        )
}

@Composable
private fun movieMeta(movie: Movie): String =
    joined(stringResource(R.string.home_movie), movie.year?.toString())

/** Time left while in progress, otherwise the runtime; a series nobody started: its episodes. */
@Composable
internal fun HomeTile.timeBadge(): String? = when (this) {
    is HomeTile.Continue -> timeLabel(item)

    is HomeTile.New -> when (item) {
        is NewItem.NewMovie -> item.movie.runtimeMs?.let { durationText(it) }
        is NewItem.NewSeries -> item.firstAdded.runtimeMs?.let { durationText(it) }
    }

    is HomeTile.NotStarted ->
        pluralStringResource(R.plurals.home_episode_count, episodeCount, episodeCount)
}

@Composable
private fun timeLabel(item: PlayableItem): String? {
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

/** How far a tile in progress is; `null` draws no line. */
internal val HomeTile.progress: Float?
    get() {
        val item = (this as? HomeTile.Continue)?.item ?: return null
        if (!item.isInProgress) return null
        return when (item) {
            is PlayableItem.MovieItem -> item.movie.progress
            is PlayableItem.EpisodeItem -> item.episode.progress
        }
    }

/** The 16:9 art: a movie's backdrop, an episode's still, a series' backdrop. */
internal val HomeTile.imageUrl: String?
    get() = when (this) {
        is HomeTile.Continue -> when (item) {
            is PlayableItem.MovieItem ->
                item.movie.artwork.backdrop ?: item.movie.artwork.poster

            is PlayableItem.EpisodeItem ->
                item.episode.stillUrl ?: item.episode.artwork.backdrop
        }

        is HomeTile.New -> when (item) {
            is NewItem.NewMovie -> item.movie.artwork.run { backdrop ?: landscape ?: poster }
            is NewItem.NewSeries -> item.series.artwork.run { backdrop ?: landscape ?: poster }
        }

        is HomeTile.NotStarted -> series.artwork.run { backdrop ?: landscape ?: poster }
    }

/** Where a tap on a New or Not started tile goes; Continue tiles play instead. */
internal val HomeTile.route: Route
    get() = when (this) {
        is HomeTile.Continue -> when (item) {
            is PlayableItem.MovieItem -> MovieRoute(item.movie.id)
            is PlayableItem.EpisodeItem -> SeriesRoute(item.episode.seriesId)
        }

        is HomeTile.New -> when (item) {
            is NewItem.NewMovie -> MovieRoute(item.movie.id)
            is NewItem.NewSeries -> SeriesRoute(item.series.id)
        }

        is HomeTile.NotStarted -> SeriesRoute(series.id)
    }

internal val HomeTile.actionTarget: ActionTarget
    get() = when (this) {
        is HomeTile.Continue -> when (item) {
            is PlayableItem.MovieItem -> ActionTarget.MovieTarget(item.movie)
            is PlayableItem.EpisodeItem -> ActionTarget.EpisodeTarget(item.episode)
        }

        is HomeTile.New -> when (item) {
            is NewItem.NewMovie -> ActionTarget.MovieTarget(item.movie)
            is NewItem.NewSeries -> ActionTarget.SeriesTarget(item.series)
        }

        is HomeTile.NotStarted -> ActionTarget.SeriesTarget(series)
    }

/** Offline and not downloaded: dimmed, and the caller disables its taps. */
@Composable
internal fun Modifier.unavailableWhen(unavailable: Boolean): Modifier {
    if (!unavailable) return this
    val hint = stringResource(R.string.home_not_downloaded)
    return alpha(UNAVAILABLE_ALPHA).semantics { stateDescription = hint }
}
