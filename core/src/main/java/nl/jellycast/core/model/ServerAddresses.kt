package nl.jellycast.core.model

/** Where the signed-in server answers, in the order the user prefers them. */
data class ServerAddresses(val addresses: List<String>, val inUse: String?)

sealed interface AddAddressResult {
    data object Added : AddAddressResult

    data object InvalidAddress : AddAddressResult

    data object AlreadyListed : AddAddressResult

    data object Unreachable : AddAddressResult

    data class OtherServer(val name: String) : AddAddressResult
}
