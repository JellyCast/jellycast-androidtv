package nl.jellycast.mobile.ui.chat

import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.ChatCredit
import nl.jellycast.core.model.ChatCredits
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.testEpisode
import nl.jellycast.core.testing.testMovie
import nl.jellycast.core.testing.testSeries
import nl.jellycast.mobile.core.chat.ChatClientFactory
import nl.jellycast.mobile.core.chat.ChatError
import nl.jellycast.mobile.core.chat.ChatException
import nl.jellycast.mobile.core.chat.ChatRole
import nl.jellycast.mobile.core.chat.ChatSubject
import nl.jellycast.mobile.core.chat.array
import nl.jellycast.mobile.core.chat.get
import nl.jellycast.mobile.core.chat.string
import nl.jellycast.mobile.testing.FakeChatCreditsRepository
import nl.jellycast.mobile.testing.FakeChatHttp
import nl.jellycast.mobile.testing.FakeChatSettings
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.ui.info.InfoSubject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val series = testSeries("s")
    private val episodes =
        listOf(
            testEpisode("e1", "s", season = 1, number = 1, isPlayed = true, lastPlayedAt = 5),
            testEpisode("e2", "s", season = 1, number = 2),
        )
    private val store =
        FakeLibraryStore(
            movies = listOf(testMovie("m")),
            series = listOf(series),
            episodes = episodes,
        )
    private val http = FakeChatHttp()
    private val settings = FakeChatSettings(key = "sk-ant-1")
    private val credits =
        FakeChatCreditsRepository(ChatCredits(actors = listOf(ChatCredit("Bryan"))))
    private val viewModel by lazy {
        ChatViewModel(store, settings, ChatClientFactory(settings, http, settings), credits)
    }

    private fun answer(text: String) {
        http.enqueueEvents(
            """{"type":"content_block_start","index":0,"content_block":{"type":"text","text":"$text"}}""",
        )
    }

    @Test
    fun `the series page talks about the episode to continue with`() {
        viewModel.onSubject(InfoSubject.SeriesInfo("s"))

        val subject = viewModel.uiState.value.subject as ChatSubject.EpisodeSubject
        assertEquals("e2", subject.episode.id)
        assertEquals(series, subject.series)
    }

    @Test
    fun `a question gets an answer with the cast in the prompt`() = runTest {
        viewModel.onSubject(InfoSubject.EpisodeInfo("e2", "s"))
        answer("Bryan plays Walter.")

        viewModel.onSend("  Who is who?  ")

        val chat = viewModel.uiState.value.conversation
        assertEquals(listOf(ChatRole.User, ChatRole.Assistant), chat.messages.map { it.role })
        assertEquals("Who is who?", chat.messages.first().text)
        assertEquals("Bryan plays Walter.", chat.messages.last().text)
        assertFalse(chat.isSending)
        assertEquals(listOf("e2" to "s"), credits.requested)
        val system = http.requests.single().body["system"].array?.single()["text"].string.orEmpty()
        assertTrue("Cast: Bryan" in system)
        assertTrue("up to and including S1E2" in system)
    }

    @Test
    fun `an error keeps the question so it can be asked again`() = runTest {
        viewModel.onSubject(InfoSubject.MovieInfo("m"))
        http.enqueueFailure(ChatException(ChatError.RateLimited))

        viewModel.onSend("Q")

        assertEquals(ChatError.RateLimited, viewModel.uiState.value.conversation.error)
        answer("A")
        viewModel.onRetry()
        val chat = viewModel.uiState.value.conversation
        assertNull(chat.error)
        assertEquals(listOf("Q", "A"), chat.messages.map { it.text })
    }

    @Test
    fun `a new conversation starts empty`() = runTest {
        viewModel.onSubject(InfoSubject.MovieInfo("m"))
        answer("A")
        viewModel.onSend("Q")

        viewModel.onNewConversation()

        assertTrue(viewModel.uiState.value.conversation.messages.isEmpty())
    }

    @Test
    fun `without a key the question is answered with the key error`() = runTest {
        settings.setKey(null)
        viewModel.onSubject(InfoSubject.MovieInfo("m"))

        viewModel.onSend("Q")

        assertEquals(ChatError.InvalidKey, viewModel.uiState.value.conversation.error)
        assertTrue(http.requests.isEmpty())
    }

    @Test
    fun `blank questions are ignored`() {
        viewModel.onSubject(InfoSubject.MovieInfo("m"))

        viewModel.onSend("   ")

        assertTrue(viewModel.uiState.value.conversation.messages.isEmpty())
    }
}
