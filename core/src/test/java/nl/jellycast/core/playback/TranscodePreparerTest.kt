package nl.jellycast.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class TranscodePreparerTest {
    @Test
    fun `the maximum width is added to the transcode url`() {
        assertEquals(
            "http://s/videos/1/main.m3u8?a=1&MaxWidth=1920",
            "http://s/videos/1/main.m3u8?a=1".withMaxWidth(1920),
        )
        assertEquals("http://s/main.m3u8?MaxWidth=1280", "http://s/main.m3u8".withMaxWidth(1280))
    }
}
