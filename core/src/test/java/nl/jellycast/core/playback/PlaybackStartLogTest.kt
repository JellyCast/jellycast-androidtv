package nl.jellycast.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackStartLogTest {
    private val log =
        """
        === 1 Casino
        +0 ms  plan
        === 2 Oak Street
        +0 ms  plan
        +900 ms  decoder
        === 3 Dark Matter
        """.trimIndent()

    @Test
    fun `keeps only the last starts`() {
        assertEquals(
            "=== 2 Oak Street\n+0 ms  plan\n+900 ms  decoder\n=== 3 Dark Matter\n",
            keepLastStarts(log, 2),
        )
    }

    @Test
    fun `fewer starts than the maximum are all kept`() {
        assertEquals(log + "\n", keepLastStarts(log, 10))
    }

    @Test
    fun `empty text or keeping zero gives nothing`() {
        assertEquals("", keepLastStarts("", 3))
        assertEquals("", keepLastStarts(log, 0))
    }
}
