package nl.jellycast.mobile.core.downloads

import java.io.File
import java.io.IOException
import nl.jellycast.core.data.repository.DownloadRepository
import timber.log.Timber

/**
 * Fetches the original file into `original.part`, going on from its length with a range
 * request after a pause or a dropped connection, and names it after the server's extension.
 */
class OriginalDownloader(
    private val repository: DownloadRepository,
    private val files: DownloadFiles,
) {
    suspend fun run(itemId: String, onProgress: (bytes: Long, totalBytes: Long?) -> Unit): Long {
        val folder = files.prepareFolder(itemId)
        val part = File(folder, PART_NAME)
        val extension = repository.downloadOriginal(itemId, part, onProgress)
        val target = File(folder, FILE_PREFIX + extension)
        target.delete()
        if (!part.renameTo(target)) throw IOException("Renaming the original failed")
        Timber.i("Original done: %d bytes", target.length())
        return target.length()
    }

    companion object {
        const val FILE_PREFIX = "original."
        const val PART_NAME = "original.part"
    }
}
