package nl.jellycast.mobile.feature.home

import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.NewEntry
import nl.jellycast.mobile.core.library.NewItem

private const val NEW_EPISODE_DAYS = 7L
private const val DAY_MS = 24L * 60 * 60 * 1000

/** Home's rows from the library: the hero, then Up next, New and Not started. */
fun homeRows(index: LibraryIndex, nowMs: Long): HomeRows {
    val continueWatching = index.continueWatching
    val first = continueWatching.firstOrNull()
    val hero =
        if (first != null) {
            heroOf(
                index,
                first,
                if (first.isInProgress) HeroLabel.ContinueWatching else HeroLabel.NextEpisode,
            )
        } else {
            fallbackHero(index)?.let { heroOf(index, it, HeroLabel.StartSomethingNew) }
        }
    val newSince = nowMs - NEW_EPISODE_DAYS * DAY_MS
    return HomeRows(
        hero = hero,
        upNext =
            continueWatching.drop(1).map { item ->
                UpNextEntry(item, isNew = item.isNewEpisode(newSince))
            },
        newEntries = index.newEntries,
        notStarted =
            index.notStarted.map { series ->
                NotStartedEntry(
                    series,
                    seasonCount = index.progress(series.id)?.seasons?.count { it > 0 } ?: 0,
                )
            },
    )
}

/** The first episode of the newest series nobody started, else the first unwatched movie. */
private fun fallbackHero(index: LibraryIndex): PlayableItem? {
    val firstEpisode =
        index.notStarted.firstOrNull()?.let { series ->
            index.progress(series.id)?.episodes?.firstOrNull { (it.seasonNumber ?: 0) > 0 }
        }
    if (firstEpisode != null) return PlayableItem.EpisodeItem(firstEpisode)
    return index.snapshot.movies.firstOrNull { !it.isPlayed }?.let { PlayableItem.MovieItem(it) }
}

private fun heroOf(index: LibraryIndex, item: PlayableItem, label: HeroLabel): HomeHero =
    when (item) {
        is PlayableItem.MovieItem ->
            HomeHero(
                item = item,
                label = label,
                title = item.movie.title,
                backdropUrl = item.movie.artwork.backdrop ?: item.movie.artwork.poster,
                logoUrl = item.movie.artwork.logo,
            )

        is PlayableItem.EpisodeItem -> {
            val episode = item.episode
            val series = index.snapshot.seriesById[episode.seriesId]
            HomeHero(
                item = item,
                label = label,
                title = series?.title ?: episode.seriesTitle,
                backdropUrl =
                    series?.artwork?.backdrop ?: episode.artwork.backdrop ?: episode.stillUrl,
                logoUrl = series?.artwork?.logo ?: episode.artwork.logo,
            )
        }
    }

internal val PlayableItem.isInProgress: Boolean
    get() = when (this) {
        is PlayableItem.MovieItem -> !movie.isPlayed && movie.resumePositionMs > 0
        is PlayableItem.EpisodeItem -> !episode.isPlayed && episode.resumePositionMs > 0
    }

private fun PlayableItem.isNewEpisode(sinceMs: Long): Boolean {
    val episode = (this as? PlayableItem.EpisodeItem)?.episode ?: return false
    if (isInProgress) return false
    val added = episode.addedAt ?: return false
    return added >= sinceMs
}

/** The 16:9 image of what continues: a movie's backdrop, an episode's still. */
internal val PlayableItem.continueImageUrl: String?
    get() = when (this) {
        is PlayableItem.MovieItem -> movie.artwork.backdrop ?: movie.artwork.poster
        is PlayableItem.EpisodeItem -> episode.stillUrl ?: episode.artwork.backdrop
    }

internal val NewEntry.itemId: String
    get() = when (val item = item) {
        is NewItem.NewMovie -> item.movie.id
        is NewItem.NewSeries -> item.series.id
    }
