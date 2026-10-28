package nl.jellycast.mobile.core.downloads

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import nl.jellycast.core.data.repository.DownloadRepository
import nl.jellycast.core.data.repository.DownloadServerException
import timber.log.Timber

/** Where a Compact download goes on after an interruption: what is already in the file. */
@Serializable
data class CompactProgress(
    val initBytes: Long = 0,
    val segments: List<SavedSegment> = emptyList(),
) {
    val bytes: Long get() = initBytes + segments.sumOf { it.bytes }

    val nextSegment: Int get() = segments.size
}

data class CompactUpdate(val bytes: Long, val mediaSeconds: Double, val totalMediaSeconds: Double)

/**
 * Fetches the server's HLS transcode of one item segment by segment and appends it into one
 * file; the progress is saved after each segment, so a new transcode goes on at the next one.
 * At the end a local playlist over that file makes it seekable.
 */
class CompactDownloader(
    private val repository: DownloadRepository,
    private val files: DownloadFiles,
    /** Stops a transcode even after the download was cancelled. */
    private val cleanupScope: CoroutineScope,
) {
    suspend fun run(itemId: String, onProgress: (CompactUpdate) -> Unit) {
        val source = repository.getSource(itemId) ?: throw DownloadServerException(0)
        val folder = files.prepareFolder(itemId)
        val part = File(folder, PART_NAME)
        var progress = loadProgress(itemId) ?: CompactProgress()
        if (progress.nextSegment == 0) progress = CompactProgress()
        truncate(part, progress.bytes)

        val transcode =
            repository.startCompactTranscode(itemId, source.mediaSourceId, MAX_WIDTH, MAX_BITRATE)
        try {
            val master = repository.fetchText(transcode.playlistUrl)
            val variantUrl = HlsPlaylist.variantUrl(master, transcode.playlistUrl)
                ?: throw DownloadServerException(0)
            val playlist = HlsPlaylist.parse(repository.fetchText(variantUrl), variantUrl)
                ?: throw DownloadServerException(0)
            val total = playlist.totalSeconds
            var mediaSeconds = progress.segments.sumOf { it.seconds }
            onProgress(CompactUpdate(progress.bytes, mediaSeconds, total))

            if (progress.initBytes == 0L) {
                val init = repository.fetchBytes(playlist.initSegmentUrl, FIRST_SEGMENT_TIMEOUT_MS)
                append(part, init)
                progress = progress.copy(initBytes = init.size.toLong())
            }
            var isFirstSegment = true
            while (progress.nextSegment < playlist.segments.size) {
                currentCoroutineContext().ensureActive()
                val segment = playlist.segments[progress.nextSegment]
                val timeout = if (isFirstSegment) FIRST_SEGMENT_TIMEOUT_MS else SEGMENT_TIMEOUT_MS
                val data = repository.fetchBytes(segment.url, timeout)
                append(part, data)
                isFirstSegment = false
                progress =
                    progress.copy(
                        segments =
                            progress.segments + SavedSegment(data.size.toLong(), segment.seconds),
                    )
                saveProgress(itemId, progress)
                mediaSeconds += segment.seconds
                onProgress(CompactUpdate(progress.bytes, mediaSeconds, total))
            }
            File(folder, PLAYLIST_NAME_PART).writeText(
                localPlaylist(FILE_NAME, progress.initBytes, progress.segments),
            )
            if (!part.renameTo(File(folder, FILE_NAME))) throw IOException("Renaming failed")
            File(folder, PLAYLIST_NAME_PART).renameTo(File(folder, PLAYLIST_NAME))
            File(folder, PROGRESS_NAME).delete()
            Timber.i("Compact done: %d bytes", progress.bytes)
        } finally {
            cleanupScope.launch {
                withContext(NonCancellable) {
                    try {
                        repository.stopTranscode(transcode.playSessionId)
                    } catch (e: IOException) {
                        Timber.w(e, "Stopping the transcode failed")
                    } catch (e: DownloadServerException) {
                        Timber.w(e, "Stopping the transcode failed")
                    }
                }
            }
        }
    }

    private fun loadProgress(itemId: String): CompactProgress? =
        files.readJson(File(files.folder(itemId), PROGRESS_NAME))

    private fun saveProgress(itemId: String, progress: CompactProgress) =
        files.writeJson(File(files.folder(itemId), PROGRESS_NAME), progress)

    private fun truncate(file: File, bytes: Long) {
        RandomAccessFile(file, "rw").use { it.setLength(bytes) }
    }

    private fun append(file: File, data: ByteArray) {
        file.appendBytes(data)
    }

    companion object {
        const val FILE_NAME = "compact.mp4"
        const val PLAYLIST_NAME = "compact.m3u8"
        private const val PLAYLIST_NAME_PART = "compact.m3u8.part"
        private const val PART_NAME = "compact.part"
        private const val PROGRESS_NAME = "compact.progress.json"
        private const val MAX_WIDTH = 1920
        private const val MAX_BITRATE = 5_000_000

        // A segment normally arrives in well under a second; this long without bytes means the
        // connection is gone.
        private const val SEGMENT_TIMEOUT_MS = 20_000L

        // The first segment of a new transcode also waits for the server's disk to spin up.
        private const val FIRST_SEGMENT_TIMEOUT_MS = 60_000L
    }
}
