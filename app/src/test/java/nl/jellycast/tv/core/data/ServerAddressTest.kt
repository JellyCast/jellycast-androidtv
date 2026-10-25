package nl.jellycast.tv.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerAddressTest {
    @Test
    fun `a bare IP gets http and the Jellyfin port`() {
        assertEquals("http://192.168.1.4:8096", normalizeServerAddress("192.168.1.4"))
    }

    @Test
    fun `an IP with port only gets http`() {
        assertEquals("http://192.168.1.4:8096", normalizeServerAddress("192.168.1.4:8096"))
    }

    @Test
    fun `spaces and a trailing slash are removed`() {
        assertEquals(
            "http://192.168.1.4:8096",
            normalizeServerAddress("  http://192.168.1.4:8096/  "),
        )
    }

    @Test
    fun `a url pasted from the browser becomes the server base`() {
        assertEquals(
            "http://192.168.1.4:8096",
            normalizeServerAddress("http://192.168.1.4:8096/web/#/home"),
        )
        assertEquals(
            "http://192.168.1.4:8096",
            normalizeServerAddress("http://192.168.1.4:8096/web/index.html"),
        )
    }

    @Test
    fun `https without port keeps the default port`() {
        assertEquals("https://jf.example.nl", normalizeServerAddress("https://jf.example.nl/"))
    }

    @Test
    fun `a base path behind a reverse proxy is kept`() {
        assertEquals(
            "https://example.nl/jellyfin",
            normalizeServerAddress("https://example.nl/jellyfin/"),
        )
    }

    @Test
    fun `an uppercase scheme is normalized`() {
        assertEquals("http://192.168.1.4:8096", normalizeServerAddress("HTTP://192.168.1.4:8096"))
    }

    @Test
    fun `invalid input gives null`() {
        assertNull(normalizeServerAddress(""))
        assertNull(normalizeServerAddress("   "))
        assertNull(normalizeServerAddress("192.168 1.4"))
        assertNull(normalizeServerAddress("ftp://192.168.1.4"))
        assertNull(normalizeServerAddress("http://"))
    }
}
