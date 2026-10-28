package nl.jellycast.mobile.feature.settings

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.settings.AudioOutputDelay
import org.koin.compose.koinInject

/** The lip-sync corrections set in the player, per Bluetooth device; here only to look back at. */
@Composable
internal fun AudioDelaySettingsSection(delay: AudioOutputDelay = koinInject()) {
    val saved by delay.saved.collectAsStateWithLifecycle()
    SettingsSection(
        title = stringResource(R.string.settings_audio_delay),
        footer = stringResource(R.string.settings_audio_delay_footer),
    ) {
        if (saved.isEmpty()) {
            SettingsRow(
                title = stringResource(R.string.settings_audio_delay_none),
                titleColor = JellyCastColors.TextMuted,
            )
        }
        saved.entries.sortedBy { it.value.name }.forEach { (id, device) ->
            SettingsRow(
                title = device.name,
                value = stringResource(R.string.player_sound_delay_value, device.milliseconds),
                trailing = {
                    TextButton(onClick = { delay.remove(id) }) {
                        Text(stringResource(R.string.settings_audio_delay_remove))
                    }
                },
            )
        }
    }
}
