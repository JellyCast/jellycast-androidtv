package nl.jellycast.mobile.feature.home

import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.core.library.LibraryIndex

/**
 * Home's one list: Continue watching, then New, then Not started, each movie or series once
 * (the first group it appears in wins).
 */
fun homeTiles(index: LibraryIndex): List<HomeTile> {
    val continueTiles = index.continueWatching.map { HomeTile.Continue(it) }
    val newTiles = index.newEntries.map { HomeTile.New(it.item) }
    val notStartedTiles =
        index.notStarted.map { series ->
            val regular = index.progress(series.id)?.episodes.orEmpty()
                .filter { (it.seasonNumber ?: 0) > 0 }
            HomeTile.NotStarted(
                series,
                seasonCount = regular.mapTo(HashSet()) { it.seasonNumber }.size,
                episodeCount = regular.size,
            )
        }
    return (continueTiles + newTiles + notStartedTiles).distinctBy { it.contentId }
}

internal val PlayableItem.isInProgress: Boolean
    get() = when (this) {
        is PlayableItem.MovieItem -> !movie.isPlayed && movie.resumePositionMs > 0
        is PlayableItem.EpisodeItem -> !episode.isPlayed && episode.resumePositionMs > 0
    }
