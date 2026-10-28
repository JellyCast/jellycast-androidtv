package nl.jellycast.mobile.testing

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.Series
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.SyncState

/** A library store for tests: set [snapshot] directly; calls are recorded. */
class FakeLibraryStore(
    movies: List<Movie> = emptyList(),
    series: List<Series> = emptyList(),
    episodes: List<Episode> = emptyList(),
) : LibraryStore {
    override val snapshot =
        MutableStateFlow<LibrarySnapshot?>(LibrarySnapshot(movies, series, episodes, 0))
    override val syncState = MutableStateFlow<SyncState>(SyncState.Idle)

    val playedCalls = mutableListOf<Pair<List<String>, Boolean>>()
    val recorded = mutableListOf<Triple<String, Long, Boolean>>()
    var refreshCalls = 0
    var failNext = false

    override suspend fun start() = Unit

    override suspend fun refresh() {
        refreshCalls++
    }

    override suspend fun setPlayed(itemIds: List<String>, played: Boolean): Result<Unit> {
        if (failNext) {
            failNext = false
            return Result.failure(IllegalStateException("server gone"))
        }
        playedCalls += itemIds to played
        return Result.success(Unit)
    }

    override fun recordPlayback(itemId: String, positionMs: Long, finished: Boolean) {
        recorded += Triple(itemId, positionMs, finished)
    }

    override suspend fun clear() {
        snapshot.value = null
    }
}
