package nl.jellycast.tv.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoInfoTest {
    private fun video(frameRate: Float, interlaced: Boolean) =
        VideoInfo("mpeg2video", 720, 576, frameRate, "SDR", null, interlaced)

    @Test
    fun `interlaced 25 or 29_97 fps plays at the field rate`() {
        assertEquals(50f, video(25f, interlaced = true).displayFrameRate)
        assertEquals(59.94f, video(29.97f, interlaced = true).displayFrameRate)
    }

    @Test
    fun `interlaced already reported as field rate stays`() {
        assertEquals(50f, video(50f, interlaced = true).displayFrameRate)
    }

    @Test
    fun `progressive keeps the frame rate`() {
        assertEquals(25f, video(25f, interlaced = false).displayFrameRate)
    }
}
