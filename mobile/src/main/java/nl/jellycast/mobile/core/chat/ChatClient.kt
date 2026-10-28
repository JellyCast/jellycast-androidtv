package nl.jellycast.mobile.core.chat

import java.net.URI
import java.net.URISyntaxException
import kotlinx.serialization.json.JsonElement

enum class ChatRole { User, Assistant }

data class ChatTurn(
    val role: ChatRole,
    val text: String,
    /**
     * The assistant's reply exactly as the service sent it, sent back unchanged on the next
     * question (Claude's thinking blocks have to come back as they were).
     */
    val providerContent: JsonElement? = null,
)

data class ChatReply(
    val text: String,
    val providerContent: JsonElement? = null,
    val sources: List<ChatSource> = emptyList(),
)

/** A web page the answer is based on. */
data class ChatSource(val title: String, val url: String) {
    companion object {
        private const val DEFAULT_LIMIT = 5

        fun of(title: String?, url: String?): ChatSource? {
            val address = url?.takeIf { siteOf(it) != null } ?: return null
            return ChatSource(title ?: siteOf(address) ?: address, address)
        }

        /** The first sources with a distinct address, in the order they were cited. */
        fun unique(sources: List<ChatSource>, limit: Int = DEFAULT_LIMIT): List<ChatSource> =
            sources.distinctBy { it.url }.take(limit)

        /** "imdb.com": shorter and more recognisable than a page title. */
        fun siteOf(url: String): String? {
            val host =
                try {
                    URI(url).host
                } catch (_: URISyntaxException) {
                    null
                } ?: return null
            return host.removePrefix("www.")
        }
    }
}

sealed interface ChatError {
    data object InvalidKey : ChatError

    data object Refused : ChatError

    data object RateLimited : ChatError

    data class Unavailable(val status: Int) : ChatError

    data object EmptyReply : ChatError
}

class ChatException(val error: ChatError) : Exception(error.toString())

/** What the model is doing while the answer is on its way. */
sealed interface ChatProgress {
    data class Searching(val query: String) : ChatProgress

    data class Found(val sites: List<String>) : ChatProgress

    data class Opening(val site: String) : ChatProgress

    data object Filtering : ChatProgress

    /** The answer so far, replacing what came before. */
    data class Answer(val text: String) : ChatProgress
}

interface ChatClient {
    suspend fun reply(
        system: String,
        turns: List<ChatTurn>,
        searchesWeb: Boolean,
        progress: suspend (ChatProgress) -> Unit,
    ): ChatReply

    /** Checks that the key works, without a paid request where the service allows. */
    suspend fun check()
}
