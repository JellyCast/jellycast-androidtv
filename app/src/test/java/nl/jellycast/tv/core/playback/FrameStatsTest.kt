package nl.jellycast.tv.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FrameStatsTest {
    @Test
    fun `everything shown gives zero`() {
        val stats = FrameStats()
        repeat(100) {
            stats.onReleased()
            stats.onRendered()
        }
        assertEquals(0L, stats.notShown)
        assertEquals("0 of 100 frames not shown", stats.summary())
    }

    @Test
    fun `unreported frames count as not shown, except the last few`() {
        val stats = FrameStats()
        repeat(100) { stats.onReleased() }
        repeat(90) { stats.onRendered() }
        assertEquals(7L, stats.notShown)
    }

    @Test
    fun `tunneling has no count`() {
        val stats = FrameStats()
        stats.markTunneled()
        stats.onReleased()
        assertNull(stats.summary())
    }

    @Test
    fun `without frames there is no count`() {
        assertNull(FrameStats().summary())
    }
}
