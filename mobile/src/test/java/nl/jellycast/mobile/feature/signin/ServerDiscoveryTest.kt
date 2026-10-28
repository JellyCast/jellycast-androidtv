package nl.jellycast.mobile.feature.signin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerDiscoveryTest {
    private fun ip(a: Int, b: Int, c: Int, d: Int) = (a shl 24) or (b shl 16) or (c shl 8) or d

    @Test
    fun `a slash 24 sweeps every other host`() {
        val own = ip(192, 168, 1, 23)

        val hosts = subnetHosts(own, 24)

        assertEquals(253, hosts.size)
        assertEquals(ip(192, 168, 1, 1), hosts.first())
        assertEquals(ip(192, 168, 1, 254), hosts.last())
        assertTrue(own !in hosts)
    }

    @Test
    fun `a wide subnet is capped at a slash 22`() {
        assertEquals(1021, subnetHosts(ip(10, 0, 5, 7), 16).size)
    }

    @Test
    fun `link-local addresses are not swept`() {
        assertTrue(subnetHosts(ip(169, 254, 3, 4), 16).isEmpty())
    }

    @Test
    fun `a discovery reply gives the server`() {
        assertEquals(
            DiscoveredServer("abc", "home server", "http://192.168.1.4:8096"),
            decodeDiscoveryReply(
                """{"Address":"http://192.168.1.4:8096/","Id":"abc","Name":" home server ","EndpointAddress":null}""",
            ),
        )
    }

    @Test
    fun `a reply without a name is named after its address, and junk is ignored`() {
        assertEquals(
            "http://192.168.1.4:8096",
            decodeDiscoveryReply("""{"Address":"192.168.1.4","Id":"abc"}""")?.name,
        )
        assertNull(decodeDiscoveryReply("who is JellyfinServer?"))
        assertNull(decodeDiscoveryReply("""{"Id":"abc"}"""))
    }
}
