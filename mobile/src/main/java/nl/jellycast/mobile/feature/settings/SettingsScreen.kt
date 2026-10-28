package nl.jellycast.mobile.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onChangeServer: () -> Unit,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onChangeServer = onChangeServer,
        onSignOut = viewModel::onSignOut,
        onBack = onBack,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onChangeServer: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_account)) },
                supportingContent = {
                    Text(
                        stringResource(
                            R.string.settings_account_on,
                            uiState.userName,
                            uiState.serverName,
                        ),
                    )
                },
            )
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.settings_other_server)) },
                modifier = Modifier.clickable(onClick = onChangeServer),
            )
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.settings_sign_out),
                        color = MaterialTheme.colorScheme.error,
                    )
                },
                modifier = Modifier.clickable(onClick = onSignOut),
            )
            HorizontalDivider()
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            R.string.settings_version,
                            uiState.versionName,
                            uiState.versionCode,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
    }
}

@Preview
@Composable
private fun SettingsContentPreview() {
    JellyCastTheme {
        SettingsContent(
            uiState = SettingsUiState("han", "Home server", "0.1.0", 1),
            onChangeServer = {},
            onSignOut = {},
            onBack = {},
        )
    }
}
