package nl.jellycast.tv.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDecoderPolicyTest {
    private data class Decoder(val name: String, val softwareOnly: Boolean)

    private val hardware = Decoder("c2.amlogic.hevc.decoder", softwareOnly = false)
    private val software = Decoder("c2.android.hevc.decoder", softwareOnly = true)

    @Test
    fun `a software decoder is dropped next to a hardware decoder`() {
        assertEquals(
            listOf(hardware),
            withoutSoftwareVideoFallback(listOf(hardware, software)) {
                it.softwareOnly
            },
        )
    }

    @Test
    fun `only software decoders are kept`() {
        assertEquals(
            listOf(software),
            withoutSoftwareVideoFallback(listOf(software)) {
                it.softwareOnly
            },
        )
    }

    @Test
    fun `an empty list stays empty`() {
        assertEquals(
            emptyList<Decoder>(),
            withoutSoftwareVideoFallback(emptyList<Decoder>()) {
                it.softwareOnly
            },
        )
    }
}
