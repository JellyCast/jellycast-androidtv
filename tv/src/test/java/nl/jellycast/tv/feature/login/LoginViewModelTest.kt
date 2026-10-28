package nl.jellycast.tv.feature.login

import kotlinx.coroutines.flow.flowOf
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.LoginResult
import nl.jellycast.core.model.QuickConnectState
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private fun repository() = FakeSessionRepository(AuthState.LoggedOut(TestServer))

    @Test
    fun `shows the server name and the Quick Connect code`() {
        val repository =
            repository().apply {
                quickConnectFlow =
                    flowOf(
                        QuickConnectState.Starting,
                        QuickConnectState.WaitingForApproval("123456"),
                    )
            }
        val viewModel = LoginViewModel(repository)

        assertEquals("home server", viewModel.uiState.value.serverName)
        assertEquals("192.168.1.4", viewModel.uiState.value.serverHost)
        assertEquals(
            QuickConnectState.WaitingForApproval("123456"),
            viewModel.uiState.value.quickConnect,
        )
        assertEquals(LoginMode.QuickConnect, viewModel.uiState.value.mode)
    }

    @Test
    fun `Quick Connect disabled on the server goes straight to the password form`() {
        val repository = repository().apply {
            quickConnectFlow = flowOf(QuickConnectState.Disabled)
        }
        val viewModel = LoginViewModel(repository)

        assertEquals(LoginMode.Password, viewModel.uiState.value.mode)
    }

    @Test
    fun `retry starts Quick Connect again`() {
        val repository = repository().apply { quickConnectFlow = flowOf(QuickConnectState.Failed) }
        val viewModel = LoginViewModel(repository)

        viewModel.onRetryQuickConnect()

        assertEquals(2, repository.quickConnectStarts)
    }

    @Test
    fun `wrong password shows an error`() {
        val repository = repository().apply { loginResult = LoginResult.InvalidCredentials }
        val viewModel = LoginViewModel(repository)

        viewModel.onUsePassword()
        viewModel.onUsernameChange("claude")
        viewModel.onPasswordChange("wrong")
        viewModel.onPasswordLogin()

        assertEquals(PasswordStatus.InvalidCredentials, viewModel.uiState.value.passwordStatus)
    }

    @Test
    fun `correct password logs in`() {
        val repository = repository()
        val viewModel = LoginViewModel(repository)

        viewModel.onUsernameChange(" claude ")
        viewModel.onPasswordLogin()

        assertEquals(AuthState.LoggedIn(TestServer, "claude"), repository.auth.value)
    }

    @Test
    fun `blank username does nothing`() {
        val repository = repository()
        val viewModel = LoginViewModel(repository)

        viewModel.onPasswordLogin()

        assertEquals(AuthState.LoggedOut(TestServer), repository.auth.value)
        assertEquals(PasswordStatus.Idle, viewModel.uiState.value.passwordStatus)
    }

    @Test
    fun `switching back to Quick Connect clears the password`() {
        val viewModel = LoginViewModel(repository())
        viewModel.onUsePassword()
        viewModel.onPasswordChange("secret")

        viewModel.onUseQuickConnect()

        assertEquals(LoginMode.QuickConnect, viewModel.uiState.value.mode)
        assertEquals("", viewModel.uiState.value.password)
    }

    @Test
    fun `display host drops scheme and port and keeps an unparsable address`() {
        assertEquals("192.168.1.4", "http://192.168.1.4:8096".toDisplayHost())
        assertEquals("jf.example.nl", "https://jf.example.nl/jellyfin".toDisplayHost())
        assertEquals("no address", "no address".toDisplayHost())
    }
}
