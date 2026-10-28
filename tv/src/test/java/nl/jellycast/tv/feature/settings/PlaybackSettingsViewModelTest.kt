package nl.jellycast.tv.feature.settings

import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.common.AppDispatchers
import nl.jellycast.core.playback.CapabilitiesReader
import nl.jellycast.core.playback.ChromecastCapabilities
import nl.jellycast.core.playback.FormatSupport
import nl.jellycast.core.playback.FrameRateMode
import nl.jellycast.core.playback.PlaybackFormat
import nl.jellycast.core.testing.FakePlaybackSettingsStore
import nl.jellycast.core.testing.MainDispatcherRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PlaybackSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakePlaybackSettingsStore()

    private fun viewModel() = PlaybackSettingsViewModel(
        store,
        CapabilitiesReader { ChromecastCapabilities },
        AppDispatchers(io = mainDispatcherRule.testDispatcher),
    )

    @Test
    fun `shows per format what the device reports`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val rows = vm.uiState.value.formats.associateBy { it.format }
        assertEquals(true, rows.getValue(PlaybackFormat.DolbyVision).detected)
        assertEquals(false, rows.getValue(PlaybackFormat.Hdr10Plus).detected)
        assertEquals(true, rows.getValue(PlaybackFormat.Eac3).detected)
        assertEquals(false, rows.getValue(PlaybackFormat.TrueHd).detected)
        assertEquals(FormatSupport.Auto, rows.getValue(PlaybackFormat.Ac3).support)
        assertEquals(ChromecastCapabilities.toDeviceInfo(), vm.uiState.value.device)
    }

    @Test
    fun `OK cycles through auto, on, off`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onFormatClick(PlaybackFormat.Ac3)
        assertEquals(FormatSupport.On, store.settings.value.formatSupport[PlaybackFormat.Ac3])
        vm.onFormatClick(PlaybackFormat.Ac3)
        assertEquals(FormatSupport.Off, store.settings.value.formatSupport[PlaybackFormat.Ac3])
        vm.onFormatClick(PlaybackFormat.Ac3)
        assertEquals(FormatSupport.Auto, store.settings.value.formatSupport[PlaybackFormat.Ac3])
    }

    @Test
    fun `frame rate and stereo`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        vm.onFrameRateClick()
        assertEquals(FrameRateMode.System, vm.uiState.value.frameRateMode)
        vm.onFrameRateClick()
        assertEquals(FrameRateMode.Off, vm.uiState.value.frameRateMode)
        vm.onFrameRateClick()
        assertEquals(FrameRateMode.Always, vm.uiState.value.frameRateMode)

        vm.onDownmixToggle()
        assertEquals(true, vm.uiState.value.downmixToStereo)

        assertEquals(FormatSupport.Auto, vm.uiState.value.tunneling)
        vm.onTunnelingClick()
        assertEquals(FormatSupport.On, vm.uiState.value.tunneling)
        vm.onTunnelingClick()
        assertEquals(FormatSupport.Off, vm.uiState.value.tunneling)

        assertEquals(false, vm.uiState.value.ignoreVideoLevel)
        vm.onVideoLevelToggle()
        assertEquals(true, vm.uiState.value.ignoreVideoLevel)

        assertEquals(true, vm.uiState.value.decodeLosslessLocally)
        vm.onDecodeLosslessToggle()
        assertEquals(false, vm.uiState.value.decodeLosslessLocally)
    }

    @Test
    fun `start delay cycles through none, half a second, 1 and 2 s`() = runTest {
        val vm = viewModel()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect {} }

        val seen =
            (1..4).map {
                vm.onStartDelayClick()
                vm.uiState.value.startDelayMs
            }
        assertEquals(listOf(500, 1_000, 2_000, 0), seen)
    }
}
