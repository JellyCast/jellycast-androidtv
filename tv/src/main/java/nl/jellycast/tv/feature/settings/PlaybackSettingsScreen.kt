package nl.jellycast.tv.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import java.text.NumberFormat
import nl.jellycast.core.playback.AudioOutput
import nl.jellycast.core.playback.FormatSupport
import nl.jellycast.core.playback.FrameRateMode
import nl.jellycast.core.playback.PlaybackFormat
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.AuthButton
import nl.jellycast.tv.core.designsystem.component.AuthHeading
import nl.jellycast.tv.core.designsystem.component.AuthLayout
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun PlaybackSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: PlaybackSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PlaybackSettingsContent(
        uiState = uiState,
        onFrameRateClick = viewModel::onFrameRateClick,
        onFormatClick = viewModel::onFormatClick,
        onDownmixToggle = viewModel::onDownmixToggle,
        onStartDelayClick = viewModel::onStartDelayClick,
        onTunnelingClick = viewModel::onTunnelingClick,
        onVideoLevelToggle = viewModel::onVideoLevelToggle,
        onDecodeLosslessToggle = viewModel::onDecodeLosslessToggle,
        modifier = modifier,
    )
}

private enum class PlaybackExplained {
    None,
    FrameRate,
    StartDelay,
    Hdr,
    Passthrough,
    Downmix,
    Tunneling,
    VideoLevel,
    Lossless,
}

private val ButtonSpacing = 8.dp

// Room for about four lines of explanation plus the device line below the brand.
private val FooterReserve = 180.dp

// Safe area height minus the heading.
private val ColumnMaxHeight = 420.dp

// Ends before the button column, which starts at 520 dp.
private val ExplainWidth = 400.dp

@Composable
fun PlaybackSettingsContent(
    uiState: PlaybackSettingsUiState,
    onFrameRateClick: () -> Unit,
    onFormatClick: (PlaybackFormat) -> Unit,
    onDownmixToggle: () -> Unit,
    onStartDelayClick: () -> Unit,
    onTunnelingClick: () -> Unit,
    onVideoLevelToggle: () -> Unit,
    onDecodeLosslessToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { firstRequester.requestFocus() }
    val explanation = remember { ExplanationState(PlaybackExplained.None) }

    AuthLayout(
        modifier = modifier,
        spacing = ButtonSpacing,
        footerReserve = FooterReserve,
        footer = { PlaybackSettingsFooter(device = uiState.device, explanation = explanation) },
    ) {
        AuthHeading(stringResource(R.string.playback_title))
        // Scrolls along with focus: there are more buttons than fit on the screen.
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = ColumnMaxHeight)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(ButtonSpacing),
        ) {
            PlaybackSettingsButtons(
                uiState = uiState,
                onFrameRateClick = onFrameRateClick,
                onFormatClick = onFormatClick,
                onDownmixToggle = onDownmixToggle,
                onStartDelayClick = onStartDelayClick,
                onTunnelingClick = onTunnelingClick,
                onVideoLevelToggle = onVideoLevelToggle,
                onDecodeLosslessToggle = onDecodeLosslessToggle,
                firstRequester = firstRequester,
                explanation = explanation,
            )
        }
    }
}

@Composable
private fun PlaybackSettingsFooter(
    device: DeviceInfo?,
    explanation: ExplanationState<PlaybackExplained>,
) {
    Column(
        modifier = Modifier.widthIn(max = ExplainWidth),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = explanation.explained.text(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        device?.let { DeviceLine(it) }
    }
}

@Composable
private fun ColumnScope.PlaybackSettingsButtons(
    uiState: PlaybackSettingsUiState,
    onFrameRateClick: () -> Unit,
    onFormatClick: (PlaybackFormat) -> Unit,
    onDownmixToggle: () -> Unit,
    onStartDelayClick: () -> Unit,
    onTunnelingClick: () -> Unit,
    onVideoLevelToggle: () -> Unit,
    onDecodeLosslessToggle: () -> Unit,
    firstRequester: FocusRequester,
    explanation: ExplanationState<PlaybackExplained>,
) {
    val buttonModifier = Modifier.align(Alignment.CenterHorizontally)
    AuthButton(
        text = stringResource(uiState.frameRateMode.label()),
        onClick = onFrameRateClick,
        modifier =
            buttonModifier
                .focusRequester(firstRequester)
                .explains(explanation, PlaybackExplained.FrameRate),
    )
    AuthButton(
        text = startDelayLabel(uiState.startDelayMs),
        onClick = onStartDelayClick,
        modifier = buttonModifier.explains(explanation, PlaybackExplained.StartDelay),
    )
    for (row in uiState.formats) {
        val explains =
            if (row.format.hdrType != null) {
                PlaybackExplained.Hdr
            } else {
                PlaybackExplained.Passthrough
            }
        AuthButton(
            text = row.label(),
            onClick = { onFormatClick(row.format) },
            modifier = buttonModifier.explains(explanation, explains),
        )
    }
    AuthButton(
        text =
            stringResource(
                if (uiState.downmixToStereo) {
                    R.string.playback_downmix_on
                } else {
                    R.string.playback_downmix_off
                },
            ),
        onClick = onDownmixToggle,
        modifier = buttonModifier.explains(explanation, PlaybackExplained.Downmix),
    )
    AuthButton(
        text =
            stringResource(
                if (uiState.decodeLosslessLocally) {
                    R.string.playback_lossless_on
                } else {
                    R.string.playback_lossless_off
                },
            ),
        onClick = onDecodeLosslessToggle,
        modifier = buttonModifier.explains(explanation, PlaybackExplained.Lossless),
    )
    AuthButton(
        text = stringResource(uiState.tunneling.tunnelingLabel()),
        onClick = onTunnelingClick,
        modifier = buttonModifier.explains(explanation, PlaybackExplained.Tunneling),
    )
    AuthButton(
        text =
            stringResource(
                if (uiState.ignoreVideoLevel) {
                    R.string.playback_video_level_ignore
                } else {
                    R.string.playback_video_level_check
                },
            ),
        onClick = onVideoLevelToggle,
        modifier = buttonModifier.explains(explanation, PlaybackExplained.VideoLevel),
    )
}

@Composable
private fun PlaybackExplained.text(): String = when (this) {
    PlaybackExplained.None -> ""
    PlaybackExplained.FrameRate -> stringResource(R.string.playback_frame_rate_explain)
    PlaybackExplained.StartDelay -> stringResource(R.string.playback_start_delay_explain)
    PlaybackExplained.Hdr -> stringResource(R.string.playback_hdr_explain)
    PlaybackExplained.Passthrough -> stringResource(R.string.playback_passthrough_explain)
    PlaybackExplained.Downmix -> stringResource(R.string.playback_downmix_explain)
    PlaybackExplained.Tunneling -> stringResource(R.string.playback_tunneling_explain)
    PlaybackExplained.VideoLevel -> stringResource(R.string.playback_video_level_explain)
    PlaybackExplained.Lossless -> stringResource(R.string.playback_lossless_explain)
}

@Composable
private fun startDelayLabel(startDelayMs: Int): String = if (startDelayMs == 0) {
    stringResource(R.string.playback_start_delay_none)
} else {
    stringResource(R.string.playback_start_delay, formatSeconds(startDelayMs))
}

@StringRes
private fun FormatSupport.tunnelingLabel(): Int = when (this) {
    FormatSupport.Auto -> R.string.playback_tunneling_auto
    FormatSupport.On -> R.string.playback_tunneling_on
    FormatSupport.Off -> R.string.playback_tunneling_off
}

/** 500 → "0,5" (or "0.5", depending on the locale), 1000 → "1". */
@Composable
private fun formatSeconds(ms: Int): String {
    val locale = LocalConfiguration.current.locales[0]
    val format = NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 1 }
    return format.format(ms / 1000.0)
}

@StringRes
private fun FrameRateMode.label(): Int = when (this) {
    FrameRateMode.Always -> R.string.playback_frame_rate_always
    FrameRateMode.System -> R.string.playback_frame_rate_system
    FrameRateMode.Off -> R.string.playback_frame_rate_off
}

@Composable
private fun FormatRow.label(): String {
    val formatName = stringResource(format.nameRes())
    val name =
        if (format.hdrType != null) {
            formatName
        } else {
            stringResource(R.string.playback_passthrough_name, formatName)
        }
    return when (support) {
        FormatSupport.Auto -> {
            val found =
                when (detected) {
                    true -> stringResource(R.string.playback_detected_yes)
                    false -> stringResource(R.string.playback_detected_no)
                    null -> stringResource(R.string.playback_detected_unknown)
                }
            stringResource(R.string.playback_format_auto, name, found)
        }

        FormatSupport.On -> stringResource(R.string.playback_format_on, name)

        FormatSupport.Off -> stringResource(R.string.playback_format_off, name)
    }
}

@StringRes
private fun PlaybackFormat.nameRes(): Int = when (this) {
    PlaybackFormat.Hdr10 -> R.string.format_hdr10
    PlaybackFormat.Hdr10Plus -> R.string.format_hdr10_plus
    PlaybackFormat.DolbyVision -> R.string.format_dolby_vision
    PlaybackFormat.Ac3 -> R.string.format_ac3
    PlaybackFormat.Eac3 -> R.string.format_eac3
    PlaybackFormat.Dts -> R.string.format_dts
    PlaybackFormat.TrueHd -> R.string.format_truehd
}

@StringRes
private fun AudioOutput.label(): Int = when (this) {
    AudioOutput.Hdmi -> R.string.settings_output_hdmi
    AudioOutput.HdmiArc -> R.string.settings_output_hdmi_arc
    AudioOutput.HdmiEarc -> R.string.settings_output_hdmi_earc
    AudioOutput.Bluetooth -> R.string.settings_output_bluetooth
    AudioOutput.Usb -> R.string.settings_output_usb
    AudioOutput.Speaker -> R.string.settings_output_speaker
    AudioOutput.Unknown -> R.string.settings_device_none
}

@Composable
private fun DeviceLine(device: DeviceInfo, modifier: Modifier = Modifier) {
    val none = stringResource(R.string.settings_device_none)
    fun List<String>.text() = ifEmpty { listOf(none) }.joinToString(", ")
    Text(
        text =
            stringResource(
                R.string.settings_device,
                device.video.text(),
                device.maxResolution,
                device.hdr.text(),
                device.passthrough.text(),
                device.maxPcmChannels,
                stringResource(device.audioOutput.label()),
            ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun PlaybackSettingsContentPreview() {
    JellyCastTheme {
        PlaybackSettingsContent(
            uiState =
                PlaybackSettingsUiState(
                    formats =
                        PlaybackFormat.entries.map {
                            FormatRow(
                                format = it,
                                support =
                                    if (it == PlaybackFormat.DolbyVision) {
                                        FormatSupport.Off
                                    } else {
                                        FormatSupport.Auto
                                    },
                                detected = it != PlaybackFormat.Dts,
                            )
                        },
                    device =
                        DeviceInfo(
                            video = listOf("HEVC Main10", "H.264", "VP9"),
                            maxResolution = "4K",
                            hdr = listOf("Dolby Vision (4, 5, 8)", "HDR10", "HLG"),
                            passthrough = listOf("AC3", "EAC3"),
                            maxPcmChannels = 8,
                        ),
                ),
            onFrameRateClick = {},
            onFormatClick = {},
            onDownmixToggle = {},
            onStartDelayClick = {},
            onTunnelingClick = {},
            onVideoLevelToggle = {},
            onDecodeLosslessToggle = {},
        )
    }
}
