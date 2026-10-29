package nl.jellycast.mobile.core.library

import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import nl.jellycast.core.data.repository.LibraryAccount
import timber.log.Timber

/** The last synced library on disk, so the app shows it before the server answers, or offline. */
interface LibraryCache {
    /** `null` without a file, for another account, or in an older format. */
    suspend fun load(account: LibraryAccount): LibrarySnapshot?

    suspend fun save(account: LibraryAccount, snapshot: LibrarySnapshot)

    suspend fun delete()
}

class FileLibraryCache(private val file: File, private val io: CoroutineDispatcher) :
    LibraryCache {
    override suspend fun load(account: LibraryAccount): LibrarySnapshot? = withContext(io) {
        if (!file.exists()) return@withContext null
        try {
            CacheJson
                .decodeFromString(CachedLibrary.serializer(), file.readText())
                .takeIf {
                    it.formatVersion == CACHE_FORMAT_VERSION &&
                        it.serverId == account.serverId &&
                        it.userId == account.userId
                }?.toSnapshot()
        } catch (e: IOException) {
            Timber.w(e, "Reading the library cache failed")
            null
        } catch (e: SerializationException) {
            Timber.w(e, "The library cache is unreadable")
            null
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "The library cache is unreadable")
            null
        }
    }

    override suspend fun save(account: LibraryAccount, snapshot: LibrarySnapshot) =
        withContext(io) {
            val text =
                CacheJson.encodeToString(
                    CachedLibrary.serializer(),
                    snapshot.toCached(account.serverId, account.userId),
                )
            // Written next to the old file and renamed, so a crash never leaves half a library.
            val partial = File(file.parentFile, "${file.name}.partial")
            try {
                partial.writeText(text)
                if (!partial.renameTo(file)) throw IOException("Could not replace ${file.name}")
            } catch (e: IOException) {
                Timber.w(e, "Saving the library cache failed")
                partial.delete()
            }
            Unit
        }

    override suspend fun delete() = withContext(io) {
        file.delete()
        Unit
    }
}

private val CacheJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
