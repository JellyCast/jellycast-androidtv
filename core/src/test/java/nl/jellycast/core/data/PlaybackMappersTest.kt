package nl.jellycast.core.data

import nl.jellycast.core.data.mapper.toMediaSegment
import nl.jellycast.core.data.mapper.transcodeReasons
import nl.jellycast.core.model.MediaSegment
import nl.jellycast.core.model.SegmentType
import nl.jellycast.core.model.formatDuration
import nl.jellycast.core.model.msToTicks
import nl.jellycast.core.model.ticksToMs
import org.jellyfin.sdk.model.api.MediaSegmentDto
import org.jellyfin.sdk.model.api.MediaSegmentType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackMappersTest {
    @Test
    fun `ticks and milliseconds`() {
        assertEquals(1_000L, 10_000_000L.ticksToMs())
        assertEquals(10_000_000L, 1_000L.msToTicks())
        assertEquals(123_456L, 123_456L.msToTicks().ticksToMs())
    }

    @Test
    fun `transcode reasons from the url`() {
        val url =
            "/videos/abc/master.m3u8?DeviceId=x" +
                "&TranscodeReasons=VideoCodecNotSupported%2CAudioCodecNotSupported&api_key=y"
        assertEquals(
            listOf("VideoCodecNotSupported", "AudioCodecNotSupported"),
            transcodeReasons(url),
        )
        assertEquals(
            listOf("ContainerBitrateExceedsLimit"),
            transcodeReasons("/x?TranscodeReasons=ContainerBitrateExceedsLimit"),
        )
    }

    @Test
    fun `no reasons without transcoding`() {
        assertEquals(emptyList<String>(), transcodeReasons(null))
        assertEquals(emptyList<String>(), transcodeReasons("/videos/abc/stream?static=true"))
    }

    @Test
    fun `readable duration`() {
        assertEquals("0:05", formatDuration(5_000))
        assertEquals("12:34", formatDuration(754_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun `server segments become models and unknown types are dropped`() {
        fun dto(type: MediaSegmentType, start: Long, end: Long) = MediaSegmentDto(
            id = java.util.UUID.randomUUID(),
            itemId = java.util.UUID.randomUUID(),
            type = type,
            startTicks = start,
            endTicks = end,
        )
        assertEquals(
            MediaSegment(SegmentType.Intro, 42_000, 90_000),
            dto(MediaSegmentType.INTRO, 420_000_000, 900_000_000).toMediaSegment(),
        )
        assertEquals(null, dto(MediaSegmentType.UNKNOWN, 0, 10).toMediaSegment())
        assertEquals(null, dto(MediaSegmentType.OUTRO, 10, 10).toMediaSegment())
    }
}
