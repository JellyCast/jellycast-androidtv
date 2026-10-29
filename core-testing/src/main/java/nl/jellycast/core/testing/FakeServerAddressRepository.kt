package nl.jellycast.core.testing

import kotlinx.coroutines.flow.MutableStateFlow
import nl.jellycast.core.data.repository.ServerAddressRepository
import nl.jellycast.core.model.AddAddressResult
import nl.jellycast.core.model.ServerAddresses

val TestVpnAddress = "http://10.8.0.1:8096"

class FakeServerAddressRepository(
    addresses: List<String> = listOf(TestServer.url),
    inUse: String? = TestServer.url,
) : ServerAddressRepository {
    override val addresses = MutableStateFlow(ServerAddresses(addresses, inUse))

    /** What [add] answers; an [AddAddressResult.Added] appends the input. */
    var addResult: AddAddressResult = AddAddressResult.Added

    /** Addresses [use] reports as not answering. */
    val silent = mutableSetOf<String>()

    override suspend fun add(input: String): AddAddressResult {
        if (addResult == AddAddressResult.Added) {
            addresses.value = addresses.value.copy(addresses = addresses.value.addresses + input)
        }
        return addResult
    }

    override suspend fun use(address: String): Boolean {
        if (address in silent) return false
        addresses.value = addresses.value.copy(inUse = address)
        return true
    }

    override suspend fun save(addresses: List<String>) {
        this.addresses.value = this.addresses.value.copy(addresses = addresses)
    }
}
