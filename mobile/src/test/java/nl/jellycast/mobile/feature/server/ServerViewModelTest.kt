package nl.jellycast.mobile.feature.server

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ServerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appInfo = AppInfo(versionName = "0.1.0", versionCode = 1)

    @Test
    fun `servers found on the network are listed once`() = runTest {
        val session = FakeSessionRepository().apply {
            discoveryFlow = flowOf(TestServer, TestServer)
        }

        val state = ServerViewModel(session, appInfo).uiState.value

        assertEquals(listOf(TestServer), state.discovered)
        assertEquals(false, state.searching)
    }

    @Test
    fun `choosing a found server saves it`() = runTest {
        val session = FakeSessionRepository()
        val viewModel = ServerViewModel(session, appInfo)

        viewModel.onServerClick(TestServer)

        assertEquals(ServerStatus.Saved, viewModel.uiState.value.status)
        assertEquals(AuthState.LoggedOut(TestServer), session.auth.value)
    }

    @Test
    fun `an unreachable address shows why and saves nothing`() = runTest {
        val session = FakeSessionRepository().apply {
            checkServerResult =
                ServerCheckResult.Unreachable
        }
        val viewModel = ServerViewModel(session, appInfo)

        viewModel.onAddressChange("192.168.1.9")
        viewModel.onConnect()

        assertEquals(
            ServerStatus.Failed(ServerCheckResult.Unreachable),
            viewModel.uiState.value.status,
        )
        assertEquals(AuthState.NoServer, session.auth.value)
    }

    @Test
    fun `the dev server address is filled in`() {
        val state =
            ServerViewModel(FakeSessionRepository(), appInfo.copy(devServerUrl = "http://dev"))
                .uiState.value

        assertEquals("http://dev", state.address)
    }
}
