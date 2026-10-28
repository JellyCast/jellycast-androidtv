package nl.jellycast.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KnownDefectsTest {
    @Test
    fun `the Chromecast has no known defects`() {
        assertEquals(ChromecastCapabilities, ChromecastCapabilities.withKnownDefects("Chromecast"))
    }

    @Test
    fun `Fire TV 4K Max has no DV with HDR10+`() {
        val fireTv = ChromecastCapabilities.withKnownDefects("AFTKRT")
        assertTrue(fireTv.dolbyVisionHdr10PlusBroken)
    }

    @Test
    fun `Shield plays p7 even though it does not report it`() {
        val shield = ChromecastCapabilities.withKnownDefects("SHIELD Android TV")
        assertTrue(7 in shield.dolbyVisionProfiles)
    }

    @Test
    fun `Shield without a DV display gets no p7`() {
        val shieldOnSdr =
            ChromecastCapabilities
                .copy(hdrTypes = emptySet())
                .withKnownDefects("SHIELD Android TV")
        assertFalse(7 in shieldOnSdr.dolbyVisionProfiles)
    }

    @Test
    fun `MediaTek overstates AVC High 10`() {
        assertTrue(overstatesAvcHigh10("OMX.MTK.VIDEO.DECODER.AVC"))
        assertTrue(overstatesAvcHigh10("c2.mtk.avc.decoder"))
        assertFalse(overstatesAvcHigh10("c2.amlogic.avc.decoder"))
    }
}
