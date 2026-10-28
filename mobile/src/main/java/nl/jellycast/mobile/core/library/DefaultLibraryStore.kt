package nl.jellycast.mobile.core.library

import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.TimeSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.LibraryAccount
import nl.jellycast.core.data.repository.LibraryContent
import nl.jellycast.core.data.repository.LibraryRepository
import nl.jellycast.core.data.repository.LibrarySyncRepository
import nl.jellycast.core.data.repository.SessionExpiredException
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AuthState
import nl.jellycast.mobile.core.network.Connectivity
import timber.log.Timber

/**
 * The library of the signed-in user, as iOS `LibraryStore`: the cached copy first, then a full
 * sync. Signing out (from anywhere) drops it; a refused token signs out.
 */
class DefaultLibraryStore(
    private val sync: LibrarySyncRepository,
    private val library: LibraryRepository,
    private val session: SessionRepository,
    private val cache: LibraryCache,
    private val connectivity: Connectivity,
    private val pendingProgress: PendingProgress,
    private val clock: Clock,
    scope: CoroutineScope,
) : LibraryStore,
    LibraryDiagnostics {
    private val _snapshot = MutableStateFlow<LibrarySnapshot?>(null)
    override val snapshot: StateFlow<LibrarySnapshot?> = _snapshot.asStateFlow()

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    override val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _timings = MutableStateFlow(LibraryTimings())
    override val timings: StateFlow<LibraryTimings> = _timings.asStateFlow()

    private val syncLock = Mutex()

    // Whose library [snapshot] is; another account never sees it.
    @Volatile private var account: LibraryAccount? = null

    // Raised by [clear], so a sync that was running then doesn't bring the library back.
    @Volatile private var generation = 0

    init {
        scope.launch {
            session.authState.collect { state -> if (state !is AuthState.LoggedIn) clear() }
        }
        scope.launch {
            connectivity.isOffline.collect { offline ->
                if (!offline && _syncState.value is SyncState.Failed) refresh()
            }
        }
    }

    override suspend fun start() {
        val current = sync.account() ?: return
        if (account != current) {
            account = current
            _snapshot.value = null
        }
        if (_snapshot.value == null) {
            val started = TimeSource.Monotonic.markNow()
            val cached = cache.load(current)
            if (cached != null && _snapshot.value == null && account == current) {
                _snapshot.value = cached.withOfflineProgress(pendingProgress.pending())
            }
            _timings.update { it.copy(cacheLoadMs = started.elapsedNow().inWholeMilliseconds) }
        }
        refresh()
    }

    override suspend fun refresh() {
        if (!syncLock.tryLock()) return
        try {
            val current = sync.account() ?: return
            val generationAtStart = generation
            _syncState.value = SyncState.Syncing
            val started = TimeSource.Monotonic.markNow()
            val result = sync.fetchLibrary()
            if (generation != generationAtStart) return
            result
                .onSuccess { content ->
                    val fresh = content.toSnapshot(clock.nowMs())
                    account = current
                    _snapshot.value = fresh.withOfflineProgress(pendingProgress.pending())
                    _timings.update {
                        it.copy(lastSyncMs = started.elapsedNow().inWholeMilliseconds)
                    }
                    _syncState.value = SyncState.Idle
                    cache.save(current, fresh)
                }.onFailure { error ->
                    _syncState.value = SyncState.Failed(error.message)
                    if (error is SessionExpiredException) {
                        Timber.i("The server refused the sign-in; signing out")
                        session.logout()
                    }
                }
        } catch (e: CancellationException) {
            if (_syncState.value == SyncState.Syncing) _syncState.value = SyncState.Idle
            throw e
        } finally {
            syncLock.unlock()
        }
    }

    override suspend fun setPlayed(itemIds: List<String>, played: Boolean): Result<Unit> {
        val before = _snapshot.value
        _snapshot.update { it?.withPlayed(itemIds, played, clock.nowMs()) }
        itemIds.forEach { id ->
            library.setPlayed(id, played).onFailure { error ->
                if (before != null) _snapshot.value = before
                _syncState.value = SyncState.Failed(error.message)
                return Result.failure(error)
            }
        }
        return Result.success(Unit)
    }

    override fun recordPlayback(itemId: String, positionMs: Long, finished: Boolean) {
        _snapshot.update { it?.withPlayback(itemId, positionMs, finished, clock.nowMs()) }
    }

    override suspend fun clear() {
        generation++
        account = null
        _snapshot.value = null
        _syncState.value = SyncState.Idle
        _timings.value = LibraryTimings()
        cache.delete()
    }
}

internal fun LibraryContent.toSnapshot(syncedAtMs: Long) = LibrarySnapshot(
    movies = movies,
    series = series,
    episodes = episodes.sortedWith(AirOrder),
    syncedAtMs = syncedAtMs,
)
