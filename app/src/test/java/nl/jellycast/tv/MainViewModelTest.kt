package nl.jellycast.tv

import nl.jellycast.tv.core.model.AuthState
import nl.jellycast.tv.navigation.Home
import nl.jellycast.tv.navigation.Login
import nl.jellycast.tv.navigation.ServerSetup
import nl.jellycast.tv.testing.FakeSessionRepository
import nl.jellycast.tv.testing.MainDispatcherRule
import nl.jellycast.tv.testing.TestServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MainViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `without a server the app starts on the server screen`() {
        val viewModel = MainViewModel(FakeSessionRepository(AuthState.NoServer))
        assertEquals(MainUiState.Ready(ServerSetup), viewModel.uiState.value)
    }

    @Test
    fun `logged out the app starts on login`() {
        val viewModel = MainViewModel(FakeSessionRepository(AuthState.LoggedOut(TestServer)))
        assertEquals(MainUiState.Ready(Login), viewModel.uiState.value)
    }

    @Test
    fun `logged in the app starts on Home and then validates the token`() {
        val repository = FakeSessionRepository(AuthState.LoggedIn(TestServer, "claude"))
        val viewModel = MainViewModel(repository)

        assertEquals(MainUiState.Ready(Home), viewModel.uiState.value)
        assertEquals(1, repository.validateCalls)
    }
}
