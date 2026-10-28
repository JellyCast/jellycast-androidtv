package nl.jellycast.mobile.feature.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlin.math.roundToInt
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.ListSectionLabel
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.settings.AudioDevice
import nl.jellycast.mobile.core.settings.AudioOutputDelay
import org.koin.compose.koinInject

private val ControlShape = RoundedCornerShape(18.dp)

/** The lip-sync nudge, shown only while a Bluetooth device plays the sound. */
@Composable
fun AudioDelaySection(modifier: Modifier = Modifier) {
    val delay: AudioOutputDelay = koinInject()
    val device by delay.currentDevice.collectAsStateWithLifecycle()
    val saved by delay.saved.collectAsStateWithLifecycle()
    val current = device ?: return
    Column(modifier) {
        ListSectionLabel(
            stringResource(R.string.player_sound_delay),
            Modifier.padding(start = 4.dp, top = 8.dp, bottom = 14.dp),
        )
        AudioDelayControl(
            device = current,
            milliseconds = saved[current.id]?.milliseconds ?: 0,
            onChange = { delay.set(it, current) },
        )
    }
}

@Composable
private fun AudioDelayControl(
    device: AudioDevice,
    milliseconds: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draft by remember { mutableStateOf<Float?>(null) }
    val shown = draft?.roundToInt() ?: milliseconds
    Column(
        modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(JellyCastColors.Text.copy(alpha = 0.05f))
            .border(BorderStroke(1.5.dp, JellyCastColors.Text.copy(alpha = 0.10f)), ControlShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    device.name,
                    style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                    color = JellyCastColors.Text,
                )
                Text(
                    stringResource(R.string.player_sound_delay_hint),
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.TextMuted,
                )
            }
            Text(
                stringResource(R.string.player_sound_delay_value, shown),
                style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StepButton("−", stringResource(R.string.player_sound_delay_less)) {
                onChange(milliseconds - AudioOutputDelay.STEP_MS)
            }
            Slider(
                value = shown.toFloat(),
                onValueChange = { value ->
                    val step = AudioOutputDelay.STEP_MS.toFloat()
                    draft = (value / step).roundToInt() * step
                },
                onValueChangeFinished = {
                    draft?.let { onChange(it.roundToInt()) }
                    draft = null
                },
                valueRange = 0f..AudioOutputDelay.MAX_MS.toFloat(),
                colors =
                    SliderDefaults.colors(
                        thumbColor = JellyCastColors.Accent,
                        activeTrackColor = JellyCastColors.Accent,
                        inactiveTrackColor = JellyCastColors.Rail,
                    ),
                modifier = Modifier.weight(1f),
            )
            StepButton("+", stringResource(R.string.player_sound_delay_more)) {
                onChange(milliseconds + AudioOutputDelay.STEP_MS)
            }
        }
    }
}

@Composable
private fun StepButton(symbol: String, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(JellyCastColors.Text.copy(alpha = 0.08f))
            .pressable(onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            symbol,
            style = JellyCastText.Body.copy(fontWeight = FontWeight.Bold),
            color = JellyCastColors.Text,
        )
    }
}
