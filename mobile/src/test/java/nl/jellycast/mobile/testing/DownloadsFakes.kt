package nl.jellycast.mobile.testing

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import nl.jellycast.core.data.repository.CompactTranscode
import nl.jellycast.core.data.repository.DownloadRepository
import nl.jellycast.core.data.repository.DownloadSource
import nl.jellycast.core.data.repository.ServerUserData
import nl.jellycast.mobile.core.downloads.DownloadSettings
import nl.jellycast.mobile.core.downloads.StorageSpace
import nl.jellycast.mobile.core.downloads.VolumeSpace
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind

/**
 * A server for the downloads: originals are [originalBytes] long, Compact transcodes have
 * [segmentCount] segments. Failures queued in [originalFailures] are thrown one per run.
 */
class FakeDownloadRepository : DownloadRepository {
    var sizeBytes: Long? = 2_000_000_000
    var originalBytes = ByteArray(10) { it.toByte() }
    var segmentCount = 3
    val originalFailures = ArrayDeque<Exception>()
    var userDataFailure: Exception? = null
    var reachable = true

    /** While set, an original download waits for it before finishing. */
    var originalGate: CompletableDeferred<Unit>? = null

    val userData = mutableMapOf<String, ServerUserData>()
    val sentUserData = mutableListOf<Pair<String, ServerUserData>>()
    val stoppedTranscodes = mutableListOf<String>()
    var originalRuns = 0

    override suspend fun getSource(itemId: String) =
        DownloadSource(mediaSourceId = itemId, sizeBytes = sizeBytes, techParts = listOf("4K"))

    override suspend fun startCompactTranscode(
        itemId: String,
        mediaSourceId: String,
        maxWidth: Int,
        maxBitsPerSecond: Int,
    ) = CompactTranscode("http://server/videos/$itemId/master.m3u8?a=1", "session-$itemId")

    override suspend fun stopTranscode(playSessionId: String) {
        stoppedTranscodes += playSessionId
    }

    override suspend fun fetchText(url: String): String = if (url.contains("master")) {
        "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=1\nmain.m3u8?a=1\n"
    } else {
        buildString {
            appendLine("#EXTM3U")
            appendLine("#EXT-X-MAP:URI=\"init.mp4\"")
            repeat(segmentCount) {
                appendLine("#EXTINF:3.000000,")
                appendLine("seg$it.mp4")
            }
            appendLine("#EXT-X-ENDLIST")
        }
    }

    override suspend fun fetchBytes(url: String, timeoutMs: Long): ByteArray =
        if (url.contains("init")) ByteArray(4) else ByteArray(SEGMENT_BYTES)

    override suspend fun downloadOriginal(
        itemId: String,
        target: File,
        onProgress: (bytes: Long, totalBytes: Long?) -> Unit,
    ): String {
        originalRuns++
        originalFailures.removeFirstOrNull()?.let { throw it }
        originalGate?.await()
        target.writeBytes(originalBytes)
        onProgress(originalBytes.size.toLong(), originalBytes.size.toLong())
        return "mkv"
    }

    override suspend fun getUserData(itemId: String): ServerUserData {
        userDataFailure?.let { throw it }
        return userData[itemId] ?: ServerUserData(0, played = false, lastPlayedAtMs = null)
    }

    override suspend fun setUserData(
        itemId: String,
        positionTicks: Long,
        played: Boolean,
        lastPlayedAtMs: Long,
    ) {
        val data = ServerUserData(positionTicks, played, lastPlayedAtMs)
        userData[itemId] = data
        sentUserData += itemId to data
    }

    override suspend fun isServerReachable(): Boolean = reachable

    companion object {
        const val SEGMENT_BYTES = 100
    }
}

class FakeDownloadConnectivity(offline: Boolean = false) : Connectivity {
    override val networkKind = MutableStateFlow(NetworkKind.Wifi)
    override val isServerReachable = MutableStateFlow(!offline)
    override val isOffline = MutableStateFlow(offline)
    var checks = 0

    override suspend fun checkServer(): Boolean {
        checks++
        return isServerReachable.value
    }
}

class FakeDownloadSettings(removesWatched: Boolean = true, keepsAhead: Boolean = false) :
    DownloadSettings {
    override val removesWatched: StateFlow<Boolean> = MutableStateFlow(removesWatched)
    override val keepsSeriesAhead: StateFlow<Boolean> = MutableStateFlow(keepsAhead)
    override val keptSeriesIds = MutableStateFlow(emptySet<String>())

    override fun setRemovesWatched(value: Boolean) {
        (removesWatched as MutableStateFlow).value = value
    }

    override fun setKeepsSeriesAhead(value: Boolean) {
        (keepsSeriesAhead as MutableStateFlow).value = value
    }

    override fun keep(seriesId: String) {
        keptSeriesIds.value += seriesId
    }

    override fun stopKeeping(seriesId: String) {
        keptSeriesIds.value -= seriesId
    }
}

class FakeStorageSpace(var space: VolumeSpace? = VolumeSpace(100_000_000_000, 50_000_000_000)) :
    StorageSpace {
    override fun read(): VolumeSpace? = space
}

fun networkDrop() = IOException("connection reset")

/**
 * A scope for the download store that the test scheduler drives with `advanceUntilIdle`
 * (which skips `backgroundScope` work); it ends with the test.
 */
fun TestScope.storeScope(): CoroutineScope {
    val scope = CoroutineScope(StandardTestDispatcher(testScheduler) + SupervisorJob())
    backgroundScope.launch {
        try {
            awaitCancellation()
        } finally {
            scope.cancel()
        }
    }
    return scope
}
