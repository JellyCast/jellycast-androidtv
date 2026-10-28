package nl.jellycast.mobile.feature.lists

import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Playlist
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.navigation.PlayQueue
import nl.jellycast.mobile.navigation.PlayRequest

data class ListItem(val entryId: String, val playable: PlayableItem)

/** A list read against the library: what it holds that the app can play, and how far along. */
data class ListProgress(val items: List<ListItem>) {
    val watchedCount: Int get() = items.count { it.playable.isPlayed }

    /** The first item not fully watched: where Continue picks up. */
    val next: ListItem? get() = items.firstOrNull { !it.playable.isPlayed }

    val isStarted: Boolean get() = items.any { it.playable.isPlayed || it.playable.resumeMs > 0 }

    val remainingMs: Long
        get() = items
            .filterNot { it.playable.isPlayed }
            .sumOf { (it.playable.runtimeMs - it.playable.resumeMs).coerceAtLeast(0) }

    companion object {
        fun of(list: Playlist, snapshot: LibrarySnapshot?): ListProgress = ListProgress(
            list.entries.mapNotNull { entry ->
                val playable =
                    snapshot?.moviesById?.get(entry.itemId)?.let { PlayableItem.MovieItem(it) }
                        ?: snapshot?.episodesById?.get(entry.itemId)?.let {
                            PlayableItem.EpisodeItem(it)
                        }
                playable?.let { ListItem(entry.entryId, it) }
            },
        )
    }
}

/** Plays the list from [position] on, item after item. */
fun ListProgress.playRequest(
    listId: String,
    listName: String,
    position: Int,
    fromStart: Boolean = false,
): PlayRequest? {
    val item = items.getOrNull(position) ?: return null
    return PlayRequest(
        item = item.playable,
        fromStart = fromStart,
        queue = PlayQueue(listId, listName, items.map { it.playable }, position),
    )
}

/** Continue from the next item, or from the beginning once everything is watched. */
fun ListProgress.continueRequest(listId: String, listName: String): PlayRequest? {
    val start = next?.let { next -> items.indexOfFirst { it.entryId == next.entryId } }
    return playRequest(listId, listName, start ?: 0, fromStart = start == null)
}

val PlayableItem.isPlayed: Boolean
    get() = when (this) {
        is PlayableItem.MovieItem -> movie.isPlayed
        is PlayableItem.EpisodeItem -> episode.isPlayed
    }

val PlayableItem.resumeMs: Long
    get() = when (this) {
        is PlayableItem.MovieItem -> movie.resumePositionMs
        is PlayableItem.EpisodeItem -> episode.resumePositionMs
    }

val PlayableItem.runtimeMs: Long
    get() = when (this) {
        is PlayableItem.MovieItem -> movie.runtimeMs
        is PlayableItem.EpisodeItem -> episode.runtimeMs
    } ?: 0

/** An episode is pictured and named by its series. */
fun PlayableItem.listPosterUrl(snapshot: LibrarySnapshot?): String? = when (this) {
    is PlayableItem.MovieItem -> movie.artwork.poster

    is PlayableItem.EpisodeItem ->
        snapshot?.seriesById?.get(episode.seriesId)?.artwork?.poster ?: episode.artwork.landscape
}
