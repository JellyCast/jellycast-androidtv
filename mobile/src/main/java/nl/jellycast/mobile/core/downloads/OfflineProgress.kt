package nl.jellycast.mobile.core.downloads

import java.io.File
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import nl.jellycast.core.common.Clock
import nl.jellycast.core.data.repository.DownloadRepository
import nl.jellycast.core.data.repository.DownloadServerException
import nl.jellycast.core.data.repository.ServerUserData
import nl.jellycast.mobile.core.library.OfflinePosition
import nl.jellycast.mobile.core.library.PendingProgress
import timber.log.Timber

/** A position reached in a download, with when it was watched. */
@Serializable
data class SavedOfflinePosition(
    val itemId: String,
    val positionTicks: Long,
    val played: Boolean,
    val watchedAtMs: Long,
)

/**
 * Positions watched in downloads, kept on the device until the server has them, so a
 * position survives the app being closed in airplane mode.
 */
class OfflineProgress(
    private val files: DownloadFiles,
    private val repository: DownloadRepository,
    private val clock: Clock,
) : PendingProgress {
    private val file = File(files.root, FILE_NAME)
    private val positions = MutableStateFlow(load())
    private val isSending = AtomicBoolean(false)

    override fun pending(): Map<String, OfflinePosition> = positions.value.mapValues { (_, saved) ->
        OfflinePosition(
            positionMs = saved.positionTicks / TICKS_PER_MS,
            played = saved.played,
            watchedAtMs = saved.watchedAtMs,
        )
    }

    fun record(itemId: String, positionMs: Long, finished: Boolean) {
        val position =
            SavedOfflinePosition(
                itemId = itemId,
                positionTicks = if (finished) 0 else positionMs * TICKS_PER_MS,
                played = finished,
                watchedAtMs = clock.nowMs(),
            )
        positions.update { it + (itemId to position) }
        save()
    }

    /**
     * Sends what the server doesn't have yet; the most recent position wins, so one watched
     * later on a TV stays. Returns whether the server changed.
     */
    suspend fun send(): Boolean {
        if (positions.value.isEmpty() || !isSending.compareAndSet(false, true)) return false
        try {
            var changed = false
            for (position in positions.value.values.sortedBy { it.watchedAtMs }) {
                try {
                    val server = repository.getUserData(position.itemId)
                    if (isNewer(position, server)) {
                        repository.setUserData(
                            itemId = position.itemId,
                            positionTicks = position.positionTicks,
                            played = position.played || server.played,
                            lastPlayedAtMs = position.watchedAtMs,
                        )
                        changed = true
                    }
                    positions.update { pending ->
                        if (pending[position.itemId] ==
                            position
                        ) {
                            pending - position.itemId
                        } else {
                            pending
                        }
                    }
                    save()
                } catch (e: IOException) {
                    Timber.w(e, "Offline position not sent")
                    return changed
                } catch (e: DownloadServerException) {
                    Timber.w(e, "Offline position not sent")
                    return changed
                }
            }
            return changed
        } finally {
            isSending.set(false)
        }
    }

    private fun load(): Map<String, SavedOfflinePosition> =
        files.readJson<List<SavedOfflinePosition>>(file).orEmpty()
            .groupBy { it.itemId }
            .mapValues { (_, positions) -> positions.maxBy { it.watchedAtMs } }

    private fun save() = files.writeJson(file, positions.value.values.toList())

    companion object {
        private const val FILE_NAME = "progress.json"
        private const val TICKS_PER_MS = 10_000L

        fun isNewer(position: SavedOfflinePosition, server: ServerUserData): Boolean {
            val lastPlayed = server.lastPlayedAtMs ?: return true
            return position.watchedAtMs > lastPlayed
        }
    }
}
