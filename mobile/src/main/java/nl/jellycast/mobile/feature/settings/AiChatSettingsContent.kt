package nl.jellycast.mobile.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.chat.ChatError
import nl.jellycast.mobile.core.chat.ChatProvider
import nl.jellycast.mobile.core.chat.ChatSettingsState
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.ui.chat.message

/** The AI service and its API key. Without a key the player has no Chat tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatSettingsContent(
    uiState: AiChatSettingsUiState,
    onProviderChange: (ChatProvider) -> Unit,
    onSaveKey: (String) -> Unit,
    onTestKey: () -> Unit,
    onRemoveKey: () -> Unit,
    onSearchesWebChange: (Boolean) -> Unit,
    onAvoidsSpoilersChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings = uiState.settings
    Scaffold(
        modifier = modifier.fillMaxSize().imePadding(),
        containerColor = JellyCastColors.Background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_ai_chat)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(R.string.settings_back),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = JellyCastColors.Background),
            )
        },
    ) { padding ->
        SettingsColumn(Modifier.padding(padding)) {
            SettingsSection(title = stringResource(R.string.settings_chat_service)) {
                ChatProvider.entries.forEach { provider ->
                    SettingsRadioRow(
                        title = provider.title,
                        selected = provider == settings.provider,
                        onClick = { onProviderChange(provider) },
                    )
                }
            }
            KeySection(uiState, onSaveKey, onTestKey, onRemoveKey)
            SettingsSection(footer = stringResource(R.string.settings_chat_search_web_footer)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_chat_search_web),
                    checked = settings.searchesWeb,
                    onCheckedChange = onSearchesWebChange,
                )
            }
            SettingsSection(footer = stringResource(R.string.settings_chat_no_spoilers_footer)) {
                SettingsSwitchRow(
                    title = stringResource(R.string.settings_chat_no_spoilers),
                    checked = settings.avoidsSpoilers,
                    onCheckedChange = onAvoidsSpoilersChange,
                )
            }
        }
    }
}

@Composable
private fun KeySection(
    uiState: AiChatSettingsUiState,
    onSaveKey: (String) -> Unit,
    onTestKey: () -> Unit,
    onRemoveKey: () -> Unit,
) {
    val settings = uiState.settings
    var isEditing by rememberSaveable { mutableStateOf(false) }
    // The key itself is never put in saved state.
    var draft by remember { mutableStateOf("") }
    LaunchedEffect(settings.provider) {
        isEditing = false
        draft = ""
    }
    val save = {
        onSaveKey(draft)
        draft = ""
        isEditing = false
    }
    SettingsSection(
        title = stringResource(R.string.settings_chat_api_key),
        footer = stringResource(R.string.settings_chat_key_footer),
    ) {
        val masked = settings.maskedKey
        if (masked != null && !isEditing) {
            SettingsRow(
                title = masked,
                trailing = {
                    TextButton(onClick = { isEditing = true }) {
                        Text(stringResource(R.string.settings_chat_change))
                    }
                },
            )
            SettingsRow(
                title = stringResource(R.string.settings_chat_test_key),
                titleColor = JellyCastColors.Accent,
                enabled = uiState.check != KeyCheck.Checking,
                onClick = onTestKey,
                trailing = { CheckStatus(uiState.check) },
            )
            SettingsRow(
                title = stringResource(R.string.settings_chat_remove_key),
                titleColor = MaterialTheme.colorScheme.error,
                onClick = onRemoveKey,
            )
        } else {
            Column(
                Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text(settings.provider.keyPlaceholder) },
                    singleLine = true,
                    textStyle = JellyCastText.Body.copy(fontFamily = FontFamily.Monospace),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions =
                        KeyboardOptions(
                            capitalization = KeyboardCapitalization.None,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done,
                        ),
                    keyboardActions = KeyboardActions(onDone = { if (draft.isNotBlank()) save() }),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = save, enabled = draft.isNotBlank()) {
                    Text(stringResource(R.string.settings_chat_save_key))
                }
            }
        }
    }
}

@Composable
private fun CheckStatus(check: KeyCheck) {
    when (check) {
        KeyCheck.Idle -> Unit

        KeyCheck.Checking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)

        KeyCheck.Valid -> Icon(
            painterResource(R.drawable.ic_settings_check_circle),
            contentDescription = null,
            tint = JellyCastColors.Accent,
        )

        is KeyCheck.Failed -> Text(
            if (check.error == ChatError.InvalidKey) {
                stringResource(R.string.settings_chat_not_accepted)
            } else {
                check.error.message()
            },
            style = JellyCastText.Footnote,
            color = JellyCastColors.Warning,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Preview
@Composable
private fun AiChatSettingsContentPreview() {
    JellyCastTheme {
        AiChatSettingsContent(
            uiState =
                AiChatSettingsUiState(
                    settings = ChatSettingsState(maskedKey = "sk-ant-•••• 4f2a"),
                    check = KeyCheck.Valid,
                ),
            onProviderChange = {},
            onSaveKey = {},
            onTestKey = {},
            onRemoveKey = {},
            onSearchesWebChange = {},
            onAvoidsSpoilersChange = {},
            onBack = {},
        )
    }
}
