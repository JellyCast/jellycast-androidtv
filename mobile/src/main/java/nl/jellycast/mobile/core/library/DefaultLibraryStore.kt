package nl.jellycast.mobile.core.library

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.LibraryRepository

/**
 * Owner: the library worker. A first version without disk cache and without episodes: it only
 * lets the screens run until the real store (cache first, full sync of movies, series and
 * episodes) replaces it.
 */
class DefaultLibraryStore(private val library: LibraryRepository, private val clock: Clock) :
    LibraryStore {
    private val _snapshot = MutableStateFlow<LibrarySnapshot?>(null)
    override val snapshot: StateFlow<LibrarySnapshot?> = _snapshot.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    override suspend fun start() = refresh()

    override suspend fun refresh() = coroutineScope {
        _syncState.value = SyncState.Syncing
        val movies = async { library.getMovies() }
        val series = async { library.getSeries() }
        val loadedMovies = movies.await().getOrNull()
        val loadedSeries = series.await().getOrNull()
        if (loadedMovies == null || loadedSeries == null) {
            _syncState.value = SyncState.Failed(null)
        } else {
            _snapshot.value =
                LibrarySnapshot(loadedMovies, loadedSeries, emptyList(), clock.nowMs())
            _syncState.value = SyncState.Idle
        }
    }

    override suspend fun setPlayed(itemIds: List<String>, played: Boolean): Result<Unit> {
        itemIds.forEach { id ->
            library.setPlayed(id, played).onFailure { return Result.failure(it) }
        }
        return Result.success(Unit)
    }

    override fun recordPlayback(itemId: String, positionMs: Long, finished: Boolean) = Unit

    override suspend fun clear() {
        _snapshot.value = null
    }
}
