package nl.jellycast.mobile.feature.signin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.net.URI
import java.net.URISyntaxException
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.BrandMark
import nl.jellycast.mobile.core.designsystem.component.BrandWordmark
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import org.koin.compose.viewmodel.koinViewModel

/**
 * Servers on the network, an address field and Quick Connect, as iOS. [hasServer] is kept for
 * the activity; the screen reads the stored server itself.
 */
@Suppress("UnusedParameter")
@Composable
fun SignInScreen(hasServer: Boolean, viewModel: SignInViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SignInContent(
        uiState = uiState,
        onAddressChange = viewModel::onAddressChange,
        onConnect = viewModel::onConnect,
        onServerClick = viewModel::onServerClick,
        onSearchAgain = viewModel::onSearchAgain,
        onCancel = viewModel::onCancel,
    )
}

@Composable
fun SignInContent(
    uiState: SignInUiState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onServerClick: (DiscoveredServer) -> Unit,
    onSearchAgain: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(JellyCastColors.Background)
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 64.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BrandMark(Modifier.size(88.dp))
                BrandWordmark(size = 28.sp)
            }
            when (val phase = uiState.phase) {
                is SignInPhase.WaitingForApproval ->
                    QuickConnectCode(phase.serverName, phase.code, onCancel)

                else -> {
                    DiscoverySection(uiState, onServerClick, onSearchAgain)
                    AddressForm(uiState, onAddressChange, onConnect)
                }
            }
        }
    }
}

@Composable
private fun DiscoverySection(
    uiState: SignInUiState,
    onServerClick: (DiscoveredServer) -> Unit,
    onSearchAgain: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.settings_signin_servers_on_network),
                style = JellyCastText.Subheadline,
                color = JellyCastColors.TextMuted,
                modifier = Modifier.weight(1f),
            )
            if (uiState.isSearching) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    color = JellyCastColors.TextMuted,
                    strokeWidth = 2.dp,
                )
            }
        }
        val connecting = uiState.phase == SignInPhase.CheckingServer
        uiState.foundServers.forEach { found ->
            ServerRow(found, enabled = !connecting, onClick = { onServerClick(found.server) })
        }
        if (uiState.foundServers.isEmpty()) {
            Text(
                stringResource(
                    if (uiState.isSearching) {
                        R.string.settings_signin_searching
                    } else {
                        R.string.settings_signin_none_found
                    },
                ),
                style = JellyCastText.Footnote,
                color = JellyCastColors.TextMuted,
            )
            if (!uiState.isSearching) {
                OutlinedButton(onClick = onSearchAgain) {
                    Icon(painterResource(R.drawable.ic_settings_refresh), null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_signin_search_again))
                }
            }
        }
    }
}

@Composable
private fun ServerRow(found: FoundServer, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(JellyCastColors.Surface)
            .pressable(onClick = onClick, enabled = enabled)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                found.server.name,
                style = JellyCastText.Body.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Text,
            )
            Text(serverDetail(found), style = JellyCastText.Footnote, color = JellyCastColors.TextMuted)
        }
        if (found.isCurrent) {
            Text(
                stringResource(R.string.settings_signin_current),
                style = JellyCastText.Caption.copy(fontWeight = FontWeight.SemiBold),
                color = JellyCastColors.Accent,
            )
        }
        Icon(
            painterResource(R.drawable.ic_settings_chevron_right),
            null,
            Modifier.size(18.dp),
            tint = JellyCastColors.TextMuted,
        )
    }
}

@Composable
private fun serverDetail(found: FoundServer): String {
    val address = hostAndPort(found.server.url)
    return when (val check = found.check) {
        ServerCheck.Checking -> address
        is ServerCheck.Ready -> stringResource(R.string.settings_signin_server_version, address, check.version)
        ServerCheck.Unreachable -> stringResource(R.string.settings_signin_not_responding, address)
    }
}

private fun hostAndPort(url: String): String = try {
    val uri = URI(url)
    val host = uri.host ?: url
    if (uri.port == -1) host else "$host:${uri.port}"
} catch (_: URISyntaxException) {
    url
}

@Composable
private fun AddressForm(
    uiState: SignInUiState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
) {
    val focusManager = LocalFocusManager.current
    val checking = uiState.phase == SignInPhase.CheckingServer
    val connect = {
        focusManager.clearFocus()
        onConnect()
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            stringResource(
                if (uiState.foundServers.isEmpty()) {
                    R.string.settings_signin_server_address
                } else {
                    R.string.settings_signin_or_enter_address
                },
            ),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
        )
        val error = (uiState.phase as? SignInPhase.Failed)?.error
        OutlinedTextField(
            value = uiState.address,
            onValueChange = onAddressChange,
            placeholder = { Text(stringResource(R.string.settings_signin_address_placeholder)) },
            singleLine = true,
            isError = error != null,
            shape = RoundedCornerShape(12.dp),
            keyboardOptions =
                KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
            keyboardActions = KeyboardActions(onGo = { connect() }),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(error.message(), style = JellyCastText.Footnote, color = JellyCastColors.Warning)
        }
        Button(
            onClick = connect,
            enabled = uiState.canConnect && !checking,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        ) {
            if (checking) {
                CircularProgressIndicator(
                    Modifier.size(16.dp),
                    color = JellyCastColors.OnAccent,
                    strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                stringResource(R.string.settings_signin_connect),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun QuickConnectCode(serverName: String, code: String, onCancel: () -> Unit) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            stringResource(R.string.settings_signin_sign_in_to, serverName),
            style = JellyCastText.Headline,
            color = JellyCastColors.Text,
        )
        SelectionContainer {
            Text(
                code,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 6.sp,
                color = JellyCastColors.Text,
            )
        }
        Text(
            stringResource(R.string.settings_signin_quick_connect_hint),
            style = JellyCastText.Subheadline,
            color = JellyCastColors.TextMuted,
            textAlign = TextAlign.Center,
        )
        CircularProgressIndicator(Modifier.size(24.dp), color = JellyCastColors.TextMuted, strokeWidth = 2.dp)
        OutlinedButton(onClick = onCancel) {
            Text(stringResource(R.string.settings_signin_cancel))
        }
    }
}

@Composable
private fun SignInError.message(): String = when (this) {
    SignInError.InvalidAddress -> stringResource(R.string.settings_signin_error_invalid)
    SignInError.NotJellyfin -> stringResource(R.string.settings_signin_error_not_jellyfin)
    SignInError.Unreachable -> stringResource(R.string.settings_signin_error_unreachable)
    SignInError.Offline -> stringResource(R.string.settings_signin_error_offline)
    is SignInError.UnsupportedVersion -> stringResource(R.string.settings_signin_error_version, version)
    SignInError.QuickConnectDisabled -> stringResource(R.string.settings_signin_error_qc_disabled)
    SignInError.QuickConnectFailed -> stringResource(R.string.settings_signin_error_qc_failed)
}

@Preview
@Composable
private fun SignInContentPreview() {
    JellyCastTheme {
        SignInContent(
            uiState =
                SignInUiState(
                    address = "192.168.1.4:8096",
                    isSearching = false,
                    foundServers =
                        listOf(
                            FoundServer(
                                DiscoveredServer("a", "home server", "http://192.168.1.4:8096"),
                                ServerCheck.Ready("10.11.11"),
                                isCurrent = true,
                            ),
                            FoundServer(
                                DiscoveredServer("b", "nas", "http://192.168.1.20:8096"),
                                ServerCheck.Unreachable,
                            ),
                        ),
                ),
            onAddressChange = {},
            onConnect = {},
            onServerClick = {},
            onSearchAgain = {},
            onCancel = {},
        )
    }
}

@Preview
@Composable
private fun SignInCodePreview() {
    JellyCastTheme {
        SignInContent(
            uiState = SignInUiState(phase = SignInPhase.WaitingForApproval("home server", "804134")),
            onAddressChange = {},
            onConnect = {},
            onServerClick = {},
            onSearchAgain = {},
            onCancel = {},
        )
    }
}
