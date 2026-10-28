package nl.jellycast.tv.feature.server

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import nl.jellycast.core.model.AppInfo
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.model.ServerCheckResult
import nl.jellycast.core.model.ServerInfo
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ServerViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val appInfo =
        AppInfo(versionName = "0.1.0", versionCode = 1, devServerUrl = "http://dev:8096")

    @Test
    fun `without a stored server the dev address is prefilled`() {
        val viewModel = ServerViewModel(FakeSessionRepository(AuthState.NoServer), appInfo)
        assertEquals("http://dev:8096", viewModel.uiState.value.address)
    }

    @Test
    fun `a stored server takes precedence over the dev address`() {
        val viewModel =
            ServerViewModel(FakeSessionRepository(AuthState.LoggedOut(TestServer)), appInfo)
        assertEquals(TestServer.url, viewModel.uiState.value.address)
    }

    @Test
    fun `a successful check saves the server and requests navigation`() {
        val repository = FakeSessionRepository()
        val viewModel = ServerViewModel(repository, appInfo)

        viewModel.onAddressChange("192.168.1.4")
        viewModel.onConnect()

        assertEquals(listOf("192.168.1.4"), repository.checkedInputs)
        assertEquals(AuthState.LoggedOut(TestServer), repository.auth.value)
        assertTrue(viewModel.uiState.value.serverSaved)

        viewModel.onNavigated()
        assertFalse(viewModel.uiState.value.serverSaved)
    }

    @Test
    fun `an unreachable server shows an error and saves nothing`() {
        val repository =
            FakeSessionRepository().apply { checkServerResult = ServerCheckResult.Unreachable }
        val viewModel = ServerViewModel(repository, appInfo)

        viewModel.onConnect()

        assertEquals(ServerStatus.Unreachable, viewModel.uiState.value.status)
        assertEquals(AuthState.NoServer, repository.auth.value)
    }

    @Test
    fun `a server that is too old shows its version`() {
        val repository = FakeSessionRepository().apply {
            checkServerResult = ServerCheckResult.UnsupportedVersion("10.9.0")
        }
        val viewModel = ServerViewModel(repository, appInfo)

        viewModel.onConnect()

        assertEquals(ServerStatus.UnsupportedVersion("10.9.0"), viewModel.uiState.value.status)
    }

    @Test
    fun `typing clears the error`() {
        val repository =
            FakeSessionRepository().apply { checkServerResult = ServerCheckResult.InvalidAddress }
        val viewModel = ServerViewModel(repository, appInfo)
        viewModel.onConnect()

        viewModel.onAddressChange("192.168.1.4")

        assertEquals(ServerStatus.Idle, viewModel.uiState.value.status)
    }

    @Test
    fun `pressing again while checking does not start a second check`() {
        val gate = CompletableDeferred<Unit>()
        val repository = FakeSessionRepository().apply { checkServerGate = gate }
        val viewModel = ServerViewModel(repository, appInfo)

        viewModel.onConnect()
        assertEquals(ServerStatus.Checking, viewModel.uiState.value.status)
        viewModel.onConnect()
        gate.complete(Unit)

        assertEquals(1, repository.checkedInputs.size)
        assertTrue(viewModel.uiState.value.serverSaved)
    }

    private val otherServer = ServerInfo(url = "http://192.168.1.9:8096", name = "attic")

    @Test
    fun `discovery starts on open and ends with the found servers`() {
        val repository = FakeSessionRepository().apply {
            discoveryFlow = flowOf(TestServer, otherServer)
        }
        val viewModel = ServerViewModel(repository, appInfo)

        assertEquals(1, repository.discoveryStarts)
        assertEquals(DiscoveryStatus.Done, viewModel.uiState.value.discovery)
        assertEquals(listOf(TestServer, otherServer), viewModel.foundServers())
    }

    @Test
    fun `each found server gets its version on the card`() {
        val repository = FakeSessionRepository().apply { discoveryFlow = flowOf(otherServer) }
        val viewModel = ServerViewModel(repository, appInfo)

        assertEquals(listOf(otherServer.url), repository.checkedInputs)
        assertEquals(
            ServerCardStatus.Ready("10.11.11"),
            viewModel.uiState.value.discoveredServers.single().check,
        )
    }

    @Test
    fun `a server that is too old or silent is marked as such on the card`() {
        val repository = FakeSessionRepository().apply {
            discoveryFlow = flowOf(otherServer)
            checkServerResult = ServerCheckResult.UnsupportedVersion("10.9.0")
        }
        assertEquals(
            ServerCardStatus.TooOld("10.9.0"),
            ServerViewModel(repository, appInfo).uiState.value.discoveredServers.single().check,
        )

        repository.checkServerResult = ServerCheckResult.Unreachable
        assertEquals(
            ServerCardStatus.Unreachable,
            ServerViewModel(repository, appInfo).uiState.value.discoveredServers.single().check,
        )
    }

    @Test
    fun `the stored server is marked as current`() {
        val repository = FakeSessionRepository(AuthState.LoggedIn(TestServer, "claude")).apply {
            discoveryFlow = flowOf(otherServer, TestServer)
        }
        val viewModel = ServerViewModel(repository, appInfo)

        assertEquals(
            listOf(false, true),
            viewModel.uiState.value.discoveredServers.map { it.isCurrent },
        )
    }

    @Test
    fun `manual entry opens the field and Back closes it`() {
        val repository = FakeSessionRepository().apply {
            checkServerResult = ServerCheckResult.Unreachable
        }
        val viewModel = ServerViewModel(repository, appInfo)
        viewModel.onServerClick(otherServer)
        assertEquals(ServerStatus.Unreachable, viewModel.uiState.value.status)

        viewModel.onManualEntryClick()
        assertTrue(viewModel.uiState.value.manualEntry)
        assertEquals(ServerStatus.Idle, viewModel.uiState.value.status)

        viewModel.onManualEntryClose()
        assertFalse(viewModel.uiState.value.manualEntry)
    }

    @Test
    fun `while discovering the status is searching`() {
        val repository = FakeSessionRepository().apply { discoveryFlow = MutableSharedFlow() }
        val viewModel = ServerViewModel(repository, appInfo)

        assertEquals(DiscoveryStatus.Searching, viewModel.uiState.value.discovery)
    }

    @Test
    fun `duplicate and excess servers are not shown`() {
        val third = ServerInfo(url = "http://192.168.1.10:8096", name = "office")
        val fourth = ServerInfo(url = "http://192.168.1.11:8096", name = "garage")
        val repository = FakeSessionRepository().apply {
            discoveryFlow = flowOf(TestServer, TestServer, otherServer, third, fourth)
        }
        val viewModel = ServerViewModel(repository, appInfo)

        assertEquals(listOf(TestServer, otherServer, third), viewModel.foundServers())
    }

    @Test
    fun `OK on a found server connects to that address`() {
        val repository = FakeSessionRepository().apply { discoveryFlow = flowOf(otherServer) }
        val viewModel = ServerViewModel(repository, appInfo)

        viewModel.onServerClick(otherServer)

        // First the version check for the card, then the connect check.
        assertEquals(listOf(otherServer.url, otherServer.url), repository.checkedInputs)
        assertEquals(otherServer.url, viewModel.uiState.value.address)
        assertTrue(viewModel.uiState.value.serverSaved)
    }

    @Test
    fun `search again starts a new discovery`() {
        val repository = FakeSessionRepository()
        val viewModel = ServerViewModel(repository, appInfo)
        assertEquals(DiscoveryStatus.Done, viewModel.uiState.value.discovery)

        repository.discoveryFlow = flowOf(TestServer)
        viewModel.onSearchAgain()

        assertEquals(2, repository.discoveryStarts)
        assertEquals(listOf(TestServer), viewModel.foundServers())
    }

    @Test
    fun `search again while discovering does nothing`() {
        val repository = FakeSessionRepository().apply { discoveryFlow = MutableSharedFlow() }
        val viewModel = ServerViewModel(repository, appInfo)

        viewModel.onSearchAgain()

        assertEquals(1, repository.discoveryStarts)
    }

    private fun ServerViewModel.foundServers() = uiState.value.discoveredServers.map { it.server }
}
