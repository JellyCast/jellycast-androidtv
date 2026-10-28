package nl.jellycast.mobile.core.chat

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import nl.jellycast.mobile.testing.FakeChatHttp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ClaudeChatClientTest {
    private val http = FakeChatHttp()
    private val client = ClaudeChatClient("sk-ant-secret", http)

    @Test
    fun `the request carries the model, the cached system prompt and the search tool`() {
        val request =
            client.messagesRequest(
                "Be brief.",
                listOf(json("role" to "user", "content" to "Hi")),
                true,
            )

        assertEquals("https://api.anthropic.com/v1/messages", request.url)
        assertEquals("sk-ant-secret", request.headers["x-api-key"])
        assertEquals("2026-10-21", request.headers["anthropic-version"])
        assertEquals("server-side-fallback-2026-07-01", request.headers["anthropic-beta"])
        val body = request.body
        assertEquals("claude-opus-5-5", body["model"].string)
        assertEquals(16_000.0, body["max_tokens"].number)
        assertEquals("default", body["fallbacks"].string)
        val system = body["system"].array.orEmpty().single()
        assertEquals("Be brief.", system["text"].string)
        assertEquals("ephemeral", system["cache_control"]["type"].string)
        val tool = body["tools"].array.orEmpty().single()
        assertEquals("web_search_20260209", tool["type"].string)
        assertEquals(5.0, tool["max_uses"].number)
    }

    @Test
    fun `without web search there is no tool`() {
        val request = client.messagesRequest("", emptyList(), searchesWeb = false)

        assertNull(request.body["tools"])
    }

    @Test
    fun `the key is not in the request's text`() {
        val request = client.messagesRequest("", emptyList(), searchesWeb = false)

        assertTrue("secret" !in request.toString())
    }

    @Test
    fun `a streamed answer reports searches and keeps the text after the last search`() = runTest {
        http.enqueueEvents(
            """{"type":"message_start"}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":0,"delta":{"type":"text_delta","text":"Let me look."}}""",
            """{"type":"content_block_stop","index":0}""",
            """{"type":"content_block_start","index":1,"content_block":{"type":"server_tool_use","name":"web_search"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"{\"query\":"}}""",
            """{"type":"content_block_delta","index":1,"delta":{"type":"input_json_delta","partial_json":"\"cast\"}"}}""",
            """{"type":"content_block_stop","index":1}""",
            """{"type":"content_block_start","index":2,"content_block":{"type":"web_search_tool_result","content":[{"url":"https://www.imdb.com/x"},{"url":"https://en.wikipedia.org/y"}]}}""",
            """{"type":"content_block_start","index":3,"content_block":{"type":"text","text":""}}""",
            """{"type":"content_block_delta","index":3,"delta":{"type":"text_delta","text":"Bryan Cranston"}}""",
            """{"type":"content_block_delta","index":3,"delta":{"type":"citations_delta","citation":{"title":"IMDb","url":"https://www.imdb.com/x"}}}""",
            """{"type":"content_block_start","index":4,"content_block":{"type":"text","text":" plays Walter."}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
        )
        val progress = mutableListOf<ChatProgress>()

        val reply = client.reply("", listOf(ChatTurn(ChatRole.User, "Who?")), true) {
            progress +=
                it
        }

        assertEquals("Bryan Cranston plays Walter.", reply.text)
        assertEquals(listOf(ChatSource("IMDb", "https://www.imdb.com/x")), reply.sources)
        assertEquals(5, (reply.providerContent as JsonArray).size)
        assertTrue(ChatProgress.Searching("cast") in progress)
        assertTrue(ChatProgress.Found(listOf("imdb.com", "en.wikipedia.org")) in progress)
        assertEquals(
            ChatProgress.Answer("Bryan Cranston"),
            progress.last {
                it is ChatProgress.Answer
            },
        )
    }

    @Test
    fun `a paused turn is sent back and continued`() = runTest {
        http.enqueueEvents(
            """{"type":"message_start"}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":"Half"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"pause_turn"}}""",
        )
        http.enqueueEvents(
            """{"type":"message_start"}""",
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":" done"}}""",
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"}}""",
        )

        val reply = client.reply("", listOf(ChatTurn(ChatRole.User, "Q")), false) {}

        assertEquals("Half done", reply.text)
        val resent = http.requests[1].body["messages"].array.orEmpty()
        assertEquals("assistant", resent.last()["role"].string)
        assertEquals("Half", resent.last()["content"].array?.single()["text"].string)
    }

    @Test
    fun `a refusal is an error`() = runTest {
        http.enqueueEvents("""{"type":"message_delta","delta":{"stop_reason":"refusal"}}""")

        assertError(ChatError.Refused) { client.reply("", emptyList(), false) {} }
    }

    @Test
    fun `a rate limit error event is reported as such`() = runTest {
        http.enqueueEvents("""{"type":"error","error":{"type":"rate_limit_error"}}""")

        assertError(ChatError.RateLimited) { client.reply("", emptyList(), false) {} }
    }

    @Test
    fun `an earlier answer goes back as the provider's blocks only while searching`() = runTest {
        val blocks = JsonArray(listOf(json("type" to "text", "text" to "A")))
        val turns =
            listOf(
                ChatTurn(ChatRole.User, "Q"),
                ChatTurn(ChatRole.Assistant, "A", blocks),
                ChatTurn(ChatRole.User, "Q2"),
            )
        repeat(2) {
            http.enqueueEvents(
                """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":"B"}}""",
            )
        }

        client.reply("", turns, searchesWeb = true) {}
        client.reply("", turns, searchesWeb = false) {}

        assertEquals(blocks, http.requests[0].body["messages"].array?.get(1)["content"])
        assertEquals("A", http.requests[1].body["messages"].array?.get(1)["content"].string)
    }

    @Test
    fun `checking the key lists the models`() = runTest {
        http.enqueueEvents("""{"data":[]}""")

        client.check()

        assertEquals("https://api.anthropic.com/v1/models", http.requests.single().url)
    }
}

internal suspend fun assertError(expected: ChatError, block: suspend () -> Unit) {
    try {
        block()
        fail("Expected $expected")
    } catch (e: ChatException) {
        assertEquals(expected, e.error)
    }
}
