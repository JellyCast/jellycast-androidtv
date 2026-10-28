package nl.jellycast.core.data.repository

import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.SegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class DebugSegmentsTest {
    @Test
    fun `reads types and times in seconds and skips garbage`() {
        assertEquals(
            listOf(
                MediaSegment(SegmentType.Intro, 5_000, 60_000),
                MediaSegment(SegmentType.Outro, 1_650_000, 1_721_000),
            ),
            parseDebugSegments("intro:5-60, outro:1650-1721, kapot, onbekend:1-2"),
        )
    }
}
