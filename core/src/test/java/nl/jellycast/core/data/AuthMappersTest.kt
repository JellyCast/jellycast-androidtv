package nl.jellycast.core.data

import nl.jellycast.core.datastore.StoredSession
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.ServerInfo
import org.jellyfin.sdk.model.api.ServerDiscoveryInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthMappersTest {
    private val server = ServerInfo(url = "http://192.168.1.4:8096", name = "home server")

    @Test
    fun `without a server url the state is no server`() {
        assertEquals(AuthState.NoServer, StoredSession().toAuthState())
    }

    @Test
    fun `a server without token is logged out`() {
        val session = StoredSession(serverUrl = server.url, serverName = server.name)
        assertEquals(AuthState.LoggedOut(server), session.toAuthState())
    }

    @Test
    fun `a server with token and user is logged in`() {
        val session =
            StoredSession(
                serverUrl = server.url,
                serverName = server.name,
                userId = "abc",
                userName = "claude",
                accessToken = "token",
            )
        assertEquals(AuthState.LoggedIn(server, "claude"), session.toAuthState())
    }

    @Test
    fun `a missing server name falls back to the url`() {
        val session = StoredSession(serverUrl = server.url)
        assertEquals(AuthState.LoggedOut(server.copy(name = server.url)), session.toAuthState())
    }

    @Test
    fun `server versions from 10_11 are supported`() {
        assertTrue(isSupportedServerVersion("10.11.11"))
        assertTrue(isSupportedServerVersion("10.11.0"))
        assertFalse(isSupportedServerVersion("10.10.7"))
        assertFalse(isSupportedServerVersion("onzin"))
        assertFalse(isSupportedServerVersion(""))
    }

    @Test
    fun `a discovered server gets the normalized address and its name`() {
        val info =
            ServerDiscoveryInfo(
                address = "http://192.168.1.4:8096/",
                id = "a1",
                name = "home server",
            )
        assertEquals(server, info.toServerInfo())
    }

    @Test
    fun `a discovered server without a name is named after its address`() {
        val info = ServerDiscoveryInfo(address = "http://192.168.1.4:8096", id = "a1", name = " ")
        assertEquals(ServerInfo(url = server.url, name = server.url), info.toServerInfo())
    }

    @Test
    fun `a discovered server with an invalid address is dropped`() {
        val info = ServerDiscoveryInfo(address = "", id = "a1", name = "home server")
        assertNull(info.toServerInfo())
    }
}
