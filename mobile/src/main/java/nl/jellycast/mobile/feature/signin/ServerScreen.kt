package nl.jellycast.mobile.feature.signin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.core.model.ServerInfo
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.BrandHeader
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ServerScreen(onNavigateToLogin: () -> Unit, viewModel: ServerViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigate by rememberUpdatedState(onNavigateToLogin)
    LaunchedEffect(uiState.status) {
        if (uiState.status == ServerStatus.Saved) {
            viewModel.onNavigated()
            currentOnNavigate()
        }
    }
    ServerContent(
        uiState = uiState,
        onAddressChange = viewModel::onAddressChange,
        onConnect = viewModel::onConnect,
        onServerClick = viewModel::onServerClick,
        onSearchAgain = viewModel::onSearchAgain,
    )
}

@Composable
fun ServerContent(
    uiState: ServerUiState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onServerClick: (ServerInfo) -> Unit,
    onSearchAgain: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BrandHeader(Modifier.padding(top = 32.dp, bottom = 16.dp))
        Column(
            Modifier.widthIn(max = 480.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.server_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.server_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DiscoveredServers(uiState, onServerClick, onSearchAgain)
            OutlinedTextField(
                value = uiState.address,
                onValueChange = onAddressChange,
                label = { Text(stringResource(R.string.server_address_label)) },
                placeholder = { Text(stringResource(R.string.server_manual_example)) },
                singleLine = true,
                isError = uiState.status is ServerStatus.Failed,
                supportingText =
                    (uiState.status as? ServerStatus.Failed)?.let { failed ->
                        { Text(failed.result.message()) }
                    },
                keyboardOptions =
                    KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onConnect() }),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = onConnect,
                enabled = uiState.status != ServerStatus.Checking && uiState.address.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (uiState.status == ServerStatus.Checking) {
                            R.string.server_checking
                        } else {
                            R.string.server_connect
                        },
                    ),
                )
            }
        }
    }
}

@Composable
private fun DiscoveredServers(
    uiState: ServerUiState,
    onServerClick: (ServerInfo) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        uiState.discovered.forEach { server ->
            OutlinedCard(onClick = { onServerClick(server) }, Modifier.fillMaxWidth()) {
                ListItem(
                    headlineContent = { Text(server.name) },
                    supportingContent = { Text(server.url) },
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (uiState.searching) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    stringResource(R.string.server_discovery_searching),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp),
                )
            } else {
                Text(
                    stringResource(
                        if (uiState.discovered.isEmpty()) {
                            R.string.server_discovery_none
                        } else {
                            R.string.server_discovery_found
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSearchAgain) {
                    Text(stringResource(R.string.server_discovery_retry))
                }
            }
        }
    }
}

@Composable
private fun ServerCheckResult.message(): String = when (this) {
    ServerCheckResult.InvalidAddress -> stringResource(R.string.server_error_invalid)

    ServerCheckResult.Unreachable -> stringResource(R.string.server_error_unreachable)

    ServerCheckResult.NotJellyfin -> stringResource(R.string.server_error_not_jellyfin)

    is ServerCheckResult.UnsupportedVersion -> stringResource(
        R.string.server_error_version,
        version,
    )

    is ServerCheckResult.Success -> ""
}

@Preview
@Composable
private fun ServerContentPreview() {
    JellyCastTheme {
        ServerContent(
            uiState =
                ServerUiState(
                    discovered = listOf(ServerInfo("http://192.168.1.4:8096", "Home server")),
                ),
            onAddressChange = {},
            onConnect = {},
            onServerClick = {},
            onSearchAgain = {},
        )
    }
}
