package nl.jellycast.core.playback

import kotlin.math.roundToLong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FramePacerTest {
    /** 23.976 Hz, the display mode for a 24p movie. */
    private val vsync = (1_000_000_000 / 23.976).roundToLong()

    /** Like Media3: the time rounded to the vsync grid, shifted by [phaseNs]. */
    private fun snapped(timesNs: List<Long>, phaseNs: Long = 0, vsyncNs: Long = vsync) =
        timesNs.map {
            ((it + phaseNs).toDouble() / vsyncNs).roundToLong() * vsyncNs
        }

    private fun paceAll(releases: List<Long>, vsyncNs: Long = vsync): List<Long> {
        val pacer = FramePacer()
        return releases.map { pacer.pace(it, vsyncNs) }
    }

    /** MKV timestamps of a 23.976 fps movie: whole milliseconds (41 and 42 ms). */
    private fun mkv24p(frames: Int) = List(frames) { i ->
        (i * 1000.0 / 23.976).roundToLong() * 1_000_000
    }

    @Test
    fun `rounded MKV times land on the same vsync with an unlucky phase`() {
        val releases = snapped(mkv24p(480), phaseNs = vsync / 2)
        assertTrue(releases.toSet().size < releases.size)
    }

    @Test
    fun `with pacing every frame gets its own vsync`() {
        for (phase in listOf(0L, vsync / 4, vsync / 2, vsync * 3 / 4)) {
            val paced = paceAll(snapped(mkv24p(480), phaseNs = phase))
            assertEquals(480, paced.toSet().size)
            paced.zipWithNext().forEach { (a, b) -> assertTrue(b - a >= vsync) }
        }
    }

    @Test
    fun `a frame moves at most one vsync`() {
        val releases = snapped(mkv24p(2_000), phaseNs = vsync / 2)
        releases.zip(paceAll(releases)).forEach { (wanted, actual) ->
            assertTrue(actual - wanted in 0..vsync)
        }
    }

    @Test
    fun `a regular rhythm is left alone`() {
        val releases = List(200) { it * vsync }
        assertEquals(releases, paceAll(releases))
    }

    @Test
    fun `a slower source is left alone`() {
        // 25 fps on 50 Hz: already two vsyncs apart.
        val v50 = 20_000_000L
        val releases = List(200) { it * 2 * v50 }
        assertEquals(releases, paceAll(releases, v50))
    }

    @Test
    fun `a source faster than the display does not fall behind for good`() {
        // 60 fps on 59.94 Hz: about one frame too many per 1000.
        val v5994 = 16_683_333L
        val releases = snapped(List(36_000) { (it * 1e9 / 60).roundToLong() }, vsyncNs = v5994)
        val paced = paceAll(releases, v5994)
        val shifted = releases.zip(paced).count { (wanted, actual) -> wanted != actual }
        assertTrue(shifted < releases.size / 100)
        releases.zip(paced).forEach { (wanted, actual) -> assertTrue(actual - wanted <= v5994) }
    }

    @Test
    fun `after a pause or seek the previous frame does not count`() {
        val pacer = FramePacer()
        pacer.pace(0, vsync)
        assertEquals(5_000_000_000L, pacer.pace(5_000_000_000L, vsync))
    }

    @Test
    fun `without a known vsync the times stay unchanged`() {
        val releases = snapped(mkv24p(100), phaseNs = vsync / 2)
        assertEquals(releases, paceAll(releases, vsyncNs = 0))
    }

    @Test
    fun `after too many shifts in a row the next frame keeps its own time`() {
        val pacer = FramePacer()
        pacer.pace(0, vsync)
        repeat(FramePacer.MAX_SHIFTS_IN_A_ROW) { i ->
            assertEquals((i + 1) * vsync, pacer.pace(i * vsync, vsync))
        }
        val wanted = FramePacer.MAX_SHIFTS_IN_A_ROW * vsync
        assertEquals(wanted, pacer.pace(wanted, vsync))
    }

    @Test
    fun `a frame just before the next vsync stays put`() {
        val pacer = FramePacer()
        pacer.pace(0, vsync)
        val almostNext = vsync - 50_000
        assertEquals(almostNext, pacer.pace(almostNext, vsync))
    }
}
