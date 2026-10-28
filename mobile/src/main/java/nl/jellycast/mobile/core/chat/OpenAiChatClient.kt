package nl.jellycast.mobile.core.chat

import kotlinx.serialization.json.JsonElement

/** Remembers which OpenAI model worked, since the model names change often. */
interface ModelMemory {
    suspend fun storedModel(): String?

    suspend fun storeModel(model: String)
}

/**
 * ChatGPT through the Responses API with the user's own key, streamed so the searches show while
 * they run. Chat Completions only searches the web with separate search models.
 */
class OpenAiChatClient(
    private val apiKey: String,
    private val http: ChatHttp,
    private val memory: ModelMemory,
) : ChatClient {
    override suspend fun reply(
        system: String,
        turns: List<ChatTurn>,
        searchesWeb: Boolean,
        progress: suspend (ChatProgress) -> Unit,
    ): ChatReply {
        val model = memory.storedModel() ?: PREFERRED_MODEL
        return try {
            stream(responsesRequest(system, turns, searchesWeb, model), progress)
        } catch (e: ChatException) {
            if (e.error != ChatError.Unavailable(HTTP_NOT_FOUND)) throw e
            stream(responsesRequest(system, turns, searchesWeb, resolveModel()), progress)
        }
    }

    override suspend fun check() {
        resolveModel()
    }

    internal fun responsesRequest(
        system: String,
        turns: List<ChatTurn>,
        searchesWeb: Boolean,
        model: String,
    ): ChatRequest {
        val input = turns.map { turn ->
            json(
                "role" to if (turn.role == ChatRole.User) "user" else "assistant",
                "content" to turn.text,
            )
        }
        val body =
            json(
                "model" to model,
                "instructions" to system,
                "input" to input,
                "store" to false,
                "stream" to true,
                "tools" to if (searchesWeb) listOf(json("type" to "web_search")) else null,
            )
        return ChatRequest(RESPONSES_ENDPOINT, headers(), body)
    }

    private suspend fun stream(
        request: ChatRequest,
        progress: suspend (ChatProgress) -> Unit,
    ): ChatReply {
        val text = StringBuilder()
        var completed: JsonElement? = null
        http.stream(request) { event ->
            when (event["type"].string) {
                "response.output_text.delta" -> {
                    text.append(event["delta"].string.orEmpty())
                    progress(ChatProgress.Answer(text.toString()))
                }

                "response.output_item.done" -> event["item"]?.let(::progressOf)?.let { progress(it) }

                "response.completed", "response.incomplete" -> completed = event["response"]

                "response.failed", "error" -> throw ChatException(ChatError.Unavailable(0))
            }
        }
        return replyFrom(completed ?: throw ChatException(ChatError.EmptyReply))
    }

    /** The newest general chat model the key can use, preferring [PREFERRED_MODEL]. */
    private suspend fun resolveModel(): String {
        val response = http.load(ChatRequest(MODELS_ENDPOINT, headers()))
        val candidates =
            response["data"].array.orEmpty().mapNotNull { model ->
                val id = model["id"].string ?: return@mapNotNull null
                if (!id.startsWith("gpt-") || ExcludedModelWords.any { it in id }) {
                    return@mapNotNull null
                }
                id to (model["created"].number ?: 0.0)
            }
        val chosen =
            candidates.firstOrNull { it.first == PREFERRED_MODEL }?.first
                ?: candidates.maxByOrNull { it.second }?.first
                ?: throw ChatException(ChatError.Unavailable(HTTP_NOT_FOUND))
        memory.storeModel(chosen)
        return chosen
    }

    private fun headers() = mapOf(
        "Authorization" to "Bearer $apiKey",
        "content-type" to "application/json",
    )

    companion object {
        private const val RESPONSES_ENDPOINT = "https://api.openai.com/v1/responses"
        private const val MODELS_ENDPOINT = "https://api.openai.com/v1/models"
        const val PREFERRED_MODEL = "gpt-5.5"
        private const val HTTP_NOT_FOUND = 404

        /** Variants that aren't the general chat model. */
        private val ExcludedModelWords =
            listOf(
                "mini", "nano", "audio", "realtime", "image", "search", "transcribe", "tts",
                "codex", "instruct", "oss",
            )

        internal fun progressOf(item: JsonElement): ChatProgress? {
            if (item["type"].string != "web_search_call") return null
            val action = item["action"] ?: return null
            return when (action["type"].string) {
                "search" ->
                    (action["query"].string ?: action["queries"].array?.firstOrNull().string)
                        ?.let { ChatProgress.Searching(it) }

                "open_page", "find_in_page" ->
                    action["url"].string?.let(ChatSource::siteOf)?.let { ChatProgress.Opening(it) }

                else -> null
            }
        }

        internal fun replyFrom(response: JsonElement): ChatReply {
            val content =
                response["output"].array.orEmpty()
                    .filter { it["type"].string == "message" }
                    .flatMap { it["content"].array.orEmpty() }
            val parts = content.filter { it["type"].string == "output_text" }
            if (parts.isEmpty() && content.any { it["type"].string == "refusal" }) {
                throw ChatException(ChatError.Refused)
            }
            val text = parts.mapNotNull { it["text"].string }.joinToString("\n\n").trim()
            if (text.isEmpty()) throw ChatException(ChatError.EmptyReply)
            val cited =
                parts.flatMap { part ->
                    part["annotations"].array.orEmpty()
                        .filter { it["type"].string == "url_citation" }
                        .mapNotNull { ChatSource.of(it["title"].string, it["url"].string) }
                }
            return ChatReply(text = text, sources = ChatSource.unique(cited))
        }
    }
}
