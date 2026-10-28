package nl.jellycast.core.playback

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackMenuTest {
    @Test
    fun `MKV subtitles show the original format`() {
        val cues = MimeTypes.APPLICATION_MEDIA3_CUES
        assertEquals("PGS", codecName(cues, MimeTypes.APPLICATION_PGS))
        assertEquals("SRT", codecName(cues, MimeTypes.APPLICATION_SUBRIP))
        assertEquals("ASS", codecName(cues, MimeTypes.TEXT_SSA))
    }

    @Test
    fun `known audio codecs get a short name`() {
        assertEquals("EAC3", codecName(MimeTypes.AUDIO_E_AC3_JOC, "ec+3"))
        assertEquals("TrueHD", codecName(MimeTypes.AUDIO_TRUEHD, null))
    }

    @Test
    fun `an unknown MIME type gives no codec name but a codec string does`() {
        assertNull(codecName(MimeTypes.APPLICATION_MEDIA3_CUES, "application/x-unknown"))
        assertEquals("EC-3", codecName("audio/x-unknown", "ec-3"))
        assertNull(codecName(null, null))
    }
}
