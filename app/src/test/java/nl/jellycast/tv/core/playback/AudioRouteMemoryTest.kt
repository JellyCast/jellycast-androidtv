package nl.jellycast.tv.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRouteMemoryTest {
    private val full = AudioRouteCapabilities(setOf("ac3", "eac3"), maxPcmChannels = 8)
    private val silent = AudioRouteCapabilities(emptySet(), maxPcmChannels = 2)

    @Test
    fun `an empty reading on the same HDMI route is ignored`() {
        val memory = AudioRouteMemory()
        memory.stable(AudioOutput.HdmiEarc, full)
        assertEquals(full, memory.stable(AudioOutput.HdmiEarc, silent))
    }

    @Test
    fun `the first reading always counts`() {
        assertEquals(silent, AudioRouteMemory().stable(AudioOutput.Hdmi, silent))
    }

    @Test
    fun `Bluetooth and other outputs trust the reading`() {
        val memory = AudioRouteMemory()
        memory.stable(AudioOutput.Bluetooth, full)
        assertEquals(silent, memory.stable(AudioOutput.Bluetooth, silent))
    }

    @Test
    fun `another route has its own memory`() {
        val memory = AudioRouteMemory()
        memory.stable(AudioOutput.HdmiEarc, full)
        assertEquals(silent, memory.stable(AudioOutput.Hdmi, silent))
    }

    @Test
    fun `a richer or different reading is taken over`() {
        val memory = AudioRouteMemory()
        memory.stable(AudioOutput.Hdmi, full)
        val more = AudioRouteCapabilities(setOf("ac3", "eac3", "dts"), maxPcmChannels = 8)
        assertEquals(more, memory.stable(AudioOutput.Hdmi, more))
        assertEquals(more, memory.stable(AudioOutput.Hdmi, silent))
    }
}
