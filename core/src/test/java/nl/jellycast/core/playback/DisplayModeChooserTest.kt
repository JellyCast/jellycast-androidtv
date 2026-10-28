package nl.jellycast.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DisplayModeChooserTest {
    private val rates = listOf(23.976f, 24f, 25f, 29.97f, 30f, 50f, 59.94f, 60f)
    private val modes =
        rates.mapIndexed { i, hz ->
            DisplayModeSpec(id = i + 1, width = 3840, height = 2160, refreshRate = hz)
        } +
            rates.mapIndexed { i, hz ->
                DisplayModeSpec(id = i + 101, width = 1920, height = 1080, refreshRate = hz)
            }
    private val current = modes.first { it.width == 3840 && it.refreshRate == 59.94f }

    private fun choose(fps: Float?) = chooseDisplayMode(modes, current, fps)?.mode?.refreshRate

    @Test
    fun `23_976 fps goes to 23_976 Hz, not 24`() {
        assertEquals(23.976f, choose(23.976025f))
    }

    @Test
    fun `true 24 fps goes to 24 Hz`() {
        assertEquals(24f, choose(24f))
    }

    @Test
    fun `25 fps goes to 25 Hz`() {
        assertEquals(25f, choose(25f))
    }

    @Test
    fun `resolution stays that of the current mode`() {
        assertEquals(3840, chooseDisplayMode(modes, current, 23.976f)?.mode?.width)
    }

    @Test
    fun `already in the right mode means no switch`() {
        assertNull(choose(59.94f))
    }

    @Test
    fun `without a known frame rate there is no switch`() {
        assertNull(choose(null))
        assertNull(choose(0f))
    }

    @Test
    fun `without a fitting mode there is no switch`() {
        assertNull(choose(17f))
    }

    @Test
    fun `without an exact match the double rate is used`() {
        val only50 = listOf(current, DisplayModeSpec(9, 3840, 2160, 50f))
        assertEquals(50f, chooseDisplayMode(only50, current, 25f)?.mode?.refreshRate)
    }

    @Test
    fun `24p on a 60 Hz TV does not switch to 60 Hz`() {
        val only60 = listOf(current, DisplayModeSpec(9, 3840, 2160, 60f))
        assertNull(chooseDisplayMode(only60, current, 23.976f))
    }

    @Test
    fun `a rate only in another resolution uses it, never below the video or 720p`() {
        val tv =
            listOf(
                current,
                DisplayModeSpec(20, 1920, 1080, 23.976f),
                DisplayModeSpec(21, 1280, 720, 23.976f),
                DisplayModeSpec(22, 720, 576, 23.976f),
            )
        val hd = chooseDisplayMode(tv, current, 23.976f, videoWidth = 1920, videoHeight = 1080)
        assertEquals(20, hd?.mode?.id)

        val sd = chooseDisplayMode(tv.filter { it.id != 20 }, current, 23.976f, 720, 576)
        assertEquals(21, sd?.mode?.id)

        val uhd = chooseDisplayMode(tv, current, 23.976f, videoWidth = 3840, videoHeight = 2160)
        assertNull(uhd)
    }

    @Test
    fun `seamless only picks what the current mode switches to seamlessly`() {
        val seamlessCurrent = current.copy(alternativeRefreshRates = listOf(23.976f, 29.97f))
        val all = modes.map { if (it.id == current.id) seamlessCurrent else it }

        val film = chooseDisplayMode(all, seamlessCurrent, 23.976f, seamlessOnly = true)
        assertEquals(23.976f, film?.mode?.refreshRate)
        assertEquals(true, film?.seamless)
        assertNull(chooseDisplayMode(all, seamlessCurrent, 25f, seamlessOnly = true))
    }

    @Test
    fun `the Chromecast does not switch seamlessly without alternative rates`() {
        assertEquals(false, chooseDisplayMode(modes, current, 23.976f)?.seamless)
    }
}
