package nl.jellycast.core.playback

import nl.jellycast.core.model.AudioResult
import nl.jellycast.core.model.AudioTrack
import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.VideoInfo
import nl.jellycast.core.model.VideoResult
import nl.jellycast.core.testing.testPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOutcomesTest {
    private val truehdAtmos =
        AudioTrack(1, "eng", "truehd", 8, "TrueHD Atmos", true, isAtmos = true)
    private val eac3Atmos = AudioTrack(2, "eng", "eac3", 8, "DD+ Atmos", false, isAtmos = true)
    private val dts = AudioTrack(1, "eng", "dts", 6, "DTS-HD MA", true)
    private val dv7 = VideoInfo("hevc", 3840, 2160, 23.976f, "DOVIWithEL", dolbyVisionProfile = 7)
    private val vc1 = VideoInfo("vc1", 1920, 1080, 23.976f, "SDR", dolbyVisionProfile = null)

    @Test
    fun `DV p7 plays as the HDR10 base layer and EAC3 Atmos passes through unchanged`() {
        val plan =
            testPlan(
                video = dv7,
                audioTracks = listOf(truehdAtmos, eac3Atmos),
                audioStreamIndex = 2,
            )
                .copy(decodeDolbyVisionAsHevc = true)
        val outcome = summarizePlayback(plan, ChromecastCapabilities)

        assertEquals(VideoResult.DolbyVisionBaseLayer, outcome.video?.result)
        val audio = outcome.audio!!
        assertEquals(AudioResult.Passthrough, audio.result)
        assertEquals(truehdAtmos, audio.source)
        assertEquals(eac3Atmos, audio.played)
        assertFalse("EAC3 Atmos via passthrough keeps Atmos", audio.losesAtmos)
    }

    @Test
    fun `TrueHD Atmos decoded locally gives PCM 7_1 and loses Atmos`() {
        val plan = testPlan(audioTracks = listOf(truehdAtmos), audioStreamIndex = 1)
        val audio = summarizePlayback(plan, ChromecastCapabilities).audio!!

        assertEquals(AudioResult.DecodedLocally, audio.result)
        assertEquals(8, audio.pcmChannels)
        assertTrue(audio.losesAtmos)
    }

    @Test
    fun `with only audio transcoded the video stays as in the file`() {
        val plan =
            testPlan(
                playMethod = PlayMethod.Transcode,
                video = vc1.copy(codec = "h264"),
                audioTracks = listOf(dts),
                audioStreamIndex = 1,
                transcodeReasons = listOf("AudioCodecNotSupported"),
            )
        val outcome = summarizePlayback(plan, ChromecastCapabilities)

        assertEquals(VideoResult.AsIs, outcome.video?.result)
        assertEquals(AudioResult.ConvertedByServer, outcome.audio?.result)
    }

    @Test
    fun `video transcoded by the server`() {
        val plan =
            testPlan(
                playMethod = PlayMethod.Transcode,
                video = vc1,
                audioTracks = listOf(dts.copy(codec = "ac3")),
                audioStreamIndex = 1,
                transcodeReasons = listOf("VideoCodecNotSupported"),
            )
        val outcome = summarizePlayback(plan, ChromecastCapabilities)

        assertEquals(VideoResult.ConvertedByServer, outcome.video?.result)
        assertEquals(AudioResult.Passthrough, outcome.audio?.result)
    }

    @Test
    fun `a container-only change keeps video and audio unchanged`() {
        val plan =
            testPlan(
                playMethod = PlayMethod.DirectStream,
                video = vc1.copy(codec = "h264"),
                audioTracks = listOf(eac3Atmos.copy(isDefault = true)),
                audioStreamIndex = 2,
                transcodeReasons = listOf("ContainerNotSupported"),
            )
        val outcome = summarizePlayback(plan, ChromecastCapabilities)

        assertEquals(VideoResult.AsIs, outcome.video?.result)
        assertEquals(AudioResult.Passthrough, outcome.audio?.result)
    }

    @Test
    fun `with passthrough off EAC3 is decoded locally`() {
        val plan =
            testPlan(audioTracks = listOf(eac3Atmos.copy(isDefault = true)), audioStreamIndex = 2)
                .copy(passthroughAudio = emptySet())
        val audio = summarizePlayback(plan, ChromecastCapabilities).audio!!

        assertEquals(AudioResult.DecodedLocally, audio.result)
        assertTrue(audio.losesAtmos)
    }
}
