package nl.jellycast.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import nl.jellycast.core.playback.FormatSupport
import nl.jellycast.core.playback.FrameRateMode
import nl.jellycast.core.playback.PlaybackFormat
import nl.jellycast.core.playback.PlaybackSettings
import nl.jellycast.core.playback.PlaybackSettingsStore
import nl.jellycast.core.playback.SubtitleSize

class FakePlaybackSettingsStore(initial: PlaybackSettings = PlaybackSettings()) :
    PlaybackSettingsStore {
    override val settings = MutableStateFlow(initial)

    override suspend fun current(): PlaybackSettings = settings.value

    override fun setFrameRateMode(mode: FrameRateMode) = settings.update {
        it.copy(frameRateMode = mode)
    }

    override fun setFormatSupport(format: PlaybackFormat, support: FormatSupport) =
        settings.update { it.copy(formatSupport = it.formatSupport + (format to support)) }

    override fun setDownmixToStereo(enabled: Boolean) =
        settings.update { it.copy(downmixToStereo = enabled) }

    override fun setStartDelay(ms: Int) = settings.update { it.copy(startDelayMs = ms) }

    override fun setDecodeLosslessAudioLocally(enabled: Boolean) =
        settings.update { it.copy(decodeLosslessAudioLocally = enabled) }

    override fun setBurnInAss(enabled: Boolean) = settings.update { it.copy(burnInAss = enabled) }

    override fun setMaxStreamingBitrate(bitsPerSecond: Int) =
        settings.update { it.copy(maxStreamingBitrate = bitsPerSecond) }

    override fun setSubtitleSize(size: SubtitleSize) = settings.update {
        it.copy(subtitleSize = size)
    }

    override fun setSubtitleBackground(enabled: Boolean) =
        settings.update { it.copy(subtitleBackground = enabled) }

    override fun setTunneling(mode: FormatSupport) = settings.update { it.copy(tunneling = mode) }

    override fun setIgnoreVideoLevel(enabled: Boolean) =
        settings.update { it.copy(ignoreVideoLevel = enabled) }
}
