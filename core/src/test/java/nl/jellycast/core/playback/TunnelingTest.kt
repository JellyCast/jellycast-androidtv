package nl.jellycast.core.playback

import nl.jellycast.core.model.PlayMethod
import nl.jellycast.core.model.VideoInfo
import nl.jellycast.core.testing.testPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelingTest {
    private val dv8 =
        VideoInfo("hevc", 3840, 2160, 23.976f, "DOVIWithHDR10", dolbyVisionProfile = 8)
    private val hdr10 = VideoInfo("hevc", 3840, 2160, 23.976f, "HDR10", dolbyVisionProfile = null)

    @Test
    fun `auto tunnels Dolby Vision via the DV decoder on Amlogic`() {
        assertTrue(usesTunneling(FormatSupport.Auto, testPlan(video = dv8), "Amlogic"))
    }

    @Test
    fun `auto does not tunnel on another chip`() {
        assertFalse(usesTunneling(FormatSupport.Auto, testPlan(video = dv8), "Mediatek"))
    }

    @Test
    fun `auto does not tunnel HDR10, DV via HEVC or a transcode`() {
        assertFalse(usesTunneling(FormatSupport.Auto, testPlan(video = hdr10), "Amlogic"))
        assertFalse(
            usesTunneling(
                FormatSupport.Auto,
                testPlan(video = dv8).copy(decodeDolbyVisionAsHevc = true),
                "Amlogic",
            ),
        )
        assertFalse(
            usesTunneling(
                FormatSupport.Auto,
                testPlan(video = dv8, playMethod = PlayMethod.Transcode),
                "Amlogic",
            ),
        )
    }

    @Test
    fun `on and off always apply`() {
        assertTrue(usesTunneling(FormatSupport.On, testPlan(video = hdr10), "Mediatek"))
        assertFalse(usesTunneling(FormatSupport.Off, testPlan(video = dv8), "Amlogic"))
    }
}
