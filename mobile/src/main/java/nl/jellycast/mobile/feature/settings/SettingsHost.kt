package nl.jellycast.mobile.feature.settings

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.navigation.LocalAppNavigation
import org.koin.compose.viewmodel.koinViewModel

/**
 * The settings as a Material full-screen dialog, shown while `AppNavigation.showsSettings` is set
 * (iOS shows a sheet). AI chat opens inside it, with Back returning to the settings.
 */
@Composable
fun SettingsHost() {
    val navigation = LocalAppNavigation.current
    if (!navigation.showsSettings) return
    val close = { navigation.showsSettings = false }
    Dialog(
        onDismissRequest = close,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        var showsAiChat by rememberSaveable { mutableStateOf(false) }
        BackHandler(enabled = showsAiChat) { showsAiChat = false }
        if (showsAiChat) {
            AiChatSettingsScreen(onBack = { showsAiChat = false })
        } else {
            SettingsScreen(onOpenAiChat = { showsAiChat = true }, onClose = close)
        }
    }
}

@Composable
private fun SettingsScreen(
    onOpenAiChat: () -> Unit,
    onClose: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onRefreshLibrary = viewModel::onRefreshLibrary,
        onQualityChange = viewModel::onQualityChange,
        onRatingSourceChange = viewModel::onRatingSourceChange,
        onOpenAiChat = onOpenAiChat,
        onSignOut = {
            onClose()
            viewModel.onSignOut()
        },
        onClose = onClose,
        downloadsSection = { DownloadsSettingsSection() },
    )
}

@Composable
private fun AiChatSettingsScreen(
    onBack: () -> Unit,
    viewModel: AiChatSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    AiChatSettingsContent(
        uiState = uiState,
        onProviderChange = viewModel::onProviderChange,
        onSaveKey = viewModel::onSaveKey,
        onTestKey = viewModel::onTestKey,
        onRemoveKey = viewModel::onRemoveKey,
        onSearchesWebChange = viewModel::onSearchesWebChange,
        onAvoidsSpoilersChange = viewModel::onAvoidsSpoilersChange,
        onBack = onBack,
    )
}
