package nl.jellycast.mobile.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import nl.jellycast.core.testing.testMovie
import nl.jellycast.mobile.core.chat.ChatClientFactory
import nl.jellycast.mobile.core.chat.ChatError
import nl.jellycast.mobile.core.chat.ChatException
import nl.jellycast.mobile.core.chat.ChatProvider
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.core.settings.QualityLevel
import nl.jellycast.mobile.core.settings.QualityPreference
import nl.jellycast.mobile.core.settings.RatingSource
import nl.jellycast.mobile.testing.FakeChatHttp
import nl.jellycast.mobile.testing.FakeChatSettings
import nl.jellycast.mobile.testing.FakeLibraryStore
import nl.jellycast.mobile.testing.SettingsTestConnectivity
import nl.jellycast.mobile.testing.SettingsTestPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val session = FakeSessionRepository(AuthState.LoggedIn(TestServer, "han"))
    private val store = FakeLibraryStore(movies = listOf(testMovie("m1"), testMovie("m2")))
    private val preferences = SettingsTestPreferences()
    private val chat = FakeChatSettings(key = "sk-ant-1234567890")
    private val viewModel by lazy {
        SettingsViewModel(
            session,
            store,
            preferences,
            SettingsTestConnectivity(NetworkKind.Cellular),
            chat,
            AppInfo("0.1.0", 7),
        )
    }

    @Test
    fun `shows the server, the account and the library`() = runTest {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        val state = viewModel.uiState.value

        assertEquals("home server", state.serverName)
        assertEquals(TestServer.url, state.serverUrl)
        assertEquals("han", state.userName)
        assertEquals(2, state.library?.movies)
        assertEquals(NetworkKind.Cellular, state.networkKind)
        assertEquals(ChatProvider.Claude, state.chat.provider)
        assertTrue(state.chat.hasKey)
        assertEquals("0.1.0", state.versionName)
    }

    @Test
    fun `changes go to the preferences`() {
        viewModel.onQualityChange(NetworkKind.Wifi, QualityPreference.Fixed(QualityLevel.P720))
        viewModel.onRatingSourceChange(RatingSource.Tmdb)

        assertEquals(
            QualityPreference.Fixed(QualityLevel.P720),
            preferences.quality.value[NetworkKind.Wifi],
        )
        assertEquals(RatingSource.Tmdb, preferences.ratingSource.value)
    }

    @Test
    fun `refresh syncs the library`() {
        viewModel.onRefreshLibrary()

        assertEquals(1, store.refreshCalls)
    }

    @Test
    fun `signing out logs out and drops the library cache`() {
        viewModel.onSignOut()

        assertEquals(1, session.logoutCalls)
        assertEquals(AuthState.LoggedOut(TestServer), session.auth.value)
        assertNull(store.snapshot.value)
    }
}

class AiChatSettingsViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val settings = FakeChatSettings()
    private val http = FakeChatHttp()
    private val viewModel by lazy {
        AiChatSettingsViewModel(settings, ChatClientFactory(settings, http, settings))
    }

    @Test
    fun `saving a key tests it`() {
        http.enqueueEvents("""{"data":[]}""")

        viewModel.onSaveKey("sk-ant-1234567890")

        val state = viewModel.uiState.value
        assertEquals("sk-ant-•••• 7890", state.settings.maskedKey)
        assertEquals(KeyCheck.Valid, state.check)
    }

    @Test
    fun `a rejected key says so`() {
        http.enqueueFailure(ChatException(ChatError.InvalidKey))

        viewModel.onSaveKey("sk-ant-wrong")

        assertEquals(KeyCheck.Failed(ChatError.InvalidKey), viewModel.uiState.value.check)
    }

    @Test
    fun `another provider has its own key and clears the check`() {
        http.enqueueEvents("""{"data":[]}""")
        viewModel.onSaveKey("sk-ant-1234567890")

        viewModel.onProviderChange(ChatProvider.OpenAi)

        val state = viewModel.uiState.value
        assertFalse(state.settings.hasKey)
        assertEquals(KeyCheck.Idle, state.check)
    }

    @Test
    fun `removing the key and the toggles`() {
        http.enqueueEvents("""{"data":[]}""")
        viewModel.onSaveKey("sk-ant-1234567890")

        viewModel.onRemoveKey()
        viewModel.onSearchesWebChange(false)
        viewModel.onAvoidsSpoilersChange(false)

        val state = viewModel.uiState.value.settings
        assertFalse(state.hasKey)
        assertFalse(state.searchesWeb)
        assertFalse(state.avoidsSpoilers)
    }
}
