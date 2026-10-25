package nl.jellycast.tv.feature.server

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import nl.jellycast.tv.R
import nl.jellycast.tv.core.designsystem.component.AuthButton
import nl.jellycast.tv.core.designsystem.component.AuthContentWidth
import nl.jellycast.tv.core.designsystem.component.AuthDivider
import nl.jellycast.tv.core.designsystem.component.AuthError
import nl.jellycast.tv.core.designsystem.component.AuthHeading
import nl.jellycast.tv.core.designsystem.component.AuthLayout
import nl.jellycast.tv.core.designsystem.component.AuthText
import nl.jellycast.tv.core.designsystem.component.FocusCard
import nl.jellycast.tv.core.designsystem.component.TvTextField
import nl.jellycast.tv.core.designsystem.theme.JellyCastColors
import nl.jellycast.tv.core.designsystem.theme.JellyCastTheme
import nl.jellycast.tv.core.model.ServerInfo
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun ServerScreen(
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ServerViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigateToLogin by rememberUpdatedState(onNavigateToLogin)

    LaunchedEffect(uiState.serverSaved) {
        if (uiState.serverSaved) {
            viewModel.onNavigated()
            currentOnNavigateToLogin()
        }
    }

    ServerContent(
        uiState = uiState,
        onAddressChange = viewModel::onAddressChange,
        onConnect = viewModel::onConnect,
        onServerClick = viewModel::onServerClick,
        onSearchAgain = viewModel::onSearchAgain,
        onManualEntryClick = viewModel::onManualEntryClick,
        onManualEntryClose = viewModel::onManualEntryClose,
        modifier = modifier,
    )
}

@Composable
fun ServerContent(
    uiState: ServerUiState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onServerClick: (ServerInfo) -> Unit,
    onSearchAgain: () -> Unit,
    onManualEntryClick: () -> Unit,
    onManualEntryClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstCardRequester = remember { FocusRequester() }
    val fieldRequester = remember { FocusRequester() }
    val connectRequester = remember { FocusRequester() }
    LaunchedEffect(uiState.manualEntry) {
        when {
            !uiState.manualEntry -> firstCardRequester.requestFocus()
            uiState.address.isEmpty() -> fieldRequester.requestFocus()
            else -> connectRequester.requestFocus()
        }
    }
    BackHandler(enabled = uiState.manualEntry, onBack = onManualEntryClose)

    AuthLayout(modifier = modifier) {
        AuthHeading(stringResource(R.string.server_title))
        if (uiState.manualEntry) {
            ManualEntry(
                uiState = uiState,
                onAddressChange = onAddressChange,
                onConnect = onConnect,
                onManualEntryClose = onManualEntryClose,
                fieldRequester = fieldRequester,
                connectRequester = connectRequester,
            )
        } else {
            Discovery(
                uiState = uiState,
                onServerClick = onServerClick,
                onSearchAgain = onSearchAgain,
                onManualEntryClick = onManualEntryClick,
                firstCardRequester = firstCardRequester,
            )
        }
    }
}

@Composable
private fun ColumnScope.Discovery(
    uiState: ServerUiState,
    onServerClick: (ServerInfo) -> Unit,
    onSearchAgain: () -> Unit,
    onManualEntryClick: () -> Unit,
    firstCardRequester: FocusRequester,
) {
    val servers = uiState.discoveredServers
    val nothingFound = servers.isEmpty() && uiState.discovery == DiscoveryStatus.Done
    DiscoveryMessage(uiState = uiState, nothingFound = nothingFound)

    // One call site for searching, found and nothing found keeps it the same focused element.
    val first = servers.firstOrNull()
    val firstCard =
        when {
            first != null -> first.toCardText()

            nothingFound -> CardText(
                title = stringResource(R.string.server_manual),
                detail = stringResource(R.string.server_manual_example),
            )

            else -> CardText(title = stringResource(R.string.server_discovery_searching_button))
        }
    ServerCard(
        text = firstCard,
        onClick = {
            when {
                first != null -> onServerClick(first.server)
                nothingFound -> onManualEntryClick()
            }
        },
        modifier = Modifier.focusRequester(firstCardRequester),
    )
    for (server in servers.drop(1)) {
        ServerCard(text = server.toCardText(), onClick = { onServerClick(server.server) })
    }

    AuthDivider()
    val buttonModifier = Modifier.align(Alignment.CenterHorizontally)
    if (nothingFound) {
        AuthButton(
            text = stringResource(R.string.server_discovery_retry),
            onClick = onSearchAgain,
            icon = painterResource(R.drawable.ic_restart),
            modifier = buttonModifier,
        )
    } else {
        AuthButton(
            text = stringResource(R.string.server_manual),
            onClick = onManualEntryClick,
            modifier = buttonModifier,
        )
    }
}

@Composable
private fun DiscoveryMessage(uiState: ServerUiState, nothingFound: Boolean) {
    val error = uiState.status.messageRes()
    if (error != null) {
        val (res, arg) = error
        AuthError(if (arg != null) stringResource(res, arg) else stringResource(res))
    } else {
        val text =
            when {
                uiState.status == ServerStatus.Checking -> R.string.server_checking
                uiState.discoveredServers.isNotEmpty() -> R.string.server_discovery_found
                nothingFound -> R.string.server_discovery_none
                else -> R.string.server_discovery_searching
            }
        AuthText(stringResource(text), minLines = 2)
    }
}

@Composable
private fun ColumnScope.ManualEntry(
    uiState: ServerUiState,
    onAddressChange: (String) -> Unit,
    onConnect: () -> Unit,
    onManualEntryClose: () -> Unit,
    fieldRequester: FocusRequester,
    connectRequester: FocusRequester,
) {
    AuthText(stringResource(R.string.server_subtitle))
    TvTextField(
        value = uiState.address,
        onValueChange = onAddressChange,
        label = stringResource(R.string.server_address_label),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
        ),
        onImeAction = onConnect,
        modifier = Modifier.width(AuthContentWidth).focusRequester(fieldRequester),
    )
    uiState.status.messageRes()?.let { (res, arg) ->
        AuthError(if (arg != null) stringResource(res, arg) else stringResource(res))
    }
    val connectLabel =
        if (uiState.status == ServerStatus.Checking) {
            R.string.server_checking
        } else {
            R.string.server_connect
        }
    AuthButton(
        text = stringResource(connectLabel),
        onClick = onConnect,
        modifier =
            Modifier
                .align(Alignment.CenterHorizontally)
                .focusRequester(connectRequester),
    )
    AuthDivider()
    AuthButton(
        text = stringResource(R.string.server_back_to_discovery),
        onClick = onManualEntryClose,
        icon = painterResource(R.drawable.ic_search),
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}

private data class CardText(
    val title: String,
    val detail: String? = null,
    val badge: String? = null,
)

@Composable
private fun DiscoveredServer.toCardText(): CardText {
    val address = server.url.substringAfter("://").trimEnd('/')
    val check =
        when (val status = check) {
            ServerCardStatus.Checking -> null

            is ServerCardStatus.Ready -> stringResource(
                R.string.server_card_version,
                status.version,
            )

            is ServerCardStatus.TooOld -> stringResource(
                R.string.server_card_too_old,
                status.version,
            )

            ServerCardStatus.Unreachable -> stringResource(R.string.server_card_unreachable)
        }
    return CardText(
        title = server.name,
        detail = listOfNotNull(address, check).joinToString(" · "),
        badge = if (isCurrent) stringResource(R.string.server_card_current) else null,
    )
}

@Composable
private fun ServerCard(text: CardText, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FocusCard(
        onClick = onClick,
        containerColor = JellyCastColors.Rail,
        modifier = modifier.size(width = AuthContentWidth, height = CardHeight),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = CardHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(CardContentSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                CardTitle(text.title)
                text.detail?.let { CardDetail(it) }
            }
            text.badge?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = JellyCastColors.Accent,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun CardTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
        color = JellyCastColors.Text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun CardDetail(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = JellyCastColors.TextMuted,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private val CardHeight = 64.dp
private val CardHorizontalPadding = 18.dp
private val CardContentSpacing = 12.dp

private fun ServerStatus.messageRes(): Pair<Int, String?>? = when (this) {
    ServerStatus.Idle, ServerStatus.Checking -> null
    ServerStatus.InvalidAddress -> R.string.server_error_invalid to null
    ServerStatus.Unreachable -> R.string.server_error_unreachable to null
    ServerStatus.NotJellyfin -> R.string.server_error_not_jellyfin to null
    is ServerStatus.UnsupportedVersion -> R.string.server_error_version to version
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun ServerContentPreview() {
    JellyCastTheme {
        ServerContent(
            uiState = ServerUiState(
                discovery = DiscoveryStatus.Done,
                discoveredServers = listOf(
                    DiscoveredServer(
                        server = ServerInfo(url = "http://192.168.1.4:8096", name = "home server"),
                        check = ServerCardStatus.Ready("10.11.2"),
                        isCurrent = true,
                    ),
                    DiscoveredServer(
                        server = ServerInfo(url = "http://192.168.1.9:8096", name = "attic"),
                        check = ServerCardStatus.TooOld("10.9.11"),
                    ),
                ),
            ),
            onAddressChange = {},
            onConnect = {},
            onServerClick = {},
            onSearchAgain = {},
            onManualEntryClick = {},
            onManualEntryClose = {},
        )
    }
}

@Preview(device = Devices.TV_1080p)
@Composable
private fun ServerContentNothingFoundPreview() {
    JellyCastTheme {
        ServerContent(
            uiState = ServerUiState(discovery = DiscoveryStatus.Done),
            onAddressChange = {},
            onConnect = {},
            onServerClick = {},
            onSearchAgain = {},
            onManualEntryClick = {},
            onManualEntryClose = {},
        )
    }
}
