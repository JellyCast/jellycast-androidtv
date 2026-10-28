package nl.jellycast.core.playback

import nl.jellycast.core.model.BitrateLimit
import nl.jellycast.core.model.BitrateLimitCause
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BitrateLimitTest {
    @Test
    fun `no limit and no fallback has no cap`() {
        assertNull(bitrateLimit(DEFAULT_MAX_BITRATE, fallbackBitrate = null))
    }

    @Test
    fun `a lower quality setting caps the bitrate`() {
        assertEquals(
            BitrateLimit(20_000_000, BitrateLimitCause.QualitySetting),
            bitrateLimit(20_000_000, fallbackBitrate = null),
        )
    }

    @Test
    fun `a slow network fallback caps below no limit`() {
        assertEquals(
            BitrateLimit(40_000_000, BitrateLimitCause.SlowNetwork),
            bitrateLimit(DEFAULT_MAX_BITRATE, fallbackBitrate = 40_000_000),
        )
    }

    @Test
    fun `a fallback above the quality setting leaves the setting in charge`() {
        assertEquals(
            BitrateLimit(10_000_000, BitrateLimitCause.QualitySetting),
            bitrateLimit(10_000_000, fallbackBitrate = 40_000_000),
        )
    }
}
