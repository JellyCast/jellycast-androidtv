package nl.jellycast.tv.feature.consent

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.core.model.UsageChoice
import nl.jellycast.tv.BuildConfig
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.AuthButton
import nl.jellycast.tv.core.designsystem.component.AuthContentWidth
import nl.jellycast.tv.core.designsystem.component.AuthHeading
import nl.jellycast.tv.core.designsystem.component.AuthLayout
import nl.jellycast.tv.core.designsystem.component.AuthText
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

/**
 * Asked once, before the app itself: may JellyCast count this TV? Continue (initial focus)
 * allows it; declining is the next button, and the app works the same either way.
 */
@Composable
fun UsageConsentScreen(onChoose: (UsageChoice) -> Unit, modifier: Modifier = Modifier) {
    var showsDetails by rememberSaveable { mutableStateOf(false) }
    val continueRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { continueRequester.requestFocus() }
    AuthLayout(modifier = modifier, spacing = 12.dp) {
        AuthHeading(stringResource(R.string.usage_title))
        if (showsDetails) Details() else AuthText(stringResource(R.string.usage_intro))
        val buttonModifier = Modifier.align(Alignment.CenterHorizontally)
        AuthButton(
            text = stringResource(R.string.usage_continue),
            onClick = { onChoose(UsageChoice.Allowed) },
            modifier = buttonModifier.focusRequester(continueRequester),
        )
        AuthButton(
            text = stringResource(R.string.usage_continue_without),
            onClick = { onChoose(UsageChoice.Declined) },
            modifier = buttonModifier,
        )
        AuthButton(
            text = stringResource(R.string.usage_more),
            onClick = { showsDetails = !showsDetails },
            modifier = buttonModifier,
        )
    }
}

@Composable
private fun ColumnScope.Details() {
    val thisDevice = "${Build.MODEL}, Android ${Build.VERSION.RELEASE}, " +
        "JellyCast ${BuildConfig.VERSION_NAME}"
    Column(Modifier.width(AuthContentWidth), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            stringResource(R.string.usage_detail_ping),
            stringResource(R.string.usage_detail_device, thisDevice),
            stringResource(R.string.usage_detail_country),
            stringResource(R.string.usage_detail_retention),
            stringResource(R.string.usage_detail_settings),
        ).forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun UsageConsentScreenPreview() {
    JellyCastTheme { UsageConsentScreen(onChoose = {}) }
}
