package nl.jellycast.mobile.core.chat

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.jellycast.mobile.testing.FakeChatHttp
import nl.jellycast.mobile.testing.ReversingCipher
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatSettingsTest {
    private val directory: File = createTempDirectory("chat-settings").toFile()

    @After
    fun cleanUp() {
        directory.deleteRecursively()
    }

    private fun TestScope.settings(): DataStoreChatSettings {
        val file = File(directory, "chat.preferences_pb")
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope) { file }
        return DataStoreChatSettings(store, ReversingCipher(), backgroundScope)
    }

    @Test
    fun `a key belongs to its provider and is stored encrypted`() = runTest {
        val settings = settings()

        settings.setKey("  sk-ant-1234567890abcd ")
        val claude = settings.state.first { it.hasKey }

        assertEquals("sk-ant-•••• abcd", claude.maskedKey)
        assertEquals("sk-ant-1234567890abcd", settings.credentials()?.apiKey)
        val stored = File(directory, "chat.preferences_pb").readText(Charsets.ISO_8859_1)
        assertFalse("sk-ant-1234567890abcd" in stored)

        settings.setProvider(ChatProvider.OpenAi)
        val openAi = settings.state.first { it.provider == ChatProvider.OpenAi }
        assertFalse(openAi.hasKey)
        assertNull(settings.credentials())
    }

    @Test
    fun `a blank key removes it`() = runTest {
        val settings = settings()
        settings.setKey("sk-1")

        settings.setKey(" ")

        assertNull(settings.credentials())
    }

    @Test
    fun `spoilers and web search default to on`() = runTest {
        val settings = settings()

        val state = settings.state.first()

        assertTrue(state.avoidsSpoilers)
        assertTrue(state.searchesWeb)
    }

    @Test
    fun `the client follows the chosen provider`() = runTest {
        val settings = settings()
        val factory = ChatClientFactory(settings, FakeChatHttp(), settings)
        assertNull(factory.client())

        settings.setKey("sk-ant-1")
        assertTrue(factory.client() is ClaudeChatClient)

        settings.setProvider(ChatProvider.OpenAi)
        settings.setKey("sk-2")
        assertTrue(factory.client() is OpenAiChatClient)
    }

    @Test
    fun `server-sent data lines are parsed and the rest is skipped`() {
        assertEquals("a", sseData("data: {\"type\":\"a\"}")["type"].string)
        assertNull(sseData("event: ping"))
        assertNull(sseData("data: [DONE]"))
    }

    @Test
    fun `statuses map to chat errors`() = runTest {
        assertError(ChatError.InvalidKey) { checkStatus(401) }
        assertError(ChatError.InvalidKey) { checkStatus(403) }
        assertError(ChatError.RateLimited) { checkStatus(429) }
        assertError(ChatError.Unavailable(500)) { checkStatus(500) }
        checkStatus(200)
    }
}
