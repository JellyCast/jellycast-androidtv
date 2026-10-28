package nl.jellycast.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.chat.ChatError
import nl.jellycast.mobile.core.chat.ChatRole
import nl.jellycast.mobile.core.chat.ChatSource
import nl.jellycast.mobile.core.chat.ChatSubject
import nl.jellycast.mobile.core.designsystem.component.pressable
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.JellyCastText
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme
import nl.jellycast.mobile.core.ui.episodeCode

/** Questions about what plays, answered by the AI service the user set a key for. */
@Composable
fun ChatContent(
    uiState: ChatUiState,
    onSend: (String) -> Unit,
    onRetry: () -> Unit,
    onNewConversation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chat = uiState.conversation
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ContextRow(uiState, onNewConversation)
        if (chat.messages.isEmpty()) {
            SuggestionChips(uiState.subject, enabled = !chat.isSending, onSend = onSend)
        } else {
            chat.messages.forEach { message -> ChatBubble(message) }
        }
        if (chat.isSending) ChatWorking(chat.steps, chat.draft)
        chat.error?.let { error ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(error.message(), style = JellyCastText.Subheadline, color = JellyCastColors.TextMuted)
                OutlinedButton(onClick = onRetry) {
                    Text(stringResource(R.string.settings_chat_try_again))
                }
            }
        }
        if (chat.messages.isNotEmpty()) {
            Text(
                stringResource(R.string.settings_chat_source_note, uiState.provider.title),
                style = JellyCastText.Caption,
                color = JellyCastColors.TextMuted,
            )
        }
        ChatInputBar(
            title = uiState.subject?.title().orEmpty(),
            isSending = chat.isSending || uiState.subject == null,
            onSend = onSend,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun ContextRow(uiState: ChatUiState, onNewConversation: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            painterResource(
                if (uiState.avoidsSpoilers) R.drawable.ic_settings_visibility_off else R.drawable.ic_settings_visibility,
            ),
            contentDescription = null,
            tint = JellyCastColors.TextMuted,
            modifier = Modifier.size(15.dp),
        )
        Text(
            contextTitle(uiState),
            style = JellyCastText.Footnote,
            color = JellyCastColors.TextMuted,
            modifier = Modifier.weight(1f),
        )
        if (uiState.conversation.messages.isNotEmpty()) {
            TextButton(onClick = onNewConversation) {
                Icon(painterResource(R.drawable.ic_settings_refresh), null, Modifier.size(16.dp))
                Text(
                    stringResource(R.string.settings_chat_new_conversation),
                    style = JellyCastText.Footnote.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun contextTitle(uiState: ChatUiState): String {
    if (!uiState.avoidsSpoilers) return stringResource(R.string.settings_chat_spoilers_allowed)
    val subject = uiState.subject
    return if (subject is ChatSubject.EpisodeSubject) {
        stringResource(R.string.settings_chat_no_spoilers_after, episodeCode(subject.episode))
    } else {
        stringResource(R.string.settings_chat_no_spoilers)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionChips(subject: ChatSubject?, enabled: Boolean, onSend: (String) -> Unit) {
    val whoIsWho = stringResource(R.string.settings_chat_who_is_who)
    val real = stringResource(R.string.settings_chat_real_events)
    val suggestions =
        if (subject is ChatSubject.EpisodeSubject) {
            listOf(stringResource(R.string.settings_chat_previous_episode), whoIsWho, real)
        } else {
            listOf(whoIsWho, real, stringResource(R.string.settings_chat_know_before))
        }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        suggestions.forEach { suggestion ->
            SuggestionChip(
                onClick = { onSend(suggestion) },
                label = { Text(suggestion, style = JellyCastText.Subheadline) },
                enabled = enabled && subject != null,
                shape = CircleShape,
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = JellyCastColors.Surface,
                    labelColor = JellyCastColors.Text,
                ),
                border = null,
            )
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    when (message.role) {
        ChatRole.User -> Box(Modifier.fillMaxWidth().padding(start = 40.dp), contentAlignment = Alignment.CenterEnd) {
            SelectionContainer {
                Text(
                    message.text,
                    style = JellyCastText.Subheadline,
                    color = JellyCastColors.Text,
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(JellyCastColors.Rail)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }

        ChatRole.Assistant -> Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SelectionContainer { MarkdownText(message.text) }
            if (message.searchCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painterResource(R.drawable.ic_search),
                        null,
                        Modifier.size(12.dp),
                        tint = JellyCastColors.TextMuted,
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.settings_chat_searched,
                            message.searchCount,
                            message.searchCount,
                        ),
                        style = JellyCastText.Caption,
                        color = JellyCastColors.TextMuted,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            if (message.sources.isNotEmpty()) ChatSources(message.sources)
        }
    }
}

/** While the answer is on its way: each search and site, then the answer as it is written. */
@Composable
private fun ChatWorking(steps: List<ChatStep>, draft: String) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { index, step ->
            WorkingRow(
                text = step.text(),
                icon = step.icon(),
                isCurrent = index == steps.lastIndex && draft.isEmpty(),
            )
        }
        if (draft.isEmpty()) {
            if (steps.isEmpty()) {
                WorkingRow(stringResource(R.string.settings_chat_thinking), R.drawable.ic_settings_sparkle, isCurrent = true)
            }
        } else {
            MarkdownText(draft)
        }
    }
}

@Composable
private fun WorkingRow(text: String, icon: Int, isCurrent: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
            if (isCurrent) {
                CircularProgressIndicator(
                    Modifier.size(12.dp),
                    color = JellyCastColors.TextMuted,
                    strokeWidth = 1.5.dp,
                )
            } else {
                Icon(painterResource(icon), null, Modifier.size(13.dp), tint = JellyCastColors.TextMuted)
            }
        }
        Text(
            text,
            style = JellyCastText.Footnote,
            color = JellyCastColors.TextMuted,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatStep.text(): String = when (this) {
    is ChatStep.Search -> stringResource(R.string.settings_chat_searching, query)

    is ChatStep.Read -> {
        val shown = sites.take(SITES_SHOWN).joinToString(", ")
        val more = sites.size - minOf(sites.size, SITES_SHOWN)
        val list = if (more > 0) stringResource(R.string.settings_chat_sites_more, shown, more) else shown
        pluralStringResource(R.plurals.settings_chat_results, count, count, list)
    }

    is ChatStep.Open -> stringResource(R.string.settings_chat_reading, site)

    ChatStep.Filter -> stringResource(R.string.settings_chat_filtering)
}

private fun ChatStep.icon(): Int = when (this) {
    is ChatStep.Search -> R.drawable.ic_search
    is ChatStep.Read -> R.drawable.ic_settings_globe
    is ChatStep.Open -> R.drawable.ic_settings_description
    ChatStep.Filter -> R.drawable.ic_settings_filter
}

private const val SITES_SHOWN = 3

/** The web pages an answer is based on, as small links. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatSources(sources: List<ChatSource>) {
    val uriHandler = LocalUriHandler.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        sources.forEach { source ->
            AssistChip(
                onClick = { uriHandler.openUri(source.url) },
                label = {
                    Text(
                        ChatSource.siteOf(source.url) ?: source.title,
                        style = JellyCastText.Caption,
                        maxLines = 1,
                    )
                },
                leadingIcon = {
                    Icon(painterResource(R.drawable.ic_settings_globe), null, Modifier.size(12.dp))
                },
                shape = CircleShape,
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = JellyCastColors.Surface,
                    labelColor = JellyCastColors.TextMuted,
                    leadingIconContentColor = JellyCastColors.TextMuted,
                ),
                border = null,
                modifier = Modifier.semantics { contentDescription = source.title },
            )
        }
    }
}

/** The question field under the conversation. */
@Composable
private fun ChatInputBar(
    title: String,
    isSending: Boolean,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by rememberSaveable { mutableStateOf("") }
    val canSend = !isSending && text.isNotBlank()
    val send = {
        if (canSend) {
            onSend(text.trim())
            text = ""
        }
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(JellyCastColors.Surface)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            textStyle = JellyCastText.Subheadline.copy(color = JellyCastColors.Text),
            cursorBrush = SolidColor(JellyCastColors.Accent),
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { send() }),
            modifier = Modifier.weight(1f),
            decorationBox = { field ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            stringResource(R.string.settings_chat_ask, title),
                            style = JellyCastText.Subheadline,
                            color = JellyCastColors.TextMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    field()
                }
            },
        )
        val sendLabel = stringResource(R.string.settings_chat_send)
        Box(
            Modifier
                .size(34.dp)
                .alpha(if (canSend) 1f else DISABLED_ALPHA)
                .clip(CircleShape)
                .background(JellyCastColors.Accent)
                .pressable(onClick = send, enabled = canSend)
                .semantics { contentDescription = sendLabel },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(R.drawable.ic_settings_arrow_upward),
                null,
                Modifier.size(18.dp),
                tint = JellyCastColors.OnAccent,
            )
        }
    }
}

private const val DISABLED_ALPHA = 0.4f

private fun ChatSubject.title(): String = when (this) {
    is ChatSubject.MovieSubject -> movie.title
    is ChatSubject.EpisodeSubject -> episode.seriesTitle
}

@Composable
internal fun ChatError.message(): String = when (this) {
    ChatError.InvalidKey -> stringResource(R.string.settings_chat_error_invalid_key)
    ChatError.Refused -> stringResource(R.string.settings_chat_error_refused)
    ChatError.RateLimited -> stringResource(R.string.settings_chat_error_rate_limited)
    is ChatError.Unavailable -> stringResource(R.string.settings_chat_error_unavailable, status)
    ChatError.EmptyReply -> stringResource(R.string.settings_chat_error_empty)
}

@Preview
@Composable
private fun ChatContentPreview() {
    JellyCastTheme {
        ChatContent(
            uiState =
                ChatUiState(
                    conversation =
                        ChatConversation(
                            messages =
                                listOf(
                                    ChatMessage(ChatRole.User, "Who is who?"),
                                    ChatMessage(
                                        ChatRole.Assistant,
                                        "Mostly **Walter** and *Jesse*.\n\n- Walter White\n- Jesse Pinkman",
                                        searchCount = 1,
                                        sources = listOf(ChatSource("IMDb", "https://www.imdb.com/title/tt0903747/")),
                                    ),
                                ),
                            isSending = true,
                            steps = listOf(ChatStep.Search("breaking bad cast")),
                        ),
                ),
            onSend = {},
            onRetry = {},
            onNewConversation = {},
        )
    }
}
