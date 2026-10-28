package nl.jellycast.core.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDecoderPolicyTest {
    @Test
    fun `a generic AC3 decoder gives way to FFmpeg`() {
        assertFalse(allowPlatformAudioDecoder("audio/ac3", "OMX.MTK.AUDIO.DECODER.DSPAC3", true))
        assertFalse(allowPlatformAudioDecoder("audio/eac3-joc", "c2.vendor.eac3.decoder", true))
    }

    @Test
    fun `a real Dolby decoder stays`() {
        assertTrue(allowPlatformAudioDecoder("audio/eac3", "c2.dolby.eac3.decoder", true))
        assertTrue(allowPlatformAudioDecoder("audio/ac3", "OMX.dolby.ac3.decoder", true))
    }

    @Test
    fun `without FFmpeg or for other formats the platform decoder stays`() {
        assertTrue(allowPlatformAudioDecoder("audio/ac3", "OMX.MTK.AUDIO.DECODER.DSPAC3", false))
        assertTrue(allowPlatformAudioDecoder("audio/mp4a-latm", "c2.android.aac.decoder", true))
        assertTrue(allowPlatformAudioDecoder("audio/vnd.dts", "OMX.vendor.dts.decoder", true))
    }
}
