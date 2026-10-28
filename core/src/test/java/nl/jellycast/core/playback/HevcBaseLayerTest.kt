package nl.jellycast.core.playback

import nl.jellycast.core.model.VideoInfo
import nl.jellycast.core.testing.testPlan
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HevcBaseLayerTest {
    private fun dv(profile: Int) = testPlan(
        video = VideoInfo("hevc", 3840, 2160, 23.976f, "DOVIWithEL", dolbyVisionProfile = profile),
    )

    @Test
    fun `profile 7 without a p7 decoder plays the HDR10 base layer`() {
        assertTrue(needsHevcBaseLayer(dv(7), ChromecastCapabilities))
    }

    @Test
    fun `profile 7 with a p7 decoder and a DV display goes to the DV decoder`() {
        val shield = ChromecastCapabilities.copy(dolbyVisionProfiles = setOf(5, 7, 8))
        assertFalse(needsHevcBaseLayer(dv(7), shield))
    }

    @Test
    fun `profile 7 with a p7 decoder but a display without DV plays HDR10`() {
        val shieldOnHdr10Tv =
            ChromecastCapabilities.copy(
                dolbyVisionProfiles = setOf(5, 7, 8),
                hdrTypes = setOf(HdrType.Hdr10),
            )
        assertTrue(needsHevcBaseLayer(dv(7), shieldOnHdr10Tv))
    }

    @Test
    fun `other profiles are left to Media3`() {
        assertFalse(needsHevcBaseLayer(dv(8), ChromecastCapabilities))
    }
}
