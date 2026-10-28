package nl.jellycast.core.playback

data class AudioRouteCapabilities(val passthrough: Set<String>, val maxPcmChannels: Int)

/**
 * An AVR or soundbar that resyncs (mode switch, power cycle) reports nothing or only stereo for a
 * few seconds, so a dropped reading on the same HDMI route keeps the previous one.
 */
class AudioRouteMemory {
    private val last = HashMap<AudioOutput, AudioRouteCapabilities>()

    fun stable(output: AudioOutput, measured: AudioRouteCapabilities): AudioRouteCapabilities {
        if (output !in HdmiOutputs) return measured
        val previous = last[output]
        val dropped =
            previous != null &&
                (
                    (measured.passthrough.isEmpty() && previous.passthrough.isNotEmpty()) ||
                        (measured.maxPcmChannels <= STEREO && previous.maxPcmChannels > STEREO)
                    )
        if (dropped) return requireNotNull(previous)
        last[output] = measured
        return measured
    }

    private companion object {
        const val STEREO = 2
        val HdmiOutputs = setOf(AudioOutput.Hdmi, AudioOutput.HdmiArc, AudioOutput.HdmiEarc)
    }
}
