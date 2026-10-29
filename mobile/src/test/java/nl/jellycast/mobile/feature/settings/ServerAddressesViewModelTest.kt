package nl.jellycast.mobile.feature.settings

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import nl.jellycast.core.model.AddAddressResult
import nl.jellycast.core.model.AuthState
import nl.jellycast.core.testing.FakeServerAddressRepository
import nl.jellycast.core.testing.FakeSessionRepository
import nl.jellycast.core.testing.MainDispatcherRule
import nl.jellycast.core.testing.TestServer
import nl.jellycast.core.testing.TestVpnAddress
import nl.jellycast.mobile.testing.SettingsTestConnectivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerAddressesViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val repository = FakeServerAddressRepository(listOf(TestServer.url, TestVpnAddress))
    private val viewModel by lazy {
        ServerAddressesViewModel(
            repository,
            SettingsTestConnectivity(),
            FakeSessionRepository(AuthState.LoggedIn(TestServer, "han")),
        )
    }

    private fun TestScope.collect() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `shows the addresses in order and the one in use`() = runTest {
        collect()

        val state = viewModel.uiState.value

        assertEquals("home server", state.serverName)
        assertEquals(listOf(TestServer.url, TestVpnAddress), state.addresses)
        assertEquals(TestServer.url, state.inUse)
    }

    @Test
    fun `a tap uses an address that answers`() = runTest {
        collect()

        viewModel.onUse(TestVpnAddress)

        assertEquals(TestVpnAddress, viewModel.uiState.value.inUse)
        assertNull(viewModel.uiState.value.problem)
    }

    @Test
    fun `an address that doesn't answer is not used`() = runTest {
        repository.silent += TestVpnAddress
        collect()

        viewModel.onUse(TestVpnAddress)

        assertEquals(TestServer.url, viewModel.uiState.value.inUse)
        assertEquals(AddressProblem.NotAnswering, viewModel.uiState.value.problem)
    }

    @Test
    fun `an added address goes to the end`() = runTest {
        val repository = FakeServerAddressRepository()
        val viewModel =
            ServerAddressesViewModel(
                repository,
                SettingsTestConnectivity(),
                FakeSessionRepository(AuthState.LoggedIn(TestServer, "han")),
            )
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }

        viewModel.onAdd(TestVpnAddress)

        assertEquals(listOf(TestServer.url, TestVpnAddress), viewModel.uiState.value.addresses)
    }

    @Test
    fun `an address of another server is refused with its name`() = runTest {
        repository.addResult = AddAddressResult.OtherServer("attic")
        collect()

        viewModel.onAdd("http://192.168.1.9:8096")

        assertEquals(AddressProblem.OtherServer("attic"), viewModel.uiState.value.problem)
    }

    @Test
    fun `moving an address up makes it preferred`() = runTest {
        collect()

        viewModel.onMove(TestVpnAddress, -1)

        assertEquals(listOf(TestVpnAddress, TestServer.url), viewModel.uiState.value.addresses)
    }

    @Test
    fun `the last address can't be removed`() = runTest {
        collect()

        viewModel.onRemove(TestVpnAddress)
        viewModel.onRemove(TestServer.url)

        assertEquals(listOf(TestServer.url), viewModel.uiState.value.addresses)
    }

    @Test
    fun `a plain http address shows as host and port`() {
        assertEquals("10.8.0.1:8096", addressTitle(TestVpnAddress))
        assertEquals("https://jf.example.org", addressTitle("https://jf.example.org"))
    }
}
