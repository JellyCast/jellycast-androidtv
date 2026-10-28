package nl.jellycast.mobile.core.downloads

import android.os.StatFs
import java.io.File
import java.io.IOException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * The files of the downloads: one folder per item under [root], and the list itself as
 * `index.json` next to them. The app's own storage is never backed up (data extraction rules).
 */
class DownloadFiles(val root: File) {
    fun folder(itemId: String): File = File(root, itemId)

    fun prepareFolder(itemId: String): File = folder(itemId).also { it.mkdirs() }

    /**
     * The finished download of an item: the Compact playlist over `compact.mp4`, or the
     * original file.
     */
    fun localFile(itemId: String): File? {
        val folder = folder(itemId)
        val names = folder.list().orEmpty()
        val name =
            names.firstOrNull { it == CompactDownloader.PLAYLIST_NAME }
                ?: names.firstOrNull {
                    it.startsWith(OriginalDownloader.FILE_PREFIX) &&
                        it != OriginalDownloader.PART_NAME
                }
        return name?.let { File(folder, it) }
    }

    /** What a finished download takes on disk. */
    fun finishedBytes(itemId: String): Long = folder(itemId).listFiles().orEmpty()
        .filter {
            it.name == CompactDownloader.FILE_NAME ||
                it.name.startsWith(OriginalDownloader.FILE_PREFIX)
        }
        .sumOf { it.length() }

    fun remove(itemId: String) {
        folder(itemId).deleteRecursively()
    }

    fun loadIndex(): List<Download> = readJson(File(root, INDEX_NAME)) ?: emptyList()

    fun saveIndex(downloads: List<Download>) = writeJson(File(root, INDEX_NAME), downloads)

    internal inline fun <reified T> readJson(file: File): T? {
        if (!file.exists()) return null
        return try {
            DownloadJson.decodeFromString<T>(file.readText())
        } catch (e: IOException) {
            Timber.w(e, "Reading %s failed", file.name)
            null
        } catch (e: SerializationException) {
            Timber.w(e, "Reading %s failed", file.name)
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Reading %s failed", file.name)
            null
        }
    }

    internal inline fun <reified T> writeJson(file: File, value: T) {
        try {
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(DownloadJson.encodeToString(value))
            if (!temporary.renameTo(file)) throw IOException("Renaming ${temporary.name} failed")
        } catch (e: IOException) {
            Timber.e(e, "Saving %s failed", file.name)
        }
    }

    companion object {
        private const val INDEX_NAME = "index.json"
    }
}

internal val DownloadJson = Json { ignoreUnknownKeys = true }

/** Total and free space of the volume the downloads are on. */
fun interface StorageSpace {
    fun read(): VolumeSpace?
}

class StatFsStorageSpace(private val root: File) : StorageSpace {
    override fun read(): VolumeSpace? = try {
        root.mkdirs()
        val stat = StatFs(root.path)
        VolumeSpace(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
    } catch (e: IllegalArgumentException) {
        Timber.w(e, "No storage info")
        null
    }
}
