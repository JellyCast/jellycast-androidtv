package nl.jellycast.mobile.core.chat

import kotlinx.coroutines.test.runTest
import nl.jellycast.mobile.testing.FakeChatHttp
import nl.jellycast.mobile.testing.FakeChatSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiChatClientTest {
    private val http = FakeChatHttp()
    private val memory = FakeChatSettings()
    private val client = OpenAiChatClient("sk-secret", http, memory)

    private val completed =
        """{"type":"response.completed","response":{"output":[{"type":"message","content":[""" +
            """{"type":"output_text","text":"Walter.","annotations":[""" +
            """{"type":"url_citation","title":"IMDb","url":"https://www.imdb.com/x"}]}]}]}}"""

    @Test
    fun `the request uses the Responses API with instructions, no storage and web search`() {
        val turns = listOf(ChatTurn(ChatRole.User, "Q"), ChatTurn(ChatRole.Assistant, "A"))

        val request = client.responsesRequest(
            "Be brief.",
            turns,
            searchesWeb = true,
            model = "gpt-5.5",
        )

        assertEquals("https://api.openai.com/v1/responses", request.url)
        assertEquals("Bearer sk-secret", request.headers["Authorization"])
        assertEquals("gpt-5.5", request.body["model"].string)
        assertEquals("Be brief.", request.body["instructions"].string)
        assertEquals(false.toString(), request.body["store"].toString())
        assertEquals(
            listOf("user", "assistant"),
            request.body["input"].array?.map {
                it["role"].string
            },
        )
        assertEquals("web_search", request.body["tools"].array?.single()["type"].string)
        assertNull(client.responsesRequest("", turns, false, "gpt-5.5").body["tools"])
    }

    @Test
    fun `a streamed answer reports the text so far, searches and pages, then the sources`() =
        runTest {
            http.enqueueEvents(
                """{"type":"response.output_item.done","item":{"type":"web_search_call","action":{"type":"search","query":"cast"}}}""",
                """{"type":"response.output_item.done","item":{"type":"web_search_call","action":{"type":"open_page","url":"https://www.imdb.com/x"}}}""",
                """{"type":"response.output_text.delta","delta":"Wal"}""",
                """{"type":"response.output_text.delta","delta":"ter."}""",
                completed,
            )
            val progress = mutableListOf<ChatProgress>()

            val reply = client.reply("", listOf(ChatTurn(ChatRole.User, "Q")), true) {
                progress +=
                    it
            }

            assertEquals("Walter.", reply.text)
            assertEquals(listOf(ChatSource("IMDb", "https://www.imdb.com/x")), reply.sources)
            assertEquals(
                listOf(
                    ChatProgress.Searching("cast"),
                    ChatProgress.Opening("imdb.com"),
                    ChatProgress.Answer("Wal"),
                    ChatProgress.Answer("Walter."),
                ),
                progress,
            )
        }

    @Test
    fun `an unknown model falls back to the newest general model of the key, and remembers it`() =
        runTest {
            http.enqueueFailure(ChatException(ChatError.Unavailable(404)))
            http.enqueueEvents(
                """{"data":[{"id":"gpt-6-mini","created":30},{"id":"gpt-6","created":20},""" +
                    """{"id":"gpt-5","created":10},{"id":"dall-e-3","created":40}]}""",
            )
            http.enqueueEvents(completed)

            client.reply("", listOf(ChatTurn(ChatRole.User, "Q")), false) {}

            assertEquals("gpt-5.5", http.requests[0].body["model"].string)
            assertEquals("gpt-6", http.requests[2].body["model"].string)
            assertEquals("gpt-6", memory.storedModel)
        }

    @Test
    fun `a refusal without text is an error`() {
        val response =
            parseJson(
                """{"output":[{"type":"message","content":[{"type":"refusal","refusal":"No"}]}]}""",
            )

        try {
            OpenAiChatClient.replyFrom(requireNotNull(response))
            throw AssertionError("Expected a refusal")
        } catch (e: ChatException) {
            assertEquals(ChatError.Refused, e.error)
        }
    }

    @Test
    fun `a stream without a completed response is an empty reply`() = runTest {
        http.enqueueEvents("""{"type":"response.output_text.delta","delta":"Wal"}""")

        assertError(ChatError.EmptyReply) { client.reply("", emptyList(), false) {} }
        assertTrue(http.requests.size == 1)
    }
}
