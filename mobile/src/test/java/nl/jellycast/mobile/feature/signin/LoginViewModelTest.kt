package nl.jellycast.mobile.feature.signin

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.LoginResult
import nl.jellycast.core.model.QuickConnectState
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class LoginViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `shows the server and starts without a code`() = runTest {
        val state = LoginViewModel(
            FakeSessionRepository(AuthState.LoggedOut(TestServer)),
        ).uiState.value

        assertEquals(TestServer.name, state.serverName)
        assertNull(state.quickConnect)
    }

    @Test
    fun `wrong credentials are reported`() = runTest {
        val session =
            FakeSessionRepository(AuthState.LoggedOut(TestServer)).apply {
                loginResult = LoginResult.InvalidCredentials
            }
        val viewModel = LoginViewModel(session)

        viewModel.onUsernameChange("han")
        viewModel.onPasswordLogin()

        assertEquals(PasswordStatus.InvalidCredentials, viewModel.uiState.value.passwordStatus)
    }

    @Test
    fun `without a username nothing is sent`() = runTest {
        val viewModel = LoginViewModel(FakeSessionRepository(AuthState.LoggedOut(TestServer)))

        viewModel.onPasswordLogin()

        assertEquals(PasswordStatus.Idle, viewModel.uiState.value.passwordStatus)
    }

    @Test
    fun `a code is shown on request and cancel goes back to the password`() = runTest {
        val session =
            FakeSessionRepository(AuthState.LoggedOut(TestServer)).apply {
                quickConnectFlow = flowOf(QuickConnectState.WaitingForApproval("123456"))
            }
        val viewModel = LoginViewModel(session)

        viewModel.onStartQuickConnect()
        assertEquals(
            QuickConnectState.WaitingForApproval("123456"),
            viewModel.uiState.value.quickConnect,
        )

        viewModel.onCancelQuickConnect()
        assertNull(viewModel.uiState.value.quickConnect)
    }
}
