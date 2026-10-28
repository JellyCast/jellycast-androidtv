package nl.jellycast.mobile.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalNetworkTest {
    private val homeWifi = Subnet(requireNotNull(LocalNetwork.ipv4("192.168.1.23")), 24)

    @Test
    fun `a server in the device's subnet is local`() {
        assertTrue(LocalNetwork.contains("http://192.168.1.4:8096", listOf(homeWifi)))
    }

    @Test
    fun `a server outside every subnet, or by name, is not local`() {
        assertFalse(LocalNetwork.contains("http://10.8.0.1:8096", listOf(homeWifi)))
        assertFalse(LocalNetwork.contains("https://jellyfin.example.com", listOf(homeWifi)))
        assertFalse(LocalNetwork.contains("http://192.168.1.4:8096", emptyList()))
    }

    @Test
    fun `IPv4 literals are parsed, anything else is not`() {
        assertEquals(0x0A080001, LocalNetwork.ipv4("10.8.0.1"))
        assertEquals(-1, LocalNetwork.ipv4("255.255.255.255"))
        assertNull(LocalNetwork.ipv4("256.1.1.1"))
        assertNull(LocalNetwork.ipv4("1.2.3"))
        assertNull(LocalNetwork.ipv4("example.com"))
    }

    @Test
    fun `network kind prefers local, then metered`() {
        val wifi = NetworkTraits(hasInternet = true, isCellular = false, isMetered = false)
        val hotspot = wifi.copy(isMetered = true)
        val cellular = wifi.copy(isCellular = true, isMetered = true)

        assertEquals(NetworkKind.Local, networkKindOf(cellular, serverIsLocal = true))
        assertEquals(NetworkKind.Wifi, networkKindOf(wifi, serverIsLocal = false))
        assertEquals(NetworkKind.Cellular, networkKindOf(hotspot, serverIsLocal = false))
        assertEquals(NetworkKind.Cellular, networkKindOf(cellular, serverIsLocal = false))
        assertEquals(NetworkKind.None, networkKindOf(null, serverIsLocal = false))
        assertEquals(
            NetworkKind.None,
            networkKindOf(wifi.copy(hasInternet = false), serverIsLocal = true),
        )
    }
}
