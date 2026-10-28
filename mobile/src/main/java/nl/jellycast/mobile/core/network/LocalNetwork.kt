package nl.jellycast.mobile.core.network

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.SocketException
import java.net.URI
import java.net.URISyntaxException

/** An IPv4 subnet of one of the device's own interfaces. */
data class Subnet(val address: Int, val prefixLength: Int) {
    private val mask: Int get() = if (prefixLength <= 0) 0 else -1 shl (IPV4_BITS - prefixLength)

    operator fun contains(ip: Int): Boolean = ip and mask == address and mask
}

/**
 * Whether the server sits on the same local network as this device, as iOS `LocalNetwork`: its
 * IPv4 address falls in the subnet of an up interface. Loopback and VPN tunnels don't count, and
 * neither does a host name, which would need a lookup.
 */
object LocalNetwork {
    fun contains(serverUrl: String, subnets: List<Subnet>): Boolean {
        val ip = hostOf(serverUrl)?.let(::ipv4) ?: return false
        return subnets.any { ip in it }
    }

    fun deviceSubnets(): List<Subnet> = try {
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .filter { it.isLocalCandidate() }
            .flatMap { networkInterface ->
                networkInterface.interfaceAddresses.mapNotNull { entry ->
                    val address = entry.address as? Inet4Address ?: return@mapNotNull null
                    Subnet(address.address.toIpv4(), entry.networkPrefixLength.toInt())
                }
            }
    } catch (_: SocketException) {
        emptyList()
    }

    /** A dotted IPv4 literal as a number; `null` for anything else. */
    fun ipv4(host: String): Int? {
        val parts = host.split('.')
        if (parts.size != IPV4_PARTS) return null
        val bytes = parts.map { part ->
            part.toIntOrNull()?.takeIf { it in 0..BYTE_MAX }
                ?: return null
        }
        return bytes.fold(0) { acc, byte -> acc shl BYTE_BITS or byte }
    }

    private fun hostOf(url: String): String? = try {
        URI(url).host
    } catch (_: URISyntaxException) {
        null
    }

    private fun NetworkInterface.isLocalCandidate(): Boolean = try {
        isUp && !isLoopback && !isPointToPoint && !isVirtual &&
            VpnPrefixes.none { name.startsWith(it) }
    } catch (_: SocketException) {
        false
    }
}

private fun ByteArray.toIpv4(): Int = fold(0) { acc, byte ->
    acc shl BYTE_BITS or
        (byte.toInt() and BYTE_MAX)
}

// Some VPN apps bring up a tunnel that isn't flagged point-to-point.
private val VpnPrefixes = listOf("tun", "ppp", "ipsec", "wg")

private const val IPV4_BITS = 32
private const val IPV4_PARTS = 4
private const val BYTE_BITS = 8
private const val BYTE_MAX = 255
