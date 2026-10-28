package nl.jellycast.tv.feature.settings

import nl.jellycast.core.playback.BasicCapabilities
import nl.jellycast.core.playback.ChromecastCapabilities
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceInfoMapperTest {
    @Test
    fun `chromecast shows HEVC Main10, DV profiles and AC3 and EAC3`() {
        assertEquals(
            DeviceInfo(
                video = listOf("HEVC Main10", "H.264", "VP9", "MPEG-2", "MPEG-4"),
                maxResolution = "4K",
                hdr = listOf("Dolby Vision (4, 5, 8)", "HDR10", "HLG"),
                passthrough = listOf("AC3", "EAC3"),
                maxPcmChannels = 8,
            ),
            ChromecastCapabilities.toDeviceInfo(),
        )
    }

    @Test
    fun `DTS and TrueHD are listed once, also with dca and mlp`() {
        val info =
            BasicCapabilities
                .copy(passthroughAudio = setOf("dts", "dca", "truehd", "mlp"))
                .toDeviceInfo()

        assertEquals(listOf("DTS", "TrueHD"), info.passthrough)
    }

    @Test
    fun `basic device without HDR and passthrough`() {
        val info = BasicCapabilities.toDeviceInfo()

        assertEquals(listOf("H.264"), info.video)
        assertEquals(emptyList<String>(), info.hdr)
        assertEquals(emptyList<String>(), info.passthrough)
    }
}
