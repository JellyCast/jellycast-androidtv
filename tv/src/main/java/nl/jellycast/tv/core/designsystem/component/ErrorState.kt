package nl.jellycast.tv.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme

/** Takes focus only when [takeFocus]: focus must never jump on its own. */
@Composable
fun ErrorState(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    takeFocus: Boolean = false,
) {
    val requester = remember { FocusRequester() }
    if (takeFocus) LaunchedEffect(Unit) { requester.requestFocus() }
    Column(
        modifier = modifier.widthIn(max = 440.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onRetry, modifier = Modifier.focusRequester(requester)) {
            Text(stringResource(R.string.action_retry))
        }
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun ErrorStatePreview() {
    JellyCastTheme {
        ErrorState(message = "The server is not responding.", onRetry = {})
    }
}
