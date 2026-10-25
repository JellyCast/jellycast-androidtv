package nl.jellycast.tv.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import nl.jellycast.tv.core.common.AppDispatchers
import nl.jellycast.tv.core.playback.CapabilitiesReader
import nl.jellycast.tv.core.playback.DeviceCapabilities
import nl.jellycast.tv.core.playback.FormatSupport
import nl.jellycast.tv.core.playback.FrameRateMode
import nl.jellycast.tv.core.playback.PlaybackFormat
import nl.jellycast.tv.core.playback.PlaybackSettingsStore
import nl.jellycast.tv.core.playback.StartDelayOptions

class PlaybackSettingsViewModel(
    private val settingsStore: PlaybackSettingsStore,
    capabilitiesReader: CapabilitiesReader,
    dispatchers: AppDispatchers,
) : ViewModel() {
    // Without the user's overrides: this is what Auto means.
    private val measured =
        flow<DeviceCapabilities?> { emit(capabilitiesReader.read()) }
            .flowOn(dispatchers.io)
            .onStart { emit(null) }

    val uiState: StateFlow<PlaybackSettingsUiState> =
        combine(settingsStore.settings, measured) { settings, capabilities ->
            PlaybackSettingsUiState(
                frameRateMode = settings.frameRateMode,
                formats =
                    PlaybackFormat.entries.map { format ->
                        FormatRow(
                            format = format,
                            support = settings.formatSupport[format] ?: FormatSupport.Auto,
                            detected = capabilities?.reports(format),
                        )
                    },
                downmixToStereo = settings.downmixToStereo,
                startDelayMs = settings.startDelayMs,
                tunneling = settings.tunneling,
                ignoreVideoLevel = settings.ignoreVideoLevel,
                decodeLosslessLocally = settings.decodeLosslessAudioLocally,
                device = capabilities?.toDeviceInfo(),
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackSettingsUiState())

    fun onFrameRateClick() {
        val modes = FrameRateMode.entries
        val current = settingsStore.settings.value.frameRateMode
        settingsStore.setFrameRateMode(modes[(current.ordinal + 1) % modes.size])
    }

    fun onFormatClick(format: PlaybackFormat) {
        val current = settingsStore.settings.value.formatSupport[format] ?: FormatSupport.Auto
        settingsStore.setFormatSupport(format, current.next())
    }

    fun onStartDelayClick() {
        val current = settingsStore.settings.value.startDelayMs
        val index = StartDelayOptions.indexOf(current)
        settingsStore.setStartDelay(StartDelayOptions[(index + 1) % StartDelayOptions.size])
    }

    fun onDownmixToggle() {
        settingsStore.setDownmixToStereo(!settingsStore.settings.value.downmixToStereo)
    }

    fun onDecodeLosslessToggle() {
        settingsStore.setDecodeLosslessAudioLocally(
            !settingsStore.settings.value.decodeLosslessAudioLocally,
        )
    }

    fun onVideoLevelToggle() {
        settingsStore.setIgnoreVideoLevel(!settingsStore.settings.value.ignoreVideoLevel)
    }

    fun onTunnelingClick() {
        settingsStore.setTunneling(settingsStore.settings.value.tunneling.next())
    }
}

private fun DeviceCapabilities.reports(format: PlaybackFormat): Boolean {
    val type = format.hdrType
    return if (type != null) type in hdrTypes else format.codecs.any { it in passthroughAudio }
}
