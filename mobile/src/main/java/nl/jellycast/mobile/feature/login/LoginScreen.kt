package nl.jellycast.mobile.feature.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.core.model.QuickConnectState
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.component.BrandHeader
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun LoginScreen(onChangeServer: () -> Unit, viewModel: LoginViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginContent(
        uiState = uiState,
        onUsernameChange = viewModel::onUsernameChange,
        onPasswordChange = viewModel::onPasswordChange,
        onPasswordLogin = viewModel::onPasswordLogin,
        onStartQuickConnect = viewModel::onStartQuickConnect,
        onCancelQuickConnect = viewModel::onCancelQuickConnect,
        onChangeServer = onChangeServer,
    )
}

@Composable
fun LoginContent(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordLogin: () -> Unit,
    onStartQuickConnect: () -> Unit,
    onCancelQuickConnect: () -> Unit,
    onChangeServer: () -> Unit,
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
            val quickConnect = uiState.quickConnect
            if (quickConnect == null) {
                PasswordForm(uiState, onUsernameChange, onPasswordChange, onPasswordLogin)
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                OutlinedButton(onClick = onStartQuickConnect, Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.login_use_code))
                }
            } else {
                QuickConnectPanel(quickConnect, onStartQuickConnect, onCancelQuickConnect)
            }
            if (uiState.serverName.isNotEmpty()) {
                Text(
                    stringResource(R.string.login_connected, uiState.serverName, uiState.serverUrl),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            TextButton(onClick = onChangeServer) {
                Text(stringResource(R.string.login_change_server))
            }
        }
    }
}

@Composable
private fun PasswordForm(
    uiState: LoginUiState,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPasswordLogin: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(
        stringResource(R.string.login_password_heading),
        style = MaterialTheme.typography.titleLarge,
    )
    OutlinedTextField(
        value = uiState.username,
        onValueChange = onUsernameChange,
        label = { Text(stringResource(R.string.login_username)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        modifier = Modifier.fillMaxWidth(),
    )
    val error =
        when (uiState.passwordStatus) {
            PasswordStatus.InvalidCredentials -> stringResource(R.string.login_error_credentials)
            PasswordStatus.Unreachable -> stringResource(R.string.login_error_unreachable)
            else -> null
        }
    OutlinedTextField(
        value = uiState.password,
        onValueChange = onPasswordChange,
        label = { Text(stringResource(R.string.login_password)) },
        singleLine = true,
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
        keyboardActions = KeyboardActions(onGo = { onPasswordLogin() }),
        modifier = Modifier.fillMaxWidth(),
    )
    val busy = uiState.passwordStatus == PasswordStatus.Busy
    Button(
        onClick = onPasswordLogin,
        enabled = !busy && uiState.username.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (busy) R.string.login_busy else R.string.login_submit))
    }
}

@Composable
private fun QuickConnectPanel(
    state: QuickConnectState,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(stringResource(R.string.login_qc_heading), style = MaterialTheme.typography.titleLarge)
    Text(
        stringResource(R.string.login_qc_instructions),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    when (state) {
        QuickConnectState.Starting -> StatusText(stringResource(R.string.login_qc_starting))

        is QuickConnectState.WaitingForApproval -> {
            Text(
                state.code.chunked(QUICK_CONNECT_GROUP).joinToString(" "),
                fontFamily = FontFamily.Monospace,
                fontSize = 40.sp,
                letterSpacing = 4.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            StatusText(stringResource(R.string.login_qc_waiting))
        }

        QuickConnectState.Authenticated -> StatusText(
            stringResource(R.string.login_qc_authenticated),
        )

        QuickConnectState.Disabled -> StatusText(stringResource(R.string.login_qc_disabled))

        QuickConnectState.Failed -> {
            StatusText(stringResource(R.string.login_qc_failed))
            OutlinedButton(onClick = onRetry, Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.login_qc_retry))
            }
        }
    }
    OutlinedButton(onClick = onCancel, Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_use_password))
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth(),
    )
}

private const val QUICK_CONNECT_GROUP = 3

@Preview
@Composable
private fun LoginContentPreview() {
    JellyCastTheme {
        LoginContent(
            uiState = LoginUiState(serverName = "Home", serverUrl = "http://192.168.1.4:8096"),
            onUsernameChange = {},
            onPasswordChange = {},
            onPasswordLogin = {},
            onStartQuickConnect = {},
            onCancelQuickConnect = {},
            onChangeServer = {},
        )
    }
}
