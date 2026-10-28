package nl.jellycast.mobile.core.chat

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Claude through the Messages API with the user's own key, streamed so the searches show while
 * they run.
 */
class ClaudeChatClient(private val apiKey: String, private val http: ChatHttp) : ChatClient {
    override suspend fun reply(
        system: String,
        turns: List<ChatTurn>,
        searchesWeb: Boolean,
        progress: suspend (ChatProgress) -> Unit,
    ): ChatReply {
        val messages = turns.map { it.toMessage(searchesWeb) }
        val message = ClaudeStreamedMessage()
        var continuations = 0
        while (true) {
            val paused =
                if (message.blocks.isEmpty()) {
                    emptyList()
                } else {
                    listOf(
                        json(
                            "role" to "assistant",
                            "content" to JsonArray(message.blocks.toList()),
                        ),
                    )
                }
            message.stopReason = null
            http.stream(messagesRequest(system, messages + paused, searchesWeb)) { event ->
                message.apply(event)?.let { progress(it) }
            }
            if (message.stopReason == "refusal") throw ChatException(ChatError.Refused)
            if (message.stopReason != "pause_turn" || continuations == MAX_CONTINUATIONS) break
            continuations++
        }
        val text = ClaudeStreamedMessage.answerText(message.blocks)
        if (text.isEmpty()) throw ChatException(ChatError.EmptyReply)
        return ChatReply(
            text = text,
            providerContent = JsonArray(message.blocks.toList()),
            sources = ClaudeStreamedMessage.sources(message.blocks),
        )
    }

    override suspend fun check() {
        http.load(ChatRequest(MODELS_ENDPOINT, headers()))
    }

    internal fun messagesRequest(
        system: String,
        messages: List<JsonElement>,
        searchesWeb: Boolean,
    ): ChatRequest {
        val systemBlock =
            json(
                "type" to "text",
                "text" to system,
                "cache_control" to json("type" to "ephemeral"),
            )
        val tools =
            if (searchesWeb) {
                listOf(
                    json(
                        "type" to "web_search_20260209",
                        "name" to "web_search",
                        "max_uses" to MAX_SEARCHES,
                    ),
                )
            } else {
                null
            }
        val body =
            json(
                "model" to MODEL,
                "max_tokens" to MAX_TOKENS,
                "fallbacks" to "default",
                "stream" to true,
                "system" to listOf(systemBlock),
                "messages" to messages,
                "tools" to tools,
            )
        return ChatRequest(ENDPOINT, headers() + ("anthropic-beta" to FALLBACK_BETA), body)
    }

    private fun headers() = mapOf(
        "x-api-key" to apiKey,
        "anthropic-version" to API_VERSION,
        "content-type" to "application/json",
    )

    companion object {
        private const val ENDPOINT = "https://api.anthropic.com/v1/messages"
        private const val MODELS_ENDPOINT = "https://api.anthropic.com/v1/models"
        const val MODEL = "claude-opus-5-5"
        private const val API_VERSION = "2026-10-21"

        /** A declined question is retried on the model Anthropic recommends for that category. */
        private const val FALLBACK_BETA = "server-side-fallback-2026-07-01"
        private const val MAX_TOKENS = 16_000
        private const val MAX_SEARCHES = 5

        /** A long search turn can pause; it is resumed by sending it back, a few times at most. */
        private const val MAX_CONTINUATIONS = 3
    }
}

private fun ChatTurn.toMessage(searchesWeb: Boolean): JsonElement = when (role) {
    ChatRole.User -> json("role" to "user", "content" to text)

    // Search blocks can't be sent back once the search tool is no longer offered.
    ChatRole.Assistant ->
        json(
            "role" to "assistant",
            "content" to if (searchesWeb) providerContent ?: JsonPrimitive(text) else text,
        )
}

/** Rebuilds Claude's content blocks from the stream, so the turn can be sent back unchanged. */
class ClaudeStreamedMessage {
    private val _blocks = mutableListOf<JsonObject>()
    val blocks: List<JsonObject> get() = _blocks
    var stopReason: String? = null
    private val partialInput = mutableMapOf<Int, StringBuilder>()

    /** Blocks of earlier, paused requests come first; the stream's indexes count from its own start. */
    private var offset = 0

    /** Applies one event and returns what changed for the user, if anything. */
    fun apply(event: JsonElement): ChatProgress? {
        when (event["type"].string) {
            "message_start" -> {
                offset = _blocks.size
                partialInput.clear()
            }

            "content_block_start" -> {
                val block = event["content_block"] as? JsonObject ?: return null
                _blocks += block
                return progressOnStart(block)
            }

            "content_block_delta" -> {
                val index = index(event) ?: return null
                val delta = event["delta"] ?: return null
                return applyDelta(delta, index)
            }

            "content_block_stop" -> return finishInput(index(event) ?: return null)

            "message_delta" -> stopReason = event["delta"]["stop_reason"].string ?: stopReason

            "error" -> throw ChatException(
                if (event["error"]["type"].string == "rate_limit_error") {
                    ChatError.RateLimited
                } else {
                    ChatError.Unavailable(0)
                },
            )
        }
        return null
    }

    private fun index(event: JsonElement): Int? {
        val value = event["index"].number ?: return null
        return (offset + value.toInt()).takeIf { it in _blocks.indices }
    }

    private fun applyDelta(delta: JsonElement, index: Int): ChatProgress? {
        val block = _blocks[index].toMutableMap()
        when (delta["type"].string) {
            "text_delta" -> block.append("text", delta["text"].string)

            "input_json_delta" -> {
                partialInput.getOrPut(index, ::StringBuilder).append(
                    delta["partial_json"].string.orEmpty(),
                )
                return null
            }

            "citations_delta" -> {
                val citation = delta["citation"] ?: return null
                block["citations"] = JsonArray(block["citations"].array.orEmpty() + citation)
            }

            "thinking_delta" -> block.append("thinking", delta["thinking"].string)

            "signature_delta" -> block["signature"] = delta["signature"] ?: return null

            else -> return null
        }
        _blocks[index] = JsonObject(block)
        if (block["type"].string != "text") return null
        return ChatProgress.Answer(answerText(_blocks))
    }

    private fun finishInput(index: Int): ChatProgress? {
        val partial = partialInput.remove(index)?.toString() ?: return null
        val block = _blocks[index].toMutableMap()
        block["input"] = (if (partial.isEmpty()) null else parseJson(partial)) ?: JsonObject(
            emptyMap(),
        )
        _blocks[index] = JsonObject(block)
        if (block["type"].string != "server_tool_use") return null
        val name = block["name"].string ?: return null
        return when {
            name == "web_search" ->
                block["input"]["query"].string?.let { ChatProgress.Searching(it) }

            "code_execution" in name -> ChatProgress.Filtering

            else -> null
        }
    }

    companion object {
        private fun MutableMap<String, JsonElement>.append(key: String, text: String?) {
            this[key] = JsonPrimitive(this[key].string.orEmpty() + text.orEmpty())
        }

        private fun progressOnStart(block: JsonObject): ChatProgress? {
            if (block["type"].string != "web_search_tool_result") return null
            val results = block["content"].array ?: return null
            val sites = results.mapNotNull { result ->
                result["url"].string?.let(ChatSource::siteOf)
            }
            return if (sites.isEmpty()) null else ChatProgress.Found(sites)
        }

        /**
         * The text after the last search: what comes before it is Claude saying it will look
         * something up. Cited text arrives split over several blocks, so they are joined as is.
         */
        fun answerText(blocks: List<JsonElement>): String {
            val lastTool = blocks.indexOfLast { it["type"].string != "text" }
            return blocks
                .drop(lastTool + 1)
                .mapNotNull { it["text"].string }
                .joinToString("")
                .trim()
        }

        fun sources(blocks: List<JsonElement>): List<ChatSource> = ChatSource.unique(
            blocks.flatMap { block ->
                block["citations"].array.orEmpty().mapNotNull {
                    ChatSource.of(it["title"].string, it["url"].string)
                }
            },
        )
    }
}
