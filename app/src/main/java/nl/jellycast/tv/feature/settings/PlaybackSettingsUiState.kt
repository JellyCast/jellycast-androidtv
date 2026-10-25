package nl.jellycast.tv.feature.settings

import nl.jellycast.tv.core.playback.AudioOutput
import nl.jellycast.tv.core.playback.FormatSupport
import nl.jellycast.tv.core.playback.FrameRateMode
import nl.jellycast.tv.core.playback.PlaybackFormat

data class PlaybackSettingsUiState(
    val frameRateMode: FrameRateMode = FrameRateMode.Always,
    val formats: List<FormatRow> = PlaybackFormat.entries.map {
        FormatRow(it, FormatSupport.Auto, null)
    },
    val downmixToStereo: Boolean = false,
    val startDelayMs: Int = 0,
    val tunneling: FormatSupport = FormatSupport.Auto,
    val ignoreVideoLevel: Boolean = false,
    val decodeLosslessLocally: Boolean = true,
    val device: DeviceInfo? = null,
)

/** [detected] is what the device reports, which applies for Auto; `null` until measured. */
data class FormatRow(
    val format: PlaybackFormat,
    val support: FormatSupport,
    val detected: Boolean?,
)

data class DeviceInfo(
    val video: List<String>,
    val maxResolution: String,
    val hdr: List<String>,
    val passthrough: List<String>,
    val maxPcmChannels: Int,
    val audioOutput: AudioOutput = AudioOutput.Unknown,
)
