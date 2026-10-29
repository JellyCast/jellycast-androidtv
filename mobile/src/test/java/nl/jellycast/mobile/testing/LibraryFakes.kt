package nl.jellycast.mobile.testing

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.core.data.repository.LibraryAccount
import nl.jellycast.core.data.repository.LibraryContent
import nl.jellycast.core.data.repository.LibrarySyncRepository
import nl.jellycast.mobile.core.library.LibraryCache
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.network.Connectivity
import nl.jellycast.mobile.core.network.NetworkKind

val TestAccount = LibraryAccount(serverId = "server-1", userId = "u1")

/** Returns [content] (or [failure]) from every fetch; [gate] holds a fetch until completed. */
class FakeLibrarySyncRepository(
    var content: LibraryContent = LibraryContent(emptyList(), emptyList(), emptyList()),
    var account: LibraryAccount? = TestAccount,
) : LibrarySyncRepository {
    var failure: Throwable? = null
    var gate: CompletableDeferred<Unit>? = null
    var fetches = 0

    override suspend fun account(): LibraryAccount? = account

    override suspend fun fetchLibrary(): Result<LibraryContent> {
        fetches++
        gate?.await()
        return failure?.let { Result.failure(it) } ?: Result.success(content)
    }
}

class FakeLibraryCache(var stored: Pair<LibraryAccount, LibrarySnapshot>? = null) : LibraryCache {
    var deletes = 0

    override suspend fun load(account: LibraryAccount): LibrarySnapshot? =
        stored?.takeIf { it.first == account }?.second

    override suspend fun save(account: LibraryAccount, snapshot: LibrarySnapshot) {
        stored = account to snapshot
    }

    override suspend fun delete() {
        deletes++
        stored = null
    }
}

class FakeConnectivity(offline: Boolean = false) : Connectivity {
    override val networkKind = MutableStateFlow(NetworkKind.Wifi)
    override val isServerReachable = MutableStateFlow(!offline)
    override val isOffline = MutableStateFlow(offline)

    override suspend fun checkServer(): Boolean = isServerReachable.value
}
