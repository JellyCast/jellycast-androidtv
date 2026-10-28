package nl.jellycast.core.playback

import nl.jellycast.core.model.VideoInfo
import nl.jellycast.core.testing.testPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOverridesTest {
    private fun settings(vararg pairs: Pair<PlaybackFormat, FormatSupport>) =
        PlaybackSettings(formatSupport = pairs.toMap())

    @Test
    fun `auto keeps the reading`() {
        assertEquals(
            ChromecastCapabilities,
            ChromecastCapabilities.withOverrides(
                settings(PlaybackFormat.Hdr10 to FormatSupport.Auto),
            ),
        )
    }

    @Test
    fun `off removes and on adds`() {
        val caps =
            ChromecastCapabilities.withOverrides(
                settings(
                    PlaybackFormat.DolbyVision to FormatSupport.Off,
                    PlaybackFormat.Ac3 to FormatSupport.Off,
                    PlaybackFormat.TrueHd to FormatSupport.On,
                    PlaybackFormat.Hdr10Plus to FormatSupport.On,
                ),
            )
        assertFalse(HdrType.DolbyVision in caps.hdrTypes)
        assertTrue(HdrType.Hdr10Plus in caps.hdrTypes)
        assertEquals(setOf("eac3", "truehd", "mlp"), caps.passthroughAudio)
    }

    @Test
    fun `Dolby Vision off plays the HDR10 base layer via HEVC and leaves p5 to the server`() {
        val caps = ChromecastCapabilities.withOverrides(
            settings(PlaybackFormat.DolbyVision to FormatSupport.Off),
        )
        assertFalse("DOVI" in hevcRangeTypes(caps).map { it.serialName })
        assertTrue(
            needsHevcBaseLayer(
                testPlan(
                    video = VideoInfo(
                        "hevc",
                        3840,
                        2160,
                        24f,
                        "DOVIWithHDR10",
                        8,
                    ),
                ),
                caps,
            ),
        )
    }

    @Test
    fun `the player limits passthrough only for a fallback or when the user turned a format off`() {
        assertNull(
            playerPassthrough(ChromecastCapabilities, PlaybackSettings(), PlaybackFallback()),
        )

        val ac3Off = settings(PlaybackFormat.Ac3 to FormatSupport.Off)
        val limited = ChromecastCapabilities.withOverrides(ac3Off)
        assertEquals(setOf("eac3"), playerPassthrough(limited, ac3Off, PlaybackFallback()))

        val noPassthrough = ChromecastCapabilities.limitedBy(PlaybackFallback(passthrough = false))
        assertEquals(
            emptySet<String>(),
            playerPassthrough(
                noPassthrough,
                PlaybackSettings(),
                PlaybackFallback(passthrough = false),
            ),
        )
    }

    @Test
    fun `bitstream formats are recognized`() {
        assertEquals("eac3", passthroughCodecFor("audio/eac3-joc"))
        assertEquals("dts", passthroughCodecFor("audio/vnd.dts.hd"))
        assertEquals("truehd", passthroughCodecFor("audio/true-hd"))
        assertNull(passthroughCodecFor("audio/raw"))
        assertNull(passthroughCodecFor(null))
    }
}
