package nl.jellycast.mobile.feature.signin

import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.QuickConnectState
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import nl.jellycast.mobile.core.network.NetworkKind
import nl.jellycast.mobile.testing.FakeServerDiscovery
import nl.jellycast.mobile.testing.SettingsTestConnectivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SignInViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val home = DiscoveredServer("a", "home server", TestServer.url)
    private val nas = DiscoveredServer("b", "nas", "http://192.168.1.20:8096")
    private val session = FakeSessionRepository()
    private val connectivity = SettingsTestConnectivity()

    private fun viewModel(discovery: ServerDiscovery = FakeServerDiscovery(listOf(home, nas))) =
        SignInViewModel(
            session,
            discovery,
            connectivity,
            AppInfo("0.1", 1, devServerUrl = "dev:8096"),
        )

    @Test
    fun `found servers are checked for their version and the stored one is current`() {
        session.auth.value = AuthState.LoggedOut(TestServer)

        val state = viewModel().uiState.value

        assertFalse(state.isSearching)
        assertEquals(TestServer.url, state.address)
        assertEquals(listOf(true, false), state.foundServers.map { it.isCurrent })
        assertEquals(ServerCheck.Ready("10.11.11"), state.foundServers.first().check)
    }

    @Test
    fun `without a stored server the dev address is filled in`() {
        assertEquals("dev:8096", viewModel(FakeServerDiscovery()).uiState.value.address)
    }

    @Test
    fun `a server that does not answer is marked`() {
        session.checkServerResult = ServerCheckResult.Unreachable

        val state = viewModel().uiState.value

        assertTrue(state.foundServers.all { it.check == ServerCheck.Unreachable })
    }

    @Test
    fun `connecting saves the server and shows the Quick Connect code`() = runTest {
        session.quickConnectFlow =
            flowOf(QuickConnectState.Starting, QuickConnectState.WaitingForApproval("804134"))
        val viewModel = viewModel()

        viewModel.onServerClick(home)

        assertEquals(
            SignInPhase.WaitingForApproval("home server", "804134"),
            viewModel.uiState.value.phase,
        )
        assertEquals(AuthState.LoggedOut(TestServer), session.auth.value)
    }

    @Test
    fun `cancel returns to the address`() = runTest {
        session.quickConnectFlow = flowOf(QuickConnectState.WaitingForApproval("1"))
        val viewModel = viewModel()
        viewModel.onConnect()

        viewModel.onCancel()

        assertEquals(SignInPhase.EnterAddress, viewModel.uiState.value.phase)
    }

    @Test
    fun `server errors are shown`() {
        val viewModel = viewModel(FakeServerDiscovery())

        session.checkServerResult = ServerCheckResult.NotJellyfin
        viewModel.onConnect()
        assertEquals(SignInPhase.Failed(SignInError.NotJellyfin), viewModel.uiState.value.phase)

        session.checkServerResult = ServerCheckResult.UnsupportedVersion("10.8.0")
        viewModel.onConnect()
        assertEquals(
            SignInPhase.Failed(SignInError.UnsupportedVersion("10.8.0")),
            viewModel.uiState.value.phase,
        )

        session.checkServerResult = ServerCheckResult.Unreachable
        connectivity.networkKind.value = NetworkKind.None
        viewModel.onConnect()
        assertEquals(SignInPhase.Failed(SignInError.Offline), viewModel.uiState.value.phase)
    }

    @Test
    fun `Quick Connect turned off is an error`() {
        session.quickConnectFlow = flowOf(QuickConnectState.Disabled)
        val viewModel = viewModel()

        viewModel.onConnect()

        assertEquals(
            SignInPhase.Failed(SignInError.QuickConnectDisabled),
            viewModel.uiState.value.phase,
        )
    }

    @Test
    fun `an invalid address can't be connected`() {
        val viewModel = viewModel()

        viewModel.onAddressChange("not an address")
        viewModel.onConnect()

        assertFalse(viewModel.uiState.value.canConnect)
        assertTrue(session.checkedInputs.none { it == "not an address" })
    }

    @Test
    fun `searching again runs a new discovery`() {
        val discovery = FakeServerDiscovery(listOf(home))
        val viewModel = viewModel(discovery)

        viewModel.onSearchAgain()

        assertEquals(2, discovery.searches)
        assertEquals(1, viewModel.uiState.value.foundServers.size)
    }
}
