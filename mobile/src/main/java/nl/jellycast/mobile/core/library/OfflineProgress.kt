package nl.jellycast.mobile.core.library

/** Where a download was left while the server couldn't be told, as iOS `OfflinePosition`. */
data class OfflinePosition(val positionMs: Long, val played: Boolean, val watchedAtMs: Long)

/**
 * Positions watched offline that the server doesn't have yet, by item id. The downloads area
 * provides the real one; the library lays them over every snapshot it shows.
 */
fun interface PendingProgress {
    fun pending(): Map<String, OfflinePosition>
}

object NoPendingProgress : PendingProgress {
    override fun pending(): Map<String, OfflinePosition> = emptyMap()
}
