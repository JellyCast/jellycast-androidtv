package nl.jellycast.tv.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class BufferSizeTest {
    private val default = 144_179_200 // Media3: video + audio

    @Test
    fun `buffer is half the heap, never more than the default`() {
        assertEquals(default, bufferBytesFor(512L * 1024 * 1024, default))
        assertEquals(128 * 1024 * 1024, bufferBytesFor(256L * 1024 * 1024, default))
    }

    @Test
    fun `a small heap gets a third, so the buffer fits next to the UI`() {
        assertEquals((128L * 1024 * 1024 / 3).toInt(), bufferBytesFor(128L * 1024 * 1024, default))
        assertEquals(64 * 1024 * 1024, bufferBytesFor(192L * 1024 * 1024, default))
    }
}
