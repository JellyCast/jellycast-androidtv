package nl.jellycast.mobile.ui.chat

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import nl.jellycast.mobile.core.chat.ChatSettings
import nl.jellycast.mobile.ui.info.InfoSubject
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/** Whether the Chat tab shows: an API key is stored for the chosen provider. */
@Composable
fun isChatAvailable(): Boolean {
    val settings: ChatSettings = koinInject()
    val state by settings.state.collectAsStateWithLifecycle()
    return state.hasKey
}

/**
 * The AI chat about [subject] (one conversation per movie or series), with its input bar at the
 * end of the conversation.
 */
@Composable
fun ChatTab(subject: InfoSubject, modifier: Modifier = Modifier) {
    val viewModel: ChatViewModel = koinViewModel(key = "chat-${subject.chatKey()}")
    LaunchedEffect(viewModel, subject) { viewModel.onSubject(subject) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    ChatContent(
        uiState = uiState,
        onSend = viewModel::onSend,
        onRetry = viewModel::onRetry,
        onNewConversation = viewModel::onNewConversation,
        modifier = modifier,
    )
}
