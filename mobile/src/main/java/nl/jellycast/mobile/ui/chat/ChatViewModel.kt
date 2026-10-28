package nl.jellycast.mobile.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import nl.jellycast.core.data.repository.ChatCreditsRepository
import nl.jellycast.core.model.ChatCredits
import nl.jellycast.mobile.core.chat.ChatClientFactory
import nl.jellycast.mobile.core.chat.ChatContext
import nl.jellycast.mobile.core.chat.ChatError
import nl.jellycast.mobile.core.chat.ChatException
import nl.jellycast.mobile.core.chat.ChatProgress
import nl.jellycast.mobile.core.chat.ChatProvider
import nl.jellycast.mobile.core.chat.ChatRole
import nl.jellycast.mobile.core.chat.ChatSettings
import nl.jellycast.mobile.core.chat.ChatSource
import nl.jellycast.mobile.core.chat.ChatSubject
import nl.jellycast.mobile.core.chat.ChatTurn
import nl.jellycast.core.model.Episode
import nl.jellycast.mobile.core.library.progressOf
import nl.jellycast.mobile.core.library.LibrarySnapshot
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.ui.info.InfoSubject

data class ChatMessage(
    val role: ChatRole,
    val text: String,
    val providerContent: JsonElement? = null,
    val sources: List<ChatSource> = emptyList(),
    val searchCount: Int = 0,
    val id: String = UUID.randomUUID().toString(),
)

/** One thing the model did on the way to an answer, shown while it works. */
sealed interface ChatStep {
    data class Search(val query: String) : ChatStep

    /** [count] results from [sites] (distinct, in order). */
    data class Read(val count: Int, val sites: List<String>) : ChatStep

    data class Open(val site: String) : ChatStep

    data object Filter : ChatStep
}

data class ChatConversation(
    val messages: List<ChatMessage> = emptyList(),
    val isSending: Boolean = false,
    val error: ChatError? = null,
    val steps: List<ChatStep> = emptyList(),
    /** The answer as far as it has arrived. */
    val draft: String = "",
)

data class ChatUiState(
    val subject: ChatSubject? = null,
    val conversation: ChatConversation = ChatConversation(),
    val avoidsSpoilers: Boolean = true,
    val provider: ChatProvider = ChatProvider.Claude,
)

/** One conversation about a series or movie: the screen keys it by [ChatSubject.contextKey]. */
class ChatViewModel(
    private val store: LibraryStore,
    private val settings: ChatSettings,
    private val clients: ChatClientFactory,
    private val credits: ChatCreditsRepository,
) : ViewModel() {
    private val subject = MutableStateFlow<InfoSubject?>(null)
    private val conversation = MutableStateFlow(ChatConversation())
    private val castByItem = mutableMapOf<String, ChatCredits>()
    private var sendJob: Job? = null

    private val chatSubject: StateFlow<ChatSubject?> =
        combine(subject, store.snapshot) { info, snapshot ->
            info?.let { snapshot?.chatSubject(it) }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<ChatUiState> =
        combine(chatSubject, conversation, settings.state) { chatSubject, chat, prefs ->
            ChatUiState(
                subject = chatSubject,
                conversation = chat,
                avoidsSpoilers = prefs.avoidsSpoilers,
                provider = prefs.provider,
            )
        }.stateIn(viewModelScope, SharingStarted.Eagerly, ChatUiState())

    /** The episode can change while the same series plays on; the conversation stays. */
    fun onSubject(info: InfoSubject) {
        subject.value = info
    }

    fun onNewConversation() {
        sendJob?.cancel()
        sendJob = null
        conversation.value = ChatConversation()
    }

    fun onSend(text: String) {
        val question = text.trim()
        if (question.isEmpty() || conversation.value.isSending) return
        conversation.update { it.copy(messages = it.messages + ChatMessage(ChatRole.User, question)) }
        ask()
    }

    /** Asks again after an error, with the same question. */
    fun onRetry() {
        val chat = conversation.value
        if (chat.isSending || chat.messages.lastOrNull()?.role != ChatRole.User) return
        ask()
    }

    private fun ask() {
        val chatSubject = chatSubject.value ?: return
        conversation.update {
            it.copy(isSending = true, error = null, steps = emptyList(), draft = "")
        }
        val turns = conversation.value.messages.map {
            ChatTurn(it.role, it.text, it.providerContent)
        }
        sendJob =
            viewModelScope.launch {
                val error =
                    try {
                        val client = clients.client() ?: throw ChatException(ChatError.InvalidKey)
                        val prefs = settings.state.value
                        val system =
                            ChatContext.system(
                                chatSubject,
                                cast(chatSubject),
                                prefs.avoidsSpoilers,
                                prefs.searchesWeb,
                            )
                        val reply = client.reply(system, turns, prefs.searchesWeb, ::show)
                        conversation.update { chat ->
                            val message =
                                ChatMessage(
                                    role = ChatRole.Assistant,
                                    text = reply.text,
                                    providerContent = reply.providerContent,
                                    sources = reply.sources,
                                    searchCount = chat.steps.count { it is ChatStep.Search },
                                )
                            chat.copy(messages = chat.messages + message)
                        }
                        null
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: ChatException) {
                        e.error
                    }
                conversation.update {
                    it.copy(isSending = false, error = error, steps = emptyList(), draft = "")
                }
            }
    }

    private fun show(progress: ChatProgress) {
        conversation.update { chat ->
            when (progress) {
                is ChatProgress.Searching ->
                    chat.copy(steps = chat.steps + ChatStep.Search(progress.query), draft = "")

                is ChatProgress.Found ->
                    chat.copy(
                        steps = chat.steps + ChatStep.Read(progress.sites.size, progress.sites.distinct()),
                    )

                is ChatProgress.Opening -> chat.copy(steps = chat.steps + ChatStep.Open(progress.site))

                ChatProgress.Filtering ->
                    if (chat.steps.lastOrNull() == ChatStep.Filter) {
                        chat
                    } else {
                        chat.copy(steps = chat.steps + ChatStep.Filter)
                    }

                is ChatProgress.Answer -> chat.copy(draft = progress.text)
            }
        }
    }

    private suspend fun cast(subject: ChatSubject): ChatCredits? {
        castByItem[subject.itemId]?.let { return it }
        val seriesId = (subject as? ChatSubject.EpisodeSubject)?.episode?.seriesId
        return credits.getCredits(subject.itemId, seriesId)?.also { castByItem[subject.itemId] = it }
    }
}

/** The movie, or the episode the series page or player shows, from the library snapshot. */
internal fun LibrarySnapshot.chatSubject(info: InfoSubject): ChatSubject? = when (info) {
    is InfoSubject.MovieInfo -> moviesById[info.movieId]?.let { ChatSubject.MovieSubject(it) }

    is InfoSubject.EpisodeInfo -> episodesById[info.episodeId]?.let(::episodeSubject)

    is InfoSubject.SeriesInfo -> {
        val series = seriesById[info.seriesId]
        val progress = series?.let { progressOf(it, episodesBySeries[it.id].orEmpty()) }
        val episode =
            progress?.next
                ?: progress?.lastTouched
                ?: progress?.episodes?.firstOrNull { (it.seasonNumber ?: 0) > 0 }
                ?: progress?.episodes?.firstOrNull()
        episode?.let(::episodeSubject)
    }
}

private fun LibrarySnapshot.episodeSubject(episode: Episode): ChatSubject.EpisodeSubject {
    val series = seriesById[episode.seriesId]
    val episodes = episodesBySeries[episode.seriesId].orEmpty()
    return ChatSubject.EpisodeSubject(
        episode = episode,
        series = series,
        episodes = series?.let { progressOf(it, episodes).episodes } ?: episodes,
    )
}

internal fun InfoSubject.chatKey(): String = when (this) {
    is InfoSubject.MovieInfo -> movieId
    is InfoSubject.EpisodeInfo -> seriesId
    is InfoSubject.SeriesInfo -> seriesId
}
