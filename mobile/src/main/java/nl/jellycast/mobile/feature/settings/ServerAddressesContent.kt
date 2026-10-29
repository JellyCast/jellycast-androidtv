package nl.jellycast.mobile.feature.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.net.URI
import java.net.URISyntaxException
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors

/** The addresses of the signed-in server, such as the LAN address and one over a VPN. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerAddressesContent(
    uiState: ServerAddressesUiState,
    onUse: (String) -> Unit,
    onAdd: (String) -> Unit,
    onMove: (address: String, by: Int) -> Unit,
    onRemove: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showsAddDialog by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = JellyCastColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_addresses)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(containerColor = JellyCastColors.Background),
            )
        },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection(footer = stringResource(R.string.settings_addresses_order_footer)) {
                uiState.addresses.forEachIndexed { index, address ->
                    AddressRow(
                        address = address,
                        uiState = uiState,
                        isFirst = index == 0,
                        isLast = index == uiState.addresses.lastIndex,
                        onUse = onUse,
                        onMove = onMove,
                        onRemove = onRemove,
                    )
                }
            }
            SettingsSection(
                footer = stringResource(R.string.settings_addresses_add_footer, uiState.serverName),
            ) {
                SettingsRow(
                    title = stringResource(R.string.settings_addresses_add),
                    titleColor = JellyCastColors.Accent,
                    enabled = !uiState.isAdding,
                    onClick = { showsAddDialog = true },
                    trailing = { if (uiState.isAdding) SmallSpinner() },
                )
                uiState.problem?.let { problem ->
                    Text(
                        problemText(problem),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
    if (showsAddDialog) {
        AddAddressDialog(
            onAdd = {
                showsAddDialog = false
                onAdd(it)
            },
            onDismiss = { showsAddDialog = false },
        )
    }
}

@Composable
private fun AddressRow(
    address: String,
    uiState: ServerAddressesUiState,
    isFirst: Boolean,
    isLast: Boolean,
    onUse: (String) -> Unit,
    onMove: (address: String, by: Int) -> Unit,
    onRemove: (String) -> Unit,
) {
    var showsMenu by remember { mutableStateOf(false) }
    SettingsRow(
        title = addressTitle(address),
        supporting =
            stringResource(R.string.settings_addresses_in_use).takeIf { address == uiState.inUse },
        enabled = uiState.checking == null,
        onClick = { onUse(address) },
        trailing = {
            if (uiState.checking == address) {
                SmallSpinner()
            } else {
                Box {
                    IconButton(onClick = { showsMenu = true }) {
                        Icon(
                            painterResource(R.drawable.ic_lists_more),
                            contentDescription = stringResource(R.string.settings_addresses_more),
                        )
                    }
                    DropdownMenu(expanded = showsMenu, onDismissRequest = { showsMenu = false }) {
                        if (!isFirst) {
                            MenuItem(R.string.settings_addresses_move_up) {
                                showsMenu = false
                                onMove(address, -1)
                            }
                        }
                        if (!isLast) {
                            MenuItem(R.string.settings_addresses_move_down) {
                                showsMenu = false
                                onMove(address, 1)
                            }
                        }
                        if (uiState.addresses.size > 1) {
                            MenuItem(R.string.settings_addresses_remove) {
                                showsMenu = false
                                onRemove(address)
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun MenuItem(label: Int, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick)
}

@Composable
private fun AddAddressDialog(onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    var input by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_addresses_add)) },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text(stringResource(R.string.settings_addresses_example)) },
                singleLine = true,
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Uri,
                    ),
            )
        },
        confirmButton = {
            TextButton(onClick = { onAdd(input) }, enabled = input.isNotBlank()) {
                Text(stringResource(R.string.settings_addresses_add_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.settings_cancel)) }
        },
    )
}

@Composable
private fun SmallSpinner() {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
}

@Composable
private fun problemText(problem: AddressProblem): String = when (problem) {
    AddressProblem.NotAnswering -> stringResource(R.string.settings_addresses_not_answering)

    AddressProblem.Invalid -> stringResource(R.string.settings_signin_error_invalid)

    AddressProblem.AlreadyListed -> stringResource(R.string.settings_addresses_already_listed)

    is AddressProblem.OtherServer ->
        stringResource(R.string.settings_addresses_other_server, problem.name)
}

/** "192.168.1.4:8096" for a plain HTTP address, the full URL otherwise. */
internal fun addressTitle(address: String): String {
    val uri =
        try {
            URI(address)
        } catch (_: URISyntaxException) {
            return address
        }
    val host = uri.host ?: return address
    val hasPath = !uri.path.isNullOrEmpty() && uri.path != "/"
    if (uri.scheme != "http" || hasPath) return address
    return if (uri.port >= 0) "$host:${uri.port}" else host
}

@Preview
@Composable
private fun ServerAddressesContentPreview() {
    ServerAddressesContent(
        uiState =
            ServerAddressesUiState(
                serverName = "home server",
                addresses = listOf("http://192.168.1.4:8096", "http://10.8.0.1:8096"),
                inUse = "http://192.168.1.4:8096",
            ),
        onUse = {},
        onAdd = {},
        onMove = { _, _ -> },
        onRemove = {},
        onBack = {},
    )
}
