package nl.jellycast.mobile.core.library

import kotlinx.coroutines.flow.StateFlow

/** How long the cache read and the last full sync took; `null` until they happened. */
data class LibraryTimings(val cacheLoadMs: Long? = null, val lastSyncMs: Long? = null)

/** For the developer line in Settings ("cache 120 ms · sync 900 ms"). */
interface LibraryDiagnostics {
    val timings: StateFlow<LibraryTimings>
}
