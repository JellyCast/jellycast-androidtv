package nl.jellycast.mobile.core.library

import kotlinx.coroutines.flow.StateFlow
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series

/** The whole library as last synced; every list on Home and in the Library is derived from it. */
data class LibrarySnapshot(
    val movies: List<Movie>,
    val series: List<Series>,
    /** All episodes of all series, in series order (season, then episode number). */
    val episodes: List<Episode>,
    val syncedAtMs: Long,
) {
    val episodesBySeries: Map<String, List<Episode>> by lazy { episodes.groupBy { it.seriesId } }
    val moviesById: Map<String, Movie> by lazy { movies.associateBy { it.id } }
    val seriesById: Map<String, Series> by lazy { series.associateBy { it.id } }
    val episodesById: Map<String, Episode> by lazy { episodes.associateBy { it.id } }
}

sealed interface SyncState {
    data object Idle : SyncState

    data object Syncing : SyncState

    /** The last sync failed; [message] is for the user. A cached snapshot may still be shown. */
    data class Failed(val message: String?) : SyncState
}

/**
 * Cache first, then sync: [snapshot] is the cached library at once (or `null` on the very first
 * start) and is replaced when a sync finishes. Watch changes are applied locally first.
 */
interface LibraryStore {
    val snapshot: StateFlow<LibrarySnapshot?>

    val syncState: StateFlow<SyncState>

    /** Loads the cache and syncs; called once by the shell. */
    suspend fun start()

    /** A full sync from the server. */
    suspend fun refresh()

    /** Marks a movie, episode, season (its episodes) or series watched or not, locally first. */
    suspend fun setPlayed(itemIds: List<String>, played: Boolean): Result<Unit>

    /** Records a stopped play locally, so lists move on before the next sync. */
    fun recordPlayback(itemId: String, positionMs: Long, finished: Boolean)

    /** Called on sign-out: drops the cache. */
    suspend fun clear()
}
