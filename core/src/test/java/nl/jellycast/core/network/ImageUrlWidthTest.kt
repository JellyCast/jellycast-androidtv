package nl.jellycast.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageUrlWidthTest {
    @Test
    fun `the fill width is replaced and the rest of the url stays`() {
        val url = "http://server/Items/1/Images/Backdrop?fillWidth=1280&quality=85&tag=abc"

        assertEquals(
            "http://server/Items/1/Images/Backdrop?fillWidth=640&quality=85&tag=abc",
            withFillWidth(url, 640),
        )
    }

    @Test
    fun `a fill width after another parameter is replaced too`() {
        assertEquals(
            "http://server/Items/1/Images/Primary?tag=abc&fillWidth=960",
            withFillWidth("http://server/Items/1/Images/Primary?tag=abc&fillWidth=392", 960),
        )
    }

    @Test
    fun `a url without a fill width stays as it is`() {
        val url = "http://server/Items/1/Images/Primary?maxWidth=200&tag=abc"

        assertEquals(url, withFillWidth(url, 960))
    }
}
