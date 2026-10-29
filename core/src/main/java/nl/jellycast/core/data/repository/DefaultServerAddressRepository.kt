package nl.jellycast.core.data.repository

import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.normalizeServerAddress
import nl.jellycast.core.data.pickAddress
import nl.jellycast.core.datastore.SessionStore
import nl.jellycast.core.datastore.StoredSession
import nl.jellycast.core.model.AddAddressResult
import nl.jellycast.core.model.ServerAddresses
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.HttpClientOptions
import org.jellyfin.sdk.api.client.exception.ApiClientException
import org.jellyfin.sdk.api.client.extensions.systemApi
import org.jellyfin.sdk.model.api.PublicSystemInfo
import timber.log.Timber

/** The addresses of the signed-in server, and which one the shared [ApiClient] uses. */
interface ServerAddressRepository {
    val addresses: Flow<ServerAddresses>

    suspend fun add(input: String): AddAddressResult

    /** Uses [address] until the next server check; `false` when it doesn't answer as this server. */
    suspend fun use(address: String): Boolean

    /** Saves the new order or removal; the next server check applies it. */
    suspend fun save(addresses: List<String>)
}

/**
 * Every server check asks all addresses and uses the first in the user's order that answers as
 * this server, so at home the LAN wins even when a VPN address answers there too.
 */
class DefaultServerAddressRepository(
    private val jellyfin: Jellyfin,
    private val api: ApiClient,
    private val store: SessionStore,
    private val dispatchers: AppDispatchers,
) : ServerStatusRepository,
    ServerAddressRepository {
    // One client per address: the shared one waits far longer than this check may.
    private val checkClients = mutableMapOf<String, ApiClient>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override val addresses: Flow<ServerAddresses> = store.session.flatMapLatest { session ->
        val current = session.serverUrl
        val serverId = session.serverId
        if (serverId == null) {
            flowOf(ServerAddresses(listOfNotNull(current), current))
        } else {
            store.addresses(serverId).map { ServerAddresses(it.withCurrent(current), current) }
        }
    }

    override suspend fun isReachable(): Boolean = withContext(dispatchers.io) {
        // From the store, not the shared client: the first check can run before the session has
        // set the address there. Without a server there is nothing to check; that is not offline.
        val session = store.session.first()
        val current = session.serverUrl ?: return@withContext true
        val serverId = serverId(session, current) ?: return@withContext false
        val addresses = store.addresses(serverId).first().withCurrent(current)
        val picked =
            pickAddress(addresses, PREFERRED_GRACE_MS) { infoAt(it)?.id == serverId }
                ?: return@withContext false
        switchTo(picked)
        true
    }

    override suspend fun add(input: String): AddAddressResult = withContext(dispatchers.io) {
        val url =
            normalizeServerAddress(input) ?: return@withContext AddAddressResult.InvalidAddress
        val session = store.session.first()
        val current = session.serverUrl ?: return@withContext AddAddressResult.Unreachable
        val serverId = serverId(session, current) ?: return@withContext AddAddressResult.Unreachable
        val known = store.addresses(serverId).first().withCurrent(current)
        if (url in known) return@withContext AddAddressResult.AlreadyListed
        val info = infoAt(url) ?: return@withContext AddAddressResult.Unreachable
        if (info.id != serverId) {
            return@withContext AddAddressResult.OtherServer(info.serverName ?: url)
        }
        store.saveAddresses(serverId, known + url)
        AddAddressResult.Added
    }

    override suspend fun use(address: String): Boolean = withContext(dispatchers.io) {
        val serverId = store.session.first().serverId ?: return@withContext false
        if (infoAt(address)?.id != serverId) return@withContext false
        switchTo(address)
        true
    }

    override suspend fun save(addresses: List<String>) {
        val serverId = store.session.first().serverId ?: return
        withContext(dispatchers.io) { store.saveAddresses(serverId, addresses) }
    }

    private suspend fun switchTo(address: String) {
        if (address == api.baseUrl && address == store.session.first().serverUrl) return
        Timber.i("Server address switched")
        api.update(baseUrl = address)
        store.saveServerAddress(address)
    }

    /** Sessions from before the address list have no server id; it is asked once and kept. */
    private suspend fun serverId(session: StoredSession, current: String): String? {
        session.serverId?.let { return it }
        val id = infoAt(current)?.id ?: return null
        store.saveServerId(id)
        if (store.addresses(id).first().isEmpty()) store.saveAddresses(id, listOf(current))
        return id
    }

    private suspend fun infoAt(address: String): PublicSystemInfo? = try {
        clientFor(address).systemApi.getPublicSystemInfo().content
    } catch (e: ApiClientException) {
        Timber.i(e, "Server check failed")
        null
    }

    @Synchronized
    private fun clientFor(address: String): ApiClient = checkClients.getOrPut(address) {
        jellyfin.createApi(baseUrl = address, httpClientOptions = ServerCheckOptions)
    }
}

private fun List<String>.withCurrent(current: String?): List<String> =
    if (current == null || current in this) this else this + current

// A server on the LAN answers in milliseconds; this is how long a VPN address waits for it.
private const val PREFERRED_GRACE_MS = 1_500L

private val ServerCheckOptions =
    HttpClientOptions(connectTimeout = 5.seconds, requestTimeout = 5.seconds)
