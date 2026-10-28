package nl.jellycast.tv.feature.login

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import nl.jellycast.core.model.QuickConnectState
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.AuthButton
import nl.jellycast.tv.core.designsystem.component.AuthContentWidth
import nl.jellycast.tv.core.designsystem.component.AuthDivider
import nl.jellycast.tv.core.designsystem.component.AuthError
import nl.jellycast.tv.core.designsystem.component.AuthHeading
import nl.jellycast.tv.core.designsystem.component.AuthLayout
import nl.jellycast.tv.core.designsystem.component.AuthText
import nl.jellycast.tv.core.designsystem.component.TvTextField
import nl.jellycast.tv.core.designsystem.component.rememberSkeletonClock
import nl.jellycast.tv.core.designsystem.component.skeletonBlock
import nl.jellycast.tv.core.designsystem.theme.Dimens
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun LoginScreen(
    onChangeServer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LoginContent(
        uiState = uiState,
        actions =
            LoginActions(
                onRetryQuickConnect = viewModel::onRetryQuickConnect,
                onUsePassword = viewModel::onUsePassword,
                onUseQuickConnect = viewModel::onUseQuickConnect,
                onUsernameChange = viewModel::onUsernameChange,
                onPasswordChange = viewModel::onPasswordChange,
                onPasswordLogin = viewModel::onPasswordLogin,
                onChangeServer = onChangeServer,
            ),
        modifier = modifier,
    )
}

data class LoginActions(
    val onRetryQuickConnect: () -> Unit,
    val onUsePassword: () -> Unit,
    val onUseQuickConnect: () -> Unit,
    val onUsernameChange: (String) -> Unit,
    val onPasswordChange: (String) -> Unit,
    val onPasswordLogin: () -> Unit,
    val onChangeServer: () -> Unit,
)

@Composable
fun LoginContent(uiState: LoginUiState, actions: LoginActions, modifier: Modifier = Modifier) {
    // Coming back from the server screen restores focus on that button instead of the page.
    var changeServerFocused by rememberSaveable { mutableStateOf(false) }
    val changeServerRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (changeServerFocused) changeServerRequester.requestFocus() }

    val canUseCode = uiState.quickConnect != QuickConnectState.Disabled
    val backReturnsToCodePage = uiState.mode == LoginMode.Password && canUseCode
    BackHandler(enabled = backReturnsToCodePage) {
        actions.onUseQuickConnect()
    }

    AuthLayout(
        modifier = modifier,
        footer = {
            ServerStatus(host = uiState.serverHost, name = uiState.serverName)
            ChangeServerButton(
                onClick = actions.onChangeServer,
                modifier =
                    Modifier
                        .focusRequester(changeServerRequester)
                        .onFocusChanged { if (it.isFocused) changeServerFocused = true },
            )
        },
    ) {
        val sectionModifier =
            Modifier.onFocusChanged { if (it.hasFocus) changeServerFocused = false }
        when (uiState.mode) {
            LoginMode.QuickConnect ->
                QuickConnectSection(
                    state = uiState.quickConnect,
                    actions = actions,
                    takeFocus = !changeServerFocused,
                    modifier = sectionModifier,
                )

            LoginMode.Password ->
                PasswordSection(
                    uiState = uiState,
                    actions = actions,
                    canUseCode = canUseCode,
                    takeFocus = !changeServerFocused,
                    modifier = sectionModifier,
                )
        }
    }
}

@Composable
private fun QuickConnectSection(
    state: QuickConnectState,
    actions: LoginActions,
    takeFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val passwordRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (takeFocus) passwordRequester.requestFocus() }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AuthHeading(stringResource(R.string.login_qc_heading))
        CodeBoxes(code = (state as? QuickConnectState.WaitingForApproval)?.code)
        AuthText(stringResource(R.string.login_qc_instructions))
        if (state == QuickConnectState.Failed) {
            AuthError(stringResource(R.string.login_qc_failed))
        } else {
            QuickConnectStatus(state)
        }
        AuthDivider()
        AuthButton(
            text = stringResource(R.string.login_use_password),
            onClick = actions.onUsePassword,
            icon = painterResource(R.drawable.ic_lock),
            modifier =
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .focusRequester(passwordRequester),
        )
        // Below the password button, so focus does not shift when it appears.
        if (state == QuickConnectState.Failed) {
            AuthButton(
                text = stringResource(R.string.login_qc_retry),
                onClick = actions.onRetryQuickConnect,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

private const val CODE_LENGTH = 6
private val CodeBoxWidth = 50.dp
private val CodeBoxHeight = 64.dp
private val CodeBoxSpacing = 8.dp
private val CodeGroupGap = 12.dp

/** Without a code the boxes are shown empty, so nothing shifts when the code arrives. */
@Composable
private fun CodeBoxes(code: String?, modifier: Modifier = Modifier) {
    val slots = code?.length?.takeIf { it != CODE_LENGTH } ?: CODE_LENGTH
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(CodeBoxSpacing)) {
        for (i in 0 until slots) {
            if (i == slots / 2) Spacer(Modifier.width(CodeGroupGap))
            Box(
                modifier =
                    Modifier
                        .size(width = CodeBoxWidth, height = CodeBoxHeight)
                        .background(JellyCastColors.Surface.copy(alpha = 0.7f), Dimens.CardShape)
                        .border(1.dp, JellyCastColors.Rail, Dimens.CardShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = code?.getOrNull(i)?.toString().orEmpty(),
                    style = MaterialTheme.typography.displaySmall,
                )
            }
        }
    }
}

@Composable
private fun QuickConnectStatus(state: QuickConnectState, modifier: Modifier = Modifier) {
    val text =
        when (state) {
            is QuickConnectState.WaitingForApproval -> R.string.login_qc_waiting
            QuickConnectState.Authenticated -> R.string.login_qc_authenticated
            else -> R.string.login_qc_starting
        }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dot = Modifier.size(8.dp)
        if (state is QuickConnectState.WaitingForApproval) {
            // Pulses in the draw phase on its own layer, so the rest of the screen is not redrawn.
            val clock = rememberSkeletonClock()
            Box(
                dot.graphicsLayer().skeletonBlock(clock, 0, JellyCastColors.Accent, 4.dp),
            )
        } else {
            val color =
                if (state == QuickConnectState.Authenticated) {
                    JellyCastColors.Accent
                } else {
                    JellyCastColors.Rail
                }
            Box(dot.background(color, CircleShape))
        }
        Text(text = stringResource(text), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PasswordSection(
    uiState: LoginUiState,
    actions: LoginActions,
    canUseCode: Boolean,
    takeFocus: Boolean,
    modifier: Modifier = Modifier,
) {
    val usernameRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (takeFocus) usernameRequester.requestFocus() }

    val message =
        when (uiState.passwordStatus) {
            PasswordStatus.InvalidCredentials -> stringResource(R.string.login_error_credentials)
            PasswordStatus.Unreachable -> stringResource(R.string.login_error_unreachable)
            PasswordStatus.Idle, PasswordStatus.Busy -> null
        }
    val submitLabel =
        if (uiState.passwordStatus == PasswordStatus.Busy) {
            R.string.login_busy
        } else {
            R.string.login_submit
        }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        AuthHeading(stringResource(R.string.login_password_heading))
        if (!canUseCode) AuthText(stringResource(R.string.login_qc_disabled))
        TvTextField(
            value = uiState.username,
            onValueChange = actions.onUsernameChange,
            label = stringResource(R.string.login_username),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier.width(AuthContentWidth).focusRequester(usernameRequester),
        )
        TvTextField(
            value = uiState.password,
            onValueChange = actions.onPasswordChange,
            label = stringResource(R.string.login_password),
            isPassword = true,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            onImeAction = actions.onPasswordLogin,
            modifier = Modifier.width(AuthContentWidth),
        )
        if (message != null) AuthError(message)
        AuthButton(
            text = stringResource(submitLabel),
            onClick = actions.onPasswordLogin,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (canUseCode) {
            AuthDivider()
            AuthButton(
                text = stringResource(R.string.login_use_code),
                onClick = actions.onUseQuickConnect,
                icon = painterResource(R.drawable.ic_phone),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ServerStatus(host: String, name: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).background(JellyCastColors.Online, CircleShape))
        Text(
            text = stringResource(R.string.login_connected, host, name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChangeServerButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(Dimens.PillShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                contentColor = JellyCastColors.Text,
                focusedContainerColor = JellyCastColors.Text,
                focusedContentColor = JellyCastColors.Background,
                pressedContainerColor = JellyCastColors.Text,
                pressedContentColor = JellyCastColors.Background,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = Dimens.FOCUS_SCALE),
    ) {
        Text(
            text = stringResource(R.string.login_change_server),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

private val PreviewActions = LoginActions({}, {}, {}, {}, {}, {}, {})

@Preview(device = Devices.TV_1080p)
@Composable
private fun LoginQuickConnectPreview() {
    JellyCastTheme {
        LoginContent(
            uiState =
                LoginUiState(
                    serverName = "home server",
                    serverHost = "192.168.1.4",
                    quickConnect = QuickConnectState.WaitingForApproval("482913"),
                ),
            actions = PreviewActions,
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun LoginPasswordPreview() {
    JellyCastTheme {
        LoginContent(
            uiState =
                LoginUiState(
                    serverName = "home server",
                    serverHost = "192.168.1.4",
                    mode = LoginMode.Password,
                    quickConnect = QuickConnectState.WaitingForApproval("482913"),
                    username = "claude",
                    passwordStatus = PasswordStatus.InvalidCredentials,
                ),
            actions = PreviewActions,
        )
    }
}
