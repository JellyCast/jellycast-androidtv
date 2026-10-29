package nl.jellycast.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerImagePathTest {
    @Test
    fun `a jellyfin image keeps only its path`() {
        assertEquals(
            "/Items/abc/Images/Backdrop?tag=1",
            serverImagePath("http://192.168.1.4:8096/Items/abc/Images/Backdrop?tag=1"),
        )
    }

    @Test
    fun `an image from elsewhere is not a server image`() {
        assertNull(serverImagePath("https://peach.blender.org/wp-content/uploads/title.jpg"))
    }
}
