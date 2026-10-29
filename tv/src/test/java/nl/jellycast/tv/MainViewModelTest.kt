package nl.jellycast.tv

import nl.jellycast.core.model.AuthState
import nl.jellycast.core.testing.FakeAdRepository
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.FakeUsageRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import nl.jellycast.tv.navigation.Home
import nl.jellycast.tv.navigation.Login
import nl.jellycast.tv.navigation.ServerSetup
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `without a server the app starts on the server screen`() {
        val viewModel =
            MainViewModel(
                FakeSessionRepository(AuthState.NoServer),
                FakeUsageRepository(),
                FakeAdRepository(),
            )
        assertEquals(MainUiState.Ready(ServerSetup), viewModel.uiState.value)
    }

    @Test
    fun `logged out the app starts on login`() {
        val viewModel =
            MainViewModel(
                FakeSessionRepository(AuthState.LoggedOut(TestServer)),
                FakeUsageRepository(),
                FakeAdRepository(),
            )
        assertEquals(MainUiState.Ready(Login), viewModel.uiState.value)
    }

    @Test
    fun `logged in the app starts on Home and then validates the token`() {
        val repository = FakeSessionRepository(AuthState.LoggedIn(TestServer, "claude"))
        val viewModel = MainViewModel(repository, FakeUsageRepository(), FakeAdRepository())

        assertEquals(MainUiState.Ready(Home), viewModel.uiState.value)
        assertEquals(1, repository.validateCalls)
    }
}
