package nl.jellycast.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSegmentTest {
    private val recap = MediaSegment(SegmentType.Recap, 0, 40_000)
    private val intro = MediaSegment(SegmentType.Intro, 42_000, 90_000)
    private val outro = MediaSegment(SegmentType.Outro, 2_400_000, 2_520_000)
    private val preview = MediaSegment(SegmentType.Preview, 2_520_000, 2_580_000)
    private val segments = listOf(recap, intro, outro, preview)

    @Test
    fun `recap and intro are skippable`() {
        assertEquals(recap, segments.skippableAt(10_000))
        assertEquals(intro, segments.skippableAt(42_000))
        assertEquals(preview, segments.skippableAt(2_530_000))
    }

    @Test
    fun `nothing to skip between segments or in the last second`() {
        assertNull(segments.skippableAt(41_000))
        assertNull(segments.skippableAt(89_500))
        assertNull(segments.skippableAt(600_000))
    }

    @Test
    fun `the outro shows the next-episode card instead of a skip button`() {
        assertNull(segments.skippableAt(2_450_000))
        assertEquals(2_400_000, segments.nextCardStartMs(2_580_000))
    }

    @Test
    fun `without an outro the card shows 30 s before the end`() {
        assertEquals(2_550_000, listOf(intro).nextCardStartMs(2_580_000))
        assertEquals(2_550_000, emptyList<MediaSegment>().nextCardStartMs(2_580_000))
    }

    @Test
    fun `an outro in the first half is ignored`() {
        val wrong = listOf(MediaSegment(SegmentType.Outro, 60_000, 120_000))
        assertEquals(2_550_000, wrong.nextCardStartMs(2_580_000))
    }

    @Test
    fun `stopping in the credits counts as watched`() {
        assertTrue(segments.isFinishedAt(2_400_000, 2_580_000))
        assertFalse(segments.isFinishedAt(2_399_000, 2_580_000))
        assertTrue(emptyList<MediaSegment>().isFinishedAt(2_550_000, 2_580_000))
        assertFalse(emptyList<MediaSegment>().isFinishedAt(2_549_000, 2_580_000))
    }

    @Test
    fun `an unknown duration is never watched`() {
        assertFalse(segments.isFinishedAt(10_000, 0))
        assertFalse(segments.isFinishedAt(10_000, Long.MIN_VALUE + 1))
    }
}
