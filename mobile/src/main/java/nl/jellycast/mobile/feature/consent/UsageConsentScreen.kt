package nl.jellycast.mobile.feature.consent

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nl.jellycast.core.model.UsageChoice
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.AccentButton
import nl.jellycast.mobile.core.designsystem.component.BrandMark
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.ui.usage.UsageStatisticsDetails

private val ContentMaxWidth = 520.dp
private val MarkSize = 56.dp

/**
 * Asked once, before the app itself: may JellyCast count this device? Continue allows it;
 * declining is one tap on the same screen, and the app works the same either way.
 */
@Composable
fun UsageConsentScreen(onChoose: (UsageChoice) -> Unit, modifier: Modifier = Modifier) {
    var showsDetails by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxSize()
            .background(JellyCastColors.Background)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .weight(1f)
                .widthIn(max = ContentMaxWidth)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BrandMark(Modifier.size(MarkSize))
            Text(
                stringResource(R.string.usage_title),
                style = JellyCastText.LargeTitle,
                color = JellyCastColors.Text,
            )
            Text(
                stringResource(R.string.usage_intro),
                style = JellyCastText.Body,
                color = JellyCastColors.Text,
            )
            MoreInformation(showsDetails, onToggle = { showsDetails = !showsDetails })
            AnimatedVisibility(showsDetails, enter = fadeIn(), exit = fadeOut()) {
                UsageStatisticsDetails()
            }
        }
        Column(
            Modifier
                .widthIn(max = ContentMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AccentButton(
                title = stringResource(R.string.usage_continue),
                onClick = { onChoose(UsageChoice.Allowed) },
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = 48.dp)
                    .pressable({ onChoose(UsageChoice.Declined) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.usage_continue_without),
                    style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.Medium),
                    color = JellyCastColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun MoreInformation(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.pressable(onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.usage_more),
            style = JellyCastText.Subheadline.copy(fontWeight = FontWeight.Medium),
            color = JellyCastColors.Accent,
        )
        Icon(
            painterResource(R.drawable.ic_settings_chevron_right),
            contentDescription = null,
            tint = JellyCastColors.Accent,
            modifier = Modifier.size(14.dp).rotate(if (expanded) -90f else 90f),
        )
    }
}

@Preview(widthDp = 390, heightDp = 844)
@Composable
private fun UsageConsentScreenPreview() {
    JellyCastTheme { UsageConsentScreen(onChoose = {}) }
}
