package nl.jellycast.tv.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PaddingRowMaskTest {
    @Test
    fun `a height that is a multiple of 8 has no strip`() {
        assertEquals(0, paddingMaskHeightPx(1080, 1080f))
        assertEquals(0, paddingMaskHeightPx(2160, 2160f))
        assertEquals(0, paddingMaskHeightPx(1920, 1080f))
    }

    @Test
    fun `3840x1606 gets a strip for the padded rows`() {
        // 1606 → 1608: 2 rows. On a 1080p UI the picture is 803 px high, so 1 px.
        assertEquals(1, paddingMaskHeightPx(1606, 803f))
        assertEquals(2, paddingMaskHeightPx(1606, 1606f))
    }

    @Test
    fun `1920x804 gets a strip`() {
        assertEquals(4, paddingMaskHeightPx(804, 804f))
    }

    @Test
    fun `an unknown size has no strip`() {
        assertEquals(0, paddingMaskHeightPx(0, 1080f))
        assertEquals(0, paddingMaskHeightPx(1606, 0f))
    }
}
