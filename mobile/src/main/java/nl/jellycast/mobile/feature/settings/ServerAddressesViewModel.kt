package nl.jellycast.mobile.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import nl.jellycast.core.data.repository.ServerAddressRepository
import nl.jellycast.core.data.repository.SessionRepository
import nl.jellycast.core.model.AddAddressResult
import nl.jellycast.core.model.AuthState
import nl.jellycast.mobile.core.network.Connectivity

sealed interface AddressProblem {
    data object NotAnswering : AddressProblem

    data object Invalid : AddressProblem

    data object AlreadyListed : AddressProblem

    data class OtherServer(val name: String) : AddressProblem
}

data class ServerAddressesUiState(
    val serverName: String = "",
    val addresses: List<String> = emptyList(),
    val inUse: String? = null,
    val checking: String? = null,
    val isAdding: Boolean = false,
    val problem: AddressProblem? = null,
)

private data class AddressWork(
    val checking: String? = null,
    val isAdding: Boolean = false,
    val problem: AddressProblem? = null,
)

class ServerAddressesViewModel(
    private val repository: ServerAddressRepository,
    private val connectivity: Connectivity,
    session: SessionRepository,
) : ViewModel() {
    private val work = MutableStateFlow(AddressWork())

    val uiState: StateFlow<ServerAddressesUiState> =
        combine(repository.addresses, session.authState, work) { addresses, auth, work ->
            ServerAddressesUiState(
                serverName = (auth as? AuthState.LoggedIn)?.server?.name.orEmpty(),
                addresses = addresses.addresses,
                inUse = addresses.inUse,
                checking = work.checking,
                isAdding = work.isAdding,
                problem = work.problem,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ServerAddressesUiState())

    fun onUse(address: String) {
        if (address == uiState.value.inUse || work.value.checking != null) return
        work.value = AddressWork(checking = address)
        viewModelScope.launch {
            val used = repository.use(address)
            work.value = AddressWork(problem = AddressProblem.NotAnswering.takeUnless { used })
        }
    }

    fun onAdd(input: String) {
        work.value = AddressWork(isAdding = true)
        viewModelScope.launch {
            val problem =
                when (val result = repository.add(input)) {
                    AddAddressResult.Added -> null
                    AddAddressResult.InvalidAddress -> AddressProblem.Invalid
                    AddAddressResult.AlreadyListed -> AddressProblem.AlreadyListed
                    AddAddressResult.Unreachable -> AddressProblem.NotAnswering
                    is AddAddressResult.OtherServer -> AddressProblem.OtherServer(result.name)
                }
            work.value = AddressWork(problem = problem)
        }
    }

    fun onMove(address: String, by: Int) {
        val addresses = uiState.value.addresses.toMutableList()
        val from = addresses.indexOf(address)
        val to = from + by
        if (from < 0 || to !in addresses.indices) return
        addresses.add(to, addresses.removeAt(from))
        saveAndRecheck(addresses)
    }

    fun onRemove(address: String) {
        val addresses = uiState.value.addresses
        if (addresses.size < 2) return
        saveAndRecheck(addresses - address)
    }

    private fun saveAndRecheck(addresses: List<String>) {
        work.value = AddressWork()
        viewModelScope.launch {
            repository.save(addresses)
            connectivity.checkServer()
        }
    }
}
