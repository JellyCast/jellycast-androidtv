package nl.jellycast.mobile.ui.actions

import java.net.URLEncoder
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.ExternalIds
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.SeriesStatus
import nl.jellycast.mobile.core.settings.RatingSource

/** One choice in the action drawer, in the order the drawer lists them. */
sealed interface DrawerAction {
    /** [episodeLabel] set: a series or season plays this episode ("Play S2 · E3"). */
    data class Play(
        val item: PlayableItem,
        val isInProgress: Boolean,
        val episodeLabel: Episode? = null,
    ) : DrawerAction

    data class PlayFromStart(val item: PlayableItem) : DrawerAction

    data class PlayOn(val item: PlayableItem) : DrawerAction

    data class SetPlayed(val itemIds: List<String>, val played: Boolean) : DrawerAction

    data class Download(val itemId: String) : DrawerAction

    data class AddToList(val itemIds: List<String>) : DrawerAction

    data class GoToMovie(val movieId: String) : DrawerAction

    data class GoToSeries(val seriesId: String) : DrawerAction

    data class OpenOn(val url: String, val source: RatingSource) : DrawerAction
}

/** What the drawer offers right now; [index] has what was marked since the card was drawn. */
data class DrawerContext(
    val index: LibraryIndex?,
    val isOffline: Boolean,
    val hasRemoteTargets: Boolean,
    val ratingSource: RatingSource,
    val allowsNavigation: Boolean,
)

fun drawerActions(target: ActionTarget, context: DrawerContext): List<DrawerAction> = buildList {
    val playable = playableOf(target, context.index)
    if (playable != null) {
        val inProgress = playable.isInProgress
        val episodeLabel =
            (playable as? PlayableItem.EpisodeItem)
                ?.episode
                ?.takeIf {
                    target is ActionTarget.SeriesTarget ||
                        target is ActionTarget.SeasonTarget
                }
        add(DrawerAction.Play(playable, inProgress, episodeLabel))
        if (inProgress) add(DrawerAction.PlayFromStart(playable))
        if (context.hasRemoteTargets) add(DrawerAction.PlayOn(playable))
    }
    if (!context.isOffline) {
        val status = statusOf(target, context.index)
        val ids = playedIds(target)
        if (status != SeriesStatus.Watched) add(DrawerAction.SetPlayed(ids, played = true))
        if (status != SeriesStatus.NotStarted) add(DrawerAction.SetPlayed(ids, played = false))
    }
    if (playable != null && target.isSingleItem) add(DrawerAction.Download(playable.id))
    if (!context.isOffline) add(DrawerAction.AddToList(playedIds(target)))
    if (context.allowsNavigation) {
        add(
            when (target) {
                is ActionTarget.MovieTarget -> DrawerAction.GoToMovie(target.movie.id)
                is ActionTarget.SeriesTarget -> DrawerAction.GoToSeries(target.series.id)
                is ActionTarget.EpisodeTarget -> DrawerAction.GoToSeries(target.episode.seriesId)
                is ActionTarget.SeasonTarget -> DrawerAction.GoToSeries(target.series.id)
            },
        )
        externalUrl(target, context)?.let { add(DrawerAction.OpenOn(it, context.ratingSource)) }
    }
}

private val ActionTarget.isSingleItem: Boolean
    get() = this is ActionTarget.MovieTarget || this is ActionTarget.EpisodeTarget

private val PlayableItem.isInProgress: Boolean
    get() = when (this) {
        is PlayableItem.MovieItem -> !movie.isPlayed && movie.resumePositionMs > 0
        is PlayableItem.EpisodeItem -> episode.isInProgress
    }

private val Episode.isInProgress: Boolean get() = !isPlayed && resumePositionMs > 0

private fun playableOf(target: ActionTarget, index: LibraryIndex?): PlayableItem? {
    val snapshot = index?.snapshot
    return when (target) {
        is ActionTarget.MovieTarget ->
            PlayableItem.MovieItem(snapshot?.moviesById?.get(target.movie.id) ?: target.movie)

        is ActionTarget.EpisodeTarget ->
            PlayableItem.EpisodeItem(
                snapshot?.episodesById?.get(target.episode.id) ?: target.episode,
            )

        is ActionTarget.SeriesTarget ->
            index?.progress(target.series.id)?.next?.let { PlayableItem.EpisodeItem(it) }

        is ActionTarget.SeasonTarget -> {
            val episodes = seasonEpisodes(target, index)
            (episodes.firstOrNull { it.isInProgress } ?: episodes.firstOrNull { !it.isPlayed })
                ?.let { PlayableItem.EpisodeItem(it) }
        }
    }
}

private fun seasonEpisodes(target: ActionTarget.SeasonTarget, index: LibraryIndex?): List<Episode> {
    val current = index?.snapshot?.episodesById
    return target.episodes.map { current?.get(it.id) ?: it }
}

private fun statusOf(target: ActionTarget, index: LibraryIndex?): SeriesStatus = when (target) {
    is ActionTarget.SeriesTarget ->
        index?.progress(target.series.id)?.status
            ?: when {
                target.series.isPlayed -> SeriesStatus.Watched
                target.series.hasPlayedEpisodes -> SeriesStatus.InProgress
                else -> SeriesStatus.NotStarted
            }

    is ActionTarget.SeasonTarget -> {
        val episodes = seasonEpisodes(target, index)
        when {
            episodes.isNotEmpty() && episodes.all { it.isPlayed } -> SeriesStatus.Watched
            episodes.any { it.isPlayed || it.isInProgress } -> SeriesStatus.InProgress
            else -> SeriesStatus.NotStarted
        }
    }

    else ->
        when (val playable = playableOf(target, index)) {
            is PlayableItem.MovieItem ->
                statusOf(playable.movie.isPlayed, playable.movie.resumePositionMs)

            is PlayableItem.EpisodeItem ->
                statusOf(playable.episode.isPlayed, playable.episode.resumePositionMs)

            null -> SeriesStatus.NotStarted
        }
}

private fun statusOf(isPlayed: Boolean, resumePositionMs: Long): SeriesStatus = when {
    isPlayed -> SeriesStatus.Watched
    resumePositionMs > 0 -> SeriesStatus.InProgress
    else -> SeriesStatus.NotStarted
}

private fun playedIds(target: ActionTarget): List<String> = when (target) {
    is ActionTarget.MovieTarget -> listOf(target.movie.id)
    is ActionTarget.SeriesTarget -> listOf(target.series.id)
    is ActionTarget.EpisodeTarget -> listOf(target.episode.id)
    is ActionTarget.SeasonTarget -> target.episodes.map { it.id }
}

private fun externalUrl(target: ActionTarget, context: DrawerContext): String? {
    val source = context.ratingSource
    return when (target) {
        is ActionTarget.MovieTarget -> {
            val movie = context.index?.snapshot?.moviesById?.get(target.movie.id) ?: target.movie
            titleUrl(source, movie.externalIds, movie.title, TitleKind.Movie)
        }

        is ActionTarget.SeriesTarget ->
            titleUrl(source, target.series.externalIds, target.series.title, TitleKind.Series)

        is ActionTarget.SeasonTarget ->
            titleUrl(source, target.series.externalIds, target.series.title, TitleKind.Series)

        is ActionTarget.EpisodeTarget -> {
            val episode = target.episode
            val series = context.index?.snapshot?.seriesById?.get(episode.seriesId)
            // TMDb files episodes under the series, so its link takes the series' id.
            val ids = episode.externalIds.copy(tmdb = series?.externalIds?.tmdb)
            titleUrl(
                source,
                ids,
                episode.seriesTitle,
                TitleKind.Episode(episode.seasonNumber, episode.episodeNumber),
            )
        }
    }
}

private sealed interface TitleKind {
    data object Movie : TitleKind

    data object Series : TitleKind

    data class Episode(val season: Int?, val number: Int?) : TitleKind
}

private fun titleUrl(
    source: RatingSource,
    ids: ExternalIds,
    searchName: String,
    kind: TitleKind,
): String? = when (source) {
    RatingSource.Imdb -> ids.imdb?.let { "https://www.imdb.com/title/$it/" }

    RatingSource.Tmdb ->
        ids.tmdb?.let { tmdb ->
            when (kind) {
                TitleKind.Movie -> "https://www.themoviedb.org/movie/$tmdb"

                TitleKind.Series -> "https://www.themoviedb.org/tv/$tmdb"

                is TitleKind.Episode ->
                    if (kind.season != null && kind.number != null) {
                        "https://www.themoviedb.org/tv/$tmdb/season/${kind.season}" +
                            "/episode/${kind.number}"
                    } else {
                        "https://www.themoviedb.org/tv/$tmdb"
                    }
            }
        }

    RatingSource.RottenTomatoes ->
        "https://www.rottentomatoes.com/search?search=" +
            URLEncoder.encode(searchName, "UTF-8").replace("+", "%20")
}
