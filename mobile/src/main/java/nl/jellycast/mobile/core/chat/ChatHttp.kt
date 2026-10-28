package nl.jellycast.mobile.core.chat

import java.io.IOException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import nl.jellycast.core.common.AppDispatchers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A request to an AI service: the body is JSON, the headers carry the key. */
data class ChatRequest(
    val url: String,
    val headers: Map<String, String>,
    val body: JsonElement? = null,
) {
    // The key is in the headers: keep it out of logs and crash reports.
    override fun toString(): String = "ChatRequest($url)"
}

interface ChatHttp {
    /** Sends the request and decodes the body, mapping the service's errors. */
    suspend fun load(request: ChatRequest): JsonElement

    /** Sends the request and hands over each server-sent event's data in order. */
    suspend fun stream(request: ChatRequest, onEvent: suspend (JsonElement) -> Unit)
}

class OkHttpChatHttp(private val client: OkHttpClient, private val dispatchers: AppDispatchers) :
    ChatHttp {
    override suspend fun load(request: ChatRequest): JsonElement = execute(request) { source ->
        parseJson(source.readUtf8()) ?: throw ChatException(ChatError.EmptyReply)
    }

    override suspend fun stream(request: ChatRequest, onEvent: suspend (JsonElement) -> Unit) {
        execute(request) { source ->
            while (true) {
                currentCoroutineContext().ensureActive()
                val line = source.readUtf8Line() ?: break
                sseData(line)?.let { onEvent(it) }
            }
        }
    }

    private suspend fun <T> execute(
        request: ChatRequest,
        read: suspend (okio.BufferedSource) -> T,
    ): T = withContext(dispatchers.io) {
        val call = client.newCall(request.toOkHttp())
        val cancel = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                checkStatus(response.code)
                read(requireNotNull(response.body) { "No body" }.source())
            }
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            throw ChatException(ChatError.Unavailable(0))
        } finally {
            cancel.dispose()
        }
    }
}

private val JsonMediaType = "application/json".toMediaType()

private fun ChatRequest.toOkHttp(): Request {
    val builder = Request.Builder().url(url)
    headers.forEach { (name, value) -> builder.header(name, value) }
    body?.let { builder.post(it.toString().toRequestBody(JsonMediaType)) }
    return builder.build()
}

internal fun checkStatus(status: Int) {
    when (status) {
        in HTTP_OK_RANGE -> return
        HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> throw ChatException(ChatError.InvalidKey)
        HTTP_TOO_MANY_REQUESTS -> throw ChatException(ChatError.RateLimited)
        else -> throw ChatException(ChatError.Unavailable(status))
    }
}

private val HTTP_OK_RANGE = 200..299
private const val HTTP_UNAUTHORIZED = 401
private const val HTTP_FORBIDDEN = 403
private const val HTTP_TOO_MANY_REQUESTS = 429

private const val SSE_DATA = "data:"
private const val SSE_DONE = "[DONE]"

/** The JSON of one server-sent `data:` line; `null` for other lines and the end marker. */
internal fun sseData(line: String): JsonElement? {
    if (!line.startsWith(SSE_DATA)) return null
    val payload = line.removePrefix(SSE_DATA).trim()
    if (payload == SSE_DONE) return null
    return parseJson(payload)
}

internal fun parseJson(text: String): JsonElement? = try {
    Json.parseToJsonElement(text)
} catch (_: SerializationException) {
    null
}

internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)

internal val JsonElement?.string: String?
    get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

internal val JsonElement?.number: Double?
    get() = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

internal val JsonElement?.array: List<JsonElement>?
    get() = this as? JsonArray

internal val JsonElement?.obj: Map<String, JsonElement>?
    get() = this as? JsonObject

internal fun json(vararg pairs: Pair<String, Any?>): JsonObject = JsonObject(
    pairs.filter { it.second != null }.associate { (key, value) -> key to value.toJson() },
)

internal fun Any?.toJson(): JsonElement = when (this) {
    null -> JsonNull
    is JsonElement -> this
    is String -> JsonPrimitive(this)
    is Number -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    is List<*> -> JsonArray(map { it.toJson() })
    else -> error("Not a JSON value: ${this::class.simpleName}")
}
