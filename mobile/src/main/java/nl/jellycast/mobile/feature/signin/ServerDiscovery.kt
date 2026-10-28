package nl.jellycast.mobile.feature.signin

import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.data.normalizeServerAddress
import timber.log.Timber

data class DiscoveredServer(val id: String, val name: String, val url: String)

/** Finds Jellyfin servers on the local network through Jellyfin's UDP discovery (port 7359). */
fun interface ServerDiscovery {
    /** Emits each server once and completes after a few seconds. */
    fun search(): Flow<DiscoveredServer>
}

/**
 * Asks by broadcast and, because some access points drop broadcasts, also unicast to every
 * address in the local subnets (capped at a /22), as the iOS app does.
 */
class UdpServerDiscovery(private val dispatchers: AppDispatchers) : ServerDiscovery {
    override fun search(): Flow<DiscoveredServer> = flow {
        try {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                sweep(socket)
            }
        } catch (e: IOException) {
            Timber.w(e, "Server discovery failed")
        }
    }.flowOn(dispatchers.io)

    private suspend fun FlowCollector<DiscoveredServer>.sweep(socket: DatagramSocket) {
        val seen = mutableSetOf<String>()
        val report: suspend (List<DiscoveredServer>) -> Unit = { servers ->
            servers.filter { seen.add(it.id) }.forEach { emit(it) }
        }
        val targets = listOf(BROADCAST) + localHosts()
        targets.forEachIndexed { index, host ->
            send(socket, host)
            if (index % SEND_BATCH_SIZE == SEND_BATCH_SIZE - 1) {
                report(receive(socket, QUICK_RECEIVE_MS))
                delay(SEND_BATCH_PAUSE_MS)
            }
        }
        val deadline = System.nanoTime() + LISTEN_MS * NANOS_PER_MS
        while (System.nanoTime() < deadline) {
            currentCoroutineContext().ensureActive()
            report(receive(socket, LISTEN_RECEIVE_MS))
        }
    }

    private fun send(socket: DatagramSocket, host: Int) {
        val packet = DatagramPacket(Question, Question.size, host.toInetAddress(), PORT)
        try {
            socket.send(packet)
        } catch (e: IOException) {
            Timber.v(e, "Discovery send failed")
        }
    }

    /** Everything that arrived, waiting up to [timeoutMs] for each reply. */
    private fun receive(socket: DatagramSocket, timeoutMs: Int): List<DiscoveredServer> {
        socket.soTimeout = timeoutMs
        val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
        val servers = mutableListOf<DiscoveredServer>()
        while (true) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                socket.receive(packet)
            } catch (_: SocketTimeoutException) {
                return servers
            }
            decodeDiscoveryReply(String(packet.data, 0, packet.length, Charsets.UTF_8))
                ?.let(servers::add)
        }
    }

    private fun localHosts(): List<Int> {
        val interfaces = NetworkInterface.getNetworkInterfaces()?.toList().orEmpty()
        return interfaces
            .filter { it.isUp && !it.isLoopback && !it.isPointToPoint }
            .flatMap { it.interfaceAddresses }
            .mapNotNull { address ->
                (address.address as? Inet4Address)?.let {
                    it.toInt() to address.networkPrefixLength
                }
            }
            .flatMap { (own, prefix) -> subnetHosts(own, prefix.toInt()) }
            .distinct()
    }

    private companion object {
        const val PORT = 7359
        val Question = "who is JellyfinServer?".toByteArray(Charsets.UTF_8)
        const val BROADCAST = -1 // 255.255.255.255
        const val SEND_BATCH_SIZE = 64
        const val SEND_BATCH_PAUSE_MS = 5L
        const val QUICK_RECEIVE_MS = 1
        const val LISTEN_RECEIVE_MS = 50
        const val LISTEN_MS = 2_000L
        const val NANOS_PER_MS = 1_000_000L
        const val RECEIVE_BUFFER_SIZE = 4096
    }
}

/** Caps a sweep at a /22 (1022 hosts). */
private const val NARROWEST_SWEEP_PREFIX = 22
private const val LINK_LOCAL_NETWORK = 0xA9FE0000.toInt()
private const val LINK_LOCAL_MASK = 0xFFFF0000.toInt()
private const val IPV4_BITS = 32

/** Every host address in [own]'s subnet except [own] itself; empty for link-local addresses. */
internal fun subnetHosts(own: Int, prefixLength: Int): List<Int> {
    if (own and LINK_LOCAL_MASK == LINK_LOCAL_NETWORK) return emptyList()
    val prefix = prefixLength.coerceIn(NARROWEST_SWEEP_PREFIX, IPV4_BITS)
    if (prefix >= IPV4_BITS - 1) return emptyList()
    val mask = -1 shl (IPV4_BITS - prefix)
    val network = own and mask
    val broadcast = network or mask.inv()
    return ((network + 1) until broadcast).filter { it != own }
}

private val DiscoveryJson = Json { ignoreUnknownKeys = true }

/** The reply is `{"Address": "http://…:8096", "Id": "…", "Name": "…"}`. */
internal fun decodeDiscoveryReply(text: String): DiscoveredServer? {
    val reply =
        try {
            DiscoveryJson.parseToJsonElement(text) as? JsonObject
        } catch (_: SerializationException) {
            null
        } ?: return null
    fun field(name: String) = (reply[name] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val id = field("Id") ?: return null
    val url = field("Address")?.let(::normalizeServerAddress) ?: return null
    val name = field("Name")?.trim().orEmpty()
    return DiscoveredServer(id = id, name = name.ifEmpty { url }, url = url)
}

private fun Inet4Address.toInt(): Int =
    address.fold(0) { acc, byte -> (acc shl BYTE_BITS) or (byte.toInt() and BYTE_MASK) }

private fun Int.toInetAddress(): InetAddress = InetAddress.getByAddress(
    byteArrayOf(
        (this ushr 24).toByte(),
        (this ushr 16).toByte(),
        (this ushr BYTE_BITS).toByte(),
        toByte(),
    ),
)

private const val BYTE_BITS = 8
private const val BYTE_MASK = 0xFF
