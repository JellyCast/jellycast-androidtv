package nl.jellycast.mobile.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.runningFold
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.core.library.SyncState
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.ui.downloads.OfflineAvailability

/** Treats nothing as downloaded, until the downloads area provides its own. */
val NothingDownloaded = OfflineAvailability { false }

private const val MIN_REFRESH_SPINNER_MS = 600L

class HomeViewModel(
    private val store: LibraryStore,
    connectivity: Connectivity,
    private val offlineAvailability: OfflineAvailability = NothingDownloaded,
    private val nowMs: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)

    private val library: Flow<Pair<LibrarySnapshot, HomeRows>?> =
        store.snapshot.map { snapshot ->
            snapshot?.let {
                val now = nowMs()
                it to homeRows(LibraryIndex(it, now), now)
            }
        }

    /** The last failure stays while a retry runs, so the banner can show its spinner. */
    private val lastFailure: Flow<SyncState.Failed?> =
        store.syncState.runningFold(null as SyncState.Failed?) { last, state ->
            when (state) {
                is SyncState.Failed -> state
                SyncState.Syncing -> last
                SyncState.Idle -> null
            }
        }

    private val syncStatus: Flow<Pair<SyncState, SyncState.Failed?>> =
        combine(store.syncState, lastFailure) { state, failure -> state to failure }

    val uiState: StateFlow<HomeUiState> =
        combine(
            library,
            syncStatus,
            connectivity.isOffline,
            refreshing,
        ) { library, (syncState, failure), isOffline, isRefreshing ->
            val content =
                when {
                    library != null -> HomeContent.Ready(library.second)

                    syncState is SyncState.Failed -> HomeContent.Failed(
                        syncState.message,
                        isOffline,
                    )

                    else -> HomeContent.Loading
                }
            HomeUiState(
                content = content,
                syncProblem =
                    if (library != null && failure != null) {
                        SyncProblem(
                            message = failure.message,
                            syncedAtMs = library.first.syncedAtMs,
                            isRetrying = syncState is SyncState.Syncing,
                            isOffline = isOffline,
                        )
                    } else {
                        null
                    },
                isRefreshing = isRefreshing,
                unavailableIds =
                    if (isOffline && library != null) {
                        library.second.itemIds
                            .filterNot(offlineAvailability::isAvailableOffline)
                            .toSet()
                    } else {
                        emptySet()
                    },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    /** Pull to refresh: the spinner stays at least a moment, so a fast refresh still registers. */
    fun onRefresh() {
        if (refreshing.value) return
        refreshing.value = true
        viewModelScope.launch {
            try {
                coroutineScope {
                    launch { delay(MIN_REFRESH_SPINNER_MS) }
                    store.refresh()
                }
            } finally {
                refreshing.value = false
            }
        }
    }

    fun onRetry() {
        viewModelScope.launch { store.refresh() }
    }
}
